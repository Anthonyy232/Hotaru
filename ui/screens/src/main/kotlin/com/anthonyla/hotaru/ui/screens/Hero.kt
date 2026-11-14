package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.presentation.library.ItemSummary
import com.anthonyla.hotaru.ui.kit.DisplayStyle
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.LocalWindowLayout
import com.anthonyla.hotaru.ui.kit.ServerImage
import com.anthonyla.hotaru.ui.kit.Tag
import com.meta.spatial.uiset.theme.SpatialTheme

/** The item's backdrop across the top of the window, fading into its glass, with [content] over it. */
@Composable
internal fun HeroHeader(item: MediaItem, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = LocalWindowLayout.current.heroHeight)) {
        HeroBackdrop(item, Modifier.matchParentSize())
        content()
    }
}

@Composable
internal fun headline() = if (LocalWindowLayout.current.let { it.short || !it.wide }) SpatialTheme.typography.headline1Strong else DisplayStyle

@Composable
private fun HeroBackdrop(item: MediaItem, modifier: Modifier = Modifier) {
    Box(
        modifier
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(0.5f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn)
            },
    ) {
        ServerImage(item.images.backdrop, Modifier.fillMaxSize(), placeholderTint = Color.Transparent)
        // Text sits at the bottom left, so the picture is darkest there.
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.3f), HotaruColors.Scrim))))
        Box(Modifier.fillMaxHeight().fillMaxWidth(0.7f).background(Brush.horizontalGradient(listOf(HotaruColors.Scrim, Color.Transparent))))
    }
}

@Composable
internal fun MetaLine(summary: ItemSummary) {
    val text = SpatialTheme.typography.body2
    Row(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md), verticalAlignment = Alignment.CenterVertically) {
        summary.episodeCode?.let { Tag(it) }
        summary.year?.let { Text(it, color = HotaruColors.TextSecondary, style = text) }
        summary.runtime?.let { Text(it, color = HotaruColors.TextSecondary, style = text) }
        summary.officialRating?.let { Tag(it) }
        summary.communityRating?.let { Text(it, color = HotaruColors.TextSecondary, style = text) }
        if (summary.is3D) Tag("3D")
        summary.genres?.let { Text(it, color = HotaruColors.TextSecondary, style = text, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}
