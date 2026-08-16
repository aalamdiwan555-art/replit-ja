package com.autopilot.app

import android.content.Context
import android.os.SystemClock
import kotlin.math.max
import java.util.Locale
import java.util.UUID

class SecureStorage(context: Context) {
    private val preferences =
        context.getSharedPreferences("autopilot_secure_state", Context.MODE_PRIVATE)

    init {
        if (!preferences.contains(KEY_STATUS)) {
            preferences.edit()
                .putString(KEY_STATUS, UserStatus.TRIAL.name)
                .putString(KEY_ID, generateUid())
                .putLong(KEY_TRIAL_STARTED_ELAPSED, SystemClock.elapsedRealtime())
                .putInt(KEY_REWARD_COUNT, 0)
                .apply()
        } else if (!preferences.contains(KEY_ID)) {
            preferences.edit().putString(KEY_ID, generateUid()).apply()
        }
    }

    @Synchronized
    fun getUser(networkTimeMillis: Long? = null): User {
        var status = readStatus()
        val expiry = preferences.getLong(KEY_EXPIRY, 0L)
        var trialStartedNetwork = preferences.getLong(KEY_TRIAL_STARTED_NETWORK, 0L)
        val trialStartedElapsed = preferences.getLong(KEY_TRIAL_STARTED_ELAPSED, 0L)
        val elapsedNow = SystemClock.elapsedRealtime()

        if (status == UserStatus.APPROVED && expiry == 0L) {
            status = UserStatus.LIFETIME
            preferences.edit().putString(KEY_STATUS, status.name).apply()
        }

        if (status == UserStatus.TRIAL && trialStartedNetwork == 0L && networkTimeMillis != null) {
            if (trialStartedElapsed > 0L && elapsedNow >= trialStartedElapsed) {
                trialStartedNetwork = networkTimeMillis - (elapsedNow - trialStartedElapsed)
                preferences.edit().putLong(KEY_TRIAL_STARTED_NETWORK, trialStartedNetwork).apply()
            } else {
                status = UserStatus.EXPIRED
                preferences.edit().putString(KEY_STATUS, status.name).apply()
            }
        }

        val trialExpiry = if (trialStartedNetwork > 0L) {
            trialStartedNetwork + TRIAL_MILLIS
        } else {
            trialStartedElapsed + TRIAL_MILLIS
        }

        if (status == UserStatus.TRIAL) {
            val trialExpired = if (trialStartedNetwork > 0L && networkTimeMillis != null) {
                networkTimeMillis >= trialExpiry
            } else {
                trialStartedElapsed <= 0L ||
                    elapsedNow < trialStartedElapsed ||
                    elapsedNow - trialStartedElapsed >= TRIAL_MILLIS
            }
            if (trialExpired) {
                status = UserStatus.EXPIRED
                preferences.edit().putString(KEY_STATUS, status.name).apply()
            }
        }

        if (status == UserStatus.APPROVED &&
            expiry > 0L &&
            networkTimeMillis != null &&
            networkTimeMillis >= expiry
        ) {
            status = UserStatus.EXPIRED
            preferences.edit().putString(KEY_STATUS, status.name).apply()
        }

        val timeValidated = when (status) {
            UserStatus.LIFETIME, UserStatus.PENDING_APPROVAL,
            UserStatus.REJECTED, UserStatus.EXPIRED -> true
            UserStatus.TRIAL -> trialStartedNetwork > 0L && networkTimeMillis != null ||
                trialStartedNetwork == 0L && trialStartedElapsed > 0L &&
                elapsedNow >= trialStartedElapsed
            UserStatus.APPROVED -> expiry == 0L || networkTimeMillis != null
        }

        val effectiveExpiry = if (status == UserStatus.TRIAL) trialExpiry else expiry
        return User(
            id = preferences.getString(KEY_ID, "local-user") ?: "local-user",
            name = preferences.getString(KEY_NAME, "AUTOPILOT user") ?: "AUTOPILOT user",
            email = preferences.getString(KEY_EMAIL, "user@autopilot.app") ?: "user@autopilot.app",
            status = status,
            expiryTimestamp = effectiveExpiry,
            adFreeOverride = preferences.getBoolean(KEY_AD_FREE, false),
            rewardAdsCompleted = preferences.getInt(KEY_REWARD_COUNT, 0),
            rewardSessionStartedAt = preferences.getLong(KEY_REWARD_SESSION, 0L),
            timeValidated = timeValidated,
        )
    }

