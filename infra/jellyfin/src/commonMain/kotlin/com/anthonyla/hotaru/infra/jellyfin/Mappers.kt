package com.anthonyla.hotaru.infra.jellyfin

import com.anthonyla.hotaru.domain.AudioStream
import com.anthonyla.hotaru.domain.Chapter
import com.anthonyla.hotaru.domain.CollectionType
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.GenericItem
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSet
import com.anthonyla.hotaru.domain.ImageType
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.domain.LibraryView
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.MediaSegment
import com.anthonyla.hotaru.domain.MediaSegmentKind
import com.anthonyla.hotaru.domain.MediaSource
import com.anthonyla.hotaru.domain.MediaSourceId
import com.anthonyla.hotaru.domain.MediaStream
import com.anthonyla.hotaru.domain.Movie
import com.anthonyla.hotaru.domain.OpaqueUrl
import com.anthonyla.hotaru.domain.Person
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.domain.Series
import com.anthonyla.hotaru.domain.SubtitleChoice
import com.anthonyla.hotaru.domain.SubtitleDelivery
import com.anthonyla.hotaru.domain.SubtitleStream
import com.anthonyla.hotaru.domain.TextSubtitleFormat
import com.anthonyla.hotaru.domain.Ticks
import com.anthonyla.hotaru.domain.TranscodingProtocol
import com.anthonyla.hotaru.domain.UserData
import com.anthonyla.hotaru.domain.UserId
import com.anthonyla.hotaru.domain.Video3DFormat
import com.anthonyla.hotaru.domain.VideoStream
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ChapterInfo
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.UserItemDataDto
import org.jellyfin.sdk.model.api.Video3dFormat
import org.jellyfin.sdk.model.api.ImageType as SdkImageType
import org.jellyfin.sdk.model.api.CollectionType as SdkCollectionType
import org.jellyfin.sdk.model.api.MediaProtocol as SdkMediaProtocol
import org.jellyfin.sdk.model.serializer.toUUID

internal object Mappers {

    fun ItemId.uuid(): UUID = value.toUUID()
    fun UUID.itemId(): ItemId = ItemId(toString())
    fun UUID.userId(): UserId = UserId(toString())

    fun userData(dto: UserItemDataDto): UserData = UserData(
        played = dto.played,
        isFavorite = dto.isFavorite,
        resumePosition = dto.playbackPositionTicks.takeIf { it > 0 }?.let(::Ticks),
        unplayedItemCount = dto.unplayedItemCount,
    )

    /** Null for a library with nothing to watch: music, books, photos and live TV. */
    private fun collectionType(type: SdkCollectionType?): CollectionType? = when (type) {
        SdkCollectionType.MOVIES -> CollectionType.Movies
        SdkCollectionType.TVSHOWS -> CollectionType.TvShows
        SdkCollectionType.MUSICVIDEOS -> CollectionType.MusicVideos
        SdkCollectionType.HOMEVIDEOS -> CollectionType.HomeVideos
        SdkCollectionType.BOXSETS -> CollectionType.BoxSets
        SdkCollectionType.PLAYLISTS -> CollectionType.Playlists
        SdkCollectionType.MUSIC, SdkCollectionType.BOOKS, SdkCollectionType.PHOTOS, SdkCollectionType.LIVETV -> null
        else -> CollectionType.Mixed
    }

    fun view(dto: BaseItemDto): LibraryView? = collectionType(dto.collectionType)?.let { LibraryView(dto.id.itemId(), checkNotNull(dto.name), it) }

    fun images(dto: BaseItemDto): ImageSet {
        val id = dto.id.itemId()
        fun ref(type: ImageType, tag: String?, itemId: ItemId = id, index: Int = 0): ImageRef? =
            tag?.let { ImageRef(itemId, type, it, index) }
        val tags = dto.imageTags.orEmpty()
        val backdrops = dto.backdropImageTags.orEmpty().mapIndexedNotNull { i, tag -> ref(ImageType.Backdrop, tag, id, i) }
        val parentBackdrops = dto.parentBackdropItemId?.let { pid -> dto.parentBackdropImageTags.orEmpty().mapIndexedNotNull { i, tag -> ref(ImageType.Backdrop, tag, pid.itemId(), i) } }.orEmpty()
        val primary = ref(ImageType.Primary, tags[SdkImageType.PRIMARY])
            ?: dto.seriesPrimaryImageTag?.let { t -> dto.seriesId?.let { sid -> ref(ImageType.Primary, t, sid.itemId()) } }
            ?: dto.albumPrimaryImageTag?.let { t -> dto.albumId?.let { aid -> ref(ImageType.Primary, t, aid.itemId()) } }
            ?: dto.parentPrimaryImageItemId?.let { pid -> ref(ImageType.Primary, dto.parentPrimaryImageTag, pid.itemId()) }
        return ImageSet(
            primary = primary,
            backdrops = backdrops,
            thumb = ref(ImageType.Thumb, tags[SdkImageType.THUMB]),
            parentBackdrops = parentBackdrops,
            parentThumb = dto.parentThumbItemId?.let { pid -> ref(ImageType.Thumb, dto.parentThumbImageTag, pid.itemId()) }
                ?: dto.seriesThumbImageTag?.let { t -> dto.seriesId?.let { sid -> ref(ImageType.Thumb, t, sid.itemId()) } },
        )
    }

