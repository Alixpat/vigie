package com.alixpat.vigie.train;

import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.TrainStop;

import java.util.Date;
import java.util.List;

/**
 * Le tronçon qui me concerne — ma gare de départ → ma gare d'arrivée — pour un
 * train donné : horaires théoriques et estimés à mes deux extrémités.
 *
 * C'est ce qui remplace le filtrage par mots-clés du terminus : un train me
 * concerne s'il <b>dessert mes deux gares</b>, et son sens de circulation est
 * donné par l'ordre dans lequel il les dessert. Le terminus annoncé n'est qu'une
 * étiquette : « Paris Montparnasse », « Plaisir - Grignon », « Épône - Mézières »…
 * Chercher des mots-clés dedans écarte des trains qui passent bel et bien chez moi.
 *
 * Trois sources sont fusionnées, par ordre de fraîcheur :
 * le stop-monitoring de ma gare de départ, celui de ma gare d'arrivée, puis le
 * parcours complet (estimated-timetable) — seul à connaître les trains déjà partis.
 */
public final class SegmentLeg {

    private final long departureMillis;          // départ théorique de ma gare (0 = inconnu)
    private final long expectedDepartureMillis;  // départ estimé (0 = non renseigné)
    private final long arrivalMillis;            // arrivée théorique à ma gare (0 = inconnu)
    private final long expectedArrivalMillis;    // arrivée estimée (0 = non renseignée)
    private final boolean cancelled;

    private SegmentLeg(long departureMillis, long expectedDepartureMillis,
                       long arrivalMillis, long expectedArrivalMillis, boolean cancelled) {
        this.departureMillis = departureMillis;
        this.expectedDepartureMillis = expectedDepartureMillis;
        this.arrivalMillis = arrivalMillis;
        this.expectedArrivalMillis = expectedArrivalMillis;
        this.cancelled = cancelled;
    }

    /**
     * @param originVisit      passage à ma gare de départ (stop-monitoring), ou null
     * @param destinationVisit passage à ma gare d'arrivée (stop-monitoring), ou null
     * @param stops            parcours complet du train (estimated-timetable), ou null
     * @param direction        le sens testé — décide quelle gare est le départ
     */
    public static SegmentLeg resolve(StopVisit originVisit, StopVisit destinationVisit,
                                     List<TrainStop> stops, LineNDirection direction) {
        long[] departure = departureTimes(originVisit, stops, direction.getOriginName());
        long[] arrival = arrivalTimes(destinationVisit, stops, direction.getDestinationName());

        boolean cancelled = (originVisit != null && originVisit.isCancelled())
                || (destinationVisit != null && destinationVisit.isCancelled());

        return new SegmentLeg(departure[0], departure[1], arrival[0], arrival[1], cancelled);
    }

    /** @return {théorique, estimé} du départ de ma gare ; 0 quand l'info manque. */
    private static long[] departureTimes(StopVisit visit, List<TrainStop> stops, String stationName) {
        if (visit != null) {
            long aimed = millis(visit.aimedDeparture);
            long expected = millis(visit.expectedDeparture);
            if (aimed > 0 || expected > 0) return new long[]{aimed, expected};
        }
        TrainStop stop = findStop(stops, stationName);
        if (stop == null) return new long[]{0L, 0L};
        return new long[]{
                stop.getAimedDepartureMillis() > 0
                        ? stop.getAimedDepartureMillis() : stop.getAimedArrivalMillis(),
                stop.getExpectedDepartureMillis() > 0
                        ? stop.getExpectedDepartureMillis() : stop.getExpectedArrivalMillis()};
    }

    /** @return {théorique, estimé} de l'arrivée à ma gare ; 0 quand l'info manque. */
    private static long[] arrivalTimes(StopVisit visit, List<TrainStop> stops, String stationName) {
        if (visit != null) {
            long aimed = visit.aimedArrival != null
                    ? visit.aimedArrival.getTime() : millis(visit.aimedDeparture);
            long expected = visit.expectedArrival != null
                    ? visit.expectedArrival.getTime() : millis(visit.expectedDeparture);
            if (aimed > 0 || expected > 0) return new long[]{aimed, expected};
        }
        TrainStop stop = findStop(stops, stationName);
        if (stop == null) return new long[]{0L, 0L};
        return new long[]{
                stop.getAimedArrivalMillis() > 0
                        ? stop.getAimedArrivalMillis() : stop.getAimedDepartureMillis(),
                stop.getExpectedArrivalMillis() > 0
                        ? stop.getExpectedArrivalMillis() : stop.getExpectedDepartureMillis()};
    }

    private static long millis(Date date) {
        return date != null ? date.getTime() : 0L;
    }

    /**
     * Retrouve une de mes gares dans un parcours. Comparaison normalisée et en mode
     * {@code contains} dans les deux sens : « Villepreux » ↔ « Villepreux - Les Clayes ».
     */
    public static TrainStop findStop(List<TrainStop> stops, String stationName) {
        if (stops == null || stationName == null || stationName.isEmpty()) return null;
        String target = LineNStation.normalize(stationName);
        if (target.isEmpty()) return null;
        for (TrainStop stop : stops) {
            String candidate = LineNStation.normalize(stop.getStopName());
            if (candidate.isEmpty()) continue;
            if (candidate.equals(target)
                    || candidate.contains(target)
                    || target.contains(candidate)) {
                return stop;
            }
        }
        return null;
    }

    public boolean hasDeparture() { return bestDepartureMillis() > 0; }

    public boolean hasArrival() { return bestArrivalMillis() > 0; }

    /**
     * @return true si le train dessert mes deux gares <b>dans ce sens</b> (départ
     *         avant arrivée). Un train du sens inverse répond false ici et sera
     *         retenu par l'autre {@link LineNDirection}.
     */
    public boolean servesMySegment() {
        return hasDeparture() && hasArrival() && bestDepartureMillis() < bestArrivalMillis();
    }

    /** Heure de départ théorique, à défaut estimée. */
    public long getDepartureMillis() {
        return departureMillis > 0 ? departureMillis : expectedDepartureMillis;
    }

    /** Heure d'arrivée théorique, à défaut estimée. */
    public long getArrivalMillis() {
        return arrivalMillis > 0 ? arrivalMillis : expectedArrivalMillis;
    }

    private long bestDepartureMillis() { return getDepartureMillis(); }

    private long bestArrivalMillis() { return getArrivalMillis(); }

    public boolean isCancelled() { return cancelled; }

    /** Retard annoncé au départ de ma gare, en minutes (0 si aucun). */
    public int getDepartureDelayMinutes() {
        return delay(departureMillis, expectedDepartureMillis);
    }

    /** Retard annoncé à l'arrivée à ma gare, en minutes (0 si aucun). */
    public int getArrivalDelayMinutes() {
        return delay(arrivalMillis, expectedArrivalMillis);
    }

    /**
     * Le retard qui compte quand je suis à bord : celui de l'arrivée, à défaut
     * celui constaté au départ.
     */
    public int getDelayMinutes() {
        int arrivalDelay = getArrivalDelayMinutes();
        return arrivalDelay > 0 ? arrivalDelay : getDepartureDelayMinutes();
    }

    private static int delay(long aimed, long expected) {
        if (aimed <= 0 || expected <= 0) return 0;
        return (int) Math.max(0L, (expected - aimed) / 60_000L);
    }
}
