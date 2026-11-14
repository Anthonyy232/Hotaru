package com.anthonyla.hotaru.application.ports

import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Chapter
import com.anthonyla.hotaru.domain.DeviceCapabilities
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSpec
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.PlayMethod
import com.anthonyla.hotaru.domain.PlaySessionId
import com.anthonyla.hotaru.domain.ServerInfo
import com.anthonyla.hotaru.domain.Session
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.Trickplay
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.domain.UserId
import kotlinx.coroutines.flow.Flow

data class QuickConnectCode(val secret: String, val code: String)

enum class QuickConnectState { Pending, Authorized, Expired }

/** [avatarUrl] is absolute and needs no sign-in. */
data class PublicUser(val id: UserId, val name: String, val avatarUrl: String?)

/** A server before sign-in: what any client may ask it. */
interface PublicServer {
    suspend fun info(): Outcome<ServerInfo>
    suspend fun users(): Outcome<List<PublicUser>>
    suspend fun authenticateByName(username: String, password: String): Outcome<Session>
    suspend fun quickConnectEnabled(): Outcome<Boolean>
    suspend fun quickConnectInitiate(): Outcome<QuickConnectCode>
    suspend fun quickConnectState(secret: String): Outcome<QuickConnectState>
    suspend fun authenticateWithQuickConnect(secret: String): Outcome<Session>
}

interface AccountGateway {
    /** Validates the token and refreshes the account's display data. */
    suspend fun currentUser(): Outcome<Account>
    suspend fun logout(): Outcome<Unit>
}

/** Dates run newest first. */
enum class LibrarySort { Name, DateAdded, ReleaseDate }

data class ItemQuery(
    val parentId: ItemId? = null,
    val kinds: Set<ItemKind> = emptySet(),
    val recursive: Boolean = true,
    val isPlayed: Boolean? = null,
    val isFavorite: Boolean? = null,
    val sort: LibrarySort = LibrarySort.Name,
    val startIndex: Int = 0,
    val limit: Int,
)

data class Page<T>(val items: List<T>, val totalCount: Int)

interface LibraryGateway {
    suspend fun views(): Outcome<List<LibraryView>>
    suspend fun items(query: ItemQuery): Outcome<Page<MediaItem>>
    suspend fun item(id: ItemId): Outcome<MediaItem>
    suspend fun resume(limit: Int): Outcome<List<MediaItem>>
    suspend fun nextUp(limit: Int): Outcome<List<MediaItem>>
    suspend fun latest(viewId: ItemId, limit: Int): Outcome<List<MediaItem>>
    suspend fun seasons(seriesId: ItemId): Outcome<List<MediaItem>>
    suspend fun episodes(seriesId: ItemId, seasonId: ItemId?): Outcome<List<Episode>>
    suspend fun similar(itemId: ItemId, limit: Int): Outcome<List<MediaItem>>
    suspend fun search(term: String, limit: Int): Outcome<List<MediaItem>>
}

interface UserDataGateway {
    suspend fun setFavorite(id: ItemId, favorite: Boolean): Outcome<UserData>
    suspend fun setPlayed(id: ItemId, played: Boolean): Outcome<UserData>
}

data class PlaybackInfoRequest(
    val itemId: ItemId,
    val startAt: Ticks,
    val audioStreamIndex: Int?,
    val subtitles: SubtitleChoice,
    val maxStreamingBitrate: Int,
    val capabilities: DeviceCapabilities,
)

sealed interface PlaybackInfoResult {
    data class Playable(val sources: List<MediaSource>, val playSessionId: PlaySessionId) : PlaybackInfoResult

    /** The server opens no play session then. */
    data class Refused(val reason: PlaybackRefusal) : PlaybackInfoResult
}

enum class PlaybackRefusal { NotAllowed, NoCompatibleStream, RateLimitExceeded }

