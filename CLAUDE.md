# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Vigie is an Android (Java, minSdk 26) surveillance app. It keeps a persistent MQTT connection through an Android foreground service and exposes five tabs that mix MQTT-driven data with external HTTP APIs.

The current app has **5 tabs**: Messages, **Infra** (LAN + Internet + Backup), Météo, Train, Voiture — note that the original LAN tab was merged into a unified Infra tab (commit `d8e3c98`).

## Build / run

JDK 17, Android Gradle Plugin 8.2.2, Java source/target 1.8. Use the wrapper:

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # signed release (needs keystore env vars below)
./gradlew installDebug           # install to attached device/emulator
./gradlew lint                   # Android lint
./gradlew clean
```

There are **no unit/instrumentation tests** in the repo (no `src/test` or `src/androidTest` source sets). Don't claim test coverage that doesn't exist.

Release signing is driven by env vars (see `app/build.gradle` `signingConfigs.release`):
`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. CI (`.github/workflows/build-apk.yml`) decodes a base64 keystore from `secrets.KEYSTORE_BASE64` on push to `main` or tag `v*`, runs `assembleRelease`, and publishes a GitHub Release with the APK. Pushes to `main` create a `dev-<sha>` prerelease tag; semver tags create stable releases.

## Architecture

### Foreground service is the source of truth

`MqttService` (in `app/src/main/java/com/alixpat/vigie/MqttService.java`) is a `START_STICKY` foreground service holding a `PARTIAL_WAKE_LOCK`. It owns the Paho `MqttClient`, subscribes to `vigie/#`, and stays alive when the activity is gone. Three patterns to know:

1. **Static caches on `MqttService`** (`messageHistory`, `lanHostsCache`, `backupJobsCache`, `internetCache`, `currentStatus`) bridge the service to fragments. Fragments hydrate from these statics on resume rather than via binder/IPC. When adding new MQTT-driven state, follow the same pattern: a `volatile`/`Collections.synchronized*` static plus a `getXxx()` accessor.
2. **Package-scoped broadcasts** (`setPackage(getPackageName())`) push live updates: `MqttService.ACTION_STATUS` for connection status; `InfraFragment.ACTION_LAN_STATUS` / `ACTION_BACKUP_STATUS` / `ACTION_INTERNET_STATUS` and `com.alixpat.vigie.MESSAGE_RECEIVED` for payloads. Receivers must register with `RECEIVER_NOT_EXPORTED` on Tiramisu+ (see `MainActivity.onResume`).
3. **Routing of incoming MQTT messages** is by JSON-shape detection in `messageArrived`. Order matters — each `fromJson` only returns non-null when the JSON's `type` field matches: `LanHost` (`lan_status`) → `BackupJob` (`backup_status`) → `InternetStatus` (`internet_status`) → `VigieMessage` (anything else). Adding a new message type means adding another model with a `type`-checking `fromJson` plus a dispatch branch.

Reconnection has two layers: Paho's `automaticReconnect`, plus a `ConnectivityManager.NetworkCallback` that calls `mqttClient.reconnect()` (or recreates the client) when the network returns. Don't disable either without understanding the other.

### Tabs and data flows

`MainActivity` is a `ViewPager2` + `TabLayout`. `ViewPagerAdapter` (positions 0..4) maps to fragments:

| # | Tab | Source | Notes |
|---|---|---|---|
| 0 | Messages | MQTT (`vigie/*`, `VigieMessage`) | History on `MqttService.messageHistory`; notifications via `NotificationHelper` |
| 1 | Infra | MQTT — three message types: `LanHost`, `BackupJob`, `InternetStatus` | Single fragment with three sections; each fed by its own broadcast action and its own static cache on `MqttService` |
| 2 | Météo | HTTP `api.open-meteo.com` (no key) | Hardcoded city coords in `WeatherFragment.CITIES` |
| 3 | Train | IDFM PRIM REST API (5 endpoints) | Needs `idfmToken` in `BrokerConfig`; line hardcoded to SNCF Ligne N (`STIF:Line::C01736:`) |
| 4 | Voiture | TomTom Routing API | Needs `tomtomApiKey` in `BrokerConfig`; rolling 30-min average history in SharedPreferences `vigie_driving_history` |

`MainActivity` persists the last selected tab in SharedPreferences `vigie_prefs` / `last_tab_position`.

### `BrokerConfig` is the single settings store

Despite the name, `BrokerConfig` (`SharedPreferences("vigie_prefs")`) holds **all** user-editable secrets — broker IP/port/credentials, `idfmToken`, `tomtomApiKey`. `SettingsActivity` is the only writer. When adding a new external integration that needs a key, extend `BrokerConfig` and `activity_settings.xml` rather than introducing a parallel store. Note `vigie_prefs` is also reused by `MainActivity` for `last_tab_position` and by `VoitureFragment` for `vigie_driving_history` (different prefs file) — be careful which preference name you read/write.

### Train tab specifics

`TrainFragment` is the largest file in the app (~2k lines) and orchestrates five IDFM PRIM endpoints: `general-message`, `stop-monitoring`, `estimated-timetable`, Navitia v2 `stop_points` discovery, and Navitia v2 `line_reports` (added in commit `65219c6` for richer perturbation data). Direction routing (Aller / Retour) is determined by string-matching destination names against `DESTINATIONS_VERS_VILLEPREUX` / `DESTINATIONS_VERS_PARIS`. Incident classification (perturbation vs. travaux vs. blocking) is keyword-based. The custom `LineMapView` renders the line schematic; train detail dialogs combine `estimated-timetable` data with on-demand `stop-monitoring` calls for `OnwardCalls`.

All HTTP calls in train/weather/voiture are plain `HttpURLConnection` on a single-thread `ExecutorService`, results posted back via a main-`Handler`. There is no Retrofit/OkHttp/coroutine layer; match the existing pattern when adding endpoints.

## Conventions worth following

- Java only — no Kotlin in the project.
- Keep MQTT-driven state in static caches on `MqttService`; keep API-driven state inside its fragment.
- Permissions in `AndroidManifest.xml` are minimal (INTERNET, FOREGROUND_SERVICE[_DATA_SYNC], POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED). The `dataSync` foreground service type is what justifies the persistent MQTT connection — don't switch to a different type casually.
- User-visible strings are in French; tab titles, status labels, etc. should match. Logging uses French too (`Log.i(TAG, "Connecté"…)`).
- Communication-with-claude rule from `.claude/settings.local.json`: WebFetch is preallowed for `prim.iledefrance-mobilites.fr`, `doc.navitia.io`, `idfm-api.readthedocs.io`, `data.iledefrance-mobilites.fr` — use these for IDFM API questions instead of guessing.
