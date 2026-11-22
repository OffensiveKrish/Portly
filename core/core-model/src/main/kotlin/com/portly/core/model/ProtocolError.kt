package com.portly.core.model

sealed class ProtocolError(
    open val userMessage: String,
    open val nextStep: String,
    open val technicalDetail: String? = null,
    open val protocolCode: Int? = null,
    open val isTransient: Boolean = false,
) {
    data class AuthenticationFailed(
        override val userMessage: String = "Authentication failed.",
        override val nextStep: String = "Check your username, password, or SSH private key and try again.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient = false)

    data class ConnectionFailed(
        override val userMessage: String = "Could not connect to the server.",
        override val nextStep: String = "Verify the host address, port, and that both devices are on the same network.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
        override val isTransient: Boolean = true,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient)

    data class Timeout(
        override val userMessage: String = "Connection timed out.",
        override val nextStep: String = "Check network stability or increase the connection timeout in profile settings.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient = true)

    data class HostKeyMismatch(
        val host: String,
        val port: Int,
        val expectedFingerprint: String,
        val actualFingerprint: String,
        override val userMessage: String = "Server SSH host key changed.",
        override val nextStep: String = "Verify the server fingerprint before trusting the new key, as this could indicate interception.",
        override val technicalDetail: String? = "Expected: $expectedFingerprint, Received: $actualFingerprint",
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode = null, isTransient = false)

    data class CertificateUntrusted(
        val host: String,
        val port: Int,
        val fingerprintSha256: String,
        val subject: String,
        override val userMessage: String = "TLS certificate is not trusted.",
        override val nextStep: String = "Inspect the SHA-256 certificate fingerprint and approve it only if you trust this server.",
        override val technicalDetail: String? = "Subject: $subject | SHA-256: $fingerprintSha256",
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode = null, isTransient = false)

    data class PermissionDenied(
        override val userMessage: String = "Permission denied.",
        override val nextStep: String = "Confirm your account has read/write rights for this directory on the server.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient = false)

    data class PathNotFound(
        override val userMessage: String = "File or folder not found.",
        override val nextStep: String = "Refresh the directory listing and verify the path still exists.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient = false)

    data class PathTraversalRejected(
        override val userMessage: String = "Blocked unsafe path access outside configured root.",
        override val nextStep: String = "Use a valid relative path inside the shared root folder.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = 550,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient = false)

    data class ProtocolFailure(
        override val userMessage: String,
        override val nextStep: String = "Review the technical protocol detail and retry.",
        override val technicalDetail: String? = null,
        override val protocolCode: Int? = null,
        override val isTransient: Boolean = false,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode, isTransient)

    data class ChecksumMismatch(
        val expected: String,
        val actual: String,
        override val userMessage: String = "File integrity verification failed.",
        override val nextStep: String = "Retry the transfer; the file may have been corrupted in transit.",
        override val technicalDetail: String? = "Expected $expected, got $actual",
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode = null, isTransient = true)

    data object Cancelled : ProtocolError(
        userMessage = "Operation cancelled.",
        nextStep = "Start or retry the operation whenever you are ready.",
        technicalDetail = null,
        protocolCode = null,
        isTransient = false,
    )

    data class UnsupportedOperation(
        override val userMessage: String = "This operation is not supported by the current protocol.",
        override val nextStep: String = "Try a different protocol such as SFTP or FTPS if available.",
        override val technicalDetail: String? = null,
    ) : ProtocolError(userMessage, nextStep, technicalDetail, protocolCode = null, isTransient = false)
}
