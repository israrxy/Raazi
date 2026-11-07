package com.israrxy.raazi.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.israrxy.raazi.ui.SelectionDialog
import com.israrxy.raazi.ui.SettingsActionItem
import com.israrxy.raazi.ui.SettingsDivider
import com.israrxy.raazi.ui.SettingsSection
import com.israrxy.raazi.ui.SettingsSelectItem
import com.israrxy.raazi.ui.theme.Emerald500
import com.israrxy.raazi.viewmodel.AiSettingsUiState
import com.israrxy.raazi.viewmodel.AiSettingsViewModel

@Composable
internal fun SettingsAiSection(
    viewModel: AiSettingsViewModel,
    onOpenGuide: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }

    SettingsSection(title = "Gemini API") {
        SettingsActionItem(
            icon = Icons.Filled.Key,
            title = "API key",
            subtitle = if (state.hasKey) {
                "Connected on this device · ${state.model}"
            } else {
                "Not set · add your own free key"
            },
            onClick = {
                viewModel.clearStatus()
                showKeyDialog = true
            }
        )
        SettingsDivider()
        SettingsSelectItem(
            icon = Icons.Filled.Memory,
            title = "Vision & audio model",
            value = state.model,
            onClick = { showModelDialog = true }
        )
        SettingsDivider()
        SettingsActionItem(
            icon = Icons.Filled.AutoAwesome,
            title = "How to get a free key",
            subtitle = "Step-by-step guide · no credit card",
            onClick = onOpenGuide
        )
        if (state.hasKey) {
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.CheckCircle,
                title = "Test connection",
                subtitle = if (state.isBusy) "Checking…" else "Verify the saved key and model",
                onClick = viewModel::testSavedKey
            )
            SettingsDivider()
            SettingsActionItem(
                icon = Icons.Filled.DeleteOutline,
                title = "Remove key",
                subtitle = "Delete it from this device",
                isDestructive = true,
                onClick = viewModel::removeKey
            )
        }
    }

    StatusMessage(state = state)

    SettingsSection(title = "Privacy") {
        Text(
            text = "Your key is encrypted with Android Keystore and stays on this device. " +
                "It is never synced to Raazi Drive or stored in the app's normal settings. " +
                "A photo or audio clip is sent directly to Google Gemini only when you start " +
                "a search. Google's free tier limits apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }

    if (showKeyDialog) {
        GeminiKeyDialog(
            state = state,
            onDismiss = {
                showKeyDialog = false
                viewModel.clearStatus()
            },
            onSaveAndTest = viewModel::saveAndTest
        )
    }

    if (showModelDialog) {
        SelectionDialog(
            title = "Vision model",
            options = MODEL_OPTIONS,
            selectedOption = state.model,
            onOptionSelected = { model ->
                viewModel.setModel(model)
                showModelDialog = false
            },
            onDismiss = { showModelDialog = false },
            descriptions = MODEL_DESCRIPTIONS
        )
    }
}

@Composable
private fun StatusMessage(state: AiSettingsUiState) {
    val message = state.message ?: state.error ?: return
    val isError = state.error != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = if (isError) MaterialTheme.colorScheme.error else Emerald500,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GeminiKeyDialog(
    state: AiSettingsUiState,
    onDismiss: () -> Unit,
    onSaveAndTest: (String) -> Unit
) {
    var keyInput by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val success = state.message != null && state.error == null

    AlertDialog(
        onDismissRequest = { if (!state.isBusy) onDismiss() },
        title = {
            Text(
                text = if (state.hasKey && !success) "Replace Gemini key" else "Gemini API key",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste the key from Google AI Studio. It is stored only on this device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it.take(200) },
                    label = { Text("API key") },
                    placeholder = { Text("AIza…") },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (visible) "Hide key" else "Show key"
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (keyInput.isNotBlank() && !state.isBusy) {
                                onSaveAndTest(keyInput)
                            }
                        }
                    ),
                    enabled = !state.isBusy && !success,
                    modifier = Modifier.fillMaxWidth()
                )
                StateLine(state = state)
            }
        },
        confirmButton = {
            if (success) {
                TextButton(onClick = onDismiss) { Text("Done") }
            } else {
                Button(
                    onClick = { onSaveAndTest(keyInput) },
                    enabled = keyInput.isNotBlank() && !state.isBusy
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (state.isBusy) "Testing…" else "Save & test")
                }
            }
        },
        dismissButton = {
            if (!success) {
                TextButton(onClick = onDismiss, enabled = !state.isBusy) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun StateLine(state: AiSettingsUiState) {
    val message = state.error ?: state.message ?: return
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (state.error != null) MaterialTheme.colorScheme.error else Emerald500
    )
    if (state.error != null && state.hasKey) {
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "The key was saved; you can fix or remove it in Settings.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val MODEL_OPTIONS = listOf(
    "gemini-3.5-flash-lite",
    "gemini-2.5-flash",
    "gemini-2.5-flash-lite"
)

private val MODEL_DESCRIPTIONS = mapOf(
    "gemini-3.5-flash-lite" to "Default. Fast vision with higher free limits.",
    "gemini-2.5-flash" to "Widely available free-tier fallback.",
    "gemini-2.5-flash-lite" to "Fastest with the highest free limits, less accurate."
)
