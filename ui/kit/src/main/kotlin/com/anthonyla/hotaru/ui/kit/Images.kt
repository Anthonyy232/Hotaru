package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.map.Mapper
import coil3.network.httpHeaders
import coil3.network.NetworkHeaders
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Dimension
import coil3.size.Size
import com.anthonyla.hotaru.domain.CollectionType
import com.anthonyla.hotaru.domain.ImageRef
import com.anthonyla.hotaru.domain.ImageSpec
import com.anthonyla.hotaru.domain.ItemKind
import com.anthonyla.hotaru.presentation.ImageUrls
import com.meta.spatial.uiset.theme.icons.regular.Folder
import com.meta.spatial.uiset.theme.icons.regular.MyMedia
import com.meta.spatial.uiset.theme.icons.regular.Television
import com.meta.spatial.uiset.theme.icons.regular.VolumeOn
import com.meta.spatial.uiset.theme.icons.SpatialIcons

/**
 * Coil resolves the layout size before mapping, so each image is requested at its rendered pixel size:
 * the server never upscales and the headset never decodes 4K posters. Null (signed out) leaves the request unhandled.
 */
class ServerImageMapper(private val urls: ImageUrls) : Mapper<ImageRef, String> {
    override fun map(data: ImageRef, options: Options): String? =
        urls.url(data, ImageSpec(fillWidth = options.size.width.pixels(), fillHeight = options.size.height.pixels()))

    private fun Dimension.pixels(): Int? = (this as? Dimension.Pixels)?.px
}

/** Fetched at its rendered size through [ServerImageMapper], which the app registers with Coil's image loader. */
@Composable
fun ServerImage(
    ref: ImageRef?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderTint: Color = HotaruColors.Fill,
    placeholderIcon: ImageVector? = null,
) = RemoteImage(ref, modifier, contentDescription, placeholderTint, placeholderIcon)

/** An absolute URL, fetched as it is. */
@Composable
fun ServerImage(url: String?, modifier: Modifier = Modifier, placeholderIcon: ImageVector? = null) =
    RemoteImage(url, modifier, contentDescription = null, HotaruColors.Fill, placeholderIcon)

/** The [width] by [height] pixels at [x], [y] of the image at [url], drawn to fill; [headers] sign its request in. */
@Composable
fun ImageRegion(url: String, headers: Map<String, String>, x: Int, y: Int, width: Int, height: Int, modifier: Modifier = Modifier) {
    val context = LocalPlatformContext.current
    val painter = rememberAsyncImagePainter(
        remember(url) {
            val signIn = NetworkHeaders.Builder().apply { headers.forEach { (name, value) -> set(name, value) } }.build()
            ImageRequest.Builder(context).data(url).httpHeaders(signIn).size(Size.ORIGINAL).build()
        },
    )
    Canvas(modifier.clipToBounds()) {
        val whole = painter.intrinsicSize
        val scale = size.width / width
        if (whole.isSpecified) translate(-x * scale, -y * scale) { with(painter) { draw(whole * scale) } }
    }
}

@Composable
private fun RemoteImage(model: Any?, modifier: Modifier, contentDescription: String?, placeholderTint: Color, placeholderIcon: ImageVector?) {
    Box(modifier.background(placeholderTint)) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            placeholderIcon?.let { Icon(it, contentDescription, tint = HotaruColors.TextTertiary, modifier = Modifier.align(Alignment.Center).fillMaxSize(0.3f)) }
        }
    }
}

@Composable
fun placeholderIcon(kind: ItemKind): ImageVector = when (kind) {
    ItemKind.Movie, ItemKind.Video, ItemKind.BoxSet -> SpatialIcons.Regular.MyMedia
    ItemKind.Series, ItemKind.Season, ItemKind.Episode -> SpatialIcons.Regular.Television
    ItemKind.Playlist, ItemKind.Folder, ItemKind.CollectionFolder, ItemKind.Unknown -> SpatialIcons.Regular.Folder
}

@Composable
fun libraryIcon(type: CollectionType): ImageVector = when (type) {
    CollectionType.Movies, CollectionType.BoxSets, CollectionType.HomeVideos -> SpatialIcons.Regular.MyMedia
    CollectionType.TvShows -> SpatialIcons.Regular.Television
    CollectionType.MusicVideos -> SpatialIcons.Regular.VolumeOn
    CollectionType.Playlists, CollectionType.Mixed -> SpatialIcons.Regular.Folder
}
