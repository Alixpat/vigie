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
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.MqttService;
import com.alixpat.vigie.R;
import com.alixpat.vigie.adapter.LanHostAdapter;
import com.alixpat.vigie.model.LanHost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class LanFragment extends Fragment {

    public static final String ACTION_LAN_STATUS = "com.alixpat.vigie.LAN_STATUS";

    private RecyclerView recyclerView;
    private TextView emptyText;
    private LanHostAdapter adapter;
    private final Map<String, LanHost> hostsMap = new LinkedHashMap<>();

    private final BroadcastReceiver lanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload != null) {
                LanHost host = LanHost.fromJson(payload);
                if (host != null) {
                    hostsMap.put(host.getIp(), host);
                    refreshList();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_lan, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.lanRecyclerView);
        emptyText = view.findViewById(R.id.lanEmptyText);

        adapter = new LanHostAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);
    }

    private void refreshList() {
        if (hostsMap.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateHosts(new ArrayList<>(hostsMap.values()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_LAN_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(lanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(lanReceiver, filter);
        }
        // Restaurer les hôtes LAN depuis le cache du service
        hostsMap.putAll(MqttService.getLanHostsCache());
        refreshList();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(lanReceiver);
    }
}
