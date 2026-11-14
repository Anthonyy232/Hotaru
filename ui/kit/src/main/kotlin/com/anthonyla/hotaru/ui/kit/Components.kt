package com.anthonyla.hotaru.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.presentation.library.hasLandscapeCard
import com.anthonyla.hotaru.presentation.library.MediaCardContent
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.PrimaryCircleButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.button.SecondaryCircleButton
import com.meta.spatial.uiset.control.SpatialSwitch
import com.meta.spatial.uiset.control.SwitchDefaults
import com.meta.spatial.uiset.theme.icons.regular.CheckAlt
import com.meta.spatial.uiset.theme.icons.regular.ChevronLeft
import com.meta.spatial.uiset.theme.icons.regular.HeartOn
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme

/** Landscape cards are video stills, the others posters. */
fun cardAspectRatio(landscape: Boolean): Float = if (landscape) 16f / 9f else 2f / 3f

@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = item.hasLandscapeCard,
) {
    val content = MediaCardContent.of(item, landscape)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val focused by interaction.collectIsFocusedAsState()
    val lift by animateFloatAsState(if (hovered || focused) 1f else 0f, label = "lift")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(cardAspectRatio(landscape))
                .graphicsLayer {
                    val scale = 1f + (HotaruMotion.liftScale - 1f) * lift
                    scaleX = scale
                    scaleY = scale
                }
                .clip(HotaruShapes.card)
                .clickable(interaction, LocalIndication.current, onClick = onClick),
        ) {
            MediaStill(item, content)
        }
        Column {
            Text(item.name, style = SpatialTheme.typography.body1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            content.subtitle?.let { Text(it, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
fun MediaStill(item: MediaItem, content: MediaCardContent) {
    Box(Modifier.fillMaxSize()) {
        ServerImage(content.image, modifier = Modifier.fillMaxSize(), contentDescription = item.name, placeholderIcon = placeholderIcon(item.kind))
        if (content.overlayCode != null || content.progress != null) {
            Column(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, HotaruColors.Scrim))).padding(HotaruDimens.md),
                verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm),
            ) {
                content.overlayCode?.let { Text(it, style = SpatialTheme.typography.body2Strong) }
                content.progress?.let { ProgressBar(it) }
            }
        }
        Badges(item, Modifier.align(Alignment.TopEnd).padding(HotaruDimens.sm))
    }
}

@Composable
private fun Badges(item: MediaItem, modifier: Modifier = Modifier) {
    val data = item.userData ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        if (data.isFavorite) Badge(SpatialIcons.Regular.HeartOn, SpatialTheme.colorScheme.negative)
        if (data.played) Badge(SpatialIcons.Regular.CheckAlt, SpatialTheme.colorScheme.positive)
        data.unplayedItemCount?.takeIf { it > 0 }?.let { count ->
            Box(Modifier.height(28.dp).glass(HotaruShapes.pill).padding(horizontal = HotaruDimens.md), contentAlignment = Alignment.Center) {
                Text(count.toString(), style = SpatialTheme.typography.body2Strong)
            }
        }
    }
}

@Composable
private fun Badge(icon: ImageVector, tint: Color) {
    Box(Modifier.size(28.dp).glass(HotaruShapes.pill), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    val colors = SpatialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(4.dp).clip(HotaruShapes.pill).background(colors.progressBarOnMedia)) {
        Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.progressOnMedia))
    }
}

@Composable
fun MediaRow(
    title: String,
    items: List<MediaItem>,
    onItemClick: (MediaItem) -> Unit,
) {
    val layout = LocalWindowLayout.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
        Text(title, style = SpatialTheme.typography.headline3Strong, modifier = Modifier.padding(horizontal = HotaruDimens.gutter))
        LazyRow(contentPadding = PaddingValues(horizontal = HotaruDimens.gutter), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
            items(items, key = { it.id.value }) { item ->
                MediaCard(item, onClick = { onItemClick(item) }, modifier = Modifier.width(layout.cardWidth(item.hasLandscapeCard)))
            }
        }
    }
}

