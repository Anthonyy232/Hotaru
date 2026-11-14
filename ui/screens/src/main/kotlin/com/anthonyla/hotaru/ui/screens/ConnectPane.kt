package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anthonyla.hotaru.ui.kit.glass
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.meta.spatial.uiset.theme.SpatialTheme

/** The connect screens: a small window of glass of its own, floating in the middle of the app's. */
@Composable
internal fun CenteredPane(maxWidth: Int, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.padding(HotaruDimens.lg).widthIn(max = maxWidth.dp).glass(HotaruShapes.window).padding(HotaruDimens.gutter),
            verticalArrangement = Arrangement.spacedBy(HotaruDimens.xl),
        ) { content() }
    }
}

@Composable
internal fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
        Text(title, style = SpatialTheme.typography.body1Strong, color = HotaruColors.TextSecondary)
        content()
    }
}
