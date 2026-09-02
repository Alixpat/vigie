package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JourneyRoutesTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    private static TrainStop stop(String name, String ref, long offsetMin) {
        long time = T0 + offsetMin * MIN;
        return new TrainStop(name, ref, time, time, time, time, "", false, false);
    }

    private static List<String> namesOf(List<TrainStop> stops) {
        List<String> names = new ArrayList<>();
        for (TrainStop stop : stops) names.add(stop.getStopName());
        return names;
    }

    @Test
    public void keepsStopsDroppedByTheNewResponse() {
        // C'est tout l'enjeu : IDFM ne renvoie que ce qu'il reste à parcourir.
        // Sans fusion, Clamart disparaîtrait dès que le train l'a dépassé.
        List<TrainStop> known = Arrays.asList(
                stop("Clamart", "STIF:StopPoint:Q:43111:", -10),
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43219:", 10),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 25));
        List<TrainStop> fresh = Arrays.asList(
                stop("Versailles Chantiers", "STIF:StopPoint:Q:43219:", 12),
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 27));

        List<TrainStop> merged = JourneyRoutes.merge(known, fresh);

        assertEquals(Arrays.asList("Clamart", "Versailles Chantiers", "Villepreux - Les Clayes"),
                namesOf(merged));
        // Les arrêts encore décrits prennent la version fraîche (train retardé).
        assertEquals(T0 + 12 * MIN, merged.get(1).getBestArrivalMillis());
    }

    @Test
    public void matchesStopsOnTheirRefEvenWhenTheNameIsResolvedLater() {
        List<TrainStop> known = Arrays.asList(
                stop("Arrêt 43111", "STIF:StopPoint:Q:43111:", -10),
                stop("Arrêt 43221", "STIF:StopPoint:Q:43221:", 25));
        List<TrainStop> fresh = Arrays.asList(
                stop("Villepreux - Les Clayes", "STIF:StopPoint:Q:43221:", 25));

        List<TrainStop> merged = JourneyRoutes.merge(known, fresh);

        assertEquals(2, merged.size());
        assertEquals(Arrays.asList("Arrêt 43111", "Villepreux - Les Clayes"), namesOf(merged));
    }

    @Test
    public void matchesUnresolvedNamesOnTheirTrailingIdWhenThereIsNoRef() {
        List<TrainStop> known = Arrays.asList(
                new TrainStop("Arrêt 43111", T0 - 10 * MIN, T0 - 10 * MIN,
                        T0 - 10 * MIN, T0 - 10 * MIN, "", true, false));
        List<TrainStop> fresh = Arrays.asList(
                new TrainStop("Clamart", "STIF:StopPoint:Q:43111:", T0 - 8 * MIN, T0 - 8 * MIN,
                        T0 - 8 * MIN, T0 - 8 * MIN, "", true, false));

        List<TrainStop> merged = JourneyRoutes.merge(known, fresh);

        assertEquals(1, merged.size());
        assertEquals("Clamart", merged.get(0).getStopName());
    }

    @Test
    public void mergeSortsChronologically() {
        List<TrainStop> known = Arrays.asList(stop("Villepreux", "C", 25));
        List<TrainStop> fresh = Arrays.asList(stop("Clamart", "A", -10), stop("Versailles", "B", 10));

        assertEquals(Arrays.asList("Clamart", "Versailles", "Villepreux"),
                namesOf(JourneyRoutes.merge(known, fresh)));
    }

    @Test
    public void mergeMarksOnlyTheEndsOfTheReconstructedRoute() {
        // La réponse fraîche marque Versailles comme "premier arrêt" : c'est le
        // premier de ce qu'il lui reste à parcourir, pas l'origine du train.
        List<TrainStop> known = Arrays.asList(
                new TrainStop("Clamart", "A", T0 - 10 * MIN, T0 - 10 * MIN,
                        T0 - 10 * MIN, T0 - 10 * MIN, "", true, false));
        List<TrainStop> fresh = Arrays.asList(
                new TrainStop("Versailles", "B", T0 + 10 * MIN, T0 + 10 * MIN,
                        T0 + 10 * MIN, T0 + 10 * MIN, "", true, false),
                new TrainStop("Villepreux", "C", T0 + 25 * MIN, T0 + 25 * MIN,
                        T0 + 25 * MIN, T0 + 25 * MIN, "", false, true));

        List<TrainStop> merged = JourneyRoutes.merge(known, fresh);

        assertTrue(merged.get(0).isDeparture());
        assertFalse(merged.get(1).isDeparture());
        assertFalse(merged.get(1).isArrival());
        assertTrue(merged.get(2).isArrival());
    }

    @Test
    public void mergeToleratesNulls() {
        List<TrainStop> fresh = Arrays.asList(stop("Clamart", "A", 0));

        assertEquals(1, JourneyRoutes.merge(null, fresh).size());
        assertEquals(1, JourneyRoutes.merge(fresh, null).size());
        assertTrue(JourneyRoutes.merge(null, null).isEmpty());
    }

    @Test
    public void purgeForgetsJourneysWhoseLastStopIsOld() {
        Map<String, List<TrainStop>> routes = new HashMap<>();
        routes.put("hier", Arrays.asList(stop("Clamart", "A", -300), stop("Villepreux", "C", -260)));
        routes.put("enCours", Arrays.asList(stop("Clamart", "A", -10), stop("Villepreux", "C", 20)));
        routes.put("vide", new ArrayList<>());

        JourneyRoutes.purge(routes, T0, JourneyRoutes.MAX_AGE_MS);

        assertEquals(1, routes.size());
        assertTrue(routes.containsKey("enCours"));
        assertFalse(routes.containsKey("hier"));
    }
}
