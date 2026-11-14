package com.anthonyla.hotaru.domain

/** Browse and Window are the panel window (browsing, or playing in it); Immersive is the Spatial SDK cinema. */
enum class StageMode { Browse, Window, Immersive }

data class ScreenGeometry(
    val widthMeters: Float,
    val aspectRatio: Float,
) {
    val heightMeters: Float get() = widthMeters / aspectRatio

    /**
     * How far ahead of the viewer the screen is put on entering and on recenter. It grows slower than width, so a bigger
     * screen also fills more of the view (~22° at 1 m, 49° at 4 m, 66° at 12 m).
     */
    val distanceMeters: Float get() = 2f + 0.6f * widthMeters

    companion object {
        const val MIN_WIDTH_METERS = 1.0f
        const val MAX_WIDTH_METERS = 12.0f
    }
}

enum class Environment { Passthrough, Dimmed, Dark }

data class StageState(
    val mode: StageMode,
    /** What is playing; null while browsing. */
    val plan: PlaybackPlan?,
    val screen: ScreenGeometry,
    val environment: Environment,
)
