package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.alixpat.vigie.model.TrainIncident;

import org.junit.Test;

public class IncidentClassifierTest {

    // --- helpers ---

    @Test
    public void hasTravauxKeyword_matchesAllListedRoots() {
        assertTrue(IncidentClassifier.hasTravauxKeyword("Travaux nocturnes prévus"));
        assertTrue(IncidentClassifier.hasTravauxKeyword("Chantier de signalisation"));
        assertTrue(IncidentClassifier.hasTravauxKeyword("Maintenance programmée"));
        assertTrue(IncidentClassifier.hasTravauxKeyword("Intervention planifiée"));   // "planifi"
        assertTrue(IncidentClassifier.hasTravauxKeyword("Programme de modernisation")); // "programme"
        assertTrue(IncidentClassifier.hasTravauxKeyword("Modification prévue"));        // "prévu"
    }

    @Test
    public void hasPerturbationKeyword_matchesAllListedRoots() {
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Trafic interrompu"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Service perturbé"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Trafic ralenti"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Train supprimé"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Retard de 10 minutes"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Allongement du temps de parcours"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Itinéraire dévié"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("Régulation en cours"));
    }

    @Test
    public void hasBlockingKeyword_matchesAllListedRoots() {
        assertTrue(IncidentClassifier.hasBlockingKeyword("Trafic interrompu"));
        assertTrue(IncidentClassifier.hasBlockingKeyword("Train supprimé"));
        assertTrue(IncidentClassifier.hasBlockingKeyword("Voyageurs immobilisés")); // "immobilis"
    }

    @Test
    public void helpersAreCaseInsensitive() {
        assertTrue(IncidentClassifier.hasTravauxKeyword("TRAVAUX MAJEURS"));
        assertTrue(IncidentClassifier.hasPerturbationKeyword("INTERROMPU"));
        assertTrue(IncidentClassifier.hasBlockingKeyword("Train SUPPRIMÉ"));
    }

    @Test
    public void helpersHandleNullAndEmpty() {
        assertFalse(IncidentClassifier.hasTravauxKeyword(null));
        assertFalse(IncidentClassifier.hasTravauxKeyword(""));
        assertFalse(IncidentClassifier.hasPerturbationKeyword(null));
        assertFalse(IncidentClassifier.hasPerturbationKeyword(""));
        assertFalse(IncidentClassifier.hasBlockingKeyword(null));
        assertFalse(IncidentClassifier.hasBlockingKeyword(""));
    }

    @Test
    public void neutralTextMatchesNothing() {
        String text = "Bonne journée à tous les voyageurs.";
        assertFalse(IncidentClassifier.hasTravauxKeyword(text));
        assertFalse(IncidentClassifier.hasPerturbationKeyword(text));
        assertFalse(IncidentClassifier.hasBlockingKeyword(text));
    }

    // --- classifyMessage : reproduit la logique du call site general-message ---

    @Test
    public void perturbationChannel_withBlockingText_isBlocking() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Trafic interrompu entre Paris et Versailles", "Perturbations");
        assertEquals(TrainIncident.TYPE_PERTURBATION, c.type);
        assertEquals("blocking", c.severity);
    }

    @Test
    public void perturbationChannel_withoutBlockingText_isDelays() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Trafic ralenti suite à un incident", "Perturbations");
        assertEquals(TrainIncident.TYPE_PERTURBATION, c.type);
        assertEquals("delays", c.severity);
    }

    @Test
    public void neutralChannel_withPerturbationKeyword_stillClassifiedAsPerturbation() {
        // L'absence de canal ne doit pas masquer une perturbation détectée par mots-clés.
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Train supprimé en gare de Clamart", "Info");
        assertEquals(TrainIncident.TYPE_PERTURBATION, c.type);
        assertEquals("blocking", c.severity); // "supprimé" est aussi blocking
    }

    @Test
    public void travauxMessage_outsidePerturbationChannel_isTravaux() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Travaux de maintenance programmée ce week-end", "Info");
        assertEquals(TrainIncident.TYPE_TRAVAUX, c.type);
        assertEquals("information", c.severity);
    }

    @Test
    public void neutralMessage_inNeutralChannel_isInformation() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Bienvenue à bord du train pour Paris.", "Info");
        assertEquals(TrainIncident.TYPE_INFORMATION, c.type);
        assertEquals("information", c.severity);
    }

    @Test
    public void perturbationKeywordOverridesTravauxInChannel() {
        // Si le texte contient à la fois travaux et perturbation, perturbation gagne.
        // C'est le comportement actuel (channel/perturbation sont vérifiés en premier).
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Travaux entraînant l'allongement des temps de parcours", "Info");
        assertEquals(TrainIncident.TYPE_PERTURBATION, c.type);
        assertEquals("delays", c.severity);
    }

    @Test
    public void channelMatchIsCaseInsensitive() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Trafic ralenti", "PERTURBATIONS Régionales");
        assertEquals(TrainIncident.TYPE_PERTURBATION, c.type);
    }

    @Test
    public void nullChannelIsTreatedAsEmpty() {
        IncidentClassifier.Classification c = IncidentClassifier.classifyMessage(
                "Bonne journée.", null);
        assertEquals(TrainIncident.TYPE_INFORMATION, c.type);
        assertEquals("information", c.severity);
    }
}
