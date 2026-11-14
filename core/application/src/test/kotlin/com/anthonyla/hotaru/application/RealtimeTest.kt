package com.anthonyla.hotaru.application

import com.anthonyla.hotaru.application.auth.SignOut
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.PlaystateCommand
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.application.realtime.RealtimeNotice
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import com.anthonyla.hotaru.testing.getOrThrow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RealtimeTest {

    @Test
    fun `server events invalidate the cache, fan out user data and drive playback`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        runCurrent()

        val aurora = Fixtures.auroraDrift
        h.itemDetail(aurora.id).getOrThrow()
        var published: Pair<ItemId, UserData>? = null
        launch { published = h.userDataEvents.changes.first() }
        runCurrent()
        h.fake.events.emit(ServerEvent.UserDataChanged(mapOf(aurora.id to UserData(isFavorite = true))))
        runCurrent()
        assertTrue(assertNotNull(published).second.isFavorite)
        assertNull(h.cache.get(server.id, h.active.account.userId, aurora.id))

        h.itemDetail(Fixtures.season1.id).getOrThrow()
        var notice: RealtimeNotice? = null
        launch { notice = h.realtime.notices.first() }
        runCurrent()
        h.fake.events.emit(ServerEvent.LibraryChanged(setOf(Fixtures.season1.id)))
        runCurrent()
        assertIs<RealtimeNotice.LibraryChanged>(notice)
        assertNull(h.cache.get(server.id, h.active.account.userId, Fixtures.season1.id))

        val (e0, e1) = Fixtures.episodes
        h.fake.events.emit(ServerEvent.Play(listOf(e0.id, e1.id), null, 1, null, SubtitleChoice.Default))
        runCurrent()
        val session = assertNotNull(h.playback.current.value)
        assertEquals(e1.id, session.plan.item.id)

        h.fake.events.emit(ServerEvent.Playstate(PlaystateCommand.Pause, null))
        runCurrent()
        assertIs<PlayerState.Paused>(h.players.last.state.value)
        h.fake.events.emit(ServerEvent.SelectSubtitle(3))
        runCurrent()
        assertEquals(3, session.selectedSubtitle)
        h.fake.events.emit(ServerEvent.SetVolume(40))
        runCurrent()
        assertEquals(0.4f, h.players.last.volume.value)

        h.settings.update { it.copy(autoplayNext = false) }
        h.fake.events.emit(ServerEvent.Playstate(PlaystateCommand.NextTrack, null))
        runCurrent()
        assertEquals(Fixtures.episodes[2].id, assertNotNull(h.playback.current.value).plan.item.id)
    }

    @Test
    fun `a rejected capability post ends the session`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val server = h.signedIn()
        val account = h.active.account
        SignOut(h.sessions)(forgetAccount = false)
        h.fake.validTokens.clear()

        // Restoring makes no request of its own, so only realtime's capability post meets the rejected token.
        h.sessions.restore(server, account).getOrThrow()
        runCurrent()

        assertNull(h.sessions.active.value)
        assertTrue(h.secure.map.isEmpty())
    }
}
