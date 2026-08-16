package com.autopilot.app

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Supplies a network-derived wall clock and advances it with monotonic uptime
 * between syncs. A stale device wall clock is never used for entitlements.
 */
class NetworkTimeProvider(context: Context) {
    private val preferences =
        context.getSharedPreferences("autopilot_network_time", Context.MODE_PRIVATE)

    fun currentTimeMillis(): Long? {
        val networkAnchor = preferences.getLong(KEY_NETWORK_ANCHOR, 0L)
        val elapsedAnchor = preferences.getLong(KEY_ELAPSED_ANCHOR, 0L)
        val elapsedNow = SystemClock.elapsedRealtime()
        if (networkAnchor <= 0L || elapsedAnchor <= 0L || elapsedNow < elapsedAnchor) {
            return null
        }
        return networkAnchor + (elapsedNow - elapsedAnchor)
    }

    suspend fun sync(): Long? {
        val networkTime = withContext(Dispatchers.IO) { requestNetworkTime() } ?: return currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()
        preferences.edit()
            .putLong(KEY_NETWORK_ANCHOR, networkTime)
            .putLong(KEY_ELAPSED_ANCHOR, elapsedNow)
            .apply()
        return networkTime
    }

    private fun requestNetworkTime(): Long? {
        val connection = (URL(TIME_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = 5_000
            readTimeout = 5_000
            useCaches = false
            instanceFollowRedirects = true
        }
        return try {
            connection.connect()
            connection.getHeaderField("Date")?.let { value ->
                SimpleDateFormat(DATE_FORMAT, Locale.US).parse(value)?.time
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIME_URL = "https://www.google.com/generate_204"
        const val DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss z"
        const val KEY_NETWORK_ANCHOR = "network_time_anchor"
        const val KEY_ELAPSED_ANCHOR = "elapsed_time_anchor"
    }
}