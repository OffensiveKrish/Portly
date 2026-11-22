package com.portly.protocol.webdav.client

import java.security.MessageDigest
import java.security.SecureRandom

internal object DigestAuthHelper {

    fun buildDigestHeader(
        wwwAuthenticate: String,
        method: String,
        uriPath: String,
        username: String,
        password: String,
    ): String? {
        if (!wwwAuthenticate.trimStart().startsWith("Digest ", ignoreCase = true)) return null
        val params = parseParams(wwwAuthenticate.trimStart().substring(7))
        val realm = params["realm"] ?: return null
        val nonce = params["nonce"] ?: return null
        val opaque = params["opaque"]
        val qopRaw = params["qop"]
        val algo = params["algorithm"]?.uppercase() ?: "MD5"
        val digestAlgo = when {
            algo.startsWith("SHA-256") -> "SHA-256"
            else -> "MD5"
        }

        val cnonce = ByteArray(8).also { SecureRandom().nextBytes(it) }
            .joinToString("") { "%02x".format(it) }
        val nc = "00000001"
        val qop = qopRaw?.split(',')?.map { it.trim() }?.firstOrNull { it == "auth" }

        val ha1Base = hash("$username:$realm:$password", digestAlgo)
        val ha1 = if (algo.endsWith("-SESS")) hash("$ha1Base:$nonce:$cnonce", digestAlgo) else ha1Base
        val ha2 = hash("$method:$uriPath", digestAlgo)

        val response = if (qop != null) {
            hash("$ha1:$nonce:$nc:$cnonce:$qop:$ha2", digestAlgo)
        } else {
            hash("$ha1:$nonce:$ha2", digestAlgo)
        }

        return buildString {
            append("Digest username=\"").append(username).append("\", ")
            append("realm=\"").append(realm).append("\", ")
            append("nonce=\"").append(nonce).append("\", ")
            append("uri=\"").append(uriPath).append("\", ")
            if (params.containsKey("algorithm")) {
                append("algorithm=").append(params["algorithm"]).append(", ")
            }
            if (qop != null) {
                append("qop=").append(qop).append(", ")
                append("nc=").append(nc).append(", ")
                append("cnonce=\"").append(cnonce).append("\", ")
            }
            append("response=\"").append(response).append("\"")
            if (opaque != null) {
                append(", opaque=\"").append(opaque).append("\"")
            }
        }
    }

    private fun hash(input: String, algorithm: String): String {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun parseParams(headerValue: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val regex = Regex("""(\w+)=("([^"]*)"|([^,\s]+))""")
        for (match in regex.findAll(headerValue)) {
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[3].ifEmpty { match.groupValues[4] }
            map[key] = value
        }
        return map
    }
}
