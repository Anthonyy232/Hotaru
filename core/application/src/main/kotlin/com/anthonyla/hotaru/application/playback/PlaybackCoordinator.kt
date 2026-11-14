package com.anthonyla.hotaru.application.playback

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.flatMap
import com.anthonyla.hotaru.application.map
import com.anthonyla.hotaru.application.onFailure
import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.PlayerFactory
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.itemAfter
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.Ticks
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A playback's requests go through the session that owns it, never whichever is active, so an account switch can't redirect them. */
@Singleton
class PlaybackCoordinator @Inject constructor(
    private val sessions: SessionManager,
    private val prepare: PreparePlayback,
    private val players: PlayerFactory,
    private val stage: Stage,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    private val logger: Logger,
) {
    private val mutex = Mutex()
    private val _current = MutableStateFlow<PlaySession?>(null)
    val current: StateFlow<PlaySession?> = _current.asStateFlow()

    private val _startingNext = MutableStateFlow(false)
    /** True from the end of one item until the next one starts or fails; [current] is null meanwhile. */
    val startingNext: StateFlow<Boolean> = _startingNext.asStateFlow()

    private val _nextFailed = MutableSharedFlow<AppError>(extraBufferCapacity = 1)
    /** Why the next item couldn't start; playback has closed. */
    val nextFailed: SharedFlow<AppError> = _nextFailed.asSharedFlow()

    private var queue: List<MediaItem> = emptyList()
    private var endWatch: Job? = null
    private var advance: Job? = null

    init {
        scope.launch {
            sessions.active.collect {
                mutex.withLock {
                    val current = _current.value
                    if (current != null && current.owner !== sessions.active.value) {
                        close()
                    }
                }
            }
        }
    }

    suspend fun play(request: PlaybackRequest, queue: List<MediaItem> = emptyList()): Outcome<PlaySession> = mutex.withLock {
        sessions.withSession { active ->
            prepare(active, request).flatMap { plan ->
                this.queue = queue
                startPlan(plan, active)
            }
        }
    }

    private suspend fun startPlan(plan: PlaybackPlan, active: ActiveSession, play: Boolean = true): Outcome<PlaySession> {
        stopCurrent()
        // Preparing suspends, so the session may have changed since the request.
        if (sessions.active.value !== active) {
            stage.close()
            return AppError.Unauthorized.failure()
        }
        val player = players.create(plan, active.connection.playback.mediaRequestHeaders())
        val session = PlaySession(
            plan = plan,
            player = player,
            owner = active,
            settings = settings,
            scope = scope,
            logger = logger,
        )
        if (play) player.play()
        stage.show(plan, player)
        _current.value = session
        endWatch = scope.launch {
            player.state.first { it is PlayerState.Ended }
            // Not a child of the watch: advancing stops this session, and stopping cancels the watch.
            advance = scope.launch { mutex.withLock { if (_current.value === session) playNext() } }
        }
        return session.success()
    }

    suspend fun selectAudio(serverStreamIndex: Int): Outcome<Unit> =
        changeTracks { serverStreamIndex to it.selectedSubtitle }

    /** A null index turns subtitles off. */
    suspend fun selectSubtitle(serverStreamIndex: Int?): Outcome<Unit> =
        changeTracks { it.selectedAudio to serverStreamIndex }

    /** [tracks] reads the other selection under the lock, so a change waiting on it keeps the one before it. */
    private suspend fun changeTracks(tracks: (PlaySession) -> Pair<Int?, Int?>): Outcome<Unit> = mutex.withLock {
        val session = _current.value ?: return@withLock Unit.success()
        val (audioStreamIndex, subtitleStreamIndex) = tracks(session)
        if (audioStreamIndex == session.selectedAudio && subtitleStreamIndex == session.selectedSubtitle) return@withLock Unit.success()
        if (!session.plan.isServerStream) {
            session.selectTracks(audioStreamIndex, subtitleStreamIndex)
            return@withLock Unit.success()
        }
        restart(session, audioStreamIndex, subtitleStreamIndex)
    }

    /** Starts the current item over where it stopped with the same tracks, on a fresh plan: a dead transcode needs a new one. */
    suspend fun retry(): Outcome<Unit> = mutex.withLock {
        val session = _current.value ?: return@withLock Unit.success()
        restart(session, session.selectedAudio, session.selectedSubtitle)
    }

    /** Prepares the current item again at its position with these tracks; a paused video stays paused. */
    private suspend fun restart(session: PlaySession, audioStreamIndex: Int?, subtitleStreamIndex: Int?): Outcome<Unit> {
        val plan = session.plan
        val request = PlaybackRequest(
            item = plan.item,
            startAt = session.player.position.value,
            audioStreamIndex = audioStreamIndex,
            subtitles = subtitleStreamIndex?.let(SubtitleChoice::Stream) ?: SubtitleChoice.Off,
        )
        return prepare(session.owner, request).flatMap { startPlan(it, session.owner, play = session.player.state.value !is PlayerState.Paused) }.map { }
    }

    /** A remote client's NextTrack: what the end of playback does, whatever the autoplay setting. */
    suspend fun next() = mutex.withLock { playNext(requested = true) }

    private suspend fun playNext(requested: Boolean = false) {
        val current = _current.value ?: return
        val owner = current.owner
        if (!requested && !settings.settings.value.autoplayNext) {
            close()
            return
        }
        _startingNext.value = true
        try {
            val following = itemAfter(owner, current.plan.item)
            // A remote Next with nothing to follow leaves playback as it is.
            if (requested && following is Outcome.Success && following.value == null) return
            stopCurrent()
            following
                .flatMap { next -> if (next == null) null.success() else prepare(owner, PlaybackRequest(next, startAt = Ticks.ZERO)).flatMap { startPlan(it, owner) } }
                .onSuccess { if (it == null) stage.close() }
                .onFailure { stage.close(); _nextFailed.tryEmit(it) }
        } finally {
            _startingNext.value = false
        }
    }

    /** The queue's next item, else the series' next episode; null when nothing follows. */
    private suspend fun itemAfter(owner: ActiveSession, item: MediaItem): Outcome<MediaItem?> {
        queue.itemAfter(item.id)?.let { return it.success() }
        val seriesId = (item as? Episode)?.seriesId ?: return null.success()
        return owner.connection.library.episodes(seriesId, null).map { it.itemAfter(item.id) }
    }

    /** Same player either way, so playback continues; content that needs immersive can't play in the window, so leaving immersive stops it. */
    suspend fun setImmersive(immersive: Boolean) = mutex.withLock {
        val plan = _current.value?.plan ?: return@withLock
        if (!immersive && plan.needsImmersive) close() else stage.setImmersive(immersive)
    }

    /** Doesn't wait for a next item that is starting. */
    suspend fun stop() {
        advance?.cancel()
        mutex.withLock { close() }
    }

    private fun close() {
        stopCurrent()
        stage.close()
    }

    private fun stopCurrent() {
        endWatch?.cancel()
        stage.detachPlayer()
        _current.value?.stop()
        _current.value = null
    }
}
