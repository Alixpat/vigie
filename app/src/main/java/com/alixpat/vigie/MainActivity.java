package com.alixpat.vigie;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.alixpat.vigie.model.VigieMessage;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;

    private TextView statusText;
    private TextView lastMessageText;
    private Button toggleButton;
    private boolean serviceRunning = false;

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload != null) {
                VigieMessage msg = VigieMessage.fromJson(payload);
                if (msg != null) {
                    lastMessageText.setText(msg.toString());
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        lastMessageText = findViewById(R.id.lastMessageText);
        toggleButton = findViewById(R.id.toggleButton);

        // Demander la permission de notification sur Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopMqttService();
            } else {
                startMqttService();
            }
        });

        Button settingsButton = findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void startMqttService() {
        Intent serviceIntent = new Intent(this, MqttService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        serviceRunning = true;
        statusText.setText("Statut : Connecté");
        toggleButton.setText("Arrêter la surveillance");
    }

    private void stopMqttService() {
        Intent serviceIntent = new Intent(this, MqttService.class);
        stopService(serviceIntent);
        serviceRunning = false;
        statusText.setText("Statut : Déconnecté");
        toggleButton.setText("Démarrer la surveillance");
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.alixpat.vigie.MESSAGE_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(messageReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(messageReceiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(messageReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Permission gérée automatiquement par le système
    }
}
