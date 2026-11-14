package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.application.AppError
import com.anthonyla.hotaru.application.failure
import com.anthonyla.hotaru.application.flatMap
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
import com.anthonyla.hotaru.application.ports.PlaybackRefusal
import com.anthonyla.hotaru.application.ports.PlaybackReport
import com.anthonyla.hotaru.application.ports.PublicServer
import com.anthonyla.hotaru.application.ports.PublicUser
import com.anthonyla.hotaru.application.ports.QuickConnectCode
import com.anthonyla.hotaru.application.ports.QuickConnectState
import com.anthonyla.hotaru.application.ports.UserDataGateway
import com.anthonyla.hotaru.application.success
import com.anthonyla.hotaru.domain.Account
import com.anthonyla.hotaru.domain.DeviceId
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSpec
import com.anthonyla.hotaru.domain.ImageType
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.PlayMethod
import com.anthonyla.hotaru.domain.PlaySessionId
import com.anthonyla.hotaru.domain.ServerId
import com.anthonyla.hotaru.domain.ServerInfo
import com.anthonyla.hotaru.domain.ServerVersion
import com.anthonyla.hotaru.domain.Session
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.Trickplay
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.infra.jellyfin.Mappers.itemId
import com.anthonyla.hotaru.infra.jellyfin.Mappers.userId
import com.anthonyla.hotaru.infra.jellyfin.Mappers.uuid
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.authenticationApi
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.showApi
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.api.client.extensions.trickPlayApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userDataApi
import org.jellyfin.sdk.api.client.extensions.userViewApi
import org.jellyfin.sdk.api.client.extensions.videoApi
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.jellyfin.sdk.model.api.AuthenticationResult
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlaybackErrorCode
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.QuickConnectDto
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.TrickplayInfoDto
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSeasonsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.jellyfin.sdk.model.api.PlayMethod as SdkPlayMethod
import org.jellyfin.sdk.model.serializer.toUUID

private const val IMAGE_QUALITY = 88

// Home rows feed the hero, which shows the synopsis and genres.
private val HOME_FIELDS = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)

internal class JellyfinPublicServer(private val api: ApiClient) : PublicServer {
    override suspend fun info(): Outcome<ServerInfo> = sdk { api.systemApi.getPublicSystemInfo().content }.flatMap { info ->
        val id = info.id
        val version = info.version?.let(ServerVersion::parse)
        // Any web server can answer a candidate address; only Jellyfin reports an id and a version.
        if (id == null || version == null) return@flatMap AppError.Network("Not a Jellyfin server").failure()
        ServerInfo(ServerId(id), checkNotNull(info.serverName), version).success()
    }

    private fun session(result: AuthenticationResult): Session {
        val user = checkNotNull(result.user)
        val userId = user.id.userId()
        val account = Account(ServerId(checkNotNull(result.serverId)), userId, checkNotNull(user.name), DeviceId(api.deviceInfo.id).forUser(userId))
        return Session(account, checkNotNull(result.accessToken))
    }

    override suspend fun users(): Outcome<List<PublicUser>> = sdk {
        api.userApi.getPublicUsers().content.map { PublicUser(it.id.userId(), checkNotNull(it.name), it.primaryImageTag?.let { tag -> api.imageApi.getUserImageUrl(it.id, tag) }) }
    }

    override suspend fun authenticateByName(username: String, password: String): Outcome<Session> = sdk {
        session(api.authenticationApi.authenticateUserByName(AuthenticateUserByName(username, password)).content)
    }

    override suspend fun quickConnectEnabled(): Outcome<Boolean> = sdk { api.authenticationApi.getQuickConnectEnabled().content }

    override suspend fun quickConnectInitiate(): Outcome<QuickConnectCode> = sdk {
        val r = api.authenticationApi.initiateQuickConnect().content
        QuickConnectCode(r.secret, r.code)
    }

    /** The server answers 404 once a code has expired. */
    override suspend fun quickConnectState(secret: String): Outcome<QuickConnectState> =
        when (val r = sdk { api.authenticationApi.getQuickConnectState(secret).content }) {
            is Outcome.Success -> Outcome.Success(if (r.value.authenticated) QuickConnectState.Authorized else QuickConnectState.Pending)
            is Outcome.Failure -> if (r.error is AppError.NotFound) Outcome.Success(QuickConnectState.Expired) else r
        }

