package com.portly.network

import android.content.Context
import com.portly.core.common.ApplicationScope
import com.portly.core.common.IoDispatcher
import com.portly.core.database.CredentialVault
import com.portly.core.database.dao.ActivityLogDao
import com.portly.core.database.entity.ActivityLogEntity
import com.portly.core.model.ActivitySeverity
import com.portly.core.model.ConnectedClientInfo
import com.portly.core.model.ProtocolServer
import com.portly.core.model.ProtocolServerFactory
import com.portly.core.model.ResolvedCredentials
import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerLogEntry
import com.portly.core.model.ServerRuntimeState
import com.portly.core.model.ServerStatus
import com.portly.core.model.ServerType
import com.portly.core.model.ServerUserAccount
import com.portly.core.security.EnterprisePolicyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortlyServerManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serverFactories: Map<ServerType, @JvmSuppressWildcards ProtocolServerFactory>,
    private val networkMonitor: NetworkMonitor,
    private val credentialVault: CredentialVault,
    private val activityLogDao: ActivityLogDao,
    private val enterprisePolicyManager: EnterprisePolicyManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val configFile: File
        get() = File(context.noBackupFilesDir, "server_configs.json")

    private val defaultShareDir: File
        get() {
            val ext = runCatching { android.os.Environment.getExternalStorageDirectory() }.getOrNull()
            return if (ext != null && ext.exists()) ext else File(context.filesDir, "PortlyShare").apply { mkdirs() }
        }

    private val servers: Map<ServerType, ProtocolServer?> = ServerType.entries.associateWith { type ->
        serverFactories[type]?.create()
    }

    private val _configs = MutableStateFlow(loadSavedConfigs())
    val configs: StateFlow<Map<ServerType, ServerConfig>> = _configs.asStateFlow()

    val states: StateFlow<Map<ServerType, ServerRuntimeState>> = combine(
        ServerType.entries.map { type ->
            servers[type]?.state ?: MutableStateFlow(ServerRuntimeState(serverType = type))
        },
    ) { arr ->
        arr.associateBy { it.serverType }
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = ServerType.entries.associateWith { ServerRuntimeState(serverType = it) },
    )

    val clients: StateFlow<List<ConnectedClientInfo>> = combine(
        ServerType.entries.map { type ->
            servers[type]?.clients ?: MutableStateFlow(emptyList())
        },
    ) { arr ->
        arr.flatMap { it }.sortedByDescending { it.connectedAtMillis }
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    val logs: StateFlow<List<ServerLogEntry>> = combine(
        ServerType.entries.map { type ->
            servers[type]?.logs ?: MutableStateFlow(emptyList())
        },
    ) { arr ->
        arr.flatMap { it }.sortedByDescending { it.timestampMillis }.take(300)
    }.stateIn(
        scope = appScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    init {
        appScope.launch(ioDispatcher) {
            networkMonitor.observeStatus().collect { status ->
                val ips = status.localIpv4Addresses
                servers.values.filterNotNull().forEach { server ->
                    server.updateLocalAddresses(ips)
                }
            }
        }
    }

    fun isProtocolSupported(serverType: ServerType): Boolean = servers[serverType] != null

    suspend fun startServer(serverType: ServerType): Result<Unit> {
        val policy = enterprisePolicyManager.currentPolicy()
        if (policy.disableServers) {
            return Result.failure(SecurityException("Starting file servers is disabled by enterprise MDM policy."))
        }
        if (policy.disablePlainFtp && serverType == ServerType.FTP) {
            return Result.failure(SecurityException("Unencrypted plain FTP server is disabled by enterprise MDM policy."))
        }
        val server = servers[serverType]
            ?: return Result.failure(UnsupportedOperationException("${serverType.displayName} is not available in this build phase."))
        val rawConfig = _configs.value[serverType] ?: defaultConfig(serverType)
        val config = if (!policy.allowPublicServerBind) {
            rawConfig.copy(lanOnlyBind = true, allowPublicBindOptIn = false)
        } else {
            rawConfig
        }
        server.updateLocalAddresses(networkMonitor.currentStatus().localIpv4Addresses)
        val result = server.start(config)
        if (result.isSuccess) {
            activityLogDao.insert(
                ActivityLogEntity(
                    timestampMillis = System.currentTimeMillis(),
                    category = "Server",
                    severity = ActivitySeverity.INFO.name,
                    message = "Started ${serverType.displayName} on port ${server.state.value.port}",
                    detail = null,
                ),
            )
        }
        return result
    }

    suspend fun stopServer(serverType: ServerType) {
        val server = servers[serverType] ?: return
        server.stop()
        activityLogDao.insert(
            ActivityLogEntity(
                timestampMillis = System.currentTimeMillis(),
                category = "Server",
                severity = ActivitySeverity.INFO.name,
                message = "Stopped ${serverType.displayName}",
                detail = null,
            ),
        )
    }

    suspend fun stopAllServers() {
        ServerType.entries.forEach { type ->
            if (states.value[type]?.status == ServerStatus.RUNNING) {
                stopServer(type)
            }
        }
    }

    suspend fun kickClient(serverType: ServerType, clientId: String): Result<Unit> {
        val server = servers[serverType] ?: return Result.failure(IllegalStateException("Server not found"))
        return server.kickClient(clientId)
    }

    suspend fun updateConfig(
        config: ServerConfig,
        newPasswordsByUsername: Map<String, String> = emptyMap(),
    ) {
        val updatedUsers = config.users.map { account ->
            val rawPass = newPasswordsByUsername[account.username]
            if (!account.isAnonymous && !rawPass.isNullOrEmpty()) {
                val ref = credentialVault.saveCredentials(
                    existingRef = account.secretRef,
                    credentials = ResolvedCredentials(username = account.username, password = rawPass),
                )
                account.copy(secretRef = ref)
            } else {
                account
            }
        }
        val finalized = config.copy(users = updatedUsers)
        _configs.update { current ->
            val next = current + (config.serverType to finalized)
            persistConfigs(next)
            next
        }
    }

    private fun loadSavedConfigs(): Map<ServerType, ServerConfig> {
        val base = ServerType.entries.associateWith { defaultConfig(it) }.toMutableMap()
        if (!configFile.exists()) return base
        runCatching {
            val stored = json.decodeFromString<Map<String, ServerConfig>>(configFile.readText())
            var needsPersist = false
            for ((k, v) in stored) {
                runCatching { ServerType.valueOf(k) }.getOrNull()?.let { type ->
                    val isLegacyInternal = v.rootTreeUriString.isNullOrBlank() && (
                        v.rootDirectoryPath.contains("PortlyShare") ||
                        v.rootDirectoryPath.contains(context.packageName) ||
                        !File(v.rootDirectoryPath).exists()
                    )
                    val updated = if (isLegacyInternal) {
                        needsPersist = true
                        v.copy(rootDirectoryPath = defaultShareDir.absolutePath)
                    } else {
                        v
                    }
                    base[type] = updated
                }
            }
            if (needsPersist) {
                persistConfigs(base)
            }
        }
        return base
    }

    private fun persistConfigs(map: Map<ServerType, ServerConfig>) {
        runCatching {
            val serializable = map.mapKeys { it.key.name }
            configFile.writeText(json.encodeToString(serializable))
        }
    }

    private fun defaultConfig(type: ServerType): ServerConfig = ServerConfig(
        serverType = type,
        port = type.defaultPort,
        rootDirectoryPath = defaultShareDir.absolutePath,
        readOnly = false,
        maxConnections = 10,
        maxConnectionsPerIp = 4,
        lanOnlyBind = true,
        allowPublicBindOptIn = false,
        requireAuth = false,
        users = listOf(
            ServerUserAccount(
                username = "anonymous",
                isAnonymous = true,
            ),
        ),
    )
}
