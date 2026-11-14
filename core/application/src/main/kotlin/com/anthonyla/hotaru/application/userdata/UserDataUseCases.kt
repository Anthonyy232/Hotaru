package com.anthonyla.hotaru.application.userdata

import com.anthonyla.hotaru.application.onSuccess
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.ItemCache
import com.anthonyla.hotaru.application.ports.UserDataGateway
import com.anthonyla.hotaru.application.session.SessionManager
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.UserData
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** User-data changes from this app or the server: the cached items go stale, and visible screens update without re-fetching. */
@Singleton
class UserDataEvents @Inject constructor(private val cache: ItemCache) {
    private val _changes = MutableSharedFlow<Pair<ItemId, UserData>>(extraBufferCapacity = 64)
    val changes: SharedFlow<Pair<ItemId, UserData>> = _changes.asSharedFlow()

    suspend fun publish(serverId: ServerId, changes: Map<ItemId, UserData>) {
        changes.forEach { (id, data) -> _changes.tryEmit(id to data) }
        cache.invalidate(serverId, changes.keys)
    }
}

class SetFavorite @Inject constructor(private val sessions: SessionManager, private val events: UserDataEvents) {
    suspend operator fun invoke(id: ItemId, favorite: Boolean): Outcome<UserData> =
        sessions.changeUserData(id, events) { it.setFavorite(id, favorite) }
}

class SetPlayed @Inject constructor(private val sessions: SessionManager, private val events: UserDataEvents) {
    suspend operator fun invoke(id: ItemId, played: Boolean): Outcome<UserData> =
        sessions.changeUserData(id, events) { it.setPlayed(id, played) }
}

/** Only this item changes here; parent counts refresh through the server's UserDataChanged event. */
private suspend inline fun SessionManager.changeUserData(
    id: ItemId,
    events: UserDataEvents,
    change: (UserDataGateway) -> Outcome<UserData>,
): Outcome<UserData> = withSession { active ->
    change(active.connection.userData).onSuccess { events.publish(active.server.id, mapOf(id to it)) }
}
