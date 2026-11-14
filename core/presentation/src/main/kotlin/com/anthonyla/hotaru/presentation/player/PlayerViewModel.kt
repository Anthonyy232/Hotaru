package com.anthonyla.hotaru.presentation.player

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.playback.PlaybackCoordinator
import com.anthonyla.hotaru.application.playback.PlaybackExtras
import com.anthonyla.hotaru.application.playback.PlaySession
import com.anthonyla.hotaru.application.playback.seekBy
import com.anthonyla.hotaru.application.playback.togglePlayPause
import com.anthonyla.hotaru.application.ports.PlaybackFailure
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.PlayerTracks
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.domain.DisplayTitles
import com.anthonyla.hotaru.domain.Environment
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSegmentKind
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.PlayMethod
import com.anthonyla.hotaru.domain.Projection
import com.anthonyla.hotaru.domain.ScreenGeometry
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.StageState
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.TrickplayThumbnail
import com.anthonyla.hotaru.presentation.userMessage
import com.anthonyla.hotaru.presentation.ViewModel
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

/** A row in the track picker and what choosing it does. */
data class TrackOption(val label: String, val selected: Boolean, val intent: PlayerIntent)

data class PlayerUiState(
    /** The last plan played; it stays while the controls fade out. */
    val plan: PlaybackPlan? = null,
    val playerState: PlayerState = PlayerState.Idle,
    val position: Ticks = Ticks.ZERO,
    val duration: Ticks? = null,
    val buffered: Ticks = Ticks.ZERO,
    val tracks: PlayerTracks = PlayerTracks(),
    val controlsVisible: Boolean = true,
    val extras: PlaybackExtras = PlaybackExtras(),
    val activeSegment: MediaSegment? = null,
    val stage: StageState,
    val showTrackPicker: Boolean = false,
    val showCinemaSettings: Boolean = false,
    /** Set while the user drags the seek bar. */
    val scrubPosition: Ticks? = null,
    /** Why the last track change or retry failed; what played keeps playing, and the next restart that works clears it. */
    val restartError: String? = null,
    /** The viewer is moving or resizing the cinema screen. */
    val screenHeld: Boolean = false,
    /** 1 is normal; it lasts through the next episode and a restart, until playback closes. */
    val speed: Float = 1f,
    /** How much later than their own times subtitles show; it lasts as long as [speed]. */
    val subtitleDelay: Duration = Duration.ZERO,
) {
    val error: String? get() = restartError ?: (playerState as? PlayerState.Error)?.failure?.let(::failureMessage)
    val title: String get() = plan?.item?.let(DisplayTitles::headline).orEmpty()
    val subtitle: String? get() = plan?.item?.let { item -> (item as? Episode)?.let(DisplayTitles::episodeTitle) ?: item.productionYear?.toString() }
    val isPlaying: Boolean get() = playerState is PlayerState.Playing
    /** The play button pauses: playing, or about to once it has loaded or buffered. */
    val canPause: Boolean get() = isPlaying || isBuffering
    /** Play starts a failed playback over. */
    val canRetry: Boolean get() = playerState is PlayerState.Error
    val isBuffering: Boolean get() = playerState is PlayerState.Buffering || playerState is PlayerState.Loading
    private val shownPosition: Ticks get() = scrubPosition ?: position
    val positionText: String get() = DisplayTitles.clock(shownPosition)
    val remainingText: String get() = duration?.let { "-" + DisplayTitles.clock(it - shownPosition) } ?: ""
    val progress: Float get() = fraction(shownPosition)
    val bufferedFraction: Float get() = fraction(buffered)
    val chapterFractions: List<Float> get() = if (duration == null) emptyList() else extras.chapters.map { fraction(it.start) }

    val scrubLabel: String? get() = scrubPosition?.let { pos -> listOfNotNull(extras.chapters.lastOrNull { it.start <= pos }?.name, DisplayTitles.clock(pos)).joinToString(" · ") }
    val scrubThumbnail: TrickplayThumbnail? get() = scrubPosition?.let { extras.trickplay?.thumbnailAt(it) }
    val skipSegmentLabel: String? get() = activeSegment?.let { "Skip " + segmentName(it.kind) }

    /** A server stream carries only the tracks chosen for it, so it offers all of its source's and restarts to switch. */
    private val serverStream: PlaybackPlan? get() = plan?.takeIf { it.isServerStream }

    val audioOptions: List<TrackOption> get() = serverStream?.let { p ->
        p.source.audioStreams.mapIndexed { i, s -> TrackOption(s.displayTitle ?: "Audio ${i + 1}", s.index == p.audioStreamIndex, PlayerIntent.SelectAudio(s.index)) }
    } ?: tracks.audio.map { TrackOption(it.label, it.selected, PlayerIntent.SelectAudio(it.serverStreamIndex)) }

    val subtitleOptions: List<TrackOption> get() {
        val subtitles = serverStream?.let { p ->
            p.source.subtitleStreams.mapIndexed { i, s -> TrackOption(s.displayTitle ?: "Subtitle ${i + 1}", s.index == p.subtitleStreamIndex, PlayerIntent.SelectSubtitle(s.index)) }
        } ?: tracks.subtitles.map { TrackOption(it.label, it.selected, PlayerIntent.SelectSubtitle(it.serverStreamIndex)) }
        return listOf(TrackOption("Off", subtitles.none { it.selected }, PlayerIntent.SelectSubtitle(null))) + subtitles
    }

    val canWatchInImmersive: Boolean get() = stage.mode == StageMode.Window
    val canWatchInWindow: Boolean get() = stage.mode == StageMode.Immersive && plan?.needsImmersive == false

    val canAdjustCinema: Boolean get() = stage.mode == StageMode.Immersive
    /** 180 and 360 video surround the viewer, so only a rectilinear screen has a size. */
    val canAdjustScreen: Boolean get() = canAdjustCinema && plan?.projection == Projection.Rectilinear

    val isTranscoding: Boolean get() = plan?.method == PlayMethod.Transcode

    /** Only subtitles the player draws can move; ones the server put in the picture cannot. */
    val canDelaySubtitles: Boolean get() = tracks.subtitles.any { it.selected }
    val subtitleDelayText: String get() = "%+.2f s".format(subtitleDelay.toDouble(DurationUnit.SECONDS))

    /** Screen width as a 0..1 position between [ScreenGeometry.MIN_WIDTH_METERS] and [ScreenGeometry.MAX_WIDTH_METERS]. */
    val screenSizeFraction: Float get() = (stage.screen.widthMeters - ScreenGeometry.MIN_WIDTH_METERS) / SCREEN_WIDTH_SPAN
    val screenWidthText: String get() = "%.1f m".format(stage.screen.widthMeters)

    private fun fraction(ticks: Ticks): Float = duration?.let { ticks.fractionOf(it).toFloat() } ?: 0f
}

