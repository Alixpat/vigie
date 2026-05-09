package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InternetStatusTest {

    @Test
    public void parsesValidInternetStatus() {
        String json = "{\"type\":\"internet_status\",\"name\":\"Wan principal\","
                + "\"host\":\"8.8.8.8\",\"status\":\"up\",\"latency_ms\":12.4,"
                + "\"last_downtime_start\":\"2026-03-12T08:14:03Z\","
                + "\"last_downtime_end\":\"2026-03-12T08:15:42Z\","
                + "\"last_downtime_duration_minutes\":1.65}";
        InternetStatus s = InternetStatus.fromJson(json);
        assertNotNull(s);
        assertEquals("Wan principal", s.getName());
        assertEquals("8.8.8.8", s.getHost());
        assertEquals("up", s.getStatus());
        assertTrue(s.isUp());
        assertEquals(Double.valueOf(12.4), s.getLatencyMs());
        assertEquals("2026-03-12T08:14:03Z", s.getLastDowntimeStart());
        assertEquals("2026-03-12T08:15:42Z", s.getLastDowntimeEnd());
        assertEquals(Double.valueOf(1.65), s.getLastDowntimeDurationMinutes());
        assertTrue(s.getLastUpdate() > 0);
        assertNull(s.getEmetteur());
    }

    @Test
    public void parsesEmetteurWhenPresent() {
        String json = "{\"type\":\"internet_status\",\"emetteur\":\"zoe-laptop\","
                + "\"name\":\"wan\",\"status\":\"up\"}";
        InternetStatus s = InternetStatus.fromJson(json);
        assertNotNull(s);
        assertEquals("zoe-laptop", s.getEmetteur());
    }

    @Test
    public void downtimeFieldsAreOptional() {
        // Champs downtime absents : doit rester null sans planter
        InternetStatus s = InternetStatus.fromJson(
                "{\"type\":\"internet_status\",\"name\":\"wan\",\"host\":\"1.1.1.1\","
                        + "\"status\":\"up\",\"latency_ms\":8.0}");
        assertNotNull(s);
        assertNull(s.getLastDowntimeStart());
        assertNull(s.getLastDowntimeEnd());
        assertNull(s.getLastDowntimeDurationMinutes());
    }

    @Test
    public void isUpHandlesDownStatus() {
        InternetStatus down = InternetStatus.fromJson(
                "{\"type\":\"internet_status\",\"name\":\"wan\",\"status\":\"down\"}");
        assertFalse(down.isUp());
    }

    // Routage en cascade : refuse les autres types.

    @Test
    public void rejectsLanStatusPayload() {
        String json = "{\"type\":\"lan_status\",\"hostname\":\"pc\",\"status\":\"up\"}";
        assertNull(InternetStatus.fromJson(json));
    }

    @Test
    public void rejectsBackupStatusPayload() {
        String json = "{\"type\":\"backup_status\",\"job\":\"nas\",\"status\":\"success\"}";
        assertNull(InternetStatus.fromJson(json));
    }

    @Test
    public void rejectsAlertPayload() {
        String json = "{\"type\":\"alert\",\"title\":\"x\",\"message\":\"y\"}";
        assertNull(InternetStatus.fromJson(json));
    }

    @Test
    public void rejectsPayloadWithoutType() {
        assertNull(InternetStatus.fromJson("{\"name\":\"wan\",\"status\":\"up\"}"));
    }

    @Test
    public void rejectsMalformedJson() {
        assertNull(InternetStatus.fromJson("not json"));
        assertNull(InternetStatus.fromJson("{"));
    }
}
