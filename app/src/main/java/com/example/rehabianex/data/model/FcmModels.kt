package com.example.rehabianex.data.model

import com.google.gson.annotations.SerializedName

data class FcmTokenRegistrationRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,
    val platform: String = "android",
    @SerializedName("device_id")
    val deviceId: String? = null,
    @SerializedName("app_version")
    val appVersion: String,
    val timezone: String,
    val locale: String,
)

data class FcmTokenRevocationRequest(
    @SerializedName("fcm_token")
    val fcmToken: String,
)

data class FcmDemoRequest(
    val type: String,
)

data class FcmDemoResponse(
    val ok: Boolean = false,
    val sent: Boolean = false,
    val type: String? = null,
    val message: String? = null,
    @SerializedName("notification_id")
    val notificationId: String? = null,
)
