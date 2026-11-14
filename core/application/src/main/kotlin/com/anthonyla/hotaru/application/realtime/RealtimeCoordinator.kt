package com.anthonyla.hotaru.application.realtime

import com.anthonyla.hotaru.application.library.GetItemDetail
import com.anthonyla.hotaru.application.logFailure
import com.anthonyla.hotaru.application.playback.PlaybackCoordinator
import com.anthonyla.hotaru.application.playback.PlaybackRequest
import com.anthonyla.hotaru.application.playback.seekBy
import com.anthonyla.hotaru.application.playback.togglePlayPause
import com.anthonyla.hotaru.application.ports.ItemCache
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.MediaPlayer
import com.anthonyla.hotaru.application.ports.PlaystateCommand
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.application.ports.VolumeCommand
import com.anthonyla.hotaru.application.session.ActiveSession
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.application.userdata.UserDataEvents
import com.anthonyla.hotaru.domain.Ticks
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

sealed interface RealtimeNotice {
    data class Message(val header: String?, val text: String) : RealtimeNotice
    data object LibraryChanged : RealtimeNotice
    data object ServerRestarting : RealtimeNotice
    /** The socket (re)connected, so the server is reachable again. */
    data object Connected : RealtimeNotice
}

/** Posts our capabilities so other Jellyfin clients can cast here, and routes their commands to playback. */
@Singleton
class RealtimeCoordinator @Inject constructor(
    private val sessions: SessionManager,
    private val playback: PlaybackCoordinator,
    private val items: GetItemDetail,
    private val cache: ItemCache,
    private val userDataEvents: UserDataEvents,
    scope: CoroutineScope,
    private val logger: Logger,
) {
    private val _notices = MutableSharedFlow<RealtimeNotice>(extraBufferCapacity = 16)
    val notices: SharedFlow<RealtimeNotice> = _notices.asSharedFlow()

    init {
        scope.launch {
            sessions.active.collectLatest { active ->
                if (active != null) run(active)
            }
        }
    }

    private suspend fun run(active: ActiveSession) {
        val realtime = active.connection.realtime
        realtime.postCapabilities().logFailure(logger, TAG, "capabilities post")

        realtime.events.collect { event ->
            when (event) {
                is ServerEvent.UserDataChanged -> userDataEvents.publish(active.server.id, event.changes)
                is ServerEvent.LibraryChanged -> {
                    cache.invalidate(active.server.id, event.ids)
                    _notices.tryEmit(RealtimeNotice.LibraryChanged)
                }
                is ServerEvent.Play -> handlePlay(event)
                is ServerEvent.Playstate -> handlePlaystate(event)
                is ServerEvent.DisplayMessage -> _notices.tryEmit(RealtimeNotice.Message(event.header, event.text))
                is ServerEvent.SelectAudio -> playback.selectAudio(event.index).logFailure(logger, TAG, "remote track change")
                is ServerEvent.SelectSubtitle -> playback.selectSubtitle(event.index).logFailure(logger, TAG, "remote track change")
                is ServerEvent.SetVolume -> playback.current.value?.player?.setVolume(event.percent / 100f)
                is ServerEvent.Volume -> playback.current.value?.player?.applyVolume(event.command)
                ServerEvent.ServerStopping -> _notices.tryEmit(RealtimeNotice.ServerRestarting)
                is ServerEvent.Connection -> if (event.connected) _notices.tryEmit(RealtimeNotice.Connected)
            }
        }
    }

    private suspend fun handlePlay(event: ServerEvent.Play) {
        val resolved = event.itemIds.mapNotNull { items(it).getOrNull() }
        if (resolved.isEmpty()) return
        // The start index counts the ids sent, not the items that resolved.
        val startId = event.itemIds[(event.startIndex ?: 0).coerceIn(0, event.itemIds.lastIndex)]
        val first = resolved.firstOrNull { it.id == startId } ?: return
        playback.play(
            PlaybackRequest(item = first, startAt = event.startAt, audioStreamIndex = event.audioStreamIndex, subtitles = event.subtitles),
            queue = resolved,
        ).logFailure(logger, TAG, "remote play")
    }

    private fun MediaPlayer.applyVolume(command: VolumeCommand) = setVolume(
        when (command) {
            VolumeCommand.Mute -> 0f
            VolumeCommand.Unmute -> 1f
            VolumeCommand.ToggleMute -> if (volume.value > 0f) 0f else 1f
            VolumeCommand.Up -> (volume.value + 0.1f).coerceAtMost(1f)
            VolumeCommand.Down -> (volume.value - 0.1f).coerceAtLeast(0f)
        },
    )

    private suspend fun handlePlaystate(event: ServerEvent.Playstate) {
        val player = playback.current.value?.player ?: return
        when (event.command) {
            PlaystateCommand.Stop -> playback.stop()
            PlaystateCommand.Pause -> player.pause()
            PlaystateCommand.Unpause -> player.play()
            PlaystateCommand.PlayPause -> player.togglePlayPause()
            PlaystateCommand.Seek -> event.seekTo?.let(player::seekTo)
            PlaystateCommand.Rewind -> player.seekBy((-10).seconds)
            PlaystateCommand.FastForward -> player.seekBy(30.seconds)
            PlaystateCommand.NextTrack -> playback.next()
            PlaystateCommand.PreviousTrack -> player.seekTo(Ticks.ZERO)
        }
    }

    companion object { private const val TAG = "Realtime" }
}
