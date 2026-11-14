package com.anthonyla.hotaru.platform.android

import io.github.peerless2012.ass.AssTex
import io.github.peerless2012.ass.AssTexType
import io.github.peerless2012.ass.AssTrack
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.executor.AssExecutor

/**
 * libass rendering a styled subtitle track frame by frame, [width] by [height] pixels. It has its own renderer because
 * the handler sizes its one to the surface.
 */
internal class AssFrames(handler: AssHandler, width: Int, height: Int, fontScale: Float) {
    private val render = handler.ass.createRender().apply { setFontScale(fontScale); setFrameSize(width, height) }
    // Renders off the calling thread and keeps the last picture when libass is slow, rather than hold up the video.
    private val executor = AssExecutor(render)
    private val none = emptyArray<AssTex>()
    private var track: AssTrack? = null
    private var images = none

    /** The picture at [timeUs] as alpha masks to tint, the same array for as long as the picture stays the same. */
    fun at(track: AssTrack, timeUs: Long): Array<AssTex> {
        if (track !== this.track) { render.setTrack(track); this.track = track }
        val frame = executor.renderFrame(timeUs, AssTexType.BITMAP_ALPHA)
        // No frame is an empty picture, and an unchanged one carries no images.
        if (frame == null) images = none else if (frame.changed != 0) images = frame.images!!
        return images
    }

    fun release() {
        executor.shutdown()
        render.release()
    }
}
