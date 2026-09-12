package com.alixpat.vigie.train;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyTrainsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    private static TrainStop stop(String name, String ref, long offsetMin) {
        long time = T0 + offsetMin * MIN;
        return new TrainStop(name, ref, time, time, time, time, "", false, false);
    }

    /** Parcours complet d'un train qui me sert : il s'arrête à mes deux gares. */
    private static List<TrainStop> myTrain() {
        return Arrays.asList(
                stop("Paris Montparnasse", "STIF:StopPoint:Q:43000:", -10),
                stop("Clamart", "STIF:StopPoint:Q:43111:", 0),
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 30));
    }

    private static Map<String, StopVisit> seen(String journeyRef) {
        StopVisit visit = new StopVisit();
        visit.journeyRef = journeyRef;
        visit.aimedDeparture = new Date(T0);
        Map<String, StopVisit> memory = new HashMap<>();
        memory.put(journeyRef, visit);
        return memory;
    }

    @Test
    public void trainServingBothStationsIsMine() {
        assertTrue(MyTrains.servesMyStations(myTrain(), LineNDirection.ALLER, false, false));
        assertTrue(MyTrains.servesMyStations(myTrain(), LineNDirection.RETOUR, false, false));
    }

    @Test
    public void trainOnAnotherBranchIsNotMine() {
        // Paris → Rambouillet : il dessert Clamart, jamais Villepreux.
        List<TrainStop> rambouillet = Arrays.asList(
                stop("Paris Montparnasse", "STIF:StopPoint:Q:43000:", -10),
                stop("Clamart", "STIF:StopPoint:Q:43111:", 0),
                stop("Trappes", "STIF:StopPoint:Q:43300:", 25),
                stop("Rambouillet", "STIF:StopPoint:Q:43400:", 45));

        assertFalse(MyTrains.servesMyStations(rambouillet, LineNDirection.ALLER, false, false));
    }

    @Test
    public void directTrainSkippingMyStationIsNotMine() {
        // Semi-direct Paris → Mantes : il traverse mon tronçon sans s'y arrêter.
        List<TrainStop> direct = Arrays.asList(
                stop("Paris Montparnasse", "STIF:StopPoint:Q:43000:", -10),
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Mantes-la-Jolie", "STIF:StopPoint:Q:43500:", 50));

        assertFalse(MyTrains.servesMyStations(direct, LineNDirection.ALLER, false, false));
    }

    @Test
    public void stationSeenAtStopMonitoringCountsEvenWhenDroppedFromRoute() {
        // Le train a franchi Clamart : l'estimated-timetable ne décrit plus que
        // la suite. Sans la mémoire des passages, il basculerait dans « les
        // autres » au moment précis où il roule sur mon tronçon.
        List<TrainStop> remaining = Arrays.asList(
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 30));

        assertFalse(MyTrains.servesMyStations(remaining, LineNDirection.ALLER, false, false));
        assertTrue(MyTrains.servesMyStations("J1", remaining, LineNDirection.ALLER,
                seen("J1"), null));
    }

    @Test
    public void unresolvedStopNamesAreMatchedOnTheirId() {
        // Quand ni StopPointName ni le cache Navitia n'ont résolu le nom, les
        // arrêts s'appellent "Arrêt 43111" : l'identifiant doit suffire.
        List<TrainStop> anonymous = Arrays.asList(
                stop("Arrêt 43111", "STIF:StopPoint:Q:43111:", 0),
                stop("Arrêt 43221", "STIF:StopPoint:Q:43221:", 30));

        assertTrue(MyTrains.servesMyStations(anonymous, LineNDirection.ALLER, false, false));
    }

    @Test
    public void unknownRouteWithoutMemoryIsNotMine() {
        assertFalse(MyTrains.servesMyStations(null, LineNDirection.ALLER, false, false));
        assertFalse(MyTrains.servesMyStations("J1", null, LineNDirection.ALLER,
                new HashMap<String, StopVisit>(), new HashMap<String, StopVisit>()));
    }

    @Test
    public void bothMemoriesTogetherAreEnough() {
        assertTrue(MyTrains.servesMyStations("J1", null, LineNDirection.ALLER,
                seen("J1"), seen("J1")));
        // Une seule des deux gares ne suffit pas : je ne peux pas prendre ce train.
        assertFalse(MyTrains.servesMyStations("J1", null, LineNDirection.ALLER,
                seen("J1"), new HashMap<String, StopVisit>()));
    }
}
