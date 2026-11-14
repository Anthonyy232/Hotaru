package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.AppError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** No framework base class, so the same view models run on every host and in plain JVM tests. */
abstract class ViewModel<State, Intent>(initial: State, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ScreenEffect>(extraBufferCapacity = 16)
    val effects: SharedFlow<ScreenEffect> = _effects.asSharedFlow()

    protected val current: State get() = _state.value

    protected fun update(transform: (State) -> State) = _state.update(transform)
    protected fun emit(effect: ScreenEffect) { _effects.tryEmit(effect) }
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    abstract fun onIntent(intent: Intent)
}

fun AppError.userMessage(): String = when (this) {
    is AppError.Network -> "Can't reach the server" + (message?.let { ": $it" } ?: "")
    AppError.Unauthorized -> "Sign-in failed. Check the name and password."
    AppError.QuickConnectExpired -> "The Quick Connect code expired. Use Quick Connect again for a new code."
    AppError.Forbidden -> "You don't have access to this."
    AppError.NotFound -> "That item is no longer on the server."
    is AppError.ServerIncompatible -> "This server is too old. Hotaru needs Jellyfin $minimum or newer (found $found)."
    is AppError.PlaybackUnsupported -> message
    is AppError.InvalidInput -> message
}
