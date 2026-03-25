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
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import android.content.SharedPreferences;

import com.alixpat.vigie.adapter.ViewPagerAdapter;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_NOTIFICATION_PERMISSION = 1001;
    private static final String[] TAB_TITLES = {"Messages", "LAN", "Météo", "Train", "Voiture"};
    private static final String PREFS_NAME = "vigie_prefs";
    private static final String PREF_LAST_TAB = "last_tab_position";

    private View statusDot;
    private TextView statusText;
    private LinearLayout statusToggle;
    private ViewPager2 viewPager;
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
        statusToggle = findViewById(R.id.statusToggle);

        statusToggle.setOnClickListener(v -> {
            if (serviceRunning) {
                stopMqttService();
            } else {
                startMqttService();
            }
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_PERMISSION);
            }
        }

        TabLayout tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);

        ViewPagerAdapter adapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int lastTab = prefs.getInt(PREF_LAST_TAB, 0);
        if (lastTab >= 0 && lastTab < TAB_TITLES.length) {
            viewPager.setCurrentItem(lastTab, false);
        }
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

        serviceRunning = isServiceRunning();
        if (serviceRunning) {
            String currentStatus = MqttService.getCurrentStatus();
            String currentError = MqttService.getCurrentErrorMsg();
            updateStatusUI(currentStatus, currentError);
        } else {
            updateStatusUI(MqttService.STATUS_DISCONNECTED, null);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(statusReceiver);

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putInt(PREF_LAST_TAB, viewPager.getCurrentItem())
                .apply();
    }

    private void updateStatusUI(String status, String errorMsg) {
        int dotColorRes;
        String label;

        switch (status) {
            case MqttService.STATUS_CONNECTED:
                serviceRunning = true;
                dotColorRes = R.color.status_ok;
                label = "Connecté";
                break;
            case MqttService.STATUS_CONNECTING:
                serviceRunning = true;
                dotColorRes = R.color.status_warning;
                label = "Connexion...";
                if (errorMsg != null) label += " (" + errorMsg + ")";
                break;
            case MqttService.STATUS_ERROR:
                serviceRunning = true;
                dotColorRes = R.color.status_error;
                label = "Erreur";
                if (errorMsg != null) label += " — " + errorMsg;
                break;
            default:
                serviceRunning = false;
                dotColorRes = R.color.status_inactive;
                label = "Déconnecté";
                break;
        }

        GradientDrawable dot = (GradientDrawable) statusDot.getBackground();
        dot.setColor(ContextCompat.getColor(this, dotColorRes));
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
