package com.israrxy.raazi.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import androidx.compose.runtime.Composable

object DevInfo {
    const val NAME = "Md Israr Ahamed"
    const val HANDLE = "israrxy"
    const val PFP_URL = "https://raw.githubusercontent.com/israrxy/images/refs/heads/main/dev.png"
    const val INSTAGRAM_URL = "https://instagram.com/israrxy"
    const val GITHUB_URL = "https://github.com/israrxy"
    const val WEBSITE_URL = "https://israrxy.qzz.io"
}

fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/**
 * Daily "Follow me on Instagram" prompt. Shown on the first app open of each
 * day (which covers the first open right after onboarding completes).
 */
@Composable
fun FollowPromptDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp),
                    RoundedCornerShape(28.dp)
                )
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            // Developer pfp over an accent halo
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(accent.copy(alpha = 0.7f), Color.Transparent))
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = DevInfo.PFP_URL,
                    contentDescription = "Developer",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceDim)
                )
            }

            Text(
                text = "Follow me on Instagram",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = "${DevInfo.NAME} aka @${DevInfo.HANDLE}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            // Follow CTA
            Button(
                onClick = {
                    openLink(context, DevInfo.INSTAGRAM_URL)
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .padding(top = 20.dp)
                    .fillMaxWidth()
            ) {
                Text("Follow on Instagram", modifier = Modifier.padding(vertical = 4.dp))
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Secondary links — GitHub and website
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "GitHub · @${DevInfo.HANDLE}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { openLink(context, DevInfo.GITHUB_URL) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
                Text(
                    text = "Website · israrxy.qzz.io",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clickable { openLink(context, DevInfo.WEBSITE_URL) }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
