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

    private static OngoingTrains.Sources sources(Map<String, StopVisit> origin,
                                                 Map<String, StopVisit> destination,
                                                 Map<String, List<TrainStop>> stops) {
        return new OngoingTrains.Sources(origin, destination, stops);
    }

    @Test
    public void buildsOngoingTrainMissingFromOriginStopMonitoring() {
        // Le train est parti de Clamart : il n'apparaît plus que dans les arrivées
        // à Villepreux. Son heure de départ vient du parcours (estimated-timetable).
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Clamart", "-5", "Versailles Chantiers", "10",
                "Villepreux - Les Clayes", "25"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(new HashMap<>(), villepreux, stopsCache), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        TrainSchedule train = ongoing.get(0);
        assertEquals("J1", train.getJourneyRef());
        assertEquals(T0 - 5 * MIN, train.getAimedDepartureMillis());
        assertEquals(T0 + 25 * MIN, train.getArrivalMillis());
        assertEquals("Clamart", train.getOriginStation());
        assertTrue(OngoingTrains.isOngoing(train, T0));
    }

    @Test
    public void findsTrainKnownOnlyFromItsJourneyStops() {
        // Ni Clamart ni Villepreux ne l'annoncent plus (parti d'un côté, pas encore
        // dans la fenêtre de l'autre) : le parcours complet suffit.
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Paris Montparnasse", "-15", "Clamart", "-8",
                "Versailles Chantiers", "6", "Villepreux - Les Clayes", "20"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(null, null, stopsCache), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(T0 - 8 * MIN, ongoing.get(0).getAimedDepartureMillis());
        assertEquals(T0 + 20 * MIN, ongoing.get(0).getArrivalMillis());
        // Terminus déduit du dernier arrêt du parcours.
        assertEquals("Villepreux - Les Clayes", ongoing.get(0).getDestination());
    }

    @Test
    public void keepsTrainWhoseTerminusMatchesNoKeyword() {
        // "Épône - Mézières" n'est dans aucune liste de mots-clés, mais ce train
        // dessert bien Clamart puis Villepreux : il me concerne.
        Map<String, StopVisit> villepreux = map(visit("J1", "Épône - Mézières", null, 20L, null));
        Map<String, StopVisit> clamart = map(visit("J1", "Épône - Mézières", -6L, null, null));

        assertFalse(LineNDirection.ALLER.matchesDestination("Épône - Mézières"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(clamart, villepreux, null), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals("Épône - Mézières", ongoing.get(0).getDestination());
    }

    @Test
    public void prefersOriginStopMonitoringForDepartureTime() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -8L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(clamart, villepreux, new HashMap<>()), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(T0 - 8 * MIN, ongoing.get(0).getAimedDepartureMillis());
    }

    @Test
    public void directionIsDecidedByTheOrderOfMyTwoStations() {
        // Villepreux desservi avant Clamart : c'est un retour, pas un aller.
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Villepreux - Les Clayes", "-10", "Versailles Chantiers", "5",
                "Clamart", "20", "Paris Montparnasse", "28"));

        assertTrue(OngoingTrains.buildOngoing(
                sources(null, null, stopsCache), LineNDirection.ALLER, T0).isEmpty());

        List<TrainSchedule> retour = OngoingTrains.buildOngoing(
                sources(null, null, stopsCache), LineNDirection.RETOUR, T0);
        assertEquals(1, retour.size());
        assertEquals("Villepreux", retour.get(0).getOriginStation());
        assertEquals(T0 - 10 * MIN, retour.get(0).getAimedDepartureMillis());
    }

    @Test
    public void ignoresTrainsThatDoNotServeBothOfMyStations() {
        // Terminus plausible, mais aucun passage à Clamart dans son parcours.
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, null));
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Saint-Cyr", "-20", "Versailles Chantiers", "10",
                "Villepreux - Les Clayes", "25"));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(new HashMap<>(), villepreux, stopsCache), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresTrainsNotYetDeparted() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", 10L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 40L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(clamart, villepreux, new HashMap<>()), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresTrainsAlreadyArrived() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -60L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, -30L, null));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(clamart, villepreux, new HashMap<>()), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void ignoresCancelledTrains() {
        StopVisit cancelled = visit("J1", "Plaisir - Grignon", null, 25L, null);
        cancelled.departureStatus = "cancelled";

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(map(visit("J1", "Plaisir - Grignon", -5L, null, null)), map(cancelled),
                        new HashMap<>()), LineNDirection.ALLER, T0);

        assertTrue(ongoing.isEmpty());
    }

    @Test
    public void delayComesFromArrivalWhenAvailable() {
        Map<String, StopVisit> clamart = map(visit("J1", "Plaisir - Grignon", -5L, null, null));
        Map<String, StopVisit> villepreux = map(visit("J1", "Plaisir - Grignon", null, 25L, 32L));

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                sources(clamart, villepreux, new HashMap<>()), LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals(7, ongoing.get(0).getDelayMinutes());
        assertTrue(ongoing.get(0).isDelayed());
        // Retard pris en route : l'heure de départ n'est pas décalée.
        assertEquals("", ongoing.get(0).getExpectedDepartureTime());
    }

    @Test
    public void trainNumberFallsBackOnTheJourneyCaches() {
        Map<String, List<TrainStop>> stopsCache = new HashMap<>();
        stopsCache.put("J1", stops("Clamart", "-5", "Villepreux - Les Clayes", "25"));
        Map<String, String> trainNumbers = new HashMap<>();
        trainNumbers.put("J1", "135642");
        Map<String, String> missions = new HashMap<>();
        missions.put("J1", "MOPI");

        List<TrainSchedule> ongoing = OngoingTrains.buildOngoing(
                new OngoingTrains.Sources(null, null, stopsCache, trainNumbers, missions),
                LineNDirection.ALLER, T0);

        assertEquals(1, ongoing.size());
        assertEquals("135642", ongoing.get(0).getTrainNumber());
        assertEquals("MOPI", ongoing.get(0).getMissionName());
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
