package com.anthonyla.hotaru.platform.quest

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import java.io.File

/**
 * Debug-build adb bridge so a headset lying on a desk can be driven and screenshotted without a
 * person wearing it. Immersive panels are Presentation windows on virtual displays, which screencap cannot
 * capture and `input` cannot reach, so we copy and touch the views directly; the panel window gets the
 * same treatment so one script drives both activities: the window, or the cinema's controls panel. Coordinates are
 * dp in the panel. Driven by tools/quest-dev.ps1.
 */
class DevBridge(
    private val activity: Activity,
    private val panelName: String,
    /** Null until the panel's view exists. */
    private val panel: () -> View?,
    private val actions: Map<String, () -> Unit>,
) : BroadcastReceiver() {

    // Exported for `adb shell am broadcast`; only senders holding DUMP (the shell, not apps) get through.
    fun register() { if (activity.isDebuggable) activity.registerReceiver(this, IntentFilter(ACTION), Manifest.permission.DUMP, null, Context.RECEIVER_EXPORTED) }
    fun unregister() { if (activity.isDebuggable) activity.unregisterReceiver(this) }

    override fun onReceive(context: Context, intent: Intent) {
        val cmd = intent.getStringExtra("cmd")
        actions[cmd]?.let { return it() }
        val view = panel()
        if (view == null || !view.isAttachedToWindow) { Log.w(TAG, "$panelName is not shown yet"); return }
        when (cmd) {
            "capture" -> intent.getFloatExtra("scale", 1f).let { if (it == 1f) capture(view, goAsync()) else render(view, it, goAsync()) }
            "tap", "swipe" -> {
                val density = view.resources.displayMetrics.density
                fun px(key: String) = intent.getFloatExtra(key, 0f) * density
                val (x1, y1) = px("x") to px("y")
                if (cmd == "tap") gesture(view, listOf(x1 to y1))
                else {
                    val (x2, y2) = px("x2") to px("y2")
                    gesture(view, (0..STEPS).map { i -> x1 + (x2 - x1) * i / STEPS to y1 + (y2 - y1) * i / STEPS })
                }
            }
            else -> Log.w(TAG, "unknown cmd $cmd")
        }
    }

    /**
     * PixelCopy reads the panel window's last queued buffer, so hardware bitmaps (Coil posters) come
     * through. A panel frame redraws only its damaged region and that buffer can be blank elsewhere,
     * so invalidate the whole window and copy once that full frame is committed.
     */
    private fun capture(view: View, completion: PendingResult) {
        view.viewTreeObserver.registerFrameCommitCallback {
            PixelCopy.request(PixelCopy.Request.Builder.ofWindow(view).build(), activity.mainExecutor) { result ->
                try {
                    if (result.status != PixelCopy.SUCCESS) { Log.w(TAG, "capture $panelName failed: ${result.status}"); return@request }
                    save(result.bitmap)
                } finally {
                    completion.finish()
                }
            }
        }
        view.rootView.invalidate()
    }

    /**
     * Redraws the panel's display lists at [scale] for sharper store screenshots. Hardware rendering, so hardware
     * bitmaps still draw. A video SurfaceView is outside the view tree and only punches a hole in it, so its
     * current frame is copied and drawn under the panel, as the compositor layers it.
     */
    private fun render(view: View, scale: Float, completion: PendingResult) {
        val root = view.rootView
        val video = root.surfaceViews().firstOrNull { it.isShown && it.width > 0 }
        if (video == null) return render(root, scale, null, completion)
        val frame = Bitmap.createBitmap((video.width * scale).toInt(), (video.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        PixelCopy.request(video, frame, { status ->
            if (status != PixelCopy.SUCCESS) Log.w(TAG, "copy of $panelName video failed: $status")
            render(root, scale, frame.takeIf { status == PixelCopy.SUCCESS }?.let { video to it }, completion)
        }, video.handler)
    }

    private fun render(root: View, scale: Float, video: Pair<SurfaceView, Bitmap>?, completion: PendingResult) {
        val (w, h) = (root.width * scale).toInt() to (root.height * scale).toInt()
        val node = RenderNode(panelName).apply { setPosition(0, 0, w, h) }
        node.beginRecording(w, h).also { canvas ->
            canvas.save()
            canvas.scale(scale, scale)
            root.draw(canvas)
            canvas.restore()
            video?.let { (surface, frame) ->
                val at = IntArray(2).also(surface::getLocationInWindow)
                canvas.drawBitmap(frame, at[0] * scale, at[1] * scale, Paint().apply { blendMode = BlendMode.DST_OVER })
            }
        }
        node.endRecording()
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 1, HardwareBuffer.USAGE_GPU_COLOR_OUTPUT or HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE)
        val renderer = HardwareRenderer().apply { setSurface(reader.surface); setContentRoot(node) }
        renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
        reader.acquireNextImage().use { image ->
            save(Bitmap.wrapHardwareBuffer(image.hardwareBuffer!!, ColorSpace.get(ColorSpace.Named.SRGB))!!.copy(Bitmap.Config.ARGB_8888, false))
        }
        renderer.destroy()
        reader.close()
        node.discardDisplayList()
        completion.finish()
    }

    /** Where tools/quest-dev.ps1 pulls it from. */
    private fun save(bitmap: Bitmap) {
        val file = File(activity.filesDir, "dev/$panelName.png").apply { parentFile?.mkdirs() }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "captured $panelName ${bitmap.width}x${bitmap.height}")
    }

    private fun View.surfaceViews(): Sequence<SurfaceView> = when (this) {
        is SurfaceView -> sequenceOf(this)
        is ViewGroup -> (0 until childCount).asSequence().flatMap { getChildAt(it).surfaceViews() }
        else -> emptySequence()
    }

    /** Down at the first point, a move per further point, up at the last, 16 ms apart so Compose sees velocity. */
    private fun gesture(view: View, points: List<Pair<Float, Float>>) {
        val path = if (points.size == 1) points + points else points
        val down = SystemClock.uptimeMillis()
        path.forEachIndexed { i, (x, y) ->
            val action = when (i) { 0 -> MotionEvent.ACTION_DOWN; path.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
            view.postDelayed({
                val event = MotionEvent.obtain(down, down + i * 16L, action, x, y, 0).apply { source = InputDevice.SOURCE_TOUCHSCREEN }
                view.rootView.dispatchTouchEvent(event)
                event.recycle()
            }, i * 16L)
        }
    }

    private companion object {
        const val ACTION = "com.anthonyla.hotaru.DEV"
        const val TAG = "Hotaru/DevBridge"
        const val STEPS = 12
    }
}

val Context.isDebuggable: Boolean get() = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
