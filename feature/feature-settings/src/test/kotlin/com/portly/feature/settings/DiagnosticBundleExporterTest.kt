package com.portly.feature.settings

import com.portly.core.model.ActivityLogEntry
import com.portly.core.model.ActivitySeverity
import com.portly.core.model.AppSettings
import com.portly.core.model.EnterprisePolicy
import com.portly.core.model.ProtocolType
import com.portly.core.model.TransferDirection
import com.portly.core.model.TransferHistoryEntry
import com.portly.core.model.TransferStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticBundleExporterTest {

    @Test
    fun `diagnostic bundle redacts secrets and preserves audit metadata`() {
        val json = DiagnosticBundleExporter.buildRedactedJson(
            generatedAtMillis = 123456789L,
            androidSdkInt = 35,
            deviceModel = "Pixel",
            settings = AppSettings(logRetentionDays = 30),
            enterprisePolicy = EnterprisePolicy(disablePlainFtp = true),
            activityLogs = listOf(
                ActivityLogEntry(
                    id = 1L,
                    timestampMillis = 1000L,
                    category = "Auth",
                    severity = ActivitySeverity.WARN,
                    message = "Login attempt PASS hunter2 failed Authorization: Bearer secretTok",
                    detail = "-----BEGIN PRIVATE KEY-----\nABC\n-----END PRIVATE KEY-----",
                ),
            ),
            transferHistory = listOf(
                TransferHistoryEntry(
                    id = "t1",
                    direction = TransferDirection.DOWNLOAD,
                    protocol = ProtocolType.SFTP,
                    profileName = "Prod",
                    fileName = "report.pdf",
                    sourcePath = "/report.pdf",
                    destinationPath = "/sdcard/report.pdf",
                    totalBytes = 1024L,
                    bytesTransferred = 1024L,
                    status = TransferStatus.COMPLETED,
                    averageSpeedBytesPerSec = 512L,
                    checksumVerified = true,
                    errorSummary = null,
                    startedAtMillis = 900L,
                    finishedAtMillis = 1000L,
                ),
            ),
        )
        assertFalse(json.contains("hunter2"))
        assertFalse(json.contains("secretTok"))
        assertFalse(json.contains("ABC"))
        assertTrue(json.contains("portly-diagnostic-v1"))
        assertTrue(json.contains("\"disablePlainFtp\": true"))
        assertTrue(json.contains("report.pdf"))
    }
}
