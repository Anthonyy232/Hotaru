package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anthonyla.hotaru.presentation.library.Hero
import com.anthonyla.hotaru.presentation.library.HomeIntent
import com.anthonyla.hotaru.presentation.library.HomeViewModel
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.LocalWindowLayout
import com.anthonyla.hotaru.ui.kit.MediaRow
import com.anthonyla.hotaru.ui.kit.PrimaryTarget
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.icons.regular.Play
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun HomeScreen(vm: HomeViewModel) {
    val state by vm.state.collectAsState()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = if (state.hero == null) HotaruDimens.gutter else 0.dp, bottom = HotaruDimens.section), verticalArrangement = Arrangement.spacedBy(HotaruDimens.gutter)) {
        state.hero?.let { hero -> item(key = "hero") { HomeHero(hero, onPlay = { vm.onIntent(HomeIntent.Play(hero.item)) }, onDetails = { vm.onIntent(HomeIntent.Open(hero.item)) }) } }
        if (state.loading && state.rows.isEmpty()) item { Loading() }
        state.error?.let { err -> item { ErrorLine(err, Modifier.padding(horizontal = HotaruDimens.gutter), onRetry = { vm.onIntent(HomeIntent.Retry) }) } }
        items(state.rows, key = { it.id }) { row -> MediaRow(row.title, row.items, onItemClick = { vm.onIntent(HomeIntent.Open(it)) }) }
        if (state.showEmpty) item { EmptyState("Your library is empty", "Add media on the server and it will show up here.") }
    }
}

@Composable
private fun HomeHero(hero: Hero, onPlay: () -> Unit, onDetails: () -> Unit) {
    val layout = LocalWindowLayout.current
    val summary = hero.summary
    HeroHeader(hero.item) {
        Column(Modifier.align(Alignment.BottomStart).padding(HotaruDimens.gutter), verticalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
            Text(summary.title, style = headline(), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = HotaruDimens.readingWidth))
            MetaLine(summary)
            hero.item.overview?.let {
                Text(it, style = SpatialTheme.typography.body1, maxLines = if (layout.short) 2 else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = HotaruDimens.readingWidth))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap), modifier = Modifier.padding(top = HotaruDimens.sm)) {
                hero.playLabel?.let { PrimaryTarget { PrimaryButton(it, onPlay, leading = { Icon(SpatialIcons.Regular.Play, null) }) } }
                SecondaryButton("Details", onDetails)
            }
        }
    }
}
