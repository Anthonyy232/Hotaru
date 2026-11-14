package com.anthonyla.hotaru.infra.persistence

import com.anthonyla.hotaru.application.ports.AccountStore
import com.anthonyla.hotaru.application.ports.CachedItem
import com.anthonyla.hotaru.application.ports.ItemCache
import com.anthonyla.hotaru.application.ports.ServerStore
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.ServerVersion
import com.anthonyla.hotaru.domain.UserId
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@ContributesBinding(AppScope::class)
class RoomServerStore(private val dao: ServerDao) : ServerStore {
    override fun observeAll(): Flow<List<Server>> = dao.observeAll().map { list -> list.map(::toDomain) }
    override suspend fun get(id: ServerId): Server? = dao.get(id.value)?.let(::toDomain)
    override suspend fun upsert(server: Server) = dao.upsert(
        ServerEntity(server.id.value, server.name, server.baseUrl, server.version.toString(), server.lastSeen.toEpochMilliseconds()),
    )

    private fun toDomain(e: ServerEntity) = Server(
        ServerId(e.id), e.name, e.baseUrl, checkNotNull(ServerVersion.parse(e.version)) { "stored server version ${e.version}" }, Instant.fromEpochMilliseconds(e.lastSeenEpochMs),
    )
}

@ContributesBinding(AppScope::class)
class RoomAccountStore(private val dao: AccountDao) : AccountStore {
    override fun observeAll(): Flow<List<Account>> = dao.observeAll().map { it.map(::toDomain) }
    override suspend fun get(serverId: ServerId, userId: UserId): Account? = dao.get(serverId.value, userId.value)?.let(::toDomain)
    override suspend fun upsert(account: Account, lastUsed: Instant) = dao.upsert(
        AccountEntity(account.serverId.value, account.userId.value, account.name, account.deviceId.value, lastUsed.toEpochMilliseconds()),
    )
    override suspend fun remove(serverId: ServerId, userId: UserId) = dao.delete(serverId.value, userId.value)
    override suspend fun lastUsed(): Account? = dao.lastUsed()?.let(::toDomain)

    private fun toDomain(e: AccountEntity) = Account(
        serverId = ServerId(e.serverId), userId = UserId(e.userId), name = e.name,
        deviceId = DeviceId(e.deviceId),
    )
}

/** Payloads are decoded strictly: only this cache writes them, so changing a serialized domain type means bumping the database version. */
@ContributesBinding(AppScope::class)
class RoomItemCache(private val dao: ItemCacheDao) : ItemCache {
    override suspend fun get(serverId: ServerId, userId: UserId, id: ItemId): CachedItem? = dao.get(serverId.value, userId.value, id.value)?.let { e ->
        CachedItem(Json.decodeFromString(MediaItem.serializer(), e.payload), Instant.fromEpochMilliseconds(e.fetchedAtEpochMs))
    }
    override suspend fun put(serverId: ServerId, userId: UserId, item: MediaItem, now: Instant) =
        dao.upsert(ItemCacheEntity(serverId.value, userId.value, item.id.value, Json.encodeToString(MediaItem.serializer(), item), now.toEpochMilliseconds()))
    override suspend fun invalidate(serverId: ServerId, ids: Set<ItemId>) = dao.delete(serverId.value, ids.map { it.value })
}

/**
 * Decoding is strict: only this store writes the row, so removing or renaming a [Settings] field means bumping the database version.
 */
@Singleton
@ContributesBinding(AppScope::class)
class RoomSettingsStore(private val dao: PreferenceDao) : SettingsStore {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow(runBlocking { load() })
    override val settings: StateFlow<Settings> = state.asStateFlow()

    private suspend fun load(): Settings = dao.get(KEY)?.let { json.decodeFromString(Settings.serializer(), it) } ?: Settings()

    private var stored = state.value

    // Readers see a change at once. Writes queue and store the latest settings, so a slider drag waits on no write and makes few.
    override suspend fun update(transform: (Settings) -> Settings) {
        state.update(transform)
        mutex.withLock {
            val latest = state.value
            if (latest != stored) dao.put(PreferenceEntity(KEY, json.encodeToString(Settings.serializer(), latest)))
            stored = latest
        }
    }

    companion object { private const val KEY = "settings" }
}
