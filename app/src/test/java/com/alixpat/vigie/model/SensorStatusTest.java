package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SensorStatusTest {

    @Test
    public void parsesValidSensorStatus() {
        String json = "{\"type\":\"sensor_status\",\"emetteur\":\"pidesk\","
                + "\"name\":\"porte-entree\",\"kind\":\"door\","
                + "\"device_id\":\"eui-a84041ffb184f8f4\","
                + "\"decoded\":{\"ALARM\":0,\"BAT_V\":2.958,\"DOOR_OPEN_STATUS\":1},"
                + "\"rssi\":-75,\"snr\":10,\"f_cnt\":39,"
                + "\"battery_percent\":39.13,"
                + "\"received_at\":\"2026-05-09T10:40:47.319Z\"}";
        SensorStatus s = SensorStatus.fromJson(json);
        assertNotNull(s);
        assertEquals("pidesk", s.getEmetteur());
        assertEquals("porte-entree", s.getName());
        assertEquals("door", s.getKind());
        assertEquals("eui-a84041ffb184f8f4", s.getDeviceId());
        assertEquals(Integer.valueOf(-75), s.getRssi());
        assertEquals(Double.valueOf(10.0), s.getSnr());
        assertEquals(Integer.valueOf(39), s.getFCnt());
        assertEquals(Double.valueOf(39.13), s.getBatteryPercent());
        assertEquals("2026-05-09T10:40:47.319Z", s.getReceivedAt());
        assertTrue(s.getLastUpdate() > 0);

        // decoded est désérialisé en Map (Gson : numbers en Double)
        assertNotNull(s.getDecoded());
        assertEquals(Double.valueOf(1.0), s.getDecoded().get("DOOR_OPEN_STATUS"));
        assertEquals(Double.valueOf(2.958), s.getDecoded().get("BAT_V"));
    }

    @Test
    public void kindIsOptional() {
        String json = "{\"type\":\"sensor_status\",\"name\":\"capteur-x\"}";
        SensorStatus s = SensorStatus.fromJson(json);
        assertNotNull(s);
        assertNull(s.getKind());
    }

    @Test
    public void allOptionalFieldsTolerateAbsence() {
        // Le payload minimal viable : juste le type
        String json = "{\"type\":\"sensor_status\"}";
        SensorStatus s = SensorStatus.fromJson(json);
        assertNotNull(s);
        assertNull(s.getName());
        assertNull(s.getDecoded());
        assertNull(s.getRssi());
        assertNull(s.getSnr());
        assertNull(s.getBatteryPercent());
        assertNull(s.getReceivedAt());
    }

    // Routage en cascade : refuse les autres types.

    @Test
    public void rejectsLanStatusPayload() {
        assertNull(SensorStatus.fromJson(
                "{\"type\":\"lan_status\",\"hostname\":\"pc\",\"status\":\"up\"}"));
    }

    @Test
    public void rejectsBackupStatusPayload() {
        assertNull(SensorStatus.fromJson(
                "{\"type\":\"backup_status\",\"job\":\"db\",\"status\":\"success\"}"));
    }

    @Test
    public void rejectsInternetStatusPayload() {
        assertNull(SensorStatus.fromJson(
                "{\"type\":\"internet_status\",\"name\":\"wan\",\"status\":\"up\"}"));
    }

    @Test
    public void rejectsAlertPayload() {
        assertNull(SensorStatus.fromJson("{\"type\":\"alert\",\"title\":\"x\"}"));
    }

    @Test
    public void rejectsPayloadWithoutType() {
        assertNull(SensorStatus.fromJson("{\"name\":\"capteur\"}"));
    }

    @Test
    public void rejectsMalformedJson() {
        assertNull(SensorStatus.fromJson("not json"));
        assertNull(SensorStatus.fromJson("{"));
    }
}
