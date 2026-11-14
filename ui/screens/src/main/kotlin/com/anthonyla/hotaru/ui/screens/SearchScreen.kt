package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Modifier
import com.anthonyla.hotaru.presentation.library.SearchIntent
import com.anthonyla.hotaru.presentation.library.SearchViewModel
import com.anthonyla.hotaru.ui.kit.ErrorLine
import com.anthonyla.hotaru.ui.kit.FloatingBarScreen
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.LocalWindowLayout
import com.anthonyla.hotaru.ui.kit.MediaCard
import com.anthonyla.hotaru.ui.kit.Spinner
import com.meta.spatial.uiset.input.SpatialSearchBar
import com.meta.spatial.uiset.theme.SpatialTheme

/** The field takes focus on open so the system keyboard is up at once. */
@Composable
internal fun SearchScreen(vm: SearchViewModel) {
    val state by vm.state.collectAsState()
    val layout = LocalWindowLayout.current
    val field = remember { FocusRequester() }
    LaunchedEffect(field) { field.requestFocus() }
    FloatingBarScreen(
        bar = {
            Row(Modifier.padding(horizontal = HotaruDimens.gutter, vertical = HotaruDimens.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                SpatialSearchBar(
                    modifier = Modifier.weight(1f).focusRequester(field),
                    query = state.query,
                    placeholder = "Movies, shows and episodes",
                    onQueryChange = { vm.onIntent(SearchIntent.QueryChanged(it)) },
                    // Results follow the typing, so the keyboard's search key has nothing left to do.
                    onQuerySubmit = {},
                )
                if (state.searching) Spinner()
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(layout.posterWidth),
            contentPadding = padding.plus(PaddingValues(start = HotaruDimens.gutter, end = HotaruDimens.gutter, top = HotaruDimens.sm, bottom = HotaruDimens.section)),
            horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xl), modifier = Modifier.fillMaxSize(),
        ) {
            state.error?.let { item(span = { GridItemSpan(maxLineSpan) }) { ErrorLine(it, onRetry = { vm.onIntent(SearchIntent.Retry) }) } }
            if (state.showNoResults) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState("No results", "Nothing matches “${state.searchedTerm}”.") }
            items(state.posterResults, key = { it.id.value }) { item -> MediaCard(item, onClick = { vm.onIntent(SearchIntent.Open(item)) }) }
            if (state.episodeResults.isNotEmpty()) {
                item(key = "episodes", span = { GridItemSpan(maxLineSpan) }) { Text("Episodes", style = SpatialTheme.typography.headline3Strong, modifier = Modifier.padding(top = HotaruDimens.sm)) }
            }
            items(state.episodeResults, key = { it.id.value }, span = { GridItemSpan(minOf(2, maxLineSpan)) }) { item ->
                MediaCard(item, onClick = { vm.onIntent(SearchIntent.Open(item)) })
            }
        }
    }
}
