package com.anthonyla.hotaru.platform.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.text.SpannableString
import android.view.View.MeasureSpec
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.ui.SubtitleView
import com.anthonyla.hotaru.domain.Projection
import com.anthonyla.hotaru.domain.StereoLayout
import io.github.peerless2012.ass.media.AssHandler
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

/**
 * It is a [TextOverlay] because Media3 composites those as SDR content, over HDR video too, where it takes any other bitmap
 * overlay for an Ultra HDR image; it draws its own bitmap, so [getText] is never asked for. A styled (ASS) track is drawn
 * by libass for every frame, so that it animates; any other track arrives as [cues].
 */
@OptIn(UnstableApi::class)
internal class SubtitleOverlay(
    context: Context,
    private val stereo: StereoLayout,
    private val projection: Projection,
    private val perEyeAspect: Float,
    private val textScale: Float,
    private val ass: AssHandler,
    /** How much later than its own time a styled track shows; cues arrive already delayed. */
    private val delayUs: AtomicLong,
) : TextOverlay() {
    // Never attached to a window: it is laid out and drawn on the video frame thread only.
    private val view = SubtitleView(context).apply { setUserDefaultStyle(); setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * textScale) }
    @Volatile var cues: List<Cue> = emptyList()
    /** The fraction of the picture's height the subtitles are raised by. A styled track moves whole, its signs too. */
    @Volatile var lift = 0f
    private var drawnLift = 0f
    private var assFrames: AssFrames? = null
    private val assPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    /** What the bitmap shows: the cue list, or libass's images. */
    private var drawn: Any? = null
    private lateinit var bitmap: Bitmap
    private lateinit var regions: List<Rect>
    private lateinit var settings: OverlaySettings

    override fun configure(videoSize: Size) {
        // Drawn at most this wide and stretched over the frame: a 4K or 8K bitmap would re-upload tens of MB with every cue.
        val scale = minOf(1f, MAX_WIDTH / videoSize.width)
        bitmap = Bitmap.createBitmap((videoSize.width * scale).roundToInt(), (videoSize.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
        settings = StaticOverlaySettings.Builder().setScale(1 / scale, 1 / scale).build()
        regions = cueRegions(bitmap.width, bitmap.height)
        val r = regions.first()
        // Half-width 3D and equirect frames are stretched on display, so the view is laid out in the shape the viewer sees
        // and squeezed into the frame.
        val shown = if (projection == Projection.Rectilinear) perEyeAspect else CUE_DEGREES_ACROSS / (CUE_DEGREES_UP + CUE_DEGREES_DOWN)
        val width = maxOf(r.width(), (r.height() * shown).roundToInt())
        val height = maxOf(r.height(), (r.width() / shown).roundToInt())
        view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        view.layout(0, 0, width, height)
        releaseAss()
        drawn = null
    }

    override fun getOverlaySettings(presentationTimeUs: Long) = settings

    override fun getText(presentationTimeUs: Long) = SpannableString("")

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val track = ass.track
        val lift = lift
        if (lift != drawnLift) {
            view.setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION + lift)
            drawnLift = lift
            drawn = null
        }
        if (track == null) {
            val current = cues
            if (current !== drawn) {
                view.setCues(current)
                paint(view::draw)
                drawn = current
            }
        } else {
            val frames = assFrames ?: AssFrames(ass, view.width, view.height, textScale).also { assFrames = it }
            // Media3 counts an effect's frame times from where playback started, so the position is the one the
            // library's renderer reports.
            val images = frames.at(track, ass.videoTime - delayUs.get())
            if (images !== drawn) {
                paint { canvas ->
                    canvas.translate(0f, -lift * view.height)
                    for (image in images) {
                        // libass packs a colour as RGBA with the alpha inverted.
                        assPaint.color = Color.argb(255 - (image.color and 0xFF), image.color ushr 24, image.color shr 16 and 0xFF, image.color shr 8 and 0xFF)
                        canvas.drawBitmap(image.bitmap!!, image.x.toFloat(), image.y.toFloat(), assPaint)
                    }
                }
                drawn = images
            }
        }
        return bitmap
    }

    override fun release() {
        releaseAss()
        super.release()
    }

    private fun releaseAss() {
        assFrames?.release()
        assFrames = null
    }

    /** Clears the bitmap and has [draw] fill each eye's region, on a canvas the size of the view. */
    private inline fun paint(draw: (Canvas) -> Unit) {
        bitmap.eraseColor(Color.TRANSPARENT)
        val canvas = Canvas(bitmap)
        for (r in regions) {
            canvas.save()
            canvas.translate(r.left.toFloat(), r.top.toFloat())
            canvas.scale(r.width().toFloat() / view.width, r.height().toFloat() / view.height)
            draw(canvas)
            canvas.restore()
        }
    }

    /**
     * One region per eye's image. On 180/360 video the image's bottom is at the viewer's feet, so the cues go in the view
     * straight ahead instead: 90° wide, from 30° above the horizon to 25° below it.
     */
    private fun cueRegions(width: Int, height: Int): List<Rect> {
        val eyes = when (stereo) {
            StereoLayout.Mono -> listOf(Rect(0, 0, width, height))
            StereoLayout.SideBySide -> listOf(Rect(0, 0, width / 2, height), Rect(width / 2, 0, width, height))
            StereoLayout.TopBottom -> listOf(Rect(0, 0, width, height / 2), Rect(0, height / 2, width, height))
        }
        val degreesAcross = when (projection) {
            Projection.Rectilinear -> return eyes
            Projection.Equirect180 -> 180f
            Projection.Equirect360 -> 360f
        }
        return eyes.map { eye ->
            val halfWidth = (eye.width() * CUE_DEGREES_ACROSS / 2 / degreesAcross).roundToInt()
            val perDegree = eye.height() / 180f
            Rect(eye.centerX() - halfWidth, (eye.centerY() - CUE_DEGREES_UP * perDegree).roundToInt(), eye.centerX() + halfWidth, (eye.centerY() + CUE_DEGREES_DOWN * perDegree).roundToInt())
        }
    }

    private companion object {
        const val MAX_WIDTH = 1920f
        const val CUE_DEGREES_ACROSS = 90f
        const val CUE_DEGREES_UP = 30f
        const val CUE_DEGREES_DOWN = 25f
    }
}
