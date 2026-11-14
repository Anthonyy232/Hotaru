package com.anthonyla.hotaru.testing

import com.anthonyla.hotaru.domain.AudioStream
import com.anthonyla.hotaru.domain.CollectionType
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSet
import com.anthonyla.hotaru.domain.ImageType
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.Movie
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.domain.Series
import com.anthonyla.hotaru.domain.SubtitleDelivery
import com.anthonyla.hotaru.domain.SubtitleStream
import com.anthonyla.hotaru.domain.TextSubtitleFormat
import com.anthonyla.hotaru.domain.ticks
import com.anthonyla.hotaru.domain.TranscodingProtocol
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.domain.Video3DFormat
import com.anthonyla.hotaru.domain.VideoStream
import kotlin.time.Duration.Companion.minutes

object Fixtures {
    val moviesView = LibraryView(ItemId("view-movies"), "Movies", CollectionType.Movies)
    val showsView = LibraryView(ItemId("view-shows"), "Shows", CollectionType.TvShows)
    val views = listOf(moviesView, showsView)

    private fun images(id: String) = ImageSet(
        primary = ImageRef(ItemId(id), ImageType.Primary, "p-$id"),
        backdrops = listOf(ImageRef(ItemId(id), ImageType.Backdrop, "b-$id")),
    )

    val auroraDrift = Movie(
        id = ItemId("movie-aurora"), name = "Aurora Drift", productionYear = 2024, runtime = 112.minutes.ticks,
        overview = "A pilot chases a light across a frozen sea.", images = images("movie-aurora"),
        communityRating = 7.8f, officialRating = "PG-13", genres = listOf("Adventure", "Sci-Fi"),
        userData = UserData(resumePosition = 40.minutes.ticks),
    )
    val neonTide = Movie(
        id = ItemId("movie-neon"), name = "Neon Tide", productionYear = 2023, runtime = 98.minutes.ticks,
        overview = "A heist in a flooded city.", images = images("movie-neon"), communityRating = 6.9f,
        genres = listOf("Thriller"), userData = UserData(played = true),
    )
    val glassOrbit3D = Movie(
        id = ItemId("movie-glass"), name = "Glass Orbit", productionYear = 2025, runtime = 105.minutes.ticks,
        images = images("movie-glass"), genres = listOf("Sci-Fi"),
        video3DFormat = Video3DFormat.HalfSideBySide, userData = UserData(),
    )

    val signalLost = Series(
        id = ItemId("series-signal"), name = "Signal Lost", productionYear = 2022, images = images("series-signal"),
        overview = "A radio operator hears something.", genres = listOf("Drama"),
        userData = UserData(unplayedItemCount = 2),
    )
    val season1 = Season(
        id = ItemId("season-signal-1"), name = "Season 1", seriesId = signalLost.id, images = images("season-signal-1"),
    )
    val episodes: List<Episode> = (1..3).map { n ->
        Episode(
            id = ItemId("ep-signal-1-$n"), name = listOf("Static", "Carrier", "Silence")[n - 1], indexNumber = n, parentIndexNumber = 1,
            seriesId = signalLost.id, seriesName = signalLost.name, seasonId = season1.id,
            runtime = 42.minutes.ticks, images = images("ep-signal-1-$n"),
            userData = if (n == 1) UserData(played = true) else UserData(),
        )
    }

    val all: List<MediaItem> = listOf(auroraDrift, neonTide, glassOrbit3D, signalLost, season1) + episodes

    /** Child to parent: the folder tree the fake server browses. */
    val parents: Map<ItemId, ItemId> =
        listOf(auroraDrift, neonTide, glassOrbit3D).associate { it.id to moviesView.id } +
            mapOf(signalLost.id to showsView.id, season1.id to signalLost.id) + episodes.associate { it.id to season1.id }

    fun source(item: MediaItem, directPlay: Boolean): MediaSource = MediaSource(
        id = MediaSourceId("src-${item.id.value}"),
        name = item.name,
        runtime = item.runtime,
        isLocalFile = true,
        eTag = "etag-${item.id.value}",
        supportsDirectPlay = directPlay,
        supportsDirectStream = !directPlay,
        supportsTranscoding = true,
        transcodingUrl = if (directPlay) null else OpaqueUrl("/videos/${item.id.value}/master.m3u8?PlaySessionId=ps1&TranscodeReasons=AudioCodecNotSupported"),
        transcodingProtocol = if (directPlay) TranscodingProtocol.Http else TranscodingProtocol.Hls,
        streams = listOf(
            VideoStream(index = 0, width = 1920, height = 1080),
            AudioStream(index = 1, displayTitle = "English - EAC3 5.1"),
            AudioStream(index = 2, displayTitle = "French - AAC"),
            SubtitleStream(
                index = 3, language = "eng", delivery = SubtitleDelivery.External, displayTitle = "English",
                deliveryUrl = OpaqueUrl("/Videos/${item.id.value}/src-${item.id.value}/Subtitles/3/0/Stream.srt"), deliveryFormat = TextSubtitleFormat.SubRip,
            ),
            SubtitleStream(index = 4, language = "eng", delivery = SubtitleDelivery.Embed, displayTitle = "English (PGS)"),
        ),
        defaultAudioStreamIndex = 1,
        defaultSubtitleStreamIndex = null,
        video3DFormat = (item as? Movie)?.video3DFormat,
        hasSegments = item is Episode,
    )
}
