package com.anthonyla.hotaru.application.stage

import com.anthonyla.hotaru.application.ports.MediaPlayer
import com.anthonyla.hotaru.application.ports.Settings
import com.anthonyla.hotaru.application.ports.SettingsStore
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.ScreenGeometry
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.StageState
import com.anthonyla.hotaru.domain.StereoRules
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Where playback is shown and where its frames go. The hosts (panel window, immersive scene) follow
 * [state] and hand over their video surface while it exists; the shown player always renders to the
 * latest one, so moving between window and immersive keeps the same player and position. The cinema
 * screen and surroundings follow [Settings], so an adjustment applies now and to the next playback. Main thread only.
 */
@Singleton
class Stage @Inject constructor(private val settings: SettingsStore, scope: CoroutineScope) {
    private val _state = MutableStateFlow(
        settings.settings.value.let { StageState(StageMode.Browse, plan = null, screenFor(it, StereoRules.DEFAULT_ASPECT), it.cinemaEnvironment) },
    )
    val state: StateFlow<StageState> = _state.asStateFlow()

    private var player: MediaPlayer? = null
    private var surface: VideoRenderTarget? = null

    init {
        scope.launch {
            settings.settings.collect { s -> _state.update { it.copy(screen = screenFor(s, it.screen.aspectRatio), environment = s.cinemaEnvironment) } }
        }
    }

    /** Content that needs immersive goes immersive; anything else stays in the current playback mode, the window when coming from browse. */
    fun show(plan: PlaybackPlan, player: MediaPlayer) {
        val immersive = plan.needsImmersive || _state.value.mode == StageMode.Immersive
        this.player = player
        player.renderTo(surface)
        _state.update {
            it.copy(mode = if (immersive) StageMode.Immersive else StageMode.Window, plan = plan, screen = screenFor(settings.settings.value, plan.perEyeAspect))
        }
    }

    /** Before the shown player is released, so no surface reaches it afterwards. */
    fun detachPlayer() {
        player = null
    }

    fun close() {
        _state.update { it.copy(mode = StageMode.Browse, plan = null) }
    }

    fun setImmersive(immersive: Boolean) {
        _state.update { it.copy(mode = if (immersive) StageMode.Immersive else StageMode.Window) }
    }

    fun surfaceChanged(target: VideoRenderTarget) {
        surface = target
        player?.renderTo(target)
    }

    /** Called before the host releases [target]. A newer surface from the other host stays current. */
    fun surfaceDestroyed(target: VideoRenderTarget) {
        if (surface != target) return
        surface = null
        player?.renderTo(null)
    }

    private fun screenFor(s: Settings, aspectRatio: Float) = ScreenGeometry(s.cinemaScreenWidthMeters, aspectRatio)
}
