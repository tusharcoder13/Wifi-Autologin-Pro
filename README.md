# WiFi AutoLogin Pro (Android)

**WiFi AutoLogin Pro** is a high-performance, native Android utility (built with **Kotlin** and **Jetpack Compose Material 3**) that automatically detects Wi-Fi captive portal login screens (college campuses, hostels, hotels, offices, and public hotspots) and securely submits your saved credentials in the background — eliminating manual sign-in prompts forever.

[![Download APK](https://img.shields.io/badge/Download-Official%20APK%20(1.70%20MB)-0D9488?style=for-the-badge&logo=android&logoColor=white)](https://tinyurl.com/WifiAutologin-Pro)
[![GitHub stars](https://img.shields.io/github/stars/tusharcoder13/Wifi-Autologin-Pro?style=for-the-badge&color=teal)](https://github.com/tusharcoder13/Wifi-Autologin-Pro)
[![Android Version](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://tinyurl.com/WifiAutologin-Pro)
[![Security](https://img.shields.io/badge/Security-AES--256--GCM%20KeyStore-blue?style=for-the-badge&logo=shield&logoColor=white)](https://github.com/tusharcoder13/Wifi-Autologin-Pro)

📥 **Official Download Link**: [https://tinyurl.com/WifiAutologin-Pro](https://tinyurl.com/WifiAutologin-Pro)

---

## 🌟 What's New in Latest Release (v1.0.0 Hardened)

- 💬 **Live In-App Feedback & Reaction System**:
  - Direct feedback form in **Settings** with 5-star reaction picker (😍 / 😊 / 😐 / 😕 / 😞) and categories (⚡ Speed, 💡 Suggestion, 🐛 Bug, ❤️ Compliment).
  - Strict 10-digit numeric phone number validation with real-time length tracking.
  - Automatic real-time dispatch to developer Google Sheets via resilient HTTP webhook.
- 🔒 **Enterprise-Grade KeyStore AES-256 Encryption**:
  - Replaced plain text storage with **AndroidX KeyStore AES-256-GCM** `EncryptedSharedPreferences`.
  - Backup protection: ADB and cloud backups disabled (`android:allowBackup="false"`) to prevent credential extraction.
- ⚡ **Single-Shot Login Engine (<300ms)**:
  - Eliminates multi-attempt conflicts and prevents device lockouts.
  - Automatic backup account failover with 30s smart cooldown guard.
- 🔄 **In-App Auto-Update System**:
  - Automatically checks GitHub for latest releases with in-app changelog display and 1-tap download.
- 🪶 **Ultra-Compact APK (1.70 MB)**:
  - Full R8 ProGuard code obfuscation and resource shrinking for lightning-fast performance and minimal battery footprint.

---

## 🎯 The Problem It Solves

On Android devices, connecting to a captive portal Wi-Fi network triggers the system alert: *"Sign in to Wi-Fi network"*. 
- Tapping that notification opens Android's restricted webview (`CaptivePortalLoginActivity`), where Google Chrome autofill, password managers, and copy-paste often fail.
- Users are forced to manually type their Wi-Fi ID and password repeatedly on reconnect or session expiration.

**WiFi AutoLogin Pro** runs as a battery-efficient background monitor, captures the captive portal page, auto-fills the credentials, and submits the login form in **less than 300ms**!

---

## 🚀 Key Features

- ⚡ **Instant Background Auto-Login**: Listens for Wi-Fi connections via Android `NetworkCallback`, probes for captive portals (`generate_204`), and executes authentication automatically.
- 🔍 **Intelligent HTML Form Parser**: Automatically parses login forms (Jsoup engine), detects input fields (`username`, `id`, `user`, `password`), and extracts hidden CSRF / session tokens.
- 🎯 **Pre-configured Portal Presets**:
  - Standard HTML Web Portal
  - Fortinet / FortiGate (Ports 1000 / 1003)
  - Sophos / Cyberoam (Port 8090)
  - MikroTik RouterOS Hotspot (`/login`)
  - Cisco Meraki & Aruba ClearPass
- 🔄 **Multi-Account Smart Failover**: Automatically switches to backup login profiles if primary account reaches maximum limit.
- 💓 **Heartbeat Keep-Alive Daemon**: Sends periodic background probes to prevent session timeout due to inactivity.
- 🔎 **Built-in Portal Inspector**: Test any portal URL live, view extracted form fields, and save profiles with 1 tap.
- 📊 **Audit Logs & Telemetry**: Full connection logs showing latency, HTTP response codes, and status history.
- 🎛️ **Quick Settings Status Bar Tile**: 1-tap manual login from anywhere on Android.
- 🔒 **AndroidX KeyStore Vault**: All user credentials encrypted at rest using AES-256-GCM hardware-backed keystore.

---

## 📂 Project Structure

```
Wifi-Autologin-Pro/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── res/
│       │   ├── xml/ (backup_rules.xml, data_extraction_rules.xml)
│       │   └── ...
│       └── java/com/wifi/autologin/
│           ├── MainActivity.kt
│           ├── WifiAutoLoginApp.kt
│           ├── data/
│           │   ├── model/ (WifiProfile, PortalPreset, LogEntry, ConnectionStatus, FeedbackPayload, UpdateInfo)
│           │   └── repository/ (ProfileRepository, AppSettingsRepository, LogRepository)
│           ├── network/
│           │   ├── CaptivePortalDetector.kt (204 probes & latency checks)
│           │   ├── HtmlFormParser.kt (Jsoup-based form token parsing)
│           │   ├── AuthEngine.kt (OkHttp3 multi-method login dispatcher)
│           │   ├── UpdateManager.kt (GitHub remote version checker)
│           │   └── FeedbackManager.kt (Google Sheets webhook dispatcher)
│           ├── service/
│           │   ├── WifiMonitorService.kt (Foreground Service)
│           │   ├── KeepAliveWorker.kt (WorkManager heartbeat)
│           │   ├── BootReceiver.kt (Auto-start on device boot)
│           │   └── QuickLoginTileService.kt (Android Quick Settings Tile)
│           └── ui/
│               ├── theme/ (Color, Theme, Type)
│               ├── components/ (AppBottomNav, StatusCard, FeedbackDialog, UpdateDialog, PrivacyDialog)
│               ├── screens/ (DashboardScreen, ProfilesScreen, PortalInspectorScreen, LogsScreen, SettingsScreen)
│               └── viewmodel/ (MainViewModel)
├── version.json (Remote update descriptor)
├── WiFi_AutoLogin_Pro.apk (Latest production signed APK - 1.70 MB)
├── build.gradle.kts
├── settings.gradle.kts
└── gradlew.bat
```

---

## 📲 Direct APK Installation Guide

Compatible with **Android 8.0 through Android 15 (API 26 to 35+)**:

### Step 1: Download the APK
Download the signed production release APK:
- **Direct Link**: [Download WiFi_AutoLogin_Pro.apk (1.70 MB)](https://tinyurl.com/WifiAutologin-Pro)

### Step 2: Install on Your Device
1. Open the downloaded `WiFi_AutoLogin_Pro.apk` from your **Downloads** or **File Manager**.
2. If prompted *"Allow installation from unknown sources"*, tap **Settings** $\rightarrow$ enable **Allow from this source**.
3. If Google Play Protect displays an unrecognized app warning:
   - Tap **"More details"** $\rightarrow$ Tap **"Install anyway"**.
   *(Standard for private sideloaded release builds signed with custom developer certificates).*

### Step 3: Setup & Permissions
1. **Open the App**:
   - **Location Permission**: Tap *Allow* (Required by Android OS to read the connected Wi-Fi SSID name).
   - **Notification Permission**: Tap *Allow* (For background status and login confirmation alerts).
2. **Battery Optimization (Essential for 24/7 Monitoring)**:
   - Go to phone **Settings > Apps > WiFi AutoLogin Pro > Battery** $\rightarrow$ Select **"Unrestricted"** (or *"Don't optimize"*).
3. **Add Wi-Fi Profile**:
   - Navigate to the **Profiles** tab $\rightarrow$ Tap **Add Profile**.
   - Enter your **Wi-Fi SSID** (e.g. `Campus_WiFi`), **Username / Wi-Fi ID**, and **Password**.
   - Select your portal preset (default: *Standard HTML Web Portal*).
   - Tap **Save Profile**.

---

## 🛠️ Build from Source

### Using Android Studio
1. Clone the repository:
   ```bash
   git clone https://github.com/tusharcoder13/Wifi-Autologin-Pro.git
   ```
2. Open the project folder in **Android Studio Hedgehog / Iguana / Ladybug+**.
3. Sync Gradle and click **Run (▶)** on your connected Android device.

### Using Command Line
```powershell
# Debug Build
.\gradlew.bat assembleDebug

# Production Obfuscated Release Build
.\gradlew.bat assembleRelease
```
The optimized release APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

---

## 📄 License & Attribution

- **Developer**: Tushar Sahu (<a href="https://www.linkedin.com/in/tusharsahu13" target="_blank" rel="noopener noreferrer">LinkedIn ↗</a>)
- **License**: Open Source for Educational & Personal Use.
