package com.alixpat.vigie.adapter;

import android.content.Context;
import android.graphics.Paint;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;

import java.util.ArrayList;
import java.util.List;

public class TrainScheduleAdapter extends RecyclerView.Adapter<TrainScheduleAdapter.ViewHolder> {

    private static final String SEPARATOR = " · ";

    public interface OnTrainClickListener {
        void onTrainClick(TrainSchedule schedule);
    }

    private final List<TrainSchedule> schedules = new ArrayList<>();
    private OnTrainClickListener clickListener;
    private TrainPinListener pinListener;

    public void setOnTrainClickListener(OnTrainClickListener listener) {
        this.clickListener = listener;
    }

    /** Sans listener, l'épingle reste invisible : la liste ne sait rien des suivis. */
    public void setPinListener(TrainPinListener listener) {
        this.pinListener = listener;
    }

    public void updateSchedules(List<TrainSchedule> newData) {
        schedules.clear();
        schedules.addAll(newData);
        notifyDataSetChanged();
    }

    public List<TrainSchedule> getSchedules() {
        return new ArrayList<>(schedules);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_train_schedule, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TrainSchedule schedule = schedules.get(position);
        Context context = holder.itemView.getContext();

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onTrainClick(schedule);
            }
        });

        holder.divider.setVisibility(position == 0 ? View.GONE : View.VISIBLE);
        PinIcons.bind(holder.pin, schedule, pinListener);

        holder.destination.setText(schedule.getDestination());
        TrainStyle.statusPill(holder.status, schedule);
        setOrHide(holder.trainInfo, TrainStyle.trainInfo(schedule));

        int textPrimary = ContextCompat.getColor(context, R.color.text_primary);
        int textHint = ContextCompat.getColor(context, R.color.text_hint);

        // Heure au tableau : barrée dès qu'elle ne tient plus (retard ou suppression),
        // l'heure réelle prenant sa place juste en dessous.
        boolean struck = schedule.isCancelled() || schedule.isDelayed();
        holder.aimedTime.setText(schedule.getAimedDepartureTime());
        holder.aimedTime.setTextColor(struck ? textHint : textPrimary);
        setStrikeThrough(holder.aimedTime, struck);

        if (schedule.isDelayed()) {
            holder.expectedTime.setText(schedule.getExpectedDepartureTime());
            holder.expectedTime.setTextColor(TrainStyle.statusColor(context, schedule));
            holder.expectedTime.setVisibility(View.VISIBLE);
        } else {
            holder.expectedTime.setVisibility(View.GONE);
        }

        CharSequence details = buildDetails(schedule);
        setOrHide(holder.details, details);
        setStrikeThrough(holder.details, schedule.isCancelled());
    }

    /** "Arrivée 08:45 · 32min 05s · Voie 2", les secondes en plus petit. */
    private static CharSequence buildDetails(TrainSchedule schedule) {
        SpannableStringBuilder details = new SpannableStringBuilder();
        String arrival = schedule.getArrivalTime();
        if (arrival != null && !arrival.isEmpty()) {
            details.append("Arrivée ").append(arrival);
        }
        String travelTime = schedule.getTravelTime();
        if (travelTime != null) {
            if (details.length() > 0) details.append(SEPARATOR);
            int start = details.length();
            details.append(travelTime);
            // Les secondes (" 05s") en retrait : elles précisent, elles ne se lisent pas.
            int secondsStart = travelTime.lastIndexOf(' ');
            if (secondsStart > 0) {
                details.setSpan(new RelativeSizeSpan(0.8f), start + secondsStart,
                        details.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        String platform = schedule.getPlatformName();
        if (platform != null && !platform.isEmpty()) {
            if (details.length() > 0) details.append(SEPARATOR);
            details.append("Voie ").append(platform);
        }
        return details;
    }

    private static void setStrikeThrough(TextView view, boolean struck) {
        int flags = view.getPaintFlags();
        view.setPaintFlags(struck ? flags | Paint.STRIKE_THRU_TEXT_FLAG
                : flags & ~Paint.STRIKE_THRU_TEXT_FLAG);
    }

    private static void setOrHide(TextView view, CharSequence text) {
        if (text == null || text.length() == 0) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return schedules.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View divider;
        final TextView aimedTime;
        final TextView expectedTime;
        final TextView destination;
        final TextView details;
        final TextView trainInfo;
        final TextView status;
        final ImageView pin;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            divider = itemView.findViewById(R.id.scheduleDivider);
            aimedTime = itemView.findViewById(R.id.scheduleAimedTime);
            expectedTime = itemView.findViewById(R.id.scheduleExpectedTime);
            destination = itemView.findViewById(R.id.scheduleDestination);
            details = itemView.findViewById(R.id.scheduleDetails);
            trainInfo = itemView.findViewById(R.id.scheduleTrainInfo);
            status = itemView.findViewById(R.id.scheduleStatus);
            pin = itemView.findViewById(R.id.schedulePin);
        }
    }
}
