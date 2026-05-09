package com.alixpat.vigie.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import androidx.core.content.ContextCompat;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.VigieMessage;
import com.alixpat.vigie.util.DateFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.ViewHolder> {

    // Stocké en ordre antéchronologique : index 0 = message le plus récent.
    private final List<VigieMessage> messages = new ArrayList<>();

    public void setMessages(List<VigieMessage> newMessages) {
        messages.clear();
        messages.addAll(newMessages);
        Collections.reverse(messages);
        notifyDataSetChanged();
    }

    public void addMessage(VigieMessage message) {
        messages.add(0, message);
        notifyItemInserted(0);
    }

    public void clear() {
        int n = messages.size();
        if (n == 0) return;
        messages.clear();
        notifyItemRangeRemoved(0, n);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VigieMessage msg = messages.get(position);

        String type = msg.getType() != null ? "[" + msg.getType() + "] " : "";
        String title = msg.getTitle() != null ? msg.getTitle() : "";
        holder.titleText.setText(type + title);

        holder.bodyText.setText(msg.getMessage() != null ? msg.getMessage() : "");

        holder.timeText.setText(DateFormats.formatDdMmHhmmss(new Date(msg.getReceivedAt())));

        int colorRes = msg.isHighPriority() ? R.color.status_error : R.color.text_primary;
        holder.titleText.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), colorRes));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView titleText;
        final TextView bodyText;
        final TextView timeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.messageTitleText);
            bodyText = itemView.findViewById(R.id.messageBodyText);
            timeText = itemView.findViewById(R.id.messageTimeText);
        }
    }
}
