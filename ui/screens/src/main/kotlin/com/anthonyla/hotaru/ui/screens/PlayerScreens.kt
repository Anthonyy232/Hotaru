package com.anthonyla.hotaru.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import com.anthonyla.hotaru.presentation.CinemaOptions
import com.anthonyla.hotaru.presentation.player.PlayerIntent
import com.anthonyla.hotaru.presentation.player.PlayerOptions
import com.anthonyla.hotaru.presentation.player.PlayerUiState
import com.anthonyla.hotaru.presentation.player.PlayerViewModel
import com.anthonyla.hotaru.presentation.player.TrackOption
import com.anthonyla.hotaru.ui.kit.BackButton
import com.anthonyla.hotaru.ui.kit.ChoiceRow
import com.anthonyla.hotaru.ui.kit.glass
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.ImageRegion
import com.anthonyla.hotaru.ui.kit.PrimaryTarget
import com.anthonyla.hotaru.ui.kit.SeekBar
import com.anthonyla.hotaru.ui.kit.Spinner
import com.anthonyla.hotaru.ui.kit.Tag
import com.anthonyla.hotaru.ui.kit.ToggleIconButton
import com.meta.spatial.uiset.button.PrimaryButton
import com.meta.spatial.uiset.button.PrimaryCircleButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.button.SecondaryCircleButton
import com.meta.spatial.uiset.slider.SpatialSliderMedium
import com.meta.spatial.uiset.theme.icons.regular.CheckAlt
import com.meta.spatial.uiset.theme.icons.regular.ClosedCaptioning
import com.meta.spatial.uiset.theme.icons.regular.Environment
import com.meta.spatial.uiset.theme.icons.regular.Media2d
import com.meta.spatial.uiset.theme.icons.regular.MediaImmersiveVideo
import com.meta.spatial.uiset.theme.icons.regular.Pause
import com.meta.spatial.uiset.theme.icons.regular.Play
import com.meta.spatial.uiset.theme.icons.regular.PlayNext
import com.meta.spatial.uiset.theme.icons.regular.Refresh
import com.meta.spatial.uiset.theme.icons.regular.Reorient
import com.meta.spatial.uiset.theme.icons.regular.TenSecondsBackward
import com.meta.spatial.uiset.theme.icons.regular.TenSecondsForward
import com.meta.spatial.uiset.theme.icons.SpatialIcons
import com.meta.spatial.uiset.theme.SpatialTheme
import kotlin.time.Duration.Companion.seconds

/**
 * The in-window player: [video] is the host's video surface, letterboxed to fill the window, under a floating control bar
 * along the bottom. The view model shows and hides it (a tap or pinch on the picture, auto-hide), never hover. The skip
 * button stays up while the controls are hidden. Subtitles are part of the picture, so they are raised clear of the bar.
 */
@Composable
internal fun WindowPlayer(vm: PlayerViewModel, video: @Composable (Modifier) -> Unit) {
    val state by vm.state.collectAsState()
    var windowHeight by remember { mutableIntStateOf(0) }
    var pictureHeight by remember { mutableIntStateOf(0) }
    var barHeight by remember { mutableIntStateOf(0) }
    val barInset = HotaruDimens.xl
    val barInsetPx = with(LocalDensity.current) { barInset.roundToPx() }
    val covered = if (state.controlsVisible && pictureHeight > 0) (barHeight + barInsetPx - (windowHeight - pictureHeight) / 2f) / pictureHeight else 0f
    val lift = covered.coerceIn(0f, MaxSubtitleLift)
    LaunchedEffect(lift) { vm.onIntent(PlayerIntent.LiftSubtitles(lift)) }
    DisposableEffect(Unit) { onDispose { vm.onIntent(PlayerIntent.LiftSubtitles(0f)) } }
    Box(Modifier.fillMaxSize().background(Color.Black).onSizeChanged { windowHeight = it.height }, contentAlignment = Alignment.Center) {
        video(Modifier.aspectRatio(state.stage.screen.aspectRatio).onSizeChanged { pictureHeight = it.height })
        ToggleControlsArea(vm)
        if (state.isBuffering) Spinner(size = 56.dp)
        Column(Modifier.align(Alignment.BottomCenter).padding(barInset).widthIn(max = BarMaxWidth).keepTaps(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
            state.skipSegmentLabel?.let { label -> SkipButton(label, vm) }
            AnimatedVisibility(
                state.controlsVisible,
                enter = fadeIn() + slideInVertically { it / 4 },
                exit = fadeOut() + slideOutVertically { it / 4 },
            ) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
                    if (state.showTrackPicker) TrackPicker(state, vm)
                    Box(Modifier.onSizeChanged { barHeight = it.height }) { ControlBar(state, vm) }
                }
            }
        }
    }
}

