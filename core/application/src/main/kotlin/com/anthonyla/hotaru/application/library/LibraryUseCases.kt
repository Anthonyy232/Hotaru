package com.anthonyla.hotaru.application.library

import com.anthonyla.hotaru.application.flatMap
import com.anthonyla.hotaru.application.map
import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.ItemCache
import com.anthonyla.hotaru.application.ports.ItemQuery
import com.anthonyla.hotaru.application.ports.LibrarySort
import com.anthonyla.hotaru.application.ports.Page
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.Series
import jakarta.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

sealed interface HomeRow {
    val items: List<MediaItem>

    data class Resume(override val items: List<MediaItem>) : HomeRow
    data class NextUp(override val items: List<MediaItem>) : HomeRow
    data class Latest(val view: LibraryView, override val items: List<MediaItem>) : HomeRow
}

data class HomeContent(val rows: List<HomeRow>) {
    val hero: MediaItem? get() = rows.asSequence().flatMap { it.items.asSequence() }.firstOrNull { it.images.backdrop != null }
}

private const val HOME_ROW_LIMIT = 20
private const val LATEST_ROW_LIMIT = 16
private const val SIMILAR_LIMIT = 12
private const val SEARCH_LIMIT = 40
private const val FOLDER_CHILDREN_LIMIT = 500

/** Rows are best effort, but when every row request fails Home has nothing to show and fails with the first error. */
class LoadHome @Inject constructor(private val sessions: SessionManager) {
    suspend operator fun invoke(): Outcome<HomeContent> = sessions.withSession { active ->
        val library = active.connection.library
        library.views().flatMap { views ->
            coroutineScope {
                val resume = async { library.resume(HOME_ROW_LIMIT).map(HomeRow::Resume) }
                val nextUp = async { library.nextUp(HOME_ROW_LIMIT).map(HomeRow::NextUp) }
                val latest = views.filter { it.collectionType.isVideo }
                    .map { view -> async { library.latest(view.id, LATEST_ROW_LIMIT).map { HomeRow.Latest(view, it) } } }

                val rows = (listOf(resume, nextUp) + latest).awaitAll()
                val failures = rows.filterIsInstance<Outcome.Failure>()
                if (failures.size == rows.size) failures.first()
                else HomeContent(rows.mapNotNull { it.getOrNull() }.filter { it.items.isNotEmpty() }).success()
            }
        }
    }
}

class LoadViews @Inject constructor(private val sessions: SessionManager) {
    suspend operator fun invoke(): Outcome<List<LibraryView>> = sessions.withSession { it.connection.library.views() }
}

class BrowseView @Inject constructor(private val sessions: SessionManager) {
    suspend operator fun invoke(view: LibraryView, sort: LibrarySort, unplayedOnly: Boolean, favoritesOnly: Boolean, startIndex: Int): Outcome<Page<MediaItem>> {
        val kinds = view.collectionType.browseKinds
        val query = ItemQuery(
            parentId = view.id, kinds = kinds, recursive = kinds.isNotEmpty(),
            isPlayed = if (unplayedOnly) false else null,
            isFavorite = if (favoritesOnly) true else null,
            sort = sort, startIndex = startIndex, limit = PAGE_SIZE,
        )
        return sessions.withSession { it.connection.library.items(query) }
    }

    private companion object {
        const val PAGE_SIZE = 60
    }
}

/** Only this full fetch fills the cache: list endpoints return partial items (no people, at most overview and genres). */
class GetItemDetail @Inject constructor(private val sessions: SessionManager, private val cache: ItemCache, private val clock: Clock) {
    private val freshness = 10.minutes

    suspend operator fun invoke(id: ItemId): Outcome<MediaItem> = sessions.withSession { active ->
        val serverId = active.server.id
        val userId = active.account.userId
        cache.get(serverId, userId, id)?.let { cached ->
            if (clock.now() - cached.fetchedAt < freshness) return cached.item.success()
        }
        active.connection.library.item(id).onSuccess { cache.put(serverId, userId, it, clock.now()) }
    }
}

data class ItemDetail(
    val item: MediaItem,
    /** A series' seasons, or a folder's direct children. */
    val children: List<MediaItem>,
    /** A series' episodes in order: the play target and the autoplay queue. */
    val episodes: List<Episode>,
    val similar: List<MediaItem>,
)

/** Everything a detail page shows; children, episodes and similar items are best effort. */
class LoadItemDetail @Inject constructor(private val sessions: SessionManager, private val getItem: GetItemDetail) {
    suspend operator fun invoke(id: ItemId): Outcome<ItemDetail> = getItem(id).flatMap { item ->
        sessions.withSession { active ->
            val library = active.connection.library
            coroutineScope {
                val children = async {
                    when {
                        !item.isFolder -> emptyList()
                        item is Series -> library.seasons(item.id).getOrNull().orEmpty()
                        else -> library.items(ItemQuery(parentId = item.id, recursive = false, limit = FOLDER_CHILDREN_LIMIT)).getOrNull()?.items.orEmpty()
                    }
                }
                val episodes = async { if (item is Series) library.episodes(item.id, null).getOrNull().orEmpty() else emptyList() }
                val similar = async {
                    if (item.kind == ItemKind.Movie || item.kind == ItemKind.Series) library.similar(item.id, SIMILAR_LIMIT).getOrNull().orEmpty() else emptyList()
                }
                ItemDetail(
                    item = item,
                    children = children.await(),
                    episodes = episodes.await(),
                    similar = similar.await(),
                ).success()
            }
        }
    }
}

class Search @Inject constructor(private val sessions: SessionManager) {
    /** [term] comes from [Search.term]. */
    suspend operator fun invoke(term: String): Outcome<List<MediaItem>> = sessions.withSession { it.connection.library.search(term, SEARCH_LIMIT) }

    companion object {
        /** The term to search for [query]; null when it is too short to search. */
        fun term(query: String): String? = query.trim().takeIf { it.length >= 2 }
    }
}
