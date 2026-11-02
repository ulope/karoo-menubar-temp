package io.github.ulope.karoostatusbartemp

import android.content.Context
import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Streams Karoo's built-in air temperature data field (`TYPE_TEMPERATURE_ID`), which is also
 * fed by a paired temperature sensor if there is one, together with the rider's preferred
 * temperature unit.
 */
class KarooTemperature(context: Context) {

    private val karooSystem = KarooSystemService(context.applicationContext)
    private val consumerIds = mutableListOf<String>()
    private var started = false

    private val _celsius = MutableStateFlow<Double?>(null)

    /** Current temperature in degrees Celsius, or null while no value is available. */
    val celsius: StateFlow<Double?> = _celsius.asStateFlow()

    private val _imperial = MutableStateFlow(false)

    /** True when the Karoo user profile is set to imperial temperature units. */
    val imperial: StateFlow<Boolean> = _imperial.asStateFlow()

    fun start() {
        if (started) return
        started = true

        karooSystem.connect { connected ->
            Log.i(TAG, "Karoo system connected: $connected")
            if (!connected) _celsius.value = null
        }
        consumerIds += karooSystem.addConsumer<UserProfile> { profile ->
            _imperial.value = profile.preferredUnit.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL
        }
        consumerIds += karooSystem.addConsumer(
            OnStreamState.StartStreaming(DataType.Type.TEMPERATURE),
            onError = { message -> Log.w(TAG, "Temperature stream error: $message") },
        ) { event: OnStreamState ->
            _celsius.value = when (val state = event.state) {
                is StreamState.Streaming -> state.dataPoint.values[DataType.Field.TEMPERATURE]
                    ?: state.dataPoint.singleValue
                else -> null
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        consumerIds.forEach { karooSystem.removeConsumer(it) }
        consumerIds.clear()
        // Throws if the bind never succeeded (e.g. when not running on a Karoo).
        runCatching { karooSystem.disconnect() }
            .onFailure { Log.w(TAG, "Disconnect failed", it) }
        _celsius.value = null
    }

    private companion object {
        const val TAG = "StatusBarTemp"
    }
}