/** A bar taller than this covers the subtitles rather than push them up the picture. */
private const val MaxSubtitleLift = 0.4f

/** The immersive controls panel: narrower than [RoomyBarWidth], so its bar shows icons only. */
val CinemaControlsSize = DpSize(640.dp, 680.dp)

/**
 * The immersive controls panel, which the scene keeps just under the screen: the bar on top so it sits against the screen's
 * bottom edge, the skip button and the popovers below it. A pinch on the screen shows and hides it. The empty space, kept
 * for the popovers, lies between a hand's ray and the lower half of the screen, so a pinch there does the same.
 */
@Composable
fun CinemaControls(vm: PlayerViewModel, onRecenter: () -> Unit) {
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize()) {
        ToggleControlsArea(vm)
        Column(Modifier.fillMaxSize().padding(HotaruDimens.md), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
            AnimatedVisibility(
                state.controlsVisible,
                Modifier.keepTaps(),
                enter = fadeIn() + slideInVertically { -it / 4 },
                exit = fadeOut() + slideOutVertically { -it / 4 },
            ) {
                ControlBar(state, vm)
            }
            state.skipSegmentLabel?.let { label -> Box(Modifier.keepTaps()) { SkipButton(label, vm) } }
            AnimatedVisibility(state.controlsVisible, Modifier.weight(1f, fill = false).keepTaps(), enter = fadeIn(), exit = fadeOut()) {
                Column(horizontalAlignment = Alignment.End) {
                    if (state.showTrackPicker) TrackPicker(state, vm)
                    if (state.showCinemaSettings) CinemaSettings(state, vm, onRecenter)
                }
            }
        }
    }
}

@Composable
fun CinemaStatus(vm: PlayerViewModel) {
    val state by vm.state.collectAsState()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ToggleControlsArea(vm)
        if (state.isBuffering) Box(Modifier.glass(HotaruShapes.pill).padding(HotaruDimens.lg)) { Spinner(size = 56.dp) }
    }
}

@Composable
private fun ToggleControlsArea(vm: PlayerViewModel) {
    Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClickLabel = "Show or hide controls") { vm.onIntent(PlayerIntent.ToggleControls) })
}

/** Taps on a bar or popover, between its buttons too, stay there instead of reaching the [ToggleControlsArea] under it. */
private fun Modifier.keepTaps(): Modifier = pointerInput(Unit) {}

@Composable
private fun SkipButton(label: String, vm: PlayerViewModel) {
    PrimaryTarget { PrimaryButton(label, { vm.onIntent(PlayerIntent.SkipSegment) }, leading = { Icon(SpatialIcons.Regular.PlayNext, null) }) }
}

