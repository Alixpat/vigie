package com.alixpat.vigie;

import android.app.Service;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.alixpat.vigie.fragment.LanFragment;
import com.alixpat.vigie.model.LanHost;
import com.alixpat.vigie.model.VigieMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MqttService extends Service {

    private static final String TAG = "MqttService";
    private static final int SERVICE_NOTIFICATION_ID = 1;

    public static final String ACTION_STATUS = "com.alixpat.vigie.CONNECTION_STATUS";
    public static final String EXTRA_STATUS = "status";
    public static final String STATUS_CONNECTING = "connecting";
    public static final String STATUS_CONNECTED = "connected";
    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_ERROR = "error";
    public static final String EXTRA_ERROR_MSG = "error_msg";

    private static final String TOPIC = "vigie/#";

    // Statut courant accessible depuis les fragments (pour synchronisation à l'ouverture)
    private static volatile String currentStatus = STATUS_DISCONNECTED;
    private static volatile String currentErrorMsg = null;

    // Historique complet des messages (jamais supprimés)
    private static final List<VigieMessage> messageHistory =
            Collections.synchronizedList(new ArrayList<>());

    // Cache des hôtes LAN (persisté entre passages en arrière-plan)
    private static final Map<String, LanHost> lanHostsCache =
            Collections.synchronizedMap(new LinkedHashMap<>());

    public static String getCurrentStatus() {
        return currentStatus;
    }

    public static String getCurrentErrorMsg() {
        return currentErrorMsg;
    }

    public static List<VigieMessage> getMessageHistory() {
        synchronized (messageHistory) {
            return new ArrayList<>(messageHistory);
        }
    }

    public static Map<String, LanHost> getLanHostsCache() {
        synchronized (lanHostsCache) {
            return new LinkedHashMap<>(lanHostsCache);
        }
    }

    private MqttClient mqttClient;
    private MqttConnectOptions connectOptions;
    private NotificationHelper notificationHelper;
    private BrokerConfig brokerConfig;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile boolean serviceActive = false;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper(this);
        brokerConfig = new BrokerConfig(this);
        connectivityManager = getSystemService(ConnectivityManager.class);

        // WakeLock pour maintenir la connexion en arrière-plan
        PowerManager pm = getSystemService(PowerManager.class);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vigie:mqtt");
        wakeLock.acquire();

        registerNetworkCallback();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(SERVICE_NOTIFICATION_ID, notificationHelper.buildServiceNotification());
        serviceActive = true;

        // Ne pas recréer la connexion si déjà connecté
        if (mqttClient != null && mqttClient.isConnected()) {
            broadcastStatus(STATUS_CONNECTED, null);
            return START_STICKY;
        }

        broadcastStatus(STATUS_CONNECTING, null);
        new Thread(this::connectMqtt).start();

        return START_STICKY;
    }

    private void connectMqtt() {
        // Nettoyer une ancienne connexion si elle existe
        cleanupClient();

        try {
            String brokerUri = brokerConfig.getBrokerUri();
            String clientId = "vigie-android-" + System.currentTimeMillis();
            mqttClient = new MqttClient(brokerUri, clientId, new MemoryPersistence());

            connectOptions = new MqttConnectOptions();
            connectOptions.setCleanSession(true);
            connectOptions.setAutomaticReconnect(true);
            connectOptions.setConnectionTimeout(10);
            connectOptions.setKeepAliveInterval(60);
            connectOptions.setMaxInflight(100);

            if (brokerConfig.hasCredentials()) {
                connectOptions.setUserName(brokerConfig.getUsername());
                connectOptions.setPassword(brokerConfig.getPassword().toCharArray());
            }

            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.i(TAG, (reconnect ? "Reconnecté" : "Connecté") + " à " + serverURI);
                    // Re-souscrire après reconnexion (nécessaire avec cleanSession)
                    try {
                        mqttClient.subscribe(TOPIC, 1);
                        Log.i(TAG, "Abonné à " + TOPIC);
                    } catch (MqttException e) {
                        Log.e(TAG, "Erreur souscription après reconnexion", e);
                    }
                    broadcastStatus(STATUS_CONNECTED, null);
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Connexion MQTT perdue", cause);
                    String msg = cause != null ? cause.getMessage() : "Connexion perdue";
                    // Auto-reconnect de Paho va tenter de reconnecter automatiquement
                    broadcastStatus(STATUS_CONNECTING, msg);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Message reçu sur " + topic + " : " + payload);

                    LanHost lanHost = LanHost.fromJson(payload);
                    if (lanHost != null) {
                        lanHostsCache.put(lanHost.getIp(), lanHost);
                        Intent broadcast = new Intent(LanFragment.ACTION_LAN_STATUS);
                        broadcast.putExtra("payload", payload);
                        broadcast.setPackage(getPackageName());
                        sendBroadcast(broadcast);
                        return;
                    }

                    VigieMessage vigieMsg = VigieMessage.fromJson(payload);
                    if (vigieMsg != null) {
                        messageHistory.add(vigieMsg);
                        notificationHelper.showMessageNotification(vigieMsg);
                        Intent broadcast = new Intent("com.alixpat.vigie.MESSAGE_RECEIVED");
                        broadcast.putExtra("payload", payload);
                        broadcast.setPackage(getPackageName());
                        sendBroadcast(broadcast);
                    }
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Non utilisé (on ne publie pas)
                }
            });

            mqttClient.connect(connectOptions);
            // Le callback connectComplete sera appelé en cas de succès

        } catch (MqttException e) {
            Log.e(TAG, "Erreur connexion MQTT", e);
            broadcastStatus(STATUS_ERROR, e.getMessage());
            // Réessayer après un délai si le service est toujours actif
            if (serviceActive) {
                handler.postDelayed(() -> {
                    if (serviceActive) {
                        new Thread(this::connectMqtt).start();
                    }
                }, 10_000);
            }
        }
    }

    private void registerNetworkCallback() {
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.i(TAG, "Réseau disponible");
                if (serviceActive && (mqttClient == null || !mqttClient.isConnected())) {
                    // Le réseau est revenu, tenter une reconnexion
                    handler.postDelayed(() -> {
                        if (serviceActive && (mqttClient == null || !mqttClient.isConnected())) {
                            Log.i(TAG, "Tentative de reconnexion après retour réseau");
                            new Thread(this::reconnectIfNeeded).start();
                        }
                    }, 2_000);
                }
            }

            @Override
            public void onLost(Network network) {
                Log.w(TAG, "Réseau perdu");
            }

            private void reconnectIfNeeded() {
                if (mqttClient == null) {
                    connectMqtt();
                    return;
                }
                if (!mqttClient.isConnected()) {
                    try {
                        mqttClient.reconnect();
                    } catch (MqttException e) {
                        Log.w(TAG, "Reconnexion échouée, recréation du client", e);
                        connectMqtt();
                    }
                }
            }
        };

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();
        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void cleanupClient() {
        if (mqttClient != null) {
            try {
                if (mqttClient.isConnected()) {
                    mqttClient.disconnect(1000);
                }
            } catch (MqttException e) {
                Log.w(TAG, "Erreur lors du disconnect pendant cleanup", e);
            }
            try {
                mqttClient.close(true);
            } catch (MqttException e) {
                Log.w(TAG, "Erreur lors du close pendant cleanup", e);
            }
            mqttClient = null;
        }
    }

    @Override
    public void onDestroy() {
        serviceActive = false;
        handler.removeCallbacksAndMessages(null);

        if (networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallback = null;
        }

        cleanupClient();
        broadcastStatus(STATUS_DISCONNECTED, null);

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        super.onDestroy();
    }

    private void broadcastStatus(String status, String errorMsg) {
        currentStatus = status;
        currentErrorMsg = errorMsg;

        Intent intent = new Intent(ACTION_STATUS);
        intent.putExtra(EXTRA_STATUS, status);
        if (errorMsg != null) {
            intent.putExtra(EXTRA_ERROR_MSG, errorMsg);
        }
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
