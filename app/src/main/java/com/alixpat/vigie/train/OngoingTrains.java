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
 * Sélection des trains « qui me concernent à l'instant T » : ceux qui roulent
 * en ce moment entre ma gare d'origine et ma gare de destination, dans un sens
 * ou dans l'autre — typiquement le train dans lequel je suis assis.
 *
 * <p>La source de vérité est le <b>parcours</b> du train
 * ({@code estimated-timetable}, mémorisé et fusionné par {@link JourneyRoutes}) :
 * il porte mes deux gares, donc l'heure de départ, l'heure d'arrivée et le sens
 * de circulation (lu dans l'ordre des arrêts).</p>
 *
 * <p>Le {@code stop-monitoring} de mes deux gares vient enrichir (retard réel,
 * voie, numéro de train) et sert de secours quand le parcours est inconnu. Il ne
 * peut pas porter la sélection à lui seul : un train déjà parti disparaît de
 * celui de ma gare de départ, et n'apparaît dans celui de ma gare d'arrivée que
 * dans l'horizon publié par IDFM — s'y fier ne montre le train qu'en toute fin
 * de trajet.</p>
 *
 * Un train qui ne dessert pas mes deux gares est écarté : il ne me concerne pas.
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
     * <p>La source principale est le <b>parcours</b> de chaque train
     * ({@code estimated-timetable}, mémorisé et fusionné par {@link JourneyRoutes}) :
     * il contient à la fois ma gare de départ et ma gare d'arrivée, donc l'heure de
     * départ, l'heure d'arrivée et le sens de circulation — celui-ci se lit dans
     * l'ordre des arrêts, sans avoir à deviner le terminus.</p>
     *
     * <p>Le stop-monitoring des deux gares ne sert plus qu'à enrichir (retard réel,
     * voie, numéro de train) et de secours quand le parcours est inconnu. C'est
     * important : un train déjà parti disparaît du stop-monitoring de ma gare de
     * départ, et il n'apparaît dans celui de ma gare d'arrivée que dans l'horizon
     * publié par IDFM — s'y fier revient à ne voir le train qu'en toute fin de
     * trajet.</p>
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
            TrainSchedule schedule = buildOne(journeyRef, originData, destinationData, routes,
                    trainNumbers, missionNames, direction);
            // Même filtre que pour le rafraîchissement local : pas encore parti →
            // il figure déjà dans les prochains départs ; déjà arrivé → il ne me
            // concerne plus.
            if (schedule != null && isOngoing(schedule, now)) result.add(schedule);
        }

        sortByDepartureDesc(result);
        return result;
    }

    /** @return le trajet de bout en bout sur mon parcours, ou null s'il ne me concerne pas. */
    private static TrainSchedule buildOne(String journeyRef,
                                          Map<String, StopVisit> originData,
                                          Map<String, StopVisit> destinationData,
                                          Map<String, List<TrainStop>> routes,
                                          Map<String, String> trainNumbers,
                                          Map<String, String> missionNames,
                                          LineNDirection direction) {
        List<TrainStop> stops = routes != null ? routes.get(journeyRef) : null;
        StopVisit originVisit = originData != null ? originData.get(journeyRef) : null;
        StopVisit destinationVisit = destinationData != null ? destinationData.get(journeyRef) : null;

        if (destinationVisit != null && destinationVisit.isCancelled()) return null;

        int originIndex = indexOfStop(stops, direction.getOriginName(), direction.getOriginStopId());
        int destinationIndex = indexOfStop(stops, direction.getDestinationName(),
                direction.getDestinationStopId());
        TrainStop originStop = originIndex >= 0 ? stops.get(originIndex) : null;
        TrainStop destinationStop = destinationIndex >= 0 ? stops.get(destinationIndex) : null;

        // Le train doit desservir mes deux gares : sinon il ne me concerne pas.
        boolean servesOrigin = originStop != null || originVisit != null;
        boolean servesDestination = destinationStop != null || destinationVisit != null;
        if (!servesOrigin || !servesDestination) return null;

        // Sens de circulation : l'ordre des arrêts du parcours fait foi ; à défaut,
        // on retombe sur les mots-clés du terminus annoncé.
        if (originIndex >= 0 && destinationIndex >= 0) {
            if (originIndex >= destinationIndex) return null;
        } else if (!direction.matchesDestination(terminusOf(stops, destinationVisit))) {
            return null;
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
        if (departureMillis <= 0) return null;

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
        long realDepartureMillis = departureMillis + departureDelay * 60_000L;
        String expectedDepartureTime = departureDelay > 0
                ? DateFormats.formatHhmm(new Date(realDepartureMillis))
                : "";

        return new TrainSchedule(
                terminusOf(stops, destinationVisit),
                DateFormats.formatHhmm(new Date(departureMillis)),
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