@Composable
private fun TitleRow(state: PlayerUiState, vm: PlayerViewModel) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap),
    ) {
        BackButton({ vm.onIntent(PlayerIntent.Back) }, label = "Close player")
        Column(Modifier.weight(1f)) {
            Text(state.title, style = SpatialTheme.typography.headline3Strong, maxLines = 1, overflow = TextOverflow.Ellipsis)
            state.subtitle?.let { Text(it, style = SpatialTheme.typography.body2, color = HotaruColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        state.error?.let { Text(it, style = SpatialTheme.typography.body2, color = SpatialTheme.colorScheme.negative, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)) }
        if (state.isTranscoding) Tag("Transcoding")
    }
}

/** Capped, so a wide window does not spread the controls apart. */
private val BarMaxWidth = 960.dp

/** Wide enough for the labelled hand-off button; narrower bars keep icons only. */
private val RoomyBarWidth = 860.dp

/** Low enough to stay inside the cinema's controls panel, where the bar is at the top. */
private val ScrubPreviewHeight = 64.dp

/** visionOS's player ornament: one pane of glass with the title over the scrubber and the transport under it. */
@Composable
private fun ControlBar(state: PlayerUiState, vm: PlayerViewModel) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val roomy = maxWidth >= RoomyBarWidth
        Column(
            Modifier
                .fillMaxWidth()
                .glass()
                .padding(horizontal = HotaruDimens.xl, vertical = HotaruDimens.lg),
            verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs),
        ) {
            TitleRow(state, vm)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
                Text(state.positionText, style = SpatialTheme.typography.body1Strong, modifier = Modifier.widthIn(min = 64.dp))
                SeekBar(
                    progress = state.progress, label = "Playback position", buffered = state.bufferedFraction,
                    chapterFractions = state.chapterFractions,
                    bubble = state.scrubLabel,
                    preview = state.scrubThumbnail?.let { t ->
                        { ImageRegion(t.sheet.value, t.headers, t.x, t.y, t.width, t.height, Modifier.height(ScrubPreviewHeight).aspectRatio(t.width.toFloat() / t.height)) }
                    },
                    onScrub = { vm.onIntent(PlayerIntent.Scrub(it)) },
                    onSeek = { vm.onIntent(PlayerIntent.SeekTo(it)) },
                    modifier = Modifier.weight(1f),
                )
                Text(state.remainingText, style = SpatialTheme.typography.body1Strong, color = HotaruColors.TextSecondary, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 64.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                val (playIcon, playLabel) = when {
                    state.canRetry -> SpatialIcons.Regular.Refresh to "Retry"
                    state.canPause -> SpatialIcons.Regular.Pause to "Pause"
                    else -> SpatialIcons.Regular.Play to "Play"
                }
                PrimaryTarget { PrimaryCircleButton({ Icon(playIcon, playLabel) }, { vm.onIntent(PlayerIntent.TogglePlayPause) }) }
                IconAction(SpatialIcons.Regular.TenSecondsBackward, "Back 10 seconds") { vm.onIntent(PlayerIntent.SeekBy((-10).seconds)) }
                IconAction(SpatialIcons.Regular.TenSecondsForward, "Forward 10 seconds") { vm.onIntent(PlayerIntent.SeekBy(10.seconds)) }
                Spacer(Modifier.weight(1f))
                ToggleIconButton(SpatialIcons.Regular.ClosedCaptioning, "Audio, subtitles and speed", checked = state.showTrackPicker) { vm.onIntent(PlayerIntent.ToggleTrackPicker) }
                if (state.canAdjustCinema) ToggleIconButton(SpatialIcons.Regular.Environment, "Cinema settings", checked = state.showCinemaSettings) { vm.onIntent(PlayerIntent.ToggleCinemaSettings) }
                if (state.canWatchInImmersive) {
                    if (roomy) SecondaryButton("Watch in immersive", { vm.onIntent(PlayerIntent.WatchInImmersive) }, leading = { Icon(SpatialIcons.Regular.MediaImmersiveVideo, null) })
                    else IconAction(SpatialIcons.Regular.MediaImmersiveVideo, "Watch in immersive") { vm.onIntent(PlayerIntent.WatchInImmersive) }
                }
                // The cinema bar is never roomy.
                if (state.canWatchInWindow) IconAction(SpatialIcons.Regular.Media2d, "Watch in window") { vm.onIntent(PlayerIntent.ExitImmersive) }
            }
        }
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    SecondaryCircleButton({ Icon(icon, label) }, onClick)
}

