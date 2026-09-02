package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.OngoingTrain;
import com.alixpat.vigie.model.TrainSchedule;
import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OngoingTrainsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    // ==================== helpers ====================

    private static TrainSchedule schedule(String journeyRef, long departureOffsetMin,
                                          long arrivalOffsetMin, int delayMinutes,
                                          String status) {
        long departure = T0 + departureOffsetMin * MIN;
        long arrival = arrivalOffsetMin == Long.MIN_VALUE ? 0 : T0 + arrivalOffsetMin * MIN;
        // Une heure de départ estimée n'est renseignée que si le retard est annoncé
        // au départ (cf. OngoingTrains.effectiveDepartureMillis).
        String expectedDeparture = delayMinutes > 0 ? "08:10" : "";
        return new TrainSchedule("Villepreux", "08:00", expectedDeparture, "08:30", status, "2",
                delayMinutes, journeyRef, departure, arrival, "Clamart", "135642", "MOPI");
    }

    private static StopVisit visit(String journeyRef, String destination,
                                   Long departureOffsetMin, Long arrivalOffsetMin,
                                   Long expectedArrivalOffsetMin) {
        StopVisit visit = new StopVisit();
        visit.journeyRef = journeyRef;
        visit.destination = destination;
        visit.departureStatus = "onTime";
        visit.platform = "2";
        visit.trainNumber = "135642";
        visit.missionName = "MOPI";
        if (departureOffsetMin != null) visit.aimedDeparture = new Date(T0 + departureOffsetMin * MIN);
        if (arrivalOffsetMin != null) visit.aimedArrival = new Date(T0 + arrivalOffsetMin * MIN);
        if (expectedArrivalOffsetMin != null) {
            visit.expectedArrival = new Date(T0 + expectedArrivalOffsetMin * MIN);
        }
        return visit;
    }

    private static Map<String, StopVisit> map(StopVisit... visits) {
        Map<String, StopVisit> result = new HashMap<>();
        for (StopVisit visit : visits) result.put(visit.journeyRef, visit);
        return result;
    }

    private static List<TrainStop> stops(String... namesAndOffsets) {
        List<TrainStop> stops = new ArrayList<>();
        for (int i = 0; i < namesAndOffsets.length; i += 2) {
            long time = T0 + Long.parseLong(namesAndOffsets[i + 1]) * MIN;
            stops.add(new TrainStop(namesAndOffsets[i], time, time, time, time, "",
                    i == 0, i + 2 >= namesAndOffsets.length));
        }
        return stops;
    }

    private static Map<String, List<TrainStop>> routes(String journeyRef, List<TrainStop> stops) {
        Map<String, List<TrainStop>> cache = new HashMap<>();
        cache.put(journeyRef, stops);
        return cache;
    }

    // ==================== isOngoing / isUpcoming ====================

    @Test
    public void trainIsOngoingBetweenDepartureAndArrival() {
        TrainSchedule train = schedule("J1", 0, 30, 0, "onTime");

        assertFalse(OngoingTrains.isOngoing(train, T0 - MIN));
        assertTrue(OngoingTrains.isOngoing(train, T0 + 5 * MIN));
        assertTrue(OngoingTrains.isOngoing(train, T0 + 29 * MIN));
    }

    @Test
    public void trainStaysVisibleShortlyAfterArrivalThenDisappears() {
        TrainSchedule train = schedule("J1", 0, 30, 0, "onTime");

        assertTrue(OngoingTrains.isOngoing(train, T0 + 31 * MIN));
        assertFalse(OngoingTrains.isOngoing(train,
                T0 + 30 * MIN + OngoingTrains.ARRIVAL_GRACE_MS + 1));
    }

    @Test
    public void delayShiftsTheWholeWindow() {
        TrainSchedule train = schedule("J1", 0, 30, 10, "delayed");

        // Départ théorique passé mais retard de 10 min : le train est encore à quai.
        assertFalse(OngoingTrains.isOngoing(train, T0 + 5 * MIN));
        assertTrue(OngoingTrains.isUpcoming(train, T0 + 5 * MIN));

        assertTrue(OngoingTrains.isOngoing(train, T0 + 15 * MIN));
        assertTrue(OngoingTrains.isOngoing(train, T0 + 39 * MIN));
    }

    @Test
    public void delayTakenEnRouteDoesNotShiftDeparture() {
        // Retard constaté à l'arrivée mais pas au départ : le train est bien parti
        // à l'heure, il reste donc "en cours" dès son départ théorique.
        TrainSchedule train = new TrainSchedule("Villepreux", "08:00", "", "08:30", "delayed",
                "2", 7, "J1", T0, T0 + 30 * MIN, "Clamart", "135642", "MOPI");

        assertTrue(OngoingTrains.isOngoing(train, T0 + MIN));
        assertFalse(OngoingTrains.isUpcoming(train, T0 + MIN));
    }

    @Test
    public void cancelledTrainIsNeverOngoing() {
        TrainSchedule train = schedule("J1", 0, 30, 0, "cancelled");
        assertFalse(OngoingTrains.isOngoing(train, T0 + 5 * MIN));
    }

    @Test
    public void withoutArrivalTimeTheTrainExpiresAfterMaxTrip() {
        TrainSchedule train = schedule("J1", 0, Long.MIN_VALUE, 0, "onTime");

        assertTrue(OngoingTrains.isOngoing(train, T0 + 20 * MIN));
        assertFalse(OngoingTrains.isOngoing(train, T0 + OngoingTrains.MAX_TRIP_MS + MIN));
    }

    @Test
    public void selectSplitsUpcomingFromOngoing() {
        TrainSchedule departed = schedule("J1", -10, 20, 0, "onTime");
        TrainSchedule upcoming = schedule("J2", 15, 45, 0, "onTime");
        List<TrainSchedule> all = Arrays.asList(departed, upcoming);

        List<TrainSchedule> ongoing = OngoingTrains.selectOngoing(all, T0);
        List<TrainSchedule> next = OngoingTrains.selectUpcoming(all, T0);

        assertEquals(1, ongoing.size());
        assertEquals("J1", ongoing.get(0).getJourneyRef());
        assertEquals(1, next.size());
        assertEquals("J2", next.get(0).getJourneyRef());
    }

    @Test
    public void ongoingTrainsAreSortedLastDepartedFirst() {
        List<TrainSchedule> all = Arrays.asList(
                schedule("J1", -40, 10, 0, "onTime"),
                schedule("J2", -5, 40, 0, "onTime"),
                schedule("J3", -20, 25, 0, "onTime"));

        List<TrainSchedule> ongoing = OngoingTrains.selectOngoing(all, T0);

        assertEquals(Arrays.asList("J2", "J3", "J1"), Arrays.asList(
                ongoing.get(0).getJourneyRef(),
                ongoing.get(1).getJourneyRef(),
                ongoing.get(2).getJourneyRef()));
    }

    // ==================== buildOngoing ====================

    @Test
    public void buildsOngoingTrainMissingFromOriginStopMonitoring() {
        // Le train est parti de Clamart : il n'apparaît plus que dans les arrivées
        // à Villepreux. Son heure de départ vient du parcours (estimated-timetable).
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Clamart", "-5", "Versailles Chantiers", "10",
                "Villepreux - Les Clayes", "25"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), villepreux, stopsCache, LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        TrainSchedule train = ongoing.get(0);
        assertEquals("J1", train.getJourneyRef());
        assertEquals(T0 - 5 * MIN, train.getAimedDepartureMillis());
        assertEquals(T0 + 25 * MIN, train.getArrivalMillis());
        assertEquals("Clamart", train.getOriginStation());
        assertTrue(OngoingTrains.isOngoing(train, T0));
    }

    @Test
    public void prefersOriginStopMonitoringForDepartureTime() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -8L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                clamart, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(T0 - 8 * MIN, ongoing.get(0).getAimedDepartureMillis());
    }

    @Test
    public void ignoresTrainsRunningInTheOtherDirection() {
        Map<String, StopVisit> villepreux = map(visit("J1", "Paris Montparnasse", -5L, 25L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresTrainsThatDoNotServeMyDepartureStation() {
        // Terminus dans le bon sens, mais aucune trace d'un passage à Clamart.
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Paris Montparnasse", "-20", "Versailles Chantiers", "10",
                "Villepreux - Les Clayes", "25"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), villepreux, stopsCache, LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void aDelayTakenEnRouteDoesNotHideATrainThatHasAlreadyLeft() {
        // Parti à l'heure de Clamart il y a 2 min, mais 10 min de retard annoncées à
        // l'arrivée : le décaler de 10 min le ferait passer pour « pas encore parti ».
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -2L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, 35L));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                clamart, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(10, ongoing.get(0).getDelayMinutes());
        assertEquals(T0 - 2 * MIN, OngoingTrains.effectiveDepartureMillis(ongoing.get(0)));
    }

    @Test
    public void aDelayAnnouncedAtDepartureShiftsTheDeparture() {
        StopVisit clamartVisit = visit("J1", "Plaisir - Grignon", -2L, null, null);
        clamartVisit.expectedDeparture = new Date(T0 + 6 * MIN);   // 8 min de retard au départ
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));

        // Toujours à quai : il appartient encore aux prochains départs.
        assertTrue(OngoingTrains.buildOngoing(
                map(clamartVisit), villepreux, new HashMap<>(), LineNDirection.ALLER, T0).isEmpty());

        List<TrainSchedule> later = OngoingTrains.buildOngoing(
                map(clamartVisit), villepreux, new HashMap<>(), LineNDirection.ALLER, T0 + 10 * MIN);
        assertEquals(1, later.size());
        assertEquals(T0 + 6 * MIN, OngoingTrains.effectiveDepartureMillis(later.get(0)));
    }

    @Test
    public void ignoresTrainsNotYetDeparted() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", 10L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 40L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                clamart, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresTrainsAlreadyArrived() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -60L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, -30L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                clamart, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresCancelledTrains() {
        StopVisit cancelled = visit("J1", "Plaisir - Grignon", null, 25L, null);
        cancelled.departureStatus = "cancelled";

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                map(visit("J1", "Plaisir - Grignon", -5L, null, null)), map(cancelled),
                new HashMap<>(), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void delayComesFromArrivalWhenAvailable() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -5L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, 32L));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                clamart, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(7, ongoing.get(0).getDelayMinutes());
        assertTrue(ongoing.get(0).isDelayed());
    }

    @Test
    public void retourDirectionUsesClamartAsDestination() {
        Map<String, StopVisit> clamart = map(visit("J1", "Paris Montparnasse", null, 20L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Villepreux - Les Clayes", "-10", "Versailles Chantiers", "5",
                "Clamart", "20"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), clamart, stopsCache, LineNDirection.RETOUR, T0);

        assertEquals(1, ongoing.size());
        assertEquals("Villepreux", ongoing.get(0).getOriginStation());
        assertEquals(T0 - 10 * MIN, ongoing.get(0).getAimedDepartureMillis());
    }

    @Test
    public void findsOriginStopByNumericIdWhenStopNamesAreUnresolved() {
        // L'estimated-timetable IDFM ne renvoie pas toujours StopPointName : les
        // arrêts s'appellent alors "Arrêt 43111" (l'ID de Clamart).
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Arrêt 43111", "-5", "Arrêt 43219", "10",
                "Arrêt 43221", "25"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), villepreux, stopsCache, LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(T0 - 5 * MIN, ongoing.get(0).getAimedDepartureMillis());
    }

    @Test
    public void buildsOngoingTrainAbsentFromBothStopMonitorings() {
        // Le cas qui cassait l'affichage : le train a dépassé Clamart (il a donc
        // disparu du stop-monitoring de Clamart) et n'est pas encore dans l'horizon
        // publié pour Villepreux. Seul son parcours le décrit — cela doit suffire.
        Map<String, List<TrainStop>> stopsCache = routes("J1",
                stops("Clamart", "-12", "Versailles Chantiers", "8",
                        "Villepreux - Les Clayes", "23"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), new HashMap<>(), stopsCache, LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        TrainSchedule train = ongoing.get(0);
        assertEquals("J1", train.getJourneyRef());
        assertEquals(T0 - 12 * MIN, train.getAimedDepartureMillis());
        assertEquals(T0 + 23 * MIN, train.getArrivalMillis());
    }

    @Test
    public void directionIsReadFromTheOrderOfTheStopsNotFromTheTerminus() {
        // Terminus "Mantes-la-Jolie" : mot-clé du sens Aller. Mais le parcours passe
        // par Villepreux AVANT Clamart — c'est donc un train du sens Retour.
        Map<String, List<TrainStop>> stopsCache = routes("J1",
                stops("Villepreux - Les Clayes", "-10", "Clamart", "15",
                        "Mantes-la-Jolie", "40"));

        assertTrue(OngoingTrains.buildOngoing(
                new HashMap<>(), new HashMap<>(), stopsCache, LineNDirection.ALLER, T0).isEmpty());
        assertEquals(1, OngoingTrains.buildOngoing(
                new HashMap<>(), new HashMap<>(), stopsCache, LineNDirection.RETOUR, T0).size());
    }

    @Test
    public void ignoresTrainsWhoseRouteStopsBeforeMyArrivalStation() {
        Map<String, List<TrainStop>> stopsCache = routes("J1",
                stops("Clamart", "-10", "Versailles Chantiers", "10"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), new HashMap<>(), stopsCache, LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void stopMonitoringDelayWinsOverTheRouteSchedule() {
        Map<String, List<TrainStop>> stopsCache = routes("J1",
                stops("Clamart", "-10", "Villepreux - Les Clayes", "20"));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 20L, 26L));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), villepreux, stopsCache, LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(6, ongoing.get(0).getDelayMinutes());
    }

    @Test
    public void takesTrainNumberFromTheEstimatedTimetableWhenStopMonitoringIsSilent() {
        Map<String, List<TrainStop>> stopsCache = routes("J1",
                stops("Clamart", "-10", "Villepreux - Les Clayes", "20"));
        Map<String, String> trainNumbers = new HashMap<>();
        trainNumbers.put("J1", "135642");
        Map<String, String> missions = new HashMap<>();
        missions.put("J1", "MOPI");

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new HashMap<>(), new HashMap<>(), stopsCache, trainNumbers, missions,
                LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals("135642", ongoing.get(0).getTrainNumber());
        assertEquals("MOPI", ongoing.get(0).getMissionName());
    }

    // ==================== rememberOriginVisits ====================

    @Test
    public void rememberedOriginVisitSurvivesItsDisappearanceFromStopMonitoring() {
        Map<String, StopVisit> memory = new HashMap<>();
        // Rafraîchissement d'il y a 5 minutes : le train est encore annoncé à Clamart.
        OngoingTrains.rememberOriginVisits(memory,
                map(visit("J1", "Plaisir - Grignon", -5L, null, null)), T0 - 5 * MIN);
        // Rafraîchissement suivant : parti, il a disparu de la réponse de l'API.
        OngoingTrains.rememberOriginVisits(memory, new HashMap<>(), T0);

        assertEquals(1, memory.size());

        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                memory, villepreux, new HashMap<>(), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(T0 - 5 * MIN, ongoing.get(0).getAimedDepartureMillis());
    }

    @Test
    public void rememberOriginVisitsForgetsOldAndUndatedVisits() {
        Map<String, StopVisit> memory = new HashMap<>();
        StopVisit undated = visit("J0", "Plaisir - Grignon", null, 10L, null);
        StopVisit old = visit("J1", "Plaisir - Grignon", -240L, null, null);   // parti il y a 4 h
        StopVisit recent = visit("J2", "Plaisir - Grignon", -20L, null, null);

        OngoingTrains.rememberOriginVisits(memory, map(undated, old, recent), T0);

        assertEquals(1, memory.size());
        assertTrue(memory.containsKey("J2"));
    }

    @Test
    public void rememberOriginVisitsKeepsFreshestVersionOfAVisit() {
        Map<String, StopVisit> memory = new HashMap<>();
        OngoingTrains.rememberOriginVisits(memory,
                map(visit("J1", "Plaisir - Grignon", -5L, null, null)), T0);
        StopVisit delayed = visit("J1", "Plaisir - Grignon", -5L, null, null);
        delayed.expectedDeparture = new Date(T0 - 2 * MIN);
        OngoingTrains.rememberOriginVisits(memory, map(delayed), T0);

        assertEquals(1, memory.size());
        assertEquals(new Date(T0 - 2 * MIN), memory.get("J1").expectedDeparture);
    }

    // ==================== describe ====================

    @Test
    public void describeBuildsReadableLabels() {
        TrainSchedule train = schedule("J1", -10, 20, 0, "onTime");
        List<TrainStop> parcours = stops("Clamart", "-10", "Versailles Chantiers", "5",
                "Villepreux - Les Clayes", "20");

        OngoingTrain display = OngoingTrains.describe(train, LineNDirection.ALLER, parcours, T0);

        assertEquals("Clamart → Villepreux", display.getDirectionLabel());
        assertTrue(display.getPositionLabel().contains("Entre Clamart et Versailles Chantiers"));
        assertTrue(display.getNextStopLabel().startsWith("Prochain arrêt : Versailles Chantiers"));
        assertTrue(display.getEtaLabel().startsWith("Arrivée Villepreux"));
        assertTrue(display.getEtaLabel().contains("dans 20 min"));
        assertEquals("Train 135642 • MOPI", display.getTrainInfoLabel());
        assertEquals(33, display.getProgressPercent());
    }

    @Test
    public void describeFallsBackOnScheduleWhenStopsAreMissing() {
        TrainSchedule train = schedule("J1", -10, 20, 0, "onTime");

        OngoingTrain display = OngoingTrains.describe(train, LineNDirection.ALLER, null, T0);

        assertFalse(display.getPositionLabel().isEmpty());
        assertEquals("", display.getNextStopLabel());
        assertEquals(33, display.getProgressPercent());
    }
}
