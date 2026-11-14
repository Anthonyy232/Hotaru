package com.anthonyla.hotaru.testing

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.Outcome
import com.anthonyla.hotaru.application.ports.AccountGateway
import com.anthonyla.hotaru.application.ports.ImageUrlResolver
import com.anthonyla.hotaru.application.ports.ItemQuery
import com.anthonyla.hotaru.application.ports.ItemTimeline
import com.anthonyla.hotaru.application.ports.LibraryGateway
import com.anthonyla.hotaru.application.ports.LibrarySort
import com.anthonyla.hotaru.application.ports.Page
import com.anthonyla.hotaru.application.ports.PlaybackGateway
import com.anthonyla.hotaru.application.ports.PlaybackInfoRequest
import com.anthonyla.hotaru.application.ports.PlaybackInfoResult
import com.anthonyla.hotaru.application.ports.PlaybackReport
import com.anthonyla.hotaru.application.ports.PublicServer
import com.anthonyla.hotaru.application.ports.PublicUser
import com.anthonyla.hotaru.application.ports.QuickConnectCode
import com.anthonyla.hotaru.application.ports.QuickConnectState
import com.anthonyla.hotaru.application.ports.RealtimeGateway
import com.anthonyla.hotaru.application.ports.ServerConnection
import com.anthonyla.hotaru.application.ports.ServerConnectionFactory
import com.anthonyla.hotaru.application.ports.ServerEvent
import com.anthonyla.hotaru.application.ports.UserDataGateway
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.Chapter
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSegmentKind
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.PlaySessionId
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.ServerInfo
import com.anthonyla.hotaru.domain.ServerVersion
import com.anthonyla.hotaru.domain.Session
import com.anthonyla.hotaru.domain.ticks
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.domain.UserId
import com.anthonyla.hotaru.domain.withUserData
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** One fake "server" shared by all connections created from a [FakeServerConnectionFactory]. */
class FakeServer {
    val id = ServerId("fake-server")
    val name = "Fake Jellyfin"
    var version = ServerVersion(12, 2, 0)
    val items: MutableMap<ItemId, MediaItem> = Fixtures.all.associateBy { it.id }.toMutableMap()
    val parents: MutableMap<ItemId, ItemId> = Fixtures.parents.toMutableMap()
    val users = listOf(PublicUser(UserId("user-quest"), "Quest", avatarUrl = null), PublicUser(UserId("user-guest"), "Guest", avatarUrl = null))
    val passwords = mapOf("Quest" to "quest", "Guest" to "guest")
    var quickConnectApproved = false
    val validTokens = mutableSetOf<String>()
    val reports = mutableListOf<Pair<String, PlaybackReport>>()
    val pings = mutableListOf<PlaySessionId>()
    val events = MutableSharedFlow<ServerEvent>(extraBufferCapacity = 32)
    var directPlay = true
    var failNetwork = false
    var lastPlaybackInfo: PlaybackInfoRequest? = null
    var views: suspend () -> List<LibraryView> = { Fixtures.views }
    var playbackInfoDelay = Duration.ZERO
    val segments: Map<ItemId, List<MediaSegment>> = Fixtures.episodes.associate { it.id to listOf(MediaSegment(MediaSegmentKind.Intro, 30.seconds.ticks, 90.seconds.ticks)) }

    private var nextToken = 0

    fun token(user: PublicUser): String = "tok-${user.id.value}-${nextToken++}".also { validTokens += it }

    fun updateUserData(id: ItemId, transform: (UserData) -> UserData): UserData {
        val item = items[id] ?: fail(AppError.NotFound)
        val data = transform(item.userData ?: UserData())
        items[id] = item.withUserData(data)
        return data
    }
}

class FakeServerConnectionFactory : ServerConnectionFactory {
    val server = FakeServer()

    override fun addressCandidates(input: String): List<String> =
        if (input.startsWith("http")) listOf(input.trimEnd('/')) else listOf("http://${input.trimEnd('/')}:8096", "https://${input.trimEnd('/')}")

    override fun publicServer(baseUrl: String): PublicServer = FakePublicServer(server, baseUrl)
    override fun connect(baseUrl: String, session: Session): ServerConnection = FakeServerConnection(server, baseUrl, session)
}

