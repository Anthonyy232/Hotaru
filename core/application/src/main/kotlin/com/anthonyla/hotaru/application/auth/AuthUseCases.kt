package com.anthonyla.hotaru.application.auth

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.flatMap
import com.anthonyla.hotaru.application.map
import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountStore
import com.anthonyla.hotaru.application.ports.QuickConnectCode
import com.anthonyla.hotaru.application.ports.QuickConnectState
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.UserId
import jakarta.inject.Inject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val QUICK_CONNECT_POLL_INTERVAL = 5.seconds

class SignInWithPassword @Inject constructor(private val sessions: SessionManager, private val connections: ServerConnectionFactory) {
    suspend operator fun invoke(server: Server, username: String, password: String): Outcome<ActiveSession> {
        if (username.isBlank()) return AppError.InvalidInput("Choose a user").failure()
        return connections.publicServer(server.baseUrl).authenticateByName(username.trim(), password).map { sessions.activate(server, it) }
    }
}

/**
 * Polls until the code is approved on another device or the server expires it
 * ([AppError.QuickConnectExpired]), treating network failures as still pending; cancel the coroutine running [await] to abort.
 */
class QuickConnect @Inject constructor(private val sessions: SessionManager, private val connections: ServerConnectionFactory) {
    suspend fun enabled(server: Server): Outcome<Boolean> = connections.publicServer(server.baseUrl).quickConnectEnabled()

    suspend fun start(server: Server): Outcome<QuickConnectCode> = connections.publicServer(server.baseUrl).quickConnectInitiate()

    suspend fun await(server: Server, code: QuickConnectCode): Outcome<ActiveSession> {
        val publicServer = connections.publicServer(server.baseUrl)
        while (true) {
            when (val state = publicServer.quickConnectState(code.secret)) {
                // Approving on a phone often means lifting the headset, which can drop its network for a while.
                is Outcome.Failure -> if (state.error !is AppError.Network) return state
                is Outcome.Success -> when (state.value) {
                    QuickConnectState.Pending -> Unit
                    QuickConnectState.Expired -> return AppError.QuickConnectExpired.failure()
                    QuickConnectState.Authorized -> return publicServer.authenticateWithQuickConnect(code.secret).map { sessions.activate(server, it) }
                }
            }
            delay(QUICK_CONNECT_POLL_INTERVAL)
        }
    }
}

class RestoreSession @Inject constructor(
    private val sessions: SessionManager,
    private val servers: ServerStore,
    private val accounts: AccountStore,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Outcome<ActiveSession> = restore(accounts.lastUsed())

    suspend operator fun invoke(serverId: ServerId, userId: UserId): Outcome<ActiveSession> = restore(accounts.get(serverId, userId))

    private suspend fun restore(stored: Account?): Outcome<ActiveSession> {
        stored ?: return AppError.Unauthorized.failure()
        val server = checkNotNull(servers.get(stored.serverId))
        return sessions.restore(server, stored).flatMap { active ->
            // Activating replaces the screen that asked, which cancels its scope; the check must still finish.
            withContext(NonCancellable) {
                val me = active.connection.account.currentUser().onSuccess { accounts.upsert(it, clock.now()) }
                // Only a rejected token fails: offline or erroring, the session stays so the
                // app recovers without a new sign-in once the server is back.
                if (me is Outcome.Failure && me.error is AppError.Unauthorized) me.error.failure() else active.success()
            }
        }
    }
}

class SignOut @Inject constructor(private val sessions: SessionManager) {
    suspend operator fun invoke(forgetAccount: Boolean) {
        val active = checkNotNull(sessions.active.value)
        // Signing out removes the initiating screen; its cancellation must not interrupt token cleanup or revocation.
        withContext(NonCancellable) {
            sessions.signOut(active, forgetAccount)
            // Switching user keeps the token for picking this account again; only forgetting it ends the server session.
            if (forgetAccount) active.connection.account.logout()
        }
    }
}
