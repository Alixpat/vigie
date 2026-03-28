package com.alixpat.vigie.adapter;

import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;

import java.util.ArrayList;
import java.util.List;

public class TrainScheduleAdapter extends RecyclerView.Adapter<TrainScheduleAdapter.ViewHolder> {

    public interface OnTrainClickListener {
        void onTrainClick(TrainSchedule schedule);
    }

    private final List<TrainSchedule> schedules = new ArrayList<>();
    private OnTrainClickListener clickListener;

    public void setOnTrainClickListener(OnTrainClickListener listener) {
        this.clickListener = listener;
    }

    public void updateSchedules(List<TrainSchedule> newData) {
        schedules.clear();
        schedules.addAll(newData);
        notifyDataSetChanged();
    }

    public List<TrainSchedule> getSchedules() {
        return new ArrayList<>(schedules);
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

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTrainClick(schedule);
            }
        });

        int textPrimary = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary);
        int textHint = ContextCompat.getColor(holder.itemView.getContext(), R.color.text_hint);
        int warning = ContextCompat.getColor(holder.itemView.getContext(), R.color.status_warning);

        holder.destination.setText(schedule.getDestination());
        holder.status.setText(schedule.getStatusLabel());
        holder.status.setTextColor(schedule.getStatusColor());

        if (schedule.getPlatformName() != null && !schedule.getPlatformName().isEmpty()) {
            holder.platform.setText("Voie " + schedule.getPlatformName());
            holder.platform.setVisibility(View.VISIBLE);
        } else {
            holder.platform.setVisibility(View.GONE);
        }

        String arrival = schedule.getArrivalTime();
        if (arrival != null && !arrival.isEmpty()) {
            holder.arrivalArrow.setVisibility(View.VISIBLE);
            holder.arrivalTime.setVisibility(View.VISIBLE);
            holder.arrivalTime.setText(arrival);
        } else {
            holder.arrivalArrow.setVisibility(View.GONE);
            holder.arrivalTime.setVisibility(View.GONE);
        }

        String travelTime = schedule.getTravelTime();
        if (travelTime != null) {
            String full = "Trajet : " + travelTime;
            // Rendre les secondes (ex: " 05s") plus petites
            int sIdx = full.lastIndexOf("s");
            if (sIdx > 0) {
                // Trouver le début de la partie secondes (espace avant les chiffres des secondes)
                int secStart = full.lastIndexOf(" ", sIdx - 1);
                if (secStart >= 0) {
                    SpannableString span = new SpannableString(full);
                    span.setSpan(new RelativeSizeSpan(0.8f), secStart, sIdx + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    holder.travelTime.setText(span);
                } else {
                    holder.travelTime.setText(full);
                }
            } else {
                holder.travelTime.setText(full);
            }
            holder.travelTime.setVisibility(View.VISIBLE);
        } else {
            holder.travelTime.setVisibility(View.GONE);
        }

        if (schedule.isCancelled()) {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(textHint);
            holder.expectedTime.setVisibility(View.GONE);
            if (arrival != null && !arrival.isEmpty()) {
                holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                holder.arrivalTime.setTextColor(textHint);
            }
        } else if (schedule.isDelayed()) {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(textHint);
            holder.expectedTime.setText(schedule.getExpectedDepartureTime());
            holder.expectedTime.setTextColor(warning);
            holder.expectedTime.setVisibility(View.VISIBLE);
            holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.arrivalTime.setTextColor(textPrimary);
        } else {
            holder.aimedTime.setText(schedule.getAimedDepartureTime());
            holder.aimedTime.setPaintFlags(holder.aimedTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.aimedTime.setTextColor(textPrimary);
            holder.expectedTime.setVisibility(View.GONE);
            holder.arrivalTime.setPaintFlags(holder.arrivalTime.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            holder.arrivalTime.setTextColor(textPrimary);
            holder.status.setText(schedule.getStatusLabel());
            holder.status.setTextColor(schedule.getStatusColor());
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
        final TextView travelTime;
        final TextView destination;
        final TextView platform;
        final TextView status;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            aimedTime = itemView.findViewById(R.id.scheduleAimedTime);
            expectedTime = itemView.findViewById(R.id.scheduleExpectedTime);
            arrivalArrow = itemView.findViewById(R.id.scheduleArrivalArrow);
            arrivalTime = itemView.findViewById(R.id.scheduleArrivalTime);
            travelTime = itemView.findViewById(R.id.scheduleTravelTime);
            destination = itemView.findViewById(R.id.scheduleDestination);
            platform = itemView.findViewById(R.id.schedulePlatform);
            status = itemView.findViewById(R.id.scheduleStatus);
        }
    }
}
