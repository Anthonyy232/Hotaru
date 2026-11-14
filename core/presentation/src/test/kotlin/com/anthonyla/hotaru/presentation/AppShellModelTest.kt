package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.application.playback.PlaybackRequest
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.presentation.player.PlayerIntent
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class AppShellModelTest {
    @Test
    fun `a session change cancels its suspended tab bar load`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val shell = shell(h)
        shell.start()
        runCurrent()
        var cancelled = false
        h.fake.views = { try { awaitCancellation() } finally { cancelled = true } }
        val server = h.signedIn()
        runCurrent()

        SignOut(h.sessions)(forgetAccount = false)
        runCurrent()
        assertTrue(cancelled)
        assertTrue(shell.state.value.views.isEmpty())

        val replacement = listOf(Fixtures.views.last())
        h.fake.views = { replacement }
        h.signIn(server, "Quest", "quest").getOrThrow()
        runCurrent()
        assertEquals(replacement, shell.state.value.views)
    }

    @Test
    fun `signing in loads the libraries for the tab bar, or once the server is back`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val shell = shell(h)
        shell.start()
        h.signedIn()
        h.fake.failNetwork = true
        runCurrent()
        assertEquals(listOf<Route>(Route.Home), shell.backStack.stack.value.map { it.route })
        assertEquals(emptyList(), shell.state.value.views)
        h.fake.failNetwork = false
        h.fake.events.emit(ServerEvent.Connection(connected = true))
        runCurrent()
        assertEquals(Fixtures.views, shell.state.value.views)
    }

    @Test
    fun `switching user opens sign-in on the same server, forgetting the account opens the server list`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val shell = shell(h)
        shell.start()
        val server = h.signedIn()
        runCurrent()
        SignOut(h.sessions)(forgetAccount = false)
        runCurrent()
        assertEquals(listOf(Route.ServerPicker, Route.SignIn(server)), shell.backStack.stack.value.map { it.route })

        h.restoreSession().getOrThrow()
        SignOut(h.sessions)(forgetAccount = true)
        runCurrent()
        assertEquals(listOf<Route>(Route.ServerPicker), shell.backStack.stack.value.map { it.route })
    }

    @Test
    fun `a failed play shows a notice that dismisses itself`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val player = playerViewModel(h)
        val shell = shell(h, player)
        h.fake.failNetwork = true
        shell.handle(ScreenEffect.Play(Fixtures.auroraDrift))
        runCurrent()
        assertEquals("Can't play", shell.state.value.notices.single().header)
        advanceTimeBy(6.seconds); runCurrent()
        assertTrue(shell.state.value.notices.isEmpty())
    }

    @Test
    fun `a play while another starts is ignored`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val shell = shell(h)
        h.fake.playbackInfoDelay = 1.seconds
        shell.handle(ScreenEffect.Play(Fixtures.auroraDrift))
        runCurrent()
        shell.handle(ScreenEffect.Play(Fixtures.neonTide))
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(Fixtures.auroraDrift.id, h.playback.current.value?.plan?.item?.id)

        shell.handle(ScreenEffect.Play(Fixtures.neonTide))
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(Fixtures.neonTide.id, h.playback.current.value?.plan?.item?.id)
    }

    @Test
    fun `when the next item can't start, playback closes with a notice`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val shell = shell(h)
        shell.start()
        h.signedIn()
        runCurrent()
        h.playback.play(PlaybackRequest(Fixtures.episodes[0])).getOrThrow()
        runCurrent()

        h.fake.failNetwork = true
        h.players.last.state.value = PlayerState.Ended
        runCurrent()
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
        assertEquals("Can't play the next item", shell.state.value.notices.single().header)
    }

    @Test
    fun `back pops the stack while browsing and stops the player in the window or in immersive`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val player = playerViewModel(h)
        val shell = shell(h, player)
        shell.backStack.push(Route.Search)
        shell.back()
        assertEquals(listOf<Route>(Route.ServerPicker), shell.backStack.stack.value.map { it.route })

        shell.backStack.push(Route.Search)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        runCurrent()
        shell.back()
        runCurrent()
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
        assertEquals(2, shell.backStack.stack.value.size)

        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        player.onIntent(PlayerIntent.WatchInImmersive)
        runCurrent()
        assertEquals(StageMode.Immersive, h.stage.state.value.mode)
        assertFalse(player.state.value.canWatchInImmersive)
        assertTrue(player.state.value.canWatchInWindow)
        shell.back()
        runCurrent()
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
        assertTrue(h.players.last.released)
    }

    @Test
    fun `closing a host stops only the playback it shows, and a window opened over immersive takes it back`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val player = playerViewModel(h)
        val shell = shell(h, player)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        h.playback.setImmersive(true)

        shell.windowClosed()
        runCurrent()
        assertEquals(StageMode.Immersive, h.stage.state.value.mode)
        shell.windowOpened()
        runCurrent()
        assertEquals(StageMode.Window, h.stage.state.value.mode)
        shell.immersiveClosed()
        runCurrent()
        assertFalse(h.players.last.released)

        shell.windowClosed()
        runCurrent()
        assertTrue(h.players.last.released)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)

        h.playback.play(PlaybackRequest(Fixtures.glassOrbit3D)).getOrThrow()
        shell.immersiveClosed()
        runCurrent()
        assertTrue(h.players.last.released)
    }
}
