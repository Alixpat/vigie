package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainStop;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PassageHistoryTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;

    /** Un arrêt desservi, avec son heure théorique et son heure annoncée. */
    private static TrainStop stop(String name, long aimedOffsetMin, long actualOffsetMin) {
        long aimed = T0 + aimedOffsetMin * MIN;
        long actual = actualOffsetMin == Long.MIN_VALUE ? 0 : T0 + actualOffsetMin * MIN;
        return new TrainStop(name, "", aimed, actual, aimed, actual, "", false, false);
    }

    private static TrainStop stopWithoutActual(String name, long aimedOffsetMin) {
        return stop(name, aimedOffsetMin, Long.MIN_VALUE);
    }

    private static List<String> stationsOf(List<PassageHistory.Passage> passages) {
        List<String> names = new ArrayList<>();
        for (PassageHistory.Passage passage : passages) names.add(passage.getStationName());
        return names;
    }

    @Test
    public void keepsOnlyTheStationsOfMySegment() {
        // Le train part de Paris et va à Mantes : seules les gares entre Clamart
        // et Villepreux me concernent.
        List<TrainStop> stops = Arrays.asList(
                stop("Paris Montparnasse", -10, -10),
                stop("Clamart", 0, 2),
                stop("Versailles Chantiers", 15, 17),
                stop("Villepreux - Les Clayes", 25, 27),
                stop("Plaisir - Grignon", 32, 34));

        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 20 * MIN);

        assertFalse(stationsOf(passages).contains("Paris Montparnasse"));
        assertFalse(stationsOf(passages).contains("Plaisir - Grignon"));
        assertTrue(stationsOf(passages).contains("Clamart"));
        assertTrue(stationsOf(passages).contains("Villepreux - Les Clayes"));
    }

    @Test
    public void ordersPassagesChronologically() {
        List<TrainStop> stops = Arrays.asList(
                stop("Villepreux - Les Clayes", 25, 27),
                stop("Clamart", 0, 2),
                stop("Versailles Chantiers", 15, 17));

        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.ALLER, T0);

        assertEquals(Arrays.asList("Clamart", "Versailles Chantiers", "Villepreux - Les Clayes"),
                stationsOf(passages));
    }

    @Test
    public void marksPassedStationsAndOnlyThose() {
        List<TrainStop> stops = Arrays.asList(
                stop("Clamart", 0, 2),
                stop("Versailles Chantiers", 15, 17),
                stop("Villepreux - Les Clayes", 25, 27));

        // Il est T0+20 : Clamart est franchie, Versailles aussi (réel = T0+17),
        // Villepreux est encore à venir.
        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 20 * MIN);

        assertTrue(passages.get(0).isPassed());
        assertTrue(passages.get(1).isPassed());
        assertFalse(passages.get(2).isPassed());
    }

    @Test
    public void judgesThePassageOnTheRealTimeNotTheTimetable() {
        // Théorique T0+15, mais le train a 10 min de retard : à T0+18 il n'est
        // pas encore passé, même si l'horaire au tableau est dépassé.
        List<TrainStop> stops = Collections.singletonList(
                stop("Versailles Chantiers", 15, 25));

        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 18 * MIN);

        assertFalse(passages.get(0).isPassed());
    }

    @Test
    public void reportsTheDelayBetweenTimetableAndReality() {
        List<TrainStop> stops = Collections.singletonList(
                stop("Versailles Chantiers", 15, 18));

        PassageHistory.Passage passage =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 20 * MIN).get(0);

        assertEquals(T0 + 15 * MIN, passage.getAimedMillis());
        assertEquals(T0 + 18 * MIN, passage.getActualMillis());
        assertEquals(3, passage.getDelayMinutes());
        assertTrue(passage.hasActual());
    }

    @Test
    public void reportsAnEarlyPassageAsANegativeDelay() {
        List<TrainStop> stops = Collections.singletonList(
                stop("Versailles Chantiers", 15, 13));

        PassageHistory.Passage passage =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 20 * MIN).get(0);

        assertEquals(-2, passage.getDelayMinutes());
    }

    @Test
    public void keepsAStationWithoutRealTime() {
        // Sans heure annoncée, la gare reste affichée avec son seul théorique :
        // l'escamoter ferait croire que le train ne la dessert pas.
        List<TrainStop> stops = Collections.singletonList(
                stopWithoutActual("Versailles Chantiers", 15));

        PassageHistory.Passage passage =
                PassageHistory.build(stops, LineNDirection.ALLER, T0).get(0);

        assertFalse(passage.hasActual());
        assertEquals(0, passage.getDelayMinutes());
        assertEquals(T0 + 15 * MIN, passage.getAimedMillis());
    }

    @Test
    public void followsTheReturnDirectionToo() {
        List<TrainStop> stops = Arrays.asList(
                stop("Plaisir - Grignon", -8, -8),
                stop("Villepreux - Les Clayes", 0, 1),
                stop("Versailles Chantiers", 10, 11),
                stop("Clamart", 25, 26),
                stop("Paris Montparnasse", 32, 33));

        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.RETOUR, T0 + 15 * MIN);

        assertEquals(Arrays.asList("Villepreux - Les Clayes", "Versailles Chantiers", "Clamart"),
                stationsOf(passages));
    }

    @Test
    public void returnsNothingWhenTheRouteIsUnknown() {
        assertTrue(PassageHistory.build(null, LineNDirection.ALLER, T0).isEmpty());
        assertTrue(PassageHistory.build(Collections.<TrainStop>emptyList(),
                LineNDirection.ALLER, T0).isEmpty());
    }

    @Test
    public void showsTheTruncatedRouteRatherThanNothing() {
        // Train déjà au-delà de Clamart et jamais observé avant : son parcours ne
        // décrit plus que la fin. Mieux vaut cette fin que rien.
        List<TrainStop> stops = Arrays.asList(
                stop("Versailles Chantiers", 15, 16),
                stop("Villepreux - Les Clayes", 25, 26));

        List<PassageHistory.Passage> passages =
                PassageHistory.build(stops, LineNDirection.ALLER, T0 + 20 * MIN);

        assertEquals(Arrays.asList("Versailles Chantiers", "Villepreux - Les Clayes"),
                stationsOf(passages));
    }
}
