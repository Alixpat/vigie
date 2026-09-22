package com.alixpat.vigie.adapter;

import android.content.Context;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.util.UiStyle;

/**
 * Rendu partagé de l'onglet Train : la couleur d'un statut, la pastille qui le
 * porte, le libellé d'un train. Les cartes, les listes et les dialogues passent
 * tous par ici, donc un « +4 min » a la même allure partout.
 *
 * <p>Les couleurs viennent des ressources ({@code values} / {@code values-night})
 * et jamais d'une constante : c'est ce qui garde l'écran lisible en mode nuit.</p>
 */
public final class TrainStyle {

    private TrainStyle() {}

    /** Couleur du statut d'un train : supprimé, en retard ou à l'heure. */
    public static int statusColor(Context context, TrainSchedule schedule) {
        int res;
        if (schedule.isCancelled()) {
            res = R.color.status_error;
        } else if (schedule.isDelayed()) {
            res = R.color.status_warning;
        } else {
            res = R.color.status_ok;
        }
        return ContextCompat.getColor(context, res);
    }

    /** Pose le statut d'un train dans une pastille. */
    public static void statusPill(TextView view, TrainSchedule schedule) {
        view.setText(schedule.getStatusLabel());
        pill(view, statusColor(view.getContext(), schedule));
    }

    /** Pastille commune à toute l'app, voir {@link UiStyle#pill}. */
    public static void pill(TextView view, int color) {
        UiStyle.pill(view, color);
    }

    /** "Train 135642 · MOPI", "Train 135642", "MOPI" ou "" selon ce qui est connu. */
    public static String trainInfo(TrainSchedule schedule) {
        StringBuilder info = new StringBuilder();
        String number = schedule.getTrainNumber();
        if (number != null && !number.isEmpty()) {
            info.append("Train ").append(number);
        }
        String mission = schedule.getMissionName();
        if (mission != null && !mission.isEmpty()) {
            if (info.length() > 0) info.append(" · ");
            info.append(mission);
        }
        return info.toString();
    }
}
