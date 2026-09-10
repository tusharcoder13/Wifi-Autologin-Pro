# ProGuard / R8 rules for WiFi AutoLogin Pro
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class org.jsoup.** { *; }
