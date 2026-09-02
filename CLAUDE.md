# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Vigie is an Android (Java, minSdk 26) surveillance app. It keeps a persistent MQTT connection through an Android foreground service and exposes five tabs that mix MQTT-driven data with external HTTP APIs.

The current app has **6 tabs**: Messages, **Infra** (LAN + Internet + Backup), Météo, Train, Voiture, Capteurs (TTN/LoRaWAN sensors via the `capteur-ttn` bridge) — note that the original LAN tab was merged into a unified Infra tab (commit `d8e3c98`).

## Build / run

JDK 17, Android Gradle Plugin 8.2.2, Java source/target 1.8. Use the wrapper:

```bash
./gradlew assembleDebug          # debug APK -> app/build/outputs/apk/debug/
./gradlew assembleRelease        # signed release (needs keystore env vars below)
./gradlew installDebug           # install to attached device/emulator
./gradlew lint                   # Android lint
./gradlew testDebugUnitTest      # JVM unit tests (src/test)
./gradlew clean
```

JVM unit tests live in `app/src/test/java/com/alixpat/vigie/`:

- **`model/`** — covers the five MQTT JSON parsers (`LanHost`, `BackupJob`, `InternetStatus`, `SensorStatus`, `VigieMessage`) plus a `RoutingCascadeTest` that locks in the cascade invariant used by `MqttService.messageArrived`: for any payload, exactly one typed parser accepts (or all reject and `VigieMessage` fallback handles it). If you change a `fromJson` filter, run `testDebugUnitTest` — silent mis-classification is the bug class these tests prevent.
- **`train/`** — covers the pure helpers extracted from `TrainFragment` (`IncidentClassifier`, `LineNDirection`, `IdfmClient`, `TrainPosition`, `JourneyRoutes`, `LineSegment`, `OngoingTrains`). Touching keyword arrays, direction matching, URL construction, or the ongoing-train selection rules without running tests is asking for a regression.

There is no `src/androidTest` source set yet. CI runs `testDebugUnitTest` before `assembleRelease`, so a failing test blocks the APK build and uploads the HTML report as a `test-report` artifact.

Release signing is driven by env vars (see `app/build.gradle` `signingConfigs.release`):
`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. CI (`.github/workflows/build-apk.yml`) decodes a base64 keystore from `secrets.KEYSTORE_BASE64` on push to `main` or tag `v*`, runs `assembleRelease`, and publishes a GitHub Release with the APK. Pushes to `main` create a `dev-<sha>` prerelease tag; semver tags create stable releases.

## Architecture

### Foreground service is the source of truth

`MqttService` (in `app/src/main/java/com/alixpat/vigie/MqttService.java`) is a `START_STICKY` foreground service holding a `PARTIAL_WAKE_LOCK`. It owns the Paho `MqttClient`, subscribes to `vigie/#`, and stays alive when the activity is gone. Three patterns to know:

1. **Static caches on `MqttService`** (`messageHistory`, `lanHostsCache`, `backupJobsCache`, `internetCache`, `currentStatus`) bridge the service to fragments. Fragments hydrate from these statics on resume rather than via binder/IPC. When adding new MQTT-driven state, follow the same pattern: a `volatile`/`Collections.synchronized*` static plus a `getXxx()` accessor.
2. **Package-scoped broadcasts** (`setPackage(getPackageName())`) push live updates: `MqttService.ACTION_STATUS` for connection status; `InfraFragment.ACTION_LAN_STATUS` / `ACTION_BACKUP_STATUS` / `ACTION_INTERNET_STATUS`, `CapteursFragment.ACTION_SENSOR_STATUS` and `com.alixpat.vigie.MESSAGE_RECEIVED` for payloads. Receivers must register with `RECEIVER_NOT_EXPORTED` on Tiramisu+ (see `MainActivity.onResume`).
3. **Routing of incoming MQTT messages** is by JSON-shape detection in `messageArrived`. Order matters — each `fromJson` only returns non-null when the JSON's `type` field matches: `LanHost` (`lan_status`) → `BackupJob` (`backup_status`) → `InternetStatus` (`internet_status`) → `SensorStatus` (`sensor_status`) → `VigieMessage` (anything else). Adding a new message type means adding another model with a `type`-checking `fromJson` plus a dispatch branch.

