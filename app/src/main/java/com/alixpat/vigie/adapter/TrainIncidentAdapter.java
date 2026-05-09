package com.alixpat.vigie.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainIncident;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrainIncidentAdapter extends RecyclerView.Adapter<TrainIncidentAdapter.ViewHolder> {

    private static final int COLLAPSED_MAX_LINES = 2;

    private final List<TrainIncident> incidents = new ArrayList<>();
    // Positions actuellement expandées. Reset à chaque updateIncidents (sinon
    // les indices référenceraient les anciens incidents après refresh).
    private final Set<Integer> expanded = new HashSet<>();

    public void updateIncidents(List<TrainIncident> newData) {
        incidents.clear();
        incidents.addAll(newData);
        expanded.clear();
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
        boolean isExpanded = expanded.contains(position);

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

        // Chevron : ▼ collapsé / ▲ expandé
        holder.chevron.setText(isExpanded ? "▲" : "▼");

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

        // Message : décodé HTML, tronqué à 2 lignes si collapsé
        holder.message.setText(renderHtml(incident.getMessage()));
        if (isExpanded) {
            holder.message.setMaxLines(Integer.MAX_VALUE);
            holder.message.setEllipsize(null);
        } else {
            holder.message.setMaxLines(COLLAPSED_MAX_LINES);
            holder.message.setEllipsize(TextUtils.TruncateAt.END);
        }

        // Période visible seulement si expandé
        StringBuilder period = new StringBuilder();
        if (incident.getStartTime() != null && !incident.getStartTime().isEmpty()) {
            period.append("Depuis ").append(incident.getStartTime());
        }
        if (incident.getEndTime() != null && !incident.getEndTime().isEmpty()) {
            if (period.length() > 0) period.append(" — ");
            period.append("Fin prévue ").append(incident.getEndTime());
        }
        if (isExpanded && period.length() > 0) {
            holder.period.setText(period.toString());
            holder.period.setVisibility(View.VISIBLE);
        } else {
            holder.period.setVisibility(View.GONE);
        }

        // Click sur la card → toggle expand
        holder.container.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;
            if (expanded.contains(adapterPos)) {
                expanded.remove(adapterPos);
            } else {
                expanded.add(adapterPos);
            }
            notifyItemChanged(adapterPos);
        });
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
        final View container;
        final TextView severity;
        final TextView cause;
        final TextView chevron;
        final TextView title;
        final TextView message;
        final TextView period;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            // L'inner LinearLayout (clickable) — le 1er enfant de la MaterialCardView
            container = ((ViewGroup) itemView).getChildAt(0);
            severity = itemView.findViewById(R.id.incidentSeverity);
            cause = itemView.findViewById(R.id.incidentCause);
            chevron = itemView.findViewById(R.id.incidentChevron);
            title = itemView.findViewById(R.id.incidentTitle);
            message = itemView.findViewById(R.id.incidentMessage);
            period = itemView.findViewById(R.id.incidentPeriod);
        }
    }
}
