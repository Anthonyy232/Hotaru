package com.anthonyla.hotaru.quest

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.runtime.Composable
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.platform.quest.QuestSceneHost
import com.anthonyla.hotaru.presentation.player.PlayerIntent
import com.anthonyla.hotaru.ui.kit.HotaruTheme
import com.anthonyla.hotaru.ui.screens.CinemaControls
import com.anthonyla.hotaru.ui.screens.CinemaControlsSize
import com.anthonyla.hotaru.ui.screens.CinemaStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ImmersiveActivity : QuestSceneHost() {
    private val graph get() = (application as HotaruApp).graph
    override val stage: Stage get() = graph.stage

    override fun back() = graph.shell.back()
    override fun screenTapped() = graph.player.onIntent(PlayerIntent.ToggleControls)
    override fun screenHeld(held: Boolean) = graph.player.onIntent(PlayerIntent.ScreenHeld(held))
    override fun screenResized(widthMeters: Float) = graph.player.onIntent(PlayerIntent.ScreenResized(widthMeters))
    override fun headsetWorn(worn: Boolean) = graph.player.onIntent(PlayerIntent.HeadsetWorn(worn))

    override val controlsPanelSize = CinemaControlsSize
    override val controlsVisible get() = graph.player.state.map { it.controlsVisible }.distinctUntilChanged()
    override val cinemaSettingsOpen get() = graph.player.state.map { it.showCinemaSettings }.distinctUntilChanged()
    @Composable override fun ControlsPanel(recenter: () -> Unit) = HotaruTheme { CinemaControls(graph.player, recenter) }
    @Composable override fun StatusPanel() = HotaruTheme { CinemaStatus(graph.player) }

    /** Horizon OS hybrid-app hand-off: Home reopens the panel window from this pending intent. */
    override fun returnToWindow() {
        val panel = Intent(this, PanelActivity::class.java).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("extra_launch_in_home_pending_intent", PendingIntent.getActivity(this, 0, panel, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)),
        )
        finish()
    }

    override fun quit() = graph.shell.immersiveClosed()
}
