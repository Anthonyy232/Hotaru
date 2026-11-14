package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.application.library.LoadItemDetail
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.userdata.SetFavorite
import com.anthonyla.hotaru.application.userdata.SetPlayed
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.DisplayTitles
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.ResumePolicy
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.domain.Series
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.presentation.ScreenEffect
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import kotlinx.coroutines.CoroutineScope

data class DetailState(
    val itemId: ItemId,
    val item: MediaItem? = null,
    val children: List<MediaItem> = emptyList(),
    val episodes: List<Episode> = emptyList(),
    val similar: List<MediaItem> = emptyList(),
    val selectedSeasonId: ItemId? = null,
    val loading: Boolean = true,
    val error: String? = null,
    /** A favorite or played change failed. */
    val userDataError: String? = null,
) {
    val seasons: List<Season> get() = if (item is Series) children.filterIsInstance<Season>() else emptyList()

    val listedEpisodes: List<Episode> get() = episodes.filter { it.seasonId == selectedSeasonId }

    val childCards: List<MediaItem> get() = if (item is Series) emptyList() else children

    /** The first unwatched episode, taking specials (season 0, which the server lists first) last. */
    val nextUp: Episode? get() {
        val inOrder = episodes.sortedBy { it.parentIndexNumber == 0 }
        return inOrder.firstOrNull { it.userData?.played != true } ?: inOrder.firstOrNull()
    }
    val summary: ItemSummary? get() = item?.let(ItemSummary::of)
    val playTarget: MediaItem? get() {
        val item = item ?: return null
        return if (item.isPlayable) item else nextUp ?: children.firstOrNull { it.isPlayable }
    }
    val canPlay: Boolean get() = playTarget != null
    private val resumePoint: Ticks? get() = playTarget?.let(ResumePolicy::resumePoint)
    val canPlayFromStart: Boolean get() = resumePoint != null

    val playLabel: String get() {
        val resume = resumePoint
        val next = nextUp
        return when {
            item?.isPlayable == false && next != null -> "${if (resume != null) "Resume" else "Play"} ${DisplayTitles.episodeCode(next) ?: "next"}"
            resume != null -> "Resume ${DisplayTitles.clock(resume)}"
            else -> "Play"
        }
    }
}

sealed interface DetailIntent {
    data object Play : DetailIntent
    data object PlayFromStart : DetailIntent
    data object ToggleFavorite : DetailIntent
    data object TogglePlayed : DetailIntent
    data class OpenChild(val item: MediaItem) : DetailIntent
    data class SelectSeason(val seasonId: ItemId) : DetailIntent
    data class OpenSimilar(val item: MediaItem) : DetailIntent
    data object Retry : DetailIntent
}

@AssistedInject
class DetailViewModel(
    @Assisted scope: CoroutineScope,
    @Assisted itemId: ItemId,
    private val loadDetail: LoadItemDetail,
    private val setFavorite: SetFavorite,
    private val setPlayed: SetPlayed,
    userDataEvents: UserDataEvents,
) : ViewModel<DetailState, DetailIntent>(DetailState(itemId), scope) {

    @AssistedFactory
    fun interface Factory {
        fun create(scope: CoroutineScope, itemId: ItemId): DetailViewModel
    }

    init {
        refresh()
        launch {
            userDataEvents.changes.collect { (id, data) ->
                update { st ->
                    st.copy(
                        item = st.item?.withUserData(id, data),
                        children = st.children.map { it.withUserData(id, data) },
                        episodes = st.episodes.map { if (it.id == id) it.copy(userData = data) else it },
                        similar = st.similar.map { it.withUserData(id, data) },
                    )
                }
            }
        }
    }

    override fun onIntent(intent: DetailIntent) {
        val item = current.item
        when (intent) {
            DetailIntent.Play -> current.playTarget?.let { play(it, fromStart = false) }
            DetailIntent.PlayFromStart -> current.playTarget?.let { play(it, fromStart = true) }
            DetailIntent.ToggleFavorite -> item?.let { i -> changeUserData { setFavorite(i.id, i.userData?.isFavorite != true) } }
            DetailIntent.TogglePlayed -> item?.let { i -> changeUserData { setPlayed(i.id, i.userData?.played != true) } }
            is DetailIntent.OpenChild -> if (intent.item.isPlayable) play(intent.item, fromStart = false) else emit(ScreenEffect.OpenItem(intent.item.id))
            is DetailIntent.SelectSeason -> update { it.copy(selectedSeasonId = intent.seasonId) }
            is DetailIntent.OpenSimilar -> emit(ScreenEffect.OpenItem(intent.item.id))
            DetailIntent.Retry -> refresh()
        }
    }

    private fun changeUserData(change: suspend () -> Outcome<UserData>) {
        update { it.copy(userDataError = null) }
        launch { change().onFailure { e -> update { it.copy(userDataError = e.userMessage()) } } }
    }

    private fun play(item: MediaItem, fromStart: Boolean) = emit(ScreenEffect.Play(item, fromStart, playQueue(item)))

    private fun playQueue(target: MediaItem): List<MediaItem> {
        val episodes = current.episodes
        if (episodes.any { it.id == target.id }) return episodes
        val playable = current.children.filter { it.isPlayable }
        return if (playable.any { it.id == target.id }) playable else emptyList()
    }

    private fun refresh() {
        update { it.copy(loading = true, error = null) }
        launch {
            when (val r = loadDetail(current.itemId)) {
                is Outcome.Success -> {
                    val d = r.value
                    update { st ->
                        val loaded = st.copy(item = d.item, children = d.children, episodes = d.episodes, similar = d.similar, loading = false)
                        loaded.copy(selectedSeasonId = loaded.nextUp?.seasonId ?: d.children.firstOrNull { it is Season }?.id)
                    }
                }
                is Outcome.Failure -> update { it.copy(loading = false, error = r.error.userMessage()) }
            }
        }
    }
}
