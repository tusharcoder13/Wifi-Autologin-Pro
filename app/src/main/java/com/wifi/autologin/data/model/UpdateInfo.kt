package com.wifi.autologin.data.model

import com.google.gson.annotations.SerializedName

data class UpdateInfo(
    @SerializedName("versionCode")
    val versionCode: Int = 1,
    @SerializedName("versionName")
    val versionName: String = "1.0.0",
    @SerializedName("releaseNotes")
    val releaseNotes: String = "",
    @SerializedName("downloadUrl")
    val downloadUrl: String = "https://tinyurl.com/WifiAutologin-Pro"
)
