package com.portly.core.security

/**
 * Redacts credentials, bearer/session tokens, private key PEM blocks,
 * and filesystem paths outside a server's configured root directory before writing logs.
 */
object LogRedactor {
    private val passwordPattern = Regex(
        pattern = """(?i)(PASS\s+|password[=:]\s*|passphrase[=:]\s*|secret[=:]\s*)([^\s;,&"]+)""",
    )
    private val authHeaderPattern = Regex(
        pattern = """(?i)(Authorization:\s*(?:Basic|Bearer|Digest)\s+)([^\r\n]+)""",
    )
    private val pemBlockPattern = Regex(
        pattern = """-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----""",
    )
    private val tokenParamPattern = Regex(
        pattern = """(?i)([?&](?:token|auth|session|key)=)([^&\s]+)""",
    )

    fun redact(raw: String, allowedServerRoot: String? = null): String {
        var result = raw
            .replace(pemBlockPattern, "[REDACTED_PRIVATE_KEY]")
            .replace(passwordPattern) { "${it.groupValues[1]}[REDACTED]" }
            .replace(authHeaderPattern) { "${it.groupValues[1]}[REDACTED]" }
            .replace(tokenParamPattern) { "${it.groupValues[1]}[REDACTED]" }

        if (!allowedServerRoot.isNullOrBlank()) {
            val normalizedRoot = allowedServerRoot.trimEnd('/')
            result = result.replace(normalizedRoot, "<root>")
            // Strip any absolute Android storage prefix outside root if leaked in exception messages.
            result = result.replace(Regex("""/(?:data|storage|mnt)/[^\s"'<>]+"""), "<external-path-redacted>")
        }
        return result
    }
}
