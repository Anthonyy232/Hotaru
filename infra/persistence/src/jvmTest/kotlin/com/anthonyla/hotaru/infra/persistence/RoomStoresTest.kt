package com.anthonyla.hotaru.infra.persistence

import androidx.room3.Room
import com.anthonyla.hotaru.application.ports.AudioMode
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.GenericItem
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.Server
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.ServerVersion
import com.anthonyla.hotaru.domain.UserId
import com.anthonyla.hotaru.testing.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Instant

class RoomStoresTest {
    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)
    private fun inMemory(): HotaruDatabase = openDatabase(Room.inMemoryDatabaseBuilder<HotaruDatabase>())

    @Test
    fun `servers and accounts round trip`() = runTest {
        val db = inMemory()
        val servers = RoomServerStore(db.servers())
        val accounts = RoomAccountStore(db.accounts())
        val server = Server(ServerId("s1"), "Home", "http://jf.local:8096", ServerVersion(12, 2, 0), now)
        servers.upsert(server)
        assertEquals(server, servers.get(ServerId("s1")))
        assertEquals(listOf(server), servers.observeAll().first())

        val account = Account(ServerId("s1"), UserId("u1"), "Quest", DeviceId("dev-1"))
        accounts.upsert(account, now)
        assertEquals(account, accounts.lastUsed())
        accounts.remove(ServerId("s1"), UserId("u1"))
        assertNull(accounts.lastUsed())
        db.close()
    }

    @Test
    fun `item cache round trips every kind`() = runTest {
        val db = inMemory()
        val cache = RoomItemCache(db.itemCache())
        val sid = ServerId("s1")
        val items = Fixtures.all + listOf(
            GenericItem(ItemId("al"), ItemKind.Playlist, "Night Drive", isFolder = true, productionYear = 2021),
            GenericItem(ItemId("bx"), ItemKind.BoxSet, "Trilogy", isFolder = true),
        )
        val uid = UserId("u1")
        items.forEach { cache.put(sid, uid, it, now) }
        for (item in items) assertEquals(item, assertNotNull(cache.get(sid, uid, item.id), item.name).item, item.name)

        db.close()
    }

    @Test
    fun `cached items are per user and invalidate for the whole server`() = runTest {
        val db = inMemory()
        val cache = RoomItemCache(db.itemCache())
        val sid = ServerId("s1")
        val (a, b) = UserId("a") to UserId("b")
        val item = Fixtures.auroraDrift
        cache.put(sid, a, item, now)
        assertNull(cache.get(sid, b, item.id))

        cache.put(sid, b, item, now)
        cache.invalidate(sid, setOf(item.id))
        assertNull(cache.get(sid, a, item.id))
        assertNull(cache.get(sid, b, item.id))
        db.close()
    }

    @Test
    fun `settings persist across store instances`() = runTest {
        val file = File.createTempFile("hotaru-settings", ".db").also { it.delete() }
        val path = file.absolutePath
        val db1 = openDatabase(Room.databaseBuilder<HotaruDatabase>(path))
        RoomSettingsStore(db1.preferences()).update { it.copy(audioMode = AudioMode.ServerStereoAac, cinemaScreenWidthMeters = 6.5f) }
        db1.close()
        val db2 = openDatabase(Room.databaseBuilder<HotaruDatabase>(path))
        val store2 = RoomSettingsStore(db2.preferences())
        assertEquals(AudioMode.ServerStereoAac, store2.settings.value.audioMode)
        assertEquals(6.5f, store2.settings.value.cinemaScreenWidthMeters)
        db2.close()
        file.delete()
    }
}
