package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPlacementTest {

    private val reference = Box(left = 280, top = 10, right = 340, bottom = 50) // height 40
    private val widthPerPx = 1.5f
    private val heightPerPx = 1.2f

    private fun place(
        leftEdge: Int?,
        rightEdge: Int = 280,
        sizePercent: Int = 100,
        offsetX: Int = 0,
        offsetY: Int = 0,
    ) = OverlayPlacement.place(
        row = StatusRow(leftEdge, rightEdge, reference),
        widthPerPx = widthPerPx,
        heightPerPx = heightPerPx,
        sizePercent = sizePercent,
        gapPx = 8,
        offsetXPx = offsetX,
        offsetYPx = offsetY,
    )

    @Test
    fun `text size follows the reference height`() {
        assertEquals(40 * OverlayPlacement.AUTO_SIZE_FACTOR, place(leftEdge = 160).textSizePx, 0.01f)
        assertEquals(40 * OverlayPlacement.AUTO_SIZE_FACTOR * 1.5f, place(160, sizePercent = 150).textSizePx, 0.01f)
    }

    @Test
    fun `centers the text in the gap`() {
        val p = place(leftEdge = 160)
        val width = p.textSizePx * widthPerPx
        assertEquals(220f, p.x + width / 2f, 1f)
    }

    @Test
    fun `centers the text vertically on the reference`() {
        val p = place(leftEdge = 160)
        val height = p.textSizePx * heightPerPx
        assertEquals(30f, p.y + height / 2f, 1f)
    }

    @Test
    fun `sits left of the battery when there is no ride time`() {
        val p = place(leftEdge = null)
        val width = p.textSizePx * widthPerPx
        assertEquals(280f - 8f, p.x + width, 1f)
    }

    @Test
    fun `shrinks to fit a narrow gap`() {
        // 60px gap minus 2 * 8px padding leaves 44px; the normal width would be ~47px.
        val p = place(leftEdge = 220)
        assertTrue(p.textSizePx < 40 * OverlayPlacement.AUTO_SIZE_FACTOR)
        assertEquals(44f, p.textSizePx * widthPerPx, 0.01f)
        assertEquals(250f, p.x + p.textSizePx * widthPerPx / 2f, 1f)
    }

    @Test
    fun `never shrinks below the minimum`() {
        val minimum = 40 * OverlayPlacement.AUTO_SIZE_FACTOR * OverlayPlacement.MIN_SHRINK
        assertEquals(minimum, place(leftEdge = 240).textSizePx, 0.01f)
        // Gap narrower than the padding.
        assertEquals(minimum, place(leftEdge = 275).textSizePx, 0.01f)
    }

    @Test
    fun `applies user offsets`() {
        val base = place(leftEdge = 160)
        val moved = place(leftEdge = 160, offsetX = 12, offsetY = -3)
        assertEquals(base.x + 12, moved.x)
        assertEquals(base.y - 3, moved.y)
    }
}
