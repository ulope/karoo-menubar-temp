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
        // The Karoo 3 ride screen always has empty containers spanning the whole screen.
        val container = RowLeaf(Box(0, 0, 480, 800), label = null, isContent = false)
        val leaves = listOf(rideTime, battery, clock, container, container)
        val anchors = StatusRowDetector.findAnchors(leaves, screenWidth, maxTop, maxHeight)!!
        val area = Box(210, 15, 250, 50)
        assertNull(StatusRowDetector.findObstruction(leaves, anchors, area, screenWidth))

        val bell = RowLeaf(Box(225, 10, 255, 45), label = "Notifications", isContent = true)
        assertEquals(bell, StatusRowDetector.findObstruction(leaves + bell, anchors, area, screenWidth))

        // A drawer's handle or icon without a description counts too.
        val handle = RowLeaf(Box(200, 4, 280, 12), label = null, isContent = false)
        assertEquals(handle, StatusRowDetector.findObstruction(leaves + handle, anchors, Box(210, 0, 250, 50), screenWidth))
    }

    @Test
    fun `nothing on top of the status row on the normal ride screen`() {
        // Karoo 3 diagnostics: five transparent full-window containers on top of the status row,
        // holding the button hints and a radar icon elsewhere.
        val above = listOf(
            LayerNode(window, label = null, isContent = false, hasVisibleChildren = true),
            LayerNode(window, label = null, isContent = false, hasVisibleChildren = false),
            LayerNode(Box(0, 0, 38, 38), label = null, isContent = true, hasVisibleChildren = false),
            LayerNode(Box(0, 740, 60, 800), label = "keyButtons_LAP", isContent = true, hasVisibleChildren = false),
        )
        assertNull(StatusRowDetector.findCover(above, temperature, window))
        // An empty strip across the top, such as a shadow, doesn't hide it either.
        val strip = LayerNode(Box(0, 0, 480, 60), label = null, isContent = false, hasVisibleChildren = false)
        assertNull(StatusRowDetector.findCover(above + strip, temperature, window))
    }

    @Test
    fun `a panel drawn over the status row covers it`() {
        val container = LayerNode(window, label = null, isContent = false, hasVisibleChildren = true)
        // A full-width sheet that isn't window-sized, or its header.
        val sheet = LayerNode(Box(0, 0, 480, 760), label = null, isContent = false, hasVisibleChildren = true)
        assertEquals(sheet, StatusRowDetector.findCover(listOf(container, sheet), temperature, window))
        // A dialog or a card narrower than the window.
        val card = LayerNode(Box(60, 8, 420, 300), label = null, isContent = false, hasVisibleChildren = false)
        assertEquals(card, StatusRowDetector.findCover(listOf(container, card), temperature, window))
        // Text or an icon.
        val title = LayerNode(Box(0, 20, 480, 70), label = "Ride", isContent = true, hasVisibleChildren = false)
        assertEquals(title, StatusRowDetector.findCover(listOf(container, title), temperature, window))
        // Nothing where the temperature goes.
        val low = LayerNode(Box(0, 300, 480, 760), label = "Ride", isContent = true, hasVisibleChildren = true)
        assertNull(StatusRowDetector.findCover(listOf(container, low), temperature, window))
    }

    private val window = Box(0, 0, 480, 800)
    private val temperature = Box(204, 9, 260, 58)
}
