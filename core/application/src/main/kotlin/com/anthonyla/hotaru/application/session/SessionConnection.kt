package com.anthonyla.hotaru.application.session

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountGateway
import com.anthonyla.hotaru.application.ports.ItemQuery
import com.anthonyla.hotaru.application.ports.LibraryGateway
import com.anthonyla.hotaru.application.ports.PlaybackGateway
import com.anthonyla.hotaru.application.ports.PlaybackInfoRequest
import com.anthonyla.hotaru.application.ports.PlaybackReport
import com.anthonyla.hotaru.application.ports.RealtimeGateway
import com.anthonyla.hotaru.application.ports.ServerConnection
import com.anthonyla.hotaru.application.ports.UserDataGateway
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.PlaySessionId

internal class SessionConnection(
    private val connection: ServerConnection,
    private val onUnauthorized: suspend () -> Unit,
) : ServerConnection {
    override val account = object : AccountGateway {
        override suspend fun currentUser() = request { connection.account.currentUser() }
        override suspend fun logout() = request { connection.account.logout() }
    }

    override val library = object : LibraryGateway {
        override suspend fun views() = request { connection.library.views() }
        override suspend fun items(query: ItemQuery) = request { connection.library.items(query) }
        override suspend fun item(id: ItemId) = request { connection.library.item(id) }
        override suspend fun resume(limit: Int) = request { connection.library.resume(limit) }
        override suspend fun nextUp(limit: Int) = request { connection.library.nextUp(limit) }
        override suspend fun latest(viewId: ItemId, limit: Int) = request { connection.library.latest(viewId, limit) }
        override suspend fun seasons(seriesId: ItemId) = request { connection.library.seasons(seriesId) }
        override suspend fun episodes(seriesId: ItemId, seasonId: ItemId?) = request { connection.library.episodes(seriesId, seasonId) }
        override suspend fun similar(itemId: ItemId, limit: Int) = request { connection.library.similar(itemId, limit) }
        override suspend fun search(term: String, limit: Int) = request { connection.library.search(term, limit) }
    }

    override val userData = object : UserDataGateway {
        override suspend fun setFavorite(id: ItemId, favorite: Boolean) = request { connection.userData.setFavorite(id, favorite) }
        override suspend fun setPlayed(id: ItemId, played: Boolean) = request { connection.userData.setPlayed(id, played) }
    }

    override val playback = object : PlaybackGateway {
        override suspend fun playbackInfo(request: PlaybackInfoRequest) = request { connection.playback.playbackInfo(request) }
        override suspend fun reportStart(report: PlaybackReport) = request { connection.playback.reportStart(report) }
        override suspend fun reportProgress(report: PlaybackReport) = request { connection.playback.reportProgress(report) }
        override suspend fun reportStopped(report: PlaybackReport) = request { connection.playback.reportStopped(report) }
        override suspend fun ping(playSessionId: PlaySessionId) = request { connection.playback.ping(playSessionId) }
        override suspend fun timeline(itemId: ItemId, sourceId: MediaSourceId) = request { connection.playback.timeline(itemId, sourceId) }
        override suspend fun segments(itemId: ItemId) = request { connection.playback.segments(itemId) }
        override fun directPlayUrl(itemId: ItemId, source: MediaSource) = connection.playback.directPlayUrl(itemId, source)
        override fun resolveServerUrl(url: OpaqueUrl) = connection.playback.resolveServerUrl(url)
        override fun mediaRequestHeaders() = connection.playback.mediaRequestHeaders()
    }

    override val realtime = object : RealtimeGateway {
        override val events = connection.realtime.events
        override suspend fun postCapabilities() = request { connection.realtime.postCapabilities() }
    }

    override val images = connection.images

    private suspend fun <T> request(block: suspend () -> Outcome<T>): Outcome<T> = block().also {
        if (it is Outcome.Failure && it.error is AppError.Unauthorized) onUnauthorized()
    }
}
