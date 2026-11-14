package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.PlaystateCommand
import com.anthonyla.hotaru.application.ports.RealtimeGateway
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.application.ports.VolumeCommand
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.infra.jellyfin.Mappers.itemId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.mapNotNull
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.sockets.SocketApiState
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.ClientCapabilitiesDto
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.LibraryChangedMessage
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMessage
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.ServerRestartingMessage
import org.jellyfin.sdk.model.api.ServerShuttingDownMessage
import org.jellyfin.sdk.model.api.UserDataChangedMessage
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.api.GeneralCommandType as SdkGeneralCommandType
import org.jellyfin.sdk.model.api.PlayCommand as SdkPlayCommand
import org.jellyfin.sdk.model.api.PlaystateCommand as SdkPlaystateCommand

/** The remote-control commands this device takes, read from each command's arguments; capabilities advertise exactly these. */
private val generalCommands: Map<SdkGeneralCommandType, (Map<String, String>) -> ServerEvent?> = mapOf(
    SdkGeneralCommandType.DISPLAY_MESSAGE to { args -> args["Text"]?.let { ServerEvent.DisplayMessage(args["Header"], it) } },
    SdkGeneralCommandType.SET_AUDIO_STREAM_INDEX to { args -> args["Index"]?.toIntOrNull()?.let(ServerEvent::SelectAudio) },
    SdkGeneralCommandType.SET_SUBTITLE_STREAM_INDEX to { args -> args["Index"]?.toIntOrNull()?.let { ServerEvent.SelectSubtitle(Mappers.subtitleStream(it)) } },
    SdkGeneralCommandType.SET_VOLUME to { args -> args["Volume"]?.toIntOrNull()?.let(ServerEvent::SetVolume) },
    SdkGeneralCommandType.MUTE to { ServerEvent.Volume(VolumeCommand.Mute) },
    SdkGeneralCommandType.UNMUTE to { ServerEvent.Volume(VolumeCommand.Unmute) },
    SdkGeneralCommandType.TOGGLE_MUTE to { ServerEvent.Volume(VolumeCommand.ToggleMute) },
    SdkGeneralCommandType.VOLUME_UP to { ServerEvent.Volume(VolumeCommand.Up) },
    SdkGeneralCommandType.VOLUME_DOWN to { ServerEvent.Volume(VolumeCommand.Down) },
)

/** The SDK socket connects while subscriptions exist, answers ForceKeepAlive and reconnects on credential changes. */
internal class JellyfinRealtimeGateway(private val api: ApiClient, private val logger: Logger) : RealtimeGateway {

    override val events: Flow<ServerEvent> = merge(
        api.webSocket.subscribe<UserDataChangedMessage>().mapNotNull { m ->
            val data = m.data ?: return@mapNotNull null
            ServerEvent.UserDataChanged(data.userDataList.associate { it.itemId.itemId() to Mappers.userData(it) })
        },
        api.webSocket.subscribe<LibraryChangedMessage>().mapNotNull { m ->
            val d = m.data ?: return@mapNotNull null
            // Jellyfin writes these ids without dashes; every other ItemId has them.
            ServerEvent.LibraryChanged((d.itemsAdded + d.itemsRemoved + d.itemsUpdated + d.foldersAddedTo + d.foldersRemovedFrom).mapTo(HashSet()) { it.toUUID().itemId() })
        },
        api.webSocket.subscribe<PlayMessage>().mapNotNull { m ->
            // There is no queue to add to, shuffle or mix: only Play Now is taken.
            val r = m.data?.takeIf { it.playCommand == SdkPlayCommand.PLAY_NOW } ?: return@mapNotNull null
            ServerEvent.Play(
                itemIds = r.itemIds.orEmpty().map { it.itemId() },
                startAt = r.startPositionTicks?.let(::Ticks), startIndex = r.startIndex,
                audioStreamIndex = r.audioStreamIndex, subtitles = Mappers.subtitleChoice(r.subtitleStreamIndex),
            )
        },
        api.webSocket.subscribe<PlaystateMessage>().mapNotNull { m ->
            val r = m.data ?: return@mapNotNull null
            ServerEvent.Playstate(
                command = when (r.command) {
                    SdkPlaystateCommand.STOP -> PlaystateCommand.Stop
                    SdkPlaystateCommand.PAUSE -> PlaystateCommand.Pause
                    SdkPlaystateCommand.UNPAUSE -> PlaystateCommand.Unpause
                    SdkPlaystateCommand.NEXT_TRACK -> PlaystateCommand.NextTrack
                    SdkPlaystateCommand.PREVIOUS_TRACK -> PlaystateCommand.PreviousTrack
                    SdkPlaystateCommand.SEEK -> PlaystateCommand.Seek
                    SdkPlaystateCommand.REWIND -> PlaystateCommand.Rewind
                    SdkPlaystateCommand.FAST_FORWARD -> PlaystateCommand.FastForward
                    SdkPlaystateCommand.PLAY_PAUSE -> PlaystateCommand.PlayPause
                },
                seekTo = r.seekPositionTicks?.let(::Ticks),
            )
        },
        api.webSocket.subscribe<GeneralCommandMessage>().mapNotNull { m -> m.data?.let { c -> generalCommands[c.name]?.invoke(c.arguments) } },
        api.webSocket.subscribe<ServerRestartingMessage>().map { ServerEvent.ServerStopping },
        api.webSocket.subscribe<ServerShuttingDownMessage>().map { ServerEvent.ServerStopping },
        api.webSocket.state.map { ServerEvent.Connection(it is SocketApiState.Connected) },
    ).catch { e -> logger.warn("Realtime", { "socket stream failed: ${e.message}" }, e) }

    override suspend fun postCapabilities(): Outcome<Unit> = sdk {
        api.sessionApi.postFullCapabilities(
            data = ClientCapabilitiesDto(
                playableMediaTypes = listOf(MediaType.VIDEO),
                supportedCommands = generalCommands.keys.toList() + SdkGeneralCommandType.PLAY_STATE + SdkGeneralCommandType.PLAY,
                supportsMediaControl = true,
                supportsPersistentIdentifier = true,
            ),
        )
    }
}
