package com.anthonyla.hotaru.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.presentation.CinemaOptions
import com.anthonyla.hotaru.presentation.settings.SettingsIntent
import com.anthonyla.hotaru.presentation.settings.SettingsOptions
import com.anthonyla.hotaru.presentation.settings.SettingsViewModel
import com.anthonyla.hotaru.ui.kit.ChoiceRow
import com.anthonyla.hotaru.ui.kit.FloatingBarScreen
import com.anthonyla.hotaru.ui.kit.HotaruColors
import com.anthonyla.hotaru.ui.kit.HotaruDimens
import com.anthonyla.hotaru.ui.kit.HotaruShapes
import com.anthonyla.hotaru.ui.kit.ScreenHeader
import com.anthonyla.hotaru.ui.kit.Separator
import com.anthonyla.hotaru.ui.kit.SettingRow
import com.meta.spatial.uiset.button.DestructiveButton
import com.meta.spatial.uiset.button.SecondaryButton
import com.meta.spatial.uiset.theme.SpatialTheme

@Composable
internal fun SettingsScreen(vm: SettingsViewModel) {
    val state by vm.state.collectAsState()
    val s = state.settings
    fun set(transform: (Settings) -> Settings) = vm.onIntent(SettingsIntent.Update(transform))

    FloatingBarScreen(bar = { ScreenHeader("Settings", onBack = null) }) { padding ->
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.padding(horizontal = HotaruDimens.gutter, vertical = HotaruDimens.sm).widthIn(max = HotaruDimens.readingWidth), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xl)) {
                SettingsGroup("Account") {
                    Column(Modifier.padding(start = HotaruDimens.lg, end = HotaruDimens.lg, top = HotaruDimens.lg), verticalArrangement = Arrangement.spacedBy(HotaruDimens.xs)) {
                        Text(state.accountLine, style = SpatialTheme.typography.body1Strong)
                        Text(state.serverAddress, color = HotaruColors.TextSecondary, style = SpatialTheme.typography.body2)
                    }
                    FlowRow(Modifier.padding(HotaruDimens.lg), horizontalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap), verticalArrangement = Arrangement.spacedBy(HotaruDimens.targetGap)) {
                        SecondaryButton("Switch user", { vm.onIntent(SettingsIntent.SignOut(forget = false)) })
                        DestructiveButton("Sign out and forget", { vm.onIntent(SettingsIntent.SignOut(forget = true)) })
                    }
                }

                SettingsGroup("Playback") {
                    ChoiceRow("Maximum quality", SettingsOptions.maxStreamingBitrate, s.maxStreamingBitrate) { v -> set { it.copy(maxStreamingBitrate = v) } }
                    Separator()
                    ChoiceRow("Audio", SettingsOptions.audioMode, s.audioMode) { v -> set { it.copy(audioMode = v) } }
                    Separator()
                    ChoiceRow("Subtitle size", SettingsOptions.subtitleSize, s.subtitleSize) { v -> set { it.copy(subtitleSize = v) } }
                    Separator()
                    SettingRow("Force SDR output", s.forceSdr) { v -> set { it.copy(forceSdr = v) } }
                    Separator()
                    SettingRow("Autoplay next episode", s.autoplayNext) { v -> set { it.copy(autoplayNext = v) } }
                }

                SettingsGroup("Skip segments") {
                    ChoiceRow("Intros", SettingsOptions.segmentAction, s.introAction) { v -> set { it.copy(introAction = v) } }
                    Separator()
                    ChoiceRow("Credits", SettingsOptions.segmentAction, s.outroAction) { v -> set { it.copy(outroAction = v) } }
                    Separator()
                    ChoiceRow("Recaps and previews", SettingsOptions.segmentAction, s.recapAction) { v -> set { it.copy(recapAction = v) } }
                }

                SettingsGroup("Cinema") {
                    ChoiceRow("Surroundings", CinemaOptions.environment, s.cinemaEnvironment) { v -> set { it.copy(cinemaEnvironment = v) } }
                }

                Text("Hotaru ${state.appVersion}", color = HotaruColors.TextSecondary, style = SpatialTheme.typography.body2, modifier = Modifier.padding(start = HotaruDimens.lg, bottom = HotaruDimens.section))
            }
        }
    }
}

/** A visionOS grouped list: the title over a lighter fill that holds the rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Text(title, style = SpatialTheme.typography.body1Strong, color = HotaruColors.TextSecondary, modifier = Modifier.padding(horizontal = HotaruDimens.lg))
        Column(Modifier.fillMaxWidth().clip(HotaruShapes.group).background(HotaruColors.Fill), content = content)
    }
}
