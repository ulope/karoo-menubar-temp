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

    private const val MAX_ENTRIES = 8
    private val DIGITS = Regex("""\d+""")

    private class Entry(val key: String, val text: String)

    private val entries = ArrayDeque<Entry>()

    /**
     * Adds [text] as the newest entry. An entry that only differs from the newest one in numbers
     * (the ride time ticking, the battery level) replaces it, so the history keeps distinct states.
     */
    @Synchronized
    fun record(text: String) {
        val key = text.replace(DIGITS, "#")
        if (entries.firstOrNull()?.key == key) entries.removeFirst()
        entries.addFirst(Entry(key, "${DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date())} $text"))
        while (entries.size > MAX_ENTRIES) entries.removeLast()
    }

    /** The recorded states, newest first. */
    @Synchronized
    fun entries(): List<String> = entries.map { it.text }
}
