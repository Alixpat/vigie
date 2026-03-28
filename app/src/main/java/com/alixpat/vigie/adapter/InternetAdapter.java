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
import com.alixpat.vigie.model.InternetStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InternetAdapter extends RecyclerView.Adapter<InternetAdapter.ViewHolder> {

    private final List<InternetStatus> items = new ArrayList<>();

    public void updateItems(List<InternetStatus> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_internet, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InternetStatus item = items.get(position);
        holder.nameText.setText(item.getName());

        String subtitle;
        if (item.isUp() && item.getLatencyMs() != null) {
            subtitle = String.format(Locale.getDefault(), "%s — %.0f ms", item.getHost(), item.getLatencyMs());
        } else if (item.getHost() != null) {
            subtitle = item.getHost();
        } else {
            subtitle = "";
        }
        holder.detailText.setText(subtitle);

        int colorRes = item.isUp() ? R.color.status_ok : R.color.status_error;
        int color = ContextCompat.getColor(holder.itemView.getContext(), colorRes);
        GradientDrawable indicator = (GradientDrawable) holder.statusIndicator.getBackground();
        indicator.setColor(color);

        // Dernière coupure
        if (item.getLastDowntimeDurationMinutes() != null && item.getLastDowntimeDurationMinutes() > 0) {
            double mins = item.getLastDowntimeDurationMinutes();
            String durationStr;
            if (mins >= 60) {
                int h = (int) (mins / 60);
                int m = (int) (mins % 60);
                durationStr = h + "h" + (m > 0 ? String.format(Locale.getDefault(), "%02dm", m) : "");
            } else {
                durationStr = String.format(Locale.getDefault(), "%.0f min", mins);
            }
            holder.downtimeText.setText("Dernière coupure : " + durationStr);
            holder.downtimeText.setVisibility(View.VISIBLE);
        } else {
            holder.downtimeText.setVisibility(View.GONE);
        }

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                item.getLastUpdate(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String exactTime = sdf.format(new Date(item.getLastUpdate()));
        holder.lastUpdateText.setText(timeAgo + " (" + exactTime + ")");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View statusIndicator;
        final TextView nameText;
        final TextView detailText;
        final TextView downtimeText;
        final TextView lastUpdateText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            nameText = itemView.findViewById(R.id.nameText);
            detailText = itemView.findViewById(R.id.detailText);
            downtimeText = itemView.findViewById(R.id.downtimeText);
            lastUpdateText = itemView.findViewById(R.id.lastUpdateText);
        }
    }
}
