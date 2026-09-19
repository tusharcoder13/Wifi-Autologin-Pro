# ProGuard / R8 rules for WiFi AutoLogin Pro
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Gson reflection and data models
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.wifi.autologin.data.model.** { *; }
-keep class com.wifi.autologin.data.repository.AppSettings { *; }
-keep class com.wifi.autologin.network.AuthResult { *; }
-keep class com.wifi.autologin.network.ProbeResult { *; }
-keep class com.wifi.autologin.network.UpdateManager { *; }

# Jsoup HTML parser
-keep class org.jsoup.** { *; }
-dontwarn org.jspecify.**

# OkHttp3 & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# AndroidX Security Crypto
-keep class androidx.security.crypto.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

