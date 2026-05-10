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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Affiche les incidents groupés par sévérité (sections "INTERROMPU",
 * "RETARDS", "SERVICE RÉDUIT", "INFORMATION", "TRAVAUX") et collapsés par
 * défaut. Tap sur une carte = expand pour voir le message intégral + période.
 */
public class TrainIncidentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_INCIDENT = 1;

    private final List<Row> rows = new ArrayList<>();
    // Indices d'incident (dans la liste linéaire `rows`) actuellement expandés.
    private final Set<Integer> expanded = new HashSet<>();

    public void updateIncidents(List<TrainIncident> newData) {
        rows.clear();
        expanded.clear();
        rows.addAll(buildRows(newData));
        notifyDataSetChanged();
    }

    /** Trie par sévérité décroissante (puis date la plus récente d'abord
     *  dans chaque groupe) et insère un Header au début de chaque groupe. */
    private static List<Row> buildRows(List<TrainIncident> incidents) {
        if (incidents == null || incidents.isEmpty()) return Collections.emptyList();

        List<TrainIncident> sorted = new ArrayList<>(incidents);
        Collections.sort(sorted, (a, b) -> {
            int c = Integer.compare(severityOrder(a), severityOrder(b));
            if (c != 0) return c;
            // Date la plus récente d'abord. Les formats utilisés
            // ("YYYY-MM-DD HH:MM" et "YYYYMMDDTHHMMSS") sont tous deux
            // triables lexicographiquement.
            String da = a.getStartTime() == null ? "" : a.getStartTime();
            String db = b.getStartTime() == null ? "" : b.getStartTime();
            return db.compareTo(da);
        });

        List<Row> out = new ArrayList<>();
        String currentLabel = null;
        int currentColor = 0;
        int currentCount = 0;
        int currentHeaderIdx = -1;

        for (TrainIncident inc : sorted) {
            String label = inc.getSeverityLabel();
            int color = inc.getSeverityColor();
            if (!label.equals(currentLabel)) {
                // Finalise le compteur du header précédent
                if (currentHeaderIdx >= 0) {
                    out.get(currentHeaderIdx).headerText =
                            currentLabel.toUpperCase() + " (" + currentCount + ")";
                }
                currentLabel = label;
                currentColor = color;
                currentCount = 0;
                currentHeaderIdx = out.size();
                out.add(Row.header("", color)); // texte rempli en fin de groupe
            }
            currentCount++;
            out.add(Row.incident(inc));
        }
        // Dernier header
        if (currentHeaderIdx >= 0) {
            out.get(currentHeaderIdx).headerText =
                    currentLabel.toUpperCase() + " (" + currentCount + ")";
        }
        return out;
    }

    /** Plus petit = plus sévère = en haut. */
    private static int severityOrder(TrainIncident incident) {
        // L'ascenseur est traité comme une info à part, juste après les travaux.
        if ("ascenseur".equalsIgnoreCase(incident.getSeverity())) return 6;
        if (incident.isTravaux()) return 5;
        if (incident.isInformation()) return 4;
        String s = incident.getSeverity();
        if (s == null) return 4;
        switch (s.toLowerCase()) {
            case "blocking": return 1;
            case "delays": return 2;
            case "reduced_service": return 3;
            case "information": return 4;
            default: return 4;
        }
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_INCIDENT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View v = inflater.inflate(R.layout.item_train_incident_header, parent, false);
            return new HeaderViewHolder(v);
        }
        View v = inflater.inflate(R.layout.item_train_incident, parent, false);
        return new IncidentViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderViewHolder) {
            HeaderViewHolder h = (HeaderViewHolder) holder;
            h.label.setText(row.headerText);
            GradientDrawable dot = (GradientDrawable) h.colorDot.getBackground();
            dot.setColor(row.headerColor);
            return;
        }
        bindIncident((IncidentViewHolder) holder, row.incident, position);
    }

    private void bindIncident(IncidentViewHolder holder, TrainIncident incident, int position) {
        boolean isExpanded = expanded.contains(position);

        // Badge sévérité
        holder.severity.setText(incident.getSeverityLabel());
        GradientDrawable badge = (GradientDrawable) holder.severity.getBackground();
        badge.setColor(incident.getSeverityColor());

        // Cause
        if (incident.getCause() != null && !incident.getCause().isEmpty()) {
            holder.cause.setText(incident.getCause());
            holder.cause.setVisibility(View.VISIBLE);
        } else {
            holder.cause.setVisibility(View.GONE);
        }

        // Chevron
        holder.chevron.setText(isExpanded ? "▲" : "▼");

        // Titre (caché si générique)
        String title = incident.getTitle();
        if (title != null && !title.isEmpty()
                && !title.equals("Perturbation Ligne N")
                && !title.equals(incident.getSeverityLabel())) {
            holder.title.setText(title);
            holder.title.setVisibility(View.VISIBLE);
        } else {
            holder.title.setVisibility(View.GONE);
        }

        // Message : visible uniquement si expandé
        if (isExpanded) {
            holder.message.setText(renderHtml(incident.getMessage()));
            holder.message.setVisibility(View.VISIBLE);
        } else {
            holder.message.setVisibility(View.GONE);
        }

        // Période : visible uniquement si expandé
        if (isExpanded) {
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
        } else {
            holder.period.setVisibility(View.GONE);
        }

        // Toggle au clic
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
        return rows.size();
    }

    private static CharSequence renderHtml(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        Spanned spanned = Html.fromHtml(raw, Html.FROM_HTML_MODE_COMPACT);
        int end = spanned.length();
        while (end > 0 && (spanned.charAt(end - 1) == '\n' || spanned.charAt(end - 1) == ' ')) {
            end--;
        }
        return end == spanned.length() ? spanned : spanned.subSequence(0, end);
    }

    /** Cellule de la liste : soit un header de groupe, soit un incident. */
    private static class Row {
        String headerText;
        int headerColor;
        TrainIncident incident;

        boolean isHeader() { return incident == null; }

        static Row header(String text, int color) {
            Row r = new Row();
            r.headerText = text;
            r.headerColor = color;
            return r;
        }

        static Row incident(TrainIncident inc) {
            Row r = new Row();
            r.incident = inc;
            return r;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        final View colorDot;
        final TextView label;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            colorDot = itemView.findViewById(R.id.headerColorDot);
            label = itemView.findViewById(R.id.headerLabel);
        }
    }

    static class IncidentViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final TextView severity;
        final TextView cause;
        final TextView chevron;
        final TextView title;
        final TextView message;
        final TextView period;

        IncidentViewHolder(@NonNull View itemView) {
            super(itemView);
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
