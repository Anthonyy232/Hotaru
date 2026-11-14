package com.anthonyla.hotaru.presentation.library

import com.anthonyla.hotaru.domain.DisplayTitles
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.ResumePolicy

/** Episodes show as landscape stills, everything else as posters. */
val MediaItem.hasLandscapeCard: Boolean get() = this is Episode

data class MediaCardContent(
    val image: ImageRef?,
    /** 0..1 watched, null when there is nothing to resume. */
    val progress: Float?,
    val overlayCode: String?,
    val subtitle: String?,
) {
    companion object {
        fun of(item: MediaItem, landscape: Boolean = item.hasLandscapeCard): MediaCardContent {
            val images = item.images
            val episode = item as? Episode
            return MediaCardContent(
                image = if (landscape) images.anyThumb ?: images.primary ?: images.backdrop else images.primary,
                progress = ResumePolicy.progressFraction(item),
                overlayCode = episode?.takeIf { landscape }?.let(DisplayTitles::episodeCode),
                subtitle = if (episode != null) DisplayTitles.episodeContext(episode) else item.productionYear?.toString(),
            )
        }
    }
}

data class EpisodeRowContent(
    val card: MediaCardContent,
    val title: String,
    val runtime: String?,
    val overview: String?,
) {
    companion object {
        fun of(episode: Episode): EpisodeRowContent = EpisodeRowContent(
            card = MediaCardContent.of(episode).copy(overlayCode = null),
            title = episode.indexNumber?.let { "$it. ${episode.name}" } ?: episode.name,
            runtime = DisplayTitles.runtime(episode.runtime),
            overview = episode.overview,
        )
    }
}