Reconnection has two layers: Paho's `automaticReconnect`, plus a `ConnectivityManager.NetworkCallback` that calls `mqttClient.reconnect()` (or recreates the client) when the network returns. Don't disable either without understanding the other.

### Tabs and data flows

`MainActivity` is a `ViewPager2` + `TabLayout`. `ViewPagerAdapter` (positions 0..5) maps to fragments:

| # | Tab | Source | Notes |
|---|---|---|---|
| 0 | Messages | MQTT (`vigie/*`, `VigieMessage`) | History on `MqttService.messageHistory`; notifications via `NotificationHelper` |
| 1 | Infra | MQTT — three message types: `LanHost`, `BackupJob`, `InternetStatus` | Single fragment with three sections; each fed by its own broadcast action and its own static cache on `MqttService` |
| 2 | Météo | HTTP `api.open-meteo.com` (no key) | Hardcoded city coords in `WeatherFragment.CITIES` |
| 3 | Train | IDFM PRIM REST API (5 endpoints) | Needs `idfmToken` in `BrokerConfig`; line hardcoded to SNCF Ligne N (`STIF:Line::C01736:`). Section *Trains entre Clamart et Villepreux* en tête : **tous** les trains physiquement sur le segment à l'instant T, dans un sens ou dans l'autre, que je sois dedans ou non ; position rafraîchie toutes les 20 s sans appel réseau |
| 4 | Voiture | TomTom Routing API | Needs `tomtomApiKey` in `BrokerConfig`; rolling 30-min average history in SharedPreferences `vigie_driving_history` |
| 5 | Capteurs | MQTT (`vigie/sensors/*`, `SensorStatus`) | TTN/LoRaWAN sensors relayed by the `capteur-ttn` bridge. Per-`kind` rendering in `SensorAdapter` (currently `door`; generic key:value fallback otherwise). Adding a new sensor type = a new branch in `SensorAdapter.renderFor`. |

The toolbar `MenuItem` `action_alarm` (next to settings ⚙) is the **alarm toggle** — bell icon (`ic_alarm_bell`) when on, crossed-out bell (`ic_alarm_bell_off`) when off. State is backed by `Settings.isAlarmEnabled()` (pref `alarm_enabled`, default false), redrawn via `invalidateOptionsMenu()` after each toggle. When the toggle is on, `MqttService` watches every incoming `SensorStatus` whose payload-level `alarm` flag is true: on a transition into the kind-specific alert state (`door` → `DOOR_OPEN_STATUS=1`, `motion` → `Occupy=1`), it synthesises a high-priority `VigieMessage` ("Alarme — \<name\>", "Porte ouverte" / "Présence détectée") and pushes it through the standard pipeline (history + system notif via `NotificationHelper` + `MESSAGE_RECEIVED` broadcast). The alert state is derived in `MqttService.isInAlertState`; new kinds need a new branch there. The `alarm` flag itself comes from the per-device config in `capteur-ttn/config.json` (`{"name": "...", "kind": "...", "alarm": true}`).

The Messages tab shows history newest-first (`MessageAdapter` reverses on `setMessages`, inserts at index 0 on `addMessage`). An "Effacer" button at the top wipes both the static `MqttService.messageHistory` (via `clearMessageHistory()`) and the adapter's local list.

`MainActivity` persists the last selected tab in SharedPreferences `vigie_prefs` / `last_tab_position`.

### `BrokerConfig` is the single settings store

