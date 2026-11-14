package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.anthonyla.hotaru.domain.Episode
import com.anthonyla.hotaru.domain.ItemId
import com.anthonyla.hotaru.domain.MediaItem
import com.anthonyla.hotaru.domain.Season
import com.anthonyla.hotaru.presentation.library.DetailIntent
import com.anthonyla.hotaru.presentation.library.DetailViewModel
import com.anthonyla.hotaru.presentation.library.EpisodeRowContent
import com.anthonyla.hotaru.presentation.library.hasLandscapeCard
import com.anthonyla.hotaru.presentation.library.ItemSummary
import com.anthonyla.hotaru.ui.kit.cardAspectRatio
import com.anthonyla.hotaru.ui.kit.ChoiceChip
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.FloatingBarScreen
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.LocalWindowLayout
import com.anthonyla.hotaru.ui.kit.MediaRow
import com.anthonyla.hotaru.ui.kit.MediaStill
import com.anthonyla.hotaru.ui.kit.placeholderIcon
import com.anthonyla.hotaru.ui.kit.PrimaryTarget
import com.anthonyla.hotaru.ui.kit.ScreenHeader
import com.anthonyla.hotaru.ui.kit.ServerImage
import com.anthonyla.hotaru.ui.kit.ToggleIconButton
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.icons.regular.CheckAlt
import com.meta.spatial.uiset.theme.icons.regular.HeartOn
import com.meta.spatial.uiset.theme.icons.regular.Play
import com.meta.spatial.uiset.theme.icons.regular.Replay
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun DetailScreen(vm: DetailViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val item = state.item
    val summary = state.summary
    val list = rememberLazyListState()
    val atTop by remember { derivedStateOf { list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 } }
    val heroGone by remember { derivedStateOf { list.firstVisibleItemIndex > 0 } }
    // The hero lies under the bar in full until it scrolls, and the bar carries the title once the hero has gone.
    FloatingBarScreen(bar = { ScreenHeader(if (heroGone) summary?.title.orEmpty() else "", onBack) }, fade = !atTop) { padding ->
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = HotaruDimens.section), verticalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
            if (item == null || summary == null) {
                item {
                    Box(Modifier.padding(padding)) {
                        if (state.loading) Loading()
                        else state.error?.let { ErrorLine(it, Modifier.padding(horizontal = HotaruDimens.gutter), onRetry = { vm.onIntent(DetailIntent.Retry) }) }
                    }
                }
                return@LazyColumn
            }
            item(key = "header") { DetailHeader(item, summary, state.playLabel, state.canPlay, state.canPlayFromStart, padding, vm::onIntent) }
            state.userDataError?.let { item(key = "userDataError") { ErrorLine(it, Modifier.padding(horizontal = HotaruDimens.gutter)) } }
            if (state.seasons.isNotEmpty()) {
                item(key = "seasons") { SeasonTabs(state.seasons, state.selectedSeasonId) { vm.onIntent(DetailIntent.SelectSeason(it.id)) } }
            }
            items(state.listedEpisodes, key = { it.id.value }) { episode -> EpisodeRow(episode) { vm.onIntent(DetailIntent.OpenChild(episode)) } }
            if (state.childCards.isNotEmpty()) item(key = "children") { Box(Modifier.padding(top = HotaruDimens.lg)) { MediaRow("Contents", state.childCards, onItemClick = { vm.onIntent(DetailIntent.OpenChild(it)) }) } }
            if (state.similar.isNotEmpty()) item(key = "similar") { Box(Modifier.padding(top = HotaruDimens.lg)) { MediaRow("More like this", state.similar, onItemClick = { vm.onIntent(DetailIntent.OpenSimilar(it)) }) } }
        }
    }
}

@Composable
private fun DetailHeader(
    item: MediaItem,
    summary: ItemSummary,
    playLabel: String,
    canPlay: Boolean,
    canPlayFromStart: Boolean,
    bar: PaddingValues,
    onIntent: (DetailIntent) -> Unit,
) {
    val layout = LocalWindowLayout.current
    HeroHeader(item) {
        Row(Modifier.padding(bar).padding(horizontal = HotaruDimens.gutter).padding(bottom = HotaruDimens.gutter), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.gutter)) {
            if (layout.wide && !item.hasLandscapeCard) {
                ServerImage(item.images.primary, Modifier.width(layout.posterWidth).aspectRatio(cardAspectRatio(landscape = false)).clip(HotaruShapes.card), placeholderIcon = placeholderIcon(item.kind))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs)) {
                    Text(summary.title, style = headline(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    summary.episodeTitle?.let { Text(it, style = SpatialTheme.typography.headline2) }
                    summary.tagline?.let { Text(it, style = SpatialTheme.typography.headline3, color = HotaruColors.TextSecondary) }
                }
                MetaLine(summary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap), verticalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                    PrimaryTarget { PrimaryButton(playLabel, { onIntent(DetailIntent.Play) }, isEnabled = canPlay, leading = { Icon(SpatialIcons.Regular.Play, null) }) }
                    if (canPlayFromStart) SecondaryButton("From start", { onIntent(DetailIntent.PlayFromStart) }, leading = { Icon(SpatialIcons.Regular.Replay, null) })
                    ToggleIconButton(SpatialIcons.Regular.HeartOn, "Favorite", checked = item.userData?.isFavorite == true) { onIntent(DetailIntent.ToggleFavorite) }
                    ToggleIconButton(SpatialIcons.Regular.CheckAlt, "Played", checked = item.userData?.played == true) { onIntent(DetailIntent.TogglePlayed) }
                }
                item.overview?.let { Text(it, style = SpatialTheme.typography.body1, modifier = Modifier.widthIn(max = HotaruDimens.readingWidth)) }
                summary.cast?.let { Text(it, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun SeasonTabs(seasons: List<Season>, selected: ItemId?, onSelect: (Season) -> Unit) {
    LazyRow(Modifier.selectableGroup(), contentPadding = PaddingValues(horizontal = HotaruDimens.gutter), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
        items(seasons, key = { it.id.value }) { season -> ChoiceChip(season.name, selected = season.id == selected, onClick = { onSelect(season) }) }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    val content = EpisodeRowContent.of(episode)
    val layout = LocalWindowLayout.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HotaruDimens.gutter - HotaruDimens.sm)
            .clip(HotaruShapes.group)
            .clickable(onClick = onClick)
            .padding(HotaruDimens.sm),
        horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg),
    ) {
        Box(Modifier.width(layout.posterWidth).aspectRatio(cardAspectRatio(landscape = true)).clip(HotaruShapes.row)) { MediaStill(episode, content.card) }
        Column(Modifier.weight(1f).padding(vertical = HotaruDimens.xs), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs)) {
            Text(content.title, style = SpatialTheme.typography.headline3Strong, maxLines = 2, overflow = TextOverflow.Ellipsis)
            content.runtime?.let { Text(it, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary) }
            content.overview?.let {
                Text(it, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary, maxLines = if (layout.wide) 3 else 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
