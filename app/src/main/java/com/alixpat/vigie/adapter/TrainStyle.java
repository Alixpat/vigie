package com.alixpat.vigie.adapter;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;

/**
 * Rendu partagé de l'onglet Train : la couleur d'un statut, la pastille qui le
 * porte, le libellé d'un train. Les cartes, les listes et les dialogues passent
 * tous par ici, donc un « +4 min » a la même allure partout.
 *
 * <p>Les couleurs viennent des ressources ({@code values} / {@code values-night})
 * et jamais d'une constante : c'est ce qui garde l'écran lisible en mode nuit.</p>
 */
public final class TrainStyle {

    /** Opacité du fond d'une pastille : une teinte estompée de sa propre couleur. */
    private static final int PILL_BACKGROUND_ALPHA = 0x26;

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

    /**
     * Habille un texte en pastille : texte de la couleur donnée, sur un fond de
     * la même couleur estompée. Un fond neuf à chaque appel — muter le drawable
     * du layout le partagerait entre toutes les lignes de la liste.
     */
    public static void pill(TextView view, int color) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.RECTANGLE);
        background.setCornerRadius(view.getResources().getDisplayMetrics().density * 100);
        background.setColor(ColorUtils.setAlphaComponent(color, PILL_BACKGROUND_ALPHA));
        view.setBackground(background);
        view.setTextColor(color);
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
