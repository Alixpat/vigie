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
import com.alixpat.vigie.adapter.BackupJobAdapter;
import com.alixpat.vigie.adapter.InternetAdapter;
import com.alixpat.vigie.adapter.LanHostAdapter;
import com.alixpat.vigie.model.BackupJob;
import com.alixpat.vigie.model.InternetStatus;
import com.alixpat.vigie.model.LanHost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class InfraFragment extends Fragment {

    public static final String ACTION_LAN_STATUS = "com.alixpat.vigie.LAN_STATUS";
    public static final String ACTION_BACKUP_STATUS = "com.alixpat.vigie.BACKUP_STATUS";
    public static final String ACTION_INTERNET_STATUS = "com.alixpat.vigie.INTERNET_STATUS";

    private static final long REFRESH_INTERVAL_MS = 15_000;

    private TextView emptyText;
    private TextView sectionInternet;
    private TextView sectionLan;
    private TextView sectionBackup;
    private RecyclerView internetRecyclerView;
    private RecyclerView lanRecyclerView;
    private RecyclerView backupRecyclerView;

    private InternetAdapter internetAdapter;
    private LanHostAdapter lanAdapter;
    private BackupJobAdapter backupAdapter;

    private final Map<String, InternetStatus> internetMap = new LinkedHashMap<>();
    private final Map<String, LanHost> lanMap = new LinkedHashMap<>();
    private final Map<String, BackupJob> backupMap = new LinkedHashMap<>();

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (internetAdapter != null && !internetMap.isEmpty()) internetAdapter.notifyDataSetChanged();
            if (lanAdapter != null && !lanMap.isEmpty()) lanAdapter.notifyDataSetChanged();
            if (backupAdapter != null && !backupMap.isEmpty()) backupAdapter.notifyDataSetChanged();
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload == null) return;

            String action = intent.getAction();
            if (ACTION_LAN_STATUS.equals(action)) {
                LanHost host = LanHost.fromJson(payload);
                if (host != null) {
                    lanMap.put(host.getIp(), host);
                    refreshSection(sectionLan, lanRecyclerView, !lanMap.isEmpty());
                    lanAdapter.updateHosts(new ArrayList<>(lanMap.values()));
                    updateEmptyState();
                }
            } else if (ACTION_BACKUP_STATUS.equals(action)) {
                BackupJob job = BackupJob.fromJson(payload);
                if (job != null) {
                    backupMap.put(job.getJob(), job);
                    refreshSection(sectionBackup, backupRecyclerView, !backupMap.isEmpty());
                    backupAdapter.updateJobs(new ArrayList<>(backupMap.values()));
                    updateEmptyState();
                }
            } else if (ACTION_INTERNET_STATUS.equals(action)) {
                InternetStatus status = InternetStatus.fromJson(payload);
                if (status != null) {
                    internetMap.put(status.getName(), status);
                    refreshSection(sectionInternet, internetRecyclerView, !internetMap.isEmpty());
                    internetAdapter.updateItems(new ArrayList<>(internetMap.values()));
                    updateEmptyState();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_infra, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emptyText = view.findViewById(R.id.infraEmptyText);
        sectionInternet = view.findViewById(R.id.sectionInternet);
        sectionLan = view.findViewById(R.id.sectionLan);
        sectionBackup = view.findViewById(R.id.sectionBackup);
        internetRecyclerView = view.findViewById(R.id.internetRecyclerView);
        lanRecyclerView = view.findViewById(R.id.lanRecyclerView);
        backupRecyclerView = view.findViewById(R.id.backupRecyclerView);

        internetAdapter = new InternetAdapter();
        internetRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        internetRecyclerView.setAdapter(internetAdapter);

        lanAdapter = new LanHostAdapter();
        lanRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        lanRecyclerView.setAdapter(lanAdapter);

        backupAdapter = new BackupJobAdapter();
        backupRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        backupRecyclerView.setAdapter(backupAdapter);
    }

    private void refreshSection(TextView header, RecyclerView recycler, boolean hasData) {
        int visibility = hasData ? View.VISIBLE : View.GONE;
        header.setVisibility(visibility);
        recycler.setVisibility(visibility);
    }

    private void updateEmptyState() {
        boolean hasAnyData = !internetMap.isEmpty() || !lanMap.isEmpty() || !backupMap.isEmpty();
        emptyText.setVisibility(hasAnyData ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_LAN_STATUS);
        filter.addAction(ACTION_BACKUP_STATUS);
        filter.addAction(ACTION_INTERNET_STATUS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(receiver, filter);
        }

        // Restaurer depuis les caches du service
        lanMap.putAll(MqttService.getLanHostsCache());
        backupMap.putAll(MqttService.getBackupJobsCache());
        internetMap.putAll(MqttService.getInternetCache());

        refreshSection(sectionInternet, internetRecyclerView, !internetMap.isEmpty());
        refreshSection(sectionLan, lanRecyclerView, !lanMap.isEmpty());
        refreshSection(sectionBackup, backupRecyclerView, !backupMap.isEmpty());

        if (!internetMap.isEmpty()) internetAdapter.updateItems(new ArrayList<>(internetMap.values()));
        if (!lanMap.isEmpty()) lanAdapter.updateHosts(new ArrayList<>(lanMap.values()));
        if (!backupMap.isEmpty()) backupAdapter.updateJobs(new ArrayList<>(backupMap.values()));

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
