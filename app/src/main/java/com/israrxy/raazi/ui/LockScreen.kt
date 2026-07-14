package com.israrxy.raazi.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.util.concurrent.Executor

/**
 * Full-screen biometric lock screen.
 *
 * When biometrics are available it shows an "Unlock with biometrics" button that
 * launches an [BiometricPrompt]. When biometrics are unavailable (device has none
 * enrolled or no hardware) it falls back to a plain "Unlock" button so the user is
 * never permanently locked out of their own app.
 */
@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
                or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(96.dp)
                        .padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "App Locked",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Authenticate to continue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (biometricAvailable && activity != null) {
                Button(
                    onClick = {
                        val executor: Executor = ContextCompat.getMainExecutor(context)
                        val prompt = BiometricPrompt(
                            activity,
                            executor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(
                                    result: BiometricPrompt.AuthenticationResult
                                ) {
                                    super.onAuthenticationSucceeded(result)
                                    onUnlocked()
                                }
                            }
                        )
                        val info = BiometricPrompt.PromptInfo.Builder()
                            .setTitle("Unlock Raazi")
                            .setNegativeButtonText("Cancel")
                            .setAllowedAuthenticators(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                                    or BiometricManager.Authenticators.BIOMETRIC_WEAK
                            )
                            .build()
                        prompt.authenticate(info)
                    },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with biometrics", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onUnlocked,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Unlock", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Walks the [Context] wrapper chain to find the hosting [FragmentActivity].
 * Returns null when the context is not backed by an activity.
 */
private fun Context.findActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