Despite the name, `BrokerConfig` (`SharedPreferences("vigie_prefs")`) holds **all** user-editable secrets — broker IP/port/credentials, `idfmToken`, `tomtomApiKey`. `SettingsActivity` is the only writer. When adding a new external integration that needs a key, extend `BrokerConfig` and `activity_settings.xml` rather than introducing a parallel store. Note `vigie_prefs` is also reused by `MainActivity` for `last_tab_position` and by `VoitureFragment` for `vigie_driving_history` (different prefs file) — be careful which preference name you read/write.

### Train tab specifics

`TrainFragment` orchestrates five IDFM PRIM endpoints: `general-message`, `stop-monitoring`, `estimated-timetable`, Navitia v2 `stop_points` discovery, and Navitia v2 `line_reports`. The pure logic has been extracted into `com.alixpat.vigie.train.*` — **add new train logic there, not in the fragment**:

- **`IdfmClient`** — HTTP layer. Constructor takes `(lineRef, navitiaLineId)`; the five `fetchXxx(token, ...)` methods return raw JSON strings and throw `IdfmClient.HttpException` (extends `IOException`) on non-200 responses. `HttpException.isServerError()` drives the `stop-monitoring` retry loop. Adding an endpoint = a new method here, not a sixth copy of HttpURLConnection boilerplate.
- **`LineNDirection`** — two static instances (`ALLER`, `RETOUR`) bundling origin/destination stop refs, human names, and destination keywords. `matchesDestination(String)` is the case-insensitive substring matcher used by `buildCrossReferencedSchedules`.
- **`IncidentClassifier`** — keyword-based classification of perturbation / travaux / blocking. Used by both the `general-message` parser (entirely via `classifyMessage(text, channel)`) and the `line_reports` parser (via the three `hasXxxKeyword` helpers).
- **`StopVisit`** — sac de données d'un `MonitoredStopVisit` SIRI (ex-`TrainFragment.RawStopVisit`). Produit par `TrainFragment.parseRawStopVisits`, consommé par `buildCrossReferencedSchedules` et `OngoingTrains`.
- **`TrainPosition`** — position d'un train sur son parcours à un instant **injecté** (`compute(stops, now)` / `statusAt(stop, now)`), donc reproductible : à quai, entre deux gares, pas encore parti, arrivé, + un avancement en %. `TrainStop.getStatus()` (qui lit l'horloge système) reste pour les autres appelants.
- **`JourneyRoutes`** — mémoire du parcours de chaque train (`journeyStopsCache`). L'`estimated-timetable` IDFM ne décrit un train **que tel qu'il lui reste à circuler** : les `RecordedCalls` sont facultatifs dans le profil SIRI et ne sont pas garantis, donc un train qui vient de quitter Clamart n'a plus Clamart dans ses `EstimatedCalls`. `merge(known, fresh)` fusionne au lieu d'écraser — sans quoi le début du trajet s'efface au fur et à mesure que le train avance, et on ne peut plus savoir qu'il dessert ma gare de départ. Les arrêts sont appariés sur leur `StopPointRef` (`TrainStop.getStopRef()`), stable même quand le nom n'a pas été résolu ("Arrêt 43111") ; `withEndsMarked` recalcule les drapeaux origine/terminus, que chaque réponse pose sur le premier arrêt *restant* ; `purge` oublie les parcours vieux de plus de 3 h.
- **`LineSegment`** — le tronçon de ligne entre mes deux gares, en suite ordonnée de gares (« corridor »), construit depuis `LineNStation` (tronc commun Paris → Saint-Cyr + branche Mantes). Sert à situer un train dont le parcours ne contient plus ma gare de départ. Un couple de gares hors de cet axe (branches Rambouillet / Dreux) donne un segment vide, et l'appelant retombe sur les horaires.
- **`OngoingTrains`** — inventaire des trains présents sur mon segment à l'instant T : **tous** ceux qui se trouvent entre mes deux gares, que je sois dedans ou non. `describe` produit l'`OngoingTrain` prêt à afficher ; `isOngoing`/`isUpcoming`/`selectUpcoming` partagent les listes de départs. Le cœur est `locate(stops, direction, now)` → `ON_SEGMENT` / `OFF_SEGMENT` / `UNKNOWN`, qui pose une question de **position** (« où est ce train maintenant ? ») et non d'heure de départ : l'estimated-timetable ne décrivant que les arrêts restants, ma gare de départ disparaît du parcours dès qu'elle est franchie, donc sélectionner sur l'heure de départ fait disparaître le train au moment précis où il roule sur mon segment. L'ancre est le **prochain arrêt** (premier arrêt non encore quitté, toujours publié puisqu'à venir) : le train est sur le segment si ce prochain arrêt est au plus ma gare d'arrivée et strictement au-delà de ma gare de départ — position lue dans le parcours quand ma gare de départ y figure encore, sinon dans le corridor `LineSegment`. `UNKNOWN` (parcours absent ou sans ma gare d'arrivée) retombe sur l'ancienne règle horaire. Compromis assumé : un train ayant franchi ma gare de départ sans que l'app ait observé ce passage est affiché **sans heure de départ** (« non communiqué ») plutôt qu'écarté — rien ne prouve plus qu'il la desservait (un semi-direct a pu la sauter), mais un train manquant est plus gênant qu'un train en trop. Le stop-monitoring des deux gares n'enrichit plus que retard réel / voie / numéro de train, et sert de secours. Tri par heure d'arrivée croissante (le prochain à arriver chez moi en tête) — il reste défini quand l'heure de départ est inconnue. `TrainFragment.collectOngoing` rejoue `locate` à chaque tick de 20 s, et `rebuildOngoingFromLastFetch` rejoue la construction après chaque estimated-timetable (le parcours arrive après les horaires au premier chargement). Attention : `effectiveDepartureMillis` lit `TrainSchedule.getExpectedDepartureMillis()` (heure réelle de départ, renseignée à la construction) et ne retombe sur `aimed + delayMinutes` que si elle vaut 0 — un retard pris en route ne doit pas décaler l'heure à laquelle le train est parti.

