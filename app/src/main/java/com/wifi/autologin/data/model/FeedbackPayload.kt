package com.wifi.autologin.data.model

import com.google.gson.annotations.SerializedName

data class FeedbackPayload(
    @SerializedName("name")
    val name: String = "",
    @SerializedName("contact")
    val contact: String = "",
    @SerializedName("reaction")
    val reaction: String = "😍 Awesome (5★)",
    @SerializedName("category")
    val category: String = "⚡ Speed",
    @SerializedName("message")
    val message: String = "",
    @SerializedName("deviceModel")
    val deviceModel: String = "",
    @SerializedName("androidVersion")
    val androidVersion: String = "",
    @SerializedName("appVersion")
    val appVersion: String = "2.0.0",
    @SerializedName("ssid")
    val ssid: String = "",
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)
