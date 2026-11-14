package com.anthonyla.hotaru.presentation

import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.presentation.library.DetailIntent
import com.anthonyla.hotaru.presentation.library.DetailViewModel
import com.anthonyla.hotaru.presentation.library.EpisodeRowContent
import com.anthonyla.hotaru.testing.ApplicationHarness
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    @Test
    fun `series detail plays the first unwatched episode and queues the rest`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        val vm = DetailViewModel(backgroundScope, Fixtures.signalLost.id, h.loadItemDetail, h.setFavorite, h.setPlayed, h.userDataEvents)
        runCurrent()
        val state = vm.state.value
        assertEquals(1, state.children.size)
        assertEquals(3, state.episodes.size)
        assertEquals(Fixtures.episodes[1].id, state.nextUp?.id)
        assertTrue(state.canPlay)
        assertEquals("Play S01E02", state.playLabel)
        var effect: ScreenEffect? = null
        launch { effect = vm.effects.first() }
        runCurrent()
        vm.onIntent(DetailIntent.Play)
        runCurrent()
        val play = assertIs<ScreenEffect.Play>(effect)
        assertEquals(Fixtures.episodes[1].id, play.item.id)
        assertEquals(Fixtures.episodes.map { it.id }, play.queue.map { it.id })
    }

    @Test
    fun `a failed detail load can be retried, and a failed favorite says why`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        h.signedIn()
        h.fake.failNetwork = true
        val vm = DetailViewModel(backgroundScope, Fixtures.auroraDrift.id, h.loadItemDetail, h.setFavorite, h.setPlayed, h.userDataEvents)
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.error)

        h.fake.failNetwork = false
        vm.onIntent(DetailIntent.Retry)
        runCurrent()
        assertEquals(Fixtures.auroraDrift.id, vm.state.value.item?.id)

        h.fake.failNetwork = true
        vm.onIntent(DetailIntent.ToggleFavorite)
        runCurrent()
        assertEquals("Can't reach the server: fake offline", vm.state.value.userDataError)
        h.fake.failNetwork = false
        vm.onIntent(DetailIntent.ToggleFavorite)
        runCurrent()
        assertNull(vm.state.value.userDataError)
        assertEquals(true, vm.state.value.item?.userData?.isFavorite)
    }

    @Test
    fun `series detail lists the next-up season's episodes under season tabs`() = runTest {
        val h = ApplicationHarness(backgroundScope)
        val series = Fixtures.signalLost
        val season2 = Season(ItemId("season-signal-2"), "Season 2", seriesId = series.id)
        val echo = Episode(
            ItemId("ep-signal-2-1"), "Echo", indexNumber = 1, parentIndexNumber = 2,
            seriesId = series.id, seriesName = series.name, seasonId = season2.id,
        )
        listOf(season2, echo).forEach { h.fake.items[it.id] = it }
        h.signedIn()
        val vm = DetailViewModel(backgroundScope, series.id, h.loadItemDetail, h.setFavorite, h.setPlayed, h.userDataEvents)
        runCurrent()
        assertEquals(listOf(Fixtures.season1.id, season2.id), vm.state.value.seasons.map { it.id })
        assertTrue(vm.state.value.childCards.isEmpty())
        assertEquals(Fixtures.episodes.map { it.id }, vm.state.value.listedEpisodes.map { it.id })

        vm.onIntent(DetailIntent.SelectSeason(season2.id))
        assertEquals(listOf(echo.id), vm.state.value.listedEpisodes.map { it.id })
        assertEquals("1. Echo", EpisodeRowContent.of(vm.state.value.listedEpisodes.single()).title)

        vm.onIntent(DetailIntent.SelectSeason(Fixtures.season1.id))
        h.setPlayed(Fixtures.episodes[1].id, true)
        runCurrent()
        assertEquals(true, vm.state.value.listedEpisodes[1].userData?.played)
        assertEquals("Play S01E03", vm.state.value.playLabel)
    }
}
