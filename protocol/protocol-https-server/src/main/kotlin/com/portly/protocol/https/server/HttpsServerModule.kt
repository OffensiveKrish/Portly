package com.portly.protocol.https.server

import android.content.Context
import com.portly.core.common.ProtocolServerKey
import com.portly.core.database.CredentialVault
import com.portly.core.model.ProtocolServerFactory
import com.portly.core.model.ServerType
import com.portly.core.security.CertificateManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
object HttpsServerModule {

    @Provides
    @IntoMap
    @ProtocolServerKey(ServerType.HTTPS)
    fun provideHttpsServerFactory(
        @ApplicationContext context: Context,
        credentialVault: CredentialVault,
        certificateManager: CertificateManager,
    ): ProtocolServerFactory = ProtocolServerFactory {
        KtorHttpsProtocolServer(
            context = context,
            credentialVault = credentialVault,
            certificateManager = certificateManager,
        )
    }
}
