package com.alixpat.vigie.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.alixpat.vigie.adapter.SensorAdapter;
import com.alixpat.vigie.model.SensorStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class CapteursFragment extends Fragment {

    public static final String ACTION_SENSOR_STATUS = "com.alixpat.vigie.SENSOR_STATUS";

    private static final long REFRESH_INTERVAL_MS = 15_000;

    private TextView emptyText;
    private RecyclerView recyclerView;
    private SensorAdapter adapter;

    private final Map<String, SensorStatus> sensorsMap = new LinkedHashMap<>();

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && !sensorsMap.isEmpty()) {
                adapter.notifyDataSetChanged();
            }
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;
            SensorStatus s = SensorStatus.fromJson(payload);
            if (s == null) return;
            String key = s.getName() != null ? s.getName() : s.getDeviceId();
            if (key == null) return;
            sensorsMap.put(key, s);
            adapter.updateItems(new ArrayList<>(sensorsMap.values()));
            updateEmptyState();
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_capteurs, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        emptyText = view.findViewById(R.id.capteursEmptyText);
        recyclerView = view.findViewById(R.id.sensorsRecyclerView);

        adapter = new SensorAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);
    }

    private void updateEmptyState() {
        boolean has = !sensorsMap.isEmpty();
        emptyText.setVisibility(has ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(has ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter(ACTION_SENSOR_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(receiver, filter);
        }

        sensorsMap.putAll(MqttService.getSensorsCache());
        if (!sensorsMap.isEmpty()) {
            adapter.updateItems(new ArrayList<>(sensorsMap.values()));
        }
        updateEmptyState();

        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(receiver);
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}
