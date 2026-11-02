package io.github.ulope.karoostatusbartemp

import kotlin.math.max
import kotlin.math.roundToInt

/** Where (top-left corner, in screen px) and how large to draw the temperature text. */
data class Placement(val x: Int, val y: Int, val textSizePx: Float)

object OverlayPlacement {

    /**
     * Height of the battery level's text bounds divided by its font size. Measured on a Karoo 3:
     * the bounds are the text's line box, a little taller than the font size.
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
     * @param row the detected status row
     * @param widthPerPx rendered text width divided by text size (text width at 1px)
     * @param ascentPerPx distance from the top of our text view to its baseline, per px of text size
     * @param sizePercent user size adjustment, 100 = same size as the battery level
     * @param gapPx minimum free space to keep next to the ride time and battery text
     * @param offsetXPx user horizontal adjustment (positive = right)
     * @param offsetYPx user vertical adjustment (positive = down)
     */
    fun place(
        row: StatusRow,
        widthPerPx: Float,
        ascentPerPx: Float,
        sizePercent: Int,
        gapPx: Int,
        offsetXPx: Int,
        offsetYPx: Int,
    ): Placement {
        val baseSize = nativeFontSize(row) * sizePercent / 100f
        var size = baseSize

        val x: Float
        if (row.leftEdge != null) {
            // Centered between the end of the ride time and the start of the battery level,
            // shrinking a little if a long ride time leaves only a narrow gap.
            val available = (row.rightEdge - row.leftEdge - 2 * gapPx).toFloat()
            if (size * widthPerPx > available) {
                size = max(available / widthPerPx, baseSize * MIN_SHRINK)
            }
            x = (row.leftEdge + row.rightEdge) / 2f - size * widthPerPx / 2f
        } else {
            // No ride time visible: sit just left of the battery level.
            x = row.rightEdge - gapPx - size * widthPerPx
        }

        // Share the battery level's baseline.
        val baseline = row.reference.top + row.reference.height * BASELINE_RATIO
        val y = baseline - size * ascentPerPx
        return Placement(
            x = x.roundToInt() + offsetXPx,
            y = y.roundToInt() + offsetYPx,
            textSizePx = size,
        )
    }
}
