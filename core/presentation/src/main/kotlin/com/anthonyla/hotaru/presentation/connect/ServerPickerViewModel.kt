package com.anthonyla.hotaru.presentation.connect

import com.anthonyla.hotaru.application.auth.RestoreSession
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountStore
import com.anthonyla.hotaru.application.ports.DiscoveredServer
import com.anthonyla.hotaru.application.ports.NetworkDiscovery
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.servers.AddServer
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.UserId
import com.anthonyla.hotaru.presentation.ScreenEffect
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope

data class ServerPickerState(
    val servers: List<Server> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val discovered: List<DiscoveredServer> = emptyList(),
    val address: String = "",
    val connecting: Boolean = false,
    val restoring: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean get() = connecting || restoring

    fun lastAccountFor(server: Server): Account? = accounts.firstOrNull { it.serverId == server.id }
}

sealed interface ServerPickerIntent {
    data class AddressChanged(val value: String) : ServerPickerIntent
    data object Connect : ServerPickerIntent
    data class ConnectDiscovered(val server: DiscoveredServer) : ServerPickerIntent
    data class Pick(val server: Server) : ServerPickerIntent
    data class Remove(val server: Server) : ServerPickerIntent
}

@AssistedInject
class ServerPickerViewModel(
    @Assisted scope: CoroutineScope,
    servers: ServerStore,
    accounts: AccountStore,
    discovery: NetworkDiscovery,
    private val add: AddServer,
    private val restore: RestoreSession,
    private val sessions: SessionManager,
) : ViewModel<ServerPickerState, ServerPickerIntent>(ServerPickerState(), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): ServerPickerViewModel
    }

    init {
        launch { servers.observeAll().collect { s -> update { it.copy(servers = s) } } }
        launch { accounts.observeAll().collect { a -> update { it.copy(accounts = a) } } }
        launch {
            discovery.discover().collect { found ->
                update { it.copy(discovered = (it.discovered + found).distinctBy(DiscoveredServer::id)) }
            }
        }
    }

    override fun onIntent(intent: ServerPickerIntent) {
        when (intent) {
            is ServerPickerIntent.AddressChanged -> update { it.copy(address = intent.value, error = null) }
            ServerPickerIntent.Connect -> connect(current.address)
            is ServerPickerIntent.ConnectDiscovered -> connect(intent.server.address)
            is ServerPickerIntent.Pick -> {
                val last = current.lastAccountFor(intent.server)
                if (last != null) resume(intent.server, last.userId) else emit(ScreenEffect.SignIn(intent.server))
            }
            is ServerPickerIntent.Remove -> launch { sessions.removeServer(intent.server) }
        }
    }

    private fun connect(address: String) {
        if (current.busy) return
        update { it.copy(connecting = true, error = null) }
        launch {
            when (val result = add(address)) {
                is Outcome.Success -> {
                    update { it.copy(connecting = false) }
                    emit(ScreenEffect.SignIn(result.value))
                }
                is Outcome.Failure -> update { it.copy(connecting = false, error = result.error.userMessage()) }
            }
        }
    }

    private fun resume(server: Server, userId: UserId) {
        if (current.busy) return
        update { it.copy(restoring = true, error = null) }
        launch {
            restore(server.id, userId).onFailure { emit(ScreenEffect.SignIn(server)) }
            update { it.copy(restoring = false) }
        }
    }
}
