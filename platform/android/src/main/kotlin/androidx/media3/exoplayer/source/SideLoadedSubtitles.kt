package androidx.media3.exoplayer.source

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.SubtitleExtractor
import androidx.media3.extractor.text.SubtitleParser

/**
 * A subtitle file fetched when its track is first selected. [DefaultMediaSourceFactory]'s own takes the file's duration
 * from the cues its first load queued: zero when started past the last cue, and then no seek back loads one. Here the
 * extractor has the track's format and reports the duration it parsed. In this package because Media3 keeps lazy loading to it.
 */
@OptIn(UnstableApi::class)
internal fun sideLoadedSubtitles(dataSource: DataSource.Factory, parsers: SubtitleParser.Factory, uri: Uri, format: Format): MediaSource {
    // The format SubtitleExtractor announces for the track.
    val cues = format.buildUpon()
        .setSampleMimeType(MimeTypes.APPLICATION_MEDIA3_CUES).setCodecs(format.sampleMimeType).setCueReplacementBehavior(parsers.getCueReplacementBehavior(format)).build()
    return ProgressiveMediaSource.Factory(dataSource, ExtractorsFactory { arrayOf(SubtitleExtractor(parsers.create(format), format)) })
        .enableLazyLoadingWithSingleTrack(SubtitleExtractor.TRACK_ID, cues)
        .createMediaSource(MediaItem.fromUri(uri))
}
