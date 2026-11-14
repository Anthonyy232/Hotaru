package com.anthonyla.hotaru.testing

import com.anthonyla.hotaru.application.ports.AccountStore
import com.anthonyla.hotaru.application.ports.CachedItem
import com.anthonyla.hotaru.application.ports.ClientInfo
import com.anthonyla.hotaru.application.ports.DeviceCapabilitiesProvider
import com.anthonyla.hotaru.application.ports.DeviceIdentity
import com.anthonyla.hotaru.application.ports.ItemCache
import com.anthonyla.hotaru.application.ports.Logger
import com.anthonyla.hotaru.application.ports.MediaPlayer
import com.anthonyla.hotaru.application.ports.PlayerFactory
import com.anthonyla.hotaru.application.ports.PlayerState
import com.anthonyla.hotaru.application.ports.PlayerTrack
import com.anthonyla.hotaru.application.ports.PlayerTracks
import com.anthonyla.hotaru.application.ports.SecureStore
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.DeviceCapabilities
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.HdrFormat
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.UserId
import com.anthonyla.hotaru.domain.VideoCodecCapability
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class FakeClock : Clock {
    private var now: Instant = Instant.fromEpochSeconds(1_800_000_000)
    override fun now(): Instant = now
    fun advance(by: Duration) { now += by }
}

object NoopLogger : Logger {
    override fun info(tag: String, message: () -> String) = Unit
    override fun warn(tag: String, message: () -> String, error: Throwable?) = Unit
    override fun error(tag: String, message: () -> String, error: Throwable?) = Unit
}

class InMemoryServerStore : ServerStore {
    private val state = MutableStateFlow<Map<ServerId, Server>>(emptyMap())
    override fun observeAll(): Flow<List<Server>> = state.map { it.values.sortedByDescending { s -> s.lastSeen } }
    override suspend fun get(id: ServerId): Server? = state.value[id]
    override suspend fun upsert(server: Server) { state.update { it + (server.id to server) } }
}

class InMemoryAccountStore : AccountStore {
    private val state = MutableStateFlow<Map<Pair<ServerId, UserId>, Pair<Account, Instant>>>(emptyMap())
    override fun observeAll(): Flow<List<Account>> = state.map { it.values.sortedByDescending { (_, used) -> used }.map { (account, _) -> account } }
    override suspend fun get(serverId: ServerId, userId: UserId): Account? = state.value[serverId to userId]?.first
    override suspend fun upsert(account: Account, lastUsed: Instant) { state.update { it + ((account.serverId to account.userId) to (account to lastUsed)) } }
    override suspend fun remove(serverId: ServerId, userId: UserId) { state.update { it - (serverId to userId) } }
    override suspend fun lastUsed(): Account? = state.value.values.maxByOrNull { (_, used) -> used }?.first
    val size: Int get() = state.value.size
}

class InMemoryItemCache : ItemCache {
    private val map = mutableMapOf<Triple<ServerId, UserId, ItemId>, CachedItem>()
    override suspend fun get(serverId: ServerId, userId: UserId, id: ItemId): CachedItem? = map[Triple(serverId, userId, id)]
    override suspend fun put(serverId: ServerId, userId: UserId, item: MediaItem, now: Instant) { map[Triple(serverId, userId, item.id)] = CachedItem(item, now) }
    override suspend fun invalidate(serverId: ServerId, ids: Set<ItemId>) { map.keys.removeAll { (server, _, id) -> server == serverId && id in ids } }
}

class InMemorySettingsStore : SettingsStore {
    private val state = MutableStateFlow(Settings())
    override val settings: StateFlow<Settings> = state
    override suspend fun update(transform: (Settings) -> Settings) { state.update(transform) }
}

class InMemorySecureStore : SecureStore {
    val map = mutableMapOf<String, String>()
    override suspend fun read(key: String): String? = map[key]
    override suspend fun write(key: String, value: String) { map[key] = value }
    override suspend fun delete(key: String) { map.remove(key) }
}

class FakeDeviceIdentity : DeviceIdentity {
    override val baseDeviceId: DeviceId = DeviceId("fake-device")
    override val deviceName: String = "Fake Quest 3"
    override val client: ClientInfo = ClientInfo("Hotaru", "0.1-fake")
}

class FakeCapabilitiesProvider : DeviceCapabilitiesProvider {
    override suspend fun capabilities(): DeviceCapabilities = quest3Like()

    companion object {
        fun quest3Like() = DeviceCapabilities(
            videoCodecs = listOf(
                VideoCodecCapability("h264", setOf("baseline", "main", "high"), 52, 4096, 2304, 8, emptySet()),
                VideoCodecCapability("hevc", setOf("main", "main 10"), 183, 8192, 4352, 10, setOf(HdrFormat.HDR10)),
                VideoCodecCapability("av1", setOf("main"), 15, 8192, 4352, 10, setOf(HdrFormat.HDR10)),
                VideoCodecCapability("vp9", setOf("profile 0"), null, 4096, 2304, 8, emptySet()),
            ),
            audioCodecs = setOf("aac", "ac3", "eac3", "opus", "flac", "mp3", "vorbis", "dts", "truehd"),
            containers = setOf("mp4", "m4v", "mkv", "webm", "mov", "ts"),
            maxAudioChannels = 8,
        )
    }
}

/** A scripted player: tests drive state transitions explicitly. */
class FakeMediaPlayer(val plan: PlaybackPlan, val headers: Map<String, String>) : MediaPlayer {
    override val state = MutableStateFlow<PlayerState>(PlayerState.Loading)
    override val position = MutableStateFlow(plan.startAt)
    override val duration = MutableStateFlow(plan.runtime)
    override val buffered = MutableStateFlow(Ticks.ZERO)
    override val tracks = MutableStateFlow(
        PlayerTracks(
            audio = plan.deliveredAudio.map { PlayerTrack("a${it.index}", it.displayTitle ?: "Audio ${it.index}", false, it.index) },
            subtitles = (plan.streamSubtitles + plan.externalSubtitles.map { it.stream }).map { PlayerTrack("s${it.index}", it.displayTitle ?: "Sub ${it.index}", false, it.index) },
        ),
    )
    override val volume = MutableStateFlow(1f)

    var target: VideoRenderTarget? = null
    var released = false
    var selectedAudio: String? = null
    var selectedSubtitle: String? = null
    var subtitleDelay = Duration.ZERO
        private set
    var speed = 1f
        private set

    override fun renderTo(target: VideoRenderTarget?) { this.target = target }
    override fun play() { if (!released) state.value = PlayerState.Playing }
    override fun pause() { if (state.value is PlayerState.Playing) state.value = PlayerState.Paused }
    override fun seekTo(position: Ticks) { this.position.value = position }
    override fun selectAudio(trackId: String) { selectedAudio = trackId }
    override fun selectSubtitle(trackId: String?) { selectedSubtitle = trackId }
    override fun setSubtitleDelay(delay: Duration) { subtitleDelay = delay }
    override fun setSpeed(speed: Float) { this.speed = speed }
    override fun setVolume(volume: Float) { this.volume.value = volume }
    override fun release() { released = true; state.value = PlayerState.Idle }

    fun advance(by: Duration) { position.value = position.value + by }
}

class FakePlayerFactory : PlayerFactory {
    lateinit var last: FakeMediaPlayer
        private set
    override fun create(plan: PlaybackPlan, headers: Map<String, String>): MediaPlayer = FakeMediaPlayer(plan, headers).also { last = it }
}
