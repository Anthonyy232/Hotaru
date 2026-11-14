package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.application.library.HomeRow
import com.anthonyla.hotaru.application.library.LoadHome
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.playback.PlaybackCoordinator
import com.anthonyla.hotaru.application.realtime.RealtimeCoordinator
import com.anthonyla.hotaru.application.realtime.RealtimeNotice
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.ResumePolicy
import com.anthonyla.hotaru.presentation.ScreenEffect
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlin.time.Duration.Companion.seconds

data class HomeRowState(val id: String, val title: String, val items: List<MediaItem>)

private fun HomeRow.toState(): HomeRowState = when (this) {
    is HomeRow.Resume -> HomeRowState("resume", "Continue Watching", items)
    is HomeRow.NextUp -> HomeRowState("nextup", "Next Up", items)
    is HomeRow.Latest -> HomeRowState("latest:${view.id}", "Latest ${view.name}", items)
}

data class Hero(val item: MediaItem) {
    val summary: ItemSummary get() = ItemSummary.of(item)

    /** Null when the hero can't be played directly (a series or folder). */
    val playLabel: String? get() = item.takeIf { it.isPlayable }?.let { if (ResumePolicy.resumePoint(it) != null) "Resume" else "Play" }
}

data class HomeState(
    val loading: Boolean = true,
    val rows: List<HomeRowState> = emptyList(),
    val hero: Hero? = null,
    val error: String? = null,
) {
    val showEmpty: Boolean get() = !loading && rows.isEmpty() && error == null
}

sealed interface HomeIntent {
    data class Open(val item: MediaItem) : HomeIntent
    data class Play(val item: MediaItem) : HomeIntent
    data object Retry : HomeIntent
}

@OptIn(FlowPreview::class)
@AssistedInject
class HomeViewModel(
    @Assisted scope: CoroutineScope,
    private val loadHome: LoadHome,
    userDataEvents: UserDataEvents,
    realtime: RealtimeCoordinator,
    playback: PlaybackCoordinator,
) : ViewModel<HomeState, HomeIntent>(HomeState(), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): HomeViewModel
    }

    private var refreshJob: Job? = null

    init {
        refresh()
        launch {
            // Continue Watching and Next Up follow what was watched. Playing here reports progress every few seconds, so wait for it to stop.
            userDataEvents.changes.filter { playback.current.value == null }.debounce(1.seconds).collect { refresh(silent = true) }
        }
        launch {
            realtime.notices.collect {
                when {
                    it is RealtimeNotice.LibraryChanged -> refresh(silent = true)
                    it is RealtimeNotice.Connected && current.error != null -> refresh()
                }
            }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.Open -> emit(ScreenEffect.OpenItem(intent.item.id))
            is HomeIntent.Play -> emit(ScreenEffect.Play(intent.item))
            HomeIntent.Retry -> refresh()
        }
    }

    private fun refresh(silent: Boolean = false) {
        if (!silent) update { it.copy(loading = true, error = null) }
        refreshJob?.cancel()
        refreshJob = launch {
            when (val r = loadHome()) {
                is Outcome.Success -> {
                    val home = r.value
                    update { it.copy(loading = false, rows = home.rows.map { row -> row.toState() }, hero = home.hero?.let(::Hero), error = null) }
                }
                is Outcome.Failure -> update { it.copy(loading = false, error = r.error.userMessage()) }
            }
        }
    }
}