    override suspend fun authenticateWithQuickConnect(secret: String): Outcome<Session> = sdk {
        session(api.authenticationApi.authenticateWithQuickConnect(QuickConnectDto(secret)).content)
    }
}

internal class JellyfinAccountGateway(private val api: ApiClient, private val session: Session) : AccountGateway {
    override suspend fun currentUser(): Outcome<Account> = sdk { session.account.copy(name = checkNotNull(api.userApi.getCurrentUser().content.name)) }

    override suspend fun logout(): Outcome<Unit> = sdk { api.sessionApi.reportSessionEnded() }
}

internal class JellyfinLibraryGateway(private val api: ApiClient) : LibraryGateway {
    override suspend fun views(): Outcome<List<LibraryView>> = sdk {
        api.userViewApi.getUserViews().content.items.mapNotNull(Mappers::view)
    }

    override suspend fun items(query: ItemQuery): Outcome<Page<MediaItem>> = sdk {
        val (sortBy, sortOrder) = when (query.sort) {
            LibrarySort.Name -> listOf(ItemSortBy.SORT_NAME) to listOf(SortOrder.ASCENDING)
            LibrarySort.DateAdded -> listOf(ItemSortBy.DATE_CREATED, ItemSortBy.SORT_NAME) to listOf(SortOrder.DESCENDING, SortOrder.ASCENDING)
            LibrarySort.ReleaseDate -> listOf(ItemSortBy.PREMIERE_DATE, ItemSortBy.SORT_NAME) to listOf(SortOrder.DESCENDING, SortOrder.ASCENDING)
        }
        val result = api.libraryApi.getItems(
            GetItemsRequest(
                parentId = query.parentId?.uuid(),
                includeItemTypes = query.kinds.map(Mappers::sdkKind).ifEmpty { null },
                recursive = query.recursive,
                isPlayed = query.isPlayed,
                isFavorite = query.isFavorite,
                sortBy = sortBy,
                sortOrder = sortOrder,
                startIndex = query.startIndex,
                limit = query.limit,
                imageTypeLimit = 1,
            ),
        ).content
        Page(result.items.map(Mappers::item), result.totalRecordCount)
    }

    override suspend fun item(id: ItemId): Outcome<MediaItem> = sdk { Mappers.item(api.libraryApi.getItem(id.uuid()).content) }

    override suspend fun resume(limit: Int): Outcome<List<MediaItem>> = sdk {
        api.libraryApi.getResumeItems(GetResumeItemsRequest(limit = limit, fields = HOME_FIELDS, imageTypeLimit = 1, includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.EPISODE, BaseItemKind.VIDEO)))
            .content.items.map(Mappers::item)
    }

    override suspend fun nextUp(limit: Int): Outcome<List<MediaItem>> = sdk {
        api.showApi.getNextUp(GetNextUpRequest(limit = limit, fields = HOME_FIELDS, imageTypeLimit = 1, enableResumable = false))
            .content.items.map(Mappers::item)
    }

    override suspend fun latest(viewId: ItemId, limit: Int): Outcome<List<MediaItem>> = sdk {
        api.libraryApi.getLatestMedia(GetLatestMediaRequest(parentId = viewId.uuid(), limit = limit, fields = HOME_FIELDS, imageTypeLimit = 1))
            .content.map(Mappers::item)
    }

    override suspend fun seasons(seriesId: ItemId): Outcome<List<MediaItem>> = sdk {
        api.showApi.getSeasons(GetSeasonsRequest(seriesId = seriesId.uuid(), imageTypeLimit = 1, isMissing = false))
            .content.items.map(Mappers::item)
    }

    override suspend fun episodes(seriesId: ItemId, seasonId: ItemId?): Outcome<List<Episode>> = sdk {
        api.showApi.getEpisodes(GetEpisodesRequest(seriesId = seriesId.uuid(), seasonId = seasonId?.uuid(), fields = listOf(ItemFields.OVERVIEW), imageTypeLimit = 1, isMissing = false))
            .content.items.map { Mappers.item(it) as Episode }
    }

    override suspend fun similar(itemId: ItemId, limit: Int): Outcome<List<MediaItem>> = sdk {
        api.libraryApi.getSimilarItems(GetSimilarItemsRequest(itemId = itemId.uuid(), limit = limit)).content.items.map(Mappers::item)
    }

    override suspend fun search(term: String, limit: Int): Outcome<List<MediaItem>> = sdk {
        api.libraryApi.getItems(
            GetItemsRequest(
                searchTerm = term, recursive = true, limit = limit, imageTypeLimit = 1,
                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES, BaseItemKind.EPISODE, BaseItemKind.BOX_SET, BaseItemKind.VIDEO),
            ),
        ).content.items.map(Mappers::item)
    }
}

