package com.portly.transfer

import com.portly.core.model.TransferEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {

    @Binds
    @Singleton
    abstract fun bindTransferStore(impl: RoomTransferStore): TransferStore

    @Binds
    @Singleton
    abstract fun bindLocalEndpointIo(impl: AndroidLocalEndpointIo): LocalEndpointIo

    @Binds
    @Singleton
    abstract fun bindTransferEngine(impl: PortlyTransferEngine): TransferEngine
}