private class FakeError(val error: AppError) : Exception()

private fun fail(error: AppError): Nothing = throw FakeError(error)

private inline fun <T> FakeServer.call(block: () -> T): Outcome<T> {
    if (failNetwork) return AppError.Network("fake offline").failure()
    return try {
        block().success()
    } catch (e: FakeError) {
        e.error.failure()
    }
}

class FakePublicServer(private val fake: FakeServer, private val baseUrl: String) : PublicServer {
    private fun session(user: PublicUser) = Session(Account(fake.id, user.id, user.name, DeviceId("fake-device").forUser(user.id)), fake.token(user))

    override suspend fun info(): Outcome<ServerInfo> = fake.call {
        if ("fake" !in baseUrl) fail(AppError.Network("unreachable $baseUrl"))
        ServerInfo(fake.id, fake.name, fake.version)
    }
    override suspend fun users(): Outcome<List<PublicUser>> = fake.call { fake.users }
    override suspend fun authenticateByName(username: String, password: String): Outcome<Session> = fake.call {
        val user = fake.users.firstOrNull { it.name.equals(username, ignoreCase = true) } ?: fail(AppError.Unauthorized)
        if (fake.passwords[user.name] != password) fail(AppError.Unauthorized)
        session(user)
    }
    override suspend fun quickConnectEnabled(): Outcome<Boolean> = fake.call { true }
    override suspend fun quickConnectInitiate(): Outcome<QuickConnectCode> = fake.call { QuickConnectCode("secret-1", "123456") }
    override suspend fun quickConnectState(secret: String): Outcome<QuickConnectState> = fake.call {
        if (fake.quickConnectApproved) QuickConnectState.Authorized else QuickConnectState.Pending
    }
    override suspend fun authenticateWithQuickConnect(secret: String): Outcome<Session> = fake.call { session(fake.users.first()) }
}

