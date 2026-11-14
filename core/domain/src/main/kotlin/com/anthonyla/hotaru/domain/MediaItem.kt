package com.anthonyla.hotaru.domain

import kotlinx.serialization.Serializable

enum class ItemKind {
    Movie, Series, Season, Episode, Video, BoxSet, Playlist, Folder, CollectionFolder, Unknown,
}

enum class CollectionType {
    Movies, TvShows, Playlists, BoxSets, HomeVideos, MusicVideos, Mixed;

    val isVideo: Boolean get() = this == Movies || this == TvShows || this == HomeVideos || this == MusicVideos

    /** The kinds a library of this type lists recursively; empty means just its direct children. */
    val browseKinds: Set<ItemKind> get() = when (this) {
        Movies -> setOf(ItemKind.Movie)
        TvShows -> setOf(ItemKind.Series)
        Playlists, BoxSets, HomeVideos, MusicVideos, Mixed -> emptySet()
    }
}

data class LibraryView(
    val id: ItemId,
    val name: String,
    val collectionType: CollectionType,
)

@Serializable
data class Person(val name: String, val role: String?)

@Serializable
data class UserData(
    val played: Boolean = false,
    val isFavorite: Boolean = false,
    val resumePosition: Ticks? = null,
    val unplayedItemCount: Int? = null,
)

@Serializable
sealed interface MediaItem {
    val id: ItemId
    val kind: ItemKind
    val name: String
    val overview: String?
    val images: ImageSet
    val userData: UserData?
    val runtime: Ticks?
    val productionYear: Int?
    val communityRating: Float?
    val officialRating: String?
    val genres: List<String>
    val people: List<Person>
    val isFolder: Boolean

    val isPlayable: Boolean get() = !isFolder && (kind == ItemKind.Movie || kind == ItemKind.Episode || kind == ItemKind.Video)
}

@Serializable
data class Movie(
    override val id: ItemId,
    override val name: String,
    override val overview: String? = null,
    override val images: ImageSet = ImageSet(),
    override val userData: UserData? = null,
    override val runtime: Ticks? = null,
    override val productionYear: Int? = null,
    override val communityRating: Float? = null,
    override val officialRating: String? = null,
    override val genres: List<String> = emptyList(),
    override val people: List<Person> = emptyList(),
    val taglines: List<String> = emptyList(),
    val video3DFormat: Video3DFormat? = null,
) : MediaItem {
    override val kind: ItemKind get() = ItemKind.Movie
    override val isFolder: Boolean get() = false
}

@Serializable
data class Series(
    override val id: ItemId,
    override val name: String,
    override val overview: String? = null,
    override val images: ImageSet = ImageSet(),
    override val userData: UserData? = null,
    override val runtime: Ticks? = null,
    override val productionYear: Int? = null,
    override val communityRating: Float? = null,
    override val officialRating: String? = null,
    override val genres: List<String> = emptyList(),
    override val people: List<Person> = emptyList(),
) : MediaItem {
    override val kind: ItemKind get() = ItemKind.Series
    override val isFolder: Boolean get() = true
}

@Serializable
data class Season(
    override val id: ItemId,
    override val name: String,
    override val overview: String? = null,
    override val images: ImageSet = ImageSet(),
    override val userData: UserData? = null,
    override val runtime: Ticks? = null,
    override val productionYear: Int? = null,
    override val communityRating: Float? = null,
    override val officialRating: String? = null,
    override val genres: List<String> = emptyList(),
    override val people: List<Person> = emptyList(),
    val seriesId: ItemId,
) : MediaItem {
    override val kind: ItemKind get() = ItemKind.Season
    override val isFolder: Boolean get() = true
}

@Serializable
data class Episode(
    override val id: ItemId,
    override val name: String,
    override val overview: String? = null,
    override val images: ImageSet = ImageSet(),
    override val userData: UserData? = null,
    override val runtime: Ticks? = null,
    override val productionYear: Int? = null,
    override val communityRating: Float? = null,
    override val officialRating: String? = null,
    override val genres: List<String> = emptyList(),
    override val people: List<Person> = emptyList(),
    val seriesId: ItemId? = null,
    val seriesName: String? = null,
    val seasonId: ItemId? = null,
    val indexNumber: Int? = null,
    val indexNumberEnd: Int? = null,
    val parentIndexNumber: Int? = null,
) : MediaItem {
    override val kind: ItemKind get() = ItemKind.Episode
    override val isFolder: Boolean get() = false
}

@Serializable
data class GenericItem(
    override val id: ItemId,
    override val kind: ItemKind,
    override val name: String,
    override val overview: String? = null,
    override val images: ImageSet = ImageSet(),
    override val userData: UserData? = null,
    override val runtime: Ticks? = null,
    override val productionYear: Int? = null,
    override val communityRating: Float? = null,
    override val officialRating: String? = null,
    override val genres: List<String> = emptyList(),
    override val people: List<Person> = emptyList(),
    override val isFolder: Boolean = false,
) : MediaItem

fun MediaItem.withUserData(data: UserData): MediaItem = when (this) {
    is Movie -> copy(userData = data)
    is Episode -> copy(userData = data)
    is Series -> copy(userData = data)
    is Season -> copy(userData = data)
    is GenericItem -> copy(userData = data)
}

fun <T : MediaItem> List<T>.itemAfter(id: ItemId): T? = zipWithNext().firstOrNull { (prev, _) -> prev.id == id }?.second
