package com.israrxy.raazi.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import com.israrxy.raazi.model.UpdateConfig

@Composable
fun UpdateDialog(
    config: UpdateConfig,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val onUpdateClick = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(config.downloadUrl))
        context.startActivity(intent)
    }

    // If forced, do not allow dismissal via back button or outside click
    val properties = if (config.forceUpdate) {
        DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    } else {
        DialogProperties()
    }

    // Handle back press explicitly to be sure
    if (config.forceUpdate) {
        BackHandler {
            // Do nothing, or maybe minimize app? 
            // Better to strictly block.
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!config.forceUpdate) {
                onDismiss()
            }
        },
        title = {
            Text(text = "Update Available")
        },
        text = {
            Text(
                text = "New version ${config.latestVersionName} is available.\n\n${config.releaseNotes}" +
                        if (config.forceUpdate) "\n\nThis update is required." else ""
            )
        },
        confirmButton = {
            Button(onClick = onUpdateClick) {
                Text(text = "Update Now")
            }
        },
        dismissButton = {
            if (!config.forceUpdate) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Later")
                }
            }
        },
        properties = properties
    )
}
