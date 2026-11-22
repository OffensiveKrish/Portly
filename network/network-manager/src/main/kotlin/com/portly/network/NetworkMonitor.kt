package com.portly.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

enum class NetworkTransport {
    NONE,
    WIFI_OR_ETHERNET,
    CELLULAR,
    OTHER,
}

data class NetworkStatus(
    val isConnected: Boolean,
    val transport: NetworkTransport,
    val localIpv4Addresses: List<String>,
    val isHotspotActive: Boolean = false,
)

@Singleton
class NetworkMonitor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val connectivityManager: ConnectivityManager
        get() = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    fun observeStatus(): Flow<NetworkStatus> = callbackFlow {
        val cm = connectivityManager
        val emitCurrent = { trySend(currentStatus()) }
        emitCurrent()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                emitCurrent()
            }

            override fun onLost(network: Network) {
                emitCurrent()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                emitCurrent()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        runCatching { cm.registerNetworkCallback(request, callback) }
        awaitClose {
            runCatching { cm.unregisterNetworkCallback(callback) }
        }
    }.distinctUntilChanged()

    fun currentStatus(): NetworkStatus {
        val cm = connectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val hotspot = isHotspotActive()
        val localIps = detectLocalIpv4Addresses()
        val connected = (caps != null && (
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            )) || hotspot
        val transport = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true ||
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> NetworkTransport.WIFI_OR_ETHERNET
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> NetworkTransport.CELLULAR
            caps == null && !hotspot -> NetworkTransport.NONE
            else -> NetworkTransport.OTHER
        }
        return NetworkStatus(
            isConnected = connected,
            transport = transport,
            localIpv4Addresses = localIps,
            isHotspotActive = hotspot,
        )
    }

    fun isHotspotActive(): Boolean {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                if (name.startsWith("ap") || name.startsWith("softap") || name.startsWith("swlan")) {
                    return true
                }
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress.orEmpty()
                        if (host.startsWith("192.168.43.") || host.startsWith("192.168.49.")) {
                            return true
                        }
                    }
                }
            }
            false
        }.getOrDefault(false)
    }

    fun detectLocalIpv4Addresses(): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue
                val name = iface.name.lowercase()
                // Ignore cellular modem interfaces and tunnels: other devices cannot connect via cellular data
                if (name.startsWith("rmnet") || name.startsWith("ccmni") ||
                    name.startsWith("pdp") || name.startsWith("wwan") ||
                    name.startsWith("clat") || name.startsWith("dummy") ||
                    name.startsWith("tun") || name.startsWith("sit") ||
                    name.startsWith("ip6") || name.startsWith("ifb") ||
                    name.startsWith("gre")
                ) {
                    continue
                }
                val addrs = iface.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress && !addr.isLinkLocalAddress) {
                        addr.hostAddress?.let { result += it }
                    }
                }
            }
        }
        return result.distinct()
    }
}
