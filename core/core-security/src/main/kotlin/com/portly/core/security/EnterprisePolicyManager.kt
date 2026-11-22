package com.portly.core.security

import android.content.Context
import android.content.RestrictionsManager
import android.os.Bundle
import com.portly.core.model.EnterprisePolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads standard Android Enterprise Managed Configurations via [RestrictionsManager]
 * without altering or bypassing endpoint controls.
 */
@Singleton
class EnterprisePolicyManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun currentPolicy(): EnterprisePolicy {
        val rm = context.getSystemService(RestrictionsManager::class.java) ?: return EnterprisePolicy()
        val bundle = runCatching { rm.applicationRestrictions }.getOrNull()
        return fromBundle(bundle)
    }

    companion object {
        const val KEY_DISABLE_PLAIN_FTP = "disable_plain_ftp"
        const val KEY_DISABLE_SMB1_LEGACY = "disable_smb1_legacy"
        const val KEY_ALLOW_PUBLIC_SERVER_BIND = "allow_public_server_bind"
        const val KEY_REQUIRE_BIOMETRIC_LOCK = "require_biometric_lock"
        const val KEY_DISABLE_SERVERS = "disable_servers"
        const val KEY_DIAGNOSTIC_LOGGING_ENABLED = "diagnostic_logging_enabled"

        fun fromBundle(bundle: Bundle?): EnterprisePolicy {
            if (bundle == null || bundle.isEmpty) return EnterprisePolicy()
            val map = buildMap<String, Boolean?> {
                if (bundle.containsKey(KEY_DISABLE_PLAIN_FTP)) {
                    put(KEY_DISABLE_PLAIN_FTP, bundle.getBoolean(KEY_DISABLE_PLAIN_FTP, false))
                }
                if (bundle.containsKey(KEY_DISABLE_SMB1_LEGACY)) {
                    put(KEY_DISABLE_SMB1_LEGACY, bundle.getBoolean(KEY_DISABLE_SMB1_LEGACY, false))
                }
                if (bundle.containsKey(KEY_ALLOW_PUBLIC_SERVER_BIND)) {
                    put(KEY_ALLOW_PUBLIC_SERVER_BIND, bundle.getBoolean(KEY_ALLOW_PUBLIC_SERVER_BIND, true))
                }
                if (bundle.containsKey(KEY_REQUIRE_BIOMETRIC_LOCK)) {
                    put(KEY_REQUIRE_BIOMETRIC_LOCK, bundle.getBoolean(KEY_REQUIRE_BIOMETRIC_LOCK, false))
                }
                if (bundle.containsKey(KEY_DISABLE_SERVERS)) {
                    put(KEY_DISABLE_SERVERS, bundle.getBoolean(KEY_DISABLE_SERVERS, false))
                }
                if (bundle.containsKey(KEY_DIAGNOSTIC_LOGGING_ENABLED)) {
                    put(KEY_DIAGNOSTIC_LOGGING_ENABLED, bundle.getBoolean(KEY_DIAGNOSTIC_LOGGING_ENABLED, true))
                }
            }
            return fromMap(map)
        }

        fun fromMap(values: Map<String, Boolean?>): EnterprisePolicy = EnterprisePolicy(
            disablePlainFtp = values[KEY_DISABLE_PLAIN_FTP] ?: false,
            disableSmb1Legacy = values[KEY_DISABLE_SMB1_LEGACY] ?: false,
            allowPublicServerBind = values[KEY_ALLOW_PUBLIC_SERVER_BIND] ?: true,
            requireBiometricLock = values[KEY_REQUIRE_BIOMETRIC_LOCK] ?: false,
            disableServers = values[KEY_DISABLE_SERVERS] ?: false,
            diagnosticLoggingOverride = values[KEY_DIAGNOSTIC_LOGGING_ENABLED],
        )
    }
}
