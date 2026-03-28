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
import com.alixpat.vigie.model.BackupJob;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class BackupFragment extends Fragment {

    public static final String ACTION_BACKUP_STATUS = "com.alixpat.vigie.BACKUP_STATUS";
    private static final long REFRESH_INTERVAL_MS = 15_000;

    private RecyclerView recyclerView;
    private TextView emptyText;
    private BackupJobAdapter adapter;
    private final Map<String, BackupJob> jobsMap = new LinkedHashMap<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && !jobsMap.isEmpty()) {
                adapter.notifyDataSetChanged();
            }
            refreshHandler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    private final BroadcastReceiver backupReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String payload = intent.getStringExtra("payload");
            if (payload != null) {
                BackupJob job = BackupJob.fromJson(payload);
                if (job != null) {
                    jobsMap.put(job.getJob(), job);
                    refreshList();
                }
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_backup, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.backupRecyclerView);
        emptyText = view.findViewById(R.id.backupEmptyText);

        adapter = new BackupJobAdapter();
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);
    }

    private void refreshList() {
        if (jobsMap.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.updateJobs(new ArrayList<>(jobsMap.values()));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(ACTION_BACKUP_STATUS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(backupReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            requireContext().registerReceiver(backupReceiver, filter);
        }
        jobsMap.putAll(MqttService.getBackupJobsCache());
        refreshList();
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(backupReceiver);
        refreshHandler.removeCallbacks(refreshRunnable);
    }
}
