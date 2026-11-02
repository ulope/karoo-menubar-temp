package io.github.ulope.karoostatusbartemp

import kotlin.math.max
import kotlin.math.roundToInt

/** Where (top-left corner, in screen px) and how large to draw the temperature text. */
data class Placement(val x: Int, val y: Int, val textSizePx: Float)

object OverlayPlacement {

    /**
     * Ratio between our text size and the height of the reference element (the battery
     * percentage). Text views are a little taller than their font size because of ascent and
     * descent, so this yields roughly the same digit height as the native text.
     */
    const val AUTO_SIZE_FACTOR = 0.78f

    /** If the gap is too narrow, the text may shrink down to this fraction of its normal size. */
    const val MIN_SHRINK = 0.6f

    /**
     * @param row the detected status row
     * @param widthPerPx rendered text width divided by text size (text width at 1px)
     * @param heightPerPx rendered line height divided by text size
     * @param sizePercent user size adjustment, 100 = match native text
     * @param gapPx minimum free space to keep next to the ride time and battery text
     * @param offsetXPx user horizontal adjustment (positive = right)
     * @param offsetYPx user vertical adjustment (positive = down)
     */
    fun place(
        row: StatusRow,
        widthPerPx: Float,
        heightPerPx: Float,
        sizePercent: Int,
        gapPx: Int,
        offsetXPx: Int,
        offsetYPx: Int,
    ): Placement {
        val baseSize = row.reference.height * AUTO_SIZE_FACTOR * sizePercent / 100f
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

        val y = row.reference.centerY - size * heightPerPx / 2f
        return Placement(
            x = x.roundToInt() + offsetXPx,
            y = y.roundToInt() + offsetYPx,
            textSizePx = size,
        )
    }
}
