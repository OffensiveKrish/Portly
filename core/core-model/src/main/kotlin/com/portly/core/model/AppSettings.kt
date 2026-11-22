package com.portly.core.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val defaultTransferConcurrency: Int = 3,
    val defaultConflictPolicy: ConflictPolicy = ConflictPolicy.ASK,
    val autoRetryTransientErrors: Boolean = true,
    val verifyChecksums: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val defaultSortField: SortField = SortField.NAME,
    val defaultSortDirection: SortDirection = SortDirection.ASCENDING,
    val biometricLockEnabled: Boolean = false,
    val strictHostKeyMode: HostKeyMode = HostKeyMode.STRICT,
    val smbLegacyEnabled: Boolean = false,
    val plainFtpWarningAcknowledged: Boolean = false,
    val batteryPromptDismissed: Boolean = false,
    val wifiOnlyTransfers: Boolean = false,
    val hideLegacyUnencryptedServers: Boolean = false,
    val diagnosticLoggingEnabled: Boolean = true,
    val logRetentionDays: Int = 30,

    // Server Configuration
    val serverAutoStopWhenIdle: Boolean = false,
    val serverIdleTimeoutMinutes: Int = 10,
    val serverFtpPort: Int = 2121,
    val serverFtpsPort: Int = 2122,
    val serverHttpPort: Int = 8080,
    val serverHttpsPort: Int = 8443,
    val serverRootFolder: String = "/storage/emulated/0",
    val serverAllowAnonymousLogin: Boolean = false,
    val serverReadOnlyMode: Boolean = false,
    val serverShowMultipleIps: Boolean = false,
    val serverEnableSslTls: Boolean = true,

    // Web Browser Access
    val allowBrowserAccessHttp: Boolean = true,
    val showHiddenFilesInWeb: Boolean = false,

    // DuckDNS DDNS
    val duckDnsEnabled: Boolean = false,
    val duckDnsHostname: String = "",
    val duckDnsToken: String = "",

    // File Management
    val overwriteExistingFiles: Boolean = false,
    val dynamicDownloadPath: Boolean = false,
    val downloadDirectory: String = "/storage/emulated/0/Download/Portly",
    val showFolderStats: Boolean = true,
    val horizontalDualPaneMode: Boolean = false,

    // Transfers
    val connectionTimeoutSeconds: Int = 30,
    val transferBufferSizeBytes: Int = 0, // 0 = Auto
    val retryAttempts: Int = 3,
    val resumeTransfers: Boolean = true,
    val allowBackgroundTransfers: Boolean = true,

    // Automatic Updates
    val autoCheckUpdates: Boolean = true,
    val updateReleaseChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val notifyOnUpdate: Boolean = true,
    val lastUpdateCheckTimestamp: Long = 0L,
    val ignoredUpdateVersionCode: Long = 0L,
    val customGitHubRepo: String = "upgrdify/portly",
)

data class EnterprisePolicy(
    val disablePlainFtp: Boolean = false,
    val disableSmb1Legacy: Boolean = false,
    val allowPublicServerBind: Boolean = true,
    val requireBiometricLock: Boolean = false,
    val disableServers: Boolean = false,
    val diagnosticLoggingOverride: Boolean? = null,
) {
    val isManaged: Boolean
        get() = disablePlainFtp ||
            disableSmb1Legacy ||
            !allowPublicServerBind ||
            requireBiometricLock ||
            disableServers ||
            diagnosticLoggingOverride != null
}
