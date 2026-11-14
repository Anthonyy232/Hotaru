package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.anthonyla.hotaru.presentation.library.LibraryViewViewModel
import com.anthonyla.hotaru.presentation.library.ViewIntent
import com.anthonyla.hotaru.ui.kit.ChoiceChip
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.FloatingBarScreen
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.LocalWindowLayout
import com.anthonyla.hotaru.ui.kit.MediaCard
import com.anthonyla.hotaru.ui.kit.ScreenHeader
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.icons.regular.ArrowsUpDown
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
internal fun LibraryViewScreen(vm: LibraryViewViewModel) {
    val state by vm.state.collectAsState()
    val layout = LocalWindowLayout.current
    val grid = rememberLazyGridState()
    LaunchedEffect(grid) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { vm.onIntent(ViewIntent.Scrolled(it)) }
    }
    FloatingBarScreen(
        bar = {
            ScreenHeader(state.view.name, onBack = null) {
                Row(horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                    SecondaryButton(state.sortLabel, { vm.onIntent(ViewIntent.NextSort) }, leading = { Icon(SpatialIcons.Regular.ArrowsUpDown, null) })
                    ChoiceChip("Unplayed", selected = state.unplayedOnly, role = Role.Checkbox, onClick = { vm.onIntent(ViewIntent.ToggleUnplayed) })
                    ChoiceChip("Favorites", selected = state.favoritesOnly, role = Role.Checkbox, onClick = { vm.onIntent(ViewIntent.ToggleFavorites) })
                }
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(layout.cardWidth(state.landscape)),
            state = grid,
            contentPadding = padding.plus(PaddingValues(start = HotaruDimens.gutter, end = HotaruDimens.gutter, top = HotaruDimens.sm, bottom = HotaruDimens.section)),
            horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap),
            verticalArrangement = Arrangement.spacedBy(HotaruDimens.xl),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            state.error?.let { item(span = { GridItemSpan(maxLineSpan) }) { ErrorLine(it, onRetry = { vm.onIntent(ViewIntent.Retry) }) } }
            if (state.showEmpty) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("Nothing here yet", state.emptyMessage) }
            items(state.items, key = { it.id.value }) { item -> MediaCard(item, onClick = { vm.onIntent(ViewIntent.Open(item)) }, landscape = state.landscape) }
            if (state.loadingMore) item(span = { GridItemSpan(maxLineSpan) }) { Loading() }
            state.nextPageError?.let { item(span = { GridItemSpan(maxLineSpan) }) { ErrorLine(it, onRetry = { vm.onIntent(ViewIntent.Retry) }) } }
        }
    }
}
