package io.github.ulope.karoostatusbartemp

/** A screen-space rectangle in pixels (like android.graphics.Rect, but usable in plain JVM tests). */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerY: Float get() = (top + bottom) / 2f
}

/**
 * The ride screen's top status row as seen through accessibility.
 *
 * @param leftEdge right edge of the left cluster, i.e. the end of the ride time text.
 *   Null when nothing is shown on the left (for example before the ride timer appears).
 * @param rightEdge left edge of the right cluster, i.e. the start of the battery level text.
 * @param reference bounds of the right cluster's first element. Its height and vertical center
 *   are used to size and vertically align the temperature so it matches the native text.
 */
data class StatusRow(
    val leftEdge: Int?,
    val rightEdge: Int,
    val reference: Box,
)

/**
 * Picks the two anchors of the status row out of the labelled, visible leaf nodes of the ride app
 * window.
 *
 * The ride app's top row is laid out as `[ride time] ... gap ... [battery %][battery][clock]`. Every
 * element hugs the top edge of the screen and is short, unlike the data fields further down.
 */
object StatusRowDetector {

    data class Anchors(val leftIndex: Int?, val rightIndex: Int)

    /**
     * @param leaves bounds of labelled, visible leaf nodes of a single window
     * @param screenWidth display width in px
     * @param maxTopPx a node belongs to the top row only if its top edge is within this distance of
     *   the screen top
     * @param maxHeightPx ... and if it is not taller than this
     * @return indices into [leaves], or null if there is no status row (no right cluster)
     */
    fun findAnchors(leaves: List<Box>, screenWidth: Int, maxTopPx: Int, maxHeightPx: Int): Anchors? {
        // The right cluster (battery, clock) is separated from the ride time by a wide gap, so
        // anything starting past the first third of the screen belongs to it.
        val clusterSplit = screenWidth / 3

        val row = leaves.withIndex().filter { (_, b) ->
            b.top in 0..maxTopPx && b.height in 1..maxHeightPx && b.width > 0 && b.left < screenWidth
        }

        val right = row.filter { it.value.left >= clusterSplit }.minByOrNull { it.value.left } ?: return null
        val left = row
            .filter { it.value.left < clusterSplit && it.value.right <= right.value.left }
            .maxByOrNull { it.value.right }

        return Anchors(leftIndex = left?.index, rightIndex = right.index)
    }
}
