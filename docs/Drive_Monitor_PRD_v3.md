# Drive Monitor App Documentation — v3

## Document 1. Product Brief

### 1.1 Working Title
Drive Monitor

### 1.2 Product Summary
Drive Monitor is a personal-use Android app that the user manually starts before driving. Once started, it monitors vehicle motion using the phone's onboard sensors while a navigation app such as Google Maps runs in the foreground. The app issues caution and alert notifications when braking, acceleration, or turning exceed hard-coded thresholds, stores a basic local event log containing event type and timestamp, and allows the user to export that log as a CSV file.

### 1.3 Primary Goal
Create a simple, reliable Android trip-monitoring app that runs during a drive, warns the user when driving maneuvers approach or exceed predefined motion thresholds, records those events locally, and allows the user to export the recorded log.

### 1.4 Target User
A single personal user.

### 1.5 Core Use Case
1. User opens the app before beginning a drive.
2. User taps **Start Trip**.
3. The app performs trip-start calibration while the vehicle is stationary.
4. Once calibration succeeds, the app begins monitoring motion in a foreground service.
5. User switches to Google Maps or another navigation app.
6. The app continues monitoring in the background.
7. The app issues caution or alert notifications when thresholds are approached or exceeded.
8. The app records events in a basic local log.
9. User taps **End Trip** at the end of the drive.
10. User taps **View Log** to review logged events.
11. User exports the log as a `.csv` file when needed.

### 1.6 MVP Feature Set
- User-initiated trip monitoring
- Foreground-service-based monitoring while other apps are visible
- Trip-start calibration with stability validation before active monitoring begins
- Hard-coded thresholds for braking, acceleration, and turning
- Separate caution and alert levels for each motion category
- Persistent active/inactive/calibrating status on the main screen
- Basic local event log with alert type and date/time
- CSV export of the local event log via the Storage Access Framework
- Minimal user interface with three buttons only:
  - Start Trip
  - End Trip
  - View Log

### 1.7 Explicit Non-Goals
- No user-configurable thresholds in MVP
- No iOS version
- No cloud sync
- No account system
- No route mapping inside the app
- No driver score system in MVP
- No insurance or telematics integration
- No overlay over navigation apps
- No automatic trip start in MVP
- No automatic restart after OS kill or crash
- No boot persistence or background recovery

### 1.8 Success Criteria
The MVP is successful when the app can:
- Start monitoring from a single button press
- Successfully calibrate while the vehicle is stationary and reject calibration when the phone is moved
- Continue monitoring while Maps is open
- Detect caution and alert events for braking, acceleration, and turning
- Display whether monitoring is active, calibrating, or inactive on the main screen
- Record events locally with timestamp and event type
- Export logged events as a `.csv` file
- Stop monitoring cleanly when the user ends the trip
- Survive quiet driving without generating false-positive event spam

### 1.9 Post MVP Wishlist
Features explicitly deferred beyond MVP. These are not scheduled for implementation and are recorded here for future reference only.

- **Custom notification sounds** — replace the system-default notification sound with a distinct sound for Drive Monitor alerts.
- **Clear log** — add a "Clear Log" option on the Log screen to wipe all stored events.
- **Live G-force displays** — add two speedometer-style gauges on the main screen showing real-time vehicle G-forces: one for the forward/reverse axis and one for the left/right axis.
- **Directional alert icon** — display a truck icon on the main screen that shows a colored arc (yellow for caution, red for severe) in the direction of the triggering event when a notification appears.
- **Custom app icon** — replace the default Android app icon with a custom Drive Monitor icon.
- **UI redesign** — redesign the main and log screens with an updated visual style, layout, and branding.
- **CSV template** — provide a predefined CSV template or column schema for exported log files to support consistent downstream use (e.g., spreadsheet import, analysis).

---

## Document 2. Requirements Specification

### 2.1 Functional Requirements

#### 2.1.1 Trip Control
- The app shall provide a **Start Trip** button.
- The app shall provide an **End Trip** button.
- The app shall provide a **View Log** button.
- The app shall start monitoring only when the user taps **Start Trip**.
- The app shall stop monitoring when the user taps **End Trip**.
- Duplicate Start Trip taps while monitoring is active shall be ignored.
- End Trip while inactive shall be harmless (no crash or misbehavior).

#### 2.1.2 Trip-Start Calibration
- On Start Trip, the app shall enter a CALIBRATING state before beginning active monitoring.
- The app shall collect sensor samples during a fixed calibration window to establish per-axis baselines.
- Raw baselines shall first be computed as the arithmetic mean of all raw X, raw Y, and raw Z samples collected during the calibration window.
- Before ACTIVE monitoring begins, those raw baselines shall be converted into normalized-axis baselines using the same locked mapping/sign rules as live samples:
  - `baselineLateral = baselineRawX`
  - `baselineVertical = baselineRawY`
  - `baselineLongitudinal = -baselineRawZ`
- Calibration shall succeed only if the minimum valid sample count is met and stability thresholds are not exceeded (RMS deviation per axis ≤ 0.02g, no individual sample deviating more than 0.05g from the running mean on that axis).
- If calibration succeeds, the app shall transition to ACTIVE monitoring. The EMA filter's initial `previousFiltered` value for each axis shall be set to the normalized-axis calibration baseline for that axis to avoid a transient spike at monitoring start.
- If calibration fails, the app shall stop the service, display a Snackbar on the main screen with the text "Calibration failed — keep the phone still and try again", and return to INACTIVE.

#### 2.1.3 Monitoring State Indicator
- The main screen shall display a monitoring status indicator.
- When monitoring is active, the indicator shall show a green light and the text **Active**.
- When calibration is in progress, the indicator shall show an amber/yellow light and the text **Calibrating**.
- When monitoring is inactive, the indicator shall show a red light and the text **Inactive**.
- The indicator shall reflect the actual service state, not just the last button pressed.

