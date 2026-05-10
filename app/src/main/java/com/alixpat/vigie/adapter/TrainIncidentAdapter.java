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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Liste à 2 niveaux d'accordéon :
 *   - Headers de section (par sévérité) : tap pour montrer/cacher les items.
 *     Tous fermés par défaut.
 *   - Cards d'incident : tap pour montrer/cacher message + période.
 *     Toutes fermées par défaut.
 */
public class TrainIncidentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_INCIDENT = 1;

    private final List<TrainIncident> allIncidents = new ArrayList<>();
    private final Set<String> expandedSections = new HashSet<>();
    private final Set<Integer> expandedItems = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();

    public void updateIncidents(List<TrainIncident> newData) {
        allIncidents.clear();
        if (newData != null) allIncidents.addAll(newData);
        // Reset des états d'expand : les indices/labels précédents pourraient
        // référencer des incidents qui ne sont plus là après refresh.
        expandedSections.clear();
        expandedItems.clear();
        rebuildRows();
    }

    /** Reconstruit la liste plate {@code rows} = headers + items des sections expandées. */
    private void rebuildRows() {
        rows.clear();

        if (allIncidents.isEmpty()) {
            notifyDataSetChanged();
            return;
        }

        // Trie par sévérité asc puis date desc
        List<TrainIncident> sorted = new ArrayList<>(allIncidents);
        Collections.sort(sorted, (a, b) -> {
            int c = Integer.compare(severityOrder(a), severityOrder(b));
            if (c != 0) return c;
            String da = a.getStartTime() == null ? "" : a.getStartTime();
            String db = b.getStartTime() == null ? "" : b.getStartTime();
            return db.compareTo(da);
        });

        // Groupe par label en préservant l'ordre d'apparition
        LinkedHashMap<String, List<TrainIncident>> groups = new LinkedHashMap<>();
        Map<String, Integer> groupColors = new LinkedHashMap<>();
        for (TrainIncident inc : sorted) {
            String label = inc.getSeverityLabel();
            groups.computeIfAbsent(label, k -> new ArrayList<>()).add(inc);
            groupColors.putIfAbsent(label, inc.getSeverityColor());
        }

        for (Map.Entry<String, List<TrainIncident>> entry : groups.entrySet()) {
            String label = entry.getKey();
            int count = entry.getValue().size();
            int color = groupColors.get(label);
            boolean sectionOpen = expandedSections.contains(label);
            rows.add(Row.header(label, color, count, sectionOpen));
            if (sectionOpen) {
                for (TrainIncident inc : entry.getValue()) {
                    rows.add(Row.incident(inc));
                }
            }
        }
        notifyDataSetChanged();
    }

    private static int severityOrder(TrainIncident incident) {
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
            bindHeader((HeaderViewHolder) holder, row);
        } else {
            bindIncident((IncidentViewHolder) holder, row.incident, position);
        }
    }

    private void bindHeader(HeaderViewHolder holder, Row row) {
        holder.label.setText(row.headerLabel.toUpperCase() + " (" + row.headerCount + ")");
        GradientDrawable dot = (GradientDrawable) holder.colorDot.getBackground();
        dot.setColor(row.headerColor);
        holder.chevron.setText(row.headerOpen ? "▲" : "▼");

        holder.itemView.setOnClickListener(v -> {
            if (expandedSections.contains(row.headerLabel)) {
                expandedSections.remove(row.headerLabel);
            } else {
                expandedSections.add(row.headerLabel);
            }
            // Toggle d'une section invalide les indices d'items expanded
            expandedItems.clear();
            rebuildRows();
        });
    }

    private void bindIncident(IncidentViewHolder holder, TrainIncident incident, int position) {
        boolean isExpanded = expandedItems.contains(position);

        holder.severity.setText(incident.getSeverityLabel());
        GradientDrawable badge = (GradientDrawable) holder.severity.getBackground();
        badge.setColor(incident.getSeverityColor());

        if (incident.getCause() != null && !incident.getCause().isEmpty()) {
            holder.cause.setText(incident.getCause());
            holder.cause.setVisibility(View.VISIBLE);
        } else {
            holder.cause.setVisibility(View.GONE);
        }

        holder.chevron.setText(isExpanded ? "▲" : "▼");

        String title = incident.getTitle();
        if (title != null && !title.isEmpty()
                && !title.equals("Perturbation Ligne N")
                && !title.equals(incident.getSeverityLabel())) {
            holder.title.setText(title);
            holder.title.setVisibility(View.VISIBLE);
        } else {
            holder.title.setVisibility(View.GONE);
        }

        if (isExpanded) {
            holder.message.setText(renderHtml(incident.getMessage()));
            holder.message.setVisibility(View.VISIBLE);
        } else {
            holder.message.setVisibility(View.GONE);
        }

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

        holder.container.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos == RecyclerView.NO_POSITION) return;
            if (expandedItems.contains(adapterPos)) {
                expandedItems.remove(adapterPos);
            } else {
                expandedItems.add(adapterPos);
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

    /** Row de la liste : soit un header de section, soit un incident. */
    private static class Row {
        String headerLabel;
        int headerColor;
        int headerCount;
        boolean headerOpen;
        TrainIncident incident;

        boolean isHeader() { return incident == null; }

        static Row header(String label, int color, int count, boolean open) {
            Row r = new Row();
            r.headerLabel = label;
            r.headerColor = color;
            r.headerCount = count;
            r.headerOpen = open;
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
        final TextView chevron;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            colorDot = itemView.findViewById(R.id.headerColorDot);
            label = itemView.findViewById(R.id.headerLabel);
            chevron = itemView.findViewById(R.id.headerChevron);
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
