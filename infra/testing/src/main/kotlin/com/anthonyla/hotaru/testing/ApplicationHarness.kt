package com.anthonyla.hotaru.testing

import com.anthonyla.hotaru.application.auth.QuickConnect
import com.anthonyla.hotaru.application.auth.RestoreSession
import com.anthonyla.hotaru.application.auth.SignInWithPassword
import com.anthonyla.hotaru.application.library.GetItemDetail
import com.anthonyla.hotaru.application.library.LoadHome
import com.anthonyla.hotaru.application.library.LoadItemDetail
import com.anthonyla.hotaru.application.library.LoadViews
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.playback.PlaybackCoordinator
import com.anthonyla.hotaru.application.playback.PreparePlayback
import com.anthonyla.hotaru.application.realtime.RealtimeCoordinator
import com.anthonyla.hotaru.application.servers.AddServer
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.application.userdata.SetFavorite
import com.anthonyla.hotaru.application.userdata.SetPlayed
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.Server
import kotlinx.coroutines.CoroutineScope

class ApplicationHarness(scope: CoroutineScope) {
    val connections = FakeServerConnectionFactory()
    val fake = connections.server
    private val servers = InMemoryServerStore()
    val accounts = InMemoryAccountStore()
    val secure = InMemorySecureStore()
    val cache = InMemoryItemCache()
    val settings = InMemorySettingsStore()
    val clock = FakeClock()
    private val capabilities = FakeCapabilitiesProvider()
    val players = FakePlayerFactory()
    val stage = Stage(settings, scope)
    val userDataEvents = UserDataEvents(cache)

    val sessions = SessionManager(connections, servers, accounts, secure, clock, NoopLogger)
    val addServer = AddServer(connections, servers, clock)
    val signIn = SignInWithPassword(sessions, connections)
    val quickConnect = QuickConnect(sessions, connections)
    val restoreSession = RestoreSession(sessions, servers, accounts, clock)
    val loadHome = LoadHome(sessions)
    val loadViews = LoadViews(sessions)
    val itemDetail = GetItemDetail(sessions, cache, clock)
    val loadItemDetail = LoadItemDetail(sessions, itemDetail)
    val setFavorite = SetFavorite(sessions, userDataEvents)
    val setPlayed = SetPlayed(sessions, userDataEvents)
    val prepare = PreparePlayback(capabilities, settings)
    val playback = PlaybackCoordinator(sessions, prepare, players, stage, settings, scope, NoopLogger)
    val realtime = RealtimeCoordinator(sessions, playback, itemDetail, cache, userDataEvents, scope, NoopLogger)

    val active: ActiveSession get() = checkNotNull(sessions.active.value)

    suspend fun server(): Server = addServer("http://fake").getOrThrow()

    suspend fun signedIn(): Server = server().also { signIn(it, "Quest", "quest").getOrThrow() }
}

fun <T> Outcome<T>.getOrThrow(): T = when (this) {
    is Outcome.Success -> value
    is Outcome.Failure -> throw AssertionError("expected success, got $error")
}
