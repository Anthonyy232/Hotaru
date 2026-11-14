package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.application.library.Search
import com.anthonyla.hotaru.presentation.library.SearchIntent
import com.anthonyla.hotaru.presentation.library.SearchViewModel
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @Test
    fun `search only reports no results for a term it searched`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = SearchViewModel(backgroundScope, Search(h.sessions), h.userDataEvents)
        vm.onIntent(SearchIntent.QueryChanged(" a "))
        advanceTimeBy(250); runCurrent()
        assertFalse(vm.state.value.showNoResults)
        vm.onIntent(SearchIntent.QueryChanged(" zzz "))
        advanceTimeBy(250); runCurrent()
        assertTrue(vm.state.value.showNoResults)
        assertEquals("zzz", vm.state.value.searchedTerm)
        vm.onIntent(SearchIntent.QueryChanged("aurora"))
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(Fixtures.auroraDrift.id), vm.state.value.results.map { it.id })
        vm.onIntent(SearchIntent.QueryChanged("si"))
        advanceTimeBy(250); runCurrent()
        assertEquals(listOf(Fixtures.signalLost.id), vm.state.value.posterResults.map { it.id })
        assertEquals(listOf(Fixtures.episodes[2].id), vm.state.value.episodeResults.map { it.id })
    }

    @Test
    fun `a failed search runs again for the same term`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = SearchViewModel(backgroundScope, Search(h.sessions), h.userDataEvents)
        h.fake.failNetwork = true
        vm.onIntent(SearchIntent.QueryChanged("aurora"))
        advanceTimeBy(250); runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.error)

        h.fake.failNetwork = false
        vm.onIntent(SearchIntent.Retry)
        runCurrent()
        assertNull(vm.state.value.error)
        assertEquals(listOf(Fixtures.auroraDrift.id), vm.state.value.results.map { it.id })
    }
}
