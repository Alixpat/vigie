package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
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
    public void trainPastMyOriginStationStaysMine() {
        // Le train a franchi Clamart : l'estimated-timetable ne décrit plus que
        // la suite. Rien ne prouve qu'il ne s'y est pas arrêté — le classer dans
        // « les autres » le ferait disparaître au moment précis où il roule sur
        // mon tronçon, vers ma gare d'arrivée.
        List<TrainStop> remaining = Arrays.asList(
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 30));

        assertTrue(MyTrains.servesMyStations(remaining, LineNDirection.ALLER, false, false));
        assertEquals(MyTrains.Verdict.PROBABLY,
                MyTrains.verdict(remaining, LineNDirection.ALLER, false, false));
    }

    @Test
    public void stationSeenAtStopMonitoringMakesTheVerdictCertain() {
        List<TrainStop> remaining = Arrays.asList(
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 30));

        assertEquals(MyTrains.Verdict.SERVES,
                MyTrains.verdict("J1", remaining, LineNDirection.ALLER, seen("J1"), null));
    }

    @Test
    public void trainPastMyDestinationStaysMineInTheOtherDirection() {
        // Sens retour : Villepreux est derrière lui, Clamart devant.
        List<TrainStop> remaining = Arrays.asList(
                stop("Saint-Cyr", "STIF:StopPoint:Q:43160:", 5),
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 12),
                stop("Clamart", "STIF:StopPoint:Q:43111:", 25),
                stop("Paris Montparnasse", "STIF:StopPoint:Q:43000:", 35));

        assertTrue(MyTrains.servesMyStations(remaining, LineNDirection.RETOUR, false, false));
    }

    @Test
    public void skippedStationInsideTheKnownRouteIsProof() {
        // Le parcours décrit Paris → Villepreux et ne mentionne pas Clamart :
        // ici le silence de l'API est une réponse, le train ne s'y arrête pas.
        List<TrainStop> skipping = Arrays.asList(
                stop("Paris Montparnasse", "STIF:StopPoint:Q:43000:", -10),
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 15),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 30));

        assertFalse(MyTrains.servesMyStations(skipping, LineNDirection.ALLER, false, false));
        assertEquals(MyTrains.Service.SKIPPED,
                MyTrains.serviceAt(skipping, "Clamart", "43111"));
    }

    @Test
    public void truncatedRouteOnAnotherBranchIsStillNotMine() {
        // Déjà à Versailles, mais il file vers Rambouillet : Villepreux est
        // au-delà de son terminus, il n'y passera jamais.
        List<TrainStop> rambouillet = Arrays.asList(
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43150:", 0),
                stop("Saint-Cyr", "STIF:StopPoint:Q:43160:", 5),
                stop("Trappes", "STIF:StopPoint:Q:43300:", 15),
                stop("Rambouillet", "STIF:StopPoint:Q:43400:", 35));

        assertFalse(MyTrains.servesMyStations(rambouillet, LineNDirection.ALLER, false, false));
    }

    @Test
    public void routeWithoutBearingOnMyAxisProvesNothing() {
        // Un seul repère sur l'axe Paris → Mantes : le sens de marche est
        // indécidable, donc on ne déduit rien.
        List<TrainStop> vague = Arrays.asList(
                stop("Trappes", "STIF:StopPoint:Q:43300:", 0),
                stop("Rambouillet", "STIF:StopPoint:Q:43400:", 20));

        assertEquals(MyTrains.Service.UNKNOWN, MyTrains.serviceAt(vague, "Clamart", "43111"));
        assertEquals(MyTrains.Service.UNKNOWN, MyTrains.serviceAt(null, "Clamart", "43111"));
        assertFalse(MyTrains.servesMyStations(vague, LineNDirection.ALLER, false, false));
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
