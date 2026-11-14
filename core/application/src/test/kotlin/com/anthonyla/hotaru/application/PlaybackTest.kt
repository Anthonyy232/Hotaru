package com.anthonyla.hotaru.application

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.application.playback.PlaybackRequest
import com.anthonyla.hotaru.application.ports.AudioMode
import com.anthonyla.hotaru.application.ports.PlaybackFailure
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.SegmentAction
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.domain.PlayMethod
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.StereoLayout
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.ticks
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackTest {

    @Test
    fun `direct play plan resumes, maps 3d and side-loads external subtitles`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val plan = h.prepare(h.active, PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        assertEquals(PlayMethod.DirectPlay, plan.method)
        assertEquals(40.minutes.ticks, plan.startAt)
        assertEquals(1, plan.audioStreamIndex)
        assertNull(plan.subtitleStreamIndex)
        assertEquals(1, plan.externalSubtitles.size)
        assertTrue(plan.externalSubtitles.single().url.value.startsWith("http://fake/"))

        val plan3d = h.prepare(h.active, PlaybackRequest(Fixtures.glassOrbit3D)).getOrThrow()
        assertEquals(StereoLayout.SideBySide, plan3d.stereo)
        assertEquals(Ticks.ZERO, plan3d.startAt)
    }

    @Test
    fun `audio mode decides channels and only server stereo asks for a downmix`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.prepare(h.active, PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        var seen = assertNotNull(h.fake.lastPlaybackInfo)
        assertEquals(8, seen.capabilities.maxAudioChannels)

        h.settings.update { it.copy(audioMode = AudioMode.ServerStereoAac, forceSdr = true) }
        h.prepare(h.active, PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        seen = assertNotNull(h.fake.lastPlaybackInfo)
        assertEquals(2, seen.capabilities.maxAudioChannels)
        assertTrue(seen.capabilities.videoCodecs.all { it.hdr.isEmpty() })
    }

    @Test
    fun `a server stream plays the opaque TranscodingUrl over HLS`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        val plan = h.prepare(h.active, PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        assertEquals(PlayMethod.DirectStream, plan.method)
        assertTrue(plan.streamUrl.value.startsWith("http://fake/videos/"))
        assertTrue(plan.isHls)
    }

    @Test
    fun `play session reports start, progress cadence, a pause at once and stop`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        assertEquals(40.minutes.ticks, player.plan.startAt)
        assertTrue(player.headers.containsKey("Authorization"))
        runCurrent()
        assertEquals("start", h.fake.reports.first().first)

        advanceTimeBy(3.seconds); player.advance(3.seconds); runCurrent()
        advanceTimeBy(3.seconds); player.advance(3.seconds); runCurrent()
        assertEquals(2, h.fake.reports.count { it.first == "progress" })

        player.pause(); runCurrent()
        assertEquals(3, h.fake.reports.count { it.first == "progress" })
        advanceTimeBy(15.seconds); runCurrent()
        assertEquals(4, h.fake.reports.count { it.first == "progress" })
        assertTrue(h.fake.reports.last().second.isPaused)
        assertTrue(h.fake.pings.isEmpty(), "direct play never pings")

        h.playback.stop()
        assertTrue(player.released)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
        assertNull(h.playback.current.value)
        assertEquals("progress", h.fake.reports.last().first, "closing doesn't wait for the server")
        runCurrent()
        assertEquals("stopped", h.fake.reports.last().first)
    }

    @Test
    fun `a rejected playback progress report ends the session`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        runCurrent()
        h.fake.validTokens.clear()

        advanceTimeBy(3.seconds); runCurrent()

        assertNull(h.sessions.active.value)
        assertTrue(h.secure.map.isEmpty())
        assertTrue(h.players.last.released)
        assertNull(h.playback.current.value)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
    }

    @Test
    fun `account changes stop their player without stopping replacement playback`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        runCurrent()
        val first = h.players.last

        h.signIn(server, "Quest", "quest").getOrThrow()
        runCurrent()
        assertTrue(first.released)
        assertNull(h.playback.current.value)

        h.playback.play(PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        h.signIn(server, "Quest", "quest").getOrThrow()
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val replacement = h.players.last
        runCurrent()
        assertFalse(replacement.released)

        SignOut(h.sessions)(forgetAccount = false)
        runCurrent()
        assertTrue(replacement.released)
        assertNull(h.playback.current.value)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
    }

    @Test
    fun `2d plays in the window and keeps its player across immersive, 3d only plays immersive`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val window = VideoRenderTarget("window", 1280, 720)
        val cinema = VideoRenderTarget("cinema", 1920, 1080)

        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        assertEquals(StageMode.Window, h.stage.state.value.mode)
        h.stage.surfaceChanged(window)
        assertEquals(window, player.target)

        h.playback.setImmersive(true)
        assertEquals(StageMode.Immersive, h.stage.state.value.mode)
        h.stage.surfaceChanged(cinema)
        h.stage.surfaceDestroyed(window)
        assertEquals(cinema, player.target)
        assertSame(player, h.players.last)
        assertFalse(player.released)

        h.stage.surfaceDestroyed(cinema)
        h.playback.setImmersive(false)
        h.stage.surfaceChanged(window)
        assertEquals(StageMode.Window, h.stage.state.value.mode)
        assertEquals(window, player.target)
        assertSame(player, h.players.last)

        h.playback.play(PlaybackRequest(Fixtures.glassOrbit3D)).getOrThrow()
        assertEquals(StageMode.Immersive, h.stage.state.value.mode)
        h.stage.surfaceDestroyed(window)
        h.stage.surfaceChanged(cinema)
        assertEquals(cinema, h.players.last.target)
        assertEquals(window, player.target, "the released player gets no more surfaces")
        h.playback.setImmersive(false)
        assertTrue(h.players.last.released)
        assertNull(h.playback.current.value)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)
    }

    @Test
    fun `paused server streams are pinged so the transcode stays alive`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        h.playback.play(PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        runCurrent()
        h.players.last.pause()
        advanceTimeBy(3.seconds); runCurrent()
        assertEquals(1, h.fake.pings.size)
        advanceTimeBy(15.seconds); runCurrent()
        assertEquals(2, h.fake.pings.size)
    }

    @Test
    fun `intro segments follow the configured action`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val episode = Fixtures.episodes[1]
        h.signedIn()

        var session = h.playback.play(PlaybackRequest(episode)).getOrThrow()
        var player = h.players.last
        runCurrent()
        assertNull(session.activeSegment.value)
        player.position.value = 45.seconds.ticks; runCurrent()
        assertEquals(90.seconds.ticks, assertNotNull(session.activeSegment.value).end)
        session.skipActiveSegment()
        assertEquals(90.seconds.ticks, player.position.value)
        assertNull(session.activeSegment.value)
        h.playback.stop()

        h.settings.update { it.copy(introAction = SegmentAction.Skip) }
        session = h.playback.play(PlaybackRequest(episode)).getOrThrow()
        player = h.players.last
        runCurrent()
        player.position.value = 45.seconds.ticks; runCurrent()
        assertNull(session.activeSegment.value)
        assertEquals(90.seconds.ticks, player.position.value)
    }

    @Test
    fun `autoplay follows the queue, then the series, then goes back to browsing`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val (e0, e1, e2) = Fixtures.episodes

        fun endPlayback() { h.players.last.state.value = PlayerState.Ended; runCurrent() }

        h.playback.play(PlaybackRequest(e0), queue = listOf(e0, e2)).getOrThrow()
        val first = h.players.last
        runCurrent()
        endPlayback()
        assertEquals(e2.id, assertNotNull(h.playback.current.value).plan.item.id)
        assertTrue(first.released)
        assertEquals(StageMode.Window, h.stage.state.value.mode)

        first.state.value = PlayerState.Paused; runCurrent()
        first.state.value = PlayerState.Ended; runCurrent()
        assertEquals(e2.id, assertNotNull(h.playback.current.value).plan.item.id)

        endPlayback()
        assertNull(h.playback.current.value)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)

        h.playback.play(PlaybackRequest(e0)).getOrThrow()
        endPlayback()
        assertEquals(e1.id, assertNotNull(h.playback.current.value).plan.item.id)
        h.settings.update { it.copy(autoplayNext = false) }
        endPlayback()
        assertNull(h.playback.current.value)
        assertEquals(StageMode.Browse, h.stage.state.value.mode)

        h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        h.playback.next()
        assertEquals(Fixtures.auroraDrift.id, assertNotNull(h.playback.current.value).plan.item.id, "a remote Next with nothing to follow keeps playing")
        assertFalse(h.players.last.released)
    }

    @Test
    fun `a plan lists the streams its media carries`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val direct = h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow().plan
        assertEquals(listOf(1, 2), direct.deliveredAudio.map { it.index })
        assertEquals(listOf(4), direct.streamSubtitles.map { it.index })
        h.fake.directPlay = false
        val stream = h.playback.play(PlaybackRequest(Fixtures.auroraDrift, audioStreamIndex = 2)).getOrThrow().plan
        assertEquals(listOf(2), stream.deliveredAudio.map { it.index })
    }

    @Test
    fun `the plan's streams are selected once the player lists its tracks`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.playback.play(PlaybackRequest(Fixtures.auroraDrift, subtitles = SubtitleChoice.Stream(3))).getOrThrow()
        runCurrent()
        assertEquals("a1", h.players.last.selectedAudio)
        assertEquals("s3", h.players.last.selectedSubtitle)
    }

    @Test
    fun `direct play switches tracks in the player and keeps the other selection`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val session = h.playback.play(PlaybackRequest(Fixtures.auroraDrift)).getOrThrow()
        val player = h.players.last
        h.playback.selectSubtitle(3).getOrThrow()
        h.playback.selectAudio(2).getOrThrow()
        assertSame(player, h.players.last)
        assertEquals("a2", player.selectedAudio)
        assertEquals("s3", player.selectedSubtitle)
        assertEquals(2, session.selectedAudio)
        assertEquals(3, session.selectedSubtitle)
        h.playback.selectSubtitle(null).getOrThrow()
        assertNull(player.selectedSubtitle)
    }

    @Test
    fun `retry after a player error starts the item over where it stopped with the same tracks`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        h.playback.play(PlaybackRequest(Fixtures.neonTide, audioStreamIndex = 2, subtitles = SubtitleChoice.Stream(3))).getOrThrow()
        val failed = h.players.last
        failed.position.value = 7.minutes.ticks
        failed.state.value = PlayerState.Error(PlaybackFailure.Network)

        h.playback.retry().getOrThrow()
        val retried = h.players.last
        assertTrue(failed.released)
        assertEquals(7.minutes.ticks, retried.plan.startAt)
        assertEquals(2, retried.plan.audioStreamIndex)
        assertEquals(3, retried.plan.subtitleStreamIndex)
        assertEquals(PlayerState.Playing, retried.state.value)
    }

    @Test
    fun `track change on a server stream re-prepares at the current position`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.directPlay = false
        h.playback.play(PlaybackRequest(Fixtures.neonTide)).getOrThrow()
        val first = h.players.last
        runCurrent()
        first.position.value = 12.minutes.ticks
        h.playback.selectSubtitle(3).getOrThrow()
        val second = h.players.last
        assertTrue(first !== second)
        assertEquals(12.minutes.ticks, second.plan.startAt)
        assertEquals(1, second.plan.audioStreamIndex)
        assertEquals(3, second.plan.subtitleStreamIndex)
        h.playback.selectAudio(2).getOrThrow()
        val third = h.players.last
        assertEquals(2, third.plan.audioStreamIndex)
        assertEquals(3, third.plan.subtitleStreamIndex)

        h.playback.selectAudio(2).getOrThrow()
        assertSame(third, h.players.last, "re-picking the playing tracks keeps the stream")

        third.pause()
        h.playback.selectSubtitle(null).getOrThrow()
        val paused = h.players.last
        assertEquals(PlayerState.Loading, paused.state.value, "a paused video stays paused")
        paused.state.value = PlayerState.Paused
        runCurrent()
        assertEquals("start", h.fake.reports.last().first)
        assertTrue(h.fake.reports.last().second.isPaused)
    }
}
