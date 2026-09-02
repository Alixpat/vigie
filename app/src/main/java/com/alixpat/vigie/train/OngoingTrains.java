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
import java.util.List;
import java.util.Map;

/**
 * Sélection des trains « qui me concernent à l'instant T » : ceux qui roulent
 * en ce moment entre ma gare d'origine et ma gare de destination, dans un sens
 * ou dans l'autre — typiquement le train dans lequel je suis assis.
 *
 * Deux sources sont nécessaires, car un train déjà parti disparaît du
 * {@code stop-monitoring} de la gare d'origine :
 * <ul>
 *   <li>le stop-monitoring de la gare <b>d'arrivée</b> (le train y est encore
 *       annoncé tant qu'il n'est pas arrivé) → heure d'arrivée + retard ;</li>
 *   <li>le stop-monitoring de la gare de <b>départ</b> ou, à défaut, les arrêts
 *       de l'estimated-timetable → heure de départ de ma gare.</li>
 * </ul>
 * Un train qui ne dessert pas ma gare de départ est écarté : il ne me concerne pas.
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
     * Construit les trajets en cours d'un sens de circulation.
     *
     * @param originData      visites à ma gare de départ (peut être null / incomplet)
     * @param destinationData visites à ma gare d'arrivée — source principale
     * @param stopsCache      arrêts par journeyRef (estimated-timetable), pour retrouver
     *                        l'heure de départ des trains absents d'{@code originData}
     * @param direction       sens de circulation concerné
     * @param now             instant de référence en epoch millis
     * @return les trains en circulation sur mon trajet, le dernier parti en tête
     */
    public static List<TrainSchedule> buildOngoing(Map<String, StopVisit> originData,
                                                   Map<String, StopVisit> destinationData,
                                                   Map<String, List<TrainStop>> stopsCache,
                                                   LineNDirection direction,
                                                   long now) {
        List<TrainSchedule> result = new ArrayList<>();
        if (destinationData == null || destinationData.isEmpty()) return result;

        for (Map.Entry<String, StopVisit> entry : destinationData.entrySet()) {
            String journeyRef = entry.getKey();
            StopVisit destinationVisit = entry.getValue();
            if (destinationVisit == null || destinationVisit.isCancelled()) continue;
            if (!direction.matchesDestination(destinationVisit.destination)) continue;

            long arrivalMillis = destinationVisit.bestArrivalMillis();
            if (arrivalMillis <= 0) continue;

            StopVisit originVisit = originData != null ? originData.get(journeyRef) : null;
            List<TrainStop> stops = stopsCache != null ? stopsCache.get(journeyRef) : null;

            long departureMillis = resolveDepartureMillis(originVisit, stops, direction);
            if (departureMillis <= 0) continue;   // ne dessert pas ma gare de départ

            // Le retard qui compte à bord est celui annoncé à l'arrivée ; celui du
            // départ ne sert qu'à savoir si le train a effectivement quitté ma gare.
            int departureDelay = originVisit != null
                    ? diffMinutes(originVisit.expectedDeparture, originVisit.aimedDeparture) : 0;
            int delayMinutes = computeDelayMinutes(destinationVisit, originVisit);

            String expectedDepartureTime = departureDelay > 0
                    ? DateFormats.formatHhmm(new Date(departureMillis + departureDelay * 60_000L))
                    : "";

            TrainSchedule schedule = new TrainSchedule(
                    destinationVisit.destination,
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
                            destinationVisit.trainNumber),
                    firstNonEmpty(originVisit != null ? originVisit.missionName : null,
                            destinationVisit.missionName));

            // Même filtre que pour le rafraîchissement local : pas encore parti →
            // il figure déjà dans les prochains départs ; déjà arrivé → il ne me
            // concerne plus.
            if (isOngoing(schedule, now)) result.add(schedule);
        }

        sortByDepartureDesc(result);
        return result;
    }

    /**
     * Heure de départ de ma gare : celle du stop-monitoring si le train y est
     * encore annoncé, sinon celle de l'arrêt correspondant dans le parcours.
     */
    private static long resolveDepartureMillis(StopVisit originVisit, List<TrainStop> stops,
                                               LineNDirection direction) {
        if (originVisit != null && originVisit.aimedDeparture != null) {
            return originVisit.aimedDeparture.getTime();
        }
        TrainStop originStop = findStop(stops, direction.getOriginName());
        if (originStop == null) {
            // Parcours dont les noms d'arrêts n'ont pas pu être résolus : ils
            // s'appellent "Arrêt 43111". On retombe sur l'identifiant numérique.
            originStop = findStop(stops, direction.getOriginStopId());
        }
        if (originStop == null) return 0L;
        if (originStop.getAimedDepartureMillis() > 0) return originStop.getAimedDepartureMillis();
        return originStop.getBestTimeMillis();
    }

    /**
     * Retrouve un arrêt par son nom de gare (comparaison normalisée, en mode
     * {@code contains} dans les deux sens : "Villepreux" ↔ "Villepreux - Les Clayes").
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

    /** Retard en minutes : à l'arrivée en priorité (c'est celui qui compte à bord). */
    private static int computeDelayMinutes(StopVisit destinationVisit, StopVisit originVisit) {
        int delay = diffMinutes(destinationVisit != null ? destinationVisit.expectedArrival : null,
                destinationVisit != null ? destinationVisit.aimedArrival : null);
        if (delay == 0 && originVisit != null) {
            delay = diffMinutes(originVisit.expectedDeparture, originVisit.aimedDeparture);
        }
        return delay;
    }

    private static int diffMinutes(Date expected, Date aimed) {
        if (expected == null || aimed == null) return 0;
        int minutes = (int) ((expected.getTime() - aimed.getTime()) / 60_000L);
        return Math.max(0, minutes);
    }

    private static String platformOf(StopVisit destinationVisit, StopVisit originVisit) {
        if (destinationVisit != null && destinationVisit.platform != null
                && !destinationVisit.platform.isEmpty()) {
            return destinationVisit.platform;
        }
        return originVisit != null && originVisit.platform != null ? originVisit.platform : "";
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) return a;
        return b != null ? b : "";
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
