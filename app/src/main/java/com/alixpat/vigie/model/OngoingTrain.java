package com.alixpat.vigie.model;

/**
 * Un train actuellement en circulation sur mon trajet (Clamart ↔ Villepreux),
 * prêt à être affiché : le {@link TrainSchedule} d'origine + les libellés
 * déjà calculés (position, prochain arrêt, arrivée estimée).
 *
 * Construit par {@code com.alixpat.vigie.train.OngoingTrains.describe(...)} :
 * l'adapter ne fait aucun calcul, il pose les textes.
 */
public class OngoingTrain {

    private final TrainSchedule schedule;
    private final String directionLabel;   // "Clamart → Villepreux"
    private final String positionLabel;    // "🚆 Entre Clamart et Meudon"
    private final int progressPercent;     // 0..100, -1 si inconnu
    private final String nextStopLabel;    // "Prochain arrêt : Meudon · 18:12" ou ""
    private final String etaLabel;         // "Arrivée Villepreux · 18:37 (dans 12 min)" ou ""
    private final String trainInfoLabel;   // "Train 135642 • MOPI" ou ""

    public OngoingTrain(TrainSchedule schedule, String directionLabel, String positionLabel,
                        int progressPercent, String nextStopLabel, String etaLabel,
                        String trainInfoLabel) {
        this.schedule = schedule;
        this.directionLabel = directionLabel != null ? directionLabel : "";
        this.positionLabel = positionLabel != null ? positionLabel : "";
        this.progressPercent = progressPercent;
        this.nextStopLabel = nextStopLabel != null ? nextStopLabel : "";
        this.etaLabel = etaLabel != null ? etaLabel : "";
        this.trainInfoLabel = trainInfoLabel != null ? trainInfoLabel : "";
    }

    public TrainSchedule getSchedule() { return schedule; }
    public String getDirectionLabel() { return directionLabel; }
    public String getPositionLabel() { return positionLabel; }
    public int getProgressPercent() { return progressPercent; }
    public String getNextStopLabel() { return nextStopLabel; }
    public String getEtaLabel() { return etaLabel; }
    public String getTrainInfoLabel() { return trainInfoLabel; }

    public String getJourneyRef() {
        return schedule != null ? schedule.getJourneyRef() : "";
    }
}
