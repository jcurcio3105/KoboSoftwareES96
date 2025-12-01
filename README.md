# Android BLE Button Counter App — README

## Overview

This project is an Android application that communicates with an external microcontroller over Bluetooth Low Energy (BLE). The microcontroller sends button-counting data, which the app logs to a CSV file. Each row of the CSV represents one individual and includes:

* 5 characteristic counts
* Timestamp
* Bluetooth transfer confirmation
* SD card write confirmation

The app allows the user to connect to the BLE peripheral, process incoming packets, and export logged data.

---

## Features

* Scan for BLE devices (Android 12+ compliant)
* Connect to a BLE peripheral using **BLUETOOTH_CONNECT** and **BLUETOOTH_SCAN** permissions
* Read characteristics/notifications
* Display current counts on screen
* Append each dataset as a new CSV row
* Export CSV using Android FileProvider

---

## Required Permissions

The project includes all modern BLE permissions:

* `BLUETOOTH`
* `BLUETOOTH_ADMIN`
* `BLUETOOTH_SCAN`
* `BLUETOOTH_CONNECT`
* `ACCESS_FINE_LOCATION` (required by some OEMs)

These are declared in **AndroidManifest.xml** exactly in the format required for Android 12+.

---

## Project Structure

Below is the project directory layout followed by brief descriptions of the key files.

```

### File Descriptions

**MainActivity.kt** — Hosts the application's composable UI, handles permission requests, initiates scanning, receives BLE data, and coordinates updates to the CSV logger.

**BluetoothService.kt** — Manages all Bluetooth Low Energy operations including scanning, connecting, setting up GATT, reading notifications, and handling characteristic UUIDs.

**CsvLogger.kt** — Handles creation, formatting, and appending of CSV rows. Responsible for writing user data to storage and interacting with FileProvider for export.

**UiScreens/** — Contains composable UI components and screens used by `MainActivity`, such as device selection dialogs and display layouts for data.

**utils/** — Contains helper functions or shared utility objects used across BLE operations or UI.

**res/layout/** — XML layout resources used during app bootstrapping or non‑Compose UI components.

**res/xml/file_paths.xml** — Defines which directories the FileProvider can expose for CSV export.

**AndroidManifest.xml** — Declares permissions, app metadata, FileProvider configuration, and main activity setup.

/app
 ├── java/com.example.buttoncounterapptest
 │     ├── MainActivity.kt
 │     ├── BluetoothService.kt
 │     ├── CsvLogger.kt
 │     ├── UiScreens/
 │     └── utils/
 ├── res/
 │     ├── layout/
 │     ├── xml/
 │     │     ├── backup_rules.xml
 │     │     ├── data_extraction_rules.xml
 │     │     └── file_paths.xml
 └── AndroidManifest.xml
```

/app
├── java/com.example.buttoncounterapptest
│     ├── MainActivity.kt
│     ├── BluetoothService.kt
│     ├── CsvLogger.kt
│     ├── UiScreens/
│     └── utils/
├── res/
│     ├── layout/
│     ├── xml/
│     │     ├── backup_rules.xml
│     │     ├── data_extraction_rules.xml
│     │     └── file_paths.xml
└── AndroidManifest.xml

```

---

## BLE Behavior

The app connects to the microcontroller's BLE GATT service and listens for a notification characteristic. Data is expected in packets containing:

```

[count1, count2, count3, count4, count5, timestamp, ble_ok_flag, sd_ok_flag]

```

When the user presses the "Next" button on the microcontroller, a new row is added to the CSV.

---

## Exporting CSV

The app uses Android's **FileProvider**:

```

<provider
 android:name="androidx.core.content.FileProvider"
 android:authorities="${applicationId}.provider"
 android:exported="false"
 android:grantUriPermissions="true"> <meta-data
     android:name="android.support.FILE_PROVIDER_PATHS"
     android:resource="@xml/file_paths" /> </provider>

```

This allows exporting or sharing files with other apps.

---

## How to Build

1. Clone repository
2. Open in Android Studio
3. Ensure **Android SDK 31+** installed
4. Build & Run on a real device (BLE does *not* work on most emulators)

---

## Troubleshooting

### Permission Errors

If you see logs such as:

```

Missing permissions required by BluetoothGatt.setCharacteristicNotification: BLUETOOTH_CONNECT

```

Ensure you:

- Request runtime permissions in the Activity
- Have all permissions declared in the manifest

### Patch Conflict (misc.xml)

If Android Studio detects a merge conflict:

- Choose **Abort and Rollback** if unsure (safer)
- Manually fix conflicts before retrying patch

### Emulator Cannot Run

BLE scanning usually fails on emulators. Use a **real Android device**.

```