@Composable
private fun ColumnScope.TrackPicker(state: PlayerUiState, vm: PlayerViewModel) {
    Column(Modifier.weight(1f, fill = false).widthIn(max = 760.dp).glass().padding(PopoverPadding)) {
        Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md)) {
            TrackColumn("Audio", state.audioOptions, Modifier.weight(1f), vm::onIntent)
            TrackColumn("Subtitles", state.subtitleOptions, Modifier.weight(1f), vm::onIntent)
        }
        ChoiceRow("Speed", PlayerOptions.speed, state.speed) { vm.onIntent(PlayerIntent.SetSpeed(it)) }
        if (state.canDelaySubtitles) {
            Row(Modifier.padding(horizontal = HotaruDimens.lg, vertical = HotaruDimens.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                Text("Subtitle delay", style = SpatialTheme.typography.body1, modifier = Modifier.weight(1f))
                SecondaryButton("Earlier", { vm.onIntent(PlayerIntent.ShiftSubtitles(-PlayerOptions.subtitleDelayStep)) })
                Text(state.subtitleDelayText, style = SpatialTheme.typography.body1Strong, color = HotaruColors.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(88.dp))
                SecondaryButton("Later", { vm.onIntent(PlayerIntent.ShiftSubtitles(PlayerOptions.subtitleDelayStep)) })
            }
        }
    }
}

@Composable
private fun TrackColumn(title: String, options: List<TrackOption>, modifier: Modifier, onIntent: (PlayerIntent) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Text(title, style = SpatialTheme.typography.body1Strong, color = HotaruColors.TextSecondary, modifier = Modifier.padding(horizontal = HotaruDimens.md))
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).selectableGroup(), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs)) {
            options.forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = HotaruDimens.hitTarget)
                        .clip(HotaruShapes.group)
                        .selectable(t.selected, role = Role.RadioButton) { onIntent(t.intent) }
                        .padding(horizontal = HotaruDimens.md),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.md),
                ) {
                    Icon(SpatialIcons.Regular.CheckAlt, null, tint = if (t.selected) SpatialTheme.colorScheme.active else Color.Transparent, modifier = Modifier.size(HotaruDimens.icon))
                    Text(t.label, style = SpatialTheme.typography.body1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A popover's rows are a group, so its corners are theirs plus this. */
private val PopoverPadding = PaddingValues(HotaruDimens.md)

@Composable
private fun CinemaSettings(state: PlayerUiState, vm: PlayerViewModel, onRecenter: () -> Unit) {
    Column(Modifier.glass().padding(PopoverPadding).widthIn(min = 520.dp), verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Row(Modifier.padding(start = HotaruDimens.lg, top = HotaruDimens.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("Cinema", style = SpatialTheme.typography.headline3Strong, modifier = Modifier.weight(1f))
            SecondaryButton("Recenter", onRecenter, leading = { Icon(SpatialIcons.Regular.Reorient, null) })
        }
        if (state.canAdjustScreen) {
            Row(Modifier.padding(horizontal = HotaruDimens.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HotaruDimens.lg)) {
                Text("Screen size", style = SpatialTheme.typography.body1, modifier = Modifier.width(140.dp))
                SpatialSliderMedium(onChanged = { vm.onIntent(PlayerIntent.ResizeScreen(it)) }, modifier = Modifier.weight(1f), value = state.screenSizeFraction)
                Text(state.screenWidthText, color = HotaruColors.TextSecondary, style = SpatialTheme.typography.body1Strong, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
            }
        }
        ChoiceRow("Surroundings", CinemaOptions.environment, state.stage.environment) { vm.onIntent(PlayerIntent.SetEnvironment(it)) }
    }
}
