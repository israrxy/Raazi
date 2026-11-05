package com.israrxy.raazi.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.player.flac.HifiApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsLosslessDialog(
    initialUrl: String,
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var urlText by remember { mutableStateOf(initialUrl) }
    var testResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isTesting by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Lossless Audio Server",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Raazi automatically resolves Lossless FLAC streams through verified community mirrors. If you host your own HiFi instance (Docker/Render), enter its URL below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        testResult = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom Server URL") },
                    placeholder = { Text("e.g. https://my-hifi.domain.com") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val target = urlText.ifBlank { "https://hifi-api-bffw.onrender.com" }
                            isTesting = true
                            testResult = null
                            scope.launch {
                                val res = HifiApiClient.testConnection(target)
                                withContext(Dispatchers.Main) {
                                    testResult = res
                                    isTesting = false
                                }
                            }
                        },
                        enabled = !isTesting,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTesting) "Testing..." else "Test Connection")
                    }

                    if (urlText.isNotBlank()) {
                        TextButton(
                            onClick = {
                                urlText = ""
                                testResult = null
                            }
                        ) {
                            Text("Clear")
                        }
                    }
                }

                testResult?.let { (success, message) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.Error,
                            contentDescription = null,
                            tint = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = urlText.trim().trimEnd('/')
                    scope.launch {
                        settingsDataStore.setHifiApiUrl(cleanUrl)
                        HifiApiClient.customInstanceUrl = cleanUrl.takeIf { it.isNotBlank() }
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
