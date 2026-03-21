# Vigie

Application Android de surveillance connectée à un broker MQTT. Elle reçoit des messages et génère des notifications en temps réel.

## Fonctionnalités (V1)

- Connexion persistante à un broker MQTT (Mosquitto) via un **foreground service**
- Abonnement au topic `vigie/#`
- Réception de messages au format **JSON**
- Affichage de **notifications Android** en fonction des messages reçus
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
│   ├── MainActivity.java            # Écran principal
│   ├── MqttService.java             # Foreground service MQTT
│   ├── NotificationHelper.java      # Gestion des notifications
│   └── model/
│       └── VigieMessage.java        # Modèle de message JSON
├── src/main/res/
│   └── layout/
│       └── activity_main.xml
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

## Configuration (V1)

Pour la V1, l'adresse du broker est codée en dur dans `MqttService.java` :

```java
private static final String BROKER_URI = "tcp://192.168.1.100:1883";
private static final String TOPIC = "vigie/#";
```

> La configuration dynamique (écran de settings) est prévue pour une version ultérieure.

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
3. Modifier l'adresse du broker dans `MqttService.java`
4. Build & Run sur un appareil/émulateur

## Évolutions prévues

- [ ] Configuration du broker dans l'app (écran settings)
- [ ] Topics MQTT configurables
- [ ] Actions personnalisées (sons, vibrations, lancement d'apps)
- [ ] Historique des messages reçus
- [ ] Filtrage et règles de notification
