package io.github.ulope.karoostatusbartemp

/** A screen-space rectangle in pixels (like android.graphics.Rect, but usable in plain JVM tests). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun intersects(other: Box): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    override fun toString() = "[$left,$top-$right,$bottom]"
}

/** A visible leaf node near the top of the screen. */
data class RowLeaf(
    val box: Box,
    /** The node's text, or its content description if it has no text. */
    val label: String?,
    /** Whether the node draws something (text or an image), as opposed to an empty view. */
    val isContent: Boolean,
)

/**
 * The ride screen's top status row as seen through accessibility.
 *
 * @param leftEdge where the ride time's ink ends. Null if no ride time is shown.
 * @param rightEdge where the battery level's ink starts.
 * @param reference bounds of the battery level text. The temperature is sized and aligned to it.
 */
data class StatusRow(
    val leftEdge: Int?,
    val rightEdge: Int,
    val reference: Box,
)

/**
 * Finds the ride time and battery level in the leaf nodes at the top of the ride app's window.
 *
 * The ride app's top row reads `[icons] 1:23:45 ... gap ... 87% [battery] 10:42`. The anchors are
 * picked by their text, so other things near the top of the screen (such as the bell icon of the
 * notification drawer) are never mistaken for the status row.
 */
object StatusRowDetector {

    private val BATTERY = Regex("""\d{1,3}\s?%""")
    private val RIDE_TIME = Regex("""\d{1,3}(:\d{2}){1,2}""")

    data class Anchors(val rideTimeIndex: Int?, val batteryIndex: Int)

    /**
     * @param leaves visible leaf nodes of a single window
     * @param screenWidth display width in px
     * @param maxTopPx a node belongs to the top row only if its top edge is within this distance of
     *   the screen top
     * @param maxHeightPx ... and if it is not taller than this
     * @return indices into [leaves], or null if there is no battery level, i.e. no status row
     */
    fun findAnchors(leaves: List<RowLeaf>, screenWidth: Int, maxTopPx: Int, maxHeightPx: Int): Anchors? {
        val row = leaves.withIndex().filter { (_, leaf) ->
            val b = leaf.box
            b.top in 0..maxTopPx && b.height in 1..maxHeightPx && b.width > 0 && b.left < screenWidth
        }

        val battery = row
            .filter { (_, leaf) -> leaf.box.left >= screenWidth / 3 && leaf.label?.let(BATTERY::containsMatchIn) == true }
            .minByOrNull { it.value.box.left }
            ?: return null
        // The clock also looks like a time, but it is right of the battery level.
        val rideTime = row
            .filter { (_, leaf) ->
                leaf.box.left < battery.value.box.left && leaf.label?.trim()?.let(RIDE_TIME::matches) == true
            }
            .maxByOrNull { it.value.box.right }

        return Anchors(rideTimeIndex = rideTime?.index, batteryIndex = battery.index)
    }

    /**
     * Returns the first leaf other than the anchors that draws something inside [area], or null if
     * the area is free. Something there means the status row is covered, e.g. by a drawer.
     */
    fun findObstruction(leaves: List<RowLeaf>, anchors: Anchors, area: Box): RowLeaf? =
        leaves.withIndex().firstOrNull { (i, leaf) ->
            i != anchors.rideTimeIndex && i != anchors.batteryIndex && leaf.isContent && leaf.box.intersects(area)
        }?.value
}