/** On is a white fill, which visionOS keeps for a toggled button. */
@Composable
fun ToggleIconButton(icon: ImageVector, label: String, checked: Boolean, onClick: () -> Unit) {
    val semantics = Modifier.semantics {
        contentDescription = label
        role = Role.Switch
        toggleableState = ToggleableState(checked)
    }
    val iconContent: @Composable () -> Unit = { Icon(icon, contentDescription = null) }
    if (checked) PrimaryCircleButton(iconContent, onClick, semantics) else SecondaryCircleButton(iconContent, onClick, semantics)
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, role: Role = Role.RadioButton) {
    val semantics = Modifier.semantics {
        this.role = role
        if (role == Role.Checkbox) toggleableState = ToggleableState(selected) else this.selected = selected
    }
    if (selected) PrimaryButton(text, onClick, semantics) else SecondaryButton(text, onClick, semantics)
}

@Composable
fun CodeDisplay(code: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
        code.forEach { ch ->
            Box(Modifier.size(width = 64.dp, height = 88.dp).background(HotaruColors.Recess, HotaruShapes.card), contentAlignment = Alignment.Center) {
                Text(ch.toString(), style = DisplayStyle)
            }
        }
    }
}

@Composable
fun Tag(text: String) {
    Box(Modifier.border(1.dp, HotaruColors.TextSecondary, HotaruShapes.tag).padding(horizontal = HotaruDimens.sm, vertical = 2.dp)) {
        Text(text, style = SpatialTheme.typography.body2Strong, color = HotaruColors.TextSecondary)
    }
}

@Composable
fun Spinner(size: Dp = 28.dp) {
    CircularProgressIndicator(color = HotaruColors.Text, trackColor = HotaruColors.Fill, strokeWidth = 3.dp, modifier = Modifier.size(size))
}

/** A screen's bar. [onBack] is null on tab destinations, where the tab bar is the way out. */
@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)?, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = HotaruDimens.hitTargetPrimary).padding(horizontal = HotaruDimens.gutter, vertical = HotaruDimens.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap),
    ) {
        if (onBack != null) BackButton(onBack)
        Text(title, style = SpatialTheme.typography.headline1Strong, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
fun BackButton(onClick: () -> Unit, label: String = "Back") {
    SecondaryCircleButton({ Icon(SpatialIcons.Regular.ChevronLeft, contentDescription = label) }, onClick)
}

/** [onRetry] offers to try again, for failures that may pass. */
@Composable
fun ErrorLine(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Row(
        modifier.padding(vertical = HotaruDimens.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap),
    ) {
        Text(message, style = SpatialTheme.typography.body1, color = SpatialTheme.colorScheme.negative, modifier = Modifier.weight(1f, fill = false))
        if (onRetry != null) SecondaryButton("Retry", onRetry)
    }
}

@Composable
fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = HotaruDimens.hitTargetPrimary)
            .toggleable(checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = HotaruDimens.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg),
    ) {
        Text(label, style = SpatialTheme.typography.body1, modifier = Modifier.weight(1f))
        SpatialSwitch(checked, onCheckedChange = null, colors = SwitchDefaults.colors().copy(checkedTrackColor = HotaruColors.Glow, checkedIconColor = HotaruColors.Glow))
    }
}

/**
 * Meta asks for 60 dp hit targets on primary controls, and the UI set draws them smaller, so Compose's touch slop around
 * [content] grows to 60 dp instead of its usual 48.
 */
@Composable
fun PrimaryTarget(content: @Composable () -> Unit) {
    val base = LocalViewConfiguration.current
    val primary = remember(base) {
        object : ViewConfiguration by base {
            override val minimumTouchTargetSize = DpSize(HotaruDimens.hitTargetPrimary, HotaruDimens.hitTargetPrimary)
        }
    }
    CompositionLocalProvider(LocalViewConfiguration provides primary, content = content)
}
