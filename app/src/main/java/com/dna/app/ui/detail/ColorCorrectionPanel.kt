package com.dna.app.ui.detail

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.dna.app.domain.color.ColorCorrection
import com.dna.app.domain.color.ColorPreset

@Composable
fun ColorCorrectionPanel(
    correction: ColorCorrection,
    neutralTapEnabled: Boolean,
    onCorrectionChange: (ColorCorrection) -> Unit,
    onNeutralTapToggle: (Boolean) -> Unit,
    onPreset: (ColorPreset) -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Colour", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ColorPreset.entries.forEach { preset ->
                AssistChip(
                    onClick = { onPreset(preset) },
                    label = { Text(preset.label) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        ColorSlider(
            label = "Brightness",
            value = correction.brightness,
            range = -1f..1f,
        ) { onCorrectionChange(correction.copy(brightness = it)) }
        ColorSlider(
            label = "Contrast",
            value = correction.contrast,
            range = -1f..1f,
        ) { onCorrectionChange(correction.copy(contrast = it)) }
        ColorSlider(
            label = "Saturation",
            value = correction.saturation,
            range = -1f..1f,
        ) { onCorrectionChange(correction.copy(saturation = it)) }
        ColorSlider(
            label = "Exposure (EV)",
            value = correction.exposureEv,
            range = -2f..2f,
        ) { onCorrectionChange(correction.copy(exposureEv = it)) }
        ColorSlider(
            label = "Temperature (K)",
            value = correction.temperatureK,
            range = 2000f..10000f,
            displayValue = { "%.0f K".format(it) },
        ) { onCorrectionChange(correction.copy(temperatureK = it)) }
        ColorSlider(
            label = "Tint",
            value = correction.tint,
            range = -1f..1f,
        ) { onCorrectionChange(correction.copy(tint = it)) }

        FilterChip(
            selected = neutralTapEnabled,
            onClick = { onNeutralTapToggle(!neutralTapEnabled) },
            label = { Text(if (neutralTapEnabled) "Tap a neutral area on the image" else "Neutral tap") },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onReset) { Text("Reset") }
            TextButton(onClick = onSave) { Text("Save") }
        }
    }
}

@Composable
private fun ColorSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: (Float) -> String = { "%.2f".format(it) },
    onChange: (Float) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(displayValue(value), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { stateDescription = displayValue(value) },
        )
    }
}
