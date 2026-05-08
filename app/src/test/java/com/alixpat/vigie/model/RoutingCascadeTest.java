package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Vérifie l'invariant de routage utilisé dans MqttService.messageArrived :
 * pour un payload entrant, EXACTEMENT UN parser typé doit l'accepter
 * (LanHost OU BackupJob OU InternetStatus), et le fallback VigieMessage
 * doit prendre le relais pour tout le reste.
 *
 * Si un de ces tests casse, le bug est silencieux en prod : un message MQTT
 * sera mal-classé et atterrira dans le mauvais cache / la mauvaise UI.
 */
public class RoutingCascadeTest {

    private static int countTypedAccepts(String json) {
        int n = 0;
        if (LanHost.fromJson(json) != null) n++;
        if (BackupJob.fromJson(json) != null) n++;
        if (InternetStatus.fromJson(json) != null) n++;
        return n;
    }

    @Test
    public void lanStatusIsAcceptedByExactlyOneTypedParser() {
        String json = "{\"type\":\"lan_status\",\"hostname\":\"pc\",\"ip\":\"1.2.3.4\",\"status\":\"up\"}";
        assertEquals(1, countTypedAccepts(json));
        assertNotNull(LanHost.fromJson(json));
    }

    @Test
    public void backupStatusIsAcceptedByExactlyOneTypedParser() {
        String json = "{\"type\":\"backup_status\",\"job\":\"db\",\"status\":\"success\"}";
        assertEquals(1, countTypedAccepts(json));
        assertNotNull(BackupJob.fromJson(json));
    }

    @Test
    public void internetStatusIsAcceptedByExactlyOneTypedParser() {
        String json = "{\"type\":\"internet_status\",\"name\":\"wan\",\"host\":\"1.1.1.1\",\"status\":\"up\"}";
        assertEquals(1, countTypedAccepts(json));
        assertNotNull(InternetStatus.fromJson(json));
    }

    @Test
    public void alertIsRejectedByAllTypedParsersAndFallsBackToVigieMessage() {
        String json = "{\"type\":\"alert\",\"title\":\"x\",\"message\":\"y\",\"priority\":\"high\"}";
        assertEquals(0, countTypedAccepts(json));
        assertNotNull(VigieMessage.fromJson(json));
    }

    @Test
    public void infoIsRejectedByAllTypedParsersAndFallsBackToVigieMessage() {
        String json = "{\"type\":\"info\",\"title\":\"Système\",\"message\":\"OK\"}";
        assertEquals(0, countTypedAccepts(json));
        assertNotNull(VigieMessage.fromJson(json));
    }

    @Test
    public void warningIsRejectedByAllTypedParsersAndFallsBackToVigieMessage() {
        String json = "{\"type\":\"warning\",\"title\":\"Batterie\",\"message\":\"15%\"}";
        assertEquals(0, countTypedAccepts(json));
        assertNotNull(VigieMessage.fromJson(json));
    }

    @Test
    public void unknownTypeWithoutTitleOrMessageIsDroppedEverywhere() {
        // Pas de parser typé qui matche, et VigieMessage refuse aussi (ni title ni message).
        // Dans MqttService, le payload est silencieusement ignoré.
        String json = "{\"type\":\"random_thing\",\"foo\":\"bar\"}";
        assertEquals(0, countTypedAccepts(json));
        assertNull(VigieMessage.fromJson(json));
    }

    @Test
    public void malformedPayloadIsDroppedEverywhere() {
        String json = "not even json";
        assertEquals(0, countTypedAccepts(json));
        assertNull(VigieMessage.fromJson(json));
    }
}
