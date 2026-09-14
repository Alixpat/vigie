package com.alixpat.vigie.adapter;

import com.alixpat.vigie.model.TrainSchedule;

/**
 * Pont entre une liste de trains et les épinglages du fragment : l'adapter
 * demande l'état courant au moment de dessiner la carte, et signale les
 * bascules. Il ne conserve rien lui-même — la vérité vit dans
 * {@link com.alixpat.vigie.train.PinnedTrains}, côté fragment.
 */
public interface TrainPinListener {

    /** @return true si ce train est actuellement suivi */
    boolean isPinned(TrainSchedule schedule);

    /** Bascule l'épinglage de ce train (suivi ↔ non suivi). */
    void onPinToggled(TrainSchedule schedule);
}
