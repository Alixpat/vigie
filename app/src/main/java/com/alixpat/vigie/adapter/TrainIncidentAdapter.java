package com.alixpat.vigie.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainIncident;

import java.util.ArrayList;
import java.util.List;

public class TrainIncidentAdapter extends RecyclerView.Adapter<TrainIncidentAdapter.ViewHolder> {

    private final List<TrainIncident> incidents = new ArrayList<>();

    public void updateIncidents(List<TrainIncident> newData) {
        incidents.clear();
        incidents.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_train_incident, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrainIncident incident = incidents.get(position);

        holder.emoji.setText(incident.getSeverityEmoji());
        holder.severity.setText(incident.getSeverityLabel());
        holder.severity.setTextColor(incident.getSeverityColor());
        holder.title.setText(incident.getTitle());
        holder.message.setText(incident.getMessage());

        if (incident.getCause() != null && !incident.getCause().isEmpty()) {
            holder.cause.setText(incident.getCause());
            holder.cause.setVisibility(View.VISIBLE);
        } else {
            holder.cause.setVisibility(View.GONE);
        }

        String period = "";
        if (incident.getStartTime() != null && !incident.getStartTime().isEmpty()) {
            period = "Depuis " + incident.getStartTime();
        }
        if (incident.getEndTime() != null && !incident.getEndTime().isEmpty()) {
            period += " — Fin prévue " + incident.getEndTime();
        }
        if (!period.isEmpty()) {
            holder.period.setText(period);
            holder.period.setVisibility(View.VISIBLE);
        } else {
            holder.period.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return incidents.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView emoji;
        final TextView severity;
        final TextView cause;
        final TextView title;
        final TextView message;
        final TextView period;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            emoji = itemView.findViewById(R.id.incidentEmoji);
            severity = itemView.findViewById(R.id.incidentSeverity);
            cause = itemView.findViewById(R.id.incidentCause);
            title = itemView.findViewById(R.id.incidentTitle);
            message = itemView.findViewById(R.id.incidentMessage);
            period = itemView.findViewById(R.id.incidentPeriod);
        }
    }
}
