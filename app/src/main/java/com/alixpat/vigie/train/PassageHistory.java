package com.alixpat.vigie.train;

import com.alixpat.vigie.model.TrainStop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Historique des passages d'un train sur <b>mon tronçon</b>, gare par gare, avec
 * pour chacune l'heure théorique et l'heure réalisée.
 *
 * <p>La source est le parcours mémorisé par {@link JourneyRoutes}. C'est lui qui
 * rend l'historique possible : l'{@code estimated-timetable} d'IDFM ne décrit un
 * train que tel qu'il lui reste à circuler, donc une gare disparaît de la réponse
 * dès qu'elle est franchie. Le parcours fusionné conserve la dernière version
 * connue de chaque arrêt — pour une gare déjà passée, son heure estimée est donc
 * l'heure à laquelle le train y est effectivement passé.</p>
 *
 * <p>D'où la distinction portée par {@link Passage#isPassed()} : avant le
 * passage, l'heure « réalisée » n'est qu'une prévision ; après, c'est une
 * observation. Les deux se lisent au même endroit, seul le temps les sépare, et
 * l'affichage doit le dire plutôt que de les confondre.</p>
 *
 * <p>Le tronçon retenu est le corridor {@link LineSegment} du sens concerné (mes
 * deux gares et tout ce qu'il y a entre elles). Les gares que le train ne dessert
 * pas — un semi-direct en saute — n'apparaissent tout simplement pas : on
 * n'affiche que ce que les données décrivent. Si le corridor est vide (couple de
 * gares hors de l'axe décrit par {@link com.alixpat.vigie.model.LineNStation}),
 * on retombe sur le parcours complet plutôt que de ne rien montrer.</p>
 */
public final class PassageHistory {

    private PassageHistory() {}

    /** Le passage d'un train à une gare : ce qui était prévu, ce qui s'est produit. */
    public static final class Passage {

        private final String stationName;
        private final long aimedMillis;
        private final long actualMillis;
        private final boolean passed;

        public Passage(String stationName, long aimedMillis, long actualMillis, boolean passed) {
            this.stationName = stationName != null ? stationName : "";
            this.aimedMillis = aimedMillis;
            this.actualMillis = actualMillis;
            this.passed = passed;
        }

        public String getStationName() { return stationName; }

        /** Heure théorique (horaire au tableau), 0 si non publiée. */
        public long getAimedMillis() { return aimedMillis; }

        /**
         * Heure réalisée si la gare est déjà franchie, estimée sinon ; 0 quand
         * IDFM n'annonce rien (le théorique fait alors seul foi).
         */
        public long getActualMillis() { return actualMillis; }

        /** @return true si le train a déjà franchi cette gare à l'instant demandé */
        public boolean isPassed() { return passed; }

        /** @return true si une heure réalisée/estimée distincte du théorique est connue */
        public boolean hasActual() { return actualMillis > 0; }

        /**
         * Écart à l'horaire, en minutes : positif en retard, négatif en avance,
         * 0 quand l'un des deux horaires manque.
         */
        public int getDelayMinutes() {
            if (aimedMillis <= 0 || actualMillis <= 0) return 0;
            return (int) Math.round((actualMillis - aimedMillis) / 60_000.0);
        }
    }

    /**
     * Construit l'historique des passages sur mon tronçon.
     *
     * @param stops     parcours du train (idéalement déjà résolu en noms de gares)
     * @param direction sens de circulation, qui porte le corridor
     * @param now       instant de référence en epoch millis
     * @return les passages dans l'ordre de circulation ; vide si le parcours est inconnu
     */
    public static List<Passage> build(List<TrainStop> stops, LineNDirection direction, long now) {
        List<Passage> passages = new ArrayList<>();
        if (stops == null || stops.isEmpty() || direction == null) return passages;

        List<TrainStop> ordered = new ArrayList<>(stops);
        Collections.sort(ordered, (a, b) -> Long.compare(timeOf(a), timeOf(b)));

        LineSegment segment = direction.getSegment();
        for (TrainStop stop : ordered) {
            if (stop == null) continue;
            // Corridor vide : le couple de gares n'est pas décrit par l'axe de la
            // ligne, on montre le parcours entier plutôt que rien.
            if (!segment.isEmpty() && segment.indexOf(stop.getStopName()) < 0) continue;

            // Théorique et réalisé doivent se lire dans la même famille de champs,
            // sinon l'écart affiché compare une arrivée à un départ.
            boolean useArrival = stop.getAimedArrivalMillis() > 0;
            long aimed = useArrival ? stop.getAimedArrivalMillis() : stop.getAimedDepartureMillis();
            long actual = useArrival
                    ? stop.getExpectedArrivalMillis() : stop.getExpectedDepartureMillis();
            if (aimed <= 0 && actual <= 0) continue;

            long reference = actual > 0 ? actual : aimed;
            passages.add(new Passage(stop.getStopName(), aimed, actual, reference <= now));
        }
        return passages;
    }

    /** Instant utilisé pour ordonner le parcours : arrivée si connue, sinon départ. */
    private static long timeOf(TrainStop stop) {
        long arrival = stop.getBestArrivalMillis();
        return arrival > 0 ? arrival : stop.getBestTimeMillis();
    }
}
