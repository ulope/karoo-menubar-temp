package io.github.ulope.karoostatusbartemp

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

enum class TextColor(val argb: Int) {
    BLACK(0xFF000000.toInt()),
    WHITE(0xFFFFFFFF.toInt()),
}

data class TempSettings(
    val enabled: Boolean = true,
    /** Horizontal fine-tuning in dp, positive moves right. */
    val offsetXDp: Int = 0,
    /** Vertical fine-tuning in dp, positive moves down. */
    val offsetYDp: Int = 0,
    /** Text size relative to the native status bar text. */
    val sizePercent: Int = 100,
    val showDecimals: Boolean = false,
    val showUnit: Boolean = true,
    val textColor: TextColor = TextColor.BLACK,
) {
    companion object {
        val OFFSET_X_RANGE = -100..100
        val OFFSET_Y_RANGE = -20..20
        val SIZE_RANGE = 50..150
    }
}

/** Settings live in SharedPreferences, shared between the settings screen and the service. */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): TempSettings {
        val defaults = TempSettings()
        return TempSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, defaults.enabled),
            offsetXDp = prefs.getInt(KEY_OFFSET_X, defaults.offsetXDp).coerceIn(TempSettings.OFFSET_X_RANGE),
            offsetYDp = prefs.getInt(KEY_OFFSET_Y, defaults.offsetYDp).coerceIn(TempSettings.OFFSET_Y_RANGE),
            sizePercent = prefs.getInt(KEY_SIZE, defaults.sizePercent).coerceIn(TempSettings.SIZE_RANGE),
            showDecimals = prefs.getBoolean(KEY_DECIMALS, defaults.showDecimals),
            showUnit = prefs.getBoolean(KEY_UNIT, defaults.showUnit),
            textColor = prefs.getString(KEY_COLOR, null)
                ?.let { name -> TextColor.entries.firstOrNull { it.name == name } }
                ?: defaults.textColor,
        )
    }

    fun save(settings: TempSettings) {
        prefs.edit {
            putBoolean(KEY_ENABLED, settings.enabled)
            putInt(KEY_OFFSET_X, settings.offsetXDp)
            putInt(KEY_OFFSET_Y, settings.offsetYDp)
            putInt(KEY_SIZE, settings.sizePercent)
            putBoolean(KEY_DECIMALS, settings.showDecimals)
            putBoolean(KEY_UNIT, settings.showUnit)
            putString(KEY_COLOR, settings.textColor.name)
        }
    }

    /** Emits the current settings, then again after every change. */
    fun updates(): Flow<TempSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(load()) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(load())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_OFFSET_X = "offset_x_dp"
        const val KEY_OFFSET_Y = "offset_y_dp"
        const val KEY_SIZE = "size_percent"
        const val KEY_DECIMALS = "show_decimals"
        const val KEY_UNIT = "show_unit"
        const val KEY_COLOR = "text_color"
    }
}
