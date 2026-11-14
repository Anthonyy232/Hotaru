package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.auth.RestoreSession
import com.anthonyla.hotaru.application.library.LoadViews
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.playback.PlaybackCoordinator
import com.anthonyla.hotaru.application.playback.PlaybackRequest
import com.anthonyla.hotaru.application.realtime.RealtimeCoordinator
import com.anthonyla.hotaru.application.realtime.RealtimeNotice
import com.anthonyla.hotaru.application.session.SessionEvent
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.presentation.player.PlayerIntent
import com.anthonyla.hotaru.presentation.player.PlayerViewModel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

data class ShellNotice(val header: String?, val text: String)

/** [views] are the signed-in user's libraries for the tab bar; empty while offline. */
data class ShellState(val notices: List<ShellNotice> = emptyList(), val views: List<LibraryView> = emptyList())

@Singleton
class AppShellModel @Inject constructor(
    private val scope: CoroutineScope,
    private val sessions: SessionManager,
    private val restore: RestoreSession,
    private val loadViews: LoadViews,
    private val playback: PlaybackCoordinator,
    private val realtime: RealtimeCoordinator,
    private val stage: Stage,
    private val player: PlayerViewModel,
) {
    /** Every session event replaces the whole stack, so no screen of the previous session survives. */
    val backStack = BackStack(scope, Route.ServerPicker)

    private val _state = MutableStateFlow(ShellState())
    val state: StateFlow<ShellState> = _state.asStateFlow()
    private var viewsJob: Job? = null
    private var starting: Job? = null

    fun start() {
        scope.launch { restore() }
        scope.launch {
            sessions.events.filterNotNull().collect { event ->
                viewsJob?.cancel()
                _state.update { it.copy(views = emptyList()) }
                when (event) {
                    is SessionEvent.SignedIn -> { backStack.replaceAll(Route.Home); refreshViews() }
                    is SessionEvent.SignedOut -> {
                        backStack.replaceAll(Route.ServerPicker)
                        if (!event.forgotAccount) handle(ScreenEffect.SignIn(event.server))
                    }
                    is SessionEvent.Unauthorized -> { backStack.replaceAll(Route.ServerPicker); handle(ScreenEffect.SignIn(event.server)) }
                }
            }
        }
        scope.launch {
            realtime.notices.collect { n ->
                when (n) {
                    is RealtimeNotice.Message -> notice(n.header, n.text)
                    RealtimeNotice.ServerRestarting -> notice(null, "Server is restarting")
                    RealtimeNotice.LibraryChanged -> refreshViews()
                    RealtimeNotice.Connected -> if (_state.value.views.isEmpty()) refreshViews()
                }
            }
        }
        scope.launch { playback.nextFailed.collect { notice("Can't play the next item", it.userMessage()) } }
    }

    fun handle(effect: ScreenEffect) {
        when (effect) {
            is ScreenEffect.OpenItem -> backStack.push(Route.Item(effect.id))
            is ScreenEffect.SignIn -> backStack.push(Route.SignIn(effect.server))
            // A Play while one starts is a double tap.
            is ScreenEffect.Play -> if (starting?.isActive != true) starting = scope.launch {
                playback.play(PlaybackRequest(effect.item, startAt = if (effect.fromStart) Ticks.ZERO else null), effect.queue)
                    .onFailure { notice("Can't play", it.userMessage()) }
            }
        }
    }

    /** Controller B / system Back: the player while something plays, else the back stack; it never leaves the app. */
    fun back() {
        if (stage.state.value.mode == StageMode.Browse) backStack.pop() else player.onIntent(PlayerIntent.Back)
    }

    /** A window opened over immersive playback (from the app library) takes the playback back into the window. */
    fun windowOpened() {
        if (stage.state.value.mode == StageMode.Immersive) player.onIntent(PlayerIntent.ExitImmersive)
    }

    /** The user closed the window; nothing keeps playing without a picture. Handing over to immersive is not a close. */
    fun windowClosed() {
        if (stage.state.value.mode != StageMode.Immersive) player.onIntent(PlayerIntent.Stop)
    }

    /** The immersive app was quit from the system; returning to the window is not a quit. */
    fun immersiveClosed() {
        if (stage.state.value.mode == StageMode.Immersive) player.onIntent(PlayerIntent.Stop)
    }

    private fun refreshViews() {
        viewsJob?.cancel()
        viewsJob = scope.launch { loadViews().onSuccess { views -> _state.update { it.copy(views = views) } } }
    }

    private fun notice(header: String?, text: String) {
        val n = ShellNotice(header, text)
        _state.update { it.copy(notices = (it.notices + n).takeLast(3)) }
        scope.launch {
            delay(6.seconds)
            _state.update { st -> st.copy(notices = st.notices - n) }
        }
    }
}
