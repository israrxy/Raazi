package com.israrxy.raazi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.ui.components.GlassBox
import com.israrxy.raazi.viewmodel.EqualizerToneGroup
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlin.math.roundToInt

@Composable
internal fun EqualizerToneControls(
    state: MusicPlayerViewModel.EqualizerState,
    onToneChange: (EqualizerToneGroup, Int) -> Unit
) {
    GlassBox(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tone",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Broad bass, mid and treble shaping",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = {
                        onToneChange(EqualizerToneGroup.BASS, 0)
                        onToneChange(EqualizerToneGroup.MID, 0)
                        onToneChange(EqualizerToneGroup.TREBLE, 0)
                    }
                ) {
                    Text("Reset")
                }
            }

            Spacer(Modifier.height(6.dp))

            ToneSlider(
                label = "Bass",
                gainDb = state.toneBassDb,
                accent = MaterialTheme.colorScheme.primary,
                onGainChange = { onToneChange(EqualizerToneGroup.BASS, it) }
            )
            ToneSlider(
                label = "Mid",
                gainDb = state.toneMidDb,
                accent = MaterialTheme.colorScheme.secondary,
                onGainChange = { onToneChange(EqualizerToneGroup.MID, it) }
            )
            ToneSlider(
                label = "Treble",
                gainDb = state.toneTrebleDb,
                accent = MaterialTheme.colorScheme.tertiary,
                onGainChange = { onToneChange(EqualizerToneGroup.TREBLE, it) }
            )
        }
    }
}

@Composable
private fun ToneSlider(
    label: String,
    gainDb: Int,
    accent: Color,
    onGainChange: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = when {
                    gainDb > 0 -> "+$gainDb dB"
                    else -> "$gainDb dB"
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
        Slider(
            value = gainDb.toFloat(),
            onValueChange = { onGainChange(it.roundToInt()) },
            valueRange = -12f..12f,
            steps = 23,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.18f)
            )
        )
    }
}
