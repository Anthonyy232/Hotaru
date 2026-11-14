package com.anthonyla.hotaru.application

import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.domain.ServerVersion

sealed interface AppError {
    data class Network(val message: String?) : AppError
    data object Unauthorized : AppError
    data object QuickConnectExpired : AppError
    data object Forbidden : AppError
    data object NotFound : AppError
    data class ServerIncompatible(val found: ServerVersion, val minimum: ServerVersion) : AppError
    data class PlaybackUnsupported(val message: String) : AppError
    data class InvalidInput(val message: String) : AppError
}

sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value
}

fun <T> T.success(): Outcome<T> = Outcome.Success(this)
fun AppError.failure(): Outcome<Nothing> = Outcome.Failure(this)

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Success -> transform(value)
    is Outcome.Failure -> this
}

inline fun <T> Outcome<T>.onSuccess(block: (T) -> Unit): Outcome<T> { if (this is Outcome.Success) block(value); return this }
inline fun <T> Outcome<T>.onFailure(block: (AppError) -> Unit): Outcome<T> { if (this is Outcome.Failure) block(error); return this }

fun Outcome<*>.logFailure(logger: Logger, tag: String, what: String) {
    onFailure { logger.warn(tag, { "$what failed: $it" }) }
}
