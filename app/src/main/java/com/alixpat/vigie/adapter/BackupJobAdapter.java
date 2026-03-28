package com.alixpat.vigie.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.BackupJob;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BackupJobAdapter extends RecyclerView.Adapter<BackupJobAdapter.ViewHolder> {

    private final List<BackupJob> jobs = new ArrayList<>();

    public void updateJobs(List<BackupJob> newJobs) {
        jobs.clear();
        jobs.addAll(newJobs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_backup_job, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BackupJob job = jobs.get(position);
        holder.jobNameText.setText(job.getJob());
        holder.detailText.setText(job.getDetail());

        int colorRes;
        if (job.isSuccess()) {
            colorRes = R.color.status_ok;
        } else if (job.isFailed()) {
            colorRes = R.color.status_error;
        } else {
            colorRes = R.color.status_warning;
        }
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        GradientDrawable indicator = (GradientDrawable) holder.statusIndicator.getBackground();
        indicator.setColor(color);

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                job.getLastUpdate(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String exactTime = sdf.format(new Date(job.getLastUpdate()));
        holder.lastUpdateText.setText(timeAgo + " (" + exactTime + ")");
    }

    @Override
    public int getItemCount() {
        return jobs.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View statusIndicator;
        final TextView jobNameText;
        final TextView detailText;
        final TextView lastUpdateText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            jobNameText = itemView.findViewById(R.id.jobNameText);
            detailText = itemView.findViewById(R.id.detailText);
            lastUpdateText = itemView.findViewById(R.id.lastUpdateText);
        }
    }
}
