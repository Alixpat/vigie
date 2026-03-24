package com.alixpat.vigie.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.MqttService;
import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.MessageAdapter;
import com.alixpat.vigie.model.VigieMessage;

import java.util.List;

public class MessagesFragment extends Fragment {

    private RecyclerView messagesRecyclerView;
    private MessageAdapter messageAdapter;

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_messages, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        messagesRecyclerView = view.findViewById(R.id.messagesRecyclerView);

        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter messageFilter = new IntentFilter("com.alixpat.vigie.MESSAGE_RECEIVED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(messageReceiver, messageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(messageReceiver, messageFilter);
        }

        // Synchroniser l'historique complet des messages
        List<VigieMessage> history = MqttService.getMessageHistory();
        messageAdapter.setMessages(history);
        if (!history.isEmpty()) {
            messagesRecyclerView.scrollToPosition(history.size() - 1);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(messageReceiver);
    }
}
