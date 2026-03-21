package com.alixpat.vigie;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

import com.alixpat.vigie.model.VigieMessage;

public class NotificationHelper {

    public static final String CHANNEL_SERVICE = "vigie_service";
    public static final String CHANNEL_ALERTS = "vigie_alerts";

    private static int notificationId = 100;

    private final Context context;
    private final NotificationManager manager;

    public NotificationHelper(Context context) {
        this.context = context;
        this.manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannels();
    }

    private void createChannels() {
        // Canal pour le foreground service (silencieux)
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_SERVICE,
                "Service Vigie",
                NotificationManager.IMPORTANCE_LOW
        );
        serviceChannel.setDescription("Notification permanente du service MQTT");
        manager.createNotificationChannel(serviceChannel);

        // Canal pour les alertes
        NotificationChannel alertChannel = new NotificationChannel(
                CHANNEL_ALERTS,
                "Alertes Vigie",
                NotificationManager.IMPORTANCE_HIGH
        );
        alertChannel.setDescription("Alertes reçues via MQTT");
        alertChannel.enableVibration(true);
        manager.createNotificationChannel(alertChannel);
    }

    /**
     * Notification persistante pour le foreground service.
     */
    public Notification buildServiceNotification() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, CHANNEL_SERVICE)
                .setContentTitle("Vigie")
                .setContentText("Surveillance MQTT active")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    /**
     * Affiche une notification pour un message MQTT reçu.
     */
    public void showMessageNotification(VigieMessage msg) {
        if (msg == null) return;

        String title = msg.getTitle() != null ? msg.getTitle() : "Vigie";
        String text = msg.getMessage() != null ? msg.getMessage() : "";

        int importance = msg.isHighPriority()
                ? NotificationCompat.PRIORITY_HIGH
                : NotificationCompat.PRIORITY_DEFAULT;

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0,
                new Intent(context, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(context, CHANNEL_ALERTS)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setPriority(importance)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        manager.notify(notificationId++, notification);
    }
}
