package com.autopilot.app

import android.content.Context

class SecureStorage(context: Context) {
    private val preferences = context.getSharedPreferences("autopilot_secure_state", Context.MODE_PRIVATE)

    fun getUser(): User {
        val storedStatus = preferences.getString(KEY_STATUS, UserStatus.PENDING_APPROVAL.name)
            ?.let { runCatching { UserStatus.valueOf(it) }.getOrDefault(UserStatus.PENDING_APPROVAL) }
            ?: UserStatus.PENDING_APPROVAL
        val expiry = preferences.getLong(KEY_EXPIRY, 0L)
        val currentStatus = if (storedStatus == UserStatus.APPROVED && expiry > 0L && System.currentTimeMillis() >= expiry) {
            UserStatus.EXPIRED
        } else {
            storedStatus
        }
        if (currentStatus != storedStatus) {
            saveUser(currentStatus, expiry)
        }
        return User(
            id = preferences.getString(KEY_ID, "local-user") ?: "local-user",
            name = preferences.getString(KEY_NAME, "AUTOPILOT user") ?: "AUTOPILOT user",
            email = preferences.getString(KEY_EMAIL, "user@autopilot.app") ?: "user@autopilot.app",
            status = currentStatus,
            expiryTimestamp = expiry,
        )
    }

    fun saveUser(status: UserStatus, expiryTimestamp: Long) {
        preferences.edit()
            .putString(KEY_STATUS, status.name)
            .putLong(KEY_EXPIRY, expiryTimestamp)
            .apply()
    }

    fun approveForDays(days: Int) {
        val expiry = System.currentTimeMillis() + days * DAY_MILLIS
        saveUser(UserStatus.APPROVED, expiry)
    }

    fun approveLifetime() {
        saveUser(UserStatus.APPROVED, 0L)
    }

    fun extendByDays(days: Int = 1) {
        val current = getUser()
        val base = maxOf(System.currentTimeMillis(), current.expiryTimestamp)
        saveUser(UserStatus.APPROVED, base + days * DAY_MILLIS)
    }

    fun reject() {
        saveUser(UserStatus.REJECTED, 0L)
    }

    private companion object {
        const val KEY_ID = "user_id"
        const val KEY_NAME = "user_name"
        const val KEY_EMAIL = "user_email"
        const val KEY_STATUS = "user_status"
        const val KEY_EXPIRY = "expiry_timestamp"
        const val DAY_MILLIS = 24L * 60L * 60L * 1000L
    }
}