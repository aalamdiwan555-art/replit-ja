package com.autopilot.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object AdService {
    const val TARGET_URL =
        "https://www.effectivecpmnetwork.com/dj6smmv1qv?key=a099939295b1c6a336badd92e255267e"

    fun openUserInitiatedAd(context: Context): Boolean {
        val uri = Uri.parse(TARGET_URL)
        return runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
            true
        }.getOrElse {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            }.getOrDefault(false)
        }
    }
}