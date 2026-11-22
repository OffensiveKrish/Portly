package com.portly.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveredLanService(
    val serviceName: String,
    val serviceType: String,
    val hostAddress: String,
    val port: Int,
)

@Singleton
class NsdDiscoveryManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val nsdManager: NsdManager
        get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discoverServices(serviceType: String): Flow<List<DiscoveredLanService>> = callbackFlow {
        val manager = nsdManager
        val found = linkedMapOf<String, DiscoveredLanService>()
        val resolveExecutor = Executors.newSingleThreadExecutor()

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                trySend(found.values.toList())
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                resolveServiceCompat(manager, serviceInfo, resolveExecutor) { resolved, host ->
                    val item = DiscoveredLanService(
                        serviceName = resolved.serviceName,
                        serviceType = resolved.serviceType,
                        hostAddress = host,
                        port = resolved.port,
                    )
                    synchronized(found) {
                        found["${item.serviceName}:${item.hostAddress}:${item.port}"] = item
                        trySend(found.values.toList())
                    }
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(found) {
                    found.entries.removeIf { it.value.serviceName == serviceInfo.serviceName }
                    trySend(found.values.toList())
                }
            }

            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }

        runCatching {
            manager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }.onFailure {
            close(it)
        }

        awaitClose {
            runCatching { manager.stopServiceDiscovery(discoveryListener) }
            resolveExecutor.shutdownNow()
        }
    }

    fun registerService(
        serviceName: String,
        serviceType: String,
        port: Int,
        onRegistered: (NsdServiceInfo) -> Unit = {},
    ): NsdManager.RegistrationListener {
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = serviceType
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                onRegistered(nsdServiceInfo)
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        return listener
    }

    fun unregisterService(listener: NsdManager.RegistrationListener) {
        runCatching { nsdManager.unregisterService(listener) }
    }

    @Suppress("DEPRECATION")
    private fun resolveServiceCompat(
        manager: NsdManager,
        serviceInfo: NsdServiceInfo,
        executor: java.util.concurrent.Executor,
        onResolved: (NsdServiceInfo, String) -> Unit,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            manager.registerServiceInfoCallback(
                serviceInfo,
                executor,
                object : NsdManager.ServiceInfoCallback {
                    override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                    override fun onServiceUpdated(updated: NsdServiceInfo) {
                        val host = updated.hostAddresses.firstOrNull()?.hostAddress ?: return
                        onResolved(updated, host)
                        runCatching { manager.unregisterServiceInfoCallback(this) }
                    }
                    override fun onServiceLost() = Unit
                    override fun onServiceInfoCallbackUnregistered() = Unit
                },
            )
        } else {
            manager.resolveService(
                serviceInfo,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        val host = resolved.host?.hostAddress ?: return
                        onResolved(resolved, host)
                    }
                },
            )
        }
    }

    companion object {
        const val SERVICE_TYPE_SMB = "_smb._tcp."
        const val SERVICE_TYPE_PORTLY = "_portly._tcp."
    }
}
