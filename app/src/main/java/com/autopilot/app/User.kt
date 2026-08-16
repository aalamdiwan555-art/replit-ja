package com.autopilot.app

enum class UserStatus {
    TRIAL,
    PENDING_APPROVAL,
    APPROVED,
    LIFETIME,
    EXPIRED,
    REJECTED,
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val status: UserStatus = UserStatus.TRIAL,
    val expiryTimestamp: Long = 0L,
    val adFreeOverride: Boolean = false,
    val rewardAdsCompleted: Int = 0,
    val rewardSessionStartedAt: Long = 0L,
    val timeValidated: Boolean = false,
) {
    val uid: String
        get() = id

    val hasActiveAccess: Boolean
        get() = timeValidated && status in setOf(UserStatus.TRIAL, UserStatus.APPROVED, UserStatus.LIFETIME)

    val shouldShowAds: Boolean
        get() = hasActiveAccess && !adFreeOverride && status != UserStatus.LIFETIME
}