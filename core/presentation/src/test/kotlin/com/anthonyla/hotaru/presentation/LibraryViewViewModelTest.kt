package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.library.BrowseView
import com.anthonyla.hotaru.application.ports.LibrarySort
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.Movie
import com.anthonyla.hotaru.presentation.library.LibraryViewViewModel
import com.anthonyla.hotaru.presentation.library.ViewIntent
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewViewModelTest {
    @Test
    fun `an empty filtered library suggests clearing the filters`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = LibraryViewViewModel(backgroundScope, Fixtures.moviesView, BrowseView(h.sessions), h.settings, h.userDataEvents)
        runCurrent()
        assertFalse(vm.state.value.showEmpty)
        vm.onIntent(ViewIntent.ToggleFavorites)
        runCurrent()
        assertTrue(vm.state.value.showEmpty)
        assertEquals("Try clearing the filters.", vm.state.value.emptyMessage)
    }

    @Test
    fun `a failed next page waits for a retry that appends without repeating items`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        fun addMovie(name: String) {
            val movie = Movie(ItemId("movie-$name"), name)
            h.fake.items[movie.id] = movie
            h.fake.parents[movie.id] = Fixtures.moviesView.id
        }
        repeat(67) { addMovie("Movie %02d".format(it)) }
        h.signedIn()
        val vm = LibraryViewViewModel(backgroundScope, Fixtures.moviesView, BrowseView(h.sessions), h.settings, h.userDataEvents)
        runCurrent()
        assertEquals(60, vm.state.value.items.size)

        h.fake.failNetwork = true
        vm.onIntent(ViewIntent.Scrolled(59))
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.nextPageError)
        h.fake.failNetwork = false
        vm.onIntent(ViewIntent.Scrolled(60))
        assertFalse(vm.state.value.loadingMore)

        // A library scan adds an item that sorts first, so the next page starts with the last item already shown.
        addMovie("A New Arrival")
        vm.onIntent(ViewIntent.Retry)
        runCurrent()
        val ids = vm.state.value.items.map { it.id }
        assertEquals(70, ids.size)
        assertEquals(ids.distinct(), ids)
        assertNull(vm.state.value.nextPageError)
    }

    @Test
    fun `the next order reloads the library and is the one another library opens in`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = LibraryViewViewModel(backgroundScope, Fixtures.moviesView, BrowseView(h.sessions), h.settings, h.userDataEvents)
        runCurrent()
        vm.onIntent(ViewIntent.NextSort)
        runCurrent()
        assertEquals(listOf(Fixtures.glassOrbit3D.id, Fixtures.neonTide.id, Fixtures.auroraDrift.id), vm.state.value.items.map { it.id })

        val shows = LibraryViewViewModel(backgroundScope, Fixtures.showsView, BrowseView(h.sessions), h.settings, h.userDataEvents)
        runCurrent()
        assertEquals(LibrarySort.DateAdded, shows.state.value.sort)
    }
}
