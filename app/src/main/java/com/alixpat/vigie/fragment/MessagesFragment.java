package com.alixpat.vigie.fragment;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.MqttService;
import com.alixpat.vigie.R;
import com.alixpat.vigie.SettingsActivity;
import com.alixpat.vigie.adapter.MessageAdapter;
import com.alixpat.vigie.model.VigieMessage;

import java.util.List;

public class MessagesFragment extends Fragment {

    private TextView statusText;
    private Button toggleButton;
    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;
    private boolean serviceRunning = false;

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload != null) {
                VigieMessage msg = VigieMessage.fromJson(payload);
                if (msg != null) {
                    messageAdapter.addMessage(msg);
                    messagesRecyclerView.scrollToPosition(messageAdapter.getItemCount() - 1);
                }
            }
        }
    };

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String status = intent.getStringExtra(MqttService.EXTRA_STATUS);
            if (status == null) return;

            switch (status) {
                case MqttService.STATUS_CONNECTING:
                    serviceRunning = true;
                    String connMsg = intent.getStringExtra(MqttService.EXTRA_ERROR_MSG);
                    statusText.setText("Statut : Connexion en cours..." +
                            (connMsg != null ? " (" + connMsg + ")" : ""));
                    toggleButton.setText("Arrêter la surveillance");
                    toggleButton.setEnabled(true);
                    break;
                case MqttService.STATUS_CONNECTED:
                    serviceRunning = true;
                    statusText.setText("Statut : Connecté");
                    toggleButton.setText("Arrêter la surveillance");
                    toggleButton.setEnabled(true);
                    break;
                case MqttService.STATUS_DISCONNECTED:
                    serviceRunning = false;
                    String dcMsg = intent.getStringExtra(MqttService.EXTRA_ERROR_MSG);
                    statusText.setText("Statut : Déconnecté" +
                            (dcMsg != null ? " (" + dcMsg + ")" : ""));
                    toggleButton.setText("Démarrer la surveillance");
                    toggleButton.setEnabled(true);
                    break;
                case MqttService.STATUS_ERROR:
                    serviceRunning = true;
                    String errMsg = intent.getStringExtra(MqttService.EXTRA_ERROR_MSG);
                    statusText.setText("Statut : Erreur" +
                            (errMsg != null ? " — " + errMsg : "") +
                            " (nouvelle tentative...)");
                    toggleButton.setText("Arrêter la surveillance");
                    toggleButton.setEnabled(true);
                    break;
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        statusText = view.findViewById(R.id.statusText);
        toggleButton = view.findViewById(R.id.toggleButton);
        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);

        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesRecyclerView.setAdapter(messageAdapter);

        toggleButton.setOnClickListener(v -> {
            if (serviceRunning) {
                stopMqttService();
            } else {
                startMqttService();
            }
        });

        Button settingsButton = view.findViewById(R.id.settingsButton);
        settingsButton.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    private void startMqttService() {
        Intent serviceIntent = new Intent(requireContext(), MqttService.class);
        ContextCompat.startForegroundService(requireContext(), serviceIntent);
    }

    private void stopMqttService() {
        Intent serviceIntent = new Intent(requireContext(), MqttService.class);
        requireContext().stopService(serviceIntent);
        serviceRunning = false;
        statusText.setText("Statut : Déconnecté");
        toggleButton.setText("Démarrer la surveillance");
        toggleButton.setEnabled(true);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter messageFilter = new IntentFilter("com.alixpat.vigie.MESSAGE_RECEIVED");
        IntentFilter statusFilter = new IntentFilter(MqttService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(messageReceiver, messageFilter, Context.RECEIVER_NOT_EXPORTED);
            requireContext().registerReceiver(statusReceiver, statusFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(messageReceiver, messageFilter);
            requireContext().registerReceiver(statusReceiver, statusFilter);
        }

        // Synchroniser l'historique complet des messages (inclut ceux reçus en arrière-plan)
        List<VigieMessage> history = MqttService.getMessageHistory();
        messageAdapter.setMessages(history);
        if (!history.isEmpty()) {
            messagesRecyclerView.scrollToPosition(history.size() - 1);
        }

        // Synchroniser l'état de l'UI avec le service réel
        serviceRunning = isServiceRunning();
        if (serviceRunning) {
            toggleButton.setText("Arrêter la surveillance");
            String currentStatus = MqttService.getCurrentStatus();
            String currentError = MqttService.getCurrentErrorMsg();
            switch (currentStatus) {
                case MqttService.STATUS_CONNECTED:
                    statusText.setText("Statut : Connecté");
                    break;
                case MqttService.STATUS_CONNECTING:
                    statusText.setText("Statut : Connexion en cours..." +
                            (currentError != null ? " (" + currentError + ")" : ""));
                    break;
                case MqttService.STATUS_ERROR:
                    statusText.setText("Statut : Erreur" +
                            (currentError != null ? " — " + currentError : "") +
                            " (nouvelle tentative...)");
                    break;
                default:
                    statusText.setText("Statut : Connexion en cours...");
                    break;
            }
        } else {
            statusText.setText("Statut : Déconnecté");
            toggleButton.setText("Démarrer la surveillance");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(messageReceiver);
        requireContext().unregisterReceiver(statusReceiver);
    }

    private boolean isServiceRunning() {
        ActivityManager am = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
            if (MqttService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
}