#### 2.1.4 Background Monitoring Behavior
- The app shall run trip monitoring in a foreground service.
- The app shall continue monitoring while a navigation app is visible in the foreground.
- The app shall maintain an ongoing system notification while monitoring is active.
- The foreground service shall use the `specialUse` foreground service type with the following manifest declarations:
  - Permission: `android.permission.FOREGROUND_SERVICE`
  - Permission: `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
  - Permission: `android.permission.POST_NOTIFICATIONS`
  - Service attribute: `android:foregroundServiceType="specialUse"`
  - Service property: `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` with value `Trip monitoring using motion sensors to detect driving maneuvers`
- At runtime, the service shall promote itself using `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE`.

#### 2.1.5 Notification Permission
- The app shall request the `POST_NOTIFICATIONS` runtime permission once on first app launch, before any trip is started. The request shall be guarded to API 33+ only; on API 29–32 no check is needed.
- If the user denies the permission, the app shall proceed normally. Monitoring is never gated behind notification permission. The app remains fully functional; caution and alert notifications are silently suppressed by the system.
- If the permission is denied, the app shall show a one-time Snackbar on the main screen: "Notifications are disabled — enable in Settings for driving alerts." This Snackbar shall not repeat on subsequent launches.

#### 2.1.6 Motion Detection
- The app shall monitor for braking behavior.
- The app shall monitor for acceleration behavior.
- The app shall monitor for turning behavior.
- The app shall use hard-coded caution thresholds for braking, acceleration, and turning.
- The app shall use hard-coded alert thresholds for braking, acceleration, and turning.
- The app shall classify detected events into caution or alert severity.
- Threshold values in ThresholdConfig are stored as unsigned magnitudes. The evaluator applies sign direction per category.

#### 2.1.7 Threshold Comparison Rules
- **Acceleration** is detected when `longitudinalG > +cautionThreshold` (positive = forward force). Alert level: `longitudinalG > +alertThreshold`.
- **Braking** is detected when `longitudinalG < -cautionThreshold` (negative = deceleration force). Alert level: `longitudinalG < -alertThreshold`.
- **Turning** is detected when `abs(lateralG) > cautionThreshold`. Alert level: `abs(lateralG) > alertThreshold`. Left and right turns are treated identically as a single "turning" category. No directional metadata is stored.
- Acceleration and braking are mutually exclusive on the same sample — a single longitudinalG value can only trigger one category.

#### 2.1.8 Motion Processing Pipeline
- The app shall use a stability-first motion-processing pipeline that prefers suppressing noise and short spikes over detecting every borderline maneuver.
- Raw sensor readings shall be axis-mapped, sign-normalized, EMA-filtered, baseline-corrected, and deadband-suppressed before reaching the threshold evaluator.
- The ThresholdEvaluator shall consume only normalized motion values and shall not perform signal cleanup.

#### 2.1.9 Sensor Mount Requirements (MVP)
- The phone must be mounted in portrait orientation, screen facing the driver, in a fixed mount.
- The phone must not be moved during a trip.
- Arbitrary phone orientations are not supported in MVP.

#### 2.1.10 Axis Mapping
- X → Lateral (left/right): `lateral = rawX`
- Y → Vertical (up/down): `vertical = rawY`
- Z → Longitudinal (forward/backward): `longitudinal = -rawZ`
- Forward acceleration produces positive longitudinal values; braking produces negative.
- `verticalG` refers to dynamic vertical motion from `TYPE_LINEAR_ACCELERATION`, not gravity. It is used for bump/shock interpretation only.

#### 2.1.11 Event Lifecycle
- Each motion category (acceleration, braking, turning) shall maintain an independent per-category lifecycle state machine with states: IDLE, CANDIDATE, ACTIVE, COOLDOWN.
- A CANDIDATE event must satisfy a persistence window before becoming ACTIVE, rejecting short spikes automatically.
- An ACTIVE event may escalate from caution to alert severity but never downgrade.
- An ACTIVE event ends only after a quiet window expires with the signal below the caution threshold.
- After finalization, a cooldown window suppresses new events of the same category.
- Only finalized events are stored; no partial or candidate events are persisted.
- Different motion categories may run concurrently as separate events.

#### 2.1.12 Bump Rejection
- During the CANDIDATE phase, if `abs(verticalG) > bumpVerticalThreshold` (0.40g) at any point during the persistence window, AND the candidate fails to satisfy the persistence window (the longitudinal/lateral signal does not sustain above the caution threshold for the full 200 ms), then the candidate shall be rejected as bump/noise and the category shall return to IDLE.
- A vertical spike alone does not reject a candidate — it only rejects candidates that were already failing persistence. A sustained real maneuver that also happens during a bump will still pass the persistence gate normally.

#### 2.1.13 User Alerts
- The app shall notify the user when a caution threshold is reached.
- The app shall notify the user when an alert threshold is reached.
- The app shall differentiate caution events from alert events in the event classification.
- Notifications are event-lifecycle-driven, not raw-threshold-driven.
- One notification per category event lifecycle; sounds trigger on state transitions only.
- Alert overrides caution within the same event lifecycle; no repeated sounds for the same severity within a single lifecycle.
- During COOLDOWN, new notifications for the same category are suppressed.

#### 2.1.14 Event Log
- The app shall store a local log of detected events.
- Each log entry shall include:
  - motion category
  - severity
  - timestamp (stored as UTC epoch milliseconds)
- The **View Log** button shall open a screen that displays logged events.
- The app shall provide a way to export the log to a `.csv` file using the Storage Access Framework.
- The log export shall include motion category, severity, and timestamp columns.
- CSV cell values shall use lowercase strings: `braking`, `acceleration`, `turning` for motion_category; `caution`, `alert` for severity. The exporter must explicitly lowercase enum values.
- The Log screen UI may display title case for readability; the CSV format and UI display format are independent.
- The log shall persist across app restarts.
- Events shall be displayed newest first.
- Timestamps shall be stored in UTC and displayed in the device's local timezone.
- CSV export shall format timestamps as ISO 8601 with explicit timezone offset.

### 2.2 Non-Functional Requirements
- The app shall function without an internet connection.
- The app shall process motion data locally on the device.
- The app shall be designed for Android only.
- The app shall use Kotlin.
- The app shall use Jetpack Compose for the UI.
- The app shall remain minimal in UI scope.
- The app shall favor reliability over feature breadth.
- The app shall be usable before driving with minimal interaction.
- The continuous monitoring pipeline shall remain lightweight and constant-time per sample, suitable for multi-hour foreground-service execution.
- The sensor hot path (Sensor → MotionProcessor → ThresholdEvaluator) shall not perform database writes, file I/O, per-sample logging, string formatting, or unnecessary object creation.
- The app shall target low-to-moderate battery usage.

### 2.3 Technical Constraints
- Monitoring must be user-initiated.
- Monitoring must run through a foreground service using the `specialUse` foreground service type.
- Threshold values must be hard-coded in the app.
- The MVP UI must expose only three user buttons.
- The app must store event logs locally using Room.
- The app must support CSV export of the stored log via SAF (`ACTION_CREATE_DOCUMENT`).
- The app must be compatible with a real Android phone used during driving.
- The phone must be mounted in portrait orientation in a fixed mount.
- Use `SENSOR_DELAY_GAME` for sensor sampling; do not use `SENSOR_DELAY_FASTEST`.
- No runtime motion-sensor permission request is required for `TYPE_LINEAR_ACCELERATION` with the locked sampling mode.
- `POST_NOTIFICATIONS` runtime permission is requested on first launch (API 33+ only) but does not gate any functionality.

### 2.4 Non-Requirements
- The app shall not include threshold tuning in the UI.
- The app shall not include map rendering.
- The app shall not include cloud backup.
- The app shall not include analytics dashboards in MVP.
- The app shall not include trip auto-detection in MVP.
- The app shall not include an always-on silent background mode outside an active trip.
- The app shall not include Kalman filtering, sensor fusion, dynamic re-zeroing, adaptive filtering, or ML-based motion classification.
- The app shall not include adaptive sampling rates, advanced background batching, or dynamic performance tuning.

---

## Document 3. User Stories and Use Cases

### 3.1 User Stories
- As a user, I want to tap **Start Trip** before driving so monitoring begins with minimal effort.
- As a user, I want the app to calibrate briefly at startup so sensor readings are accurate for my current mount position.
- As a user, I want the app to keep monitoring while Google Maps is on screen so I can use navigation normally.
- As a user, I want to see a clear active/calibrating/inactive indicator so I know the current monitoring state.
- As a user, I want caution and alert notifications so I know when my driving maneuvers approach or exceed thresholds.
- As a user, I want notifications to sound once per event, not repeatedly, so they are informative without being distracting.
- As a user, I want a local log of detected events so I can review what happened after a trip.
- As a user, I want to export the log as a `.csv` file so I can review or store it outside the app.
- As a user, I want to tap **End Trip** when I finish driving so the app stops monitoring cleanly.

### 3.2 Primary Use Case: Start and Run a Trip
1. User opens the app.
2. User sees the status indicator showing **Inactive**.
3. User taps **Start Trip**.
4. The app starts the monitoring foreground service and registers sensor listeners.
5. The status indicator changes to **Calibrating**.
6. The app posts an ongoing notification.
7. The app collects calibration samples while the vehicle is stationary.
8. Calibration succeeds; EMA filters initialize to calibration baselines; the status indicator changes to **Active**.
9. Incoming sensor samples now flow through the normal processing pipeline.
10. User switches to Google Maps.
11. The app monitors motion in the background.
12. The app issues caution or alert notifications if thresholds are crossed (one notification per event lifecycle).
13. The app records each finalized event in the local log.

### 3.3 Primary Use Case: End a Trip
1. User returns to the app or uses the notification **End Trip** action.
2. User taps **End Trip**.
3. The app stops the foreground service and unregisters sensor listeners.
4. The ongoing notification is removed.
5. The status indicator changes to **Inactive**.

### 3.4 Primary Use Case: View Log
1. User opens the app.
2. User taps **View Log**.
3. The app opens the log screen.
4. The user sees logged events listed by type, severity, and timestamp (newest first).
5. The user can export the displayed or stored log as a `.csv` file.

### 3.5 Edge Case: Calibration Failure
1. User taps **Start Trip** while the phone is being moved.
2. Calibration detects instability and fails.
3. The service stops, sensor listeners are unregistered, and a Snackbar appears: "Calibration failed — keep the phone still and try again".
4. The status indicator returns to **Inactive**.

### 3.6 Edge Case: App Reopen During Monitoring
1. User returns to the app while monitoring is active.
2. The UI queries current service state and displays **Active**.
3. The UI does not assume prior state.

---

## Document 4. UX and Screen Specification

### 4.1 Main Screen
The main screen shall contain:
- Monitoring status indicator
  - Green light + **Active** when monitoring is running
  - Amber/yellow light + **Calibrating** when calibration is in progress
  - Red light + **Inactive** when monitoring is not running
- **Start Trip** button
- **End Trip** button
- **View Log** button

### 4.2 Main Screen Behavior
- **Start Trip** starts monitoring if it is not already active.
- **End Trip** stops monitoring if it is active.
- **View Log** opens the log screen.
- The status indicator updates to reflect actual monitoring state.
- Duplicate Start Trip is ignored; End Trip while inactive is harmless.

### 4.3 Log Screen
The log screen shall display a scrollable list of event entries sorted newest first.

Each entry shall show:
- motion category (title case for display)
- severity (title case for display)
- date and time (displayed in device local timezone)

The log screen shall also provide an export action for generating a `.csv` file from the current stored log. Export is disabled or suppressed when the log is empty.

An empty-state message shall be shown when no events exist.

The log screen shall include a top app bar with a leading back navigation button that returns the user to the Main Screen.

Example display values:
- Braking — Caution — 2026-04-03 1:42 PM
- Turning — Alert — 2026-04-03 1:44 PM
- Acceleration — Alert — 2026-04-03 1:46 PM

### 4.4 UI Principles
- Minimal interaction count
- Large, clear controls suitable for pre-drive use
- No settings screen in MVP
- No threshold display required in MVP
- Status should be visually unambiguous

### 4.5 Accessibility Semantics
- `MonitoringStatusIndicator` shall expose a clear state label:
  - `Monitoring status: Inactive`
  - `Monitoring status: Calibrating`
  - `Monitoring status: Active`
- The three main buttons shall use their visible text labels as their accessible labels.
- If icons are introduced later, they must not replace the text labels in MVP.

### 4.6 Compose Visual Defaults
- Inactive indicator color: red
- Calibrating indicator color: amber/yellow
- Active indicator color: green
- Visual state must remain obvious at a glance.
- No custom theming work is required for MVP beyond maintaining clear contrast and large tap targets.

---

## Document 5. Technical Design

### 5.1 Architecture Overview
The app will be a native Android application written in Kotlin using Jetpack Compose for the UI. Monitoring will run in a foreground service using the `specialUse` foreground service type. Motion data will be collected from Android motion sensors, processed through a stability-first pipeline (axis mapping, EMA filtering, baseline correction, deadband suppression), evaluated against hard-coded thresholds via a per-category lifecycle state machine using signed longitudinal comparisons and absolute lateral comparisons, recorded as local log events when events are finalized, and made exportable as a CSV file via SAF.

### 5.2 Architectural Components and Layer Ownership
The app uses a layered ownership model where each component has a single, clearly defined responsibility. No layer may duplicate or override responsibilities of another layer.

#### Sensor Layer — `MotionSensorManager`
- Register/unregister Android sensors
- Receive raw SensorEvent data
- Forward raw samples downstream
- Report sensor availability/failure
- Sensors are registered once at the start of CALIBRATING and remain registered through the transition to ACTIVE. Sensors are unregistered only on End Trip (service stop) or on critical error. There is no unregister/re-register cycle at the calibration-to-monitoring boundary.
- **Must NOT** perform filtering, calibration, threshold logic, or interact with persistence

#### Processing Layer — `MotionProcessor`
- Axis mapping (device → vehicle)
- Sign normalization (`longitudinal = -rawZ`, `lateral = rawX`, `vertical = rawY`)
- Trip-start calibration (baseline capture via arithmetic mean of raw samples)
- EMA filtering per axis (initialized to calibration baseline values)
- Baseline subtraction
- Deadband suppression
- Output normalized motion samples: longitudinalG, lateralG, verticalG, timestampMillis
- During CALIBRATING, incoming samples are routed to calibration collection logic. On transition to ACTIVE, the same incoming samples flow through the normal processing pipeline. This transition is a state change in how MotionProcessor handles samples, not a sensor lifecycle event.
- **Must NOT** apply thresholds, manage lifecycle states, trigger notifications, or write to database

#### Evaluation Layer — `ThresholdEvaluator`
- Consume normalized motion samples
- Apply thresholds from `ThresholdConfig` using signed comparisons for longitudinal (acceleration: positive, braking: negative) and absolute comparisons for lateral (turning)
- Manage lifecycle states (IDLE, CANDIDATE, ACTIVE, COOLDOWN)
- Apply persistence window, quiet window, cooldown
- Apply bump rejection by reading verticalG directly from normalized output
- Handle severity escalation (caution → alert, never downgrade)
- Emit finalized domain events
- **Must NOT** access raw sensor data, perform filtering/calibration, write to database, or trigger notifications

#### Orchestration Layer — `DriveMonitorService`
- Control monitoring lifecycle (start/stop)
- Coordinate calibration and activation
- Pipe data: Sensor → Processor → Evaluator
- Receive finalized events and hand off for persistence and notifications outside the sensor hot path
- Maintain authoritative monitoring state
- **Must NOT** contain threshold logic or duplicate processing/evaluation behavior

#### Notification Layer — `AlertManager`
- Create and manage notification channels (three channels — see §5.11)
- Post/update caution and alert notifications using locked content strings
- Enforce per-category notification slot behavior
- **Must NOT** determine event validity or severity, access motion data, or store data

#### State Layer — `MonitoringStateRepository`
- Provide observable `StateFlow<MonitoringState>` (INACTIVE, CALIBRATING, ACTIVE)
- Reflect service state for UI
- **Must NOT** control service lifecycle, persist to disk, or apply business logic

#### Persistence Layer — `TripLogRepository` + Room
- Store finalized events
- Retrieve events for display/export (newest-first ordering)
- **Must NOT** apply business rules, decide event validity, or trigger notifications

#### UI Layer — Screens and ViewModels
- `MainViewModel` observes `MonitoringStateRepository` and exposes `MonitoringState` to `MainScreen`. Obtains dependencies from `application as DriveMonitorApp`. Does NOT contain service control logic — Start Trip and End Trip still send explicit intents from the UI/Activity layer.
- `LogViewModel` reads from `TripLogRepository` and exposes the event list to `LogScreen`. May hold CSV export trigger state. Obtains dependencies from `application as DriveMonitorApp`.
- ViewModels are thin observation and plumbing layers. They must NOT control service lifecycle directly, implement motion/threshold logic, write directly to database, or duplicate orchestration behavior.
- Screens render monitoring state, trigger user actions, display logs and export results.
- **Must NOT** control service state directly, implement motion/threshold logic, or write directly to database

### 5.3 Proposed Package Structure
```text
com.bobbhimself.drivemonitor/
  DriveMonitorApp.kt
  MainActivity.kt

  ui/
    MainScreen.kt
    MainViewModel.kt
    LogScreen.kt
    LogViewModel.kt
    MonitoringStatusIndicator.kt

  service/
    DriveMonitorService.kt

  sensors/
    MotionSensorManager.kt
    MotionProcessor.kt
    ThresholdEvaluator.kt
    ThresholdConfig.kt

  alerts/
    AlertManager.kt

  data/
    local/
      TripEventEntity.kt
      TripEventDao.kt
      AppDatabase.kt
    repository/
      MonitoringStateRepository.kt
      TripLogRepository.kt
    export/
      LogCsvExporter.kt
    model/
      MotionCategory.kt
      AlertSeverity.kt
      MonitoringState.kt
      TripEvent.kt
