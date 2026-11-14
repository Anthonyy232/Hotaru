package com.anthonyla.hotaru.application.playback

import com.anthonyla.hotaru.application.logFailure
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.MediaPlayer
import com.anthonyla.hotaru.application.ports.PlaybackReport
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.SegmentAction
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.domain.Chapter
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSegmentKind
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.Trickplay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class PlaybackExtras(
    val chapters: List<Chapter> = emptyList(),
    val trickplay: Trickplay? = null,
    val segments: List<MediaSegment> = emptyList(),
)

/**
 * Jellyfin playback reporting for one plan. Paused progress reports (and HLS pings) keep transcodes
 * alive past the server's ~60 s kill timer. It runs in the app scope, so moving between browse, window and immersive never
 * interrupts it.
 */
class PlaySession(
    val plan: PlaybackPlan,
    val player: MediaPlayer,
    internal val owner: ActiveSession,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val gateway = owner.connection.playback

    private val _extras = MutableStateFlow(PlaybackExtras())
    val extras: StateFlow<PlaybackExtras> = _extras.asStateFlow()

    private val _activeSegment = MutableStateFlow<MediaSegment?>(null)
    val activeSegment: StateFlow<MediaSegment?> = _activeSegment.asStateFlow()

    /** Server stream indexes currently selected, starting from the plan's. */
    var selectedAudio: Int? = plan.audioStreamIndex
        private set
    var selectedSubtitle: Int? = plan.subtitleStreamIndex
        private set

    private val skippedSegments = mutableSetOf<MediaSegment>()
    private var started = false
    private val job = scope.launch {
        launch { reportingLoop() }
        launch { loadExtras() }
        launch { player.tracks.first { it.audio.isNotEmpty() || it.subtitles.isNotEmpty() }; selectTracks(selectedAudio, selectedSubtitle) }
        segmentLoop()
    }

    /** Selects server streams among the player's tracks; a null subtitle turns subtitles off. */
    fun selectTracks(audio: Int?, subtitle: Int?) {
        selectedAudio = audio
        selectedSubtitle = subtitle
        val tracks = player.tracks.value
        audio?.let { a -> tracks.audio.firstOrNull { it.serverStreamIndex == a } }?.let { player.selectAudio(it.id) }
        player.selectSubtitle(subtitle?.let { s -> tracks.subtitles.firstOrNull { it.serverStreamIndex == s } }?.id)
    }

    fun skipActiveSegment() {
        skip(_activeSegment.value ?: return)
        _activeSegment.value = null
    }

    private fun skip(segment: MediaSegment) {
        skippedSegments += segment
        player.seekTo(segment.end)
    }

    /** Releases the player at once; the final report goes out from the app scope, so a slow server never holds up a close. */
    fun stop() {
        job.cancel()
        val last = report(player.position.value, paused = true)
        player.release()
        if (started) scope.launch { gateway.reportStopped(last).logFailure(logger, TAG, "report stopped") }
    }

    private suspend fun reportingLoop() {
        // A restart can begin paused; it reports from then on, so its transcode is kept alive.
        val ready = player.state.first { it is PlayerState.Playing || it is PlayerState.Paused }
        started = true
        gateway.reportStart(report(player.position.value, paused = ready is PlayerState.Paused)).logFailure(logger, TAG, "report start")
        while (true) {
            val running = player.state.value.isRunning
            // A pause or resume is reported as it happens, not at the next interval.
            withTimeoutOrNull(if (running) PLAYING_INTERVAL else PAUSED_INTERVAL) { player.state.first { it.isRunning != running } }
            val state = player.state.value
            if (state is PlayerState.Ended) break
            val pausedNow = !state.isRunning
            gateway.reportProgress(report(player.position.value, paused = pausedNow)).logFailure(logger, TAG, "report progress")
            if (pausedNow && plan.isHls) {
                gateway.ping(plan.playSessionId).logFailure(logger, TAG, "ping")
            }
        }
    }

    /** Buffering after a seek or on a slow network is still playing to the server and to other clients. */
    private val PlayerState.isRunning get() = this is PlayerState.Playing || this is PlayerState.Buffering

    private suspend fun segmentLoop() {
        combine(player.position, _extras.map { it.segments }.distinctUntilChanged()) { pos, segs ->
            segs.firstOrNull { pos in it && it !in skippedSegments }
        }.collect { hit ->
            when {
                hit == null -> _activeSegment.value = null
                hit != _activeSegment.value -> when (actionFor(hit.kind)) {
                    SegmentAction.Ignore -> _activeSegment.value = null
                    SegmentAction.Skip -> skip(hit)
                    SegmentAction.Prompt -> _activeSegment.value = hit
                }
            }
        }
    }

    private fun actionFor(kind: MediaSegmentKind): SegmentAction {
        val s = settings.settings.value
        return when (kind) {
            MediaSegmentKind.Intro -> s.introAction
            MediaSegmentKind.Outro -> s.outroAction
            MediaSegmentKind.Recap, MediaSegmentKind.Preview -> s.recapAction
            MediaSegmentKind.Commercial -> SegmentAction.Skip
            MediaSegmentKind.Unknown -> SegmentAction.Ignore
        }
    }

    private suspend fun loadExtras() = coroutineScope {
        val tl = async { gateway.timeline(plan.item.id, plan.source.id).getOrNull() }
        val sg = async { if (plan.source.hasSegments) gateway.segments(plan.item.id).getOrNull().orEmpty() else emptyList() }
        val timeline = tl.await()
        _extras.value = PlaybackExtras(chapters = timeline?.chapters.orEmpty(), trickplay = timeline?.trickplay, segments = sg.await())
    }

    private fun report(position: Ticks, paused: Boolean) = PlaybackReport(
        itemId = plan.item.id,
        mediaSourceId = plan.source.id,
        playSessionId = plan.playSessionId,
        position = position,
        isPaused = paused,
        isMuted = player.volume.value <= 0f,
        volume = (player.volume.value * 100).toInt(),
        audioStreamIndex = selectedAudio,
        subtitleStreamIndex = selectedSubtitle,
        method = plan.method,
    )

    companion object {
        private const val TAG = "PlaySession"
        private val PLAYING_INTERVAL = 3.seconds
        private val PAUSED_INTERVAL = 15.seconds
    }
}

fun MediaPlayer.togglePlayPause() = if (state.value is PlayerState.Paused) play() else pause()

/** Never before the start. */
fun MediaPlayer.seekBy(delta: Duration) = seekTo((position.value + delta).coerceAtLeast(Ticks.ZERO))
