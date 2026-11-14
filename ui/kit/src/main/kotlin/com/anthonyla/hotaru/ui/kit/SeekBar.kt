package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.meta.spatial.uiset.theme.SpatialTheme
import kotlin.math.roundToInt

/** The track stops this short of each end, so the thumb stays inside the strip. */
private val TrackInset = 12.dp
private val ThumbRadius = 9.dp

private fun Density.fractionAt(x: Float, width: Int): Float = ((x - TrackInset.toPx()) / (width - 2 * TrackInset.toPx())).coerceIn(0f, 1f)

/**
 * visionOS's scrubber: a bare track at rest, which thickens and shows its thumb under the pointer, with focus or while
 * scrubbing. Left/Right keys step 2% for D-pad and gamepad.
 * A drag scrubs while it moves and seeks to where it was released. The whole 60 dp strip takes input; [bubble] (the
 * scrub time) sits on the thumb, under [preview] of the picture there.
 */
@Composable
fun SeekBar(
    progress: Float,
    label: String,
    onScrub: (Float?) -> Unit,          // null when the gesture ends
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    buffered: Float,
    chapterFractions: List<Float>,
    bubble: String?,
    preview: (@Composable () -> Unit)?,
) {
    // The gesture detectors outlive recompositions; read the latest callbacks, not the first ones.
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val emphasized = focused || hovered || bubble != null
    val colors = SpatialTheme.colorScheme
    Box(modifier.fillMaxWidth().height(HotaruDimens.hitTargetPrimary)) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                    setProgress { onSeek(it.coerceIn(0f, 1f)); true }
                }
                .hoverable(interaction)
                .focusable(interactionSource = interaction)
                .onKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (e.key) {
                        Key.DirectionLeft -> { onSeek((progress - 0.02f).coerceIn(0f, 1f)); true }
                        Key.DirectionRight -> { onSeek((progress + 0.02f).coerceIn(0f, 1f)); true }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures { pos -> currentOnSeek(fractionAt(pos.x, size.width)) }
                }
                .pointerInput(Unit) {
                    var fraction = 0f
                    fun scrubTo(x: Float) {
                        fraction = fractionAt(x, size.width)
                        currentOnScrub(fraction)
                    }
                    detectDragGestures(
                        onDragStart = { pos -> scrubTo(pos.x) },
                        onDragEnd = { currentOnSeek(fraction); currentOnScrub(null) },
                        onDragCancel = { currentOnScrub(null) },
                    ) { change, _ -> scrubTo(change.position.x) }
                },
        ) {
            val start = TrackInset.toPx()
            val width = size.width - 2 * start
            val trackH = if (emphasized) 10.dp.toPx() else 6.dp.toPx()
            val y = size.height / 2 - trackH / 2
            val radius = CornerRadius(trackH / 2)
            drawRoundRect(colors.progressBarOnMedia, Offset(start, y), Size(width, trackH), radius)
            drawRoundRect(colors.progressBarOnMedia, Offset(start, y), Size(width * buffered, trackH), radius)
            drawRoundRect(colors.progressOnMedia, Offset(start, y), Size(width * progress, trackH), radius)
            chapterFractions.forEach { f ->
                drawRect(HotaruColors.Scrim, Offset(start + width * f - 1.dp.toPx(), y), Size(2.dp.toPx(), trackH))
            }
            if (emphasized) drawCircle(colors.progressOnMedia, ThumbRadius.toPx(), Offset(start + width * progress, size.height / 2))
            if (focused) {
                val ring = 2.dp.toPx()
                drawRoundRect(colors.active, Offset(ring / 2, ring / 2), Size(size.width - ring, size.height - ring), CornerRadius(size.height / 2), style = Stroke(ring))
            }
        }
        if (bubble != null) {
            Column(
                Modifier
                    .layout { measurable, constraints ->
                        val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            val inset = TrackInset.toPx()
                            val x = (inset + (constraints.maxWidth - 2 * inset) * progress - p.width / 2f).roundToInt().coerceIn(0, constraints.maxWidth - p.width)
                            p.place(x, constraints.maxHeight / 2 - ThumbRadius.roundToPx() - p.height)
                        }
                    }
                    .clip(if (preview == null) HotaruShapes.pill else HotaruShapes.row)
                    .background(HotaruColors.Scrim),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                preview?.invoke()
                Text(bubble, style = SpatialTheme.typography.body2Strong, maxLines = 1, modifier = Modifier.padding(horizontal = HotaruDimens.md, vertical = HotaruDimens.xs))
            }
        }
    }
}
