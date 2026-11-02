package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementTest {

    // Battery level text bounds, 40px tall.
    private val reference = Box(left = 280, top = 10, right = 340, bottom = 50)
    private val nativeSize = 40 / OverlayPlacement.REFERENCE_HEIGHT_PER_FONT_SIZE
    private val nativeBaseline = 10 + 40 * OverlayPlacement.BASELINE_RATIO

    // "30°" in Roboto Condensed Regular: ink from 0.049 to 1.299 em, ascent 0.928 em.
    private val text = TextMetrics(inkLeft = 0.049f, inkRight = 1.299f, ascent = 0.928f)

    private fun place(
        leftEdge: Int?,
        rightEdge: Int = 280,
        sizePercent: Int = 100,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) = OverlayPlacement.place(
        row = StatusRow(leftEdge, rightEdge, reference),
        text = text,
        sizePercent = sizePercent,
        gapPx = 4,
        offsetXPx = offsetX,
        offsetYPx = offsetY,
    )

    private fun inkLeft(p: Placement) = p.x + p.textSizePx * text.inkLeft
    private fun inkRight(p: Placement) = p.x + p.textSizePx * text.inkRight
    private fun baseline(p: Placement) = p.y + p.textSizePx * text.ascent

    @Test
    fun `100 percent matches the battery level's digit size`() {
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
    fun `centers the ink in the gap`() {
        val p = place(leftEdge = 150)
        assertEquals(215f, (inkLeft(p) + inkRight(p)) / 2f, 1f)
    }

    @Test
    fun `sits left of the battery level when there is no ride time`() {
        val p = place(leftEdge = null)
        assertEquals(280f - 4f, inkRight(p), 1f)
    }

    @Test
    fun `shrinks to fit a narrow gap`() {
        // 44px gap minus 2 * 4px leaves 36px of ink; at full size the ink is ~40.7px wide.
        val p = place(leftEdge = 236)
        assertTrue(p.textSizePx < nativeSize)
        assertEquals(36f, inkRight(p) - inkLeft(p), 0.5f)
        assertEquals(258f, (inkLeft(p) + inkRight(p)) / 2f, 1f)
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
        // Battery "70%" text [258,7-315,53]: ink starts ~2px in. Digits ~26.5px tall.
        val battery = Box(258, 7, 315, 53)
        fun karoo(rideTimeInkEnd: Int) = OverlayPlacement.place(
            StatusRow(leftEdge = rideTimeInkEnd, rightEdge = 260, reference = battery),
            text, sizePercent = 100, gapPx = 4, offsetXPx = 0, offsetYPx = 0,
        )

        // Ride time "0:06:27" node [8,0-169,58], ink ends ~3px in.
        val wide = karoo(rideTimeInkEnd = 166)
        assertEquals(37.4f, wide.textSizePx, 0.1f)
        assertEquals(26.5f, wide.textSizePx * 0.711f, 0.3f)
        assertEquals(213f, (inkLeft(wide) + inkRight(wide)) / 2f, 1f)

        // With the radar icon the ride time moves to [46,0-207,58]: still full size, ~4.5px free.
        val narrow = karoo(rideTimeInkEnd = 204)
        assertEquals(37.4f, narrow.textSizePx, 0.1f)
        assertTrue(inkLeft(narrow) - 204 >= 4 && 260 - inkRight(narrow) >= 4)
    }

    @Test
    fun `applies user offsets`() {
        val base = place(leftEdge = 150)
        val moved = place(leftEdge = 150, offsetX = 12, offsetY = -3)
        assertEquals(base.x + 12, moved.x)
        assertEquals(base.y - 3, moved.y)
    }
}
