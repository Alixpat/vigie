package com.alixpat.vigie.train;

import com.alixpat.vigie.model.TrainStop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Position d'un train sur son parcours à l'instant T, déduite de la liste
 * d'arrêts (estimated-timetable) et des horaires de chaque arrêt.
 *
 * Logique reprise de l'affichage "arrêt par arrêt" du détail d'un train et
 * du plan de la ligne, extraite ici pour être partagée et testable.
 */
public final class TrainPosition {

    public enum Phase {
        /** Le train n'a pas encore quitté son origine. */
        NOT_STARTED,
        /** Le train est à quai dans une gare. */
        AT_STOP,
        /** Le train roule entre deux gares. */
        BETWEEN,
        /** Le train a atteint son terminus. */
        ARRIVED,
        /** Pas assez de données pour se prononcer. */
        UNKNOWN
    }

    private final Phase phase;
    private final String currentStopName;
    private final String nextStopName;
    private final long nextStopMillis;
    private final int progressPercent;

    private TrainPosition(Phase phase, String currentStopName, String nextStopName,
                          long nextStopMillis, int progressPercent) {
        this.phase = phase;
        this.currentStopName = currentStopName != null ? currentStopName : "";
        this.nextStopName = nextStopName != null ? nextStopName : "";
        this.nextStopMillis = nextStopMillis;
        this.progressPercent = progressPercent;
    }

    public static TrainPosition unknown() {
        return new TrainPosition(Phase.UNKNOWN, "", "", 0L, -1);
    }

    /**
     * @param stops arrêts du trajet (ordre quelconque, triés en interne)
     * @param now   instant de référence en epoch millis
     * @return la position du train ; jamais null ({@link Phase#UNKNOWN} si indéterminable)
     */
    public static TrainPosition compute(List<TrainStop> stops, long now) {
        if (stops == null || stops.isEmpty()) return unknown();

        List<TrainStop> sorted = new ArrayList<>(stops);
        Collections.sort(sorted, (a, b) -> Long.compare(a.getBestTimeMillis(), b.getBestTimeMillis()));

        int progress = computeProgress(sorted, now);

        // 1. Un arrêt "CURRENT" = le train est à quai.
        for (int i = 0; i < sorted.size(); i++) {
            if (statusAt(sorted.get(i), now) == TrainStop.StopStatus.CURRENT) {
                TrainStop next = (i + 1 < sorted.size()) ? sorted.get(i + 1) : null;
                return new TrainPosition(Phase.AT_STOP,
                        sorted.get(i).getStopName(),
                        next != null ? next.getStopName() : "",
                        next != null ? next.getBestArrivalMillis() : 0L,
                        progress);
            }
        }

        // 2. Une frontière PASSED → UPCOMING = le train roule entre les deux.
        for (int i = 0; i < sorted.size() - 1; i++) {
            if (statusAt(sorted.get(i), now) == TrainStop.StopStatus.PASSED
                    && statusAt(sorted.get(i + 1), now) == TrainStop.StopStatus.UPCOMING) {
                return new TrainPosition(Phase.BETWEEN,
                        sorted.get(i).getStopName(),
                        sorted.get(i + 1).getStopName(),
                        sorted.get(i + 1).getBestArrivalMillis(),
                        progress);
            }
        }

        // 3. Tous les arrêts à venir / tous passés.
        boolean allUpcoming = true;
        boolean allPassed = true;
        for (TrainStop stop : sorted) {
            TrainStop.StopStatus status = statusAt(stop, now);
            if (status != TrainStop.StopStatus.UPCOMING) allUpcoming = false;
            if (status != TrainStop.StopStatus.PASSED) allPassed = false;
        }
        if (allUpcoming) {
            TrainStop first = sorted.get(0);
            TrainStop next = sorted.size() > 1 ? sorted.get(1) : null;
            return new TrainPosition(Phase.NOT_STARTED,
                    first.getStopName(),
                    next != null ? next.getStopName() : "",
                    next != null ? next.getBestArrivalMillis() : 0L,
                    progress);
        }
        if (allPassed) {
            return new TrainPosition(Phase.ARRIVED,
                    sorted.get(sorted.size() - 1).getStopName(), "", 0L, progress);
        }
        return unknown();
    }

    /**
     * Même règle que {@link TrainStop#getStatus()}, mais à un instant injecté :
     * la position doit être reproductible (et testable) pour un {@code now} donné.
     */
    public static TrainStop.StopStatus statusAt(TrainStop stop, long now) {
        long arrival = stop.getBestArrivalMillis();
        long departure = stop.getBestTimeMillis();

        if (departure > 0 && now > departure) return TrainStop.StopStatus.PASSED;
        if (arrival > 0 && now >= arrival && (departure <= 0 || now <= departure)) {
            return TrainStop.StopStatus.CURRENT;
        }
        if (stop.isArrival() && arrival > 0 && now >= arrival) return TrainStop.StopStatus.CURRENT;
        return TrainStop.StopStatus.UPCOMING;
    }

    /** Avancement sur l'ensemble du parcours, en % ; -1 si les horaires manquent. */
    private static int computeProgress(List<TrainStop> sorted, long now) {
        long start = sorted.get(0).getBestTimeMillis();
        long end = sorted.get(sorted.size() - 1).getBestArrivalMillis();
        if (start <= 0 || end <= start) return -1;
        long percent = ((now - start) * 100L) / (end - start);
        return (int) Math.max(0L, Math.min(100L, percent));
    }

    public Phase getPhase() { return phase; }
    public String getCurrentStopName() { return currentStopName; }
    public String getNextStopName() { return nextStopName; }
    public long getNextStopMillis() { return nextStopMillis; }
    public int getProgressPercent() { return progressPercent; }

    public boolean isKnown() {
        return phase != Phase.UNKNOWN;
    }

    /** Libellé lisible, en français, avec un pictogramme de statut. */
    public String describe() {
        switch (phase) {
            case AT_STOP:
                return "\uD83D\uDCCD En gare de " + currentStopName;
            case BETWEEN:
                return "\uD83D\uDE86 Entre " + currentStopName + " et " + nextStopName;
            case NOT_STARTED:
                return "\u23F3 Pas encore parti de " + currentStopName;
            case ARRIVED:
                return "\u2705 Arrivé à " + currentStopName;
            default:
                return "";
        }
    }
}
