# WiFi AutoLogin Pro (Android)

**WiFi AutoLogin Pro** is a modern Native Android application (built with **Kotlin** and **Jetpack Compose Material 3**) that automatically detects Wi-Fi captive portal login screens (college campus, hostel, hotel, office, and public hotspot networks) and automatically submits your saved Wi-Fi ID and Password in the background.

[![Download APK](https://img.shields.io/badge/Download-Official%20APK-0D9488?style=for-the-badge&logo=android&logoColor=white)](https://tinyurl.com/WifiAutologin-Pro)
[![GitHub stars](https://img.shields.io/github/stars/tusharcoder13/Wifi-Autologin-Pro?style=for-the-badge&color=teal)](https://github.com/tusharcoder13/Wifi-Autologin-Pro)

📥 **Download Link**: [https://tinyurl.com/WifiAutologin-Pro](https://tinyurl.com/WifiAutologin-Pro)

---

## The Problem It Solves

On Android devices, connecting to a captive portal Wi-Fi network triggers the system notification: *"Sign in to Wi-Fi network"*. 
- Tapping that notification opens Android's restricted mini-browser (`CaptivePortalLoginActivity`), where Google Chrome autofill and password managers **do not work**.
- Users are forced to manually type their Wi-Fi ID / username / password every time they reconnect or their session expires.

**WiFi AutoLogin Pro** runs as a lightweight Android background service, captures the captive portal page, auto-fills the credentials, and submits the login form in **less than 1 second** — eliminating the manual sign-in prompt entirely!

---

## Features

- ⚡ **Background Auto-Login Engine**: Listens for Wi-Fi connections via Android `NetworkCallback`, probes for captive portals (`generate_204`), and executes authentication automatically.
- 🔍 **Intelligent HTML Form Parser**: Automatically parses HTML login pages, detects field names (e.g. `username`, `roll_no`, `id`, `password`), and extracts hidden CSRF / session tokens.
- 🎯 **Pre-configured Portal Presets**:
  - Standard HTML Web Portal
  - Fortinet / FortiGate (Ports 1000 / 1003)
  - Sophos / Cyberoam / Cyberores (Port 8090)
  - MikroTik RouterOS Hotspot (`/login`)
  - Cisco Meraki & Aruba ClearPass
- 💓 **Heartbeat Keep-Alive Daemon**: Sends periodic background pings to prevent Wi-Fi timeout/disconnection due to inactivity.
- 🔎 **Built-in Portal Inspector**: Test any portal URL live, view extracted form fields, and save profiles with 1 tap.
- 📊 **Real-time Traffic & Event Logs**: Full audit stream showing HTTP status codes, connection latencies, and server responses.
- 🎛️ **Quick Settings Tile**: Android status bar tile to trigger 1-tap manual login from anywhere.
- 🔒 **Encrypted Credential Vault**: Stored credentials are encrypted on the device.

---

## Project Structure

```
Wifi-Autologin-Pro/
├── app\
│   ├── build.gradle.kts
│   └── src\main\
│       ├── AndroidManifest.xml
│       ├── res\
│       └── java\com\wifi\autologin\
│           ├── MainActivity.kt
│           ├── WifiAutoLoginApp.kt
│           ├── data\
│           │   ├── model\ (WifiProfile, PortalPreset, LogEntry, ConnectionStatus)
│           │   └── repository\ (ProfileRepository, AppSettingsRepository, LogRepository)
│           ├── network\
│           │   ├── CaptivePortalDetector.kt (204 probes & latency check)
│           │   ├── HtmlFormParser.kt (Jsoup-based HTML form & token extraction)
│           │   └── AuthEngine.kt (OkHttp3 multi-method login dispatcher)
│           ├── service\
│           │   ├── WifiMonitorService.kt (Foreground Service)
│           │   ├── KeepAliveWorker.kt (WorkManager heartbeat)
│           │   ├── BootReceiver.kt (Auto-start on boot)
│           │   └── QuickLoginTileService.kt (Quick Settings Tile)
│           └── ui\
│               ├── theme\ (Color, Theme, Type)
│               ├── components\ (Status badges, Bottom Nav)
│               ├── screens\ (Dashboard, Profiles, Inspector, Logs, Settings)
│               └── viewmodel\ (MainViewModel)
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew.bat
```

---

## 📲 Direct APK Installation on Android Phones

You can install and use **WiFi AutoLogin Pro** directly on any Android phone (Android 8.0 to Android 15):

### Step 1: Download the APK
Download the pre-compiled, signed production release APK directly:
- **Direct Download**: [`WiFi_AutoLogin_Pro.apk`](WiFi_AutoLogin_Pro.apk) (11.47 MB)

### Step 2: Install the APK on Your Phone
1. Open the downloaded `WiFi_AutoLogin_Pro.apk` from your **Downloads** or **File Manager** (or WhatsApp/Telegram).
2. If Android prompts *"For your security, your phone is not allowed to install unknown apps from this source"*:
   - Tap **Settings** $\rightarrow$ Toggle ON **"Allow from this source"** $\rightarrow$ Tap **Install**.
3. If Google Play Protect shows *"Unrecognized app / Play Protect doesn't recognize this app's developer"*:
   - Tap **"More details"** (or the small arrow) $\rightarrow$ Tap **"Install anyway"**.
   *(This is standard for all sideloaded APKs signed with a private developer certificate).*

### Step 3: Initial Setup & Permissions
1. **Open the App**:
   - **Location Permission**: Tap *Allow* (Required by Android OS to retrieve the connected Wi-Fi SSID name, e.g., `KU-ROOM_16`).
   - **Notification Permission**: Tap *Allow* (Shows background monitoring status and 5-second login success alerts).
2. **Battery Optimization (Important for 24/7 Hands-Free Login)**:
   - Go to phone **Settings > Apps > WiFi AutoLogin Pro > Battery** $\rightarrow$ Select **"Unrestricted"** (or *"Don't optimize"*). This ensures Android doesn't kill the background monitor when the screen is locked.
3. **Add Your Wi-Fi Profile**:
   - Tap **Add Profile** on the Profiles screen.
   - Enter your **Wi-Fi SSID** (Network name), **Username / Wi-Fi ID**, and **Password**.
   - Select your portal preset (default: *Standard HTML Web Portal*, or *Sophos / Cyberoam*, *Fortinet*, *MikroTik*).
   - Tap **Save Profile**.

Now, whenever you connect to campus Wi-Fi, the app will authenticate instantly in the background!

---

## 🛠️ How to Build from Source (For Developers)

### Method 1: Android Studio (Recommended)
1. Open **Android Studio**.
2. Click **File > Open** and select this repository folder.
3. Allow Gradle to sync dependencies.
4. Connect your phone via USB (with **USB Debugging** enabled) and click **Run (▶)**.

### Method 2: Command Line Build
```powershell
# Debug APK
.\gradlew.bat assembleDebug

# Production Signed Release APK
.\gradlew.bat assembleRelease
```
The output APK will be generated at `app/build/outputs/apk/release/app-release.apk`.

