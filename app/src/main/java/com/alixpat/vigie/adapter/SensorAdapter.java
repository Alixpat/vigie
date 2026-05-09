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
import com.alixpat.vigie.model.SensorStatus;
import com.alixpat.vigie.util.DateFormats;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class SensorAdapter extends RecyclerView.Adapter<SensorAdapter.ViewHolder> {

    private final List<SensorStatus> items = new ArrayList<>();

    public void updateItems(List<SensorStatus> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_sensor, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SensorStatus s = items.get(position);
        holder.nameText.setText(s.getName() != null ? s.getName() : s.getDeviceId());

        Render render = renderFor(s);

        // Indicateur d'état (couleur dépend du kind)
        int color = ContextCompat.getColor(holder.itemView.getContext(), render.statusColorRes);
        GradientDrawable indicator = (GradientDrawable) holder.statusIndicator.getBackground();
        indicator.setColor(color);

        // Ligne primaire = résumé sémantique du kind (ex. "OUVERTE" / "FERMÉE")
        if (render.primary != null) {
            holder.primaryText.setVisibility(View.VISIBLE);
            holder.primaryText.setText(render.primary);
            holder.primaryText.setTextColor(color);
        } else {
            holder.primaryText.setVisibility(View.GONE);
        }

        // Ligne détails (si on a des choses à dire)
        if (render.detail != null && !render.detail.isEmpty()) {
            holder.detailText.setVisibility(View.VISIBLE);
            holder.detailText.setText(render.detail);
        } else {
            holder.detailText.setVisibility(View.GONE);
        }

        // Footer : "il y a X (HH:mm:ss) · emetteur — bat 39% — RSSI -75 dBm"
        long ts = effectiveTimestamp(s);
        StringBuilder footer = new StringBuilder();
        if (ts > 0) {
            CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                    ts, System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
            footer.append(timeAgo).append(" (")
                    .append(DateFormats.formatHhmmss(new Date(ts))).append(")");
        }
        if (s.getEmetteur() != null && !s.getEmetteur().isEmpty()) {
            footer.append(" · ").append(s.getEmetteur());
        }
        if (s.getBatteryPercent() != null) {
            footer.append(" — bat ").append(String.format(Locale.getDefault(), "%.0f%%", s.getBatteryPercent()));
        }
        if (s.getRssi() != null) {
            footer.append(" — RSSI ").append(s.getRssi()).append(" dBm");
        }
        holder.lastUpdateText.setText(footer.toString());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    /** Préfère received_at (instant de la mesure côté capteur) à lastUpdate
     *  (instant de réception côté Android). lastUpdate peut tromper avec les
     *  retained messages : on connecte → on reçoit le retain → lastUpdate=now,
     *  même si la donnée a 1 h. */
    private static long effectiveTimestamp(SensorStatus s) {
        long parsed = DateFormats.parseIsoToMillis(s.getReceivedAt());
        return parsed > 0 ? parsed : s.getLastUpdate();
    }

    private static Render renderFor(SensorStatus s) {
        Map<String, Object> decoded = s.getDecoded();
        String kind = s.getKind();
        if ("door".equals(kind) && decoded != null) {
            return renderDoor(decoded);
        }
        return renderGeneric(decoded);
    }

    private static Render renderDoor(Map<String, Object> decoded) {
        Number open = asNumber(decoded.get("DOOR_OPEN_STATUS"));
        Render r = new Render();
        if (open == null) {
            r.primary = "État inconnu";
            r.statusColorRes = R.color.status_warning;
        } else if (open.intValue() == 1) {
            r.primary = "OUVERTE";
            r.statusColorRes = R.color.status_error;
        } else {
            r.primary = "FERMÉE";
            r.statusColorRes = R.color.status_ok;
        }

        StringBuilder sb = new StringBuilder();
        Number times = asNumber(decoded.get("DOOR_OPEN_TIMES"));
        if (times != null) {
            sb.append("Ouvertures : ").append(times.intValue());
        }
        Number lastDuration = asNumber(decoded.get("LAST_DOOR_OPEN_DURATION"));
        if (lastDuration != null && lastDuration.intValue() > 0) {
            if (sb.length() > 0) sb.append(" — ");
            sb.append("dernière : ").append(lastDuration.intValue()).append(" min");
        }
        r.detail = sb.toString();
        return r;
    }

    private static Render renderGeneric(Map<String, Object> decoded) {
        Render r = new Render();
        r.statusColorRes = R.color.status_ok;
        if (decoded == null || decoded.isEmpty()) {
            r.primary = "(aucune donnée décodée)";
            r.statusColorRes = R.color.status_warning;
            return r;
        }
        // Présentation en clé:valeur, triée alphabétiquement pour la stabilité visuelle
        StringBuilder sb = new StringBuilder();
        Map<String, Object> sorted = new TreeMap<>(decoded);
        boolean first = true;
        for (Map.Entry<String, Object> e : sorted.entrySet()) {
            if (!first) sb.append(" · ");
            sb.append(e.getKey()).append(": ").append(formatValue(e.getValue()));
            first = false;
        }
        r.primary = sb.toString();
        return r;
    }

    private static String formatValue(Object v) {
        if (v == null) return "null";
        if (v instanceof Double) {
            double d = (Double) v;
            // Affiche les entiers sans décimale, sinon 3 décimales max
            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                return String.valueOf((long) d);
            }
            return String.format(Locale.getDefault(), "%.3f", d);
        }
        return v.toString();
    }

    private static Number asNumber(Object v) {
        return v instanceof Number ? (Number) v : null;
    }

    private static class Render {
        String primary;
        String detail;
        int statusColorRes = R.color.status_ok;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View statusIndicator;
        final TextView nameText;
        final TextView primaryText;
        final TextView detailText;
        final TextView lastUpdateText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            nameText = itemView.findViewById(R.id.nameText);
            primaryText = itemView.findViewById(R.id.primaryText);
            detailText = itemView.findViewById(R.id.detailText);
            lastUpdateText = itemView.findViewById(R.id.lastUpdateText);
        }
    }
}
