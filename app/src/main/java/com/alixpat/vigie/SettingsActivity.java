package com.alixpat.vigie;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText ipField;
    private EditText portField;
    private EditText usernameField;
    private EditText passwordField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Configuration Broker");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ipField = findViewById(R.id.ipField);
        portField = findViewById(R.id.portField);
        usernameField = findViewById(R.id.usernameField);
        passwordField = findViewById(R.id.passwordField);
        Button saveButton = findViewById(R.id.saveButton);

        // Charger les valeurs actuelles
        BrokerConfig config = new BrokerConfig(this);
        ipField.setText(config.getBrokerIp());
        portField.setText(String.valueOf(config.getBrokerPort()));
        usernameField.setText(config.getUsername());
        passwordField.setText(config.getPassword());

        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void saveSettings() {
        String ip = ipField.getText().toString().trim();
        String portStr = portField.getText().toString().trim();
        String username = usernameField.getText().toString().trim();
        String password = passwordField.getText().toString();

        if (ip.isEmpty()) {
            ipField.setError("Adresse IP requise");
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                portField.setError("Port invalide (1-65535)");
                return;
            }
        } catch (NumberFormatException e) {
            portField.setError("Port invalide");
            return;
        }

        BrokerConfig config = new BrokerConfig(this);
        config.save(ip, port, username, password);

        Toast.makeText(this, "Configuration sauvegardée", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
