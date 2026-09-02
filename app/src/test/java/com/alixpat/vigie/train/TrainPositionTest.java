package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TrainPositionTest {

    private static final long T0 = 1_700_000_000_000L; // instant de référence arbitraire
    private static final long MIN = 60_000L;

    /** Arrêt desservi à {@code offsetMin} minutes de T0 (arrivée = départ). */
    private static TrainStop stop(String name, long offsetMin, boolean first, boolean last) {
        long time = T0 + offsetMin * MIN;
        return new TrainStop(name, time, time, time, time, "", first, last);
    }

    private static List<TrainStop> parcours() {
        return new ArrayList<>(Arrays.asList(
                stop("Clamart", 0, true, false),
                stop("Meudon", 10, false, false),
                stop("Versailles Chantiers", 20, false, false),
                stop("Villepreux - Les Clayes", 30, false, true)));
    }

    @Test
    public void betweenTwoStopsWhenRunning() {
        // 15 min après le départ : entre Meudon (10) et Versailles (20)
        TrainPosition position = TrainPosition.compute(parcours(), T0 + 15 * MIN);

        assertEquals(TrainPosition.Phase.BETWEEN, position.getPhase());
        assertEquals("Meudon", position.getCurrentStopName());
        assertEquals("Versailles Chantiers", position.getNextStopName());
        assertEquals(T0 + 20 * MIN, position.getNextStopMillis());
        assertEquals(50, position.getProgressPercent());
        assertTrue(position.describe().contains("Entre Meudon et Versailles Chantiers"));
    }

    @Test
    public void atStopWhenArrivalTimeReached() {
        // Pile à l'heure de Meudon : le train y est à quai.
        TrainPosition position = TrainPosition.compute(parcours(), T0 + 10 * MIN);

        assertEquals(TrainPosition.Phase.AT_STOP, position.getPhase());
        assertEquals("Meudon", position.getCurrentStopName());
        assertEquals("Versailles Chantiers", position.getNextStopName());
        assertTrue(position.describe().contains("En gare de Meudon"));
    }

    @Test
    public void notStartedBeforeFirstStop() {
        TrainPosition position = TrainPosition.compute(parcours(), T0 - 5 * MIN);

        assertEquals(TrainPosition.Phase.NOT_STARTED, position.getPhase());
        assertEquals("Clamart", position.getCurrentStopName());
        assertEquals(0, position.getProgressPercent());
        assertTrue(position.describe().contains("Pas encore parti de Clamart"));
    }

    @Test
    public void arrivedAfterLastStop() {
        TrainPosition position = TrainPosition.compute(parcours(), T0 + 45 * MIN);

        assertEquals(TrainPosition.Phase.ARRIVED, position.getPhase());
        assertEquals("Villepreux - Les Clayes", position.getCurrentStopName());
        assertEquals(100, position.getProgressPercent());
        assertTrue(position.describe().contains("Arrivé à"));
    }

    @Test
    public void unknownWhenNoStops() {
        assertFalse(TrainPosition.compute(null, T0).isKnown());
        assertFalse(TrainPosition.compute(new ArrayList<>(), T0).isKnown());
        assertEquals("", TrainPosition.compute(null, T0).describe());
        assertEquals(-1, TrainPosition.compute(null, T0).getProgressPercent());
    }

    @Test
    public void stopsAreSortedBeforeAnalysis() {
        // Ordre d'entrée volontairement mélangé : le résultat doit être identique.
        List<TrainStop> shuffled = Arrays.asList(
                stop("Villepreux - Les Clayes", 30, false, true),
                stop("Clamart", 0, true, false),
                stop("Versailles Chantiers", 20, false, false),
                stop("Meudon", 10, false, false));

        TrainPosition position = TrainPosition.compute(shuffled, T0 + 15 * MIN);

        assertEquals(TrainPosition.Phase.BETWEEN, position.getPhase());
        assertEquals("Meudon", position.getCurrentStopName());
        assertEquals("Versailles Chantiers", position.getNextStopName());
    }

    @Test
    public void progressIsClampedBetweenZeroAndHundred() {
        assertEquals(0, TrainPosition.compute(parcours(), T0 - 60 * MIN).getProgressPercent());
        assertEquals(100, TrainPosition.compute(parcours(), T0 + 120 * MIN).getProgressPercent());
    }

    @Test
    public void statusAtUsesInjectedInstant() {
        TrainStop meudon = stop("Meudon", 10, false, false);

        assertEquals(TrainStop.StopStatus.UPCOMING, TrainPosition.statusAt(meudon, T0));
        assertEquals(TrainStop.StopStatus.CURRENT, TrainPosition.statusAt(meudon, T0 + 10 * MIN));
        assertEquals(TrainStop.StopStatus.PASSED, TrainPosition.statusAt(meudon, T0 + 11 * MIN));
    }
}
