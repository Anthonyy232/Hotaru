package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.computeWindowSizeClass
import androidx.window.core.layout.WindowSizeClass

/** What the panel window's current size allows; the user resizes it freely, from 640 x 400 dp up. */
@Immutable
data class WindowLayout(val width: Dp, val height: Dp) {
    private val sizeClass = WindowSizeClass.BREAKPOINTS_V1.computeWindowSizeClass(width.value, height.value)

    val wide: Boolean get() = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)

    val short: Boolean get() = !sizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

    val posterWidth: Dp get() = if (wide) 196.dp else 168.dp
    fun cardWidth(landscape: Boolean): Dp = if (landscape) posterWidth * 1.6f else posterWidth

    val heroHeight: Dp get() = (height * 0.6f).coerceAtMost(560.dp)
}

val LocalWindowLayout = compositionLocalOf<WindowLayout> { error("WindowContent provides the window layout") }

/** Resizing recomposes with the new size (PanelActivity handles the resize itself instead of being recreated). */
@Composable
fun ProvideWindowLayout(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalWindowLayout provides WindowLayout(maxWidth, maxHeight), content = content)
    }
}
