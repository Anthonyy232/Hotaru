package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val Letters =
    "M30 72 V200 M30 160 A40 40 0 0 1 110 160 " +
        "M154 160 A40 40 0 1 0 234 160 A40 40 0 1 0 154 160 Z " +
        "M288 84 V172 A28 28 0 0 0 316 200 H324 M264 120 H320 " +
        "M364 160 A40 40 0 1 0 444 160 A40 40 0 1 0 364 160 Z M444 120 V200 " +
        "M496 120 V200 M496 160 A40 40 0 0 1 536 120 H542 " +
        "M588 120 V160 A40 40 0 0 0 668 160 V120 M668 120 V200"
private const val Light = "M93 193 A17 17 0 1 0 127 193 A17 17 0 1 0 93 193 Z"

/** The name as drawn in `branding/hotaru-wordmark.svg`, cropped to its letters, in the logo's own warm white. */
private val Vector = ImageVector.Builder(defaultWidth = 213.dp, defaultHeight = 48.dp, viewportWidth = 658f, viewportHeight = 148f).apply {
    addGroup(translationX = -20f, translationY = -62f)
    addPath(addPathNodes(Letters), stroke = SolidColor(Color(0xFFFFF6E8)), strokeLineWidth = 20f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    addPath(addPathNodes(Light), fill = SolidColor(HotaruColors.Glow))
    clearGroup()
}.build()

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    Image(Vector, "Hotaru", modifier.height(48.dp))
}
