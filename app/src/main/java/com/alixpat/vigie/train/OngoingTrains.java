package com.alixpat.vigie.train;

import com.alixpat.vigie.model.OngoingTrain;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;
import com.alixpat.vigie.util.DateFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sélection des trains « qui me concernent à l'instant T » : ceux qui roulent
 * en ce moment entre ma gare d'origine et ma gare de destination, dans un sens
 * ou dans l'autre — typiquement le train dans lequel je suis assis.
 *
 * Un train me concerne s'il <b>dessert mes deux gares</b> — c'est
 * {@link SegmentLeg} qui le détermine, et l'ordre des deux arrêts donne le sens
 * de circulation. Le terminus annoncé n'entre pas dans la décision.
 *
 * Trois sources sont fusionnées, car un train déjà parti a disparu du
 * {@code stop-monitoring} de ma gare de départ :
 * <ul>
 *   <li>le stop-monitoring de ma gare de <b>départ</b> → heure de départ + retard ;</li>
 *   <li>celui de ma gare <b>d'arrivée</b> (le train y est encore annoncé tant
 *       qu'il n'est pas arrivé) → heure d'arrivée + retard à l'arrivée ;</li>
 *   <li>le parcours complet de l'{@code estimated-timetable} → seule source qui
 *       connaisse encore les trains déjà partis, et la plus exhaustive.</li>
 * </ul>
 */
public final class OngoingTrains {

    /** Marge après l'arrivée théorique pendant laquelle le train reste affiché. */
    public static final long ARRIVAL_GRACE_MS = 3 * 60_000L;
    /** Durée de trajet maximale retenue quand l'heure d'arrivée est inconnue. */
    public static final long MAX_TRIP_MS = 90 * 60_000L;

    private OngoingTrains() {}

    // ==================== Filtres sur les trajets déjà construits ====================

    /**
     * Heure de départ réelle, 0 si inconnue. Le retard ne décale le départ que
     * s'il est annoncé <b>au départ</b> (heure de départ estimée renseignée) :
     * un retard pris en route ne change pas l'heure à laquelle le train est parti.
     */
    public static long effectiveDepartureMillis(TrainSchedule schedule) {
        if (schedule == null) return 0L;
        long departure = schedule.getAimedDepartureMillis();
        if (departure <= 0) return 0L;
        String expected = schedule.getExpectedDepartureTime();
        if (schedule.getDelayMinutes() > 0 && expected != null && !expected.isEmpty()) {
            departure += schedule.getDelayMinutes() * 60_000L;
        }
        return departure;
    }

    /** Heure d'arrivée réelle (théorique + retard annoncé), 0 si inconnue. */
    public static long effectiveArrivalMillis(TrainSchedule schedule) {
        if (schedule == null) return 0L;
        long arrival = schedule.getArrivalMillis();
        if (arrival <= 0) return 0L;
        if (schedule.getDelayMinutes() > 0) arrival += schedule.getDelayMinutes() * 60_000L;
        return arrival;
    }

    /**
     * @return true si le train est parti de ma gare et n'est pas encore arrivé à
     *         destination (donc : je peux être dedans). Un train supprimé n'est
     *         jamais « en cours ».
     */
    public static boolean isOngoing(TrainSchedule schedule, long now) {
        if (schedule == null || schedule.isCancelled()) return false;
        long departure = effectiveDepartureMillis(schedule);
        if (departure <= 0 || departure > now) return false;
        long arrival = effectiveArrivalMillis(schedule);
        if (arrival > 0) return now < arrival + ARRIVAL_GRACE_MS;
        return now - departure <= MAX_TRIP_MS;
    }

    /** @return true si le train n'a pas encore quitté ma gare de départ. */
    public static boolean isUpcoming(TrainSchedule schedule, long now) {
        long departure = effectiveDepartureMillis(schedule);
        return departure > 0 && departure > now;
    }

    /** Trajets à venir uniquement, ordre chronologique préservé. */
    public static List<TrainSchedule> selectUpcoming(List<TrainSchedule> schedules, long now) {
        List<TrainSchedule> result = new ArrayList<>();
        if (schedules == null) return result;
        for (TrainSchedule schedule : schedules) {
            if (isUpcoming(schedule, now)) result.add(schedule);
        }
        return result;
    }

    /** Trajets en cours uniquement, le dernier parti en tête. */
    public static List<TrainSchedule> selectOngoing(List<TrainSchedule> schedules, long now) {
        List<TrainSchedule> result = new ArrayList<>();
        if (schedules == null) return result;
        for (TrainSchedule schedule : schedules) {
            if (isOngoing(schedule, now)) result.add(schedule);
        }
        sortByDepartureDesc(result);
        return result;
    }

