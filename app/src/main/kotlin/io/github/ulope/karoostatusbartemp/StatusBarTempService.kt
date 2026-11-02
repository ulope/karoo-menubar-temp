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
 *  2. draws the temperature in that gap with an accessibility overlay window, at the battery
 *     level's size and baseline.
 */
class StatusBarTempService : AccessibilityService() {

    /** The result of reading the ride app's window. */
    private data class Scan(
        val row: StatusRow,
        val leaves: List<RowLeaf>,
        val anchors: StatusRowDetector.Anchors,
        /** Windows above the ride app that may cover the row, with a description for diagnostics. */
        val windowsAbove: List<Pair<String, Box>>,
        /** Nodes near the top in the layers the ride app draws on top of the status row. */
        val layersAbove: List<LayerNode>,
        /** The ride app's window bounds. */
        val window: Box,
        val screenWidth: Int,
        /** Details for the diagnostics screen. */
        val details: String,
    )

    private val handler = Handler(Looper.getMainLooper())
    private var scope: CoroutineScope? = null
    private var overlay: TemperatureOverlay? = null
    private var temperature: KarooTemperature? = null
    private val measurePaint by lazy { statusTextPaint(this).apply { textSize = MEASURE_TEXT_SIZE } }

    private var settings = TempSettings()
    private var scan: Scan? = null
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
        val found = if (settings.enabled) scanRideApp() else null
        if (found?.row != scan?.row) Log.d(TAG, "Status row: ${found?.row}")
        if (found == null && settings.enabled && Diagnostics.verbose) recordNoStatusRow()
        scan = found
        render()
    }

    /** Diagnostics for when the ride app's status row isn't found (e.g. another app in front). */
    private fun recordNoStatusRow() {
        val windows = try {
            windows
        } catch (e: RuntimeException) {
            return
        }
        // Not interesting while our own settings screen is in front.
        val ours = windows.any { w -> w.root?.let { root -> (root.packageName == packageName).also { root.recycleCompat() } } == true }
        if (ours) return
        Diagnostics.record("NO STATUS ROW (temperature hidden)\nwindows (top first):\n${describeWindows(windows)}")
    }

    private fun describeWindows(windows: List<AccessibilityWindowInfo>): String =
        windows.sortedByDescending { it.layer }.joinToString("\n") { w ->
            val r = Rect().also { w.getBoundsInScreen(it) }
            "  type ${w.type} layer ${w.layer} \"${w.title ?: ""}\" [${r.left},${r.top}-${r.right},${r.bottom}]"
        }

    private fun render() {
        val overlay = overlay ?: return
        val temperature = temperature ?: return
        val scan = scan
        if (!settings.enabled || scan == null) {
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
        val widthPerPx = measurePaint.measureText(text) / MEASURE_TEXT_SIZE
        val ink = inkBounds(text)
        val placement = OverlayPlacement.place(
            row = scan.row,
            text = TextMetrics(
                inkLeft = ink.left / MEASURE_TEXT_SIZE,
                inkRight = ink.right / MEASURE_TEXT_SIZE,
                ascent = -metrics.ascent / MEASURE_TEXT_SIZE,
            ),
            sizePercent = settings.sizePercent,
            gapPx = dp(GAP_DP),
            offsetXPx = dp(settings.offsetXDp),
            offsetYPx = dp(settings.offsetYDp),
        )

        // Hide if something else is where the text would go: a drawer pulled over the ride
        // screen, a dialog, the control center.
        val area = Box(
            left = placement.x,
            top = placement.y,
            right = placement.x + ceil(placement.textSizePx * widthPerPx).toInt(),
            bottom = placement.y + ceil(placement.textSizePx * (metrics.descent - metrics.ascent) / MEASURE_TEXT_SIZE).toInt(),
        )
        val coveringWindow = scan.windowsAbove.firstOrNull { (_, bounds) -> bounds.intersects(area) }
        val obstruction = StatusRowDetector.findObstruction(scan.leaves, scan.anchors, area, scan.screenWidth)
        val cover = StatusRowDetector.findCover(scan.layersAbove, area, scan.window)
        val details = "${scan.details}\n${describeLayersOver(scan.layersAbove, area)}"
        if (coveringWindow != null || obstruction != null || cover != null) {
            val reason = when {
                coveringWindow != null -> "covered by window ${coveringWindow.first} ${coveringWindow.second}"
                obstruction != null -> "\"${obstruction.label ?: "(unlabelled)"}\" ${obstruction.box} is in the way"
                else -> "covered by ${cover?.className} \"${cover?.label ?: ""}\" ${cover?.box} drawn on top of the status row"
            }
            Diagnostics.record("HIDDEN: $reason\n$details")
            overlay.hide()
            return
        }

        overlay.show(text, placement, settings.textColor.argb)
        Diagnostics.record(
            "SHOWN \"$text\" ${"%.1f".format(placement.textSizePx)}px at $area " +
                "(native font ${"%.1f".format(OverlayPlacement.nativeFontSize(scan.row))}px)\n$details",
        )
    }

    /** For diagnostics: what the ride app draws on top of the status row where the temperature goes. */
    private fun describeLayersOver(layersAbove: List<LayerNode>, area: Box): String {
        val over = layersAbove.filter { it.box.intersects(area) }
        val lines = over.take(MAX_LAYER_LINES).joinToString("\n") { node ->
            "  ${"·".repeat(node.depth)}${node.className} \"${node.label ?: ""}\" ${node.box}" +
                if (node.hasVisibleChildren) " +children" else ""
        }
        val more = if (over.size > MAX_LAYER_LINES) "\n  … ${over.size - MAX_LAYER_LINES} more" else ""
        return "drawn on top of the status row, over the temperature:\n${lines.ifEmpty { "  none" }}$more"
    }

    /**
     * Reads the ride app's top status row, or returns null if it is not on screen (ride app not
     * in front, or not showing the row).
     */
    private fun scanRideApp(): Scan? {
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
                val pkg = root.packageName?.toString()
                if (pkg?.startsWith(RIDE_APP_PACKAGE_PREFIX) != true) continue

                val nodes = mutableListOf<AccessibilityNodeInfo>()
                collectTopRowLeaves(root, maxTopPx, nodes, obtained)
                val leaves = nodes.map { node ->
                    val label = node.text?.toString()?.takeIf { it.isNotBlank() }
                        ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
                    RowLeaf(
                        box = node.screenBox(),
                        label = label,
                        isContent = label != null || node.className?.contains("Image") == true,
                    )
                }
                val windowBounds = Rect().also { window.getBoundsInScreen(it) }
                val screenWidth = maxOf(resources.displayMetrics.widthPixels, windowBounds.right)
                val anchors = StatusRowDetector.findAnchors(leaves, screenWidth, maxTopPx, maxHeightPx)
                    ?: continue

                val battery = leaves[anchors.batteryIndex]
                val batteryText = textBounds(nodes[anchors.batteryIndex], battery.box)
                val batteryBox = batteryText ?: battery.box

                val rideTime = anchors.rideTimeIndex?.let { leaves[it] }
                val rideTimeText = anchors.rideTimeIndex?.let { textBounds(nodes[it], leaves[it].box) }
                // The Karoo 3 ride app doesn't report character bounds for the ride time, but its
                // node hugs the text (diagnostics: node ends at 169, last ink at ~165).
                val rideTimeBox = rideTime?.let { rideTimeText ?: it.box }
                // Center on the visible ink: the last digit of the ride time and the first digit of
                // the battery level have a few px of empty space inside their text bounds.
                val rideTimeEnd = rideTimeBox?.let { it.right - inkInset(it) }
                val batteryStart = batteryBox.left + inkInset(batteryBox)

                val windowsAbove = windows
                    .filter { it.layer > window.layer && it.type != AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY }
                    .map { it to Rect().also { r -> it.getBoundsInScreen(r) } }
                    // Thin system bars along the edge don't cover the ride app's status row.
                    .filterNot { (w, r) -> w.type == AccessibilityWindowInfo.TYPE_SYSTEM && r.height() <= maxHeightPx }
                    .map { (w, r) -> "type ${w.type} \"${w.title ?: ""}\"" to Box(r.left, r.top, r.right, r.bottom) }

                // Drawers pulled over the ride screen are drawn inside the ride app's own window,
                // in layers on top of the status row.
                val batteryNode = nodes[anchors.batteryIndex]
                val rowContainer = rowContainer(batteryNode, anchors.rideTimeIndex?.let { nodes[it] }, obtained)
                val layersAbove = rowContainer
                    ?.let { collectLayersAbove(it, bandBottom = 2 * maxHeightPx, obtained) }
                    .orEmpty()

                val metrics = resources.displayMetrics
                val others = leaves.indices
                    .filter { it != anchors.batteryIndex && it != anchors.rideTimeIndex }
                    .take(10)
                    .joinToString("\n") { i ->
                        val l = leaves[i]
                        "  ${nodes[i].shortClassName()} \"${l.label ?: ""}\" ${l.box}"
                    }
                // The layers of the ride screen and what each contains: shows whether something
                // like a drawer is drawn over the status row inside the ride app's own window.
                val gap = Box(rideTimeEnd ?: (batteryStart - dp(40)), 0, batteryStart, batteryBox.bottom)
                val layers = if (Diagnostics.verbose) describeLayers(batteryNode, gap, obtained) else null
                val allWindows = describeWindows(windows)
                val details = buildString {
                    append("app $pkg, screen ${metrics.widthPixels}x${metrics.heightPixels} @${metrics.density}x\n")
                    append("battery \"${battery.label}\" node ${battery.box} text ${batteryText ?: "n/a"} → ink starts at $batteryStart\n")
                    append(
                        if (rideTime != null) {
                            "ride time \"${rideTime.label}\" node ${rideTime.box} text ${rideTimeText ?: "n/a"} → ink ends at $rideTimeEnd\n"
                        } else {
                            "ride time not found\n"
                        },
                    )
                    append("other items at the top:\n").append(others.ifEmpty { "  none" }).append('\n')
                    append("windows (top first):\n").append(allWindows)
                    if (layers != null) append("\nlayers around the battery level (gap $gap):\n").append(layers)
                }

                return Scan(
                    row = StatusRow(leftEdge = rideTimeEnd, rightEdge = batteryStart, reference = batteryBox),
                    leaves = leaves,
                    anchors = anchors,
                    windowsAbove = windowsAbove,
                    layersAbove = layersAbove,
                    window = Box(windowBounds.left, windowBounds.top, windowBounds.right, windowBounds.bottom),
                    screenWidth = screenWidth,
                    details = details,
                )
            } finally {
                obtained.forEach { it.recycleCompat() }
            }
        }
        return null
    }

    /** Ink bounds of [text] in our font at [MEASURE_TEXT_SIZE], relative to the text origin. */
    private fun inkBounds(text: String): Rect = Rect().also { measurePaint.getTextBounds(text, 0, text.length, it) }

    /**
     * Typical empty space between the edge of a native text's bounds and its ink. The ride
     * screen's digits are close to, but not exactly, our Roboto Condensed, so this is calibrated
     * from photos rather than measured in our font (2-6px for digits on a Karoo 3).
     */
    private fun inkInset(box: Box): Int =
        (box.height / OverlayPlacement.REFERENCE_HEIGHT_PER_FONT_SIZE * NATIVE_INK_INSET).roundToInt()

    /**
     * For diagnostics: walks up from [anchor] and, at every level with siblings, describes each
     * sibling layer: index (later ones are drawn on top), drawing order, bounds, how many visible
     * nodes and labels it contains, how many of them overlap [gap], and its first labels.
     */
    private fun describeLayers(anchor: AccessibilityNodeInfo, gap: Box, obtained: MutableList<AccessibilityNodeInfo>): String {
        val out = StringBuilder()
        var child = anchor
        var parent = anchor.parent?.also { obtained += it }
        var levels = 0
        while (parent != null && levels < 20) {
            val siblings = children(parent, obtained)
            if (siblings.size > 1) {
                out.append("  ${parent.shortClassName()} ${parent.screenBox()}:\n")
                val branch = siblings.indexOf(child)
                siblings.forEachIndexed { i, c ->
                    val stats = LayerStats()
                    stats.collect(c, gap, obtained)
                    // ">" leads to the battery level, "^" is drawn on top of it.
                    val above = branch >= 0 && i != branch && isDrawnAbove(c, i, child, branch)
                    val marker = if (i == branch) ">" else if (above) "^" else " "
                    // Labels only where they matter, so data fields changing don't add entries.
                    val labels = if (above) stats.firstLabels.joinToString("") { " \"$it\"" } else ""
                    out.append(
                        "  $marker#$i z${c.drawingOrder} ${c.shortClassName()} ${c.screenBox()}" +
                            "${if (c.isVisibleToUser) "" else " invisible"} nodes ${stats.nodes} labels ${stats.labels}" +
                            " in gap ${stats.inGap}$labels\n",
                    )
                }
            }
            child = parent
            parent = parent.parent?.also { obtained += it }
            levels++
        }
        return out.toString().trimEnd()
    }

    /** The status row's container: the closest common ancestor of the battery level and ride time. */
    private fun rowContainer(
        battery: AccessibilityNodeInfo,
        rideTime: AccessibilityNodeInfo?,
        obtained: MutableList<AccessibilityNodeInfo>,
    ): AccessibilityNodeInfo? {
        val batteryAncestors = ancestors(battery, obtained)
        if (rideTime == null) return batteryAncestors.firstOrNull()
        return ancestors(rideTime, obtained).firstOrNull { it in batteryAncestors } ?: batteryAncestors.firstOrNull()
    }

    private fun ancestors(node: AccessibilityNodeInfo, obtained: MutableList<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        var parent = node.parent
        while (parent != null && out.size < MAX_DEPTH) {
            obtained += parent
            out += parent
            parent = parent.parent
        }
        return out
    }

    private fun children(node: AccessibilityNodeInfo, obtained: MutableList<AccessibilityNodeInfo>): List<AccessibilityNodeInfo> =
        (0 until node.childCount).mapNotNull { i -> node.getChild(i)?.also { obtained += it } }

    /**
     * Collects the visible nodes above [bandBottom] in every layer drawn on top of [row]: the
     * siblings drawn after it, and after each of its ancestors.
     */
    private fun collectLayersAbove(
        row: AccessibilityNodeInfo,
        bandBottom: Int,
        obtained: MutableList<AccessibilityNodeInfo>,
    ): List<LayerNode> {
        val out = mutableListOf<LayerNode>()
        var child = row
        for (parent in ancestors(row, obtained)) {
            val siblings = children(parent, obtained)
            val branch = siblings.indexOf(child)
            if (branch >= 0) {
                siblings.forEachIndexed { i, sibling ->
                    if (i != branch && isDrawnAbove(sibling, i, child, branch)) {
                        collectBand(sibling, bandBottom, 0, out, obtained)
                    }
                }
            }
            child = parent
        }
        return out
    }

    /** Whether [node] (child number [index]) is drawn after [other] (child number [otherIndex]). */
    private fun isDrawnAbove(node: AccessibilityNodeInfo, index: Int, other: AccessibilityNodeInfo, otherIndex: Int): Boolean =
        if (node.drawingOrder != other.drawingOrder) node.drawingOrder > other.drawingOrder else index > otherIndex

    private fun collectBand(
        node: AccessibilityNodeInfo,
        bandBottom: Int,
        depth: Int,
        out: MutableList<LayerNode>,
        obtained: MutableList<AccessibilityNodeInfo>,
    ) {
        if (out.size >= MAX_LAYER_NODES || !node.isVisibleToUser) return
        val box = node.screenBox()
        // Children lie within their parent's bounds, so nothing below the band can contain it.
        if (box.top >= bandBottom || box.width <= 0 || box.height <= 0) return
        val children = children(node, obtained)
        val label = (node.text ?: node.contentDescription)?.toString()?.takeIf { it.isNotBlank() }
        out += LayerNode(
            box = box,
            label = label,
            isContent = label != null || node.className?.contains("Image") == true,
            hasVisibleChildren = children.any { it.isVisibleToUser },
            depth = depth,
            className = node.shortClassName(),
        )
        children.forEach { collectBand(it, bandBottom, depth + 1, out, obtained) }
    }

    private inner class LayerStats {
        var nodes = 0
        var labels = 0
        var inGap = 0
        val firstLabels = mutableListOf<String>()

        fun collect(node: AccessibilityNodeInfo, gap: Box, obtained: MutableList<AccessibilityNodeInfo>) {
            if (nodes >= 300 || !node.isVisibleToUser) return
            nodes++
            val label = (node.text ?: node.contentDescription)?.toString()?.takeIf { it.isNotBlank() }
            if (label != null) {
                labels++
                if (firstLabels.size < 2) firstLabels += label.take(14)
            }
            val box = node.screenBox()
            // Containers spanning the whole screen overlap everything; count only real content.
            if (box.intersects(gap) && (label != null || box.width < screenWidthPx * 0.9f)) inGap++
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                obtained += child
                collect(child, gap, obtained)
            }
        }
    }

    private val screenWidthPx: Int get() = resources.displayMetrics.widthPixels

    private fun AccessibilityNodeInfo.shortClassName(): String =
        className?.toString()?.substringAfterLast('.') ?: "?"

    /** Collects visible leaf nodes near the top of the screen. */
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
            leaves += node
            return
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            obtained += child
            collectTopRowLeaves(child, maxTopPx, leaves, obtained)
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
         * Minimum space kept between the temperature's ink and the ride time / battery ink. With
         * an icon left of the ride time, the gap on a Karoo 3 is ~58px; at 1dp a full-size "30°"
         * (~51px of ink) still fits, so the size stays the same with and without the icon.
         */
        private const val GAP_DP = 1

        /** Limits for walking the layers on top of the status row (about 20 nodes on a Karoo 3). */
        private const val MAX_LAYER_NODES = 300
        private const val MAX_DEPTH = 30

        /** Diagnostics: how many of those nodes to list. */
        private const val MAX_LAYER_LINES = 12

        private const val EVENT_SCAN_DELAY_MS = 100L
        private const val PERIODIC_SCAN_MS = 1000L
        private const val MEASURE_TEXT_SIZE = 100f

        /**
         * Side bearing of the native digits, as a fraction of their font size. Measured on a
         * Karoo 3: ride time 4.3-5.6px (~52px font), battery level 2-4.2px (~41px font).
         */
        private const val NATIVE_INK_INSET = 0.08f
    }
}
