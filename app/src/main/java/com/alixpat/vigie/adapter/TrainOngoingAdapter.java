package com.alixpat.vigie.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.OngoingTrain;

import java.util.ArrayList;
import java.util.List;

/**
 * Trains actuellement en circulation sur mon trajet : une carte par train,
 * avec sa position à l'instant T. Tous les libellés sont déjà calculés dans
 * {@link OngoingTrain} — cet adapter ne fait que les poser.
 */
public class TrainOngoingAdapter extends RecyclerView.Adapter<TrainOngoingAdapter.ViewHolder> {

    public interface OnOngoingTrainClickListener {
        void onOngoingTrainClick(OngoingTrain train);
    }

    private final List<OngoingTrain> trains = new ArrayList<>();
    private OnOngoingTrainClickListener clickListener;

    public void setOnOngoingTrainClickListener(OnOngoingTrainClickListener listener) {
        this.clickListener = listener;
    }

    public void updateTrains(List<OngoingTrain> newData) {
        trains.clear();
        if (newData != null) trains.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_train_ongoing, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OngoingTrain train = trains.get(position);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onOngoingTrainClick(train);
        });

        holder.direction.setText(train.getDirectionLabel());
        holder.status.setText(train.getSchedule().getStatusLabel());
        holder.status.setTextColor(train.getSchedule().getStatusColor());
        holder.stripe.setBackgroundColor(train.getSchedule().getStatusColor());

        holder.position.setText(train.getPositionLabel());
        holder.position.setVisibility(train.getPositionLabel().isEmpty() ? View.GONE : View.VISIBLE);

        if (train.getProgressPercent() >= 0) {
            holder.progress.setProgress(train.getProgressPercent());
            holder.progress.setVisibility(View.VISIBLE);
        } else {
            holder.progress.setVisibility(View.GONE);
        }

        setOrHide(holder.nextStop, train.getNextStopLabel());
        setOrHide(holder.eta, train.getEtaLabel());
        setOrHide(holder.trainInfo, train.getTrainInfoLabel());

        StringBuilder departure = new StringBuilder();
        String aimed = train.getSchedule().getAimedDepartureTime();
        if (aimed != null && !aimed.isEmpty()) {
            departure.append("Départ ").append(train.getSchedule().getOriginStation())
                    .append(" · ").append(aimed);
            String expected = train.getSchedule().getExpectedDepartureTime();
            if (expected != null && !expected.isEmpty()) {
                departure.append(" → ").append(expected);
            }
        }
        String platform = train.getSchedule().getPlatformName();
        if (platform != null && !platform.isEmpty()) {
            if (departure.length() > 0) departure.append(" · ");
            departure.append("Voie ").append(platform);
        }
        setOrHide(holder.departure, departure.toString());
    }

    private static void setOrHide(TextView view, String text) {
        if (text == null || text.isEmpty()) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return trains.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View stripe;
        final TextView direction;
        final TextView status;
        final TextView position;
        final ProgressBar progress;
        final TextView nextStop;
        final TextView eta;
        final TextView departure;
        final TextView trainInfo;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            stripe = itemView.findViewById(R.id.ongoingStripe);
            direction = itemView.findViewById(R.id.ongoingDirection);
            status = itemView.findViewById(R.id.ongoingStatus);
            position = itemView.findViewById(R.id.ongoingPosition);
            progress = itemView.findViewById(R.id.ongoingProgress);
            nextStop = itemView.findViewById(R.id.ongoingNextStop);
            eta = itemView.findViewById(R.id.ongoingEta);
            departure = itemView.findViewById(R.id.ongoingDeparture);
            trainInfo = itemView.findViewById(R.id.ongoingTrainInfo);
        }
    }
}
