package io.github.ulope.karoostatusbartemp

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Shows the current air temperature in the ride screen's top status row, between the ride time
 * and the battery level.
 *
 * Karoo OS has no API for adding items to the status bar. During a ride, that row is drawn by the
 * ride app itself, so this service:
 *  1. reads the ride app's window through accessibility to find where the ride time ends and the
 *     battery level starts, and whether the row is on screen at all (it is not on menus, the
 *     launcher or other apps), and
 *  2. draws the temperature in that gap with an accessibility overlay window.
 */
class StatusBarTempService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var overlay: TemperatureOverlay? = null
    private var temperature: KarooTemperature? = null
    private val measurePaint by lazy { statusTextPaint(this).apply { textSize = MEASURE_TEXT_SIZE } }

    private var settings = TempSettings()
    private var row: StatusRow? = null
    private var scanPending = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")

        overlay = TemperatureOverlay(this)
        val temperature = KarooTemperature(this).also { temperature = it }
        val store = SettingsStore(this)
        settings = store.load()

        scope = MainScope().apply {
            launch {
                store.updates().collect {
                    settings = it
                    if (it.enabled) temperature.start() else temperature.stop()
                    rescan()
                }
            }
            launch {
                combine(temperature.celsius, temperature.imperial) { _, _ -> }.collect { render() }
            }
        }
        handler.post(periodicScan)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window changes (screen switches, dialogs, the ride app coming to the front) are
        // coalesced into one scan shortly after.
        if (scanPending) return
        scanPending = true
        handler.postDelayed(eventScan, EVENT_SCAN_DELAY_MS)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        Log.i(TAG, "Accessibility service destroyed")
        handler.removeCallbacksAndMessages(null)
        scope?.cancel()
        scope = null
        overlay?.hide()
        overlay = null
        temperature?.stop()
        temperature = null
        super.onDestroy()
    }

    private val eventScan = Runnable {
        scanPending = false
        rescan()
    }

    /** Catches changes inside the row (ride time getting longer, battery digits) and missed events. */
    private val periodicScan = object : Runnable {
        override fun run() {
            rescan()
            handler.postDelayed(this, PERIODIC_SCAN_MS)
        }
    }

    private fun rescan() {
        val found = if (settings.enabled) findRideStatusRow() else null
        if (found != row) Log.d(TAG, "Status row: $found")
        row = found
        render()
    }

    private fun render() {
        val overlay = overlay ?: return
        val temperature = temperature ?: return
        val row = row
        if (!settings.enabled || row == null) {
            overlay.hide()
            return
        }

        val text = TemperatureFormat.format(
            celsius = temperature.celsius.value,
            imperial = temperature.imperial.value,
            decimals = settings.showDecimals,
            showUnit = settings.showUnit,
        )
        val metrics = measurePaint.fontMetrics
        val placement = OverlayPlacement.place(
            row = row,
            widthPerPx = measurePaint.measureText(text) / MEASURE_TEXT_SIZE,
            heightPerPx = (metrics.descent - metrics.ascent) / MEASURE_TEXT_SIZE,
            sizePercent = settings.sizePercent,
            gapPx = dp(GAP_DP),
            offsetXPx = dp(settings.offsetXDp),
            offsetYPx = dp(settings.offsetYDp),
        )
        overlay.show(text, placement, settings.textColor.argb)
    }

    /**
     * Finds the ride app's top status row, or returns null if it is not visible (ride app not in
     * front, or the row covered by another window such as the control center or a dialog).
     */
    private fun findRideStatusRow(): StatusRow? {
        val windows = try {
            windows
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read windows", e)
            return null
        }
        val maxTopPx = dp(MAX_TOP_DP)
        val maxHeightPx = dp(MAX_ROW_HEIGHT_DP)

        // Top-most window first, so a ride app dialog is checked before the ride screen below it.
        for (window in windows.sortedByDescending { it.layer }) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            val obtained = mutableListOf(root)
            try {
                if (root.packageName?.toString()?.startsWith(RIDE_APP_PACKAGE_PREFIX) != true) continue

                val leaves = mutableListOf<AccessibilityNodeInfo>()
                collectTopRowLeaves(root, maxTopPx, leaves, obtained)
                val boxes = leaves.map { it.screenBox() }
                val windowBounds = Rect().also { window.getBoundsInScreen(it) }
                val screenWidth = maxOf(resources.displayMetrics.widthPixels, windowBounds.right)
                val anchors = StatusRowDetector.findAnchors(boxes, screenWidth, maxTopPx, maxHeightPx)
                    ?: continue

                val rightNodeBox = boxes[anchors.rightIndex]
                val rightBox = textBounds(leaves[anchors.rightIndex], rightNodeBox) ?: rightNodeBox
                val leftBox = anchors.leftIndex?.let { textBounds(leaves[it], boxes[it]) ?: boxes[it] }
                val row = StatusRow(leftEdge = leftBox?.right, rightEdge = rightBox.left, reference = rightBox)

                return if (isCovered(row, window, windows, maxHeightPx)) null else row
            } finally {
                obtained.forEach { it.recycleCompat() }
            }
        }
        return null
    }

    /** Collects labelled, visible leaf nodes near the top of the screen. */
    private fun collectTopRowLeaves(
        node: AccessibilityNodeInfo,
        maxTopPx: Int,
        leaves: MutableList<AccessibilityNodeInfo>,
        obtained: MutableList<AccessibilityNodeInfo>,
    ) {
        if (!node.isVisibleToUser) return
        // Children lie within their parent's bounds, so nothing below the top row can contain it.
        if (node.screenBox().top > maxTopPx) return

        if (node.childCount == 0) {
            if (!node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) leaves += node
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            obtained += child
            collectTopRowLeaves(child, maxTopPx, leaves, obtained)
        }
    }

    /**
     * True if a window above the ride app covers the spot where the temperature would be drawn,
     * e.g. the control center, a notification or a dialog. Thin system bars and other
     * accessibility overlays are ignored.
     */
    private fun isCovered(
        row: StatusRow,
        rideWindow: AccessibilityWindowInfo,
        windows: List<AccessibilityWindowInfo>,
        maxHeightPx: Int,
    ): Boolean {
        val gap = Rect(
            row.leftEdge ?: (row.rightEdge - dp(GAP_DP)),
            row.reference.top,
            row.rightEdge,
            row.reference.bottom,
        )
        return windows.any { window ->
            if (window.layer <= rideWindow.layer) return@any false
            if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) return@any false
            val bounds = Rect().also { window.getBoundsInScreen(it) }
            if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM && bounds.height() <= maxHeightPx) {
                return@any false
            }
            Rect.intersects(bounds, gap)
        }
    }

    /**
     * Bounds of the characters of a text node, which can be narrower than the node itself (text
     * views with padding or a fixed width). Falls back to null if the app does not provide them.
     */
    private fun textBounds(node: AccessibilityNodeInfo, nodeBox: Box): Box? {
        val text = node.text
        if (text.isNullOrEmpty()) return null
        val key = AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY
        if (key !in node.availableExtraData) return null

        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
            putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH, text.length)
        }
        if (!node.refreshWithExtraData(key, args)) return null
        val rects = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            node.extras.getParcelableArray(key, RectF::class.java)
        } else {
            @Suppress("DEPRECATION")
            node.extras.getParcelableArray(key)
        }?.filterIsInstance<RectF>()?.filter { it.width() > 0f && it.height() > 0f }
        if (rects.isNullOrEmpty()) return null

        val box = Box(
            left = floor(rects.minOf { it.left }).toInt(),
            top = floor(rects.minOf { it.top }).toInt(),
            right = ceil(rects.maxOf { it.right }).toInt(),
            bottom = ceil(rects.maxOf { it.bottom }).toInt(),
        )
        // Distrust results that do not lie within the node.
        val slack = dp(4)
        val plausible = box.left >= nodeBox.left - slack && box.right <= nodeBox.right + slack &&
            box.top >= nodeBox.top - slack && box.bottom <= nodeBox.bottom + slack
        return if (plausible) box else null
    }

    private fun AccessibilityNodeInfo.screenBox(): Box {
        val rect = Rect().also { getBoundsInScreen(it) }
        return Box(rect.left, rect.top, rect.right, rect.bottom)
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleCompat() {
        // Required before Android 13 to return nodes to the pool; a no-op afterwards.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) runCatching { recycle() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val TAG = "StatusBarTemp"

        /** The Karoo ride app (`io.hammerhead.rideapp`), which draws the in-ride status row. */
        private const val RIDE_APP_PACKAGE_PREFIX = "io.hammerhead.ride"

        /** Status row elements start within this distance from the top of the screen... */
        private const val MAX_TOP_DP = 16

        /** ... and are at most this tall. Data fields further down are excluded. */
        private const val MAX_ROW_HEIGHT_DP = 60

        /**
         * Minimum space kept between the temperature and the ride time / battery text. The gap
         * is narrow on a Karoo 3 (about 45px with an icon left of the ride time), so keep it tight.
         */
        private const val GAP_DP = 2

        private const val EVENT_SCAN_DELAY_MS = 100L
        private const val PERIODIC_SCAN_MS = 1000L
        private const val MEASURE_TEXT_SIZE = 100f
    }
}
