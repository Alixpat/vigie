package com.alixpat.vigie;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.alixpat.vigie.adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String[] TAB_TITLES = {"Messages", "LAN"};

    private View statusDot;
    private TextView statusText;
    private Button toggleButton;
    private boolean serviceRunning = false;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(MqttService.EXTRA_STATUS);
            if (status == null) return;
            updateStatusUI(status, intent.getStringExtra(MqttService.EXTRA_ERROR_MSG));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("VIGIE");
        }

        statusDot = findViewById(R.id.statusDot);
        statusText = findViewById(R.id.statusText);
        toggleButton = findViewById(R.id.toggleButton);

        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopMqttService();
            } else {
                startMqttService();
            }
        });

        // Demander la permission de notification sur Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        ViewPager2 viewPager = findViewById(R.id.viewPager);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter statusFilter = new IntentFilter(MqttService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(statusReceiver, statusFilter);
        }

        // Synchroniser l'etat avec le service
        serviceRunning = isServiceRunning();
        if (serviceRunning) {
            toggleButton.setText("Arrêter la surveillance");
            String currentStatus = MqttService.getCurrentStatus();
            String currentError = MqttService.getCurrentErrorMsg();
            updateStatusUI(currentStatus, currentError);
        } else {
            updateStatusUI(MqttService.STATUS_DISCONNECTED, null);
            toggleButton.setText("Démarrer la surveillance");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(statusReceiver);
    }

    private void updateStatusUI(String status, String errorMsg) {
        int dotColor;
        String label;

        switch (status) {
            case MqttService.STATUS_CONNECTED:
                serviceRunning = true;
                dotColor = 0xFF4CAF50; // vert
                label = "Connecté";
                toggleButton.setText("Arrêter la surveillance");
                toggleButton.setEnabled(true);
                break;
            case MqttService.STATUS_CONNECTING:
                serviceRunning = true;
                dotColor = 0xFFFF9800; // orange
                label = "Connexion...";
                if (errorMsg != null) label += " (" + errorMsg + ")";
                toggleButton.setText("Arrêter la surveillance");
                toggleButton.setEnabled(true);
                break;
            case MqttService.STATUS_ERROR:
                serviceRunning = true;
                dotColor = 0xFFF44336; // rouge
                label = "Erreur";
                if (errorMsg != null) label += " — " + errorMsg;
                toggleButton.setText("Arrêter la surveillance");
                toggleButton.setEnabled(true);
                break;
            default: // DISCONNECTED
                serviceRunning = false;
                dotColor = 0xFF999999; // gris
                label = "Déconnecté";
                toggleButton.setText("Démarrer la surveillance");
                toggleButton.setEnabled(true);
                break;
        }

        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
        dot.setColor(dotColor);
        statusText.setText(label);
    }

    private void startMqttService() {
        Intent serviceIntent = new Intent(this, MqttService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }

    private void stopMqttService() {
        Intent serviceIntent = new Intent(this, MqttService.class);
        stopService(serviceIntent);
        serviceRunning = false;
        updateStatusUI(MqttService.STATUS_DISCONNECTED, null);
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (MqttService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
