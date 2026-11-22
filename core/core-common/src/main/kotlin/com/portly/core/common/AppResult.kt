package com.portly.core.common

import com.portly.core.model.ProtocolError

sealed interface AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>
    data class Failure(val error: ProtocolError) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> = when (this) {
    is AppResult.Success -> AppResult.Success(transform(data))
    is AppResult.Failure -> this
}

inline fun <T> AppResult<T>.getOrElse(fallback: (ProtocolError) -> T): T = when (this) {
    is AppResult.Success -> data
    is AppResult.Failure -> fallback(error)
}

class ProtocolException(val protocolError: ProtocolError, cause: Throwable? = null) :
    Exception(protocolError.userMessage, cause)
