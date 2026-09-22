package com.alixpat.vigie.adapter;

import android.content.Context;
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
import com.alixpat.vigie.util.UiStyle;

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
        // L'ancien premier gagne son filet de séparation : il faut le redessiner.
        if (messages.size() > 1) notifyItemChanged(1);
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

        Context context = holder.itemView.getContext();
        holder.divider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);

        // Le type en pastille, rouge pour un message prioritaire
        String type = msg.getType();
        if (type != null && !type.isEmpty()) {
            holder.typeText.setText(type);
            UiStyle.pill(holder.typeText, ContextCompat.getColor(context,
                    msg.isHighPriority() ? R.color.status_error : R.color.text_secondary));
            holder.typeText.setVisibility(View.VISIBLE);
        } else {
            holder.typeText.setVisibility(View.GONE);
        }

        holder.titleText.setText(msg.getTitle() != null ? msg.getTitle() : "");
        int titleColor = msg.isHighPriority() ? R.color.status_error : R.color.text_primary;
        holder.titleText.setTextColor(ContextCompat.getColor(context, titleColor));

        holder.bodyText.setText(msg.getMessage() != null ? msg.getMessage() : "");
        holder.timeText.setText(DateFormats.formatDdMmHhmmss(new Date(msg.getReceivedAt())));
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View divider;
        final TextView typeText;
        final TextView titleText;
        final TextView bodyText;
        final TextView timeText;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            divider = itemView.findViewById(R.id.messageDivider);
            typeText = itemView.findViewById(R.id.messageTypeText);
            titleText = itemView.findViewById(R.id.messageTitleText);
            bodyText = itemView.findViewById(R.id.messageBodyText);
            timeText = itemView.findViewById(R.id.messageTimeText);
        }
    }
}