private const val SCREEN_WIDTH_SPAN = ScreenGeometry.MAX_WIDTH_METERS - ScreenGeometry.MIN_WIDTH_METERS

private fun failureMessage(failure: PlaybackFailure): String = when (failure) {
    PlaybackFailure.Network -> "Lost the connection to the server."
    PlaybackFailure.Unplayable -> "This video can't be played."
    PlaybackFailure.Other -> "Playback stopped unexpectedly."
}

private fun segmentName(kind: MediaSegmentKind): String = when (kind) {
    MediaSegmentKind.Intro -> "intro"
    MediaSegmentKind.Outro -> "outro"
    MediaSegmentKind.Recap -> "recap"
    MediaSegmentKind.Preview -> "preview"
    MediaSegmentKind.Commercial -> "ad"
    MediaSegmentKind.Unknown -> "segment"
}

object PlayerOptions {
    val speed = listOf(0.5f to "0.5×", 0.75f to "0.75×", 1f to "1×", 1.25f to "1.25×", 1.5f to "1.5×", 2f to "2×")
    val subtitleDelayStep = 250.milliseconds
}

sealed interface PlayerIntent {
    /** Controller B / system Back, and the bar's own arrow: closes the popover, else stops. */
    data object Back : PlayerIntent
    /** After a playback error, retries. */
    data object TogglePlayPause : PlayerIntent
    data object ToggleControls : PlayerIntent
    /** Seek bar positions are 0..1 of the duration. */
    data class SeekTo(val fraction: Float) : PlayerIntent
    /** Null when the drag ends. */
    data class Scrub(val fraction: Float?) : PlayerIntent
    data class SeekBy(val delta: Duration) : PlayerIntent
    data object ToggleTrackPicker : PlayerIntent
    data object ToggleCinemaSettings : PlayerIntent
    data class SelectAudio(val serverStreamIndex: Int) : PlayerIntent
    /** null turns subtitles off. */
    data class SelectSubtitle(val serverStreamIndex: Int?) : PlayerIntent
    data class SetSpeed(val speed: Float) : PlayerIntent
    /** Later by this much, or earlier when negative. */
    data class ShiftSubtitles(val by: Duration) : PlayerIntent
    data object SkipSegment : PlayerIntent
    /** The size slider, 0..1 like [PlayerUiState.screenSizeFraction]. */
    data class ResizeScreen(val fraction: Float) : PlayerIntent
    /** The cinema screen was grabbed to move or resize it, or let go. */
    data class ScreenHeld(val held: Boolean) : PlayerIntent
    /** A hand resize of the screen in the cinema finished at this width. */
    data class ScreenResized(val widthMeters: Float) : PlayerIntent
    data class SetEnvironment(val environment: Environment) : PlayerIntent
    data object Stop : PlayerIntent
    data object WatchInImmersive : PlayerIntent
    /** Back to the window; content that needs immersive stops instead. */
    data object ExitImmersive : PlayerIntent
    /** The headset came off or went back on: taking it off pauses, and putting it on resumes only what that paused. */
    data class HeadsetWorn(val worn: Boolean) : PlayerIntent
}