class FakeServerConnection(
    private val fake: FakeServer,
    private val baseUrl: String,
    private val session: Session,
) : ServerConnection {

    private inline fun <T> authed(block: () -> T): Outcome<T> = fake.call {
        if (session.accessToken !in fake.validTokens) fail(AppError.Unauthorized)
        block()
    }

    override val account: AccountGateway = object : AccountGateway {
        override suspend fun currentUser(): Outcome<Account> = authed { session.account }
        override suspend fun logout(): Outcome<Unit> = fake.call { fake.validTokens -= session.accessToken }
    }

    override val library: LibraryGateway = object : LibraryGateway {
        override suspend fun views(): Outcome<List<LibraryView>> = authed { fake.views() }
        override suspend fun items(query: ItemQuery): Outcome<Page<MediaItem>> = authed {
            var list = fake.items.values.asSequence()
            query.parentId?.let { pid ->
                list = if (query.recursive) list.filter { ancestorOf(it, pid) } else list.filter { fake.parents[it.id] == pid }
            }
            if (query.kinds.isNotEmpty()) list = list.filter { it.kind in query.kinds }
            query.isFavorite?.let { fav -> list = list.filter { (it.userData?.isFavorite ?: false) == fav } }
            query.isPlayed?.let { p -> list = list.filter { (it.userData?.played ?: false) == p } }
            val sorted = when (query.sort) {
                LibrarySort.Name -> list.sortedBy { it.name }.toList()
                // The fake holds its items oldest first.
                LibrarySort.DateAdded -> list.toList().asReversed()
                LibrarySort.ReleaseDate -> list.sortedByDescending { it.productionYear }.toList()
            }
            Page(sorted.drop(query.startIndex).take(query.limit), sorted.size)
        }
        override suspend fun item(id: ItemId): Outcome<MediaItem> = authed { fake.items[id] ?: fail(AppError.NotFound) }
        override suspend fun resume(limit: Int): Outcome<List<MediaItem>> = authed { fake.items.values.filter { it.userData?.resumePosition != null }.take(limit) }
        override suspend fun nextUp(limit: Int): Outcome<List<MediaItem>> = authed {
            fake.items.values.filterIsInstance<Episode>().filter { it.userData?.played != true }.sortedBy { it.indexNumber }.take(1)
        }
        override suspend fun latest(viewId: ItemId, limit: Int): Outcome<List<MediaItem>> = authed { fake.items.values.filter { fake.parents[it.id] == viewId }.take(limit) }
        override suspend fun seasons(seriesId: ItemId): Outcome<List<MediaItem>> = authed { fake.items.values.filterIsInstance<Season>().filter { it.seriesId == seriesId } }
        override suspend fun episodes(seriesId: ItemId, seasonId: ItemId?): Outcome<List<Episode>> = authed {
            fake.items.values.filterIsInstance<Episode>().filter { it.seriesId == seriesId && (seasonId == null || it.seasonId == seasonId) }
                .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))
        }
        override suspend fun similar(itemId: ItemId, limit: Int): Outcome<List<MediaItem>> = authed { fake.items.values.filter { it.id != itemId && it.kind == ItemKind.Movie }.take(limit) }
        override suspend fun search(term: String, limit: Int): Outcome<List<MediaItem>> = authed { fake.items.values.filter { it.name.contains(term, ignoreCase = true) }.take(limit) }

        private fun ancestorOf(item: MediaItem, ancestor: ItemId): Boolean =
            generateSequence(fake.parents[item.id]) { fake.parents[it] }.any { it == ancestor }
    }

    override val userData: UserDataGateway = object : UserDataGateway {
        override suspend fun setFavorite(id: ItemId, favorite: Boolean): Outcome<UserData> = authed { fake.updateUserData(id) { it.copy(isFavorite = favorite) } }
        override suspend fun setPlayed(id: ItemId, played: Boolean): Outcome<UserData> = authed {
            fake.updateUserData(id) { it.copy(played = played, resumePosition = null) }
        }
    }

    override val playback: PlaybackGateway = object : PlaybackGateway {
        override suspend fun playbackInfo(request: PlaybackInfoRequest): Outcome<PlaybackInfoResult> = authed {
            delay(fake.playbackInfoDelay)
            fake.lastPlaybackInfo = request
            val item = fake.items[request.itemId] ?: fail(AppError.NotFound)
            PlaybackInfoResult.Playable(listOf(Fixtures.source(item, directPlay = fake.directPlay)), PlaySessionId("ps-${item.id.value}"))
        }
        override fun directPlayUrl(itemId: ItemId, source: MediaSource): OpaqueUrl = OpaqueUrl("$baseUrl/Videos/${itemId.value}/stream?static=true&mediaSourceId=${source.id.value}&tag=${source.eTag}")
        override fun resolveServerUrl(url: OpaqueUrl): OpaqueUrl = OpaqueUrl(baseUrl + url.value)
        override suspend fun reportStart(report: PlaybackReport): Outcome<Unit> = authed { fake.reports += "start" to report }
        override suspend fun reportProgress(report: PlaybackReport): Outcome<Unit> = authed { fake.reports += "progress" to report }
        override suspend fun reportStopped(report: PlaybackReport): Outcome<Unit> = authed {
            fake.reports += "stopped" to report
            fake.updateUserData(report.itemId) { it.copy(resumePosition = report.position) }   // the server owns resume bookkeeping
        }
        override suspend fun ping(playSessionId: PlaySessionId): Outcome<Unit> = authed { fake.pings += playSessionId }
        override fun mediaRequestHeaders(): Map<String, String> = mapOf("Authorization" to "MediaBrowser Token=\"${session.accessToken}\"")
        override suspend fun timeline(itemId: ItemId, sourceId: MediaSourceId): Outcome<ItemTimeline> =
            authed { ItemTimeline((0 until 5).map { i -> Chapter("Chapter ${i + 1}", (i * 20).minutes.ticks) }, trickplay = null) }
        override suspend fun segments(itemId: ItemId): Outcome<List<MediaSegment>> = authed { fake.segments[itemId].orEmpty() }
    }

    override val realtime: RealtimeGateway = object : RealtimeGateway {
        override val events: Flow<ServerEvent> get() = fake.events
        override suspend fun postCapabilities(): Outcome<Unit> = authed { }
    }

    override val images: ImageUrlResolver = ImageUrlResolver { ref, _ -> "$baseUrl/Items/${ref.itemId.value}/Images/${ref.type}?tag=${ref.tag}" }
}