    fun person(p: BaseItemPerson): Person = Person(name = checkNotNull(p.name), role = p.role)

    fun kind(kind: BaseItemKind): ItemKind = when (kind) {
        BaseItemKind.MOVIE -> ItemKind.Movie
        BaseItemKind.SERIES -> ItemKind.Series
        BaseItemKind.SEASON -> ItemKind.Season
        BaseItemKind.EPISODE -> ItemKind.Episode
        BaseItemKind.VIDEO, BaseItemKind.MUSIC_VIDEO, BaseItemKind.TRAILER -> ItemKind.Video
        BaseItemKind.BOX_SET -> ItemKind.BoxSet
        BaseItemKind.PLAYLIST -> ItemKind.Playlist
        BaseItemKind.FOLDER, BaseItemKind.USER_VIEW -> ItemKind.Folder
        BaseItemKind.COLLECTION_FOLDER -> ItemKind.CollectionFolder
        else -> ItemKind.Unknown
    }

    fun sdkKind(kind: ItemKind): BaseItemKind = when (kind) {
        ItemKind.Movie -> BaseItemKind.MOVIE
        ItemKind.Series -> BaseItemKind.SERIES
        ItemKind.Season -> BaseItemKind.SEASON
        ItemKind.Episode -> BaseItemKind.EPISODE
        ItemKind.Video -> BaseItemKind.VIDEO
        ItemKind.BoxSet -> BaseItemKind.BOX_SET
        ItemKind.Playlist -> BaseItemKind.PLAYLIST
        ItemKind.Folder -> BaseItemKind.FOLDER
        ItemKind.CollectionFolder -> BaseItemKind.COLLECTION_FOLDER
        ItemKind.Unknown -> error("no server kind for ItemKind.Unknown")
    }

    fun video3D(format: Video3dFormat?): Video3DFormat? = when (format) {
        Video3dFormat.HALF_SIDE_BY_SIDE -> Video3DFormat.HalfSideBySide
        Video3dFormat.FULL_SIDE_BY_SIDE -> Video3DFormat.FullSideBySide
        Video3dFormat.FULL_TOP_AND_BOTTOM -> Video3DFormat.FullTopAndBottom
        Video3dFormat.HALF_TOP_AND_BOTTOM -> Video3DFormat.HalfTopAndBottom
        Video3dFormat.MVC -> Video3DFormat.MVC
        null -> null
    }

    fun item(dto: BaseItemDto): MediaItem {
        val id = dto.id.itemId()
        val images = images(dto)
        val userData = dto.userData?.let(::userData)
        // Jellyfin reports 0 for a series it has no runtime for.
        val runtime = dto.runTimeTicks?.takeIf { it > 0 }?.let(::Ticks)
        val people = dto.people.orEmpty().map(::person)
        val genres = dto.genres.orEmpty()
        return when (dto.type) {
            BaseItemKind.MOVIE -> Movie(
                id, checkNotNull(dto.name), dto.overview, images, userData, runtime, dto.productionYear,
                dto.communityRating, dto.officialRating, genres, people,
                taglines = dto.taglines.orEmpty(), video3DFormat = video3D(dto.video3dFormat),
            )
            BaseItemKind.SERIES -> Series(
                id, checkNotNull(dto.name), dto.overview, images, userData, runtime, dto.productionYear,
                dto.communityRating, dto.officialRating, genres, people,
            )
            BaseItemKind.SEASON -> Season(
                id, checkNotNull(dto.name), dto.overview, images, userData, runtime, dto.productionYear,
                dto.communityRating, dto.officialRating, genres, people, seriesId = checkNotNull(dto.seriesId) { "season without SeriesId" }.itemId(),
            )
            BaseItemKind.EPISODE -> Episode(
                id, checkNotNull(dto.name), dto.overview, images, userData, runtime, dto.productionYear,
                dto.communityRating, dto.officialRating, genres, people,
                seriesId = dto.seriesId?.itemId(), seriesName = dto.seriesName, seasonId = dto.seasonId?.itemId(),
                indexNumber = dto.indexNumber, indexNumberEnd = dto.indexNumberEnd, parentIndexNumber = dto.parentIndexNumber,
            )
            else -> GenericItem(
                id, kind(dto.type), checkNotNull(dto.name), dto.overview, images, userData, runtime, dto.productionYear,
                dto.communityRating, dto.officialRating, genres, people,
                isFolder = dto.isFolder ?: false,
            )
        }
    }

