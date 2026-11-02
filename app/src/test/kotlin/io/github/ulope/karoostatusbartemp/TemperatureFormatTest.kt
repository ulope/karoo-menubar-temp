package io.github.ulope.karoostatusbartemp

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class TemperatureFormatTest {

    private fun format(
        celsius: Double?,
        imperial: Boolean = false,
        decimals: Boolean = false,
        showUnit: Boolean = true,
        locale: Locale = Locale.US,
    ) = TemperatureFormat.format(celsius, imperial, decimals, showUnit, locale)

    @Test
    fun `rounds to whole degrees celsius`() {
        assertEquals("18°C", format(18.4))
        assertEquals("19°C", format(18.5))
        assertEquals("-3°C", format(-3.2))
    }

    @Test
    fun `values rounding to zero have no sign`() {
        assertEquals("0°C", format(-0.4))
        assertEquals("0.0°C", format(-0.04, decimals = true))
    }

    @Test
    fun `converts to fahrenheit for imperial profiles`() {
        assertEquals("64°F", format(18.0, imperial = true))
        assertEquals("32°F", format(0.0, imperial = true))
        assertEquals("-4°F", format(-20.0, imperial = true))
        assertEquals("64.4°F", format(18.0, imperial = true, decimals = true))
    }

    @Test
    fun `decimals use the locale's separator`() {
        assertEquals("18.4°C", format(18.44, decimals = true))
        assertEquals("18,4°C", format(18.44, decimals = true, locale = Locale.GERMANY))
        assertEquals("-2,5°C", format(-2.46, decimals = true, locale = Locale.GERMANY))
    }

    @Test
    fun `unit letter can be hidden`() {
        assertEquals("18°", format(18.0, showUnit = false))
        assertEquals("64°", format(18.0, imperial = true, showUnit = false))
    }

    @Test
    fun `missing readings show a placeholder`() {
        assertEquals("--°C", format(null))
        assertEquals("--°F", format(null, imperial = true))
        assertEquals("--°", format(Double.NaN, showUnit = false))
    }
}
