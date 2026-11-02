package io.github.ulope.karoostatusbartemp

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val StatusFont = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

/** Temperature shown in the preview while the Karoo has not delivered a reading yet. */
private const val SAMPLE_CELSIUS = 18.4

@Composable
fun SettingsScreen(
    settings: TempSettings,
    serviceEnabled: Boolean,
    celsius: Double?,
    imperial: Boolean,
    onSettingsChange: (TempSettings) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Status Bar Temp", style = MaterialTheme.typography.titleLarge)

            SetupCard(serviceEnabled, onOpenAccessibilitySettings)

            Text(
                text = if (celsius != null) {
                    "Karoo temperature: " +
                        TemperatureFormat.format(celsius, imperial, decimals = true, showUnit = true)
                } else {
                    "Waiting for a temperature reading from the Karoo…"
                },
                style = MaterialTheme.typography.bodyMedium,
            )

            Text("Preview", style = MaterialTheme.typography.titleSmall)
            StatusRowPreview(settings, celsius ?: SAMPLE_CELSIUS, imperial)

            SwitchRow("Show temperature", settings.enabled) { onSettingsChange(settings.copy(enabled = it)) }
            SwitchRow("Show decimals", settings.showDecimals) { onSettingsChange(settings.copy(showDecimals = it)) }
            SwitchRow("Show unit (C / F)", settings.showUnit) { onSettingsChange(settings.copy(showUnit = it)) }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Text color", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                TextColor.entries.forEach { color ->
                    FilterChip(
                        selected = settings.textColor == color,
                        onClick = { onSettingsChange(settings.copy(textColor = color)) },
                        label = { Text(color.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }

            StepSlider("Size", settings.sizePercent, TempSettings.SIZE_RANGE, step = 5, unit = "%") {
                onSettingsChange(settings.copy(sizePercent = it))
            }
            StepSlider("Horizontal position", settings.offsetXDp, TempSettings.OFFSET_X_RANGE, step = 1, unit = "dp") {
                onSettingsChange(settings.copy(offsetXDp = it))
            }
            StepSlider("Vertical position", settings.offsetYDp, TempSettings.OFFSET_Y_RANGE, step = 1, unit = "dp") {
                onSettingsChange(settings.copy(offsetYDp = it))
            }

            Text(
                "The temperature is placed between the ride time and the battery level " +
                    "automatically. Use the sliders to fine-tune it on your device.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val defaults = TempSettings()
                    onSettingsChange(
                        settings.copy(
                            sizePercent = defaults.sizePercent,
                            offsetXDp = defaults.offsetXDp,
                            offsetYDp = defaults.offsetYDp,
                        ),
                    )
                }) { Text("Reset position") }
                Button(onClick = onClose) { Text("Done") }
            }
        }
    }
}

@Composable
private fun SetupCard(serviceEnabled: Boolean, onOpenAccessibilitySettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (serviceEnabled) Color(0xFFE3F2E5) else Color(0xFFFFF3E0),
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (serviceEnabled) {
                Text("✓ Accessibility service is on", fontWeight = FontWeight.Bold)
                Text(
                    "The temperature appears in the top bar of the ride screen.",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text("Turn on the accessibility service", fontWeight = FontWeight.Bold)
                Text(
                    "Karoo has no way for extensions to add items to the status bar. This app " +
                        "uses an accessibility service to find the ride time and battery level in " +
                        "the ride screen's top bar, then draws the temperature between them.\n\n" +
                        "Open the settings, select \"Status Bar Temp\" and switch it on. Android " +
                        "warns that the service can see the screen: it only reads the positions " +
                        "of the items in the top bar, never performs actions, and sends no data.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = onOpenAccessibilitySettings) { Text("Open accessibility settings") }
            }
        }
    }
}

/** A mock of the ride screen's top bar: ride time, temperature, battery level and clock. */
@Composable
private fun StatusRowPreview(settings: TempSettings, celsius: Double, imperial: Boolean) {
    val foreground = Color(settings.textColor.argb)
    val background = if (settings.textColor == TextColor.BLACK) Color.White else Color(0xFF1E1E1E)
    val temperature = TemperatureFormat.format(celsius, imperial, settings.showDecimals, settings.showUnit)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline)
            .background(background)
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("1:23:45", color = foreground, fontFamily = StatusFont, fontSize = 24.sp)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                if (settings.enabled) {
                    Text(
                        text = temperature,
                        color = foreground,
                        fontFamily = StatusFont,
                        fontSize = (17f * settings.sizePercent / 100f).sp,
                        modifier = Modifier.offset(x = settings.offsetXDp.dp, y = settings.offsetYDp.dp),
                    )
                }
            }
            Text("87%", color = foreground, fontFamily = StatusFont, fontSize = 17.sp)
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = 8.dp, height = 15.dp)
                    .background(foreground, RoundedCornerShape(1.dp)),
            )
            Spacer(Modifier.width(4.dp))
            Text("10:42", color = foreground, fontFamily = StatusFont, fontSize = 24.sp)
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A slider with − / + buttons for precise adjustment on a small touch screen. */
@Composable
private fun StepSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int,
    unit: String,
    onChange: (Int) -> Unit,
) {
    fun snap(raw: Float): Int = ((raw / step).roundToInt() * step).coerceIn(range)

    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("$value $unit", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−", fontSize = 20.sp) }
            Slider(
                value = value.toFloat(),
                onValueChange = { raw -> snap(raw).let { if (it != value) onChange(it) } },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+", fontSize = 20.sp) }
        }
    }
}

@Preview(widthDp = 320, heightDp = 900)
@Composable
private fun SettingsScreenPreview() {
    MaterialTheme {
        SettingsScreen(
            settings = TempSettings(),
            serviceEnabled = false,
            celsius = 21.3,
            imperial = false,
            onSettingsChange = {},
            onOpenAccessibilitySettings = {},
            onClose = {},
        )
    }
}
