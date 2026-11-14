package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.application.library.Search
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.presentation.ScreenEffect
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.milliseconds

data class SearchState(
    val query: String = "",
    val results: List<MediaItem> = emptyList(),
    val searching: Boolean = false,
    /** The term [results] answer; null while the query is too short to search. */
    val searchedTerm: String? = null,
    val error: String? = null,
) {
    val showNoResults: Boolean get() = searchedTerm != null && !searching && results.isEmpty()

    val posterResults: List<MediaItem> get() = results.filterNot { it.hasLandscapeCard }
    val episodeResults: List<MediaItem> get() = results.filter { it.hasLandscapeCard }
}

sealed interface SearchIntent {
    data class QueryChanged(val value: String) : SearchIntent
    data class Open(val item: MediaItem) : SearchIntent
    data object Retry : SearchIntent
}

@OptIn(FlowPreview::class)
@AssistedInject
class SearchViewModel(@Assisted scope: CoroutineScope, private val search: Search, userDataEvents: UserDataEvents) :
    ViewModel<SearchState, SearchIntent>(SearchState(), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope): SearchViewModel
    }

    private var searchJob: Job? = null

    init {
        launch { state.map { Search.term(it.query) }.debounce(250.milliseconds).distinctUntilChanged().collect(::runSearch) }
        launch {
            userDataEvents.changes.collect { (id, data) ->
                update { st -> st.copy(results = st.results.map { it.withUserData(id, data) }) }
            }
        }
    }

    override fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> update { it.copy(query = intent.value) }
            is SearchIntent.Open -> emit(ScreenEffect.OpenItem(intent.item.id))
            SearchIntent.Retry -> runSearch(Search.term(current.query))
        }
    }

    private fun runSearch(term: String?) {
        searchJob?.cancel()
        if (term == null) {
            update { it.copy(results = emptyList(), searching = false, searchedTerm = null, error = null) }
            return
        }
        update { it.copy(searching = true, error = null) }
        searchJob = launch {
            when (val r = search(term)) {
                is Outcome.Success -> update { it.copy(results = r.value, searching = false, searchedTerm = term) }
                is Outcome.Failure -> update { it.copy(results = emptyList(), searching = false, searchedTerm = null, error = r.error.userMessage()) }
            }
        }
    }
}
