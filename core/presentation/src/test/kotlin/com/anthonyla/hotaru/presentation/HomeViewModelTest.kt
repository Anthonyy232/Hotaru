package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.presentation.library.HomeIntent
import com.anthonyla.hotaru.presentation.library.HomeViewModel
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test
    fun `a library refresh cancels the initial home request`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        runCurrent()
        var cancelled = false
        h.fake.views = { try { awaitCancellation() } finally { cancelled = true } }
        val vm = HomeViewModel(backgroundScope, h.loadHome, h.userDataEvents, h.realtime, h.playback)
        runCurrent()

        h.fake.views = { Fixtures.views }
        h.fake.events.emit(ServerEvent.LibraryChanged(emptySet()))
        runCurrent()

        assertTrue(cancelled)
        assertFalse(vm.state.value.loading)
        assertEquals(Fixtures.auroraDrift.id, vm.state.value.hero?.item?.id)
    }

    @Test
    fun `home shows an error to retry, not an empty library, when every row fails`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        // The server drops right after listing the libraries.
        h.fake.views = { Fixtures.views.also { h.fake.failNetwork = true } }
        val vm = HomeViewModel(backgroundScope, h.loadHome, h.userDataEvents, h.realtime, h.playback)
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.error)
        assertFalse(vm.state.value.showEmpty)

        h.fake.views = { Fixtures.views }
        h.fake.failNetwork = false
        vm.onIntent(HomeIntent.Retry)
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(4, vm.state.value.rows.size)
    }

    @Test
    fun `home loads rows and the hero`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = HomeViewModel(backgroundScope, h.loadHome, h.userDataEvents, h.realtime, h.playback)
        runCurrent()
        val state = vm.state.value
        assertFalse(state.loading)
        assertEquals(listOf("Continue Watching", "Next Up", "Latest Movies", "Latest Shows"), state.rows.map { it.title })
        assertEquals(Fixtures.auroraDrift.id, state.hero?.item?.id)
        assertEquals("Resume", state.hero?.playLabel)
    }
}
