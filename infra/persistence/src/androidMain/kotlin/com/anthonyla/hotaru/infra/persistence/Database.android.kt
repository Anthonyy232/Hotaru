package com.anthonyla.hotaru.infra.persistence

import android.content.Context
import androidx.room3.Room
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import jakarta.inject.Singleton

@ContributesTo(AppScope::class)
@BindingContainer
object DatabaseBindings {
    @Provides
    @Singleton
    fun database(context: Context): HotaruDatabase = openDatabase(Room.databaseBuilder<HotaruDatabase>(context, "hotaru.db"))

    @Provides fun servers(db: HotaruDatabase): ServerDao = db.servers()
    @Provides fun accounts(db: HotaruDatabase): AccountDao = db.accounts()
    @Provides fun itemCache(db: HotaruDatabase): ItemCacheDao = db.itemCache()
    @Provides fun preferences(db: HotaruDatabase): PreferenceDao = db.preferences()
}