data class PlaybackReport(
    val itemId: ItemId,
    val mediaSourceId: MediaSourceId,
    val playSessionId: PlaySessionId,
    val position: Ticks,
    val isPaused: Boolean,
    val isMuted: Boolean,
    val volume: Int,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val method: PlayMethod,
)

/** What a source's seek bar shows along its length. */
data class ItemTimeline(val chapters: List<Chapter>, val trickplay: Trickplay?)

interface PlaybackGateway {
    suspend fun playbackInfo(request: PlaybackInfoRequest): Outcome<PlaybackInfoResult>
    /** Static (direct play) URL for the original file; includes the ETag tag for caching. */
    fun directPlayUrl(itemId: ItemId, source: MediaSource): OpaqueUrl
    /** Make a server-relative URL (TranscodingUrl, subtitle DeliveryUrl) absolute; it carries no credentials. Treat the result as opaque. */
    fun resolveServerUrl(url: OpaqueUrl): OpaqueUrl
    suspend fun reportStart(report: PlaybackReport): Outcome<Unit>
    suspend fun reportProgress(report: PlaybackReport): Outcome<Unit>
    suspend fun reportStopped(report: PlaybackReport): Outcome<Unit>
    suspend fun ping(playSessionId: PlaySessionId): Outcome<Unit>
    /** Auth headers the player needs to fetch media. */
    fun mediaRequestHeaders(): Map<String, String>
    suspend fun timeline(itemId: ItemId, sourceId: MediaSourceId): Outcome<ItemTimeline>
    suspend fun segments(itemId: ItemId): Outcome<List<MediaSegment>>
}

fun interface ImageUrlResolver {
    fun url(ref: ImageRef, spec: ImageSpec): String
}

enum class PlaystateCommand { Stop, Pause, Unpause, NextTrack, PreviousTrack, Seek, Rewind, FastForward, PlayPause }

enum class VolumeCommand { Mute, Unmute, ToggleMute, Up, Down }

sealed interface ServerEvent {
    data class UserDataChanged(val changes: Map<ItemId, UserData>) : ServerEvent
    /** [ids] are the items added, removed or updated, and the folders that gained or lost them. */
    data class LibraryChanged(val ids: Set<ItemId>) : ServerEvent
    data class Play(val itemIds: List<ItemId>, val startAt: Ticks?, val startIndex: Int?, val audioStreamIndex: Int?, val subtitles: SubtitleChoice) : ServerEvent
    data class Playstate(val command: PlaystateCommand, val seekTo: Ticks?) : ServerEvent
    data class DisplayMessage(val header: String?, val text: String) : ServerEvent
    data class SelectAudio(val index: Int) : ServerEvent
    /** A null [index] turns subtitles off. */
    data class SelectSubtitle(val index: Int?) : ServerEvent
    /** [percent] is 0..100. */
    data class SetVolume(val percent: Int) : ServerEvent
    data class Volume(val command: VolumeCommand) : ServerEvent
    /** Restarting or shutting down. */
    data object ServerStopping : ServerEvent
    data class Connection(val connected: Boolean) : ServerEvent
}

interface RealtimeGateway {
    val events: Flow<ServerEvent>
    /** Lets other Jellyfin clients cast to this device and send it the commands [events] carries. */
    suspend fun postCapabilities(): Outcome<Unit>
}

/** The gateways for one signed-in session. */
interface ServerConnection {
    val account: AccountGateway
    val library: LibraryGateway
    val userData: UserDataGateway
    val playback: PlaybackGateway
    val realtime: RealtimeGateway
    val images: ImageUrlResolver
}

interface ServerConnectionFactory {
    /** Turn user input ("192.168.1.5", "https://jf.example.com/jelly") into candidate base URLs, best first. */
    fun addressCandidates(input: String): List<String>
    fun publicServer(baseUrl: String): PublicServer
    fun connect(baseUrl: String, session: Session): ServerConnection
}