    private static void sortByDepartureDesc(List<TrainSchedule> schedules) {
        Collections.sort(schedules, (a, b) ->
                Long.compare(effectiveDepartureMillis(b), effectiveDepartureMillis(a)));
    }

    // ==================== Construction depuis les réponses SIRI ====================

    /**
     * Les sources disponibles pour reconstituer les trains d'un sens de circulation.
     * Toutes sont facultatives : plus il y en a, plus la liste est complète.
     */
    public static final class Sources {
        /** Passages à ma gare de départ (stop-monitoring). */
        public Map<String, StopVisit> originVisits;
        /** Passages à ma gare d'arrivée (stop-monitoring). */
        public Map<String, StopVisit> destinationVisits;
        /** Parcours complets par journeyRef (estimated-timetable). */
        public Map<String, List<TrainStop>> stops;
        /** Numéros de train par journeyRef. */
        public Map<String, String> trainNumbers;
        /** Noms de mission par journeyRef. */
        public Map<String, String> missionNames;

        public Sources(Map<String, StopVisit> originVisits,
                       Map<String, StopVisit> destinationVisits,
                       Map<String, List<TrainStop>> stops,
                       Map<String, String> trainNumbers,
                       Map<String, String> missionNames) {
            this.originVisits = originVisits;
            this.destinationVisits = destinationVisits;
            this.stops = stops;
            this.trainNumbers = trainNumbers;
            this.missionNames = missionNames;
        }

        public Sources(Map<String, StopVisit> originVisits,
                       Map<String, StopVisit> destinationVisits,
                       Map<String, List<TrainStop>> stops) {
            this(originVisits, destinationVisits, stops, null, null);
        }

        /** Tous les journeyRef connus, toutes sources confondues. */
        Set<String> journeyRefs() {
            Set<String> refs = new LinkedHashSet<>();
            if (originVisits != null) refs.addAll(originVisits.keySet());
            if (destinationVisits != null) refs.addAll(destinationVisits.keySet());
            if (stops != null) refs.addAll(stops.keySet());
            return refs;
        }
    }

    /**
     * Construit les trains d'un sens qui roulent en ce moment sur mon tronçon.
     *
     * Le critère est « le train dessert mes deux gares, la gare de départ en
     * premier » — pas le terminus annoncé. Un train déjà parti ayant disparu du
     * stop-monitoring de ma gare de départ, son heure de départ est reprise du
     * parcours (estimated-timetable), seule source à le connaître encore.
     *
     * @return les trains en circulation sur mon trajet, le dernier parti en tête
     */
    public static List<TrainSchedule> buildOngoing(Sources sources, LineNDirection direction,
                                                   long now) {
        List<TrainSchedule> result = new ArrayList<>();
        if (sources == null) return result;

        for (String journeyRef : sources.journeyRefs()) {
            TrainSchedule schedule = toSchedule(journeyRef, sources, direction);
            if (schedule != null && isOngoing(schedule, now)) result.add(schedule);
        }

        sortByDepartureDesc(result);
        return result;
    }

    /**
     * @return le trajet sur mon tronçon pour ce train, ou null s'il ne dessert pas
     *         mes deux gares dans ce sens (ou s'il est supprimé).
     */
    private static TrainSchedule toSchedule(String journeyRef, Sources sources,
                                            LineNDirection direction) {
        StopVisit originVisit = get(sources.originVisits, journeyRef);
        StopVisit destinationVisit = get(sources.destinationVisits, journeyRef);
        List<TrainStop> stops = get(sources.stops, journeyRef);

        SegmentLeg leg = SegmentLeg.resolve(originVisit, destinationVisit, stops, direction);
        if (leg.isCancelled() || !leg.servesMySegment()) return null;

        long departureMillis = leg.getDepartureMillis();
        long arrivalMillis = leg.getArrivalMillis();
        int delayMinutes = leg.getDelayMinutes();

        String expectedDepartureTime = leg.getDepartureDelayMinutes() > 0
                ? DateFormats.formatHhmm(new Date(
                        departureMillis + leg.getDepartureDelayMinutes() * 60_000L))
                : "";

        return new TrainSchedule(
                terminusOf(originVisit, destinationVisit, stops, direction),
                DateFormats.formatHhmm(new Date(departureMillis)),
                expectedDepartureTime,
                DateFormats.formatHhmm(new Date(arrivalMillis)),
                delayMinutes > 0 ? "delayed" : "onTime",
                platformOf(destinationVisit, originVisit),
                delayMinutes,
                journeyRef,
                departureMillis,
                arrivalMillis,
                direction.getOriginName(),
                firstNonEmpty(originVisit != null ? originVisit.trainNumber : null,
                        destinationVisit != null ? destinationVisit.trainNumber : null,
                        get(sources.trainNumbers, journeyRef)),
                firstNonEmpty(originVisit != null ? originVisit.missionName : null,
                        destinationVisit != null ? destinationVisit.missionName : null,
                        get(sources.missionNames, journeyRef)));
    }

