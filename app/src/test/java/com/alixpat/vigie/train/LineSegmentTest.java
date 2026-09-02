package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LineSegmentTest {

    @Test
    public void allerRunsFromClamartToVillepreux() {
        LineSegment segment = LineNDirection.ALLER.getSegment();

        // Clamart, Meudon, Bellevue, Sèvres, Chaville, Viroflay, Versailles,
        // Saint-Cyr, Fontenay, Villepreux.
        assertEquals(10, segment.size());
        assertEquals(0, segment.indexOf("Clamart"));
        assertEquals(9, segment.indexOf("Villepreux - Les Clayes"));
        assertTrue(segment.indexOf("Versailles Chantiers") > segment.indexOf("Meudon"));
    }

    @Test
    public void retourIsTheSameSegmentReversed() {
        LineSegment segment = LineNDirection.RETOUR.getSegment();

        assertEquals(10, segment.size());
        assertEquals(0, segment.indexOf("Villepreux - Les Clayes"));
        assertEquals(9, segment.indexOf("Clamart"));
    }

    @Test
    public void stationsOutsideTheSegmentAreNotFound() {
        LineSegment segment = LineNDirection.ALLER.getSegment();

        // Avant ma gare de départ, et au-delà de ma gare d'arrivée.
        assertEquals(-1, segment.indexOf("Paris Montparnasse"));
        assertEquals(-1, segment.indexOf("Vanves - Malakoff"));
        assertEquals(-1, segment.indexOf("Plaisir - Grignon"));
        assertEquals(-1, segment.indexOf("Rambouillet"));
    }

    @Test
    public void neighbouringStationsWithSharedWordsDoNotCollide() {
        LineSegment segment = LineNDirection.ALLER.getSegment();

        // "Plaisir - Les Clayes" ne doit pas être pris pour "Villepreux - Les Clayes".
        assertEquals(-1, segment.indexOf("Plaisir - Les Clayes"));
    }

    @Test
    public void aPairOutsideTheParisMantesAxisGivesNoSegment() {
        assertTrue(LineSegment.between("Clamart", "Rambouillet").isEmpty());
        assertTrue(LineSegment.between("Clamart", "Clamart").isEmpty());
    }
}
