package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class SegmentLegTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    private static TrainStop stop(String name, long offsetMin) {
        long time = T0 + offsetMin * MIN;
        return new TrainStop(name, time, time, time, time, "", false, false);
    }

    private static TrainStop stop(String name, long aimedMin, long expectedMin) {
        return new TrainStop(name,
                T0 + aimedMin * MIN, T0 + expectedMin * MIN,
                T0 + aimedMin * MIN, T0 + expectedMin * MIN, "", false, false);
    }

    private static StopVisit visit(Long aimedDepartureMin, Long aimedArrivalMin,
                                   Long expectedArrivalMin) {
        StopVisit visit = new StopVisit();
        visit.journeyRef = "J1";
        visit.destination = "Peu importe";
        visit.departureStatus = "onTime";
        if (aimedDepartureMin != null) visit.aimedDeparture = new Date(T0 + aimedDepartureMin * MIN);
        if (aimedArrivalMin != null) visit.aimedArrival = new Date(T0 + aimedArrivalMin * MIN);
        if (expectedArrivalMin != null) visit.expectedArrival = new Date(T0 + expectedArrivalMin * MIN);
        return visit;
    }

    @Test
    public void servesMySegmentWhenBothStationsAreOnTheRoute() {
        List<TrainStop> route = Arrays.asList(
                stop("Paris Montparnasse", -15),
                stop("Clamart", -8),
                stop("Versailles Chantiers", 6),
                stop("Villepreux - Les Clayes", 20));

        SegmentLeg leg = SegmentLeg.resolve(null, null, route, LineNDirection.ALLER);

        assertTrue(leg.servesMySegment());
        assertEquals(T0 - 8 * MIN, leg.getDepartureMillis());
        assertEquals(T0 + 20 * MIN, leg.getArrivalMillis());
    }

    @Test
    public void doesNotServeMySegmentInTheOppositeDirection() {
        List<TrainStop> route = Arrays.asList(
                stop("Villepreux - Les Clayes", -10),
                stop("Clamart", 20));

        assertFalse(SegmentLeg.resolve(null, null, route, LineNDirection.ALLER).servesMySegment());
        assertTrue(SegmentLeg.resolve(null, null, route, LineNDirection.RETOUR).servesMySegment());
    }

    @Test
    public void doesNotServeMySegmentWhenOneStationIsMissing() {
        List<TrainStop> route = Arrays.asList(
                stop("Saint-Cyr", -10),
                stop("Villepreux - Les Clayes", 20));

        assertFalse(SegmentLeg.resolve(null, null, route, LineNDirection.ALLER).servesMySegment());
    }

    @Test
    public void stopMonitoringWinsOverTheRoute() {
        List<TrainStop> route = Arrays.asList(
                stop("Clamart", -8),
                stop("Villepreux - Les Clayes", 20));

        SegmentLeg leg = SegmentLeg.resolve(visit(-6L, null, null), visit(null, 22L, null),
                route, LineNDirection.ALLER);

        assertEquals(T0 - 6 * MIN, leg.getDepartureMillis());
        assertEquals(T0 + 22 * MIN, leg.getArrivalMillis());
    }

    @Test
    public void delaysAreReadAtBothEnds() {
        StopVisit origin = visit(-10L, null, null);
        origin.expectedDeparture = new Date(T0 - 8 * MIN);           // 2 min au départ
        SegmentLeg leg = SegmentLeg.resolve(origin, visit(null, 20L, 27L), null,
                LineNDirection.ALLER);

        assertEquals(2, leg.getDepartureDelayMinutes());
        assertEquals(7, leg.getArrivalDelayMinutes());
        assertEquals(7, leg.getDelayMinutes());   // à bord, c'est l'arrivée qui compte
    }

    @Test
    public void delayFallsBackOnDepartureWhenArrivalHasNone() {
        StopVisit origin = visit(-10L, null, null);
        origin.expectedDeparture = new Date(T0 - 5 * MIN);
        SegmentLeg leg = SegmentLeg.resolve(origin, visit(null, 20L, null), null,
                LineNDirection.ALLER);

        assertEquals(5, leg.getDelayMinutes());
    }

    @Test
    public void routeTimesFallBackOnEstimatesWhenTheoreticalIsMissing() {
        List<TrainStop> route = new ArrayList<>(Arrays.asList(
                stop("Clamart", -8, -6),
                stop("Villepreux - Les Clayes", 20, 24)));

        SegmentLeg leg = SegmentLeg.resolve(null, null, route, LineNDirection.ALLER);

        assertEquals(T0 - 8 * MIN, leg.getDepartureMillis());
        assertEquals(2, leg.getDepartureDelayMinutes());
        assertEquals(4, leg.getArrivalDelayMinutes());
    }

    @Test
    public void cancelledOnEitherEndMarksTheLegCancelled() {
        StopVisit cancelled = visit(-5L, null, null);
        cancelled.departureStatus = "cancelled";

        assertTrue(SegmentLeg.resolve(cancelled, visit(null, 20L, null), null,
                LineNDirection.ALLER).isCancelled());
        assertFalse(SegmentLeg.resolve(visit(-5L, null, null), visit(null, 20L, null), null,
                LineNDirection.ALLER).isCancelled());
    }

    @Test
    public void findStopMatchesStationNamesLoosely() {
        List<TrainStop> route = Arrays.asList(
                stop("Villepreux - Les Clayes", 0),
                stop("Clamart", 10));

        assertNotNull(SegmentLeg.findStop(route, "Villepreux"));
        assertEquals("Villepreux - Les Clayes",
                SegmentLeg.findStop(route, "Villepreux").getStopName());
        assertNotNull(SegmentLeg.findStop(route, "Clamart"));
        assertNull(SegmentLeg.findStop(route, "Rambouillet"));
        assertNull(SegmentLeg.findStop(null, "Clamart"));
    }
}
