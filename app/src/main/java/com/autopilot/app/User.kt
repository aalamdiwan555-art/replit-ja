package com.autopilot.app

enum class UserStatus {
    PENDING_APPROVAL,
    APPROVED,
    EXPIRED,
    REJECTED,
}

data class User(
    val id: String,
    val name: String,
    val email: String,
    val status: UserStatus = UserStatus.PENDING_APPROVAL,
    val expiryTimestamp: Long = 0L,
)