package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementTest {

    // Battery level text bounds, 40px tall: native font size 40 / 1.12 ≈ 35.7px.
    private val reference = Box(left = 280, top = 10, right = 340, bottom = 50)
    private val nativeSize = 40 / OverlayPlacement.REFERENCE_HEIGHT_PER_FONT_SIZE
    private val nativeBaseline = 10 + 40 * OverlayPlacement.BASELINE_RATIO
    private val widthPerPx = 1.36f // "18°" in Roboto Condensed
    private val ascentPerPx = 0.928f

    private fun place(
        leftEdge: Int?,
        rightEdge: Int = 280,
        sizePercent: Int = 100,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) = OverlayPlacement.place(
        row = StatusRow(leftEdge, rightEdge, reference),
        widthPerPx = widthPerPx,
        ascentPerPx = ascentPerPx,
        sizePercent = sizePercent,
        gapPx = 4,
        offsetXPx = offsetX,
        offsetYPx = offsetY,
    )

    private fun baseline(p: Placement) = p.y + p.textSizePx * ascentPerPx

    @Test
    fun `100 percent matches the battery level's font size`() {
        assertEquals(nativeSize, place(leftEdge = 150).textSizePx, 0.01f)
        assertEquals(nativeSize * 1.5f, place(150, sizePercent = 150).textSizePx, 0.01f)
    }

    @Test
    fun `shares the battery level's baseline at any size`() {
        assertEquals(nativeBaseline, baseline(place(leftEdge = 150)), 1f)
        assertEquals(nativeBaseline, baseline(place(leftEdge = 150, sizePercent = 70)), 1f)
        assertEquals(nativeBaseline, baseline(place(leftEdge = 150, sizePercent = 130)), 1f)
    }

    @Test
    fun `centers the text in the gap`() {
        val p = place(leftEdge = 150)
        assertEquals(215f, p.x + p.textSizePx * widthPerPx / 2f, 1f)
    }

    @Test
    fun `sits left of the battery level when there is no ride time`() {
        val p = place(leftEdge = null)
        assertEquals(280f - 4f, p.x + p.textSizePx * widthPerPx, 1f)
    }

    @Test
    fun `shrinks to fit a narrow gap`() {
        // 54px gap minus 2 * 4px leaves 46px; at full size the text is ~48.6px wide.
        val p = place(leftEdge = 226)
        assertTrue(p.textSizePx < nativeSize)
        assertEquals(46f, p.textSizePx * widthPerPx, 0.01f)
        assertEquals(253f, p.x + p.textSizePx * widthPerPx / 2f, 1f)
        assertEquals(nativeBaseline, baseline(p), 1f)
    }

    @Test
    fun `never shrinks below the minimum`() {
        val minimum = nativeSize * OverlayPlacement.MIN_SHRINK
        assertEquals(minimum, place(leftEdge = 250).textSizePx, 0.01f)
        // Gap narrower than the padding.
        assertEquals(minimum, place(leftEdge = 277).textSizePx, 0.01f)
    }

    @Test
    fun `Karoo 3 ride screen from diagnostics`() {
        // Ride time "0:06:27" node [8,0-169,58], battery "70%" text [258,7-315,53], density 1.875.
        val row = StatusRow(leftEdge = 169, rightEdge = 258, reference = Box(258, 7, 315, 53))
        val p = OverlayPlacement.place(row, widthPerPx, ascentPerPx, 100, gapPx = 4, offsetXPx = 0, offsetYPx = 0)
        assertEquals(41.1f, p.textSizePx, 0.1f)
        val width = p.textSizePx * widthPerPx
        // Centered between the ride time and the battery level, ~16px free on both sides.
        assertEquals(213.5f, p.x + width / 2f, 1f)
        assertTrue(p.x - 169 > 14 && 258 - (p.x + width) > 14)
    }

    @Test
    fun `applies user offsets`() {
        val base = place(leftEdge = 150)
        val moved = place(leftEdge = 150, offsetX = 12, offsetY = -3)
        assertEquals(base.x + 12, moved.x)
        assertEquals(base.y - 3, moved.y)
    }
}
