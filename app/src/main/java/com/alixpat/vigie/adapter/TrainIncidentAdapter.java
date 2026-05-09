package com.alixpat.vigie.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.Spanned;
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

        // Badge sévérité : fond coloré + texte blanc
        holder.severity.setText(incident.getSeverityLabel());
        GradientDrawable badge = (GradientDrawable) holder.severity.getBackground();
        badge.setColor(incident.getSeverityColor());

        // Cause à droite (1 ligne max, ellipsis si trop long)
        if (incident.getCause() != null && !incident.getCause().isEmpty()) {
            holder.cause.setText(incident.getCause());
            holder.cause.setVisibility(View.VISIBLE);
        } else {
            holder.cause.setVisibility(View.GONE);
        }

        // Titre : caché si générique ou identique au badge
        String title = incident.getTitle();
        if (title != null && !title.isEmpty()
                && !title.equals("Perturbation Ligne N")
                && !title.equals(incident.getSeverityLabel())) {
            holder.title.setText(title);
            holder.title.setVisibility(View.VISIBLE);
        } else {
            holder.title.setVisibility(View.GONE);
        }

        // Message intégral, pas de troncage. Le texte arrive de Navitia avec
        // du HTML (<br>, <strong>, &eacute;…) → on le rend en Spanned pour
        // décoder les entités, conserver les retours à la ligne et le gras.
        holder.message.setText(renderHtml(incident.getMessage()));

        // Période discrète en bas
        StringBuilder period = new StringBuilder();
        if (incident.getStartTime() != null && !incident.getStartTime().isEmpty()) {
            period.append("Depuis ").append(incident.getStartTime());
        }
        if (incident.getEndTime() != null && !incident.getEndTime().isEmpty()) {
            if (period.length() > 0) period.append(" — ");
            period.append("Fin prévue ").append(incident.getEndTime());
        }
        if (period.length() > 0) {
            holder.period.setText(period.toString());
            holder.period.setVisibility(View.VISIBLE);
        } else {
            holder.period.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return incidents.size();
    }

    private static CharSequence renderHtml(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Spanned spanned = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT);
        // Trim les \n traînants ajoutés par fromHtml en fin de bloc
        int end = spanned.length();
        while (end > 0 && (spanned.charAt(end - 1) == '\n' || spanned.charAt(end - 1) == ' ')) {
            end--;
        }
        return end == spanned.length() ? spanned : spanned.subSequence(0, end);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView severity;
        final TextView cause;
        final TextView title;
        final TextView message;
        final TextView period;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            severity = itemView.findViewById(R.id.incidentSeverity);
            cause = itemView.findViewById(R.id.incidentCause);
            title = itemView.findViewById(R.id.incidentTitle);
            message = itemView.findViewById(R.id.incidentMessage);
            period = itemView.findViewById(R.id.incidentPeriod);
        }
    }
}
