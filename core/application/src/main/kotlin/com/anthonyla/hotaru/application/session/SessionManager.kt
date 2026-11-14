package com.anthonyla.hotaru.application.session

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountStore
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.SecureStore
import com.anthonyla.hotaru.application.ports.ServerConnection
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.Session
import com.anthonyla.hotaru.domain.UserId
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

class ActiveSession internal constructor(
    val server: Server,
    val account: Account,
    connection: ServerConnection,
    onUnauthorized: suspend (ActiveSession) -> Unit,
) {
    val connection: ServerConnection = SessionConnection(connection) { onUnauthorized(this) }
}

sealed interface SessionEvent {
    data class SignedIn(val session: ActiveSession) : SessionEvent
    /** Without [forgotAccount] the user is switching to another account on [server]. */
    data class SignedOut(val server: Server, val forgotAccount: Boolean) : SessionEvent
    /** The server rejected our token. Presentation shows the sign-in flow once. */
    data class Unauthorized(val server: Server) : SessionEvent
}

/** The only holder of authenticated gateways. Tokens live only in [SecureStore]. */
@Singleton
class SessionManager @Inject constructor(
    private val connections: ServerConnectionFactory,
    private val servers: ServerStore,
    private val accounts: AccountStore,
    private val secureStore: SecureStore,
    private val clock: Clock,
    private val logger: Logger,
) {
    private val mutex = Mutex()
    private val _active = MutableStateFlow<ActiveSession?>(null)
    val active: StateFlow<ActiveSession?> = _active.asStateFlow()

    private val _events = MutableStateFlow<SessionEvent?>(null)
    val events: StateFlow<SessionEvent?> = _events.asStateFlow()

    suspend inline fun <T> withSession(block: (ActiveSession) -> Outcome<T>): Outcome<T> {
        val session = active.value ?: return AppError.Unauthorized.failure()
        return block(session)
    }

    suspend fun activate(server: Server, session: Session): ActiveSession = mutex.withLock {
        val now = clock.now()
        servers.upsert(server.copy(lastSeen = now))
        accounts.upsert(session.account, now)
        secureStore.write(tokenKey(server.id, session.account.userId), session.accessToken)
        signIn(server, session).also { logger.info(TAG) { "signed in on ${server.name}" } }
    }

    /** Rebuild a session from the stored token without a network call; validation happens in RestoreSession. */
    suspend fun restore(server: Server, account: Account): Outcome<ActiveSession> = mutex.withLock {
        val token = secureStore.read(tokenKey(server.id, account.userId)) ?: return AppError.Unauthorized.failure()
        signIn(server, Session(account, token)).success()
    }

    private fun signIn(server: Server, session: Session): ActiveSession =
        ActiveSession(server, session.account, connections.connect(server.baseUrl, session), ::onUnauthorized).also {
            _active.value = it
            _events.value = SessionEvent.SignedIn(it)
        }

    suspend fun signOut(session: ActiveSession, forgetAccount: Boolean): Unit = mutex.withLock {
        if (_active.value !== session) return
        if (forgetAccount) {
            secureStore.delete(tokenKey(session.server.id, session.account.userId))
            accounts.remove(session.server.id, session.account.userId)
        }
        _active.value = null
        _events.value = SessionEvent.SignedOut(session.server, forgetAccount)
    }

    /** The server answered 401 for [session]'s token. Ignored when a newer sign-in already replaced it. */
    private suspend fun onUnauthorized(session: ActiveSession): Unit = mutex.withLock {
        val current = _active.value?.takeIf { it === session } ?: return
        secureStore.delete(tokenKey(current.server.id, current.account.userId))
        _active.value = null
        _events.value = SessionEvent.Unauthorized(current.server)
    }

    companion object {
        private const val TAG = "SessionManager"
        private fun tokenKey(serverId: ServerId, userId: UserId) = "token:${serverId.value}:${userId.value}"
    }
}
