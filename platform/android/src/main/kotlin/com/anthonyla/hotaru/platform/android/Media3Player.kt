package com.anthonyla.hotaru.platform.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.net.Uri
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.effect.OverlayEffect
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.extractor.DefaultExtractorsFactory
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.MediaPlayer
import com.anthonyla.hotaru.application.ports.PlaybackFailure
import com.anthonyla.hotaru.application.ports.PlayerFactory
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.PlayerTrack
import com.anthonyla.hotaru.application.ports.PlayerTracks
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.ports.SubtitleSize
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.Ticks
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

@OptIn(UnstableApi::class)
internal class Media3Player(
    context: Context,
    okHttp: OkHttpClient,
    private val logger: Logger,
    private val plan: PlaybackPlan,
    headers: Map<String, String>,
    subtitleSize: SubtitleSize,
) : MediaPlayer {
    override val state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    override val position = MutableStateFlow(plan.startAt)
    override val duration = MutableStateFlow(plan.runtime)
    override val buffered = MutableStateFlow(Ticks.ZERO)
    override val tracks = MutableStateFlow(PlayerTracks())
    override val volume = MutableStateFlow(1f)

    private val httpFactory = OkHttpDataSource.Factory(okHttp).setDefaultRequestProperties(headers)
    private val subtitleStreams = plan.streamSubtitles + plan.externalSubtitles.map { it.stream }
    // In this mode the library only loads a styled track and its fonts into libass, for the overlay to draw from; its cue
    // mode draws each line once, so nothing animates.
    private val ass = AssHandler(AssRenderType.OVERLAY_CANVAS)
    private val assParsers = AssSubtitleParserFactory(ass)
    private val subtitleDelayUs = AtomicLong()
    // The effect costs every frame a pass through Media3's GL pipeline.
    private val subtitleOverlay = if (subtitleStreams.isEmpty()) null else SubtitleOverlay(context, plan.stereo, plan.projection, plan.perEyeAspect, subtitleSize.scale, ass, subtitleDelayUs)
    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .setHandleAudioBecomingNoisy(true)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory), DefaultExtractorsFactory().withAssMkvSupport(assParsers, ass))
                .setSubtitleParserFactory(assParsers),
        )
        // The FFmpeg extension decodes DTS/TrueHD/FLAC on device; Android downmixes it.
        .setRenderersFactory(DelayedTextRenderers(context, subtitleDelayUs).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER).withAssSupport(ass))
        .build()
        .also(ass::init)
        .apply {
            addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_PLAYER_ERROR)) publishState()
                    if (events.contains(Player.EVENT_TRACKS_CHANGED)) publishTracks(player.currentTracks)
                }
                override fun onCues(cueGroup: CueGroup) { subtitleOverlay?.cues = cueGroup.cues }
                override fun onPlayerError(error: PlaybackException) = logger.error(TAG, { "player error ${error.errorCodeName}" }, error)
            })
        }

    init {
        subtitleOverlay?.let { player.setVideoEffects(listOf(OverlayEffect(listOf(it)))) }
        player.setMediaItem(
            // Only HLS needs naming; Media3 finds a file's container itself.
            MediaItem.Builder().setUri(plan.streamUrl.value).setMimeType(MimeTypes.APPLICATION_M3U8.takeIf { plan.isHls })
                .setSubtitleConfigurations(
                    plan.externalSubtitles.map {
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url.value))
                            .setMimeType(it.format.mimeType).setLanguage(it.stream.language).setLabel(it.stream.displayTitle ?: it.stream.title).build()
                    },
                ).build(),
            plan.startAt.milliseconds,
        )
        player.prepare()
        // Media3 has no position callback, so poll until release.
        scope.launch {
            while (true) {
                position.value = Ticks.ofMillis(player.currentPosition.coerceAtLeast(0))
                buffered.value = Ticks.ofMillis(player.bufferedPosition.coerceAtLeast(0))
                delay(250)
            }
        }
        logger.info(TAG) { "load ${plan.method} hls=${plan.isHls} start=${plan.startAt.milliseconds}ms audio=${plan.audioStreamIndex} subtitle=${plan.subtitleStreamIndex}" }
    }

    private fun publishState() {
        val error = player.playerError
        state.value = when {
            error != null -> PlayerState.Error(failureOf(error))
            player.playbackState == Player.STATE_ENDED -> PlayerState.Ended
            // Paused while it buffers is paused: it won't start when the buffer fills.
            player.playbackState == Player.STATE_BUFFERING && player.playWhenReady -> PlayerState.Buffering
            player.playbackState == Player.STATE_IDLE -> PlayerState.Idle
            player.isPlaying -> PlayerState.Playing
            else -> PlayerState.Paused
        }
        val d = player.duration
        if (d > 0) duration.value = Ticks.ofMillis(d)
    }

    // Media3 groups its error codes by thousands: 2xxx input/output, 3xxx parsing, 4xxx decoding.
    private fun failureOf(error: PlaybackException): PlaybackFailure = when (error.errorCode / 1000) {
        2 -> PlaybackFailure.Network
        3, 4 -> PlaybackFailure.Unplayable
        else -> PlaybackFailure.Other
    }

    private fun publishTracks(t: Tracks) {
        tracks.value = PlayerTracks(
            audio = playerTracks(t, C.TRACK_TYPE_AUDIO, plan.deliveredAudio.map { it.index to it.displayTitle }, "Audio"),
            subtitles = playerTracks(t, C.TRACK_TYPE_TEXT, subtitleStreams.map { it.index to it.displayTitle }, "Subtitle"),
        )
    }

    /**
     * Supported tracks of [type] that pair with a server stream. Every track, supported or not, pairs in order with one of
     * [streams]; tracks past them (captions inside the video) have no server index to select them by.
     */
    private fun playerTracks(t: Tracks, type: Int, streams: List<Pair<Int, String?>>, fallbackLabel: String): List<PlayerTrack> {
        val result = mutableListOf<PlayerTrack>()
        var paired = 0
        t.groups.forEachIndexed { gi, group ->
            if (group.type != type) return@forEachIndexed
            for (i in 0 until group.length) {
                val (index, title) = streams.getOrNull(paired++) ?: break
                if (!group.isTrackSupported(i)) continue
                val f = group.getTrackFormat(i)
                result += PlayerTrack("$gi:$i", title ?: f.label ?: f.language ?: "$fallbackLabel ${result.size + 1}", group.isTrackSelected(i), index)
            }
        }
        return result
    }

    override fun renderTo(target: VideoRenderTarget?) { player.setVideoSurfaceHolder(target?.let { SizedSurface(it.surface as Surface, it.width, it.height) }) }
    override fun play() { player.playWhenReady = true }
    override fun pause() { player.playWhenReady = false }
    override fun seekTo(position: Ticks) { player.seekTo(position.milliseconds); this.position.value = position }

    override fun selectAudio(trackId: String) = updateTrackSelection { trackOverride(C.TRACK_TYPE_AUDIO, trackId)?.let { setOverrideForType(it) } }

    override fun selectSubtitle(trackId: String?) = updateTrackSelection {
        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, trackId == null)
        if (trackId != null) trackOverride(C.TRACK_TYPE_TEXT, trackId)?.let { setOverrideForType(it) }
    }

    private fun updateTrackSelection(change: TrackSelectionParameters.Builder.() -> Unit) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply(change).build()
    }

    /** Null when the tracks changed since [trackId] was published. */
    private fun trackOverride(type: Int, trackId: String): TrackSelectionOverride? {
        val (gi, ti) = trackId.split(':').map { it.toInt() }
        val group = player.currentTracks.groups.getOrNull(gi)?.takeIf { it.type == type } ?: return null
        return TrackSelectionOverride(group.mediaTrackGroup, ti)
    }

    override fun setSubtitleDelay(delay: Duration) = subtitleDelayUs.set(delay.inWholeMicroseconds)

    override fun setSpeed(speed: Float) = player.setPlaybackSpeed(speed)

    override fun setVolume(volume: Float) { this.volume.value = volume; player.volume = this.volume.value }

    override fun release() {
        scope.cancel()
        player.release()
        ass.release()
        state.value = PlayerState.Idle
    }

    companion object { private const val TAG = "Media3Player" }
}

