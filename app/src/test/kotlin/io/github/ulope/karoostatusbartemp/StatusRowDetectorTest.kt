package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Layouts are modelled on 480x800 Karoo ride screen screenshots: ride time on the left, then
 * battery percentage, battery icon and clock on the right.
 */
class StatusRowDetectorTest {

    private val screenWidth = 480
    private val maxTop = 24
    private val maxHeight = 90

    private val rideTime = Box(46, 4, 205, 56)
    private val battery = Box(280, 12, 340, 50)
    private val clock = Box(355, 4, 470, 56)
    private val dataFieldLabel = Box(300, 90, 420, 120)
    private val bigMapView = Box(0, 60, 480, 800)

    private fun detect(vararg leaves: Box) =
        StatusRowDetector.findAnchors(leaves.toList(), screenWidth, maxTop, maxHeight)

    @Test
    fun `finds ride time and battery`() {
        val anchors = detect(clock, rideTime, dataFieldLabel, battery)!!
        assertEquals(1, anchors.leftIndex)
        assertEquals(3, anchors.rightIndex)
    }

    @Test
    fun `left anchor is optional`() {
        val anchors = detect(battery, clock)!!
        assertNull(anchors.leftIndex)
        assertEquals(0, anchors.rightIndex)
    }

    @Test
    fun `uses the right-most element of the left cluster`() {
        val recordingIcon = Box(4, 10, 40, 50)
        val anchors = detect(recordingIcon, rideTime, battery)!!
        assertEquals(1, anchors.leftIndex)
    }

    @Test
    fun `no status row without a right cluster`() {
        assertNull(detect(rideTime))
        assertNull(detect())
    }

    @Test
    fun `ignores elements below the top row or too tall`() {
        assertNull(detect(dataFieldLabel, bigMapView))
    }

    @Test
    fun `ignores empty boxes`() {
        assertNull(detect(Box(300, 10, 300, 50)))
    }
}