```

### 5.4 Dependency Wiring
The app shall use a custom `Application` class (`DriveMonitorApp`) with manual singleton wiring for MVP.

`DriveMonitorApp` owns app-scoped singleton instances for:
- `MonitoringStateRepository`
- `AlertManager`
- `AppDatabase`
- `TripLogRepository`

Access pattern:
- `MainActivity` and `DriveMonitorService` obtain shared objects from `application as DriveMonitorApp`.
- `MainViewModel` and `LogViewModel` obtain repositories from `application as DriveMonitorApp` via a ViewModel factory.
- `MotionProcessor` and `ThresholdEvaluator` are created and owned by `DriveMonitorService` per monitoring session, not as app-global singletons.

### 5.5 UI-to-Service Command Pattern
UI commands for Start Trip and End Trip shall use explicit service intents with action strings, handled in `DriveMonitorService.onStartCommand()`.

Locked action constants:
- `ACTION_START_MONITORING`
- `ACTION_STOP_MONITORING`

- `Start Trip` sends an explicit intent using `ACTION_START_MONITORING` via `startForegroundService(intent)`.
- `End Trip` sends an explicit intent using `ACTION_STOP_MONITORING`.
- `DriveMonitorService.onStartCommand()` is the single command-entry point for UI and notification control flow.

Disallowed for MVP: bound service for trip control, broadcast-based command path, UI directly mutating repository state as a substitute for service commands.

### 5.6 Monitoring Flow
1. User taps **Start Trip**.
2. UI sends `ACTION_START_MONITORING` intent to `DriveMonitorService`.
3. Service enters foreground mode with ongoing notification, transitions to CALIBRATING state, and registers sensor listeners.
4. During CALIBRATING, sensor samples are routed to calibration collection logic in MotionProcessor.
5. If calibration succeeds, EMA filters are initialized to calibration baselines, and the service transitions to ACTIVE. The same sensor registration remains active; samples now flow through the normal processing pipeline.
6. If calibration fails, service stops, sensor listeners are unregistered, user sees a Snackbar, and state returns to INACTIVE.
7. Sensor readings are passed into MotionProcessor (axis mapping, sign normalization, EMA, baseline correction, deadband).
8. Normalized values are checked against thresholds by ThresholdEvaluator using signed longitudinal comparisons and absolute lateral comparisons, through the per-category lifecycle state machine.
9. Finalized events are emitted to DriveMonitorService.
10. Service hands off events to AlertManager (notifications) and TripLogRepository (persistence) outside the hot path.
11. CSV exporter can later retrieve stored events and generate a `.csv` file via SAF.
12. User taps **End Trip** (from UI or notification action) to stop monitoring. Sensor listeners are unregistered.

### 5.7 Provisional Threshold Values
These provisional hard-coded values are intended for initial testing on a Dodge Ram 2500 with a GVW of 10,000 lbs. They are based on heavier-vehicle telematics guidance and should be treated as starting values pending validation against actual Emkay/Geotab fleet settings.

All values are stored as unsigned magnitudes in ThresholdConfig. The evaluator applies sign direction per category (positive for acceleration, negative for braking, absolute for turning).

#### Caution Thresholds
- Acceleration caution: 0.28 G
- Braking caution: 0.29 G
- Turning caution: 0.28 G

#### Alert Thresholds
- Acceleration alert: 0.35 G
- Braking alert: 0.36 G
- Turning alert: 0.35 G

These values should be tuned after real-world testing to account for phone mount position, sensor noise, road conditions, and vehicle loading.

### 5.8 Motion Processing Pipeline

#### Processing Order (Locked for MVP)
1. Read raw linear-acceleration sensor sample
2. Map raw device axes into vehicle-relative axes (X→lateral, Y→vertical, Z→longitudinal)
3. Apply sign normalization (`longitudinal = -rawZ`)
4. Apply exponential moving average (EMA) smoothing per axis
5. Subtract trip baseline per axis
6. Apply deadband/noise-floor suppression
7. Produce corrected normalized values
8. Provide normalized values to downstream evaluator logic

#### EMA Filtering
Formula: `filtered = alpha * currentRaw + (1 - alpha) * previousFiltered`
- Initial `alpha = 0.20` (stability-first: lower alpha = more smoothing)
- `previousFiltered` for each axis is initialized to the calibration baseline for that axis when active monitoring begins. This avoids a transient spike at monitoring start.

#### Baseline Correction
Trip-start calibration produces per-axis baselines via arithmetic mean of all raw samples collected during the calibration window. For each filtered sample:
- `correctedLongitudinal = filteredLongitudinal - baselineLongitudinal`
- `correctedLateral = filteredLateral - baselineLateral`
- `correctedVertical = filteredVertical - baselineVertical`

#### Calibration Procedure
1. Collect raw X, Y, Z samples for the full calibration duration (2.0 seconds).
2. Validate that at least 40 samples were collected.
3. Validate that the RMS deviation on each axis is ≤ 0.02g.
4. Validate that no individual sample on any axis deviates more than 0.05g from the running mean on that axis.
5. If validation passes, compute raw baselines per axis as `sum(samples) / count(samples)` on each of raw X, raw Y, and raw Z.
6. Convert those raw baselines into normalized-axis baselines using the locked mapping/sign rules:
   - `baselineLateral = baselineRawX`
   - `baselineVertical = baselineRawY`
   - `baselineLongitudinal = -baselineRawZ`
7. Store these normalized-axis baselines for EMA initialization and per-sample baseline subtraction (step 5 of the processing order).

#### Calibration Constants
- Calibration duration: 2.0 seconds
- Minimum valid sample count: 40
- Stability rejection threshold (RMS on any axis): 0.02g
- Maximum absolute deviation threshold on any axis: 0.05g
- Baseline must be captured for X, Y, Z prior to sign inversion.

#### Deadband / Noise Floor
After baseline subtraction: `abs(value) < 0.03g` → treat as `0.0g`, applied independently per axis.

#### Normalized Output Model
`MotionProcessor` outputs: longitudinalG, lateralG, verticalG, timestampMillis.

### 5.9 Event Lifecycle State Machine

#### Timing Constants (MVP)
- Persistence Window: 200 ms
- Quiet Window: 750 ms
- Cooldown Window: 2000 ms

These values are stability-first defaults and must be configurable constants.

#### Threshold Comparison Rules
- **Acceleration:** `longitudinalG > +threshold` (positive = forward force)
- **Braking:** `longitudinalG < -threshold` (negative = deceleration)
- **Turning:** `abs(lateralG) > threshold` (left and right treated identically)
- Acceleration and braking are mutually exclusive per sample.

#### States

**IDLE** — No event in progress. If signal exceeds caution threshold (per comparison rules above) → transition to CANDIDATE.

**CANDIDATE** — Potential event detected; candidate timer starts. If signal remains above caution for full persistence window → transition to ACTIVE. If signal drops below before window completes → return to IDLE (reject). Bump rejection: if `abs(verticalG) > bumpVerticalThreshold` (0.40g) at any point during the persistence window AND the candidate fails to satisfy the persistence window → reject as bump/noise and return to IDLE. A vertical spike alone does not reject; it only rejects candidates already failing persistence.

**ACTIVE** — Event is confirmed. Track highest severity reached. If signal drops below threshold, begin quiet-window timing. If signal returns above before quiet window expires → continue ACTIVE. If quiet window fully expires → finalize event and transition to COOLDOWN.

**COOLDOWN** — Suppress new events of same category. When cooldown timer expires → transition to IDLE.

#### Severity Rules
- Event begins at caution level.
- If alert threshold is reached during ACTIVE → escalate severity.
- Severity never downgrades within the same event.
- Only one event is stored per lifecycle; final stored severity = highest reached.

#### Output
- Only finalized events are emitted to repository.
- Event timestamp = initial threshold crossing time (start of CANDIDATE).

### 5.10 Sensor Processing Responsibilities
#### `MotionSensorManager`
- Register and unregister sensor listeners
- Collect motion readings from Android sensors (TYPE_LINEAR_ACCELERATION)
- Forward raw sensor values to the processor
- Use `SENSOR_DELAY_GAME` sampling rate
- No runtime permission request needed for this sensor at this sampling rate
- Register once at CALIBRATING start; unregister only on service stop or critical error

#### `MotionProcessor`
- Full processing pipeline (see §5.8)
- Raw sensor values are never used outside MotionProcessor
- During CALIBRATING, routes samples to calibration collection. On ACTIVE transition, routes samples through normal pipeline.

#### `ThresholdEvaluator`
- Compare normalized readings to caution and alert thresholds using signed longitudinal and absolute lateral comparisons
- Manage per-category lifecycle state machines (see §5.9)
- Distinguish between acceleration (positive longitudinalG), braking (negative longitudinalG), and turning (absolute lateralG)
- Read verticalG directly for bump rejection decisions

#### `ThresholdConfig`
- Store all hard-coded threshold values as unsigned magnitudes
- Separate thresholds by category and severity
- Store bump vertical threshold (0.40g)
- Store processing constants (EMA alpha, deadband)
- Store calibration constants (duration, min samples, RMS threshold, max deviation)
- Store lifecycle timing constants (persistence, quiet, cooldown windows)

### 5.11 Notification Architecture

#### Notification Channels
The app shall use three notification channels:

**1. Ongoing Monitoring Channel**
- Channel ID: `drive_monitoring_ongoing`
- User-visible name: `Trip Monitoring`
- Description: `Shows active trip monitoring status and provides the End Trip action.`
- Importance: `IMPORTANCE_LOW`
- Sound: disabled / Vibration: disabled / Badge: disabled

**2. Caution Event Channel**
- Channel ID: `drive_monitoring_caution_events`
- User-visible name: `Driving Caution Events`
- Description: `Used for caution-level driving event notifications.`
- Importance: `IMPORTANCE_DEFAULT`
- Sound: enabled / Vibration: disabled / Badge: disabled

**3. Alert Event Channel**
- Channel ID: `drive_monitoring_alert_events`
- User-visible name: `Driving Alert Events`
- Description: `Used for alert-level driving event notifications.`
- Importance: `IMPORTANCE_HIGH`
- Sound: enabled / Vibration: enabled / Badge: disabled

#### Notification IDs (Per-Slot)
- Ongoing monitoring: `1000`
- Braking event: `2001`
- Acceleration event: `2002`
- Turning event: `2003`

Rules:
- Event notifications reuse the same per-category ID within a lifecycle.
- Escalation from caution to alert reposts/updates the same per-category ID using the alert channel.
- Same-category notifications never stack.
- Different categories may notify concurrently using different IDs.

#### End Trip Notification Action
The ongoing monitoring notification shall include a working **End Trip** action using a service `PendingIntent` with `ACTION_STOP_MONITORING`, keeping notification-driven stop behavior aligned with the same command path used by the main UI.

#### Notification Content Text
All notification strings shall be defined as constants, not inline strings.

**Ongoing monitoring notification:**
- Title: `Drive Monitor`
- Body: `Trip monitoring is active`
- Action button: `End Trip`

**Caution event notifications:**
- Braking: title `Braking — Caution`, body `Hard braking detected`
- Acceleration: title `Acceleration — Caution`, body `Hard acceleration detected`
- Turning: title `Turning — Caution`, body `Hard turn detected`

**Alert event notifications:**
- Braking: title `Braking — Alert`, body `Severe braking detected`
- Acceleration: title `Acceleration — Alert`, body `Severe acceleration detected`
- Turning: title `Turning — Alert`, body `Severe turn detected`

When a caution notification escalates to alert within the same lifecycle, the existing notification (same per-category ID) is updated with the alert title, body, and channel. The caution notification is replaced, not stacked.

#### Notification Sound Behavior
- Caution sound: triggers once when event transitions CANDIDATE → ACTIVE at caution level.
- Alert sound: triggers once when event becomes ACTIVE at alert level, or when an ACTIVE caution event escalates to alert.
- No downgrade rule: once alert is reached, caution is never replayed.
- No repeated sounds for steady ACTIVE state or ACTIVE → COOLDOWN transitions.

### 5.12 Time Handling
- **Processing time:** Use monotonic time (e.g., `elapsedRealtimeNanos` or sensor timestamps) for calibration window, persistence window, quiet window, and cooldown window.
- **Stored event timestamp:** `timestampUtcMillis` (UTC epoch milliseconds), representing the moment the event candidate first began.
- **Log ordering:** By `timestampUtcMillis` descending.
- **UI display:** Convert stored UTC timestamp to device's current local timezone using a human-readable format.
- **CSV export:** ISO 8601 with explicit timezone offset using device's current local timezone at export time (e.g., `2026-04-10T15:42:18-04:00`).

### 5.13 Error Handling Model

The app uses a two-tier error model:

#### Critical Errors — Stop monitoring (fail closed)
On critical error: stop monitoring pipeline, stop foreground service, unregister sensor listeners, set state to INACTIVE, remove notification, notify user, log the error.

Critical error examples: required sensor unavailable, sensor registration failure, calibration failure (display Snackbar: "Calibration failed — keep the phone still and try again"), runtime exception in MotionProcessor or ThresholdEvaluator, sensor stream interruption during ACTIVE monitoring, foreground notification failure.

#### Non-Critical Errors — Continue monitoring (fail open)
On non-critical error: continue monitoring, log the error, notify user only when relevant.

Non-critical error examples: event persistence (database write) failure, log read failure, CSV export failure (display Snackbar on Log screen), non-foreground notification failure.

#### State Integrity Rule
If the system enters an invalid or uncertain state, it shall transition to INACTIVE rather than continue in degraded operation.

#### Logging Requirements
All handled errors shall log: component name, error type, severity (critical/non-critical), action taken.

#### Retry Behavior (MVP)
No automatic retries. User must manually retry (e.g., Start Trip again).

### 5.14 Performance and Battery Constraints

#### Hot-Path Processing Constraints
Allowed in the continuous sensor loop: simple arithmetic, EMA updates, threshold comparisons, lifecycle state updates.

Forbidden in the hot path: database writes, file I/O, per-sample logging, string formatting, unnecessary object creation, unbounded buffering.

#### Memory / Allocation Rules
Minimize object creation inside the sensor loop. Prefer primitive fields and reusable state. Finalized event object creation is acceptable because it is infrequent.

#### Logging Constraints
Logging is limited to lifecycle transitions, finalized events, and error conditions. No per-sample logging.

#### Notification Update Constraints
Notifications may be updated only on event activation, severity escalation, or event completion. No continuous refresh during active monitoring.

#### Threading Model
Default MVP behavior: process on the sensor callback thread. Do not introduce additional processing threads unless real testing shows a need.

#### Backpressure Handling
If processing cannot keep pace: prefer latest-sample handling over unbounded queue growth. Intermediate samples may be dropped.

#### Performance Warning Signs
Noticeable UI lag during monitoring, excessive device heat, unexpectedly high battery drain, delayed notifications, missed or inconsistent event detection under normal motion.

### 5.15 Persistence Responsibilities
#### `TripLogRepository`
- Save event entries locally
- Return event history for log display

#### `LogCsvExporter`
- Convert stored log events into CSV format
- Write CSV output via SAF (`ACTION_CREATE_DOCUMENT` + `ContentResolver.openOutputStream(uri)`)
- Do not use raw filesystem paths for exported CSV files
- Use lowercase enum string values for motion_category and severity columns
- Preserve column order and readable timestamp formatting

#### `TripEvent`
Recommended fields:
- id
- motionCategory
- alertSeverity
- timestampUtcMillis

### 5.16 Storage Recommendation
Use Room for the event log.

Reason:
- simple persistence model
- clean query support for log display
- avoids improvised storage patterns for structured historical data

### 5.17 Key Risks
- False positives from potholes or bumps (mitigated by persistence gate, bump vertical threshold, and vertical-dominance heuristic)
- Mount-angle variation affecting sensor interpretation (mitigated by fixed-mount requirement and calibration)
- Notification spam without proper cooldown logic (mitigated by lifecycle state machine)
- Device-specific battery behavior affecting long-running monitoring
- CSV export edge cases such as empty logs, timestamp formatting, and file destination handling

---

## Document 6. Decision Log

### Decision 001
**Decision:** Build the app for Android only.
**Reason:** The app depends on Android-specific service and sensor behavior, and the project is for personal use.

### Decision 002
**Decision:** Use Kotlin and Jetpack Compose.
**Reason:** Native Android tooling is the best fit for a sensor-driven foreground-service app.

### Decision 003
**Decision:** Monitoring is user-initiated only.
**Reason:** This avoids unnecessary complexity and aligns with Android execution limits.

### Decision 004
**Decision:** The app runs through a foreground service while a navigation app remains visible.
**Reason:** This is the correct Android model for long-running, user-noticeable monitoring during a drive.

### Decision 005
**Decision:** Thresholds are hard-coded in the app as unsigned magnitudes.
**Reason:** The app is for personal use, and hard-coded values reduce UI complexity and keep the MVP focused. The evaluator applies sign direction per category.

### Decision 006
**Decision:** Separate caution and alert thresholds exist for braking, acceleration, and turning.
**Reason:** This allows pre-threshold warning and full-threshold event detection.

### Decision 007
**Decision:** The main UI contains only three buttons: Start Trip, End Trip, and View Log.
**Reason:** The app should remain minimal and fast to use before driving.

### Decision 008
**Decision:** The main screen includes a visual monitoring status indicator with three states (Inactive, Calibrating, Active).
**Reason:** The user needs immediate confirmation of the current monitoring state before switching to a navigation app.

### Decision 009
**Decision:** Event logging is included in MVP.
**Reason:** A local log gives the app immediate utility beyond real-time alerts.

### Decision 010
**Decision:** Use local storage only for MVP.
**Reason:** Cloud sync and remote services are unnecessary for the initial version.

### Decision 010A
**Decision:** CSV export of the local log is included.
**Reason:** Export makes the recorded trip events more useful outside the app without requiring cloud features or analytics dashboards.

### Decision 011
**Decision:** Use provisional Ram 2500-specific hard-coded thresholds for MVP testing.
**Reason:** A 10,000-lb GVW pickup should use lower motion thresholds than a typical passenger car, and these values provide a more realistic starting point until actual Emkay/Geotab fleet thresholds are confirmed.

### Decision 012
**Decision:** Use a stability-first motion-processing pipeline with EMA smoothing, baseline correction, and deadband suppression.
**Reason:** Suppressing noise and short spikes is more important than detecting every borderline maneuver for a phone-mounted sensor in a heavy truck.

### Decision 013
**Decision:** Use a per-category lifecycle state machine (IDLE → CANDIDATE → ACTIVE → COOLDOWN) for event detection and debounce.
**Reason:** This eliminates notification spam, ensures persistence gates reject short spikes, and produces clean one-event-per-maneuver output.

### Decision 014
**Decision:** Notifications are event-lifecycle-driven with per-category slots; sounds trigger only on state transitions.
**Reason:** Prevents repeated alert noise from sustained events while ensuring each new event is audible.

### Decision 015
**Decision:** The foreground service uses `specialUse` type with trip-start calibration required before active monitoring.
**Reason:** The app does not fit standard named foreground-service categories; calibration ensures sensor baselines are valid.

### Decision 016
**Decision:** Use monotonic time for processing, UTC for storage, device local time for display/export.
**Reason:** Avoids clock-change issues in the processing pipeline while keeping stored data consistent and exports human-readable.

### Decision 017
**Decision:** Layered ownership model where each component has a single responsibility with explicit must-not constraints.
**Reason:** Prevents logic duplication and keeps the architecture agent-safe for AI-assisted implementation.

### Decision 018
**Decision:** Two-tier error model (critical = fail closed, non-critical = fail open).
**Reason:** Monitoring integrity is more important than availability; a degraded state is worse than stopping.

### Decision 019
**Decision:** Use a custom Application class (`DriveMonitorApp`) with manual singleton wiring for MVP.
**Reason:** Simplest reliable way to keep service and UI synchronized without introducing Hilt or DI frameworks.

### Decision 020
**Decision:** UI-to-service commands use explicit intents with action strings handled in `onStartCommand()`.
**Reason:** Single command entry point for both UI buttons and notification actions, avoiding divergent stop logic.

### Decision 021
**Decision:** Three notification channels (ongoing/caution/alert) with per-category notification IDs.
**Reason:** Gives Android and the user appropriate control over interruption behavior for each notification type.

### Decision 022
**Decision:** Use signed longitudinal comparisons for acceleration/braking and absolute lateral comparisons for turning.
**Reason:** The longitudinal axis sign distinguishes forward acceleration from braking. Turning direction (left/right) is not meaningful for this app, so absolute value collapses both into a single category.

### Decision 023
**Decision:** Add bumpVerticalThreshold (0.40g) as a concrete constant for bump rejection; remove the undefined bumpCandidate flag from MotionProcessor output.
**Reason:** Bump rejection needs a defined threshold to be implementable. The flag was architecturally redundant since the evaluator reads verticalG directly.

### Decision 024
**Decision:** Calibration baseline is the arithmetic mean of raw samples; EMA filters initialize to baseline values.
**Reason:** Mean is the simplest unbiased estimator. Initializing EMA to baseline prevents a transient spike at monitoring start.

### Decision 025
**Decision:** Use MainViewModel and LogViewModel as thin observation/plumbing layers.
**Reason:** Standard Compose architecture; prevents AI agents from inventing inconsistent wiring. ViewModels must not contain business logic.

### Decision 026
**Decision:** CSV cell values use lowercase strings; the exporter must explicitly lowercase enum values.
**Reason:** Lowercase is the most portable and script-friendly format. Display rendering on the Log screen is independent.

### Decision 027
**Decision:** POST_NOTIFICATIONS permission is requested once on first launch; denial does not gate any functionality.
**Reason:** Monitoring should never be blocked by notification permission. The user can enable notifications later in system Settings.

### Decision 028
**Decision:** Calibration failure displays a Snackbar on the main screen.
**Reason:** The user is still on the main screen watching the indicator; Snackbar is consistent with the CSV export failure pattern.

### Decision 029
**Decision:** Sensors are registered once at CALIBRATING start and remain registered through ACTIVE; unregistered only on stop or critical error.
**Reason:** Simpler than unregister/re-register, avoids a gap in sensor data, keeps sensor lifecycle in one place.

### Decision 030
**Decision:** Notification content text is locked as defined constants.
**Reason:** Ensures consistent, glanceable text suitable for a driving context.

### Decision 031
**Decision:** AGP and Gradle wrapper versions defer to Android Studio stable defaults.
**Reason:** Avoids version conflicts with the project creation tool. Pinned library versions are the authoritative constraints.

---

## Document 7. Implementation Roadmap

### Phase 0. Project Setup
- [ ] Create Android Studio project using Kotlin and Compose
- [ ] Configure package structure
- [ ] Create `DriveMonitorApp` custom Application class
- [ ] Confirm app runs on physical device
- [ ] Set up basic Git repository

### Phase 1. Main UI Shell
- [ ] Build main screen with status indicator (three states: Inactive, Calibrating, Active)
- [ ] Add accessibility semantics to status indicator and buttons
- [ ] Add Start Trip button
- [ ] Add End Trip button
- [ ] Add View Log button
- [ ] Add navigation to log screen
- [ ] Create MainViewModel as thin observation layer
- [ ] Request POST_NOTIFICATIONS permission on first launch (API 33+)

### Phase 2. Foreground Service
- [ ] Create `DriveMonitorService` with `specialUse` foreground service type (exact manifest XML specified)
- [ ] Add service manifest entries, foreground service type declarations, and permissions
- [ ] Create notification channels (ongoing, caution, alert) with locked content strings
- [ ] Implement Start Trip behavior via `ACTION_START_MONITORING` intent
- [ ] Implement End Trip behavior via `ACTION_STOP_MONITORING` intent
- [ ] Add End Trip notification action using service PendingIntent
- [ ] Connect service state to UI status indicator via `MonitoringStateRepository`

### Phase 3. Sensor Intake and Processing
- [ ] Create `MotionSensorManager` (TYPE_LINEAR_ACCELERATION, SENSOR_DELAY_GAME, no runtime sensor permission)
- [ ] Create `MotionProcessor` with full pipeline (axis mapping, sign normalization, EMA with baseline initialization, baseline correction, deadband)
- [ ] Implement trip-start calibration with locked constants and arithmetic mean baseline computation
- [ ] Register sensors once at CALIBRATING start; unregister only on stop/error
- [ ] Verify monitoring continues while Maps is open

### Phase 4. Threshold Detection
- [ ] Create `ThresholdConfig` with all provisional values including bumpVerticalThreshold (0.40g)
- [ ] Create `ThresholdEvaluator` with per-category lifecycle state machine (IDLE, CANDIDATE, ACTIVE, COOLDOWN)
- [ ] Implement signed longitudinal comparisons (acceleration: positive, braking: negative) and absolute lateral comparisons (turning)
- [ ] Implement persistence window, quiet window, cooldown window
- [ ] Implement severity escalation (caution → alert, no downgrade)
- [ ] Implement bump rejection using verticalG directly from normalized output
- [ ] Ensure finalized events are handed off outside the sensor hot path

### Phase 5. Alerts
- [ ] Create `AlertManager` with three notification channels and locked content strings
- [ ] Implement notification IDs per category slot
- [ ] Post lifecycle-driven caution notifications (sound once on CANDIDATE → ACTIVE)
- [ ] Post lifecycle-driven alert notifications (sound once on escalation)
- [ ] Implement escalation: update existing notification with alert title/body/channel
- [ ] Enforce no-downgrade and cooldown suppression rules

### Phase 6. Local Log
- [ ] Create Room entities and DAO
- [ ] Create repository for trip events
- [ ] Store finalized events with UTC timestamp
- [ ] Create LogViewModel to expose event list to LogScreen
- [ ] Build log screen list view (newest first, title case display)
- [ ] Add empty-state handling

### Phase 7. CSV Export
- [ ] Create CSV exporter with lowercase enum string values
- [ ] Use SAF (ACTION_CREATE_DOCUMENT) for file creation
- [ ] Write CSV output to chosen URI via ContentResolver
- [ ] Use ISO 8601 timestamps with timezone offset
- [ ] Validate CSV column order and format
- [ ] Show Snackbar feedback for success/failure

### Phase 8. Automated Validation Tests
- [ ] Configure test infrastructure (returnDefaultValues, remove placeholder)
- [ ] MotionProcessor unit tests (calibration, axis mapping, EMA, deadband)
- [ ] ThresholdEvaluator unit tests (persistence, quiet window, cooldown, escalation, bump rejection)
- [ ] Synthetic pipeline tests (Processor → Evaluator end-to-end scenarios)
- [ ] LogCsvExporter unit tests (header, lowercase, ISO 8601, row count)
- [ ] Full test suite passes (`./gradlew test`)

### Phase 9. Real-World Testing and Tuning

**Part A — Connected parking lot sessions (laptop + ADB + live Logcat):**
- [ ] Validate calibration success/failure scenarios (including Snackbar)
- [ ] Validate active/inactive/calibrating indicator behavior
- [ ] Validate error handling (critical and non-critical paths)
- [ ] Braking runs at varying intensity — correlate G values to thresholds live
- [ ] Acceleration runs at varying intensity — correlate G values to thresholds live
- [ ] Turning runs at varying sharpness — correlate G values to thresholds live
- [ ] Bump/pothole test — confirm rejection with live verticalG data
- [ ] Validate acceleration vs braking classification with live signed longitudinalG
- [ ] Tune thresholds and constants based on live data (iterate: adjust → rebuild → retest)
- [ ] Export CSV and validate format

**Part B — Road sessions (laptop on passenger seat, USB connected, logcat streamed to file):**
- [ ] Pre-drive: start `adb logcat -s ThresholdEvaluator:V DriveMonitorService:V *:S > road_session.log` on host before departure
- [ ] Test with Google Maps open during active trip
- [ ] Quiet driving — confirm no false-positive event spam (30–45 min, highway + residential)
- [ ] Validate battery and performance under sustained use (combined with false-positive session)
- [ ] Post-drive: stop log capture, pull Room database, analyze full log with Claude
- [ ] Compare results against actual Emkay/Geotab behavior when available

---

## Document 8. AI Prompting Rules

### 8.1 Coding Rules
- Do not add user-configurable settings unless explicitly requested.
- Do not introduce cloud services or accounts.
- Do not convert the app to a cross-platform stack.
- Preserve the foreground-service architecture and `specialUse` service type.
- Keep the UI limited to the defined screens and buttons unless adding the approved log export action.
- Prefer small focused Kotlin classes.
- Avoid introducing third-party libraries unless necessary (no Hilt, no DI frameworks in MVP).
- Explain manifest changes clearly.
- Do not replace threshold logic structure without justification.
- Implement only the requested task, not speculative extras.
- Respect layer ownership boundaries: do not put threshold logic in MotionProcessor, do not put database writes in ThresholdEvaluator, do not put business logic in repositories, do not put service control in ViewModels.
- Keep the sensor hot path free of database writes, file I/O, per-sample logging, and unnecessary object creation.
- Use monotonic time for all processing windows; use UTC for stored timestamps.
- Use signed comparisons for longitudinal thresholds and absolute comparisons for lateral thresholds.
- Define notification content strings as constants, not inline.
- CSV exporter must explicitly lowercase enum values.

### 8.2 Product Rules
- The app is a personal-use Android trip monitor.
- Monitoring is manually started and stopped.
- Thresholds are hard-coded as unsigned magnitudes; evaluator applies sign direction.
- Motion categories are braking, acceleration, and turning.
- Severities are caution and alert.
- Log entries must include event type and UTC timestamp.
- CSV export is allowed for the local log via SAF, using lowercase enum values.
- The phone must be in a fixed portrait mount during use.
- Trip-start calibration is required before active monitoring.
- POST_NOTIFICATIONS permission does not gate any functionality.
- Calibration failure shows a Snackbar, not a dialog or Toast.

---

## Document 9. Testing Strategy

### 9.1 Unit Tests (Pure Logic)
Target classes without Android dependencies using Kotlin/JUnit:

**MotionProcessor:**
- Deadband suppression: corrected values below `DEADBAND_G` (0.03g) output as 0.0
- EMA filtering: step-change input shows gradual convergence consistent with alpha=0.20; EMA initializes to baselines (no transient spike)
- Baseline subtraction: post-calibration output subtracts calibrated bias from filtered values
- Axis mapping and sign normalization: raw X → lateral, raw Y → vertical, raw Z → longitudinal (inverted sign)

**MotionProcessor — Calibration:**
- Calibration success: 50+ stable samples over 2000ms → `onCalibrationComplete` fires, correct baselines computed, EMA initialized to baselines, subsequent samples produce output
- Calibration failure — insufficient samples: fewer than 40 samples before window expires → `onCalibrationFailed`
- Calibration failure — RMS too high: samples with RMS > 0.02g on any axis → `onCalibrationFailed`
- Calibration failure — max deviation exceeded: sample deviates > 0.05g from running mean → `onCalibrationFailed`

**ThresholdEvaluator:**
- Persistence gate rejects short spikes: above-caution signal for < 200ms then drops → no event
- Persistence gate passes sustained signal: above-caution signal for ≥ 200ms → `onEventStarted` with correct category and CAUTION severity
- Quiet window finalizes event: signal drops below caution for ≥ 750ms → `onEventFinalized`
- Severity escalation: caution-level start, then exceeds alert during ACTIVE → `onEventEscalated` with ALERT; finalized event has ALERT (never downgrades)
- Cooldown suppression: same-category signal during 2000ms cooldown → no new event until cooldown expires
- Signed longitudinal detection: positive longitudinalG → ACCELERATION; negative → BRAKING; mutually exclusive per sample
- Absolute lateral detection: positive or negative lateralG → TURNING (same category)
- Bump rejection: above-caution longitudinalG + verticalG > 0.40g, signal drops before 200ms → candidate rejected
- Sustained maneuver during bump: above-caution longitudinalG + high verticalG, sustained ≥ 200ms → event passes normally
- Concurrent independent categories: simultaneous longitudinalG and lateralG above caution → both categories fire independently

**LogCsvExporter:**
- Correct header row: `timestamp,motion_category,severity`
- Empty list produces header only (no data rows)
- Lowercase enum values: `braking`, `acceleration`, `turning`, `caution`, `alert`
- ISO 8601 timestamps with timezone offset (e.g. `2026-04-10T15:42:18-04:00`)
- Row count matches input event count

### 9.2 Synthetic Sequence Tests
Create reusable motion sequences fed through the full pipeline (Processor → Evaluator):
- Clean braking event (negative longitudinalG) → one braking event, correct severity
- Clean acceleration event (positive longitudinalG) → one acceleration event, correct severity
- Sustained turning (positive or negative lateralG) → one turning event
- Threshold chatter → no excessive event spam
- Pothole spike (high verticalG + brief longitudinalG) → no event (rejected by persistence gate + bump rejection)
- Sustained maneuver during bump (high verticalG + sustained longitudinalG) → event passes persistence gate normally
- Severity escalation mid-event: signal starts above caution, exceeds alert during ACTIVE → one event finalized at ALERT severity
- Cooldown suppression: second event of same category arrives during cooldown window → suppressed, no second event emitted

### 9.3 Integration Tests (Deferred)
Android instrumentation tests (service lifecycle, Snackbar assertions, event routing, sensor failure handling) are deferred to manual validation in Phase 9. The synthetic sequence tests (9.2) validate the full processing pipeline, and the manual on-device tests (9.4–9.7) cover service behavior, UI state, and error paths. The cost-to-value ratio of instrumentation tests is poor for this project scope.

### 9.4 Connected Parking Lot Sessions (Part A)

Testing method: laptop connected to the phone via ADB in the vehicle. Claude streams Logcat live, correlates real-time sensor data with observed maneuvers, and assists with threshold tuning between runs. Workflow is iterative: maneuver → analyze live data → adjust ThresholdConfig if needed → rebuild and push APK → repeat.

**Functional validation:**
- Start Trip changes state from Inactive to Calibrating to Active
- End Trip changes state from Active to Inactive
- Ongoing notification appears during active monitoring with working End Trip action and correct content text
- Ongoing notification disappears after trip end
- Stable calibration succeeds (vehicle stationary)
- Calibration fails when phone is moved during startup; Snackbar appears

**Error handling:**
- Sensor unavailable → do not start monitoring
- Calibration failure → stop service, Snackbar, return to INACTIVE
- Repository write failure → continue monitoring
- CSV export failure → notify user via Snackbar, do not affect monitoring

**Threshold tuning (live data):**
- Braking runs at varying intensity — observe raw G values vs caution/alert thresholds
- Acceleration runs at varying intensity — observe raw G values vs caution/alert thresholds
- Turning runs at varying sharpness — observe raw G values vs caution/alert thresholds
- Bump/pothole test — confirm rejection, observe verticalG magnitudes vs bump threshold
- Verify acceleration vs braking classification using live signed longitudinalG values
- For each issue: capture maneuver type, observed G values, threshold hit/miss, evaluator state transitions

**Event logging and export:**
- Generate detectable events, confirm they appear in log with correct category and severity
- Confirm timestamp is correct and displays in device local timezone
- Export log to CSV and confirm header, lowercase values, and ISO 8601 timestamps

**Tuning iteration:**
- Adjust ThresholdConfig values based on live observations
- Rebuild and push updated APK to device
- Re-run the affected maneuver to confirm improvement
- Repeat until thresholds feel calibrated for the Ram 2500
- Document each adjustment using the Threshold Revision Entry template

### 9.5 Road Sessions (Part B)

Testing method: laptop on the passenger seat, USB cable to phone in its mount. Logcat is streamed to a file on the host for the full session — `adb logcat -d` post-drive is not used, as the default ring buffer (~256KB) is insufficient to retain 30+ minutes of lifecycle-level logs. No active interaction with the laptop is needed during the drive. Analysis happens post-drive with Claude using the captured log file and pulled Room database.

**Steps 9.8 and 9.9 are combined into one session** (30–45 minutes covers both the false-positive check and the battery/performance observation).

**Pre-drive setup (parked):**
- Connect phone via USB, confirm `adb devices`
- Start log capture: `adb logcat -s ThresholdEvaluator:V DriveMonitorService:V *:S > road_session.log`
- Note starting battery percentage

**Background behavior:**
- Start trip, open Google Maps, confirm monitoring continues
- Return to app, confirm status remains Active
- End trip, confirm status changes to Inactive

**False-positive validation:**
- Drive normally (highway, residential, mixed) for 30–45 minutes and check for event spam
- Confirm quiet driving does not produce unreasonable events

**Performance and battery:**
- Record ending battery percentage after the session
- Confirm no excessive device heat, UI lag, delayed notifications, or missed detections

**Post-drive analysis:**
- Stop log capture (Ctrl+C); pull Room database via `adb shell run-as` + `adb pull`
- Review all `Event finalized:` lines in the log against driving memory
- Identify any remaining false positives, missed detections, or classification errors
- If further tuning needed, return to parking lot session (9.4) for targeted adjustments
- Compare results against actual Emkay/Geotab behavior once available

### 9.6 Tuning Feedback Loop
For each observed issue capture: driving condition, mount position, road type, event category, observed vs expected result, suspected cause (threshold, filter, lifecycle). This enables structured iteration without random tuning. Parking lot sessions (9.4) produce immediate adjustments; road sessions (9.5) validate those adjustments under real conditions.

---

## Document 10. Threshold and Calibration Appendix

### 10.1 Purpose
This appendix exists to isolate motion-threshold decisions, tuning notes, and calibration history from the rest of the project documentation.

It serves four purposes:
- record the initial threshold values used for MVP development
- explain why those initial values were chosen
- provide a structured place to record threshold updates after testing
- preserve the reasoning behind each threshold revision so future AI-assisted development does not drift or undo changes without context

### 10.2 Current Provisional Threshold Set
These are the current working threshold values for initial MVP development and testing. All values are stored as unsigned magnitudes; the evaluator applies sign direction per category.

#### Caution Thresholds
- Acceleration caution: 0.24 G *(revised THR-004)*
- Braking caution: 0.13 G *(revised THR-002)*
- Turning caution: 0.26 G *(revised THR-003)*

#### Alert Thresholds
- Acceleration alert: 0.31 G *(revised THR-004)*
- Braking alert: 0.26 G *(revised THR-002)*
- Turning alert: 0.35 G

### 10.3 Current Provisional Processing Constants
These are the current working processing constants for initial MVP development and testing.

#### Motion Processing
- EMA alpha: 0.20
- Deadband / noise floor: 0.03g

#### Bump Rejection
- Bump vertical threshold: 0.40g

#### Calibration
- Calibration duration: 2.0 seconds
- Minimum valid sample count: 40
- Stability rejection threshold (RMS on any axis): 0.02g
- Maximum absolute deviation threshold on any axis: 0.05g
- Baseline computation: arithmetic mean of raw samples per axis

#### Event Lifecycle Timing
- Persistence Window: 200 ms
- Quiet Window: 750 ms
- Cooldown Window: 2000 ms

### 10.4 Vehicle Context for Current Values
These provisional values are being chosen for:
- Dodge Ram 2500
- GVW: 10,000 lbs
- phone-based trip monitoring app
- personal-use Android implementation

These values are intended as a practical starting point, not as confirmed parity with Emkay or Geotab scoring.

### 10.5 Initial Decision Logic
The initial values were chosen using the following logic:

1. The app is intended for a heavy pickup truck at the upper end of the light-duty range, not a passenger sedan.
2. Larger and heavier vehicles generally require lower harsh-event g-force thresholds than lighter passenger vehicles.
3. Generic passenger-vehicle-style thresholds were rejected as likely too high for a 10,000-lb GVW truck.
4. The selected provisional alert thresholds were chosen to align more closely with heavier-vehicle telematics guidance.
5. The selected provisional caution thresholds were set below the alert thresholds to create an early-warning band.
6. Caution values were intentionally kept close enough to alert values to be meaningful, but not so close that caution and alert become functionally identical.
7. The threshold set is expected to require revision after real-world testing because a phone mounted in a vehicle is not equivalent to a dedicated fleet telematics device.

### 10.6 Initial Threshold Rationale by Category
#### Acceleration
- Alert threshold was set to 0.35 G as a provisional heavy-vehicle starting point.
- Caution threshold was set to 0.28 G to provide warning before full alert severity.

#### Braking
- Alert threshold was set to 0.36 G as a provisional heavy-vehicle braking trigger.
- Caution threshold was set to 0.29 G to create a meaningful pre-alert band.

#### Turning
- Alert threshold was set to 0.35 G as a provisional hard-turn starting point for a 10,000-lb pickup.
- Caution threshold was set to 0.28 G to provide early warning before alert severity.

### 10.7 Known Limitations of Current Thresholds
- They are not yet confirmed against actual Emkay fleet rules.
- They are not yet confirmed against actual Geotab settings for the specific vehicle.
- They do not yet account for phone mount orientation differences (mitigated by fixed-mount requirement).
- They do not yet account for full-load versus unloaded vehicle behavior.
- They may produce false positives until smoothing, cooldowns, and real-world testing are completed.

### 10.8 Threshold Update Log Template
Use the following structure whenever thresholds or processing constants are revised.

#### Threshold Revision Entry
- Revision ID:
- Date:
- Updated by:
- Reason for change:
- Evidence source:
  - real-world test result
  - comparison to Emkay behavior
  - comparison to Geotab behavior
  - false positive reduction
  - missed-event correction
  - other
- Vehicle condition during testing:
  - unloaded / loaded
  - approximate payload
  - road type
  - phone mount position
- Previous threshold values:
  - Acceleration caution:
  - Acceleration alert:
  - Braking caution:
  - Braking alert:
  - Turning caution:
  - Turning alert:
- New threshold values:
  - Acceleration caution:
  - Acceleration alert:
  - Braking caution:
  - Braking alert:
  - Turning caution:
  - Turning alert:
- Previous processing constants (if changed):
  - EMA alpha:
  - Deadband:
  - Bump vertical threshold:
  - Persistence window:
  - Quiet window:
  - Cooldown window:
- New processing constants (if changed):
  - EMA alpha:
  - Deadband:
  - Bump vertical threshold:
  - Persistence window:
  - Quiet window:
  - Cooldown window:
- Decision logic for update:
- Expected effect of change:
- Follow-up validation needed:

### 10.9 Threshold Update Decision Rules
Any future threshold revision should answer these questions:
- What specific bad behavior prompted the change?
- Was the problem false positives, missed detections, or severity misclassification?
- Was the issue isolated to acceleration, braking, turning, or more than one category?
- Did the problem occur under normal driving, aggressive driving, rough road conditions, or unusual loading?
- Is the new value intended to reduce sensitivity or increase sensitivity?
- What evidence supports the new value?
- How will the new value be validated?

### 10.10 Calibration Notes Template
Use this section to capture real-world testing notes that may influence threshold changes.

#### Calibration Session Entry
- Session ID:
- Date:
- Device model:
- Android version:
- Vehicle:
- Vehicle load state:
- Phone mount type and position:
- App build version:
- Threshold set used:
- Processing constants used (EMA alpha, deadband, bump vertical, timing):
- Road conditions:
- Driving scenario:
- Observed caution events:
- Observed alert events:
- Suspected false positives:
- Suspected missed events:
- Notes:
- Recommendation:

### 10.11 CSV Export Format
The exported log file shall use the following header row:

`timestamp,motion_category,severity`

Each subsequent row shall represent one persisted event. Timestamps shall be formatted as ISO 8601 with explicit timezone offset. Cell values for motion_category and severity shall be lowercase.

Example:
```
timestamp,motion_category,severity
2026-04-10T15:42:18-04:00,braking,caution
2026-04-10T15:44:03-04:00,turning,alert
2026-04-10T15:46:11-04:00,acceleration,alert
```

### 10.12 Rules for AI Use of This Appendix
When using AI coding assistance on this project:
- treat the current threshold set in this appendix as the authoritative working values
- treat the current processing constants as the authoritative working values
- do not change threshold or processing values unless explicitly instructed
- preserve the update log history
- record reasoning for every threshold or constant revision
- prefer updating this appendix before changing implementation values in code when threshold changes are being discussed
- respect layer ownership boundaries when implementing changes

### 10.13 Baseline Threshold Revision Entry
This entry records the initial threshold set selected before real-world calibration.

#### Threshold Revision Entry
- Revision ID: THR-001
- Date: 2026-04-03
- Updated by: Project owner
- Reason for change: Establish initial baseline threshold set for MVP implementation
- Evidence source:
  - heavier-vehicle telematics guidance
  - project vehicle characteristics
  - initial design judgment for caution versus alert separation
- Vehicle condition during testing:
  - unloaded / loaded: not yet field-tested
  - approximate payload: unknown
  - road type: not yet field-tested
  - phone mount position: not yet finalized
- Previous threshold values:
  - Acceleration caution: none
  - Acceleration alert: none
  - Braking caution: none
  - Braking alert: none
  - Turning caution: none
  - Turning alert: none
- New threshold values:
  - Acceleration caution: 0.28 G
  - Acceleration alert: 0.35 G
  - Braking caution: 0.29 G
  - Braking alert: 0.36 G
  - Turning caution: 0.28 G
  - Turning alert: 0.35 G
- Decision logic for update:
  - The monitored vehicle is a Dodge Ram 2500 with a GVW of 10,000 lbs, which places it at the heavy end of light-duty vehicle use.
  - Passenger-car-oriented thresholds were considered likely too high for this vehicle class.
  - The initial alert thresholds were selected as conservative heavy-vehicle starting points rather than consumer-car defaults.
  - The caution thresholds were set below alert thresholds to create an early-warning layer while remaining close enough to be meaningful.
  - The values were intentionally chosen as provisional because phone-based sensing is expected to differ from dedicated fleet telematics hardware.
- Expected effect of change:
  - Provide a workable first-pass threshold configuration for implementation and initial road testing.
  - Reduce the risk of beginning with thresholds that are unrealistically high for a 10,000-lb pickup.
- Follow-up validation needed:
  - confirm behavior during real-world test drives
  - compare results against actual Emkay or Geotab event behavior when available
  - refine values based on false positives, missed events, and mount-specific sensor behavior

### 10.14 Baseline Calibration Session Placeholder
This placeholder reserves the first calibration session record for the first live vehicle test.

#### Calibration Session Entry
- Session ID: CAL-001
- Date: 2026-04-15
- Device model: Samsung SM-S931U (Galaxy S25)
- Android version: 16
- Vehicle: Dodge Ram 2500
- Vehicle load state: unloaded
- Phone mount type and position: dash/windshield mount, screen facing driver (portrait), Z-axis toward rear of vehicle
- App build version: 1.0 (versionCode 1)
- Threshold set used: THR-001 (initial run); THR-002 applied mid-session after analysis
- Processing constants used: EMA alpha 0.20, deadband 0.03g, bump vertical 0.40g, persistence 200ms, quiet 750ms, cooldown 2000ms
- Road conditions: parking lot, dry pavement, low speed
- Driving scenario: three controlled braking runs — light (~10–15 mph gradual stop), moderate (~20 mph firm stop), firm (~25–30 mph hard stop)
- Observed caution events: none under THR-001; confirmed under THR-002 (moderate braking)
- Observed alert events: firm braking produced ALERT under THR-001 (0.29g caution / 0.36g alert) only at the very top of the firm run; full expected behavior confirmed under THR-002
- Suspected false positives: none
- Suspected missed events: moderate braking missed entirely under THR-001 (peak -0.15g vs 0.29g threshold)
- Notes: Samsung Galaxy S25 (Android 16) suppresses Log.d from user-app processes; upgraded ThresholdEvaluator and DriveMonitorService diagnostic logs to Log.i to restore logcat visibility. Live DIAG data revealed actual EMA-filtered peaks: light = -0.10g, moderate = -0.15g, firm = -0.41g (peaking through -0.28g before dropping). Original 0.29g caution threshold sat above moderate braking output for this vehicle, requiring downward revision.
- Recommendation: Apply THR-002 braking threshold revision and re-validate all three runs.

---

### 10.15 Braking Threshold Revision — Parking Lot Session

#### Threshold Revision Entry
- Revision ID: THR-002
- Date: 2026-04-15
- Updated by: Project owner (AI-assisted)
- Reason for change: Braking caution and alert thresholds were too high for the Ram 2500. Under THR-001, light and moderate braking produced no events. Only firm braking triggered a response, and only near the top of the intensity range. Moderate braking (a routine firm stop from ~20 mph) should trigger CAUTION.
- Evidence source: real-world test result
- Vehicle condition during testing:
  - unloaded / loaded: unloaded
  - approximate payload: none
  - road type: dry parking lot
  - phone mount position: screen facing driver, portrait, Z-axis pointing toward rear of vehicle
- Previous threshold values:
  - Acceleration caution: 0.28 G
  - Acceleration alert: 0.35 G
  - Braking caution: 0.29 G
  - Braking alert: 0.36 G
  - Turning caution: 0.28 G
  - Turning alert: 0.35 G
- New threshold values:
  - Acceleration caution: 0.28 G (unchanged)
  - Acceleration alert: 0.35 G (unchanged)
  - Braking caution: 0.13 G
  - Braking alert: 0.26 G
  - Turning caution: 0.28 G (unchanged)
  - Turning alert: 0.35 G (unchanged)
- Previous processing constants (if changed): none changed
- New processing constants (if changed): none changed
- Decision logic for update:
  - Live DIAG logging captured EMA-filtered longitudinalG peaks during three parking lot braking runs: light = -0.10g, moderate = -0.15g, firm peak = -0.41g (threshold crossed at -0.28g on the way up).
  - The 0.29g caution threshold was above the moderate braking ceiling (-0.15g), so moderate braking never fired under THR-001.
  - New caution threshold of 0.13g sits between the light (-0.10g) and moderate (-0.15g) peaks, providing a clear margin above noise while catching routine firm stops.
  - New alert threshold of 0.26g provides a meaningful separation from moderate braking (-0.15g) and is well below the firm braking onset (-0.28g), ensuring firm stops escalate to ALERT quickly.
- Expected effect of change:
  - Light braking (~0.10g): no event
  - Moderate braking (~0.15g): CAUTION, category BRAKING
  - Firm braking (~0.41g, onset at ~0.28g): ALERT, category BRAKING
- Follow-up validation needed:
  - Re-run all three braking maneuvers with THR-002 values to confirm expected caution/alert/no-event behavior
  - Confirm no false positives during normal driving on a future road session

---

### 10.16 Acceleration Threshold Tuning — Parking Lot Session (Deferred)

#### Calibration Session Entry
- Session ID: CAL-002
- Date: 2026-04-15
- Device model: Samsung SM-S931U (Galaxy S25)
- Android version: 16
- Vehicle: Dodge Ram 2500
- Vehicle load state: unloaded
- Phone mount type and position: screen facing driver, portrait, Z-axis toward rear of vehicle
- App build version: 1.0 (versionCode 1)
- Threshold set used: THR-001 (acceleration values unchanged — 0.28g caution / 0.35g alert)
- Processing constants used: EMA alpha 0.20, deadband 0.03g, bump vertical 0.40g, persistence 200ms, quiet 750ms, cooldown 2000ms
- Road conditions: parking lot, dry pavement, low speed
- Driving scenario: six controlled acceleration runs across two sessions — gentle, moderate, and hard throttle pulls from a stop
- Observed caution events: none (no ACCELERATION events fired across all six runs)
- Observed alert events: none
- Suspected false positives: none for acceleration; BRAKING CAUTION events correctly fired at end of each run during deceleration
- Suspected missed events: all three acceleration intensities registered similar EMA-filtered peaks — gentle ~0.10–0.12g, moderate ~0.08g, hard ~0.11–0.14g — all well below the 0.28g caution threshold
- Notes: Parking lot space is insufficient for meaningful acceleration threshold tuning. The available run distance (~1–2 seconds of sustained throttle before needing to stop) prevents the EMA filter from building up to realistic peak values. All three intensity levels produced nearly identical filtered output with no usable separation. The 0.28g/0.35g acceleration thresholds remain unvalidated against real-world data. No threshold change was made.
- Recommendation: defer acceleration threshold tuning to Step 9.5 (first real-road drive). A highway on-ramp or open straight road with 4–5 seconds of sustained full-throttle acceleration is needed for the EMA to converge and produce meaningful caution/alert separation. Expect thresholds to require significant downward revision after road testing, consistent with the braking experience in CAL-001.

---

### 10.17 Turning Threshold Tuning — Parking Lot Session

#### Calibration Session Entry
- Session ID: CAL-003
- Date: 2026-04-15
- Device model: Samsung SM-S931U (Galaxy S25)
- Android version: 16
- Vehicle: Dodge Ram 2500
- Vehicle load state: unloaded
- Phone mount type and position: screen facing driver, portrait, Z-axis toward rear of vehicle
- App build version: 1.0 (versionCode 1)
- Threshold set used: THR-001 (turning values — 0.28g caution / 0.35g alert)
- Processing constants used: EMA alpha 0.20, deadband 0.03g, bump vertical 0.40g, persistence 200ms, quiet 750ms, cooldown 2000ms
- Road conditions: parking lot, dry pavement, low speed
- Driving scenario: three controlled turning runs — gentle, moderate, and sharp turns
- Observed caution events: sharp turn fired CAUTION; gentle and moderate turns produced no events as expected
- Observed alert events: none
- Suspected false positives: none
- Suspected missed events: moderate turning produced EMA-filtered peaks of ~0.229g — below the 0.28g caution threshold and correctly produced no event; however the gap between moderate (~0.229g) and the 0.28g threshold is narrow
- EMA-filtered lateralG peaks observed:
  - Gentle turn: ~0.198g
  - Moderate turn: ~0.229g
  - Sharp turn: ~0.342–0.457g (multiple peaks across the turn)
- Notes: The 0.28g caution threshold correctly differentiated between sharp and moderate turns, but the margin above moderate (~0.051g) was judged too narrow. Lowering to 0.26g increases the margin above moderate (~0.031g) while still providing clear separation from the sharp turn onset (~0.342g).
- Recommendation: Lower TURNING_CAUTION_G from 0.28g to 0.26g (THR-003). Run confirmation turns after threshold change to verify moderate still misses and sharp still fires.

#### Threshold Revision Entry
- Revision ID: THR-003
- Date: 2026-04-15
- Updated by: Project owner (AI-assisted)
- Reason for change: The 0.28g turning caution threshold left only a ~0.051g margin above the observed moderate turn peak (~0.229g). Lowering to 0.26g widens that margin to ~0.031g while keeping the threshold well below the sharp turn onset (~0.342g).
- Evidence source: real-world test result
- Vehicle condition during testing:
  - unloaded / loaded: unloaded
  - approximate payload: none
  - road type: dry parking lot
  - phone mount position: screen facing driver, portrait, Z-axis pointing toward rear of vehicle
- Previous threshold values:
  - Acceleration caution: 0.28 G (unchanged)
  - Acceleration alert: 0.35 G (unchanged)
  - Braking caution: 0.13 G (unchanged)
  - Braking alert: 0.26 G (unchanged)
  - Turning caution: 0.28 G
  - Turning alert: 0.35 G (unchanged)
- New threshold values:
  - Acceleration caution: 0.28 G (unchanged)
  - Acceleration alert: 0.35 G (unchanged)
  - Braking caution: 0.13 G (unchanged)
  - Braking alert: 0.26 G (unchanged)
  - Turning caution: 0.26 G
  - Turning alert: 0.35 G (unchanged)
- Previous processing constants (if changed): none changed
- New processing constants (if changed): none changed
- Decision logic for update:
  - DIAG logging captured EMA-filtered lateralG peaks: gentle ~0.198g, moderate ~0.229g, sharp ~0.342–0.457g.
  - Original 0.28g threshold sits only 0.051g above the moderate peak. A single firmer-than-average moderate turn could cross the threshold unexpectedly.
  - Revised 0.26g threshold sits 0.031g above moderate peak and 0.082g below the sharp turn onset — still a comfortable gap.
  - Alert threshold of 0.35g remains well above moderate (~0.229g) and below the upper range of sharp turns (~0.457g), preserving correct CAUTION/ALERT escalation for sharp maneuvers.
- Expected effect of change:
  - Gentle turning (~0.198g): no event
  - Moderate turning (~0.229g): no event
  - Sharp turning (~0.342–0.457g): CAUTION (onset), escalating to ALERT at sustained sharp input
- Follow-up validation needed:
  - Re-run gentle, moderate, and sharp turns with THR-003 values to confirm no-event / CAUTION / CAUTION-or-ALERT behavior
  - Confirm no false positives during normal driving on a future road session

---

### 10.18 Acceleration Threshold Tuning — Road Session

#### Calibration Session Entry
- Session ID: CAL-004
- Date: 2026-04-15
- Device model: Samsung SM-S931U (Galaxy S25)
- Android version: 16
- Vehicle: Dodge Ram 2500
- Vehicle load state: unloaded
- Phone mount type and position: screen facing driver, portrait, Z-axis toward rear of vehicle
- App build version: 1.0 (versionCode 1)
- Threshold set used: THR-001 (acceleration values unchanged — 0.28g caution / 0.35g alert)
- Processing constants used: EMA alpha 0.20, deadband 0.03g, bump vertical 0.40g, persistence 200ms, quiet 750ms, cooldown 2000ms
- Road conditions: longer parking lot stretch + short road segment
- Driving scenario: three controlled acceleration runs — gentle, moderate, and hard throttle; hard run performed on a longer road segment to allow EMA convergence
- Observed caution events: one ACCELERATION CAUTION on the hard road run (peak ~0.322g)
- Observed alert events: none (hard run peak 0.322g fell short of 0.35g alert threshold)
- Suspected false positives: none
- Suspected missed events: gentle (~0.082g) and moderate (~0.174g) produced no events under THR-001; this is acceptable behavior — moderate acceleration on the Ram 2500 is everyday driving
- EMA-filtered longitudinalG peaks observed:
  - Gentle: ~0.082g
  - Moderate: ~0.174g
  - Hard (road): ~0.322g (EMA still converging at peak — true sustained value likely higher)
- Notes: The longer road segment gave the EMA enough time to converge. The current 0.28g caution threshold was validated — hard acceleration correctly fired CAUTION with a peak of 0.322g. The 0.35g alert threshold was not reached; the hard run peaked at 0.322g. Slight downward adjustments to both thresholds are warranted: lowering caution to 0.24g fires CAUTION earlier in hard acceleration buildup; lowering alert to 0.31g places the alert threshold just below the observed hard peak, so a sustained hard pull escalates to ALERT.
- Recommendation: Apply THR-004 (caution 0.24g, alert 0.31g). Run confirmation passes to verify gentle and moderate still produce no event, hard produces CAUTION escalating to ALERT.

#### Threshold Revision Entry
- Revision ID: THR-004
- Date: 2026-04-15
- Updated by: Project owner (AI-assisted)
- Reason for change: The 0.28g caution threshold was validated as functional but slightly high — hard acceleration crossed it late in the EMA buildup. Lowering to 0.24g catches hard acceleration earlier. The 0.35g alert threshold was never reached under test conditions; lowering to 0.31g puts it just below the observed hard peak (0.322g) so sustained hard acceleration escalates to ALERT.
- Evidence source: real-world test result (parking lot + road segment)
- Vehicle condition during testing:
  - unloaded / loaded: unloaded
  - approximate payload: none
  - road type: parking lot and short road segment, dry pavement
  - phone mount position: screen facing driver, portrait, Z-axis pointing toward rear of vehicle
- Previous threshold values:
  - Acceleration caution: 0.28 G
  - Acceleration alert: 0.35 G
  - Braking caution: 0.13 G (unchanged)
  - Braking alert: 0.26 G (unchanged)
  - Turning caution: 0.26 G (unchanged)
  - Turning alert: 0.35 G (unchanged)
- New threshold values:
  - Acceleration caution: 0.24 G
  - Acceleration alert: 0.31 G
  - Braking caution: 0.13 G (unchanged)
  - Braking alert: 0.26 G (unchanged)
  - Turning caution: 0.26 G (unchanged)
  - Turning alert: 0.35 G (unchanged)
- Previous processing constants (if changed): none changed
- New processing constants (if changed): none changed
- Decision logic for update:
  - Hard acceleration EMA-filtered peak on road: ~0.322g. Under THR-001 (0.28g caution), the event fired correctly but only after ~600ms of sustained full throttle.
  - Moderate acceleration peak: ~0.174g — well below both old and new caution thresholds; correctly produces no event.
  - New caution of 0.24g sits between moderate (0.174g) and the hard run onset (~0.273g where CANDIDATE first fired), providing a narrower gap and earlier detection on hard acceleration.
  - New alert of 0.31g sits just below the hard run peak (0.322g), so a sustained hard acceleration run should escalate from CAUTION to ALERT.
- Expected effect of change:
  - Gentle acceleration (~0.082g): no event
  - Moderate acceleration (~0.174g): no event
  - Hard acceleration (~0.322g): CAUTION at onset, escalating to ALERT
- Follow-up validation needed:
  - Re-run gentle, moderate, and hard acceleration with THR-004 values to confirm no-event / no-event / CAUTION→ALERT behavior
  - Confirm no false positives during normal driving on a future road session

---

### 10.19 Bump Rejection Validation — Parking Lot Session

#### Calibration Session Entry
- Session ID: CAL-005
- Date: 2026-04-15
- Device model: Samsung SM-S931U (Galaxy S25)
- Android version: 16
- Vehicle: Dodge Ram 2500
- Vehicle load state: unloaded
- Phone mount type and position: screen facing driver, portrait, Z-axis toward rear of vehicle
- App build version: 1.0 (versionCode 1)
- Threshold set used: THR-004 (all current values)
- Processing constants used: EMA alpha 0.20, deadband 0.03g, bump vertical 0.40g, persistence 200ms, quiet 750ms, cooldown 2000ms
- Road conditions: parking lot, dry pavement, low speed
- Driving scenario: two controlled bump passes — (1) coasting over a speed bump with no braking or steering input; (2) braking firmly through the same bump from ~15–20 mph, holding the brake before, through, and past the bump
- Observed caution events: BRAKING CAUTION on pass 2 (braking through bump) — correct behavior
- Observed alert events: none
- Suspected false positives: none — coasting pass produced no events despite vertical shocks
- EMA-filtered verticalG peaks observed:
  - Coasting passes: ~0.22–0.26g (two crossings)
  - Braking-through pass: ~0.26g peak during active braking event
- Notes: The 0.40g BUMP_VERTICAL_THRESHOLD_G was never crossed. The available parking lot speed bumps produce a maximum of approximately 0.26g vertical, well below the rejection threshold. The persistence gate (200ms minimum sustained signal) is the primary mechanism preventing false positives from bumps — this was confirmed correct. The dedicated bump rejection code path (bumpDetected flag in ThresholdEvaluator) requires verticalG > 0.40g to activate; this path remains unexercised and would require a sharper bump or pothole to validate. No threshold changes warranted.
- Recommendation: Accept current BUMP_VERTICAL_THRESHOLD_G = 0.40g. The behavior is correct — bumps that don't produce sustained G-force above the caution thresholds produce no events regardless of the vertical threshold. The 0.40g threshold is a guard against larger shocks (potholes, railroad crossings) coincidentally crossing the braking/turning threshold; this scenario was not reproducible in the parking lot and is deferred to real-road validation.