    fun stream(s: org.jellyfin.sdk.model.api.MediaStream): MediaStream? = when (s.type) {
        MediaStreamType.VIDEO -> VideoStream(
            index = s.index,
            width = s.width, height = s.height,
        )
        MediaStreamType.AUDIO -> AudioStream(
            index = s.index, displayTitle = s.displayTitle,
        )
        MediaStreamType.SUBTITLE -> SubtitleStream(
            index = s.index, language = s.language, title = s.title, displayTitle = s.displayTitle,
            delivery = when (s.deliveryMethod) {
                SubtitleDeliveryMethod.ENCODE -> SubtitleDelivery.Encode
                SubtitleDeliveryMethod.EMBED -> SubtitleDelivery.Embed
                SubtitleDeliveryMethod.EXTERNAL -> SubtitleDelivery.External
                SubtitleDeliveryMethod.HLS -> SubtitleDelivery.Hls
                SubtitleDeliveryMethod.DROP -> SubtitleDelivery.Drop
                null -> null
            },
            deliveryUrl = s.deliveryUrl?.let(::OpaqueUrl),
            // The server converts external subtitles to a profile format and names it as the URL's extension (.../Stream.srt).
            deliveryFormat = TextSubtitleFormat.fromExtension(s.deliveryUrl?.substringBefore('?')?.substringAfterLast('.', "")?.lowercase()),
        )
        else -> null
    }

    fun source(info: MediaSourceInfo): MediaSource = MediaSource(
        id = MediaSourceId(requireNotNull(info.id) { "media source without id" }),
        name = info.name,
        runtime = info.runTimeTicks?.let(::Ticks),
        isLocalFile = info.protocol == SdkMediaProtocol.FILE && !info.isRemote,
        eTag = info.eTag,
        supportsDirectPlay = info.supportsDirectPlay,
        supportsDirectStream = info.supportsDirectStream,
        supportsTranscoding = info.supportsTranscoding,
        transcodingUrl = info.transcodingUrl?.let(::OpaqueUrl),
        transcodingProtocol = when (info.transcodingSubProtocol) { MediaStreamProtocol.HLS -> TranscodingProtocol.Hls; MediaStreamProtocol.HTTP -> TranscodingProtocol.Http },
        streams = info.mediaStreams.orEmpty().mapNotNull(::stream),
        defaultAudioStreamIndex = info.defaultAudioStreamIndex,
        defaultSubtitleStreamIndex = info.defaultSubtitleStreamIndex?.let(::subtitleStream),
        video3DFormat = video3D(info.video3dFormat),
        hasSegments = info.hasSegments,
    )

    // Jellyfin writes subtitle stream index -1 for none, and leaves the index out for its default.
    fun subtitleStream(index: Int): Int? = index.takeIf { it >= 0 }

    fun subtitleChoice(index: Int?): SubtitleChoice =
        if (index == null) SubtitleChoice.Default else subtitleStream(index)?.let(SubtitleChoice::Stream) ?: SubtitleChoice.Off

    fun subtitleIndex(choice: SubtitleChoice): Int? = when (choice) {
        SubtitleChoice.Default -> null
        SubtitleChoice.Off -> -1
        is SubtitleChoice.Stream -> choice.index
    }

    fun chapter(c: ChapterInfo): Chapter = Chapter(c.name, Ticks(c.startPositionTicks))

    fun segment(dto: MediaSegmentDto): MediaSegment = MediaSegment(
        kind = when (dto.type) {
            MediaSegmentType.INTRO -> MediaSegmentKind.Intro
            MediaSegmentType.OUTRO -> MediaSegmentKind.Outro
            MediaSegmentType.RECAP -> MediaSegmentKind.Recap
            MediaSegmentType.PREVIEW -> MediaSegmentKind.Preview
            MediaSegmentType.COMMERCIAL -> MediaSegmentKind.Commercial
            MediaSegmentType.UNKNOWN -> MediaSegmentKind.Unknown
        },
        start = Ticks(dto.startTicks), end = Ticks(dto.endTicks),
    )
}