    fun saveUser(status: UserStatus, expiryTimestamp: Long) {
        preferences.edit()
            .putString(KEY_STATUS, status.name)
            .putLong(KEY_EXPIRY, expiryTimestamp)
            .apply()
    }

    fun approveForDays(days: Int, networkTimeMillis: Long?): Boolean {
        if (days <= 0 || networkTimeMillis == null) return false
        saveUser(UserStatus.APPROVED, networkTimeMillis + days * DAY_MILLIS)
        return true
    }

    fun approveLifetime() {
        saveUser(UserStatus.LIFETIME, 0L)
    }

    fun extendByDays(networkTimeMillis: Long?): Boolean {
        if (networkTimeMillis == null) return false
        val current = getUser(networkTimeMillis)
        if (current.status == UserStatus.LIFETIME) return false
        val base = max(networkTimeMillis, current.expiryTimestamp)
        saveUser(UserStatus.APPROVED, base + DAY_MILLIS)
        preferences.edit()
            .putInt(KEY_REWARD_COUNT, 0)
            .remove(KEY_REWARD_SESSION)
            .apply()
        return true
    }

    fun setAdFreeOverride(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_AD_FREE, enabled).apply()
    }

    fun setAdFreeForUid(uid: String, enabled: Boolean): Boolean {
        val normalized = uid.trim().uppercase(Locale.US)
        val currentUid = preferences.getString(KEY_ID, "")?.uppercase(Locale.US)
        if (normalized.isBlank() || normalized != currentUid) return false
        setAdFreeOverride(enabled)
        return true
    }

    fun reject() {
        saveUser(UserStatus.REJECTED, 0L)
    }

    fun beginRewardSession(networkTimeMillis: Long?): Boolean {
        if (networkTimeMillis == null || preferences.getLong(KEY_REWARD_SESSION, 0L) > 0L) {
            return false
        }
        preferences.edit().putLong(KEY_REWARD_SESSION, networkTimeMillis).apply()
        return true
    }

    fun completeRewardSession(networkTimeMillis: Long?): RewardResult {
        if (networkTimeMillis == null) return RewardResult(false, false, rewardCount())
        val startedAt = preferences.getLong(KEY_REWARD_SESSION, 0L)
        if (startedAt <= 0L) return RewardResult(false, false, rewardCount())
        if (networkTimeMillis - startedAt < MIN_REWARD_SESSION_MILLIS) {
            return RewardResult(false, false, rewardCount())
        }

        val nextCount = rewardCount() + 1
        preferences.edit().remove(KEY_REWARD_SESSION).apply()
        if (nextCount < REWARD_TARGET) {
            preferences.edit().putInt(KEY_REWARD_COUNT, nextCount).apply()
            return RewardResult(true, false, nextCount)
        }

        val rewarded = extendByDays(networkTimeMillis)
        return RewardResult(true, rewarded, 0)
    }

    fun hasPendingRewardSession(): Boolean = preferences.getLong(KEY_REWARD_SESSION, 0L) > 0L

    private fun rewardCount(): Int = preferences.getInt(KEY_REWARD_COUNT, 0)

    private fun readStatus(): UserStatus =
        preferences.getString(KEY_STATUS, UserStatus.TRIAL.name)
            ?.let { runCatching { UserStatus.valueOf(it) }.getOrDefault(UserStatus.TRIAL) }
            ?: UserStatus.TRIAL

    data class RewardResult(
        val completed: Boolean,
        val rewarded: Boolean,
        val count: Int,
    )

    private companion object {
        const val KEY_ID = "user_id"
        const val KEY_NAME = "user_name"
        const val KEY_EMAIL = "user_email"
        const val KEY_STATUS = "user_status"
        const val KEY_EXPIRY = "expiry_timestamp"
        const val KEY_AD_FREE = "admin_ad_free"
        const val KEY_REWARD_COUNT = "reward_ad_count"
        const val KEY_REWARD_SESSION = "reward_session_started"
        const val KEY_TRIAL_STARTED_NETWORK = "trial_started_network"
        const val KEY_TRIAL_STARTED_ELAPSED = "trial_started_elapsed"
        const val TRIAL_MILLIS = 60L * 60L * 1000L
        const val MIN_REWARD_SESSION_MILLIS = 25_000L
        const val REWARD_TARGET = 10
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L

        fun generateUid(): String =
            "AP-" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase(Locale.US)
    }
}