package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.application.library.BrowseView
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.LibrarySort
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.presentation.ScreenEffect
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Job

private const val LOAD_MORE_THRESHOLD = 12

data class ViewState(
    val view: LibraryView,
    val items: List<MediaItem> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val sort: LibrarySort = LibrarySort.Name,
    val unplayedOnly: Boolean = false,
    val favoritesOnly: Boolean = false,
    /** The first page failed. */
    val error: String? = null,
    val nextPageError: String? = null,
) {
    val hasMore: Boolean get() = items.size < total
    /** The grid shows every card in the shape of its first. */
    val landscape: Boolean get() = items.firstOrNull()?.hasLandscapeCard == true
    val showEmpty: Boolean get() = !loading && items.isEmpty() && error == null
    val emptyMessage: String get() = if (unplayedOnly || favoritesOnly) "Try clearing the filters." else "This library has no items."
    val sortLabel: String get() = when (sort) {
        LibrarySort.Name -> "By name"
        LibrarySort.DateAdded -> "Recently added"
        LibrarySort.ReleaseDate -> "Latest releases"
    }
}

sealed interface ViewIntent {
    data class Scrolled(val lastVisibleIndex: Int) : ViewIntent
    /** Moves on to the next order, which every library then uses. */
    data object NextSort : ViewIntent
    data object ToggleUnplayed : ViewIntent
    data object ToggleFavorites : ViewIntent
    data class Open(val item: MediaItem) : ViewIntent
    data object Retry : ViewIntent
}

@AssistedInject
class LibraryViewViewModel(
    @Assisted scope: CoroutineScope,
    @Assisted view: LibraryView,
    private val browse: BrowseView,
    private val settings: SettingsStore,
    userDataEvents: UserDataEvents,
) : ViewModel<ViewState, ViewIntent>(ViewState(view), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope, view: LibraryView): LibraryViewViewModel
    }

    private var loadJob: Job? = null

    init {
        launch {
            settings.settings.map { it.librarySort }.distinctUntilChanged().collect { sort ->
                update { it.copy(sort = sort) }
                load(reset = true)
            }
        }
        launch {
            userDataEvents.changes.collect { (id, data) ->
                update { st -> st.copy(items = st.items.map { it.withUserData(id, data) }) }
            }
        }
    }

    override fun onIntent(intent: ViewIntent) {
        when (intent) {
            is ViewIntent.Scrolled -> {
                val nearEnd = intent.lastVisibleIndex >= current.items.size - LOAD_MORE_THRESHOLD
                val idle = !current.loading && !current.loadingMore && current.nextPageError == null
                if (nearEnd && current.hasMore && idle) load(reset = false)
            }
            ViewIntent.NextSort -> launch { settings.update { it.copy(librarySort = LibrarySort.entries[(it.librarySort.ordinal + 1) % LibrarySort.entries.size]) } }
            ViewIntent.ToggleUnplayed -> { update { it.copy(unplayedOnly = !it.unplayedOnly) }; load(reset = true) }
            ViewIntent.ToggleFavorites -> { update { it.copy(favoritesOnly = !it.favoritesOnly) }; load(reset = true) }
            is ViewIntent.Open -> emit(ScreenEffect.OpenItem(intent.item.id))
            ViewIntent.Retry -> load(reset = current.error != null)
        }
    }

    private fun load(reset: Boolean) {
        loadJob?.cancel()
        val start = if (reset) 0 else current.items.size
        update {
            if (reset) it.copy(loading = true, loadingMore = false, items = emptyList(), total = 0, error = null, nextPageError = null)
            else it.copy(loadingMore = true, nextPageError = null)
        }
        loadJob = launch {
            when (val r = browse(current.view, current.sort, current.unplayedOnly, current.favoritesOnly, start)) {
                // Items added on the server between pages shift the offsets, so a page can repeat items already shown.
                is Outcome.Success -> update {
                    val items = if (reset) r.value.items else (it.items + r.value.items).distinctBy { item -> item.id }
                    it.copy(loading = false, loadingMore = false, items = items, total = r.value.totalCount)
                }
                is Outcome.Failure -> update {
                    val message = r.error.userMessage()
                    if (reset) it.copy(loading = false, error = message) else it.copy(loadingMore = false, nextPageError = message)
                }
            }
        }
    }
}
