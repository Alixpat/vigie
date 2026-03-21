# Vigie

Application Android de surveillance connectée à un broker MQTT. Elle reçoit des messages et génère des notifications en temps réel.

## Fonctionnalités

- Connexion persistante à un broker MQTT (Mosquitto) via un **foreground service**
- Abonnement au topic `vigie/#`
- Réception de messages au format **JSON**
- Affichage de **notifications Android** en fonction des messages reçus
- **Navigation par onglets** : Messages et LAN (extensible)
- **Onglet LAN** : supervision en temps réel des machines du réseau (up/down)
- **Écran de configuration** : adresse IP du broker, port, login et mot de passe
- **Statut de connexion en temps réel** (connexion en cours, connecté, erreur, déconnecté)
- Fonctionne en arrière-plan (la connexion reste active même si l'app est fermée)

## Stack technique

| Élément | Choix |
|---------|-------|
| Langage | Java |
| IDE | Android Studio |
| minSdk | API 26 (Android 8.0) |
| Bibliothèque MQTT | Eclipse Paho Android Service |
| Broker | Mosquitto (authentifié) |
| Format des messages | JSON |

## Architecture

```
app/
├── src/main/java/com/alixpat/vigie/
│   ├── MainActivity.java            # Navigation par onglets (TabLayout + ViewPager2)
│   ├── MqttService.java             # Foreground service MQTT
│   ├── NotificationHelper.java      # Gestion des notifications
│   ├── SettingsActivity.java        # Écran de configuration broker
│   ├── BrokerConfig.java            # Stockage des paramètres (SharedPreferences)
│   ├── adapter/
│   │   ├── ViewPagerAdapter.java    # Adaptateur des onglets
│   │   └── LanHostAdapter.java      # Adaptateur grille LAN
│   ├── fragment/
│   │   ├── MessagesFragment.java    # Onglet Messages (start/stop, statut, dernier message)
│   │   └── LanFragment.java         # Onglet LAN (grille des machines)
│   └── model/
│       ├── VigieMessage.java        # Modèle de message notification
│       └── LanHost.java             # Modèle de statut machine LAN
├── src/main/res/
│   └── layout/
│       ├── activity_main.xml        # Layout principal (tabs + viewpager)
│       ├── activity_settings.xml
│       ├── fragment_messages.xml     # Layout onglet Messages
│       ├── fragment_lan.xml          # Layout onglet LAN
│       └── item_lan_host.xml        # Carte machine LAN
└── src/main/AndroidManifest.xml
```

## Format des messages MQTT

### Notifications (topic `vigie/*`)

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

### Statut LAN (topic `vigie/lan`)

```json
{
  "type": "lan_status",
  "hostname": "pc-bureau",
  "ip": "192.168.1.10",
  "status": "up"
}
```

| Champ | Type | Description |
|-------|------|-------------|
| `type` | string | Toujours `lan_status` |
| `hostname` | string | Nom de la machine |
| `ip` | string | Adresse IP |
| `status` | string | `up` ou `down` |

Un message par machine. L'app met à jour la grille à chaque message reçu.

## Configuration

L'adresse du broker, le port et les identifiants se configurent directement dans l'app via le bouton **Configuration** sur l'onglet Messages.

Les paramètres sont sauvegardés localement (SharedPreferences). Le topic par défaut est `vigie/#`.

## Tester avec Mosquitto

### Installer Mosquitto (si besoin)

```bash
# Debian / Ubuntu
sudo apt install mosquitto mosquitto-clients

# macOS
brew install mosquitto
```

> Dans tous les exemples ci-dessous, remplacer `192.168.1.100`, `mon_user` et `mon_password` par vos valeurs.

### Envoyer une notification de test

```bash
# Alerte haute priorité
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/test" -m '{"type":"alert","title":"Alerte drone","message":"Drone détecté dans la zone A","priority":"high"}'

# Information normale
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/info" -m '{"type":"info","title":"Système","message":"Tous les capteurs sont opérationnels","priority":"normal"}'

# Warning
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/warning" -m '{"type":"warning","title":"Batterie faible","message":"Capteur B2 à 15%","priority":"normal"}'
```

### Envoyer des statuts LAN

```bash
# Machine up
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"pc-bureau","ip":"192.168.1.10","status":"up"}'

# Machine down
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"nas-synology","ip":"192.168.1.20","status":"down"}'

# Plusieurs machines d'un coup
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"serveur-web","ip":"192.168.1.30","status":"up"}'
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"imprimante","ip":"192.168.1.40","status":"up"}'
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/lan" -m '{"type":"lan_status","hostname":"camera-garage","ip":"192.168.1.50","status":"down"}'
```

## Dépendances principales

```gradle
dependencies {
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1'
    implementation 'com.google.code.gson:gson:2.10.1'
    implementation 'androidx.viewpager2:viewpager2:1.0.0'
}
```

## Prérequis

- Android Studio (dernière version stable)
- Un broker Mosquitto authentifié accessible sur le réseau
- Un appareil ou émulateur Android (API 26+)

## Installation

1. Cloner le repo :
   ```bash
   git clone https://github.com/Alixpat/vigie.git
   ```
2. Ouvrir le projet dans Android Studio
3. Build & Run sur un appareil/émulateur
4. Configurer l'adresse du broker via le bouton **Configuration** dans l'app

## Évolutions prévues

- [ ] Topics MQTT configurables
- [ ] Actions personnalisées (sons, vibrations, lancement d'apps)
- [ ] Historique des messages reçus
- [ ] Filtrage et règles de notification
