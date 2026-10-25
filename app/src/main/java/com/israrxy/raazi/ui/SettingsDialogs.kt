package com.israrxy.raazi.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.israrxy.raazi.data.local.SettingsDataStore
import com.israrxy.raazi.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SettingsClearCacheDialog(
    context: Context,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Clear Cache?", fontWeight = FontWeight.SemiBold) },
        text = { Text("Cached thumbnails and temporary streams will be removed. Downloaded tracks are kept.") },
        confirmButton = {
            TextButton(
                onClick = {
                    runCatching { context.cacheDir.deleteRecursively() }
                    Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("Clear")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SettingsResetDialog(
    context: Context,
    scope: CoroutineScope,
    settingsDataStore: SettingsDataStore,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Settings?", fontWeight = FontWeight.SemiBold) },
        text = { Text("Restore all preferences to their default values?") },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        settingsDataStore.clearAll()
                        Toast.makeText(context, "Settings reset", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun SettingsGeminiApiKeyDialog(
    geminiApiKey: String?,
    scope: CoroutineScope,
    settingsDataStore: SettingsDataStore,
    onDismiss: () -> Unit
) {
    var keyInput by remember(geminiApiKey) { mutableStateOf(geminiApiKey ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gemini API Key", fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text("API Key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch { settingsDataStore.setGeminiApiKey(keyInput.trim()) }
                    onDismiss()
                }
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

@Composable
internal fun SettingsLastFmLoginDialog(
    viewModel: MusicPlayerViewModel,
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoggingIn by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoggingIn) onDismiss() },
        title = { Text("Sign in to Last.fm", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !isLoggingIn && username.isNotBlank() && password.isNotBlank(),
                onClick = {
                    isLoggingIn = true
                    errorMessage = null
                    scope.launch {
                        val success = viewModel.connectLastFm(username.trim(), password)
                        isLoggingIn = false
                        if (success) {
                            settingsDataStore.setLastfmScrobbleEnabled(true)
                            onDismiss()
                        } else {
                            errorMessage = "Invalid credentials or network error"
                        }
                    }
                }
            ) {
                if (isLoggingIn) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Sign In")
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isLoggingIn,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}
