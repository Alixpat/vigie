# Vigie

Application Android de surveillance personnelle. Elle combine une connexion MQTT persistante (notifications domotiques, statut LAN, sauvegardes, qualité internet) avec plusieurs onglets de supervision externes (météo, transports, trafic routier).

## Fonctionnalités

- Connexion persistante à un broker MQTT (Mosquitto) via un **foreground service**
- Abonnement au topic `vigie/#` (reconnexion automatique, conservation en arrière-plan)
- Réception de messages au format **JSON** et **notifications Android**
- **Navigation par onglets** (5) : Messages, Infra, Météo, Train, Voiture
- **Statut de connexion en temps réel** (déconnecté, connexion, connecté, erreur)
- **Onglet Messages** : historique complet des notifications reçues
- **Onglet Infra** : trois sections alimentées par MQTT
  - Internet : statut up/down, latence, dernière coupure
  - LAN : grille des machines du réseau (up/down)
  - Backup : statut des jobs de sauvegarde (success / failed / missing)
- **Onglet Météo** : conditions courantes via [Open-Meteo](https://open-meteo.com/) (sans clé API)
- **Onglet Train** : passages, perturbations et travaux de la ligne SNCF N via l'API [IDFM PRIM](https://prim.iledefrance-mobilites.fr/) (token requis)
- **Onglet Voiture** : temps de trajet domicile-travail via l'API [TomTom Routing](https://developer.tomtom.com/) (clé API requise), avec moyenne glissante sur 30 min
- **Écran de configuration** : broker MQTT (IP, port, identifiants) + tokens IDFM et TomTom

## Stack technique

| Élément | Choix |
|---------|-------|
| Langage | Java |
| IDE | Android Studio |
| minSdk / targetSdk | 26 (Android 8.0) / 34 |
| Java source/target | 1.8 |
| Build | Gradle Wrapper, AGP 8.2.2, JDK 17 |
| Bibliothèque MQTT | Eclipse Paho (client Java synchrone) |
| Broker | Mosquitto (authentifié) |
| Météo | Open-Meteo (sans clé) |
| Transport | IDFM PRIM (token) |
| Trafic routier | TomTom Routing API (clé) |

## Architecture

```
app/
├── src/main/java/com/alixpat/vigie/
│   ├── MainActivity.java            # ViewPager2 + TabLayout (5 onglets), statut MQTT
│   ├── MqttService.java             # Foreground service Paho, caches statiques + broadcasts
│   ├── NotificationHelper.java      # Notifications (canal foreground + canal messages)
│   ├── SettingsActivity.java        # Configuration broker + tokens (IDFM, TomTom)
│   ├── BrokerConfig.java            # SharedPreferences : broker, IDFM, TomTom
│   ├── adapter/
│   │   ├── ViewPagerAdapter.java
│   │   ├── MessageAdapter.java
│   │   ├── LanHostAdapter.java
│   │   ├── BackupJobAdapter.java
│   │   ├── InternetAdapter.java
│   │   ├── WeatherAdapter.java
│   │   ├── TrainScheduleAdapter.java
│   │   └── TrainIncidentAdapter.java
│   ├── fragment/
│   │   ├── MessagesFragment.java    # Historique des messages MQTT
│   │   ├── InfraFragment.java       # Internet + LAN + Backup (sections)
│   │   ├── WeatherFragment.java     # Open-Meteo (rafraîchi toutes les 10 min)
│   │   ├── TrainFragment.java       # IDFM : 5 endpoints (general-message, stop-monitoring,
│   │   │                            #         estimated-timetable, stop_points, line_reports)
│   │   └── VoitureFragment.java     # TomTom routing aller/retour + moyenne 30 min
│   ├── view/
│   │   └── LineMapView.java         # Vue custom : schéma de la ligne N
│   └── model/
│       ├── VigieMessage.java
│       ├── LanHost.java
│       ├── BackupJob.java
│       ├── InternetStatus.java
│       ├── WeatherData.java
│       ├── LineNStation.java
│       ├── TrainSchedule.java
│       ├── TrainStop.java
│       └── TrainIncident.java
├── src/main/res/layout/
│   ├── activity_main.xml
│   ├── activity_settings.xml
│   ├── fragment_messages.xml
│   ├── fragment_infra.xml
│   ├── fragment_weather.xml
│   ├── fragment_train.xml
│   ├── fragment_voiture.xml
│   ├── dialog_line_map.xml
│   ├── dialog_train_detail.xml
│   ├── item_message.xml
│   ├── item_lan_host.xml
│   ├── item_internet.xml
│   ├── item_backup_job.xml
│   ├── item_weather_city.xml
│   ├── item_train_schedule.xml
│   └── item_train_incident.xml
└── src/main/AndroidManifest.xml
```

Le `MqttService` est la source de vérité : il maintient la connexion via `WAKE_LOCK`, expose des caches statiques (`messageHistory`, `lanHostsCache`, `backupJobsCache`, `internetCache`, `currentStatus`) que les fragments consomment au resume, et diffuse les mises à jour live par broadcasts package-scoped (`MqttService.ACTION_STATUS`, `InfraFragment.ACTION_LAN_STATUS` / `ACTION_BACKUP_STATUS` / `ACTION_INTERNET_STATUS`, `com.alixpat.vigie.MESSAGE_RECEIVED`). La reconnexion est doublée : auto-reconnect Paho + `ConnectivityManager.NetworkCallback` qui relance la connexion au retour du réseau.

## Format des messages MQTT

Tous les messages sont publiés sur le topic `vigie/#` (sous-topic libre). Le routage côté app se fait par le champ `type` du JSON, dans cet ordre : `lan_status` → `backup_status` → `internet_status` → autres (notification générique).

### Notification générique (`vigie/*`)

```json
{
  "type": "alert",
  "title": "Alerte drone",
  "message": "Drone détecté dans la zone A",
  "priority": "high"
}
```

| Champ | Type | Description |
|-------|------|-------------|
| `type` | string | Type de notification (`alert`, `info`, `warning`) |
| `title` | string | Titre de la notification |
| `message` | string | Corps du message |
| `priority` | string | Priorité : `high`, `normal`, `low` |

### Statut LAN

```json
{
  "type": "lan_status",
  "hostname": "pc-bureau",
  "ip": "192.168.1.10",
  "status": "up"
}
```

Une entrée par machine. Clé de cache : `ip`.

### Statut Internet

```json
{
  "type": "internet_status",
  "name": "Wan principal",
  "host": "8.8.8.8",
  "status": "up",
  "latency_ms": 12.4,
  "last_downtime_start": "2024-03-12T08:14:03Z",
  "last_downtime_end": "2024-03-12T08:15:42Z",
  "last_downtime_duration_minutes": 1.65
}
```

| Champ | Type | Description |
|-------|------|-------------|
| `type` | string | Toujours `internet_status` |
| `name` | string | Nom affiché (clé de cache) |
| `host` | string | Hôte testé (ex. `8.8.8.8`) |
| `status` | string | `up` ou `down` |
| `latency_ms` | number | Latence en millisecondes |
| `last_downtime_*` | string/number | Dernière coupure (début ISO 8601, fin, durée en minutes) |

### Statut Backup

```json
{
  "type": "backup_status",
  "job": "nas-photos",
  "status": "success",
  "detail": "12.3 GiB transférés en 14m",
  "last_run": "2024-03-12T03:00:00Z"
}
```

| Champ | Type | Description |
|-------|------|-------------|
| `type` | string | Toujours `backup_status` |
| `job` | string | Nom du job (clé de cache) |
| `status` | string | `success`, `failed`, `missing` |
| `detail` | string | Texte libre (résumé / erreur) |
| `last_run` | string | Date ISO 8601 de la dernière exécution |

## Configuration

Tous les paramètres se configurent dans l'app via le bouton **Configuration** (icône engrenage dans la toolbar). Ils sont sauvegardés dans `SharedPreferences` via `BrokerConfig`.

| Paramètre | Onglet concerné | Obligatoire |
|-----------|-----------------|-------------|
| Adresse IP / port du broker | Messages, Infra | Oui pour MQTT |
| Identifiants broker | Messages, Infra | Selon broker |
| Token IDFM (`apikey`) | Train | Oui (sinon onglet vide) |
| Clé API TomTom | Voiture | Oui (sinon onglet vide) |

Le topic MQTT par défaut est `vigie/#`. Les coordonnées des villes (Météo) et des points de départ/arrivée (Voiture) sont actuellement codées en dur dans les fragments.

### Obtenir un token IDFM

Inscription gratuite sur [prim.iledefrance-mobilites.fr](https://prim.iledefrance-mobilites.fr/), puis souscrire aux APIs SIRI (`general-message`, `stop-monitoring`, `estimated-timetable`) et Navitia (`stop_points`, `line_reports`).

### Obtenir une clé TomTom

Inscription gratuite sur [developer.tomtom.com](https://developer.tomtom.com/) (quota gratuit suffisant pour un usage personnel).

## Tester avec Mosquitto

### Installer Mosquitto (si besoin)

```bash
# Debian / Ubuntu
sudo apt install mosquitto mosquitto-clients

# macOS
brew install mosquitto
```

> Dans tous les exemples ci-dessous, remplacer `192.168.1.100`, `mon_user` et `mon_password` par vos valeurs.

### Notifications

```bash
# Alerte haute priorité
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/test" -m '{"type":"alert","title":"Alerte drone","message":"Drone détecté dans la zone A","priority":"high"}'

# Information normale
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/info" -m '{"type":"info","title":"Système","message":"Tous les capteurs sont opérationnels","priority":"normal"}'

# Warning
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/warning" -m '{"type":"warning","title":"Batterie faible","message":"Capteur B2 à 15%","priority":"normal"}'
```

### Statuts LAN

```bash
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"pc-bureau","ip":"192.168.1.10","status":"up"}'
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"nas-synology","ip":"192.168.1.20","status":"down"}'
```

### Statuts Internet

```bash
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/internet" -m '{"type":"internet_status","name":"Wan principal","host":"8.8.8.8","status":"up","latency_ms":12.4}'
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/internet" -m '{"type":"internet_status","name":"Wan principal","host":"8.8.8.8","status":"down","last_downtime_start":"2024-03-12T08:14:03Z"}'
```

### Statuts Backup

```bash
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/backup" -m '{"type":"backup_status","job":"nas-photos","status":"success","detail":"12.3 GiB transférés en 14m","last_run":"2024-03-12T03:00:00Z"}'
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/backup" -m '{"type":"backup_status","job":"db-postgres","status":"failed","detail":"timeout connexion","last_run":"2024-03-12T03:00:00Z"}'
```

## Dépendances principales

```gradle
dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
    implementation 'androidx.viewpager2:viewpager2:1.0.0'

    // MQTT - Eclipse Paho (client Java synchrone, utilisé directement dans MqttService)
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'

    // JSON
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

Les appels HTTP (Météo, Train, Voiture) utilisent `HttpURLConnection` sur un `ExecutorService` mono-thread, sans Retrofit/OkHttp.

## Prérequis

- Android Studio (dernière version stable) ou JDK 17 + Android SDK
- Un broker Mosquitto authentifié accessible sur le réseau (pour Messages/Infra)
- Un appareil ou émulateur Android (API 26+)
- Optionnel : token IDFM (Train), clé TomTom (Voiture)

## Installation

1. Cloner le repo :
   ```bash
   git clone https://github.com/Alixpat/vigie.git
   ```
2. Ouvrir le projet dans Android Studio (ou builder en CLI : `./gradlew assembleDebug`).
3. Build & Run sur un appareil/émulateur.
4. Configurer le broker et les tokens via le bouton **Configuration**.

### APK release signé

Le build release est signé via des variables d'environnement (voir `app/build.gradle`) :

```bash
export KEYSTORE_PATH=/chemin/vers/vigie-release.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=vigie
export KEY_PASSWORD=...
./gradlew assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## CI / Releases

Le workflow `.github/workflows/build-apk.yml` construit l'APK release et publie automatiquement une release GitHub avec l'APK :

- **Push sur `main`** → tag `dev-<sha>` + prerelease (workflow utilisé en pratique)
- **Tag `v*`** → release stable (jamais utilisé à ce jour)
- **`workflow_dispatch`** → lancement manuel depuis l'onglet Actions

Secrets GitHub requis : `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

## Évolutions prévues

- [ ] Topics MQTT configurables
- [ ] Actions personnalisées (sons, vibrations, lancement d'apps)
- [ ] Filtrage et règles de notification
- [ ] Coordonnées Météo / Voiture configurables depuis l'app
- [ ] Choix de la ligne SNCF/RATP dans l'onglet Train
