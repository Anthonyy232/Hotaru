package com.anthonyla.hotaru.infra.persistence

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "server")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val version: String,
    val lastSeenEpochMs: Long,
)

@Entity(tableName = "account", primaryKeys = ["serverId", "userId"])
data class AccountEntity(
    val serverId: String,
    val userId: String,
    val name: String,
    val deviceId: String,
    val lastUsedEpochMs: Long,
)

@Entity(tableName = "item_cache", primaryKeys = ["serverId", "userId", "itemId"])
data class ItemCacheEntity(
    val serverId: String,
    val userId: String,
    val itemId: String,
    val payload: String,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "preference")
data class PreferenceEntity(@PrimaryKey val key: String, val json: String)

@Dao
interface ServerDao {
    @Query("SELECT * FROM server ORDER BY lastSeenEpochMs DESC") fun observeAll(): Flow<List<ServerEntity>>
    @Query("SELECT * FROM server WHERE id = :id") suspend fun get(id: String): ServerEntity?
    @Upsert suspend fun upsert(server: ServerEntity)
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM account ORDER BY lastUsedEpochMs DESC") fun observeAll(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM account WHERE serverId = :serverId AND userId = :userId") suspend fun get(serverId: String, userId: String): AccountEntity?
    @Query("SELECT * FROM account ORDER BY lastUsedEpochMs DESC LIMIT 1") suspend fun lastUsed(): AccountEntity?
    @Upsert suspend fun upsert(account: AccountEntity)
    @Query("DELETE FROM account WHERE serverId = :serverId AND userId = :userId") suspend fun delete(serverId: String, userId: String)
}

@Dao
interface ItemCacheDao {
    @Query("SELECT * FROM item_cache WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId") suspend fun get(serverId: String, userId: String, itemId: String): ItemCacheEntity?
    @Upsert suspend fun upsert(item: ItemCacheEntity)
    @Query("DELETE FROM item_cache WHERE serverId = :serverId AND itemId IN (:ids)") suspend fun delete(serverId: String, ids: List<String>)
}

@Dao
interface PreferenceDao {
    @Query("SELECT json FROM preference WHERE `key` = :key") suspend fun get(key: String): String?
    @Upsert suspend fun put(entity: PreferenceEntity)
}

@Database(entities = [ServerEntity::class, AccountEntity::class, ItemCacheEntity::class, PreferenceEntity::class], version = 14, exportSchema = true)
@ConstructedBy(HotaruDatabaseConstructor::class)
abstract class HotaruDatabase : RoomDatabase() {
    abstract fun servers(): ServerDao
    abstract fun accounts(): AccountDao
    abstract fun itemCache(): ItemCacheDao
    abstract fun preferences(): PreferenceDao
}

@Suppress("KotlinNoActualForExpect")
expect object HotaruDatabaseConstructor : RoomDatabaseConstructor<HotaruDatabase> {
    override fun initialize(): HotaruDatabase
}

fun openDatabase(builder: RoomDatabase.Builder<HotaruDatabase>): HotaruDatabase =
    builder
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)   // pre-1.0: no migrations yet
        .build()
