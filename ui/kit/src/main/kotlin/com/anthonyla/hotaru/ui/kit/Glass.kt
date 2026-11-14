package com.anthonyla.hotaru.ui.kit

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val Tint = Brush.verticalGradient(listOf(Color(0xD6585450), Color(0xD6423E3A)))

/** Light catching the rim: brightest at the top-left corner, faint along the sides, back a little at the bottom-right. */
private val Rim = Brush.linearGradient(0f to Color.White.copy(alpha = 0.4f), 0.35f to Color.White.copy(alpha = 0.06f), 0.75f to Color.White.copy(alpha = 0.06f), 1f to Color.White.copy(alpha = 0.18f))

/**
 * visionOS's glass without its blur, which Horizon OS gives neither a window over the room nor a panel over video: a grey
 * the surroundings show through, lit along its rim. The window, the tab bar, panes, popovers and the player's bar are all
 * made of it.
 */
fun Modifier.glass(shape: Shape = HotaruShapes.pane): Modifier = background(Tint, shape).border(1.dp, Rim, shape)

/**
 * A screen whose content scrolls under its bar and fades out there, which is how visionOS keeps a toolbar readable without
 * a second layer of glass. [content] fills the screen and gets the bar's height as top padding; with [fade] off, what lies
 * under the bar shows in full.
 */
@Composable
fun FloatingBarScreen(bar: @Composable () -> Unit, fade: Boolean = true, content: @Composable (PaddingValues) -> Unit) {
    val strength by animateFloatAsState(if (fade) 1f else 0f, tween(HotaruMotion.quickMs), label = "fade")
    Scaffold(topBar = bar, containerColor = Color.Transparent, contentColor = HotaruColors.Text) { padding ->
        val barHeight = padding.calculateTopPadding()
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                .drawWithCache {
                    val height = barHeight.toPx()
                    val clear = Color.Black.copy(alpha = 1f - strength)
                    val mask = Brush.verticalGradient(0f to clear, 0.65f to clear, 1f to Color.Black, endY = height)
                    onDrawWithContent {
                        drawContent()
                        drawRect(mask, size = Size(size.width, height), blendMode = BlendMode.DstIn)
                    }
                },
        ) { content(padding) }
    }
}

/** Between the rows of a group. */
@Composable
fun Separator() {
    Box(Modifier.padding(horizontal = HotaruDimens.lg).fillMaxWidth().height(1.dp).background(HotaruColors.Separator))
}
