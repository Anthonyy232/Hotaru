package com.anthonyla.hotaru.platform.quest

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.os.Bundle
import android.util.Log
import android.view.View
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.DpSize
import com.anthonyla.hotaru.application.ports.VideoRenderTarget
import com.anthonyla.hotaru.application.stage.Stage
import com.anthonyla.hotaru.domain.Environment
import com.anthonyla.hotaru.domain.PlaybackPlan
import com.anthonyla.hotaru.domain.Projection
import com.anthonyla.hotaru.domain.ScreenGeometry
import com.anthonyla.hotaru.domain.StageMode
import com.anthonyla.hotaru.domain.StageState
import com.anthonyla.hotaru.domain.StereoLayout
import com.meta.spatial.compose.ComposeFeature
import com.meta.spatial.compose.ComposeViewPanelRegistration
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Lut
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.SystemBase
import com.meta.spatial.core.Vector2
import com.meta.spatial.core.Vector3
import com.meta.spatial.core.Vector4
import com.meta.spatial.isdk.IsdkGrabMovementType
import com.meta.spatial.isdk.IsdkGrabState
import com.meta.spatial.isdk.IsdkGrabbable
import com.meta.spatial.isdk.IsdkPanelGrabHandle
import com.meta.spatial.isdk.IsdkPanelResize
import com.meta.spatial.isdk.ResizeCornerState
import com.meta.spatial.isdk.ResizeMode
import com.meta.spatial.runtime.HitInfo
import com.meta.spatial.runtime.InputListener
import com.meta.spatial.runtime.PanelSceneObject
import com.meta.spatial.runtime.SceneObject
import com.meta.spatial.runtime.StereoMode
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.DpPerMeterDisplayOptions
import com.meta.spatial.toolkit.Equirect180ShapeOptions
import com.meta.spatial.toolkit.Equirect360ShapeOptions
import com.meta.spatial.toolkit.MediaPanelRenderOptions
import com.meta.spatial.toolkit.MediaPanelSettings
import com.meta.spatial.toolkit.Panel
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PanelStyleOptions
import com.meta.spatial.toolkit.PixelDisplayOptions
import com.meta.spatial.toolkit.QuadShapeOptions
import com.meta.spatial.toolkit.Scale
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.UIPanelSettings
import com.meta.spatial.toolkit.VideoSurfacePanelRegistration
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

abstract class QuestSceneHost : AppSystemActivity() {

    protected abstract val stage: Stage
    /** The size [ControlsPanel] is laid out for. */
    protected abstract val controlsPanelSize: DpSize
    @Composable protected abstract fun ControlsPanel(recenter: () -> Unit)
    @Composable protected abstract fun StatusPanel()

    /** While the controls are hidden the screen is locked, so a stray pinch (hand tracking in low light) cannot move it. */
    protected abstract val controlsVisible: Flow<Boolean>

    /** While cinema settings are open the panels hold still, so the size slider stays under the hand as the screen grows. */
    protected abstract val cinemaSettingsOpen: Flow<Boolean>

    /** Controller B / system Back. */
    protected abstract fun back()

    /** A pinch or trigger click on the screen. */
    protected abstract fun screenTapped()

    /** The screen was grabbed to move or resize it, or let go. */
    protected abstract fun screenHeld(held: Boolean)

    protected abstract fun screenResized(widthMeters: Float)

    protected abstract fun headsetWorn(worn: Boolean)

    protected abstract fun returnToWindow()

    /** The viewer quit the cinema from the system. Finishing after [returnToWindow] is not a quit. */
    protected abstract fun quit()

    private val controlsWidthMeters get() = controlsPanelSize.width.value / DP_PER_METER
    private val controlsHeightMeters get() = controlsPanelSize.height.value / DP_PER_METER
    private var controlsView: View? = null
    private val devBridge = DevBridge(this, "controls", { controlsView }, mapOf("recenter" to ::recenter, "back" to ::onBack))