Les horaires sont ramenés sur une fenêtre `[now - 30 min, now + 2 h]` : `OngoingTrains.selectUpcoming` alimente les deux listes de départs, le reste bascule dans la section *En circulation*. Un tick local (`POSITION_TICK_MS`, 20 s) rejoue ce partage et recalcule les positions sans appel réseau ; seul `fetchSchedules` (5 min) refait les requêtes.

The custom `LineMapView` renders the line schematic; train detail dialogs still live in the fragment and combine `estimated-timetable` data with on-demand `stop-monitoring` calls for `OnwardCalls`.

`WeatherFragment` and `VoitureFragment` still use plain `HttpURLConnection` on a single-thread `ExecutorService` — they have not been refactored onto an HTTP helper yet. There is no Retrofit/OkHttp/coroutine layer.

## Conventions worth following

- Java only — no Kotlin in the project.
- Keep MQTT-driven state in static caches on `MqttService`; keep API-driven state inside its fragment.
- Permissions in `AndroidManifest.xml` are minimal (INTERNET, FOREGROUND_SERVICE[_DATA_SYNC], POST_NOTIFICATIONS, WAKE_LOCK, RECEIVE_BOOT_COMPLETED). The `dataSync` foreground service type is what justifies the persistent MQTT connection — don't switch to a different type casually.
- User-visible strings are in French; tab titles, status labels, etc. should match. Logging uses French too (`Log.i(TAG, "Connecté"…)`).
- Communication-with-claude rule from `.claude/settings.local.json`: WebFetch is preallowed for `prim.iledefrance-mobilites.fr`, `doc.navitia.io`, `idfm-api.readthedocs.io`, `data.iledefrance-mobilites.fr` — use these for IDFM API questions instead of guessing.