@Singleton
class PlayerViewModel @Inject constructor(
    scope: CoroutineScope,
    private val playback: PlaybackCoordinator,
    stage: Stage,
    private val settings: SettingsStore,
) : ViewModel<PlayerUiState, PlayerIntent>(PlayerUiState(stage = stage.state.value), scope) {

    private var pausedForHeadset = false
    /** Bumped by each input, which starts the auto-hide wait over. */
    private val inputs = MutableStateFlow(0)

    init {
        launch {
            stage.state.collect { s ->
                val immersive = s.mode == StageMode.Immersive
                update { it.copy(stage = s, showCinemaSettings = it.showCinemaSettings && immersive, screenHeld = it.screenHeld && immersive) }
            }
        }
        launch {
            playback.current.collectLatest { session ->
                when {
                    session != null -> bind(session)
                    playback.startingNext.value -> update { it.copy(playerState = PlayerState.Loading) }
                    else -> update { it.copy(speed = 1f, subtitleDelay = Duration.ZERO) }
                }
            }
        }
        launch {
            combine(state.map { it.autoHides }.distinctUntilChanged(), inputs) { autoHides, _ -> autoHides }
                .collectLatest { autoHides -> if (autoHides) { delay(5.seconds); update { it.copy(controlsVisible = false) } } }
        }
    }

    private suspend fun bind(session: PlaySession) {
        pausedForHeadset = false
        update {
            it.copy(
                plan = session.plan,
                controlsVisible = true,
                showTrackPicker = false,
                showCinemaSettings = false,
                scrubPosition = null,
                restartError = null,
            )
        }
        restartHideWait()
        val p = session.player
        p.setSpeed(current.speed)
        p.setSubtitleDelay(current.subtitleDelay)
        val snapshots = combine(p.state, p.position, p.duration, p.buffered, p.tracks, ::Snapshot)
        combine(snapshots, session.extras, session.activeSegment, ::Triple)
            .collectLatest { (snap, extras, seg) ->
                update {
                    val failed = snap.state is PlayerState.Error && it.playerState !is PlayerState.Error
                    it.copy(
                        playerState = snap.state, position = snap.position, duration = snap.duration, buffered = snap.buffered, tracks = snap.tracks,
                        extras = extras, activeSegment = seg, controlsVisible = it.controlsVisible || failed,
                    )
                }
            }
    }

    private data class Snapshot(val state: PlayerState, val position: Ticks, val duration: Ticks?, val buffered: Ticks, val tracks: PlayerTracks)

    override fun onIntent(intent: PlayerIntent) {
        val session = playback.current.value
        val player = session?.player
        when (intent) {
            PlayerIntent.Back ->
                if (current.showTrackPicker || current.showCinemaSettings) update { it.copy(showTrackPicker = false, showCinemaSettings = false) }
                else onIntent(PlayerIntent.Stop)
            PlayerIntent.TogglePlayPause -> {
                if (current.canRetry) launch { playback.retry().onFailure(::showRestartError) } else player?.togglePlayPause()
                showControls()
            }
            PlayerIntent.ToggleControls -> if (current.controlsVisible) hideControls() else showControls()
            is PlayerIntent.SeekTo -> { current.duration?.let { player?.seekTo(it * intent.fraction) }; showControls() }
            is PlayerIntent.Scrub -> { update { st -> st.copy(scrubPosition = intent.fraction?.let { f -> st.duration?.let { it * f } }) }; if (intent.fraction != null) showControls() }
            is PlayerIntent.SeekBy -> { player?.seekBy(intent.delta); showControls() }
            PlayerIntent.ToggleTrackPicker -> { update { it.copy(showTrackPicker = !it.showTrackPicker, showCinemaSettings = false) }; showControls() }
            PlayerIntent.ToggleCinemaSettings -> { update { it.copy(showCinemaSettings = !it.showCinemaSettings, showTrackPicker = false) }; showControls() }
            is PlayerIntent.SelectAudio -> launch { playback.selectAudio(intent.serverStreamIndex).onFailure(::showRestartError) }
            is PlayerIntent.SelectSubtitle -> launch { playback.selectSubtitle(intent.serverStreamIndex).onFailure(::showRestartError) }
            is PlayerIntent.SetSpeed -> { player?.setSpeed(intent.speed); update { it.copy(speed = intent.speed) } }
            is PlayerIntent.ShiftSubtitles -> {
                val delay = current.subtitleDelay + intent.by
                player?.setSubtitleDelay(delay)
                update { it.copy(subtitleDelay = delay) }
            }
            PlayerIntent.SkipSegment -> session?.skipActiveSegment()
            is PlayerIntent.ResizeScreen -> setCinema { it.copy(cinemaScreenWidthMeters = ScreenGeometry.MIN_WIDTH_METERS + intent.fraction * SCREEN_WIDTH_SPAN) }
            is PlayerIntent.ScreenHeld -> update { it.copy(screenHeld = intent.held) }
            is PlayerIntent.ScreenResized -> setCinema { it.copy(cinemaScreenWidthMeters = intent.widthMeters) }
            is PlayerIntent.SetEnvironment -> setCinema { it.copy(cinemaEnvironment = intent.environment) }
            PlayerIntent.Stop -> launch { playback.stop() }
            PlayerIntent.WatchInImmersive -> launch { playback.setImmersive(true) }
            PlayerIntent.ExitImmersive -> launch { playback.setImmersive(false) }
            is PlayerIntent.HeadsetWorn -> when {
                // A buffering player would start once its buffer fills.
                !intent.worn && current.canPause -> { player?.pause(); pausedForHeadset = true }
                intent.worn && pausedForHeadset -> { player?.play(); pausedForHeadset = false }
            }
        }
    }

    private fun showRestartError(error: AppError) = update { it.copy(restartError = error.userMessage()) }

    private fun setCinema(transform: (Settings) -> Settings) = launch { settings.update(transform) }

    private fun hideControls() = update { it.copy(controlsVisible = false, showTrackPicker = false, showCinemaSettings = false) }

    private fun showControls() {
        update { it.copy(controlsVisible = true) }
        restartHideWait()
    }

    private fun restartHideWait() = inputs.update { it + 1 }
}

/** Shown controls hide after a quiet spell, but only while playback runs and nothing holds them up. */
private val PlayerUiState.autoHides: Boolean
    get() = controlsVisible && isPlaying && !showTrackPicker && !showCinemaSettings && scrubPosition == null && !screenHeld
