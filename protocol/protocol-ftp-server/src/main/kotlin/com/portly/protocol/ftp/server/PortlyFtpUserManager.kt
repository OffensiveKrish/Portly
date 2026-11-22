package com.portly.protocol.ftp.server

import com.portly.core.model.ServerConfig
import com.portly.core.model.ServerPermission
import com.portly.core.model.ServerUserAccount
import org.apache.ftpserver.ftplet.Authentication
import org.apache.ftpserver.ftplet.AuthenticationFailedException
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.ftplet.User
import org.apache.ftpserver.ftplet.UserManager
import org.apache.ftpserver.usermanager.AnonymousAuthentication
import org.apache.ftpserver.usermanager.UsernamePasswordAuthentication
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission
import org.apache.ftpserver.usermanager.impl.TransferRatePermission
import org.apache.ftpserver.usermanager.impl.WritePermission
import java.security.MessageDigest

internal class PortlyFtpUserManager(
    private val config: ServerConfig,
    private val resolvedPasswords: Map<String, String>,
) : UserManager {

    private val usersByName: Map<String, ServerUserAccount> =
        config.users.associateBy { it.username }

    fun permissionsFor(username: String): Set<ServerPermission> {
        val account = usersByName[username] ?: return if (!config.requireAuth) {
            setOf(ServerPermission.READ)
        } else {
            emptySet()
        }
        return account.permissions
    }

    override fun getUserByName(username: String): User? {
        val account = usersByName[username] ?: if (!config.requireAuth && username == "anonymous") {
            ServerUserAccount(
                username = "anonymous",
                isAnonymous = true,
                permissions = if (config.readOnly) {
                    setOf(ServerPermission.READ)
                } else {
                    setOf(ServerPermission.READ, ServerPermission.WRITE, ServerPermission.DELETE, ServerPermission.MKDIR)
                },
            )
        } else {
            return null
        }
        return toFtpUser(account)
    }

    override fun getAllUserNames(): Array<String> = usersByName.keys.toTypedArray()

    override fun delete(username: String) = Unit

    override fun save(user: User) = Unit

    override fun doesExist(username: String): Boolean = getUserByName(username) != null

    override fun authenticate(authentication: Authentication): User {
        return when (authentication) {
            is AnonymousAuthentication -> {
                val anon = usersByName["anonymous"]
                if (anon != null && anon.isAnonymous) {
                    toFtpUser(anon)
                } else if (!config.requireAuth) {
                    getUserByName("anonymous")!!
                } else {
                    throw AuthenticationFailedException("Anonymous login disabled")
                }
            }
            is UsernamePasswordAuthentication -> {
                val uname = authentication.username ?: throw AuthenticationFailedException("Missing username")
                val account = usersByName[uname] ?: throw AuthenticationFailedException("Unknown user")
                if (account.isAnonymous) return toFtpUser(account)
                val expected = resolvedPasswords[uname]
                    ?: throw AuthenticationFailedException("No credentials configured")
                val provided = authentication.password ?: ""
                if (!constantTimeEquals(expected, provided)) {
                    throw AuthenticationFailedException("Invalid credentials")
                }
                toFtpUser(account)
            }
            else -> throw AuthenticationFailedException("Unsupported authentication mechanism")
        }
    }

    override fun getAdminName(): String = config.users.firstOrNull()?.username ?: "admin"

    override fun isAdmin(username: String): Boolean = username == adminName

    private fun toFtpUser(account: ServerUserAccount): BaseUser {
        val authorities = mutableListOf<Authority>()
        val canWrite = !config.readOnly && (
            ServerPermission.WRITE in account.permissions ||
                ServerPermission.MKDIR in account.permissions ||
                ServerPermission.DELETE in account.permissions
            )
        if (canWrite) {
            authorities += WritePermission()
        }
        authorities += ConcurrentLoginPermission(config.maxConnections, config.maxConnectionsPerIp)
        authorities += TransferRatePermission(0, 0)
        return BaseUser().apply {
            name = account.username
            homeDirectory = "/"
            enabled = true
            maxIdleTime = 300
            this.authorities = authorities
        }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        return MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))
    }
}
