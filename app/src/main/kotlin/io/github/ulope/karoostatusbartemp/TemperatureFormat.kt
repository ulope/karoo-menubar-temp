package io.github.ulope.karoostatusbartemp

import java.util.Locale
import kotlin.math.roundToLong

/** Turns a Karoo temperature reading (always degrees Celsius) into status bar text. */
object TemperatureFormat {

    const val PLACEHOLDER = "--"

    fun celsiusToFahrenheit(celsius: Double): Double = celsius * 9.0 / 5.0 + 32.0

    /**
     * @param celsius the current reading, or null when no temperature is available
     * @param imperial show degrees Fahrenheit instead of Celsius
     * @param decimals show one decimal place (using the locale's decimal separator)
     * @param showUnit append "C"/"F" after the degree sign
     */
    fun format(
        celsius: Double?,
        imperial: Boolean,
        decimals: Boolean,
        showUnit: Boolean,
        locale: Locale = Locale.getDefault(),
    ): String {
        val unit = when {
            !showUnit -> "°"
            imperial -> "°F"
            else -> "°C"
        }
        if (celsius == null || celsius.isNaN() || celsius.isInfinite()) return PLACEHOLDER + unit

        val value = if (imperial) celsiusToFahrenheit(celsius) else celsius
        val number = if (decimals) {
            val rounded = (value * 10).roundToLong() / 10.0
            // `+ 0.0` turns -0.0 into 0.0, so values that round to zero are shown without a sign.
            String.format(locale, "%.1f", rounded + 0.0)
        } else {
            value.roundToLong().toString()
        }
        return number + unit
    }
}
