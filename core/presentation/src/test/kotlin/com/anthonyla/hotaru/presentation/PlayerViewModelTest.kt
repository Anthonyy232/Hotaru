package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.playback.PlaybackRequest
import com.anthonyla.hotaru.application.ports.PlaybackFailure
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.domain.Environment
import com.anthonyla.hotaru.domain.ScreenGeometry
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.ticks
import com.anthonyla.hotaru.presentation.player.PlayerIntent
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @Test
    fun `player controls speak in fractions of the duration and the screen range`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        player.duration.value = 100.minutes.ticks
        runCurrent()
        assertEquals(listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f), vm.state.value.chapterFractions)
        assertEquals("Off", vm.state.value.subtitleOptions.first { it.selected }.label)

        vm.onIntent(PlayerIntent.Scrub(0.25f))
        assertEquals("25:00", vm.state.value.positionText)
        assertEquals("Chapter 2 · 25:00", vm.state.value.scrubLabel)
        vm.onIntent(PlayerIntent.SeekTo(0.25f))
        assertEquals(25.minutes.ticks, player.position.value)

        vm.onIntent(PlayerIntent.ResizeScreen(1f))
        runCurrent()
        assertEquals(ScreenGeometry.MAX_WIDTH_METERS, h.stage.state.value.screen.widthMeters)
        assertEquals(1f, vm.state.value.screenSizeFraction)
    }

    @Test
    fun `cinema settings exist only in immersive, apply now, carry to the next item and close on the way back to the window`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        runCurrent()
        assertFalse(vm.state.value.canAdjustCinema)
        vm.onIntent(PlayerIntent.WatchInImmersive)
        runCurrent()
        assertTrue(vm.state.value.canAdjustScreen)
        vm.onIntent(PlayerIntent.ToggleCinemaSettings)
        vm.onIntent(PlayerIntent.ScreenResized(6.5f))
        vm.onIntent(PlayerIntent.SetEnvironment(Environment.Dimmed))
        runCurrent()
        assertEquals(6.5f, h.stage.state.value.screen.widthMeters)

        vm.onIntent(PlayerIntent.Back)
        assertFalse(vm.state.value.showCinemaSettings)
        assertTrue(vm.state.value.controlsVisible)

        h.playback.play(PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        runCurrent()
        val stage = h.stage.state.value
        assertEquals(StageMode.Immersive, stage.mode)
        assertEquals(6.5f, stage.screen.widthMeters)
        assertEquals(Environment.Dimmed, stage.environment)

        vm.onIntent(PlayerIntent.ToggleCinemaSettings)
        vm.onIntent(PlayerIntent.ExitImmersive)
        runCurrent()
        assertFalse(vm.state.value.canAdjustCinema)
        assertFalse(vm.state.value.showCinemaSettings)
    }

    @Test
    fun `shown controls hide once playback has run five seconds untouched, with nothing open or held`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        player.state.value = PlayerState.Buffering
        fun after(seconds: Int): Boolean { advanceTimeBy(seconds.seconds); runCurrent(); return vm.state.value.controlsVisible }

        assertTrue(after(10))
        player.state.value = PlayerState.Playing
        assertTrue(after(4))
        vm.onIntent(PlayerIntent.SeekBy(10.seconds))
        assertTrue(after(4))
        assertFalse(after(1))

        vm.onIntent(PlayerIntent.ToggleTrackPicker)
        assertTrue(after(10))
        vm.onIntent(PlayerIntent.Back)
        assertFalse(after(5))

        vm.onIntent(PlayerIntent.ToggleControls)
        vm.onIntent(PlayerIntent.Scrub(0.5f))
        assertTrue(after(10))
        vm.onIntent(PlayerIntent.Scrub(null))
        assertFalse(after(5))

        vm.onIntent(PlayerIntent.WatchInImmersive)
        vm.onIntent(PlayerIntent.ToggleControls)
        runCurrent()
        vm.onIntent(PlayerIntent.ScreenHeld(true))
        assertTrue(after(10))
        vm.onIntent(PlayerIntent.ScreenHeld(false))
        assertTrue(after(4))
        assertFalse(after(1))
    }

    @Test
    fun `a player error brings up the controls with a readable reason, and play retries`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        runCurrent()
        vm.onIntent(PlayerIntent.ToggleControls)

        player.state.value = PlayerState.Error(PlaybackFailure.Network)
        runCurrent()
        assertTrue(vm.state.value.controlsVisible)
        assertEquals("Lost the connection to the server.", vm.state.value.error)

        vm.onIntent(PlayerIntent.TogglePlayPause)
        runCurrent()
        assertTrue(player.released)
        assertTrue(vm.state.value.isPlaying)
        assertNull(vm.state.value.error)
    }

    @Test
    fun `a server stream offers every track of its source and marks the ones it plays`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.neonTide, subtitles = SubtitleChoice.Stream(4))).getOrThrow()
        runCurrent()
        assertEquals(listOf("English - EAC3 5.1", "French - AAC"), vm.state.value.audioOptions.map { it.label })
        assertEquals("English (PGS)", vm.state.value.subtitleOptions.single { it.selected }.label)

        vm.onIntent(vm.state.value.audioOptions.last().intent)
        runCurrent()
        assertEquals(2, h.players.last.plan.audioStreamIndex)
        assertEquals("French - AAC", vm.state.value.audioOptions.single { it.selected }.label)
        assertEquals("English (PGS)", vm.state.value.subtitleOptions.single { it.selected }.label)
    }

    @Test
    fun `the player shows loading while the next episode starts, and closing doesn't wait for it`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        val (e0, e1) = Fixtures.episodes
        h.playback.play(PlaybackRequest(e0)).getOrThrow()
        runCurrent()
        h.fake.playbackInfoDelay = 2.seconds

        h.players.last.state.value = PlayerState.Ended
        runCurrent()
        assertTrue(vm.state.value.isBuffering)
        advanceTimeBy(2.seconds); runCurrent()
        assertEquals(e1.id, vm.state.value.plan?.item?.id)
        assertTrue(vm.state.value.isPlaying)

        h.players.last.state.value = PlayerState.Ended
        runCurrent()
        vm.onIntent(PlayerIntent.Stop)
        runCurrent()
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
        advanceTimeBy(2.seconds); runCurrent()
        assertNull(h.playback.current.value)
    }

    @Test
    fun `a chosen speed and subtitle delay last through a restart and the next episode, and a new playback starts without them`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        val vm = playerViewModel(h)
        val (e0, e1) = Fixtures.episodes
        h.playback.play(PlaybackRequest(e0)).getOrThrow()
        runCurrent()
        vm.onIntent(PlayerIntent.SetSpeed(1.5f))
        vm.onIntent(PlayerIntent.ShiftSubtitles((-250).milliseconds))
        assertEquals(1.5f, h.players.last.speed)

        h.playback.retry().getOrThrow()
        runCurrent()
        assertEquals(1.5f, h.players.last.speed)

        h.players.last.state.value = PlayerState.Ended
        runCurrent()
        assertEquals(e1.id, h.players.last.plan.item.id)
        assertEquals(1.5f, h.players.last.speed)
        assertEquals((-250).milliseconds, h.players.last.subtitleDelay)

        vm.onIntent(PlayerIntent.Stop)
        runCurrent()
        h.playback.play(PlaybackRequest(e0)).getOrThrow()
        runCurrent()
        assertEquals(1f, h.players.last.speed)
        assertEquals(1f, vm.state.value.speed)
        assertEquals(Duration.ZERO, h.players.last.subtitleDelay)
    }

    @Test
    fun `a failed track change shows why until a change works`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        runCurrent()

        h.fake.failNetwork = true
        vm.onIntent(PlayerIntent.SelectAudio(2))
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.error)

        h.fake.failNetwork = false
        vm.onIntent(PlayerIntent.SelectAudio(2))
        runCurrent()
        assertNull(vm.state.value.error)
    }

    @Test
    fun `taking the headset off pauses playing or buffering video, and putting it on resumes only what that paused`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = playerViewModel(h)
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        runCurrent()

        vm.onIntent(PlayerIntent.HeadsetWorn(false))
        assertIs<PlayerState.Paused>(player.state.value)
        vm.onIntent(PlayerIntent.HeadsetWorn(true))
        assertIs<PlayerState.Playing>(player.state.value)

        player.pause()
        runCurrent()
        vm.onIntent(PlayerIntent.HeadsetWorn(false))
        vm.onIntent(PlayerIntent.HeadsetWorn(true))
        assertIs<PlayerState.Paused>(player.state.value)

        player.state.value = PlayerState.Buffering
        runCurrent()
        vm.onIntent(PlayerIntent.HeadsetWorn(false))
        vm.onIntent(PlayerIntent.HeadsetWorn(true))
        assertIs<PlayerState.Playing>(player.state.value)
    }
}
