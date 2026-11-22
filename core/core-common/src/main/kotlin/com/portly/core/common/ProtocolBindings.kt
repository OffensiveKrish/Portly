package com.portly.core.common

import com.portly.core.model.ProtocolClientFactory
import com.portly.core.model.ProtocolServerFactory
import com.portly.core.model.ProtocolType
import com.portly.core.model.ServerType
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds

@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolClientKey(val value: ProtocolType)

@MapKey
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProtocolServerKey(val value: ServerType)

@Module
@InstallIn(SingletonComponent::class)
abstract class ProtocolRegistryMultibindsModule {

    @Multibinds
    abstract fun multibindProtocolClients(): Map<ProtocolType, ProtocolClientFactory>

    @Multibinds
    abstract fun multibindProtocolServers(): Map<ServerType, ProtocolServerFactory>
}
