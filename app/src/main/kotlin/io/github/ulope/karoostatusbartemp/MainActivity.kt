package io.github.ulope.karoostatusbartemp

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

class MainActivity : ComponentActivity() {

    private lateinit var temperature: KarooTemperature
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        temperature = KarooTemperature(this)
        store = SettingsStore(this)

        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                var settings by remember { mutableStateOf(store.load()) }
                var serviceEnabled by remember { mutableStateOf(isServiceEnabled(this)) }
                val celsius by temperature.celsius.collectAsState()
                val imperial by temperature.imperial.collectAsState()

                LifecycleResumeEffect(Unit) {
                    // The user may have just come back from the accessibility settings.
                    serviceEnabled = isServiceEnabled(this@MainActivity)
                    onPauseOrDispose { }
                }

                SettingsScreen(
                    settings = settings,
                    serviceEnabled = serviceEnabled,
                    celsius = celsius,
                    imperial = imperial,
                    onSettingsChange = {
                        settings = it
                        store.save(it)
                    },
                    onOpenAccessibilitySettings = ::openAccessibilitySettings,
                    onClose = ::finish,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        temperature.start()
    }

    override fun onStop() {
        temperature.stop()
        super.onStop()
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.w("StatusBarTemp", "No accessibility settings screen", e)
        }
    }

    companion object {
        /** Whether [StatusBarTempService] is switched on in the Android accessibility settings. */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, StatusBarTempService::class.java)
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
