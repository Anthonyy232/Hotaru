package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.Spinner
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun Loading() {
    Box(Modifier.fillMaxWidth().padding(HotaruDimens.section), contentAlignment = Alignment.Center) { Spinner(size = 40.dp) }
}

@Composable
internal fun EmptyState(title: String, detail: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = HotaruDimens.gutter, vertical = HotaruDimens.section), verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Text(title, style = SpatialTheme.typography.headline3Strong)
        Text(detail, style = SpatialTheme.typography.body1, color = HotaruColors.TextSecondary)
    }
}
