package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.alixpat.vigie.model.LineNStation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reconnaissance d'une gare du plan à partir du nom renvoyé par l'API.
 *
 * <p>Le cas qui motive ces tests : les noms de la ligne N partagent des mots
 * entiers (« Rive Gauche », « Les Clayes », « Plaisir »). Une recherche par
 * mot-clé y place un train à la mauvaise gare sans rien signaler.</p>
 */
public class StationMatchTest {

    private static List<String> allStations() {
        List<String> keys = new ArrayList<>();
        for (LineNStation station : LineNStation.getAll()) {
            keys.add(LineNStation.normalize(station.getName()));
        }
        return keys;
    }

    @Test
    public void trouveLaGareExacte() {
        assertEquals("clamart", StationMatch.bestMatch("Clamart", allStations()));
        assertEquals("plaisir grignon",
                StationMatch.bestMatch("Plaisir - Grignon", allStations()));
    }

    @Test
    public void tolereAccentsEtTirets() {
        assertEquals("epone mezieres",
                StationMatch.bestMatch("Épône - Mézières", allStations()));
        assertEquals("vanves malakoff",
                StationMatch.bestMatch("Gare de Vanves - Malakoff", allStations()));
    }

    @Test
    public void neConfondPasDeuxGaresQuiPartagentDesMots() {
        // Deux "Rive Gauche" sur la ligne : chacune doit rester chez elle.
        assertEquals("chaville rive gauche",
                StationMatch.bestMatch("Chaville Rive Gauche", allStations()));
        assertEquals("sevres rive gauche",
                StationMatch.bestMatch("Sèvres Rive Gauche", allStations()));
    }

    @Test
    public void refuseUneCorrespondanceAmbigue() {
        // "Rive Gauche" seul ou "Plaisir" seul désignent deux gares : on préfère
        // ne pas placer le train plutôt que de le placer au mauvais endroit.
        assertNull(StationMatch.bestMatch("Rive Gauche", allStations()));
        assertNull(StationMatch.bestMatch("Plaisir", allStations()));
    }

    @Test
    public void accepteUnNomPlusCourtOuPlusLongQuandIlEstSansAmbiguite() {
        assertEquals("villepreux les clayes",
                StationMatch.bestMatch("Villepreux", allStations()));
        assertEquals("saint cyr",
                StationMatch.bestMatch("Saint-Cyr-l'École", allStations()));
        assertEquals("paris montparnasse",
                StationMatch.bestMatch("Montparnasse", allStations()));
    }

    @Test
    public void renvoieNullQuandRienNeCorrespond() {
        // Arrêt dont le nom n'a pas encore été résolu : aucune gare ne convient.
        assertNull(StationMatch.bestMatch("Arrêt 43111", allStations()));
        assertNull(StationMatch.bestMatch("", allStations()));
        assertNull(StationMatch.bestMatch(null, allStations()));
        assertNull(StationMatch.bestMatch("Clamart", null));
        assertNull(StationMatch.bestMatch("Clamart", Arrays.asList("meudon", "bellevue")));
    }
}
