package com.anthonyla.hotaru.domain

enum class Video3DFormat { HalfSideBySide, FullSideBySide, FullTopAndBottom, HalfTopAndBottom, MVC }

enum class StereoLayout { Mono, SideBySide, TopBottom }

enum class Projection { Rectilinear, Equirect180, Equirect360 }

enum class HdrFormat { HDR10, HDR10Plus, DolbyVision }

enum class SubtitleDelivery { Encode, Embed, External, Hls, Drop }

enum class TranscodingProtocol { Http, Hls }

sealed interface MediaStream {
    val index: Int
}

data class VideoStream(
    override val index: Int,
    val width: Int?,
    val height: Int?,
) : MediaStream

data class AudioStream(
    override val index: Int,
    val displayTitle: String?,
) : MediaStream

data class SubtitleStream(
    override val index: Int,
    val language: String?,
    val title: String? = null,
    val displayTitle: String?,
    val delivery: SubtitleDelivery?,
    val deliveryUrl: OpaqueUrl? = null,
    /** The text format [deliveryUrl] serves after server conversion; null when absent, unsupported or not text. */
    val deliveryFormat: TextSubtitleFormat? = null,
) : MediaStream

/** A text subtitle file the player side-loads next to the media; [url] is absolute. */
data class ExternalSubtitle(val stream: SubtitleStream, val url: OpaqueUrl, val format: TextSubtitleFormat)

/** Which subtitles to play: the source's default, none, or one stream by server index. */
sealed interface SubtitleChoice {
    data object Default : SubtitleChoice
    data object Off : SubtitleChoice
    data class Stream(val index: Int) : SubtitleChoice
}

data class MediaSource(
    val id: MediaSourceId,
    val name: String?,
    val runtime: Ticks?,
    /** A file the server reads directly (not a remote URL); preferred for direct play. */
    val isLocalFile: Boolean,
    val eTag: String?,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    val transcodingUrl: OpaqueUrl?,
    val transcodingProtocol: TranscodingProtocol,
    val streams: List<MediaStream>,
    val defaultAudioStreamIndex: Int?,
    val defaultSubtitleStreamIndex: Int?,
    val video3DFormat: Video3DFormat?,
    val hasSegments: Boolean,
) {
    val videoStream: VideoStream? get() = streams.filterIsInstance<VideoStream>().firstOrNull()
    val audioStreams: List<AudioStream> get() = streams.filterIsInstance<AudioStream>()
    val subtitleStreams: List<SubtitleStream> get() = streams.filterIsInstance<SubtitleStream>()
}

enum class PlayMethod { DirectPlay, DirectStream, Transcode }

data class PlaybackPlan(
    val item: MediaItem,
    val source: MediaSource,
    val method: PlayMethod,
    val streamUrl: OpaqueUrl,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val startAt: Ticks,
    val playSessionId: PlaySessionId,
    val externalSubtitles: List<ExternalSubtitle>,
) {
    private val video = source.videoStream
    val stereo: StereoLayout = StereoRules.layoutFor(source.video3DFormat)
    val projection: Projection = StereoRules.projectionFor(source.name ?: item.name)
    val perEyeAspect: Float = StereoRules.perEyeAspect(source.video3DFormat, video?.width, video?.height)

    val runtime: Ticks? get() = source.runtime ?: item.runtime
    /** Remux and transcode both run a server-side stream: track changes need a new PlaybackInfo and HLS jobs need pings. */
    val isServerStream: Boolean get() = method != PlayMethod.DirectPlay
    val isHls: Boolean get() = isServerStream && source.transcodingProtocol == TranscodingProtocol.Hls
    /** The audio the delivered media carries, in its order: a server stream carries only the plan's. */
    val deliveredAudio: List<AudioStream> get() = if (isServerStream) source.audioStreams.filter { it.index == audioStreamIndex } else source.audioStreams
    /** The subtitles inside the delivered media, in its order; [externalSubtitles] are side-loaded after them. */
    val streamSubtitles: List<SubtitleStream> get() = source.subtitleStreams.filter { it.delivery == SubtitleDelivery.Embed || it.delivery == SubtitleDelivery.Hls }
    /** Stereo and 180/360 video only look right in the immersive cinema; flat 2D also plays in the window. */
    val needsImmersive: Boolean get() = stereo != StereoLayout.Mono || projection != Projection.Rectilinear
}

data class Chapter(val name: String?, val start: Ticks)

/**
 * Scrub previews: [count] thumbnails, one every [interval] of the video, laid out [columns] to a row and [perSheet] to
 * each of the [sheets]. [headers] sign a sheet's request in.
 */
data class Trickplay(
    val sheets: List<OpaqueUrl>,
    val headers: Map<String, String>,
    val columns: Int,
    val perSheet: Int,
    val count: Int,
    val interval: Ticks,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
) {
    fun thumbnailAt(position: Ticks): TrickplayThumbnail {
        val index = (position.value / interval.value).toInt().coerceAtMost(count - 1)
        val cell = index % perSheet
        return TrickplayThumbnail(sheets[index / perSheet], headers, cell % columns * thumbnailWidth, cell / columns * thumbnailHeight, thumbnailWidth, thumbnailHeight)
    }
}

/** The [width] by [height] pixels at [x], [y] of the image at [sheet]. */
data class TrickplayThumbnail(val sheet: OpaqueUrl, val headers: Map<String, String>, val x: Int, val y: Int, val width: Int, val height: Int)

enum class MediaSegmentKind { Intro, Outro, Recap, Preview, Commercial, Unknown }

data class MediaSegment(val kind: MediaSegmentKind, val start: Ticks, val end: Ticks) {
    operator fun contains(position: Ticks): Boolean = position >= start && position < end
}
