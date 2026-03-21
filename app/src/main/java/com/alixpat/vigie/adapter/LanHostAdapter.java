package com.alixpat.vigie.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.LanHost;

import java.util.ArrayList;
import java.util.List;

public class LanHostAdapter extends RecyclerView.Adapter<LanHostAdapter.ViewHolder> {

    private final List<LanHost> hosts = new ArrayList<>();

    public void updateHosts(List<LanHost> newHosts) {
        hosts.clear();
        hosts.addAll(newHosts);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lan_host, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LanHost host = hosts.get(position);
        holder.hostnameText.setText(host.getHostname());
        holder.ipText.setText(host.getIp());

        int color = host.isUp() ? 0xFF4CAF50 : 0xFFF44336;
        GradientDrawable indicator = (GradientDrawable) holder.statusIndicator.getBackground();
        indicator.setColor(color);

        CharSequence timeAgo = DateUtils.getRelativeTimeSpanString(
                host.getLastUpdate(), System.currentTimeMillis(), DateUtils.SECOND_IN_MILLIS);
        holder.lastUpdateText.setText(timeAgo);
    }

    @Override
    public int getItemCount() {
        return hosts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View statusIndicator;
        final TextView hostnameText;
        final TextView ipText;
        final TextView lastUpdateText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            statusIndicator = itemView.findViewById(R.id.statusIndicator);
            hostnameText = itemView.findViewById(R.id.hostnameText);
            ipText = itemView.findViewById(R.id.ipText);
            lastUpdateText = itemView.findViewById(R.id.lastUpdateText);
        }
    }
}
