package com.portly.feature.settings

import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.AppSettings
import com.portly.core.model.EnterprisePolicy
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.security.LogRedactor

object DiagnosticBundleExporter {

    fun buildRedactedJson(
        generatedAtMillis: Long,
        androidSdkInt: Int,
        deviceModel: String,
        settings: AppSettings,
        enterprisePolicy: EnterprisePolicy,
        activityLogs: List<ActivityLogEntry>,
        transferHistory: List<TransferHistoryEntry>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("{")
        sb.appendLine("""  "schema": "portly-diagnostic-v1",""")
        sb.appendLine("""  "generatedAtMillis": $generatedAtMillis,""")
        sb.appendLine("""  "androidSdkInt": $androidSdkInt,""")
        sb.appendLine("""  "deviceModel": "${esc(deviceModel)}",""")
        sb.appendLine("""  "privacyGuarantees": {""")
        sb.appendLine("""    "thirdPartyTrackers": false,""")
        sb.appendLine("""    "analyticsSdk": false,""")
        sb.appendLine("""    "credentialsIncluded": false,""")
        sb.appendLine("""    "privateKeysIncluded": false""")
        sb.appendLine("""  },""")
        sb.appendLine("""  "settings": {""")
        sb.appendLine("""    "themeMode": "${settings.themeMode.name}",""")
        sb.appendLine("""    "defaultConcurrency": ${settings.defaultTransferConcurrency},""")
        sb.appendLine("""    "conflictPolicy": "${settings.defaultConflictPolicy.name}",""")
        sb.appendLine("""    "verifyChecksums": ${settings.verifyChecksums},""")
        sb.appendLine("""    "strictHostKeyMode": "${settings.strictHostKeyMode.name}",""")
        sb.appendLine("""    "smbLegacyEnabled": ${settings.smbLegacyEnabled},""")
        sb.appendLine("""    "wifiOnlyTransfers": ${settings.wifiOnlyTransfers},""")
        sb.appendLine("""    "diagnosticLoggingEnabled": ${settings.diagnosticLoggingEnabled},""")
        sb.appendLine("""    "logRetentionDays": ${settings.logRetentionDays}""")
        sb.appendLine("""  },""")
        sb.appendLine("""  "enterprisePolicy": {""")
        sb.appendLine("""    "isManaged": ${enterprisePolicy.isManaged},""")
        sb.appendLine("""    "disablePlainFtp": ${enterprisePolicy.disablePlainFtp},""")
        sb.appendLine("""    "disableSmb1Legacy": ${enterprisePolicy.disableSmb1Legacy},""")
        sb.appendLine("""    "allowPublicServerBind": ${enterprisePolicy.allowPublicServerBind},""")
        sb.appendLine("""    "requireBiometricLock": ${enterprisePolicy.requireBiometricLock},""")
        sb.appendLine("""    "disableServers": ${enterprisePolicy.disableServers}""")
        sb.appendLine("""  },""")
        sb.appendLine("""  "activityLogs": [""")
        activityLogs.forEachIndexed { idx, entry ->
            val comma = if (idx + 1 < activityLogs.size) "," else ""
            val cleanMsg = LogRedactor.redact(entry.message)
            val cleanDetail = entry.detail?.let { LogRedactor.redact(it) }.orEmpty()
            sb.appendLine(
                """    {"timestampMillis": ${entry.timestampMillis}, "severity": "${entry.severity.name}", "category": "${esc(entry.category)}", "message": "${esc(cleanMsg)}", "detail": "${esc(cleanDetail)}"}$comma""",
            )
        }
        sb.appendLine("""  ],""")
        sb.appendLine("""  "transferHistory": [""")
        transferHistory.forEachIndexed { idx, item ->
            val comma = if (idx + 1 < transferHistory.size) "," else ""
            val cleanError = item.errorSummary?.let { LogRedactor.redact(it) }.orEmpty()
            sb.appendLine(
                """    {"finishedAtMillis": ${item.finishedAtMillis}, "direction": "${item.direction.name}", "protocol": "${item.protocol?.name.orEmpty()}", "status": "${item.status.name}", "fileName": "${esc(item.fileName)}", "bytesTransferred": ${item.bytesTransferred}, "checksumVerified": ${item.checksumVerified}, "errorSummary": "${esc(cleanError)}"}$comma""",
            )
        }
        sb.appendLine("""  ]""")
        sb.appendLine("}")
        return sb.toString()
    }

    private fun esc(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }
}
