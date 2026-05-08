package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class VigieMessageTest {

    @Test
    public void parsesValidAlert() {
        String json = "{\"type\":\"alert\",\"title\":\"Alerte drone\","
                + "\"message\":\"Drone détecté\",\"priority\":\"high\"}";
        VigieMessage msg = VigieMessage.fromJson(json);
        assertNotNull(msg);
        assertEquals("alert", msg.getType());
        assertEquals("Alerte drone", msg.getTitle());
        assertEquals("Drone détecté", msg.getMessage());
        assertEquals("high", msg.getPriority());
        assertTrue(msg.isHighPriority());
        assertTrue(msg.getReceivedAt() > 0);
    }

    @Test
    public void acceptsTitleOnly() {
        VigieMessage msg = VigieMessage.fromJson("{\"type\":\"info\",\"title\":\"x\"}");
        assertNotNull(msg);
    }

    @Test
    public void acceptsMessageOnly() {
        VigieMessage msg = VigieMessage.fromJson("{\"type\":\"info\",\"message\":\"x\"}");
        assertNotNull(msg);
    }

    @Test
    public void rejectsPayloadWithoutTitleOrMessage() {
        assertNull(VigieMessage.fromJson("{\"type\":\"info\",\"priority\":\"low\"}"));
        assertNull(VigieMessage.fromJson("{}"));
    }

    @Test
    public void isHighPriorityIsCaseInsensitive() {
        VigieMessage msg = VigieMessage.fromJson(
                "{\"type\":\"alert\",\"title\":\"x\",\"priority\":\"HIGH\"}");
        assertTrue(msg.isHighPriority());
    }

    @Test
    public void normalPriorityIsNotHigh() {
        VigieMessage msg = VigieMessage.fromJson(
                "{\"type\":\"info\",\"title\":\"x\",\"priority\":\"normal\"}");
        assertFalse(msg.isHighPriority());
    }

    @Test
    public void rejectsMalformedJson() {
        assertNull(VigieMessage.fromJson("not json"));
        assertNull(VigieMessage.fromJson("{"));
    }

    /**
     * Comportement de fallback INTENTIONNEL : VigieMessage.fromJson n'inspecte PAS
     * le champ "type". Tout JSON portant un "title" ou "message" est accepté, peu
     * importe son type. C'est ce qui rend le routage en cascade dans
     * MqttService.messageArrived sûr SEULEMENT si les parsers typés
     * (LanHost / BackupJob / InternetStatus) sont essayés AVANT.
     *
     * Ce test verrouille l'invariant. S'il échoue parce que VigieMessage commence
     * à filtrer sur "type", revoir l'ordre du routage dans MqttService.
     */
    @Test
    public void acceptsAnyTypeAsLongAsTitleOrMessageIsPresent_fallbackBehavior() {
        // Un payload "lan_status" vu via VigieMessage.fromJson : accepté car contient "title".
        // Dans MqttService, ce JSON serait d'abord intercepté par LanHost.fromJson.
        VigieMessage msg = VigieMessage.fromJson(
                "{\"type\":\"lan_status\",\"title\":\"unused\",\"hostname\":\"pc\"}");
        assertNotNull("VigieMessage est le fallback : il accepte tout JSON avec title/message", msg);
    }
}
