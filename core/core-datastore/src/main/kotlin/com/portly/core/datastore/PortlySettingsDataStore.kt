package com.portly.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.portly.core.model.AppSettings
import com.portly.core.model.ConflictPolicy
import com.portly.core.model.HostKeyMode
import com.portly.core.model.ReleaseChannel
import com.portly.core.model.SortDirection
import com.portly.core.model.SortField
import com.portly.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.portlyDataStore: DataStore<Preferences> by preferencesDataStore(name = "portly_settings")

@Singleton
class PortlySettingsDataStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        val DEFAULT_CONCURRENCY = intPreferencesKey("default_transfer_concurrency")
        val DEFAULT_CONFLICT_POLICY = stringPreferencesKey("default_conflict_policy")
        val AUTO_RETRY = booleanPreferencesKey("auto_retry_transient_errors")
        val VERIFY_CHECKSUMS = booleanPreferencesKey("verify_checksums")
        val SHOW_HIDDEN_FILES = booleanPreferencesKey("show_hidden_files")
        val SORT_FIELD = stringPreferencesKey("default_sort_field")
        val SORT_DIRECTION = stringPreferencesKey("default_sort_direction")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock_enabled")
        val STRICT_HOST_KEY = stringPreferencesKey("strict_host_key_mode")
        val SMB_LEGACY = booleanPreferencesKey("smb_legacy_enabled")
        val PLAIN_FTP_ACK = booleanPreferencesKey("plain_ftp_warning_acknowledged")
        val BATTERY_DISMISSED = booleanPreferencesKey("battery_prompt_dismissed")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_transfers")
        val HIDE_LEGACY_SERVERS = booleanPreferencesKey("hide_legacy_unencrypted_servers")
        val DIAGNOSTIC_LOGGING = booleanPreferencesKey("diagnostic_logging_enabled")
        val LOG_RETENTION_DAYS = intPreferencesKey("log_retention_days")

        // Server Configuration
        val SERVER_AUTO_STOP_IDLE = booleanPreferencesKey("server_auto_stop_idle")
        val SERVER_IDLE_TIMEOUT = intPreferencesKey("server_idle_timeout_minutes")
        val SERVER_FTP_PORT = intPreferencesKey("server_ftp_port")
        val SERVER_FTPS_PORT = intPreferencesKey("server_ftps_port")
        val SERVER_HTTP_PORT = intPreferencesKey("server_http_port")
        val SERVER_HTTPS_PORT = intPreferencesKey("server_https_port")
        val SERVER_ROOT_FOLDER = stringPreferencesKey("server_root_folder")
        val SERVER_ALLOW_ANON = booleanPreferencesKey("server_allow_anon")
        val SERVER_READ_ONLY = booleanPreferencesKey("server_read_only")
        val SERVER_SHOW_MULTIPLE_IPS = booleanPreferencesKey("server_show_multiple_ips")
        val SERVER_ENABLE_SSL_TLS = booleanPreferencesKey("server_enable_ssl_tls")

        // Web Browser Access
        val ALLOW_BROWSER_HTTP = booleanPreferencesKey("allow_browser_http")
        val SHOW_HIDDEN_IN_WEB = booleanPreferencesKey("show_hidden_in_web")

        // DuckDNS DDNS
        val DUCK_DNS_ENABLED = booleanPreferencesKey("duck_dns_enabled")
        val DUCK_DNS_HOSTNAME = stringPreferencesKey("duck_dns_hostname")
        val DUCK_DNS_TOKEN = stringPreferencesKey("duck_dns_token")

        // File Management
        val OVERWRITE_EXISTING = booleanPreferencesKey("overwrite_existing_files")
        val DYNAMIC_DOWNLOAD_PATH = booleanPreferencesKey("dynamic_download_path")
        val DOWNLOAD_DIRECTORY = stringPreferencesKey("download_directory")
        val SHOW_FOLDER_STATS = booleanPreferencesKey("show_folder_stats")
        val HORIZONTAL_DUAL_PANE = booleanPreferencesKey("horizontal_dual_pane")

        // Transfers
        val CONNECTION_TIMEOUT = intPreferencesKey("connection_timeout_seconds")
        val TRANSFER_BUFFER_SIZE = intPreferencesKey("transfer_buffer_size_bytes")
        val RETRY_ATTEMPTS = intPreferencesKey("retry_attempts")
        val RESUME_TRANSFERS = booleanPreferencesKey("resume_transfers")
        val ALLOW_BG_TRANSFERS = booleanPreferencesKey("allow_background_transfers")

        // Automatic Updates
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val UPDATE_RELEASE_CHANNEL = stringPreferencesKey("update_release_channel")
        val NOTIFY_ON_UPDATE = booleanPreferencesKey("notify_on_update")
        val LAST_UPDATE_CHECK_TIMESTAMP = longPreferencesKey("last_update_check_timestamp")
        val IGNORED_UPDATE_VERSION_CODE = longPreferencesKey("ignored_update_version_code")
        val CUSTOM_GITHUB_REPO = stringPreferencesKey("custom_github_repo")
    }

    val settings: Flow<AppSettings> = context.portlyDataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            useDynamicColor = prefs[Keys.USE_DYNAMIC_COLOR] ?: true,
            defaultTransferConcurrency = (prefs[Keys.DEFAULT_CONCURRENCY] ?: 3).coerceIn(1, 6),
            defaultConflictPolicy = prefs[Keys.DEFAULT_CONFLICT_POLICY]?.let {
                runCatching { ConflictPolicy.valueOf(it) }.getOrNull()
            } ?: ConflictPolicy.ASK,
            autoRetryTransientErrors = prefs[Keys.AUTO_RETRY] ?: true,
            verifyChecksums = prefs[Keys.VERIFY_CHECKSUMS] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN_FILES] ?: false,
            defaultSortField = prefs[Keys.SORT_FIELD]?.let {
                runCatching { SortField.valueOf(it) }.getOrNull()
            } ?: SortField.NAME,
            defaultSortDirection = prefs[Keys.SORT_DIRECTION]?.let {
                runCatching { SortDirection.valueOf(it) }.getOrNull()
            } ?: SortDirection.ASCENDING,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            strictHostKeyMode = prefs[Keys.STRICT_HOST_KEY]?.let {
                runCatching { HostKeyMode.valueOf(it) }.getOrNull()
            } ?: HostKeyMode.STRICT,
            smbLegacyEnabled = prefs[Keys.SMB_LEGACY] ?: false,
            plainFtpWarningAcknowledged = prefs[Keys.PLAIN_FTP_ACK] ?: false,
            batteryPromptDismissed = prefs[Keys.BATTERY_DISMISSED] ?: false,
            wifiOnlyTransfers = prefs[Keys.WIFI_ONLY] ?: false,
            hideLegacyUnencryptedServers = prefs[Keys.HIDE_LEGACY_SERVERS] ?: false,
            diagnosticLoggingEnabled = prefs[Keys.DIAGNOSTIC_LOGGING] ?: true,
            logRetentionDays = (prefs[Keys.LOG_RETENTION_DAYS] ?: 30).coerceIn(1, 365),

            // Server Configuration
            serverAutoStopWhenIdle = prefs[Keys.SERVER_AUTO_STOP_IDLE] ?: false,
            serverIdleTimeoutMinutes = prefs[Keys.SERVER_IDLE_TIMEOUT] ?: 10,
            serverFtpPort = prefs[Keys.SERVER_FTP_PORT] ?: 2121,
            serverFtpsPort = prefs[Keys.SERVER_FTPS_PORT] ?: 2122,
            serverHttpPort = prefs[Keys.SERVER_HTTP_PORT] ?: 8080,
            serverHttpsPort = prefs[Keys.SERVER_HTTPS_PORT] ?: 8443,
            serverRootFolder = prefs[Keys.SERVER_ROOT_FOLDER] ?: "/storage/emulated/0",
            serverAllowAnonymousLogin = prefs[Keys.SERVER_ALLOW_ANON] ?: false,
            serverReadOnlyMode = prefs[Keys.SERVER_READ_ONLY] ?: false,
            serverShowMultipleIps = prefs[Keys.SERVER_SHOW_MULTIPLE_IPS] ?: false,
            serverEnableSslTls = prefs[Keys.SERVER_ENABLE_SSL_TLS] ?: true,

            // Web Browser Access
            allowBrowserAccessHttp = prefs[Keys.ALLOW_BROWSER_HTTP] ?: true,
            showHiddenFilesInWeb = prefs[Keys.SHOW_HIDDEN_IN_WEB] ?: false,

            // DuckDNS DDNS
            duckDnsEnabled = prefs[Keys.DUCK_DNS_ENABLED] ?: false,
            duckDnsHostname = prefs[Keys.DUCK_DNS_HOSTNAME] ?: "",
            duckDnsToken = prefs[Keys.DUCK_DNS_TOKEN] ?: "",

            // File Management
            overwriteExistingFiles = prefs[Keys.OVERWRITE_EXISTING] ?: false,
            dynamicDownloadPath = prefs[Keys.DYNAMIC_DOWNLOAD_PATH] ?: false,
            downloadDirectory = prefs[Keys.DOWNLOAD_DIRECTORY] ?: "/storage/emulated/0/Download/Portly",
            showFolderStats = prefs[Keys.SHOW_FOLDER_STATS] ?: true,
            horizontalDualPaneMode = prefs[Keys.HORIZONTAL_DUAL_PANE] ?: false,

            // Transfers
            connectionTimeoutSeconds = prefs[Keys.CONNECTION_TIMEOUT] ?: 30,
            transferBufferSizeBytes = prefs[Keys.TRANSFER_BUFFER_SIZE] ?: 0,
            retryAttempts = prefs[Keys.RETRY_ATTEMPTS] ?: 3,
            resumeTransfers = prefs[Keys.RESUME_TRANSFERS] ?: true,
            allowBackgroundTransfers = prefs[Keys.ALLOW_BG_TRANSFERS] ?: true,

            // Automatic Updates
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            updateReleaseChannel = prefs[Keys.UPDATE_RELEASE_CHANNEL]?.let {
                runCatching { ReleaseChannel.valueOf(it) }.getOrNull()
            } ?: ReleaseChannel.STABLE,
            notifyOnUpdate = prefs[Keys.NOTIFY_ON_UPDATE] ?: true,
            lastUpdateCheckTimestamp = prefs[Keys.LAST_UPDATE_CHECK_TIMESTAMP] ?: 0L,
            ignoredUpdateVersionCode = prefs[Keys.IGNORED_UPDATE_VERSION_CODE] ?: 0L,
            customGitHubRepo = prefs[Keys.CUSTOM_GITHUB_REPO]?.takeIf {
                it.isNotBlank() && !it.equals("upgrdify/portly", ignoreCase = true)
            } ?: "OffensiveKrish/Portly",
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.portlyDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.USE_DYNAMIC_COLOR] = enabled }
    }

    suspend fun setDefaultConcurrency(concurrency: Int) {
        context.portlyDataStore.edit { it[Keys.DEFAULT_CONCURRENCY] = concurrency.coerceIn(1, 6) }
    }

    suspend fun setDefaultConflictPolicy(policy: ConflictPolicy) {
        context.portlyDataStore.edit { it[Keys.DEFAULT_CONFLICT_POLICY] = policy.name }
    }

    suspend fun setAutoRetry(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.AUTO_RETRY] = enabled }
    }

    suspend fun setVerifyChecksums(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.VERIFY_CHECKSUMS] = enabled }
    }

    suspend fun setShowHiddenFiles(show: Boolean) {
        context.portlyDataStore.edit { it[Keys.SHOW_HIDDEN_FILES] = show }
    }

    suspend fun setSort(field: SortField, direction: SortDirection) {
        context.portlyDataStore.edit {
            it[Keys.SORT_FIELD] = field.name
            it[Keys.SORT_DIRECTION] = direction.name
        }
    }

    suspend fun setBiometricLockEnabled(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.BIOMETRIC_LOCK] = enabled }
    }

    suspend fun setStrictHostKeyMode(mode: HostKeyMode) {
        context.portlyDataStore.edit { it[Keys.STRICT_HOST_KEY] = mode.name }
    }

    suspend fun setSmbLegacyEnabled(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.SMB_LEGACY] = enabled }
    }

    suspend fun setPlainFtpWarningAcknowledged(ack: Boolean) {
        context.portlyDataStore.edit { it[Keys.PLAIN_FTP_ACK] = ack }
    }

    suspend fun setBatteryPromptDismissed(dismissed: Boolean) {
        context.portlyDataStore.edit { it[Keys.BATTERY_DISMISSED] = dismissed }
    }

    suspend fun setWifiOnlyTransfers(wifiOnly: Boolean) {
        context.portlyDataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setHideLegacyUnencryptedServers(hide: Boolean) {
        context.portlyDataStore.edit { it[Keys.HIDE_LEGACY_SERVERS] = hide }
    }

    suspend fun setDiagnosticLoggingEnabled(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.DIAGNOSTIC_LOGGING] = enabled }
    }

    suspend fun setLogRetentionDays(days: Int) {
        context.portlyDataStore.edit { it[Keys.LOG_RETENTION_DAYS] = days.coerceIn(1, 365) }
    }

    // Server Configuration
    suspend fun setServerAutoStopWhenIdle(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.SERVER_AUTO_STOP_IDLE] = enabled }
    }

    suspend fun setServerIdleTimeoutMinutes(minutes: Int) {
        context.portlyDataStore.edit { it[Keys.SERVER_IDLE_TIMEOUT] = minutes }
    }

    suspend fun setServerFtpPort(port: Int) {
        context.portlyDataStore.edit { it[Keys.SERVER_FTP_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setServerFtpsPort(port: Int) {
        context.portlyDataStore.edit { it[Keys.SERVER_FTPS_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setServerHttpPort(port: Int) {
        context.portlyDataStore.edit { it[Keys.SERVER_HTTP_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setServerHttpsPort(port: Int) {
        context.portlyDataStore.edit { it[Keys.SERVER_HTTPS_PORT] = port.coerceIn(1024, 65535) }
    }

    suspend fun setServerRootFolder(path: String) {
        context.portlyDataStore.edit { it[Keys.SERVER_ROOT_FOLDER] = path }
    }

    suspend fun setServerAllowAnonymousLogin(allow: Boolean) {
        context.portlyDataStore.edit { it[Keys.SERVER_ALLOW_ANON] = allow }
    }

    suspend fun setServerReadOnlyMode(readOnly: Boolean) {
        context.portlyDataStore.edit { it[Keys.SERVER_READ_ONLY] = readOnly }
    }

    suspend fun setServerShowMultipleIps(show: Boolean) {
        context.portlyDataStore.edit { it[Keys.SERVER_SHOW_MULTIPLE_IPS] = show }
    }

    suspend fun setServerEnableSslTls(enable: Boolean) {
        context.portlyDataStore.edit { it[Keys.SERVER_ENABLE_SSL_TLS] = enable }
    }

    // Web Browser Access
    suspend fun setAllowBrowserAccessHttp(allow: Boolean) {
        context.portlyDataStore.edit { it[Keys.ALLOW_BROWSER_HTTP] = allow }
    }

    suspend fun setShowHiddenFilesInWeb(show: Boolean) {
        context.portlyDataStore.edit { it[Keys.SHOW_HIDDEN_IN_WEB] = show }
    }

    // DuckDNS DDNS
    suspend fun setDuckDnsEnabled(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.DUCK_DNS_ENABLED] = enabled }
    }

    suspend fun setDuckDnsHostname(hostname: String) {
        context.portlyDataStore.edit { it[Keys.DUCK_DNS_HOSTNAME] = hostname.trim() }
    }

    suspend fun setDuckDnsToken(token: String) {
        context.portlyDataStore.edit { it[Keys.DUCK_DNS_TOKEN] = token.trim() }
    }

    // File Management
    suspend fun setOverwriteExistingFiles(overwrite: Boolean) {
        context.portlyDataStore.edit { it[Keys.OVERWRITE_EXISTING] = overwrite }
    }

    suspend fun setDynamicDownloadPath(dynamic: Boolean) {
        context.portlyDataStore.edit { it[Keys.DYNAMIC_DOWNLOAD_PATH] = dynamic }
    }

    suspend fun setDownloadDirectory(path: String) {
        context.portlyDataStore.edit { it[Keys.DOWNLOAD_DIRECTORY] = path }
    }

    suspend fun setShowFolderStats(show: Boolean) {
        context.portlyDataStore.edit { it[Keys.SHOW_FOLDER_STATS] = show }
    }

    suspend fun setHorizontalDualPaneMode(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.HORIZONTAL_DUAL_PANE] = enabled }
    }

    // Transfers
    suspend fun setConnectionTimeoutSeconds(seconds: Int) {
        context.portlyDataStore.edit { it[Keys.CONNECTION_TIMEOUT] = seconds.coerceIn(5, 300) }
    }

    suspend fun setTransferBufferSizeBytes(bytes: Int) {
        context.portlyDataStore.edit { it[Keys.TRANSFER_BUFFER_SIZE] = bytes }
    }

    suspend fun setRetryAttempts(attempts: Int) {
        context.portlyDataStore.edit { it[Keys.RETRY_ATTEMPTS] = attempts.coerceIn(0, 10) }
    }

    suspend fun setResumeTransfers(resume: Boolean) {
        context.portlyDataStore.edit { it[Keys.RESUME_TRANSFERS] = resume }
    }

    suspend fun setAllowBackgroundTransfers(allow: Boolean) {
        context.portlyDataStore.edit { it[Keys.ALLOW_BG_TRANSFERS] = allow }
    }

    // Automatic Updates
    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setUpdateReleaseChannel(channel: ReleaseChannel) {
        context.portlyDataStore.edit { it[Keys.UPDATE_RELEASE_CHANNEL] = channel.name }
    }

    suspend fun setNotifyOnUpdate(enabled: Boolean) {
        context.portlyDataStore.edit { it[Keys.NOTIFY_ON_UPDATE] = enabled }
    }

    suspend fun setLastUpdateCheckTimestamp(timestamp: Long) {
        context.portlyDataStore.edit { it[Keys.LAST_UPDATE_CHECK_TIMESTAMP] = timestamp }
    }

    suspend fun setIgnoredUpdateVersionCode(code: Long) {
        context.portlyDataStore.edit { it[Keys.IGNORED_UPDATE_VERSION_CODE] = code }
    }

    suspend fun setCustomGitHubRepo(repo: String) {
        context.portlyDataStore.edit { it[Keys.CUSTOM_GITHUB_REPO] = repo.trim() }
    }
}
