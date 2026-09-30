package com.wifi.autologin.data.model

import com.google.gson.annotations.SerializedName

data class InstallPayload(
    @SerializedName("type")
    val type: String = "install",
    @SerializedName("installId")
    val installId: String = "",
    @SerializedName("event")
    val event: String = "NEW_INSTALL",
    @SerializedName("deviceModel")
    val deviceModel: String = "",
    @SerializedName("androidVersion")
    val androidVersion: String = "",
    @SerializedName("appVersion")
    val appVersion: String = "2.0.0",
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
