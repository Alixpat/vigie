package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LineNDirectionTest {

    @Test
    public void allerHasClamartOriginAndVillepreuxDestination() {
        assertEquals("Clamart", LineNDirection.ALLER.getOriginName());
        assertEquals("Villepreux", LineNDirection.ALLER.getDestinationName());
        assertEquals("Aller", LineNDirection.ALLER.getLabel());
    }

    @Test
    public void retourHasVillepreuxOriginAndClamartDestination() {
        assertEquals("Villepreux", LineNDirection.RETOUR.getOriginName());
        assertEquals("Clamart", LineNDirection.RETOUR.getDestinationName());
        assertEquals("Retour", LineNDirection.RETOUR.getLabel());
    }

    @Test
    public void allerAndRetourSwapStopRefs() {
        // L'origine de l'aller doit être la destination du retour, et vice-versa.
        assertEquals(LineNDirection.ALLER.getOriginStopRef(),
                LineNDirection.RETOUR.getDestinationStopRef());
        assertEquals(LineNDirection.ALLER.getDestinationStopRef(),
                LineNDirection.RETOUR.getOriginStopRef());
        assertNotEquals(LineNDirection.ALLER.getOriginStopRef(),
                LineNDirection.ALLER.getDestinationStopRef());
    }

    // --- matchesDestination : sens Aller (vers Villepreux/Plaisir/Dreux/Mantes) ---

    @Test
    public void allerMatchesVillepreuxBound() {
        assertTrue(LineNDirection.ALLER.matchesDestination("Villepreux Les Clayes"));
        assertTrue(LineNDirection.ALLER.matchesDestination("Plaisir-Grignon"));
        assertTrue(LineNDirection.ALLER.matchesDestination("Dreux"));
        assertTrue(LineNDirection.ALLER.matchesDestination("Mantes-la-Jolie"));
    }

    @Test
    public void allerRejectsParisBound() {
        assertFalse(LineNDirection.ALLER.matchesDestination("Paris-Montparnasse"));
        assertFalse(LineNDirection.ALLER.matchesDestination("Versailles-Chantiers"));
        assertFalse(LineNDirection.ALLER.matchesDestination("Sèvres"));
    }

    // --- matchesDestination : sens Retour (vers Paris/Montparnasse/Clamart/...) ---

    @Test
    public void retourMatchesParisBound() {
        assertTrue(LineNDirection.RETOUR.matchesDestination("Paris-Montparnasse"));
        assertTrue(LineNDirection.RETOUR.matchesDestination("Montparnasse-3 Vaugirard"));
        assertTrue(LineNDirection.RETOUR.matchesDestination("Versailles-Chantiers"));
        assertTrue(LineNDirection.RETOUR.matchesDestination("Sèvres-Ville-d'Avray"));
        // Variante sans accent : "sevres" est aussi listé.
        assertTrue(LineNDirection.RETOUR.matchesDestination("Sevres"));
    }

    @Test
    public void retourRejectsVillepreuxBound() {
        assertFalse(LineNDirection.RETOUR.matchesDestination("Villepreux"));
        assertFalse(LineNDirection.RETOUR.matchesDestination("Plaisir"));
    }

    // --- robustesse ---

    @Test
    public void matchingIsCaseInsensitive() {
        assertTrue(LineNDirection.ALLER.matchesDestination("VILLEPREUX"));
        assertTrue(LineNDirection.RETOUR.matchesDestination("PARIS-MONTPARNASSE"));
    }

    @Test
    public void nullOrEmptyDestinationIsNotMatched() {
        assertFalse(LineNDirection.ALLER.matchesDestination(null));
        assertFalse(LineNDirection.ALLER.matchesDestination(""));
        assertFalse(LineNDirection.RETOUR.matchesDestination(null));
        assertFalse(LineNDirection.RETOUR.matchesDestination(""));
    }

    @Test
    public void unrelatedDestinationDoesNotMatchEitherDirection() {
        // Un train dont le terminus annoncé n'est sur aucune des deux listes
        // (ex. terminus partiel) n'est gardé par aucune direction.
        assertFalse(LineNDirection.ALLER.matchesDestination("Argenteuil"));
        assertFalse(LineNDirection.RETOUR.matchesDestination("Argenteuil"));
    }
}
