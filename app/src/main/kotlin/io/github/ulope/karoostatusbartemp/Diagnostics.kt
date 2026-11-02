package io.github.ulope.karoostatusbartemp

import java.text.DateFormat
import java.util.Date

/**
 * A short history of what the service saw in the ride screen's top row and whether it showed the
 * temperature, shown in the settings screen to help tune the placement and hiding on devices or
 * firmware versions the app wasn't tested on. The service and the settings screen live in the
 * app's process, so a plain object is enough.
 */
object Diagnostics {

    private const val MAX_ENTRIES = 12
    private val DIGITS = Regex("""\d+""")
    private val QUOTED = Regex("\"[^\"\n]*\"")
    private val NODE_COUNTS = Regex("""nodes \d+ labels \d+""")

    /**
     * Whether to also record the ride screen's layer structure. Collecting it walks the whole
     * window, so it is only switched on while the diagnostics are open in the settings screen.
     */
    @Volatile
    var verbose = false

    private class Entry(val key: String, val text: String)

    private val entries = ArrayDeque<Entry>()

    /**
     * Adds [text] as the newest entry. An entry that only differs from the newest one in the values
     * shown (the ride time ticking, the battery level, the temperature) replaces it, so the history
     * keeps distinct states. Positions and sizes are kept, so a drawer moving over the status row
     * shows up as a new entry.
     */
    @Synchronized
    fun record(text: String) {
        val key = keyOf(text)
        if (entries.firstOrNull()?.key == key) entries.removeFirst()
        entries.addFirst(Entry(key, "${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())} $text"))
        while (entries.size > MAX_ENTRIES) entries.removeLast()
    }

    @Synchronized
    fun clear() = entries.clear()

    /** The recorded states, newest first. */
    @Synchronized
    fun entries(): List<String> = entries.map { it.text }

    /** Masks the numbers that change all the time: those in quoted labels, and node counts. */
    internal fun keyOf(text: String): String = text
        .replace(QUOTED) { it.value.replace(DIGITS, "#") }
        .replace(NODE_COUNTS, "nodes # labels #")
}