@ContributesBinding(AppScope::class)
class Media3PlayerFactory(private val context: Context, private val okHttp: OkHttpClient, private val logger: Logger, private val settings: SettingsStore) : PlayerFactory {
    override fun create(plan: PlaybackPlan, headers: Map<String, String>): MediaPlayer =
        Media3Player(context, okHttp, logger, plan, headers, settings.settings.value.subtitleSize)
}

/** Media3 has no subtitle delay, so the text renderer is told a position [delayUs] behind the real one. */
@OptIn(UnstableApi::class)
private class DelayedTextRenderers(context: Context, private val delayUs: AtomicLong) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(context: Context, output: TextOutput, outputLooper: Looper, extensionRendererMode: Int, out: ArrayList<Renderer>) {
        out += object : ForwardingRenderer(TextRenderer(output, outputLooper)) {
            override fun render(positionUs: Long, elapsedRealtimeUs: Long) = super.render(positionUs - delayUs.get(), elapsedRealtimeUs)

            // Media3 ends a text renderer with the video only when it is a bare TextRenderer; wrapped, a cue that outlasts
            // the video would keep playback from ending.
            override fun isEnded() = super.isEnded() || isCurrentStreamFinal
        }
    }
}

/**
 * Media3 learns a surface's size only from a [SurfaceHolder]. Given a bare [Surface] it first reports the size as unknown,
 * and the subtitle effect fails on any frame presented before the size arrives, as happens when playback moves between the
 * window and the cinema. Both hosts know their surface's size, so it goes in with the surface.
 */
private class SizedSurface(private val surface: Surface, width: Int, height: Int) : SurfaceHolder {
    private val frame = Rect(0, 0, width, height)
    override fun getSurface() = surface
    override fun getSurfaceFrame() = frame
    override fun isCreating() = false
    // The host reports a resize or loss of its surface through the stage, not through holder callbacks.
    override fun addCallback(callback: SurfaceHolder.Callback) = Unit
    override fun removeCallback(callback: SurfaceHolder.Callback) = Unit
    @Deprecated("Ignored by every surface since API 11")
    override fun setType(type: Int) = Unit
    override fun setFixedSize(width: Int, height: Int) = Unit
    override fun setSizeFromLayout() = Unit
    override fun setFormat(format: Int) = Unit
    override fun setKeepScreenOn(screenOn: Boolean) = Unit
    override fun lockCanvas(): Canvas? = null
    override fun lockCanvas(dirty: Rect?): Canvas? = null
    override fun unlockCanvasAndPost(canvas: Canvas) = Unit
}
