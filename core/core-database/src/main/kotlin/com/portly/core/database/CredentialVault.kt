package com.portly.core.database

import com.portly.core.common.IoDispatcher
import com.portly.core.database.dao.EncryptedSecretDao
import com.portly.core.database.entity.EncryptedSecretEntity
import com.portly.core.model.ResolvedCredentials
import com.portly.core.security.CredentialCipher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SecretPayload(
    val username: String,
    val password: String? = null,
    val privateKeyPem: String? = null,
    val passphrase: String? = null,
)

@Singleton
class CredentialVault @Inject constructor(
    private val secretDao: EncryptedSecretDao,
    private val cipher: CredentialCipher,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveCredentials(
        existingRef: String?,
        credentials: ResolvedCredentials,
    ): String = withContext(ioDispatcher) {
        val ref = existingRef ?: "sec_${UUID.randomUUID()}"
        val payload = json.encodeToString(
            SecretPayload.serializer(),
            SecretPayload(
                username = credentials.username,
                password = credentials.password,
                privateKeyPem = credentials.privateKeyPem,
                passphrase = credentials.passphrase,
            ),
        )
        val encrypted = cipher.encrypt(payload)
        secretDao.upsert(
            EncryptedSecretEntity(
                secretRef = ref,
                encryptedBlob = encrypted,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        ref
    }

    suspend fun loadCredentials(secretRef: String?, fallbackUsername: String): ResolvedCredentials =
        withContext(ioDispatcher) {
            if (secretRef.isNullOrBlank()) {
                return@withContext ResolvedCredentials(username = fallbackUsername)
            }
            val entity = secretDao.getByRef(secretRef)
                ?: return@withContext ResolvedCredentials(username = fallbackUsername)
            val decrypted = cipher.decrypt(entity.encryptedBlob)
            val parsed = json.decodeFromString(SecretPayload.serializer(), decrypted)
            ResolvedCredentials(
                username = parsed.username.ifEmpty { fallbackUsername },
                password = parsed.password,
                privateKeyPem = parsed.privateKeyPem,
                passphrase = parsed.passphrase,
            )
        }

    suspend fun deleteCredentials(secretRef: String?) = withContext(ioDispatcher) {
        if (!secretRef.isNullOrBlank()) {
            secretDao.deleteByRef(secretRef)
        }
    }
}
