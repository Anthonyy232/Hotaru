package com.anthonyla.hotaru.domain

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

object DisplayTitles {
    fun headline(item: MediaItem): String = (item as? Episode)?.seriesName ?: item.name

    /** "S01E02"; null when numbering is missing. */
    fun episodeCode(episode: Episode): String? {
        val season = episode.parentIndexNumber ?: return null
        val index = episode.indexNumber ?: return null
        val end = episode.indexNumberEnd
        val ep = if (end != null && end > index) "E${pad2(index)}-${pad2(end)}" else "E${pad2(index)}"
        return "S${pad2(season)}$ep"
    }

    /** "S01E02 · Title" or just the title. */
    fun episodeTitle(episode: Episode): String {
        val code = episodeCode(episode)
        return if (code != null) "$code · ${episode.name}" else episode.name
    }

    /** "1h 42m"; partial minutes round up so a short clip reads "1m", not "0m". */
    fun runtime(ticks: Ticks?): String? {
        ticks ?: return null
        return (ticks.toDuration() + 59.seconds).toComponents { h, m, _, _ -> if (h > 0) "${h}h ${m}m" else "${m}m" }
    }

    /** "1:02:03" or "42:10". */
    fun clock(ticks: Ticks): String =
        ticks.toDuration().coerceAtLeast(Duration.ZERO).toComponents { h, m, s, _ ->
            if (h > 0) "$h:${pad2(m)}:${pad2(s)}" else "$m:${pad2(s)}"
        }

    private fun pad2(n: Number) = n.toString().padStart(2, '0')

    /** "Series Name · S01E02". */
    fun episodeContext(episode: Episode): String? {
        val code = episodeCode(episode)
        return listOfNotNull(episode.seriesName, code).takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}

object StereoRules {
    /** A video of unknown size, or nothing playing yet. */
    const val DEFAULT_ASPECT = 16f / 9f

    fun layoutFor(format: Video3DFormat?): StereoLayout = when (format) {
        Video3DFormat.HalfSideBySide, Video3DFormat.FullSideBySide -> StereoLayout.SideBySide
        Video3DFormat.HalfTopAndBottom, Video3DFormat.FullTopAndBottom -> StereoLayout.TopBottom
        Video3DFormat.MVC, null -> StereoLayout.Mono
    }

    /** Half-width formats keep the item's nominal aspect; full-width ones halve it per eye. [DEFAULT_ASPECT] when the size is unknown. */
    fun perEyeAspect(format: Video3DFormat?, width: Int?, height: Int?): Float {
        if (width == null || height == null) return DEFAULT_ASPECT
        return when (format) {
            Video3DFormat.FullSideBySide -> (width / 2f) / height
            Video3DFormat.FullTopAndBottom -> width / (height / 2f)
            Video3DFormat.HalfSideBySide, Video3DFormat.HalfTopAndBottom, Video3DFormat.MVC, null -> width.toFloat() / height
        }
    }

    private val projectionTags = listOf(
        // Underscore and dot are common separators in file names, so they must not count as word characters.
        Regex("""(?<![A-Za-z0-9])(360|vr360)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE) to Projection.Equirect360,
        Regex("""(?<![A-Za-z0-9])(180|vr180)(?![A-Za-z0-9])""", RegexOption.IGNORE_CASE) to Projection.Equirect180,
    )

    /** Projection is not a Jellyfin field, and a flat 2:1 film has an equirect frame's shape; only the name/path tags users rely on tell. */
    fun projectionFor(nameOrPath: String): Projection =
        projectionTags.firstOrNull { (regex, _) -> regex.containsMatchIn(nameOrPath) }?.second ?: Projection.Rectilinear
}

/** The server owns played/resume bookkeeping; this only decides what we offer the user. */
object ResumePolicy {
    private val minResume = 30.seconds.ticks
    private val tailGuard = 2.minutes.ticks

    fun resumePoint(item: MediaItem): Ticks? {
        val pos = item.userData?.resumePosition ?: return null
        val runtime = item.runtime
        if (pos < minResume) return null
        if (runtime != null && runtime > tailGuard && pos > runtime - tailGuard) return null
        return pos
    }

    fun progressFraction(item: MediaItem): Float? {
        val pos = item.userData?.resumePosition ?: return null
        val runtime = item.runtime ?: return null
        return pos.fractionOf(runtime).toFloat().takeIf { it > 0f }
    }
}
