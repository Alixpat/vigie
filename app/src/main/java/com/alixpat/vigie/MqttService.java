package com.alixpat.vigie;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.alixpat.vigie.model.VigieMessage;

public class MqttService extends Service {

    private static final String TAG = "MqttService";
    private static final int SERVICE_NOTIFICATION_ID = 1;

    // ---- Configuration MQTT (V1 : en dur) ----
    private static final String BROKER_URI = "tcp://192.168.1.100:1883";
    private static final String TOPIC = "vigie/#";
    private static final String CLIENT_ID = "vigie-android";

    private MqttClient mqttClient;
    private NotificationHelper notificationHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationHelper = new NotificationHelper(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Démarrer en foreground immédiatement
        startForeground(SERVICE_NOTIFICATION_ID, notificationHelper.buildServiceNotification());

        // Connexion MQTT dans un thread séparé
        new Thread(this::connectMqtt).start();

        return START_STICKY;
    }

    private void connectMqtt() {
        try {
            mqttClient = new MqttClient(BROKER_URI, CLIENT_ID, new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Connexion MQTT perdue", cause);
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    Log.d(TAG, "Message reçu sur " + topic + " : " + payload);

                    VigieMessage vigieMsg = VigieMessage.fromJson(payload);
                    if (vigieMsg != null) {
                        notificationHelper.showMessageNotification(vigieMsg);
                        // Broadcast pour mettre à jour l'UI
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

            mqttClient.connect(options);
            mqttClient.subscribe(TOPIC, 1);
            Log.i(TAG, "Connecté au broker MQTT et abonné à " + TOPIC);

        } catch (MqttException e) {
            Log.e(TAG, "Erreur connexion MQTT", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                Log.e(TAG, "Erreur déconnexion MQTT", e);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
