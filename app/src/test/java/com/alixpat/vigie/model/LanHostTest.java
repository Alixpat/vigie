package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LanHostTest {

    @Test
    public void parsesValidLanStatus() {
        String json = "{\"type\":\"lan_status\",\"hostname\":\"pc-bureau\","
                + "\"ip\":\"192.168.1.10\",\"status\":\"up\"}";
        LanHost host = LanHost.fromJson(json);
        assertNotNull(host);
        assertEquals("pc-bureau", host.getHostname());
        assertEquals("192.168.1.10", host.getIp());
        assertEquals("up", host.getStatus());
        assertTrue(host.isUp());
        assertTrue(host.getLastUpdate() > 0);
    }

    @Test
    public void isUpHandlesCaseAndDownValue() {
        LanHost up = LanHost.fromJson("{\"type\":\"lan_status\",\"status\":\"UP\"}");
        LanHost down = LanHost.fromJson("{\"type\":\"lan_status\",\"status\":\"down\"}");
        assertTrue(up.isUp());
        assertFalse(down.isUp());
    }

    // Routage en cascade : un payload pour un AUTRE type doit être rejeté.
    // Si ces tests passent au null, la cascade dans MqttService.messageArrived est sûre.

    @Test
    public void rejectsBackupStatusPayload() {
        String json = "{\"type\":\"backup_status\",\"job\":\"nas\",\"status\":\"success\"}";
        assertNull(LanHost.fromJson(json));
    }

    @Test
    public void rejectsInternetStatusPayload() {
        String json = "{\"type\":\"internet_status\",\"name\":\"wan\",\"status\":\"up\"}";
        assertNull(LanHost.fromJson(json));
    }

    @Test
    public void rejectsAlertPayload() {
        String json = "{\"type\":\"alert\",\"title\":\"x\",\"message\":\"y\"}";
        assertNull(LanHost.fromJson(json));
    }

    @Test
    public void rejectsPayloadWithoutType() {
        assertNull(LanHost.fromJson("{\"hostname\":\"pc\",\"status\":\"up\"}"));
    }

    @Test
    public void rejectsMalformedJson() {
        assertNull(LanHost.fromJson("not json"));
        assertNull(LanHost.fromJson("{"));
    }

    @Test
    public void emptyJsonObjectReturnsNull() {
        assertNull(LanHost.fromJson("{}"));
    }
}
