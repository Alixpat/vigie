# Vigie

Application Android de surveillance connectée à un broker MQTT. Elle reçoit des messages et génère des notifications en temps réel.

## Fonctionnalités

- Connexion persistante à un broker MQTT (Mosquitto) via un **foreground service**
- Abonnement au topic `vigie/#`
- Réception de messages au format **JSON**
- Affichage de **notifications Android** en fonction des messages reçus
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
| Broker | Mosquitto |
| Format des messages | JSON |

## Architecture

```
app/
├── src/main/java/com/alixpat/vigie/
│   ├── MainActivity.java            # Écran principal (start/stop, statut)
│   ├── MqttService.java             # Foreground service MQTT
│   ├── NotificationHelper.java      # Gestion des notifications
│   ├── SettingsActivity.java        # Écran de configuration broker
│   ├── BrokerConfig.java            # Stockage des paramètres (SharedPreferences)
│   └── model/
│       └── VigieMessage.java        # Modèle de message JSON
├── src/main/res/
│   └── layout/
│       ├── activity_main.xml
│       └── activity_settings.xml
└── src/main/AndroidManifest.xml
```

## Format des messages MQTT

Les messages reçus sur `vigie/#` sont au format JSON :

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

## Configuration

L'adresse du broker, le port et les identifiants (optionnels) se configurent directement dans l'app via le bouton **Configuration** sur l'écran principal.

Les paramètres sont sauvegardés localement (SharedPreferences). Le topic par défaut est `vigie/#`.

## Tester avec Mosquitto

### Installer Mosquitto (si besoin)

```bash
# Debian / Ubuntu
sudo apt install mosquitto mosquitto-clients

# macOS
brew install mosquitto
```

### Envoyer une notification de test

```bash
# Alerte haute priorité
mosquitto_pub -h 192.168.1.100 -p 1883 -t "vigie/test" -m '{"type":"alert","title":"Alerte drone","message":"Drone détecté dans la zone A","priority":"high"}'

# Information normale
mosquitto_pub -h 192.168.1.100 -p 1883 -t "vigie/info" -m '{"type":"info","title":"Système","message":"Tous les capteurs sont opérationnels","priority":"normal"}'

# Warning
mosquitto_pub -h 192.168.1.100 -p 1883 -t "vigie/warning" -m '{"type":"warning","title":"Batterie faible","message":"Capteur B2 à 15%","priority":"normal"}'
```

### Avec authentification

```bash
mosquitto_pub -h 192.168.1.100 -p 1883 -u "mon_user" -P "mon_password" -t "vigie/test" -m '{"type":"alert","title":"Test auth","message":"Message avec login","priority":"high"}'
```

> Remplacer `192.168.1.100` par l'IP de votre broker.

## Dépendances principales

```gradle
dependencies {
    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'org.eclipse.paho:org.eclipse.paho.android.service:1.1.1'
    implementation 'com.google.code.gson:gson:2.10.1'
}
```

## Prérequis

- Android Studio (dernière version stable)
- Un broker Mosquitto accessible sur le réseau
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