    private val scope = MainScope()
    private var anchor = Pose(Vector3(0f, EYE_HEIGHT, 0f), Quaternion())
    private lateinit var controlsPanel: Entity
    private lateinit var statusPanel: Entity
    private var controlsObject: PanelSceneObject? = null
    private var statusObject: PanelSceneObject? = null
    private var cinema: Cinema? = null
    private var environment: Environment? = null
    private var passthroughLevel = 0f
    private var environmentFade: Animator? = null
    private var presence = 0f
    private var presenceFade: Animator? = null
    private var screenFade = 0f
    private var screenFadeIn: Animator? = null
    private var leaving = false
    private var controlsShown = false
    private var settingsOpen = false
    /** Set by a placement, so the panels move with it even while held still. */
    private var replacePanels = false
    private var placing = true
    /** Where the last flat screen was left, for the next one until Recenter. */
    private var flatPose: Pose? = null

    // A cinema has nowhere to go: thumbsticks and thumb swipes would only carry the viewer away from the screen.
    override fun registerFeatures(): List<SpatialFeature> = listOf(VRFeature(this, handMicrogestureLocomotion = false), ComposeFeature())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // VrActivity is a plain Activity with no OnBackPressedDispatcher; the platform dispatcher replaces onBackPressed.
        onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { onBack() }
        devBridge.register()
    }

    /** While the cinema fades out the stage is already back in the window, where Back would stop playback. */
    private fun onBack() {
        if (!leaving) back()
    }

    override fun onDestroy() {
        devBridge.unregister()
        // A hand-back still fading out completes now; otherwise the viewer quit.
        presenceFade?.let { if (leaving && it.isStarted) it.end() else it.cancel() }
        if (!leaving) quit()
        environmentFade?.cancel()
        screenFadeIn?.cancel()
        cinema?.surface?.let(stage::surfaceDestroyed)
        super.onDestroy()
    }

    override fun onHMDMounted() {
        super.onHMDMounted()
        headsetWorn(true)
    }

    override fun onHMDUnmounted() {
        super.onHMDUnmounted()
        headsetWorn(false)
    }

    override fun onSceneReady() {
        super.onSceneReady()
        systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
        scene.setLightingEnvironment(ambientColor = Vector3(0.22f), sunColor = Vector3(0.0f), sunDirection = -Vector3(1f, 3f, 2f))
        controlsPanel = Entity.create(Panel(R.id.hotaru_controls_panel), Transform(), Scale(Vector3(1f)), Visible(true))
        statusPanel = Entity.create(Panel(R.id.hotaru_status_panel), Transform(), Scale(Vector3(1f)), Visible(true))
        systemManager.registerSystem(PlacementSystem())
        systemManager.registerSystem(FollowSystem())
        fadePresence(1f)
        scope.launch { controlsVisible.collect { controlsShown = it } }
        scope.launch { cinemaSettingsOpen.collect { settingsOpen = it } }
        scope.launch {
            // A finishing activity must not rebuild the cinema if playback goes immersive again before it is destroyed.
            stage.state.takeWhile { it.mode == StageMode.Immersive }.collect(::render)
            leaving = true
            fadePresence(0f) { destroyCinema(); returnToWindow() }
        }
    }

    /**
     * Puts the cinema ahead of the viewer on entering and on Recenter, and at no other time: the scene stays in the SDK's
     * default STAGE space, which is fixed to the room, so a system recenter (tracking lost in the dark, the headset back on)
     * moves nothing. The viewer pose is identity until tracking delivers a frame, so placing waits for one.
     */
    private inner class PlacementSystem : SystemBase() {
        override fun execute() {
            if (!placing) return
            val viewer = scene.getViewerPose()
            if (viewer.isApproximatelyEqual(Pose(), 1e-4f, 1e-4f)) return
            anchor = viewer.removePitchAndRoll()
            cinema?.let { it.entity.setComponent(Transform(cinemaPose(it.plan, it.geometry))) }
            placing = false
            replacePanels = true
            Log.i(TAG, "anchored at ${anchor.t}")
        }
    }

    private fun recenter() {
        placing = true
        flatPose = null
    }

    override fun registerPanels(): List<PanelRegistration> = listOf(
        ComposeViewPanelRegistration(
            R.id.hotaru_controls_panel,
            composeViewCreator = { _, ctx -> ComposeView(ctx).apply { setContent { ControlsPanel(::recenter) }; controlsView = this } },
            settingsCreator = { uiPanelSettings(controlsWidthMeters, controlsHeightMeters) },
            panelSetupWithComposeView = { _, panel, _ -> controlsObject = panel; applyAlpha() },
        ),
        ComposeViewPanelRegistration(
            R.id.hotaru_status_panel,
            composeViewCreator = { _, ctx -> ComposeView(ctx).apply { setContent { StatusPanel() } } },
            settingsCreator = { uiPanelSettings(STATUS_SIZE_M, STATUS_SIZE_M) },
            panelSetupWithComposeView = { _, panel, _ -> statusObject = panel; applyAlpha() },
        ),
    )

    private fun uiPanelSettings(width: Float, height: Float) = UIPanelSettings(
        shape = QuadShapeOptions(width = width, height = height),
        display = DpPerMeterDisplayOptions(dpPerMeter = DP_PER_METER),
        style = PanelStyleOptions(themeResourceId = R.style.HotaruPanelTransparent),
    )

    private fun render(state: StageState) {
        if (state.environment != environment) showEnvironment(state.environment)
        val plan = checkNotNull(state.plan)
        val screen = cinema
        when {
            screen == null || plan != screen.plan -> showCinema(plan, state.screen)
            screen.grabbable && state.screen != screen.geometry -> resizeCinema(screen, state.screen)
        }
    }

    private fun showCinema(plan: PlaybackPlan, geometry: ScreenGeometry) {
        val grabbable = plan.projection == Projection.Rectilinear
        destroyCinema()
        // The panel callbacks fire on a later frame, once the entity below exists.
        lateinit var screen: Cinema
        val id = R.id.hotaru_cinema_panel
        val stereo = when (plan.stereo) { StereoLayout.SideBySide -> StereoMode.LeftRight; StereoLayout.TopBottom -> StereoMode.UpDown; StereoLayout.Mono -> StereoMode.None }
        val video = plan.source.videoStream
        val px = PixelDisplayOptions(width = (video?.width ?: 1920).coerceIn(640, 4096), height = (video?.height ?: 1080).coerceIn(360, 4096))
        registerPanel(
            VideoSurfacePanelRegistration(
                id,
                surfaceConsumer = { _, surface -> VideoRenderTarget(surface, px.width, px.height).also { screen.surface = it; stage.surfaceChanged(it) } },
                settingsCreator = {
                    MediaPanelSettings(
                        shape = when (plan.projection) {
                            Projection.Equirect180 -> Equirect180ShapeOptions(radius = 50f)
                            Projection.Equirect360 -> Equirect360ShapeOptions(radius = 300f)
                            Projection.Rectilinear -> QuadShapeOptions(width = geometry.widthMeters, height = geometry.heightMeters)
                        },
                        display = px,
                        rendering = MediaPanelRenderOptions(stereoMode = stereo, zIndex = if (plan.projection == Projection.Rectilinear) 0 else -1),
                    )
                },
                panelSetup = { panel, _ ->
                    screen.panel = panel
                    panel.addInputListener(object : InputListener {
                        override fun onClick(receiver: SceneObject, hitInfo: HitInfo, sourceOfInput: Entity) = screenTapped()
                    })
                    screenFadeIn?.cancel()
                    screenFadeIn = fade(0f, 1f, { screenFade = it; applyAlpha() })
                },
            ),
        )
        val pose = flatPose?.takeIf { grabbable } ?: cinemaPose(plan, geometry)
        screen = Cinema(plan, geometry, grabbable, Entity.create(Panel(id), Transform(pose), Scale(Vector3(1f)), Visible(true)))
        if (grabbable) {
            screen.handleScale = pose.t.distanceTo(scene.getViewerPose().t)
            screen.entity.setComponents(
                listOf(
                    IsdkGrabbable(enabled = controlsShown, movementType = IsdkGrabMovementType.AxialBillboard),
                    IsdkPanelResize(
                        enabled = controlsShown,
                        resizeMode = ResizeMode.Simple,
                        minDimensions = Vector2(ScreenGeometry.MIN_WIDTH_METERS, ScreenGeometry.MIN_WIDTH_METERS / geometry.aspectRatio),
                        maxDimensions = Vector2(ScreenGeometry.MAX_WIDTH_METERS, ScreenGeometry.MAX_WIDTH_METERS / geometry.aspectRatio),
                    ),
                    grabHandles(screen.handleScale),
                ),
            )
        }
        cinema = screen
        Log.i(TAG, "cinema: ${geometry.widthMeters}m @ ${geometry.distanceMeters}m stereo=$stereo proj=${plan.projection}")
    }

    /** The screen scales where it is. */
    private fun resizeCinema(screen: Cinema, geometry: ScreenGeometry) {
        screen.geometry = geometry
        screen.entity.setComponent(Scale(Vector3(geometry.widthMeters / screen.baseWidth)))
    }

    private fun destroyCinema() {
        val screen = cinema ?: return
        screen.surface?.let(stage::surfaceDestroyed)
        if (screen.held) screenHeld(false)
        if (screen.grabbable) flatPose = screen.entity.getComponent<Transform>().transform
        screen.entity.destroy()
        cinema = null
    }

    private class Cinema(val plan: PlaybackPlan, var geometry: ScreenGeometry, val grabbable: Boolean, val entity: Entity) {
        /** The width the mesh was registered at; the screen resizes by scaling from it. */
        val baseWidth = geometry.widthMeters
        var panel: PanelSceneObject? = null
        var surface: VideoRenderTarget? = null
        var handleScale = 1f
        var resizing = false
        var held = false
    }

    /** ISDK sizes its handles for a window about a meter away; a screen [scale] meters away gets handles that much bigger, so they stay as easy to hit. */
    private fun grabHandles(scale: Float) = IsdkPanelGrabHandle().apply {
        grabHandleCollisionWidths = grabHandleCollisionWidths * scale
        resizeCornerCollisionSizes = resizeCornerCollisionSizes * scale
        resizeCornerCollisionInset *= scale
        scaleFactor = Vector2(scale)
    }

    private inner class FollowSystem : SystemBase() {
        override fun execute() {
            val screen = cinema ?: return
            val geometry = screen.geometry
            val pose = screen.entity.getComponent<Transform>().transform
            val scale = screen.entity.getComponent<Scale>().scale.x
            val (face, halfHeight) =
                if (screen.grabbable) pose to screen.baseWidth * scale / geometry.aspectRatio / 2
                else ahead(VIRTUAL_SCREEN_DISTANCE_M, 0f) to VIRTUAL_SCREEN_HALF_HEIGHT_M
            val distance = face.t.distanceTo(scene.getViewerPose().t)
            if (!settingsOpen || replacePanels) {
                // The panels are laid out for UI_DISTANCE_M; scaled by the screen's distance they look that size on a screen anywhere.
                val size = distance / UI_DISTANCE_M
                place(controlsPanel, face * Pose(Vector3(0f, -halfHeight - (CONTROLS_GAP_M + controlsHeightMeters / 2) * size, 0f)), size)
                place(statusPanel, face * Pose(Vector3(0f, 0f, -STATUS_LIFT_M * size)), size)
            }
            replacePanels = false

            if (!screen.grabbable) return
            val grab = screen.entity.getComponent<IsdkGrabbable>()
            val resize = screen.entity.getComponent<IsdkPanelResize>()
            val grabbed = grab.grabState != IsdkGrabState.NotGrabbed
            val resizing = resize.activeResizeCorner != ResizeCornerState.NONE
            // A grab or resize under way finishes even if the controls hide meanwhile.
            val unlocked = controlsShown || grabbed || resizing
            if (grab.enabled != unlocked) {
                grab.enabled = unlocked
                resize.enabled = unlocked
                screen.entity.setComponents(listOf(grab, resize))
            }
            if (screen.resizing && !resizing) screenResized(screen.baseWidth * scale)
            screen.resizing = resizing
            val held = grabbed || resizing
            if (screen.held != held) {
                screen.held = held
                screenHeld(held)
            }
            if (!resizing && !grabbed && abs(distance / screen.handleScale - 1f) > 0.1f) {
                screen.handleScale = distance
                screen.entity.setComponent(grabHandles(distance))
            }
        }

        private fun place(panel: Entity, target: Pose, size: Float) {
            if (!panel.getComponent<Transform>().transform.isApproximatelyEqual(target, 0.002f, 0.2f)) panel.setComponent(Transform(target))
            if (abs(panel.getComponent<Scale>().scale.x / size - 1f) > 0.01f) panel.setComponent(Scale(Vector3(size)))
        }
    }

    /** Passthrough fades through a black lookup table, so Dark is reached at level 0 and only then switched off. */
    private fun showEnvironment(target: Environment) {
        environment = target
        environmentFade?.cancel()
        scene.enablePassthrough(true)
        val level = when (target) { Environment.Passthrough -> 1f; Environment.Dimmed -> DIMMED_LEVEL; Environment.Dark -> 0f }
        environmentFade = fade(passthroughLevel, level, ::tintPassthrough) {
            when (target) {
                Environment.Passthrough -> scene.setPassthroughLUT(null)
                Environment.Dimmed -> Unit
                Environment.Dark -> scene.enablePassthrough(false)
            }
        }
    }

    /** Scales each of the LUT's 16 steps per channel (0..15 onto 0..255) by [level]. */
    private fun tintPassthrough(level: Float) {
        passthroughLevel = level
        fun shade(step: Int) = (step * 17 * level).roundToInt()
        scene.setPassthroughLUT(Lut().apply { for (r in 0..15) for (g in 0..15) for (b in 0..15) setMapping(r, g, b, shade(r), shade(g), shade(b)) })
    }

    private fun fadePresence(to: Float, then: () -> Unit = {}) {
        presenceFade?.cancel()
        presenceFade = fade(presence, to, { presence = it; applyAlpha() }, then)
    }

    /**
     * Android's own animator: Spatial SDK has no tween facility, and it steps on the main thread with the scene.
     * [then] runs only when the fade completes; cancel() also calls onAnimationEnd.
     */
    private fun fade(from: Float, to: Float, step: (Float) -> Unit, then: () -> Unit = {}): Animator =
        ValueAnimator.ofFloat(from, to).apply {
            setDuration(FADE_MS)
            addUpdateListener { step(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false
                override fun onAnimationCancel(animation: Animator) { cancelled = true }
                override fun onAnimationEnd(animation: Animator) { if (!cancelled) then() }
            })
            start()
        }

    private fun applyAlpha() {
        controlsObject?.let { layerAlpha(it, presence) }
        statusObject?.let { layerAlpha(it, presence) }
        cinema?.panel?.let { layerAlpha(it, presence * screenFade) }
    }

    /** Scales color with alpha, which fades a premultiplied layer evenly. */
    private fun layerAlpha(panel: PanelSceneObject, alpha: Float) = checkNotNull(panel.layer).setColorScaleBias(Vector4(alpha), Vector4(0f))

    /** A pose [forward] meters along the viewer's gaze (+Z), [drop] meters below eye level. A panel shows its -Z face, so the anchor's own yaw faces the viewer. */
    private fun ahead(forward: Float, drop: Float): Pose = anchor * Pose(Vector3(0f, -drop, forward))

    /** The screen sits at the viewing distance; 180/360 video is centred on the viewer. */
    private fun cinemaPose(plan: PlaybackPlan, g: ScreenGeometry): Pose =
        if (plan.projection == Projection.Rectilinear) ahead(g.distanceMeters, drop = -g.heightMeters * 0.05f) else ahead(0f, 0f)

    override fun onSpatialShutdown() { scope.cancel(); super.onSpatialShutdown() }

    private companion object {
        const val TAG = "Hotaru/QuestScene"        // same prefix as AndroidLogger, so one logcat filter covers the app
        const val EYE_HEIGHT = 1.45f             // until the first tracked frame
        const val UI_DISTANCE_M = 1.1f           // the distance the panels' sizes are meant for
        const val DP_PER_METER = 833f            // 48 dp spans 3° at 1.1 m
        const val STATUS_SIZE_M = 120 / DP_PER_METER
        const val CONTROLS_GAP_M = 0.04f         // 2° between the screen's bottom edge and the controls
        const val STATUS_LIFT_M = 0.05f          // in front of the screen, clear of its surface
        // 180/360 video has no screen to attach to: the panels sit as on one 2 m ahead whose bottom is 10° down.
        const val VIRTUAL_SCREEN_DISTANCE_M = 2f
        const val VIRTUAL_SCREEN_HALF_HEIGHT_M = 0.35f
        const val DIMMED_LEVEL = 0.3f
        const val FADE_MS = 250L
    }
}
