package com.israrxy.raazi.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.israrxy.raazi.BuildConfig

internal fun launchFeedbackEmail(context: Context) {
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_SENDTO,
                Uri.parse("mailto:feedback@israrxy.qzz.io")
            ).apply {
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Raazi feedback (v${BuildConfig.VERSION_NAME})"
                )
            }
        )
    }.onFailure {
        Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
    }
}
