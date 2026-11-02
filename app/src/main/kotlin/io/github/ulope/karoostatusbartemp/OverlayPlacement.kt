package io.github.ulope.karoostatusbartemp

import kotlin.math.max
import kotlin.math.roundToInt

/** Where (top-left corner, in screen px) and how large to draw the temperature text. */
data class Placement(val x: Int, val y: Int, val textSizePx: Float)

/**
 * Horizontal metrics of our text, per px of text size.
 *
 * @param inkLeft where the first glyph's ink starts, measured from the text origin
 * @param inkRight where the last glyph's ink ends, measured from the text origin
 */
data class TextMetrics(val inkLeft: Float, val inkRight: Float, val ascent: Float) {
    val inkWidth: Float get() = inkRight - inkLeft
}

object OverlayPlacement {

    /**
     * Height of the battery level's text bounds divided by the font size that gives our digits the
     * same height. Measured on a Karoo 3: 46px bounds, round digits ("0", "5", "8") ~29px tall,
     * like ours at 41.1px. (Digits with thin diagonals, "4" and "7", measure short in photos.)
     */
    const val REFERENCE_HEIGHT_PER_FONT_SIZE = 1.12f

    /**
     * Where the battery level's baseline sits within its text bounds, as a fraction of their height
     * from the top. Measured on a Karoo 3.
     */
    const val BASELINE_RATIO = 0.865f

    /**
     * If the gap is too narrow, the text may shrink down to this fraction of its normal size.
     * Shrinking further makes it hard to read on the bike, so it stays fairly high.
     */
    const val MIN_SHRINK = 0.85f

    /** Font size of the native battery level text, in px. */
    fun nativeFontSize(row: StatusRow): Float = row.reference.height / REFERENCE_HEIGHT_PER_FONT_SIZE

    /**
     * @param row the detected status row; its edges are where the neighbours' ink ends and starts
     * @param text our text's metrics
     * @param sizePercent user size adjustment, 100 = same size as the battery level
     * @param gapPx minimum free space between our ink and the neighbours' ink
     * @param offsetXPx user horizontal adjustment (positive = right)
     * @param offsetYPx user vertical adjustment (positive = down)
     */
    fun place(
        row: StatusRow,
        text: TextMetrics,
        sizePercent: Int,
        gapPx: Int,
        offsetXPx: Int,
        offsetYPx: Int,
    ): Placement {
        val baseSize = nativeFontSize(row) * sizePercent / 100f
        var size = baseSize

        val x: Float
        if (row.leftEdge != null) {
            // The ink is centered between the end of the ride time and the start of the battery
            // level. If a long ride time or an icon leaves only a narrow gap, it shrinks a little.
            val available = (row.rightEdge - row.leftEdge - 2 * gapPx).toFloat()
            if (size * text.inkWidth > available) {
                size = max(available / text.inkWidth, baseSize * MIN_SHRINK)
            }
            val center = (row.leftEdge + row.rightEdge) / 2f
            x = center - size * (text.inkLeft + text.inkRight) / 2f
        } else {
            // No ride time visible: sit just left of the battery level.
            x = row.rightEdge - gapPx - size * text.inkRight
        }

        // Share the battery level's baseline.
        val baseline = row.reference.top + row.reference.height * BASELINE_RATIO
        val y = baseline - size * text.ascent
        return Placement(
            x = x.roundToInt() + offsetXPx,
            y = y.roundToInt() + offsetYPx,
            textSizePx = size,
        )
    }
}
