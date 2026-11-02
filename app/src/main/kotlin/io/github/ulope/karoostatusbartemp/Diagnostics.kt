package io.github.ulope.karoostatusbartemp

/**
 * What the service last saw in the ride screen's top row, shown in the settings screen to help
 * tune the placement on devices or firmware versions the app wasn't tested on. Both live in the
 * app's process, so a plain object is enough.
 */
object Diagnostics {
    /** Details of the last time the temperature was shown. */
    @Volatile
    var lastShown: String? = null

    /** Why the temperature was last hidden although the ride app's status row was found. */
    @Volatile
    var lastHidden: String? = null
}
