package com.example.data.subscription

import com.example.data.model.AccountType

enum class SubscriptionStatus {
    TRIAL,
    ACTIVE,
    EXPIRED,
    SUSPENDED,
    UNKNOWN;

    companion object {
        fun fromString(value: String?): SubscriptionStatus {
            return when (value?.lowercase()) {
                "trial" -> TRIAL
                "active" -> ACTIVE
                "expired" -> EXPIRED
                "suspended" -> SUSPENDED
                else -> UNKNOWN
            }
        }
    }
}

data class SubscriptionInfo(
    val status: SubscriptionStatus,
    val expiresAt: Long, // timestamp in ms
    val expiresAtFormatted: String,
    val serverTime: Long, // server clock in ms
    val planId: String?,
    val planName: String?,
    val signedToken: String?,
    val renewalUrl: String? = "https://mluona-iptv.com/renew",
    val isGracePeriodActive: Boolean = false
) {
    fun isPlaybackAllowed(clockSkewOffsetMs: Long): Boolean {
        if (status == SubscriptionStatus.SUSPENDED) return false
        val effectiveNow = System.currentTimeMillis() + clockSkewOffsetMs
        return when (status) {
            SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIAL -> effectiveNow < expiresAt
            else -> false
        }
    }
}

data class DeviceCodeInfo(
    val code: String, // 6 characters
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val qrPayload: String
)

data class ServerPlaylistDto(
    val id: String,
    val name: String,
    val type: AccountType,
    val serverUrl: String,
    val username: String,
    val password: String,
    val m3uUrl: String,
    val sortOrder: Int
)

data class RedeemResult(
    val success: Boolean,
    val message: String,
    val planName: String? = null,
    val expiresAt: Long? = null
)
