package com.alixpat.vigie.train;

import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.OngoingTrain;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;
import com.alixpat.vigie.util.DateFormats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Inventaire des trains présents sur mon segment à l'instant T : <b>tous</b> ceux
 * qui se trouvent en ce moment entre ma gare d'origine et ma gare de destination,
 * dans un sens ou dans l'autre — que je sois dedans ou non.
 *
 * <p>Le critère est une <b>position</b>, pas un horaire de départ : « où est ce
 * train maintenant ? ». C'est la seule question à laquelle les données d'IDFM
 * répondent de bout en bout. L'{@code estimated-timetable} ne décrit que les
 * arrêts restants d'un train, donc ma gare de départ disparaît de son parcours
 * dès qu'il l'a franchie ; sélectionner sur l'heure de départ fait donc
 * disparaître le train au moment précis où il roule sur mon segment. Le prochain
 * arrêt, lui, est toujours publié — c'est l'ancre de {@link #locate}, épaulée par
 * la géographie de la ligne ({@link LineSegment}).</p>
 *
 * <p>Le {@code stop-monitoring} de mes deux gares vient enrichir (retard réel,
 * voie, numéro de train) et sert de secours quand le parcours est inconnu. Il ne
 * peut pas porter la sélection : un train déjà parti disparaît de celui de ma
 * gare de départ, et n'apparaît dans celui de ma gare d'arrivée que dans
 * l'horizon publié par IDFM — s'y fier ne montre le train qu'en fin de trajet.</p>
 *
 * <p>Compromis assumé : quand un train a franchi ma gare de départ sans que l'app
 * ait pu observer ce passage, rien ne prouve plus qu'il la desservait (un
 * semi-direct a pu la sauter). Il est alors affiché sans heure de départ plutôt
 * qu'écarté — un train manquant est plus gênant qu'un train en trop.</p>
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
        // Heure réelle renseignée : elle fait foi, sans reconstruction approximative.
        if (schedule.getExpectedDepartureMillis() > 0) return schedule.getExpectedDepartureMillis();
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

    // ==================== Mémoire des passages à ma gare de départ ====================

    /** Durée pendant laquelle on se souvient d'un train vu à ma gare de départ. */
    public static final long ORIGIN_MEMORY_MS = 3 * 60 * 60_000L;

    /**
     * Entretient le souvenir des trains vus à ma gare de départ.
     *
     * Un train qui vient de partir disparaît du {@code stop-monitoring} de cette
     * gare : sans mémoire, son heure de départ n'est plus connue et il ne peut
     * plus être reconnu comme « en circulation sur mon trajet ». On conserve donc
     * les passages observés lors des rafraîchissements précédents, purgés au-delà
     * de {@link #ORIGIN_MEMORY_MS} après leur heure de départ.
     *
     * @param memory carte accumulée entre deux rafraîchissements (modifiée sur place)
     * @param fresh  passages tout juste ramenés par l'API (peut être null)
     * @param now    instant de référence en epoch millis
     * @return {@code memory}, pour chaîner
     */
    public static Map<String, StopVisit> rememberOriginVisits(Map<String, StopVisit> memory,
                                                              Map<String, StopVisit> fresh,
                                                              long now) {
        if (memory == null) return null;
        if (fresh != null) memory.putAll(fresh);
        Iterator<Map.Entry<String, StopVisit>> it = memory.entrySet().iterator();
        while (it.hasNext()) {
            StopVisit visit = it.next().getValue();
            if (visit == null || visit.aimedDeparture == null) {
                it.remove();
                continue;
            }
            if (now - visit.aimedDeparture.getTime() > ORIGIN_MEMORY_MS) it.remove();
        }
        return memory;
    }

    // ==================== Construction depuis les réponses SIRI ====================

    /**
     * Construit les trajets en cours d'un sens de circulation, sans métadonnées
     * de train (numéro, mission) autres que celles portées par le stop-monitoring.
     */
    public static List<TrainSchedule> buildOngoing(Map<String, StopVisit> originData,
                                                   Map<String, StopVisit> destinationData,
                                                   Map<String, List<TrainStop>> routes,
                                                   LineNDirection direction,
                                                   long now) {
        return buildOngoing(originData, destinationData, routes, null, null, direction, now);
    }

    /**
     * Construit les trajets en cours d'un sens de circulation.
     *
     * <p>Le critère est <b>positionnel</b> : tout train qui se trouve entre mes deux
     * gares à l'instant demandé, que je sois dedans ou non. {@link #locate} tranche
     * à partir du parcours ({@code estimated-timetable}, mémorisé et fusionné par
     * {@link JourneyRoutes}) et, quand ma gare de départ n'y figure plus, de la
     * géographie de la ligne ({@link LineSegment}).</p>
     *
     * <p>Un train dont le passage à ma gare de départ n'est plus exposé est affiché
     * sans heure de départ plutôt qu'écarté : le manquer serait pire que l'afficher
     * incomplet. Le stop-monitoring des deux gares ne sert qu'à enrichir (retard
     * réel, voie, numéro de train) et de secours quand le parcours est inconnu.</p>
     *
     * @param originData      visites à ma gare de départ (peut être null / incomplet)
     * @param destinationData visites à ma gare d'arrivée (peut être null / incomplet)
     * @param routes          parcours par journeyRef (estimated-timetable fusionné)
     * @param trainNumbers    numéros de train par journeyRef, pour les trajets connus
     *                        du seul estimated-timetable (peut être null)
     * @param missionNames    noms de mission par journeyRef (peut être null)
     * @param direction       sens de circulation concerné
     * @param now             instant de référence en epoch millis
     * @return les trains en circulation sur mon trajet, le dernier parti en tête
     */
    public static List<TrainSchedule> buildOngoing(Map<String, StopVisit> originData,
                                                   Map<String, StopVisit> destinationData,
                                                   Map<String, List<TrainStop>> routes,
                                                   Map<String, String> trainNumbers,
                                                   Map<String, String> missionNames,
                                                   LineNDirection direction,
                                                   long now) {
        List<TrainSchedule> result = new ArrayList<>();

        Set<String> journeyRefs = new LinkedHashSet<>();
        if (routes != null) journeyRefs.addAll(routes.keySet());
        if (destinationData != null) journeyRefs.addAll(destinationData.keySet());

        for (String journeyRef : journeyRefs) {
            List<TrainStop> stops = routes != null ? routes.get(journeyRef) : null;
            Placement placement = locate(stops, direction, now);
            if (placement == Placement.OFF_SEGMENT) continue;

            TrainSchedule schedule = buildOne(journeyRef, originData, destinationData, stops,
                    trainNumbers, missionNames, direction, placement);
            if (schedule == null) continue;
            // Parcours inconnu : faute de position réelle, on tranche sur les horaires
            // (pas encore parti → il figure déjà dans les prochains départs).
            if (placement == Placement.UNKNOWN && !isOngoing(schedule, now)) continue;
            result.add(schedule);
        }

        sortByArrival(result);
        return result;
    }

    /** Où en est un train vis-à-vis de mon segment, d'après son parcours. */
    public enum Placement {
        /** Physiquement entre mes deux gares à l'instant demandé. */
        ON_SEGMENT,
        /** Ailleurs : pas encore à ma gare de départ, déjà au-delà, ou autre sens. */
        OFF_SEGMENT,
        /** Parcours absent ou trop incomplet pour conclure. */
        UNKNOWN
    }

    /**
     * Situe un train sur mon segment à l'instant {@code now}, à partir de son
     * parcours et — quand ma gare de départ n'y figure plus — de la géographie de
     * la ligne.
     *
     * <p>La question posée est « où est ce train maintenant ? », pas « à quelle
     * heure est-il parti de chez moi ? ». C'est la seule formulation qui tienne :
     * l'{@code estimated-timetable} ne décrit que les arrêts restants, donc ma
     * gare de départ disparaît du parcours dès qu'elle est franchie. Raisonner sur
     * l'heure de départ fait disparaître le train à ce moment précis, alors qu'il
     * est justement sur mon segment.</p>
     *
     * <p>L'ancre est le <b>prochain arrêt</b> — le premier que le train n'a pas
     * encore quitté —, toujours présent dans la réponse puisqu'il est à venir.</p>
     */
    public static Placement locate(List<TrainStop> stops, LineNDirection direction, long now) {
        if (stops == null || stops.isEmpty()) return Placement.UNKNOWN;

        int destinationIndex = indexOfStop(stops, direction.getDestinationName(),
                direction.getDestinationStopId());
        // Ma gare d'arrivée absente du parcours connu : soit le train n'y va pas,
        // soit le parcours est incomplet — on ne tranche pas ici.
        if (destinationIndex < 0) return Placement.UNKNOWN;

        int pendingIndex = indexOfPendingStop(stops, now);
        if (pendingIndex < 0) return Placement.OFF_SEGMENT;              // parcours terminé
        if (pendingIndex > destinationIndex) return Placement.OFF_SEGMENT; // ma gare déjà dépassée

        int originIndex = indexOfStop(stops, direction.getOriginName(), direction.getOriginStopId());
        if (originIndex >= 0) {
            // Ma gare de départ est encore décrite : tout se lit dans le parcours.
            if (originIndex >= destinationIndex) return Placement.OFF_SEGMENT;   // sens inverse
            return pendingIndex > originIndex ? Placement.ON_SEGMENT : Placement.OFF_SEGMENT;
        }

        // Ma gare de départ n'est plus décrite (franchie) ou ne l'a jamais été : on
        // situe le prochain arrêt sur le tronçon lui-même. Au-delà de l'index 0
        // (ma gare de départ), le train est entre mes deux gares.
        int corridorIndex = direction.getSegment().indexOf(stops.get(pendingIndex).getStopName());
        if (corridorIndex < 0) return Placement.OFF_SEGMENT;
        return corridorIndex >= 1 ? Placement.ON_SEGMENT : Placement.OFF_SEGMENT;
    }

    /**
     * @return l'index du premier arrêt que le train n'a pas encore quitté (il y
     *         roule ou y est à quai), ou -1 si le parcours est terminé
     */
    private static int indexOfPendingStop(List<TrainStop> stops, long now) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).getBestTimeMillis() > now) return i;
        }
        return -1;
    }

    /** Le prochain à arriver chez moi en tête : c'est l'ordre utile pour choisir un train. */
    private static void sortByArrival(List<TrainSchedule> schedules) {
        Collections.sort(schedules, (a, b) ->
                Long.compare(effectiveArrivalMillis(a), effectiveArrivalMillis(b)));
    }

    /** @return le trajet de bout en bout sur mon parcours, ou null s'il ne me concerne pas. */
    private static TrainSchedule buildOne(String journeyRef,
                                          Map<String, StopVisit> originData,
                                          Map<String, StopVisit> destinationData,
                                          List<TrainStop> stops,
                                          Map<String, String> trainNumbers,
                                          Map<String, String> missionNames,
                                          LineNDirection direction,
                                          Placement placement) {
        StopVisit originVisit = originData != null ? originData.get(journeyRef) : null;
        StopVisit destinationVisit = destinationData != null ? destinationData.get(journeyRef) : null;

        if (destinationVisit != null && destinationVisit.isCancelled()) return null;

        int originIndex = indexOfStop(stops, direction.getOriginName(), direction.getOriginStopId());
        int destinationIndex = indexOfStop(stops, direction.getDestinationName(),
                direction.getDestinationStopId());
        TrainStop originStop = originIndex >= 0 ? stops.get(originIndex) : null;
        TrainStop destinationStop = destinationIndex >= 0 ? stops.get(destinationIndex) : null;

        if (placement == Placement.UNKNOWN) {
            // Sans position réelle, on exige de voir le train à mes deux gares et on
            // lit le sens dans les mots-clés du terminus annoncé.
            boolean servesOrigin = originStop != null || originVisit != null;
            boolean servesDestination = destinationStop != null || destinationVisit != null;
            if (!servesOrigin || !servesDestination) return null;
            if (originIndex >= 0 && destinationIndex >= 0) {
                if (originIndex >= destinationIndex) return null;
            } else if (!direction.matchesDestination(terminusOf(stops, destinationVisit))) {
                return null;
            }
        }

        long departureMillis = 0L;
        int departureDelay = 0;
        if (originVisit != null && originVisit.aimedDeparture != null) {
            departureMillis = originVisit.aimedDeparture.getTime();
            departureDelay = diffMinutes(originVisit.expectedDeparture, originVisit.aimedDeparture);
        } else if (originStop != null) {
            departureMillis = originStop.getAimedDepartureMillis() > 0
                    ? originStop.getAimedDepartureMillis() : originStop.getBestTimeMillis();
            departureDelay = diffMinutes(originStop.getExpectedDepartureMillis(),
                    originStop.getAimedDepartureMillis());
        }
        // Un train déjà au-delà de ma gare de départ n'expose plus son heure de
        // passage : on l'affiche quand même (il est sur mon segment), départ vide.
        if (departureMillis <= 0 && placement != Placement.ON_SEGMENT) return null;

        long arrivalMillis = 0L;
        int arrivalDelay = 0;
        if (destinationVisit != null && destinationVisit.bestArrivalMillis() > 0) {
            arrivalMillis = destinationVisit.bestArrivalMillis();
            arrivalDelay = diffMinutes(destinationVisit.expectedArrival, destinationVisit.aimedArrival);
        } else if (destinationStop != null) {
            arrivalMillis = destinationStop.getAimedArrivalMillis() > 0
                    ? destinationStop.getAimedArrivalMillis() : destinationStop.getBestArrivalMillis();
            arrivalDelay = diffMinutes(destinationStop.getExpectedArrivalMillis(),
                    destinationStop.getAimedArrivalMillis());
        }
        if (arrivalMillis <= 0) return null;

        // Le retard qui compte à bord est celui annoncé à l'arrivée ; celui du
        // départ ne sert qu'à savoir si le train a effectivement quitté ma gare.
        int delayMinutes = arrivalDelay > 0 ? arrivalDelay : departureDelay;

        // Le départ n'est décalé que par le retard annoncé **au départ** : un retard
        // pris en route ne change pas l'heure à laquelle le train a quitté ma gare.
        long realDepartureMillis = departureMillis > 0
                ? departureMillis + departureDelay * 60_000L : 0L;
        String expectedDepartureTime = departureDelay > 0
                ? DateFormats.formatHhmm(new Date(realDepartureMillis))
                : "";

        return new TrainSchedule(
                terminusOf(stops, destinationVisit),
                departureMillis > 0 ? DateFormats.formatHhmm(new Date(departureMillis)) : "",
                expectedDepartureTime,
                DateFormats.formatHhmm(new Date(arrivalMillis)),
                delayMinutes > 0 ? "delayed" : "onTime",
                platformOf(destinationVisit, originVisit, originStop),
                delayMinutes,
                journeyRef,
                departureMillis,
                realDepartureMillis,
                arrivalMillis,
                direction.getOriginName(),
                firstNonEmpty(originVisit != null ? originVisit.trainNumber : null,
                        destinationVisit != null ? destinationVisit.trainNumber : null,
                        trainNumbers != null ? trainNumbers.get(journeyRef) : null),
                firstNonEmpty(originVisit != null ? originVisit.missionName : null,
                        destinationVisit != null ? destinationVisit.missionName : null,
                        missionNames != null ? missionNames.get(journeyRef) : null));
    }

    /** Terminus annoncé du train : celui du stop-monitoring, sinon le dernier arrêt du parcours. */
    private static String terminusOf(List<TrainStop> stops, StopVisit destinationVisit) {
        if (destinationVisit != null && destinationVisit.destination != null
                && !destinationVisit.destination.isEmpty()) {
            return destinationVisit.destination;
        }
        if (stops != null && !stops.isEmpty()) {
            return stops.get(stops.size() - 1).getStopName();
        }
        return "";
    }

    /**
     * Retrouve un arrêt dans un parcours, par nom de gare puis, à défaut, par
     * identifiant numérique (l'estimated-timetable IDFM ne renvoie pas toujours
     * StopPointName : les arrêts s'appellent alors "Arrêt 43111").
     *
     * @return l'index de l'arrêt dans {@code stops}, ou -1
     */
    public static int indexOfStop(List<TrainStop> stops, String stationName, String stationId) {
        int index = indexMatching(stops, stationName);
        return index >= 0 ? index : indexMatching(stops, stationId);
    }

    private static int indexMatching(List<TrainStop> stops, String stationName) {
        if (stops == null || stationName == null || stationName.isEmpty()) return -1;
        String target = LineNStation.normalize(stationName);
        if (target.isEmpty()) return -1;
        for (int i = 0; i < stops.size(); i++) {
            String candidate = LineNStation.normalize(stops.get(i).getStopName());
            if (candidate.isEmpty()) continue;
            if (candidate.equals(target)
                    || candidate.contains(target)
                    || target.contains(candidate)) {
                return i;
            }
        }
        return -1;
    }

    private static int diffMinutes(Date expected, Date aimed) {
        if (expected == null || aimed == null) return 0;
        return diffMinutes(expected.getTime(), aimed.getTime());
    }

    private static int diffMinutes(long expectedMillis, long aimedMillis) {
        if (expectedMillis <= 0 || aimedMillis <= 0) return 0;
        return Math.max(0, (int) ((expectedMillis - aimedMillis) / 60_000L));
    }

    private static String platformOf(StopVisit destinationVisit, StopVisit originVisit,
                                     TrainStop originStop) {
        if (destinationVisit != null && destinationVisit.platform != null
                && !destinationVisit.platform.isEmpty()) {
            return destinationVisit.platform;
        }
        if (originVisit != null && originVisit.platform != null && !originVisit.platform.isEmpty()) {
            return originVisit.platform;
        }
        return originStop != null && originStop.getPlatformName() != null
                ? originStop.getPlatformName() : "";
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.isEmpty()) return value;
        }
        return "";
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
