package com.portly.app

import android.content.Context
import com.portly.core.common.ApplicationScope
import com.portly.core.common.IoDispatcher
import com.portly.core.datastore.PortlySettingsDataStore
import com.portly.core.model.TransferEngine
import com.portly.network.NetworkMonitor
import com.portly.network.NetworkTransport
import com.portly.transfer.TransferReconcileWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkTransferCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val settingsDataStore: PortlySettingsDataStore,
    private val transferEngine: TransferEngine,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) {
    private var started = false

    fun start() {
        if (started) return
        started = true
        appScope.launch(ioDispatcher) {
            var wasUsable: Boolean? = null
            combine(
                networkMonitor.observeStatus(),
                settingsDataStore.settings,
            ) { net, settings ->
                val usable = net.isConnected && (!settings.wifiOnlyTransfers || net.transport == NetworkTransport.WIFI_OR_ETHERNET)
                usable to settings.wifiOnlyTransfers
            }.collect { (usable, wifiOnly) ->
                if (wasUsable == true && !usable) {
                    transferEngine.pauseAllForNetworkLoss()
                    TransferReconcileWorker.schedule(context, wifiOnly = wifiOnly)
                } else if (wasUsable == false && usable) {
                    transferEngine.resumeNetworkPaused()
                }
                wasUsable = usable
            }
        }
    }
}
