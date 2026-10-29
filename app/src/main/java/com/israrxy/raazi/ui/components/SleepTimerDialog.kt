package com.israrxy.raazi.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.service.SleepTimer

@Composable
fun SleepTimerDialog(
    onDismiss: () -> Unit,
    onTimerSet: (Int) -> Unit
) {
    val sleepTimer = SleepTimer.getInstance()
    val isActive by sleepTimer.isActive.collectAsState()
    val remainingTime by sleepTimer.remainingTimeMs.collectAsState()
    var customMinutesText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isActive) "Sleep Timer Active" else "Set Sleep Timer",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (isActive) {
                    Text(
                        text = "Playback will pause in:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val totalSec = (remainingTime / 1000).coerceAtLeast(0L)
                    val formatted = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("sleep_timer_countdown")
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            sleepTimer.cancel()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sleep_timer_cancel_button")
                    ) {
                        Text("Cancel Timer")
                    }
                } else {
                    Text(
                        text = "Pause playback after:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SleepTimer.PRESET_DURATIONS.forEach { minutes ->
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sleep_timer_${minutes}m_button")
                                    .clickable { onTimerSet(minutes) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "$minutes",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutesText,
                            onValueChange = { input ->
                                customMinutesText = input.filter { it.isDigit() }.take(3)
                            },
                            label = { Text("Custom (min)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sleep_timer_custom_input")
                        )
                        Button(
                            onClick = {
                                val customMin = customMinutesText.toIntOrNull()
                                if (customMin != null && customMin > 0) {
                                    onTimerSet(customMin)
                                }
                            },
                            enabled = (customMinutesText.toIntOrNull() ?: 0) > 0,
                            modifier = Modifier.testTag("sleep_timer_custom_start_button")
                        ) {
                            Text("Start")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
