package com.anthonyla.hotaru.application.ports

import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.DeviceCapabilities
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.Environment
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Instant

interface ServerStore {
    fun observeAll(): Flow<List<Server>>
    suspend fun get(id: ServerId): Server?
    suspend fun upsert(server: Server)
    suspend fun remove(id: ServerId)
}

interface AccountStore {
    /** Most recently used first. */
    fun observeAll(): Flow<List<Account>>
    suspend fun get(serverId: ServerId, userId: UserId): Account?
    suspend fun upsert(account: Account, lastUsed: Instant)
    suspend fun remove(serverId: ServerId, userId: UserId)
    suspend fun lastUsed(): Account?
}

/** Per user: an item carries its user's favourite, played state and resume point. */
interface ItemCache {
    suspend fun get(serverId: ServerId, userId: UserId, id: ItemId): CachedItem?
    suspend fun put(serverId: ServerId, userId: UserId, item: MediaItem, now: Instant)
    /** Drops [ids] for every user of the server. */
    suspend fun invalidate(serverId: ServerId, ids: Set<ItemId>)
    suspend fun forget(serverId: ServerId, userId: UserId)
}

data class CachedItem(val item: MediaItem, val fetchedAt: Instant)

interface SettingsStore {
    val settings: StateFlow<Settings>
    suspend fun update(transform: (Settings) -> Settings)
}

enum class AudioMode { DownmixStereo, ServerStereoAac }
enum class SegmentAction { Skip, Prompt, Ignore }
/** Text and styled subtitles; picture subtitles keep their size. [scale] is of each one's own size. */
enum class SubtitleSize(val scale: Float) { Small(0.75f), Medium(1f), Large(1.4f), ExtraLarge(1.8f) }

@Serializable
data class Settings(
    val maxStreamingBitrate: Int = 120_000_000,
    val audioMode: AudioMode = AudioMode.DownmixStereo,
    val forceSdr: Boolean = false,
    val introAction: SegmentAction = SegmentAction.Prompt,
    val outroAction: SegmentAction = SegmentAction.Prompt,
    val recapAction: SegmentAction = SegmentAction.Prompt,
    val autoplayNext: Boolean = true,
    val subtitleSize: SubtitleSize = SubtitleSize.Medium,
    val librarySort: LibrarySort = LibrarySort.Name,
    val cinemaScreenWidthMeters: Float = 4.0f,
    val cinemaEnvironment: Environment = Environment.Passthrough,
)

interface SecureStore {
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
    suspend fun delete(key: String)
}

data class ClientInfo(val name: String, val version: String)

interface DeviceIdentity {
    val baseDeviceId: DeviceId
    val deviceName: String
    val client: ClientInfo
}

fun interface DeviceCapabilitiesProvider {
    suspend fun capabilities(): DeviceCapabilities
}

data class DiscoveredServer(val id: String, val name: String, val address: String)

fun interface NetworkDiscovery {
    fun discover(): Flow<DiscoveredServer>
}

interface Logger {
    fun info(tag: String, message: () -> String)
    fun warn(tag: String, message: () -> String, error: Throwable? = null)
    fun error(tag: String, message: () -> String, error: Throwable? = null)
}

sealed interface PlayerState {
    data object Idle : PlayerState
    data object Loading : PlayerState
    data object Buffering : PlayerState
    data object Playing : PlayerState
    data object Paused : PlayerState
    data object Ended : PlayerState
    data class Error(val failure: PlaybackFailure) : PlayerState
}

/** Why the player stopped: the stream from the server broke off, the media can't be read or decoded, or anything else. */
enum class PlaybackFailure { Network, Unplayable, Other }

data class PlayerTrack(val id: String, val label: String, val selected: Boolean, val serverStreamIndex: Int)

data class PlayerTracks(val audio: List<PlayerTrack> = emptyList(), val subtitles: List<PlayerTrack> = emptyList())

/** Where frames go, [width] by [height] pixels. The surface is platform-typed (android.view.Surface on Quest). */
data class VideoRenderTarget(val surface: Any, val width: Int, val height: Int)

interface MediaPlayer {
    val state: StateFlow<PlayerState>
    val position: StateFlow<Ticks>
    val duration: StateFlow<Ticks?>
    val buffered: StateFlow<Ticks>
    val tracks: StateFlow<PlayerTracks>
    val volume: StateFlow<Float>

    /** null while no surface exists: playback (and audio) continues without a picture. */
    fun renderTo(target: VideoRenderTarget?)
    fun play()
    fun pause()
    fun seekTo(position: Ticks)
    fun selectAudio(trackId: String)
    fun selectSubtitle(trackId: String?)
    /** Shows subtitles this much later than their own times, or earlier when negative. Subtitles in the picture stay. */
    fun setSubtitleDelay(delay: Duration)
    /** Raises subtitles by this fraction of the picture's height, clear of what covers its bottom edge. Subtitles in the picture stay. */
    fun setSubtitleLift(fraction: Float)
    /** 1 is normal speed. */
    fun setSpeed(speed: Float)
    /** App-level volume 0..1. Never touches system volume (Meta rule). */
    fun setVolume(volume: Float)
    fun release()
}

fun interface PlayerFactory {
    /** Prepares the plan at [PlaybackPlan.startAt]; the player owns the start offset. */
    fun create(plan: PlaybackPlan, headers: Map<String, String>): MediaPlayer
}
