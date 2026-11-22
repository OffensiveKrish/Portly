package com.portly.protocol.ftp.client

import com.portly.core.model.ProtocolError
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.net.SocketTimeoutException

internal object FtpClientErrorMapper {

    fun fromReplyCode(replyCode: Int, replyString: String?): ProtocolError {
        val detail = "FTP $replyCode: ${replyString?.trim().orEmpty()}".trim()
        return when (replyCode) {
            FTPReply.NOT_LOGGED_IN, 530 -> ProtocolError.AuthenticationFailed(
                userMessage = "FTP login failed.",
                nextStep = "Verify your username and password for this FTP server.",
                technicalDetail = detail,
                protocolCode = replyCode,
            )
            FTPReply.FILE_UNAVAILABLE, 550 -> ProtocolError.PathNotFound(
                userMessage = "Remote file or folder unavailable.",
                nextStep = "Check the path exists and your account has permission.",
                technicalDetail = detail,
                protocolCode = replyCode,
            )
            553, 551 -> ProtocolError.PermissionDenied(
                userMessage = "FTP server rejected file write or name.",
                nextStep = "Check folder write permissions and filename characters.",
                technicalDetail = detail,
                protocolCode = replyCode,
            )
            FTPReply.SERVICE_NOT_AVAILABLE, 421, 425, 426, 450, 451 -> ProtocolError.ConnectionFailed(
                userMessage = "FTP connection or data channel interrupted.",
                nextStep = "Try toggling Passive/Active mode in the connection profile and retry.",
                technicalDetail = detail,
                protocolCode = replyCode,
                isTransient = true,
            )
            500, 502, 504 -> ProtocolError.UnsupportedOperation(
                userMessage = "FTP server does not support this command.",
                nextStep = "Use an alternative action supported by this server.",
                technicalDetail = detail,
            )
            else -> ProtocolError.ProtocolFailure(
                userMessage = "FTP server returned error $replyCode.",
                technicalDetail = detail,
                protocolCode = replyCode,
                isTransient = replyCode in 400..499,
            )
        }
    }

    fun fromException(throwable: Throwable): ProtocolError = when (throwable) {
        is SocketTimeoutException -> ProtocolError.Timeout(
            technicalDetail = throwable.message,
        )
        is IOException -> ProtocolError.ConnectionFailed(
            userMessage = "Could not reach FTP server.",
            nextStep = "Check the host, port, firewall, and Passive/Active mode setting.",
            technicalDetail = throwable.message,
            isTransient = true,
        )
        else -> ProtocolError.ProtocolFailure(
            userMessage = throwable.message ?: "Unexpected FTP client error.",
            technicalDetail = throwable.javaClass.simpleName,
        )
    }
}
