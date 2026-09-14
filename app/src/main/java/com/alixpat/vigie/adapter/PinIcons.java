package com.alixpat.vigie.adapter;

import android.view.View;
import android.widget.TextView;

import com.alixpat.vigie.model.TrainSchedule;

/**
 * Rendu commun de l'épingle sur les cartes de train : les deux listes (départs
 * et circulation) l'affichent de la même façon, donc elles la dessinent au même
 * endroit.
 *
 * <p>Un train suivi porte l'épingle pleine, un train quelconque la même épingle
 * estompée : la place reste la même d'une carte à l'autre, donc la colonne ne
 * bouge pas quand on épingle.</p>
 */
final class PinIcons {

    /** Opacité de l'épingle d'un train non suivi : présente mais discrète. */
    private static final float IDLE_ALPHA = 0.25f;

    private PinIcons() {}

    static void bind(TextView pin, TrainSchedule schedule, TrainPinListener listener) {
        if (pin == null) return;
        if (listener == null || schedule == null) {
            pin.setVisibility(View.GONE);
            return;
        }
        boolean pinned = listener.isPinned(schedule);
        pin.setVisibility(View.VISIBLE);
        pin.setAlpha(pinned ? 1f : IDLE_ALPHA);
        pin.setContentDescription(pinned ? "Ne plus suivre ce train" : "Suivre ce train");
        pin.setOnClickListener(v -> listener.onPinToggled(schedule));
    }
}
