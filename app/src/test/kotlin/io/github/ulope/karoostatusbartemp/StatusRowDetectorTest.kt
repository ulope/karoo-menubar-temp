package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Layouts are modelled on photos of a Karoo 3 ride screen (480px wide): an icon, the ride time,
 * then battery level, battery icon and clock.
 */
class StatusRowDetectorTest {

    private val screenWidth = 480
    private val maxTop = 32
    private val maxHeight = 120

    private val icon = RowLeaf(Box(4, 4, 34, 34), label = "Radar", isContent = true)
    private val rideTime = RowLeaf(Box(46, 6, 202, 54), label = "0:01:08", isContent = true)
    private val battery = RowLeaf(Box(258, 10, 315, 52), label = "45%", isContent = true)
    private val batteryIcon = RowLeaf(Box(318, 12, 342, 50), label = null, isContent = true)
    private val clock = RowLeaf(Box(355, 6, 470, 54), label = "18:05", isContent = true)
    private val dataField = RowLeaf(Box(300, 90, 420, 120), label = "SPEED", isContent = true)

    private fun detect(vararg leaves: RowLeaf) =
        StatusRowDetector.findAnchors(leaves.toList(), screenWidth, maxTop, maxHeight)

    @Test
    fun `finds ride time and battery level by their text`() {
        val anchors = detect(icon, rideTime, battery, batteryIcon, clock, dataField)!!
        assertEquals(1, anchors.rideTimeIndex)
        assertEquals(2, anchors.batteryIndex)
    }

    @Test
    fun `clock is not mistaken for the ride time`() {
        val anchors = detect(battery, clock)!!
        assertNull(anchors.rideTimeIndex)
        assertEquals(0, anchors.batteryIndex)
    }

    @Test
    fun `battery level in a content description`() {
        val described = battery.copy(label = "Battery 87 %")
        assertEquals(1, detect(rideTime, described)!!.batteryIndex)
    }

    @Test
    fun `long ride times`() {
        val long = rideTime.copy(label = "10:24:59")
        assertEquals(0, detect(long, battery)!!.rideTimeIndex)
        val short = rideTime.copy(label = "59:59")
        assertEquals(0, detect(short, battery)!!.rideTimeIndex)
    }

    @Test
    fun `no status row without a battery level`() {
        // The notification drawer pulled over the ride screen: a bell icon at the top.
        val bell = RowLeaf(Box(225, 10, 255, 45), label = "Notifications", isContent = true)
        assertNull(detect(bell))
        assertNull(detect(rideTime, clock))
        assertNull(detect())
    }

    @Test
    fun `ignores battery-like text below the top row`() {
        val lowBattery = battery.copy(box = Box(258, 200, 315, 240))
        assertNull(detect(rideTime, lowBattery))
    }

    @Test
    fun `finds what is in the way`() {
        val leaves = listOf(rideTime, battery, clock)
        val anchors = StatusRowDetector.findAnchors(leaves, screenWidth, maxTop, maxHeight)!!
        val area = Box(210, 15, 250, 50)
        assertNull(StatusRowDetector.findObstruction(leaves, anchors, area))

        val bell = RowLeaf(Box(225, 10, 255, 45), label = null, isContent = true)
        val withBell = leaves + bell
        assertEquals(bell, StatusRowDetector.findObstruction(withBell, anchors, area))

        // Empty views (spacers, dividers) don't count.
        val spacer = RowLeaf(Box(200, 0, 260, 60), label = null, isContent = false)
        assertNull(StatusRowDetector.findObstruction(leaves + spacer, anchors, area))
    }
}
