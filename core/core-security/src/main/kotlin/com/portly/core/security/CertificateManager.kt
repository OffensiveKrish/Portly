package com.portly.core.security

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class GeneratedServerCert(
    val keystoreFile: File,
    val alias: String,
    val sha256Fingerprint: String,
    val validFromMillis: Long,
    val validUntilMillis: Long,
)

class CertificateUntrustedException(
    val host: String,
    val port: Int,
    val sha256Fingerprint: String,
    val subject: String,
    val issuer: String,
    val validFromMillis: Long,
    val validUntilMillis: Long,
) : java.security.cert.CertificateException(
    "Untrusted certificate for $host:$port ($sha256Fingerprint)",
)

@Singleton
class CertificateManager(
    private val certStorageDir: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(File(context.noBackupFilesDir, "certs"))

    init {
        ensureBouncyCastleRegistered()
    }

    fun serverKeystoreDir(): File = certStorageDir.apply { mkdirs() }

    fun generateOrLoadServerKeystore(
        serverName: String,
        sanIpAddresses: List<String>,
        keystorePassword: CharArray,
        forceRegenerate: Boolean = false,
    ): GeneratedServerCert {
        val safeName = serverName.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val keystoreFile = File(serverKeystoreDir(), "portly_${safeName}.p12")
        val alias = "portly_$safeName"

        if (!forceRegenerate && keystoreFile.exists()) {
            val ks = KeyStore.getInstance("PKCS12")
            keystoreFile.inputStream().use { ks.load(it, keystorePassword) }
            val existingCert = ks.getCertificate(alias) as? X509Certificate
            if (existingCert != null && existingCert.notAfter.after(Date())) {
                return GeneratedServerCert(
                    keystoreFile = keystoreFile,
                    alias = alias,
                    sha256Fingerprint = sha256Fingerprint(existingCert),
                    validFromMillis = existingCert.notBefore.time,
                    validUntilMillis = existingCert.notAfter.time,
                )
            }
        }

        val generated = createSelfSignedCertificate(
            commonName = "Portly $serverName Server",
            sanIpAddresses = sanIpAddresses,
            validityDays = 365,
        )

        val ks = KeyStore.getInstance("PKCS12").apply {
            load(null, keystorePassword)
            setKeyEntry(alias, generated.privateKey, keystorePassword, arrayOf(generated.certificate))
        }
        keystoreFile.outputStream().use { ks.store(it, keystorePassword) }

        return GeneratedServerCert(
            keystoreFile = keystoreFile,
            alias = alias,
            sha256Fingerprint = sha256Fingerprint(generated.certificate),
            validFromMillis = generated.certificate.notBefore.time,
            validUntilMillis = generated.certificate.notAfter.time,
        )
    }

    fun createSslContextFromPkcs12(keystoreFile: File, password: CharArray): SSLContext {
        val ks = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { ks.load(it, password) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(ks, password)
        }
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }
    }

    fun buildTrustManager(
        host: String,
        port: Int,
        trustedHostFingerprints: Set<String>,
        userCaCertificates: List<X509Certificate>,
    ): X509TrustManager {
        val systemTmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
            init(null as KeyStore?)
        }
        val systemTm = systemTmf.trustManagers.filterIsInstance<X509TrustManager>().first()

        val userTm: X509TrustManager? = if (userCaCertificates.isNotEmpty()) {
            val userKs = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                userCaCertificates.forEachIndexed { idx, cert ->
                    setCertificateEntry("user_ca_$idx", cert)
                }
            }
            TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(userKs)
            }.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
        } else {
            null
        }

        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>, authType: String) {
                systemTm.checkClientTrusted(chain, authType)
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>, authType: String) {
                if (chain.isEmpty()) throw java.security.cert.CertificateException("Empty certificate chain")
                val leaf = chain[0]
                val fingerprint = sha256Fingerprint(leaf)
                if (fingerprint in trustedHostFingerprints) {
                    leaf.checkValidity()
                    return
                }
                val systemErr = runCatching { systemTm.checkServerTrusted(chain, authType) }.exceptionOrNull()
                if (systemErr == null) return

                if (userTm != null) {
                    val userErr = runCatching { userTm.checkServerTrusted(chain, authType) }.exceptionOrNull()
                    if (userErr == null) return
                }

                throw CertificateUntrustedException(
                    host = host,
                    port = port,
                    sha256Fingerprint = fingerprint,
                    subject = leaf.subjectX500Principal.name,
                    issuer = leaf.issuerX500Principal.name,
                    validFromMillis = leaf.notBefore.time,
                    validUntilMillis = leaf.notAfter.time,
                )
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> {
                val sys = systemTm.acceptedIssuers
                val usr = userTm?.acceptedIssuers ?: emptyArray()
                return sys + usr
            }
        }
    }

    fun buildSslContext(trustManager: TrustManager): SSLContext {
        return SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustManager), SecureRandom())
        }
    }

    fun parseCertificates(bytes: ByteArray): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        return ByteArrayInputStream(bytes).use { stream ->
            factory.generateCertificates(stream).filterIsInstance<X509Certificate>()
        }
    }

    data class KeyPairCert(
        val privateKey: java.security.PrivateKey,
        val certificate: X509Certificate,
    )

    companion object {
        fun ensureBouncyCastleRegistered() {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            } else {
                // On Android, a stripped BC provider is bundled; replace/insert full BC if needed for PKIX.
                val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                if (current != null && !current.javaClass.name.startsWith("org.bouncycastle.jce.provider")) {
                    Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                    Security.addProvider(BouncyCastleProvider())
                }
            }
        }

        fun createSelfSignedCertificate(
            commonName: String,
            sanIpAddresses: List<String> = emptyList(),
            validityDays: Int = 365,
        ): KeyPairCert {
            ensureBouncyCastleRegistered()
            val keyPairGen = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }
            val keyPair = keyPairGen.generateKeyPair()
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 60_000L)
            val notAfter = Date(now + validityDays.toLong() * 86_400_000L)
            val serial = BigInteger(128, SecureRandom()).abs()
            val subject = X500Name("CN=$commonName, O=Portly")

            val builder = JcaX509v3CertificateBuilder(
                subject,
                serial,
                notBefore,
                notAfter,
                subject,
                keyPair.public,
            )
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
            )
            val sanNames = buildList {
                add(GeneralName(GeneralName.dNSName, "localhost"))
                add(GeneralName(GeneralName.iPAddress, "127.0.0.1"))
                sanIpAddresses.distinct().filter { it.isNotBlank() && it != "127.0.0.1" }.forEach { ip ->
                    add(GeneralName(GeneralName.iPAddress, ip))
                }
            }
            builder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(sanNames.toTypedArray()),
            )

            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val holder = builder.build(signer)
            val cert = JcaX509CertificateConverter().getCertificate(holder)
            return KeyPairCert(keyPair.private, cert)
        }

        fun sha256Fingerprint(certificate: X509Certificate): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            return digest.joinToString(":") { "%02X".format(it) }
        }
    }
}
