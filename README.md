# GCE Wi-Fi AutoLogin

A simple android app to remove the hastle of logging in everytime we use the hostel WiFi at GCE Gaya

GCE Wi-Fi AutoLogin is an Android application designed to automate the captive portal authentication process for the Hostel Wi-Fi network at Gaya Engineering College (GCE), Gaya. It replaces the repetitive manual process of Signing-In and submitting credentials every time a user connects to the network.

---

## Features

- **One-Tap Authentication**: Log in to the 24online captive portal directly from the app interface.
- **Quick Settings Tile**: Trigger network login directly from the Android notification shade without opening the app.
- **Home Screen Widget**: Compact 1x1 circular widget for quick access from the home screen.
- **Automated Network Promotion**: Notifies the Android operating system upon successful authentication to clear captive portal restrictions and enable system-wide internet access.
- **Version Enforcement**: Checks for mandatory application updates hosted on GitHub and prompts users when an update is required.
- **Minimalist Dark UI**: Pure black (`#000000`) theme designed for low power consumption on OLED screens.

---

## Architecture and Workflow

```
[ User Action: App / Tile / Widget ]
                 │
                 ▼
    [ Send POST Request to 172.16.16.16 ]
                 │
                 ├─► Success (HTTP 200)
                 │         │
                 │         ▼
                 │   [ Report Connectivity to OS ]
                 │         │
                 │         ▼
                 │   [ Google Domain Probe ]
                 │         │
                 │         ▼
                 │   [ Check App Updates ]
                 │
                 └─► Failure: Retry (up to 3 attempts)
```

1. **Authentication Request**: The app sends an HTTP POST request containing stored credentials to the portal gateway (`http://172.16.16.16/24online/servlet/E24onlineHTTPClient`).
2. **OS Network Promotion**: Upon receiving HTTP 200 OK, the app invokes `ConnectivityManager.reportNetworkConnectivity()` to notify the Android OS system monitor that internet access is active.
3. **Domain Probe Verification**: Performs a verification check (`http://www.google.com/generate_204`) to confirm domain-level resolution.
4. **Update Check**: Verifies local application version against the online repository assets.

---

## Prerequisites and Building

### System Requirements

- Android 7.0 (API Level 24) or higher
- Android Studio Hedgehog (2023.1.1) or newer / JDK 17
- Gradle 8.4

### Build Instructions

1. Clone the repository:
   ```bash
   git clone https://github.com/RanvirRox/GCE-Wifi-AutoLogin.git
   cd GCE-Wifi-AutoLogin
   ```

2. Assemble the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. Install on a connected Android device:
   ```bash
   ./gradlew installDebug
   ```

The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Setup Instructions for Users

### Quick Settings Tile

1. Swipe down from the top of your screen to open the Notification Panel.
2. Swipe down again to expand the Quick Settings panel.
3. Tap the **Edit (Pencil)** icon.
4. Drag **Wi-Fi Login** into your active tiles list.

### Home Screen Widget

1. Long-press any empty space on your Android home screen.
2. Select **Widgets**.
3. Locate **Wi-Fi Login** in the list.
4. Touch and drag the circular icon to your home screen.

---

## Download Links
- Direct Download: 
- Releases: [https://github.com/RanvirRox/GCE-Wifi-AutoLogin/releases](https://github.com/RanvirRox/GCE-Wifi-AutoLogin/releases)

---

## Developer Contact and Support

- **Developer**: Ranvir Rox
- **Email**: ranvirrox5999@gmail.com
- **Repository**: [https://github.com/RanvirRox/GCE-Wifi-AutoLogin](https://github.com/RanvirRox/GCE-Wifi-AutoLogin)
