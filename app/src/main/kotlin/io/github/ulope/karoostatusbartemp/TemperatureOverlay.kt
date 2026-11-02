package io.github.ulope.karoostatusbartemp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.ceil

/**
 * Paint shared by the overlay and the measurements used to place it.
 *
 * Karoo's ride screen uses Roboto Condensed, but the system font aliases (such as
 * "sans-serif-condensed") don't resolve to it on every Karoo, so the app bundles a copy trimmed
 * to the characters it draws. Anything else falls back to the system font.
 */
fun statusTextPaint(context: Context): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    typeface = statusTypeface(context)
}

fun statusTypeface(context: Context): Typeface = context.resources.getFont(R.font.roboto_condensed_status)

/** Draws a single line of text, sized exactly to the text. */
@SuppressLint("ViewConstructor")
class StatusTextView(context: Context) : View(context) {

    private val paint = statusTextPaint(context)
    private var text = ""

    init {
        // Keep our own overlay out of the accessibility tree so it is never mistaken for part
        // of the ride app's status row.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }

    /** @return true if the view's size changed and the window needs a relayout. */
    fun update(text: String, textSizePx: Float, color: Int): Boolean {
        val sizeChanged = text != this.text || textSizePx != paint.textSize
        if (!sizeChanged && color == paint.color) return false
        this.text = text
        paint.textSize = textSizePx
        paint.color = color
        if (sizeChanged) requestLayout()
        invalidate()
        return sizeChanged
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val metrics = paint.fontMetrics
        setMeasuredDimension(
            ceil(paint.measureText(text)).toInt(),
            ceil(metrics.descent - metrics.ascent).toInt(),
        )
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawText(text, 0f, -paint.fontMetrics.ascent, paint)
    }
}

/**
 * Owns the overlay window. Accessibility services may add windows of type
 * TYPE_ACCESSIBILITY_OVERLAY without the "display over other apps" permission; these windows
 * sit above everything else, including status bars, and pass touches through.
 */
class TemperatureOverlay(context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = StatusTextView(context)
    private var attached = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        title = "StatusBarTemp"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Position relative to the physical screen, not the area inside the system bars.
            fitInsetsTypes = 0
        }
    }

    fun show(text: String, placement: Placement, color: Int) {
        val resized = view.update(text, placement.textSizePx, color)
        val moved = placement.x != params.x || placement.y != params.y
        params.x = placement.x
        params.y = placement.y
        try {
            if (!attached) {
                windowManager.addView(view, params)
                attached = true
            } else if (resized || moved) {
                windowManager.updateViewLayout(view, params)
            }
        } catch (e: RuntimeException) {
            // WindowManager.BadTokenException and friends, e.g. while the service is shutting down.
            Log.w(TAG, "Could not show overlay", e)
            attached = false
        }
    }

    fun hide() {
        if (!attached) return
        attached = false
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { Log.w(TAG, "Could not remove overlay", it) }
    }

    private companion object {
        const val TAG = "StatusBarTemp"
    }
}
