package com.anthonyla.hotaru.presentation.connect

import com.anthonyla.hotaru.application.auth.QuickConnect
import com.anthonyla.hotaru.application.auth.RestoreSession
import com.anthonyla.hotaru.application.auth.SignInWithPassword
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.PublicUser
import com.anthonyla.hotaru.application.ports.QuickConnectCode
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

data class SignInState(
    val server: Server,
    val users: List<PublicUser> = emptyList(),
    val canUseQuickConnect: Boolean = false,
    val quickConnectCode: QuickConnectCode? = null,
    val selectedUser: PublicUser? = null,
    val username: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val effectiveUsername: String get() = selectedUser?.name ?: username

    /** The code to show; null while the password form is shown. */
    val shownQuickConnectCode: QuickConnectCode? get() = quickConnectCode?.takeIf { !showPassword }
}

sealed interface SignInIntent {
    data object UseQuickConnect : SignInIntent
    data class SelectUser(val user: PublicUser) : SignInIntent
    data class UsernameChanged(val value: String) : SignInIntent
    data class PasswordChanged(val value: String) : SignInIntent
    data object UsePassword : SignInIntent
    data object Submit : SignInIntent
}

@AssistedInject
class SignInViewModel(
    @Assisted scope: CoroutineScope,
    @Assisted server: Server,
    connections: ServerConnectionFactory,
    private val quickConnect: QuickConnect,
    private val signIn: SignInWithPassword,
    private val restore: RestoreSession,
) : ViewModel<SignInState, SignInIntent>(SignInState(server), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope, server: Server): SignInViewModel
    }

    private var quickConnectJob: Job? = null

    init {
        launch { connections.publicServer(server.baseUrl).users().onSuccess { users -> update { it.copy(users = users) } } }
        launch {
            val enabled = quickConnect.enabled(server).getOrNull() == true
            update { it.copy(canUseQuickConnect = enabled) }
            if (enabled) beginQuickConnect() else update { it.copy(showPassword = true) }
        }
    }

    override fun onIntent(intent: SignInIntent) {
        when (intent) {
            SignInIntent.UseQuickConnect -> { update { it.copy(showPassword = false) }; beginQuickConnect() }
            is SignInIntent.SelectUser -> selectUser(intent.user)
            is SignInIntent.UsernameChanged -> update { it.copy(username = intent.value, selectedUser = null, error = null) }
            is SignInIntent.PasswordChanged -> update { it.copy(password = intent.value, error = null) }
            SignInIntent.UsePassword -> update { it.copy(showPassword = true) }
            SignInIntent.Submit -> submit()
        }
    }

    /** A user with a saved account here resumes it; any other, or a second tap on the selected one, goes to the password form. */
    private fun selectUser(user: PublicUser) {
        if (current.busy) return
        if (current.selectedUser?.id == user.id) {
            update { it.copy(selectedUser = null, error = null) }
            return
        }
        update { it.copy(selectedUser = user, showPassword = true, busy = true, error = null) }
        launch {
            when (restore(current.server.id, user.id)) {
                is Outcome.Success -> quickConnectJob?.cancel()
                is Outcome.Failure -> update { it.copy(busy = false) }
            }
        }
    }

    private fun beginQuickConnect() {
        quickConnectJob?.cancel()
        quickConnectJob = launch {
            when (val code = quickConnect.start(current.server)) {
                is Outcome.Success -> {
                    update { it.copy(quickConnectCode = code.value, error = null) }
                    quickConnect.await(current.server, code.value).onFailure { e ->
                        update { it.copy(quickConnectCode = null, error = e.userMessage()) }
                    }
                }
                is Outcome.Failure -> update { it.copy(showPassword = true, error = code.error.userMessage()) }
            }
        }
    }

    private fun submit() {
        if (current.busy) return
        update { it.copy(busy = true, error = null) }
        launch {
            when (val r = signIn(current.server, current.effectiveUsername, current.password)) {
                is Outcome.Success -> quickConnectJob?.cancel()
                is Outcome.Failure -> update { it.copy(busy = false, error = r.error.userMessage()) }
            }
        }
    }
}
