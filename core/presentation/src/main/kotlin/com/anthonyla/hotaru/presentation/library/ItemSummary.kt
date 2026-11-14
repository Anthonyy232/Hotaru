package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.domain.DisplayTitles
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.Movie

data class ItemSummary(
    val title: String,
    val episodeTitle: String?,
    val tagline: String?,
    val episodeCode: String?,
    val year: String?,
    val runtime: String?,
    val officialRating: String?,
    val communityRating: String?,
    val is3D: Boolean,
    val genres: String?,
    val cast: String?,
) {
    companion object {
        fun of(item: MediaItem): ItemSummary {
            val episode = item as? Episode
            return ItemSummary(
                title = DisplayTitles.headline(item),
                episodeTitle = episode?.name,
                tagline = (item as? Movie)?.taglines?.firstOrNull(),
                episodeCode = episode?.let(DisplayTitles::episodeCode),
                year = item.productionYear?.takeIf { episode == null }?.toString(),
                runtime = DisplayTitles.runtime(item.runtime),
                officialRating = item.officialRating,
                communityRating = item.communityRating?.let { "★ %.1f".format(it) },
                is3D = (item as? Movie)?.video3DFormat != null,
                genres = item.genres.take(3).joinToString(" · ").ifEmpty { null },
                cast = item.people.take(6).joinToString("  ·  ") { p -> p.role?.let { "${p.name} as $it" } ?: p.name }.ifEmpty { null },
            )
        }
    }
}
