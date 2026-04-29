# Drive Monitor

An Android app that monitors vehicle motion and alerts you to hard braking, acceleration, and turning in real time.

[![Release APK](https://github.com/bobbhimself/Drive-Monitor-public/actions/workflows/release.yml/badge.svg)](https://github.com/bobbhimself/Drive-Monitor-public/actions/workflows/release.yml)

## Install

Drive Monitor is distributed as an APK on the [GitHub Releases page](https://github.com/bobbhimself/Drive-Monitor-public/releases/latest). To install on a phone running Android 10 or newer:

1. On the phone, open the Releases page and download the `drive-monitor-vX.Y.Z.apk` asset.
2. Allow your browser (or file manager) to install apps: **Settings → Apps → Special app access → Install unknown apps** → pick the app you used to download → enable *Allow from this source*.
3. Open the downloaded APK and tap **Install**. You may need to dismiss a Play Protect warning the first time — the app is signed by the project maintainer, not Google.
4. Launch Drive Monitor and grant the notification permission when prompted.

## Features

- **User-initiated monitoring** — Start Trip / End Trip buttons; no auto-start, no background recovery
- **Trip-start calibration** with stability validation while stationary
- **Foreground service** with `specialUse` type — keeps monitoring while navigation apps are foregrounded
- **Caution and alert severity** for braking, acceleration, and turning, with per-category thresholds
- **Real-time notifications** when thresholds are exceeded
- **Persistent status display** — Inactive, Calibrating, or Active
- **Local event log** with timestamps and event details
- **Clear Log** button with a confirmation dialog
- **XLSX export** built from a bundled template, with bold headers and conditional highlighting (yellow for caution rows, red for alert rows)

## Permissions

| Permission | Purpose |
|---|---|
| `POST_NOTIFICATIONS` | Display caution and alert notifications (required on API 33+) |
| `FOREGROUND_SERVICE` | Run the monitoring service while the app is in the background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground service type for trip monitoring |

Linear acceleration sensor access does not require a runtime permission.

## Building from Source

**Prerequisites**

- Android Studio (stable channel)
- Physical Android device running Android 10 or higher
- JDK 21

> **Note:** This app is designed for physical devices. Emulator testing is not supported because the sensor pipeline relies on real linear acceleration data.

**Steps**

1. Clone the repository
2. Open the project in Android Studio
3. Connect a physical Android device with USB debugging enabled
4. Build and run the `app` module on the device

## Project Structure

```
app/src/main/java/com/bobbhimself/drivemonitor/
├── alerts/           # Notification channels and posting (AlertManager)
├── data/
│   ├── export/       # XLSX export via SAF (LogXlsxExporter)
│   ├── local/        # Room database
│   ├── model/        # Data classes (MonitoringState, MotionCategory, AlertSeverity, TripEvent)
│   └── repository/   # State and log repositories
├── sensors/          # Motion sensor pipeline
│   ├── MotionSensorManager.kt   # Sensor registration only
│   ├── MotionProcessor.kt       # Axis mapping, EMA filtering, calibration, deadband
│   ├── ThresholdEvaluator.kt    # Event lifecycle state machine, severity, bump rejection
│   └── ThresholdConfig.kt       # All threshold and processing constants
├── service/          # Foreground service orchestration (DriveMonitorService)
└── ui/               # Jetpack Compose screens, ViewModel, theme
```

The XLSX template lives at `app/src/main/assets/drive_monitor_template.xltx` and supplies styles, column widths, and conditional formatting rules.

## Architecture

The sensor processing pipeline follows a strict layered design where each class has a single responsibility:

```
Sensor Hardware
  → MotionSensorManager (registration only)
    → MotionProcessor (filtering, calibration, baseline)
      → ThresholdEvaluator (event lifecycle, severity)
        → DriveMonitorService (orchestration, hands off events)
          → AlertManager (notifications)
          → Repository (persistence)
```

Key design decisions:

- **No cross-layer logic** — each layer has explicit must-not constraints
- **Hot path constraint** — the sensor loop performs no I/O, logging, or allocation
- **Manual DI** — singletons wired in `DriveMonitorApp : Application`
- **UI-to-service communication** via explicit intents (`ACTION_START_MONITORING` / `ACTION_STOP_MONITORING`)
- **EMA filtering** with alpha = 0.20, initialized to the calibration baseline
- **Per-category event lifecycle** — IDLE → CANDIDATE → ACTIVE → COOLDOWN, with persistence, quiet, and cooldown windows

## Status

MVP feature-complete: monitoring, calibration, threshold evaluation, notifications, log persistence, Clear Log, and XLSX export are all in place. Post-MVP work (live gauges, directional alerts) is in progress.

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose with Material3
- **Architecture:** Manual singleton wiring (no DI frameworks)
- **Service:** Foreground service with `specialUse` type
- **Persistence:** Room
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35 (Android 15)
- **Build:** Gradle with version catalog (`libs.versions.toml`)

## Releasing (maintainers)

Releases are built and published by the `Release APK` GitHub Actions workflow (`.github/workflows/release.yml`) when a `v*` tag is pushed. One-time setup is required: a Play-Store-compatible upload keystore plus four GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). For local release builds, copy `keystore.properties.example` to `keystore.properties` and place the keystore at `app/upload-keystore.jks`.

To cut a release:

1. Bump `versionCode` (integer, must increase) and `versionName` (e.g. `"1.0.1"`) in `app/build.gradle.kts` so `versionName` matches the tag without the `v` prefix.
2. Commit and push to `main`.
3. Tag and push: `git tag v1.0.1 && git push origin v1.0.1`.
4. The workflow builds, signs, and attaches `drive-monitor-v1.0.1.apk` and `drive-monitor-v1.0.1.aab` to a new GitHub Release.

## Documentation

- [`docs/Drive_Monitor_PRD_v3.md`](docs/Drive_Monitor_PRD_v3.md) — requirements, architecture, design, threshold appendix

## Credits

- **UI illustrations** (truck artwork, launch screen) generated with ChatGPT.
- **Notification sounds** (`sound_alert.ogg`, `sound_caution.ogg`) — sourced from [Mixkit](https://mixkit.co) under the [Mixkit Free License](https://mixkit.co/license/).

## License

This project is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE) for the full text.