internal class JellyfinUserDataGateway(private val api: ApiClient) : UserDataGateway {
    override suspend fun setFavorite(id: ItemId, favorite: Boolean): Outcome<UserData> = sdk {
        val dto = if (favorite) api.userDataApi.markFavoriteItem(id.uuid()).content else api.userDataApi.unmarkFavoriteItem(id.uuid()).content
        Mappers.userData(dto)
    }
    override suspend fun setPlayed(id: ItemId, played: Boolean): Outcome<UserData> = sdk {
        val dto = if (played) api.userDataApi.markPlayedItem(id.uuid()).content else api.userDataApi.markUnplayedItem(id.uuid()).content
        Mappers.userData(dto)
    }
}

internal class JellyfinPlaybackGateway(private val api: ApiClient, private val session: Session) : PlaybackGateway {
    override suspend fun playbackInfo(request: PlaybackInfoRequest): Outcome<PlaybackInfoResult> = sdk {
        val response = api.mediaInfoApi.getPostedPlaybackInfo(
            request.itemId.uuid(),
            PlaybackInfoDto(
                maxStreamingBitrate = request.maxStreamingBitrate,
                startTimeTicks = request.startAt.value,
                audioStreamIndex = request.audioStreamIndex,
                subtitleStreamIndex = Mappers.subtitleIndex(request.subtitles),
                maxAudioChannels = request.capabilities.maxAudioChannels,
                deviceProfile = JellyfinDeviceProfile.build(request.capabilities),
            ),
        ).content
        // The server opens a play session whenever it answers without an ErrorCode.
        response.errorCode?.let {
            PlaybackInfoResult.Refused(
                when (it) {
                    PlaybackErrorCode.NOT_ALLOWED -> PlaybackRefusal.NotAllowed
                    PlaybackErrorCode.NO_COMPATIBLE_STREAM -> PlaybackRefusal.NoCompatibleStream
                    PlaybackErrorCode.RATE_LIMIT_EXCEEDED -> PlaybackRefusal.RateLimitExceeded
                },
            )
        }
            ?: PlaybackInfoResult.Playable(response.mediaSources.map(Mappers::source), PlaySessionId(checkNotNull(response.playSessionId)))
    }

    // Media URLs carry no credentials: the player sends mediaRequestHeaders() on every request
    // (manifest, segments, side-loaded subtitles) through its shared OkHttp data source.
    override fun directPlayUrl(itemId: ItemId, source: MediaSource): OpaqueUrl = OpaqueUrl(
        api.videoApi.getVideoStreamUrl(itemId = itemId.uuid(), static = true, mediaSourceId = source.id.value, tag = source.eTag),
    )

    override fun resolveServerUrl(url: OpaqueUrl): OpaqueUrl {
        val raw = url.value
        // External-URL subtitle streams (IsExternalUrl) are delivered as absolute URLs.
        if (raw.startsWith("http://") || raw.startsWith("https://")) return url
        // TranscodingUrl already carries its own query (PlaySessionId, ...); keep it opaque.
        return OpaqueUrl(api.createUrl(raw, ignorePathParameters = true))
    }

