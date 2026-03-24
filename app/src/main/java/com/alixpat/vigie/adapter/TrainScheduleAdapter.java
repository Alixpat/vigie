package com.alixpat.vigie.adapter;

import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;

import java.util.ArrayList;
import java.util.List;

public class TrainScheduleAdapter extends RecyclerView.Adapter<TrainScheduleAdapter.ViewHolder> {

    private final List<TrainSchedule> schedules = new ArrayList<>();

    public void updateSchedules(List<TrainSchedule> newData) {
        schedules.clear();
        schedules.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_train_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrainSchedule schedule = schedules.get(position);

        holder.destination.setText(schedule.getDestination());
        holder.status.setText(schedule.getStatusLabel());
        holder.status.setTextColor(schedule.getStatusColor());

        if (schedule.getPlatformName() != null && !schedule.getPlatformName().isEmpty()) {
            holder.platform.setText("Voie " + schedule.getPlatformName());
            holder.platform.setVisibility(View.VISIBLE);
        } else {
            holder.platform.setVisibility(View.GONE);
        }

        // Arrival time at destination
        String arrival = schedule.getArrivalTime();
        if (arrival != null && !arrival.isEmpty()) {
            holder.arrivalArrow.setVisibility(View.VISIBLE);
            holder.arrivalTime.setVisibility(View.VISIBLE);
            holder.arrivalTime.setText(arrival);
        } else {
            holder.arrivalArrow.setVisibility(View.GONE);
            holder.arrivalTime.setVisibility(View.GONE);
        }

        if (schedule.isCancelled()) {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(0xFF999999);
            holder.expectedTime.setVisibility(View.GONE);
            if (arrival != null && !arrival.isEmpty()) {
                holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.arrivalTime.setTextColor(0xFF999999);
            }
        } else if (schedule.isDelayed()) {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(0xFF999999);
            holder.expectedTime.setText(schedule.getExpectedDepartureTime());
            holder.expectedTime.setTextColor(0xFFFF9800);
            holder.expectedTime.setVisibility(View.VISIBLE);
            // Keep arrival time normal style (aimed arrival)
            holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.arrivalTime.setTextColor(0xFF1565C0);
        } else {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(0xFF000000);
            holder.expectedTime.setVisibility(View.GONE);
            holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.arrivalTime.setTextColor(0xFF1565C0);
        }
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView aimedTime;
        final TextView expectedTime;
        final TextView arrivalArrow;
        final TextView arrivalTime;
        final TextView destination;
        final TextView platform;
        final TextView status;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            aimedTime = itemView.findViewById(R.id.scheduleAimedTime);
            expectedTime = itemView.findViewById(R.id.scheduleExpectedTime);
            arrivalArrow = itemView.findViewById(R.id.scheduleArrivalArrow);
            arrivalTime = itemView.findViewById(R.id.scheduleArrivalTime);
            destination = itemView.findViewById(R.id.scheduleDestination);
            platform = itemView.findViewById(R.id.schedulePlatform);
            status = itemView.findViewById(R.id.scheduleStatus);
        }
    }
}