    /** Terminus annoncé du train ; à défaut le dernier arrêt de son parcours. */
    private static String terminusOf(StopVisit originVisit, StopVisit destinationVisit,
                                     List<TrainStop> stops, LineNDirection direction) {
        String terminus = firstNonEmpty(
                originVisit != null ? originVisit.destination : null,
                destinationVisit != null ? destinationVisit.destination : null, null);
        if (!terminus.isEmpty()) return terminus;
        if (stops != null && !stops.isEmpty()) {
            long latest = Long.MIN_VALUE;
            String name = "";
            for (TrainStop stop : stops) {
                long time = stop.getBestArrivalMillis();
                if (time > latest) {
                    latest = time;
                    name = stop.getStopName();
                }
            }
            if (name != null && !name.isEmpty()) return name;
        }
        return direction.getDestinationName();
    }

    private static <T> T get(Map<String, T> map, String key) {
        return map != null ? map.get(key) : null;
    }

    private static String platformOf(StopVisit destinationVisit, StopVisit originVisit) {
        if (destinationVisit != null && destinationVisit.platform != null
                && !destinationVisit.platform.isEmpty()) {
            return destinationVisit.platform;
        }
        return originVisit != null && originVisit.platform != null ? originVisit.platform : "";
    }

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && !a.isEmpty()) return a;
        if (b != null && !b.isEmpty()) return b;
        return c != null ? c : "";
    }

    // ==================== Mise en forme pour l'affichage ====================

    /**
     * Habille un trajet en cours des libellés prêts à afficher (position à
     * l'instant T, prochain arrêt, arrivée estimée).
     *
     * @param stops arrêts du trajet (estimated-timetable) ; si absents, on
     *              retombe sur l'estimation basée sur les seuls horaires.
     */
    public static OngoingTrain describe(TrainSchedule schedule, LineNDirection direction,
                                        List<TrainStop> stops, long now) {
        TrainPosition position = TrainPosition.compute(stops, now);

        String positionLabel = position.isKnown()
                ? position.describe()
                : schedule.estimatePosition();

        int progress = position.getProgressPercent();
        if (progress < 0) progress = progressFromSchedule(schedule, now);

        StringBuilder nextStop = new StringBuilder();
        if (!position.getNextStopName().isEmpty()) {
            nextStop.append("Prochain arrêt : ").append(position.getNextStopName());
            if (position.getNextStopMillis() > 0) {
                nextStop.append(" · ")
                        .append(DateFormats.formatHhmm(new Date(position.getNextStopMillis())));
            }
        }

        StringBuilder eta = new StringBuilder();
        long arrival = effectiveArrivalMillis(schedule);
        if (arrival > 0) {
            eta.append("Arrivée ").append(direction.getDestinationName())
                    .append(" · ").append(DateFormats.formatHhmm(new Date(arrival)));
            long remaining = arrival - now;
            if (remaining > 30_000L) {
                eta.append(" (dans ").append(Math.max(1L, remaining / 60_000L)).append(" min)");
            } else {
                eta.append(" (imminente)");
            }
        }

        StringBuilder trainInfo = new StringBuilder();
        if (schedule.getTrainNumber() != null && !schedule.getTrainNumber().isEmpty()) {
            trainInfo.append("Train ").append(schedule.getTrainNumber());
        }
        if (schedule.getMissionName() != null && !schedule.getMissionName().isEmpty()) {
            if (trainInfo.length() > 0) trainInfo.append(" • ");
            trainInfo.append(schedule.getMissionName());
        }

        String directionLabel = direction.getOriginName() + " → " + direction.getDestinationName();

        return new OngoingTrain(schedule, directionLabel, positionLabel, progress,
                nextStop.toString(), eta.toString(), trainInfo.toString());
    }

    /** Avancement calculé sur les seuls horaires départ/arrivée ; -1 si inconnu. */
    private static int progressFromSchedule(TrainSchedule schedule, long now) {
        long departure = effectiveDepartureMillis(schedule);
        long arrival = effectiveArrivalMillis(schedule);
        if (departure <= 0 || arrival <= departure) return -1;
        long percent = ((now - departure) * 100L) / (arrival - departure);
        return (int) Math.max(0L, Math.min(100L, percent));
    }
}