    override suspend fun reportStart(report: PlaybackReport): Outcome<Unit> = sdk {
        api.sessionApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true, itemId = report.itemId.uuid(), mediaSourceId = report.mediaSourceId.value,
                audioStreamIndex = report.audioStreamIndex, subtitleStreamIndex = report.subtitleStreamIndex,
                isPaused = report.isPaused, isMuted = report.isMuted, positionTicks = report.position.value, volumeLevel = report.volume,
                playMethod = method(report.method), playSessionId = report.playSessionId.value, repeatMode = RepeatMode.REPEAT_NONE, playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    override suspend fun reportProgress(report: PlaybackReport): Outcome<Unit> = sdk {
        api.sessionApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true, itemId = report.itemId.uuid(), mediaSourceId = report.mediaSourceId.value,
                audioStreamIndex = report.audioStreamIndex, subtitleStreamIndex = report.subtitleStreamIndex,
                isPaused = report.isPaused, isMuted = report.isMuted, positionTicks = report.position.value, volumeLevel = report.volume,
                playMethod = method(report.method), playSessionId = report.playSessionId.value, repeatMode = RepeatMode.REPEAT_NONE, playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    override suspend fun reportStopped(report: PlaybackReport): Outcome<Unit> = sdk {
        api.sessionApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = report.itemId.uuid(), mediaSourceId = report.mediaSourceId.value, positionTicks = report.position.value,
                playSessionId = report.playSessionId.value, failed = false,
            ),
        )
    }

    override suspend fun ping(playSessionId: PlaySessionId): Outcome<Unit> = sdk { api.sessionApi.pingPlaybackSession(playSessionId.value) }

    // The same builder (and value encoding) the SDK uses for its own API requests.
    override fun mediaRequestHeaders(): Map<String, String> =
        mapOf("Authorization" to AuthorizationHeaderBuilder.buildHeader(api.clientInfo.name, api.clientInfo.version, api.deviceInfo.id, api.deviceInfo.name, session.accessToken))

    override suspend fun timeline(itemId: ItemId, sourceId: MediaSourceId): Outcome<ItemTimeline> = sdk {
        val item = api.libraryApi.getItem(itemId.uuid()).content
        // The server may keep several widths of preview; the widest is still small.
        val trickplay = item.trickplay?.get(sourceId.value)?.values?.maxByOrNull { it.width }
        ItemTimeline(item.chapters.orEmpty().map(Mappers::chapter), trickplay?.let { trickplay(itemId, sourceId, it) })
    }

    private fun trickplay(itemId: ItemId, sourceId: MediaSourceId, info: TrickplayInfoDto): Trickplay {
        val perSheet = info.tileWidth * info.tileHeight
        return Trickplay(
            sheets = (0 until (info.thumbnailCount + perSheet - 1) / perSheet).map {
                OpaqueUrl(api.trickPlayApi.getTrickplayTileImageUrl(itemId.uuid(), info.width, it, sourceId.value.toUUID()))
            },
            headers = mediaRequestHeaders(),
            columns = info.tileWidth,
            perSheet = perSheet,
            count = info.thumbnailCount,
            interval = Ticks.ofMillis(info.interval.toLong()),
            thumbnailWidth = info.width,
            thumbnailHeight = info.height,
        )
    }

    override suspend fun segments(itemId: ItemId): Outcome<List<MediaSegment>> = sdk {
        api.mediaSegmentApi.getItemSegments(itemId.uuid()).content.items.map(Mappers::segment)
    }

    private fun method(m: PlayMethod): SdkPlayMethod = when (m) {
        PlayMethod.DirectPlay -> SdkPlayMethod.DIRECT_PLAY
        PlayMethod.DirectStream -> SdkPlayMethod.DIRECT_STREAM
        PlayMethod.Transcode -> SdkPlayMethod.TRANSCODE
    }
}

internal class JellyfinImageUrlResolver(private val api: ApiClient) : ImageUrlResolver {
    override fun url(ref: ImageRef, spec: ImageSpec): String {
        val type = when (ref.type) {
            ImageType.Primary -> SdkImageType.PRIMARY
            ImageType.Backdrop -> SdkImageType.BACKDROP
            ImageType.Thumb -> SdkImageType.THUMB
        }
        return api.imageApi.getItemImageUrl(
            itemId = ref.itemId.uuid(),
            imageType = type,
            fillWidth = spec.fillWidth, fillHeight = spec.fillHeight,
            quality = IMAGE_QUALITY, tag = ref.tag, imageIndex = ref.index.takeIf { it > 0 },
        )
    }
}
