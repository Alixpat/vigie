package com.alixpat.vigie.adapter;

import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.alixpat.vigie.R;
import com.alixpat.vigie.model.TrainSchedule;

/**
 * Rendu commun de l'épingle sur les cartes de train : les deux listes (départs
 * et circulation) l'affichent de la même façon, donc elles la dessinent au même
 * endroit.
 *
 * <p>Un train suivi porte l'épingle à la couleur de la ligne, un train quelconque
 * la même épingle estompée : la place reste la même d'une carte à l'autre, donc
 * la colonne ne bouge pas quand on épingle.</p>
 */
final class PinIcons {

    /** Opacité de l'épingle d'un train non suivi : présente mais discrète. */
    private static final float IDLE_ALPHA = 0.35f;

    private PinIcons() {}

    static void bind(ImageView pin, TrainSchedule schedule, TrainPinListener listener) {
        if (pin == null) return;
        if (listener == null || schedule == null) {
            pin.setVisibility(View.GONE);
            return;
        }
        boolean pinned = listener.isPinned(schedule);
        pin.setVisibility(View.VISIBLE);
        pin.setColorFilter(ContextCompat.getColor(pin.getContext(),
                pinned ? R.color.line_n : R.color.text_hint));
        pin.setAlpha(pinned ? 1f : IDLE_ALPHA);
        pin.setContentDescription(pinned ? "Ne plus suivre ce train" : "Suivre ce train");
        pin.setOnClickListener(v -> listener.onPinToggled(schedule));
    }
}
