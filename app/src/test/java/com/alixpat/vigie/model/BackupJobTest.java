package com.alixpat.vigie.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BackupJobTest {

    @Test
    public void parsesValidBackupStatus() {
        String json = "{\"type\":\"backup_status\",\"job\":\"nas-photos\","
                + "\"status\":\"success\",\"detail\":\"12.3 GiB\","
                + "\"last_run\":\"2026-03-12T03:00:00Z\"}";
        BackupJob job = BackupJob.fromJson(json);
        assertNotNull(job);
        assertEquals("nas-photos", job.getJob());
        assertEquals("success", job.getStatus());
        assertEquals("12.3 GiB", job.getDetail());
        assertEquals("2026-03-12T03:00:00Z", job.getLastRun());
        assertTrue(job.isSuccess());
        assertFalse(job.isFailed());
        assertFalse(job.isMissing());
        assertTrue(job.getLastUpdate() > 0);
        assertNull(job.getEmetteur());
    }

    @Test
    public void parsesEmetteurWhenPresent() {
        String json = "{\"type\":\"backup_status\",\"emetteur\":\"latitude\","
                + "\"job\":\"nas\",\"status\":\"success\"}";
        BackupJob job = BackupJob.fromJson(json);
        assertNotNull(job);
        assertEquals("latitude", job.getEmetteur());
    }

    @Test
    public void statusFlagsAreMutuallyExclusive() {
        BackupJob failed = BackupJob.fromJson(
                "{\"type\":\"backup_status\",\"job\":\"db\",\"status\":\"failed\"}");
        BackupJob missing = BackupJob.fromJson(
                "{\"type\":\"backup_status\",\"job\":\"db\",\"status\":\"missing\"}");
        assertTrue(failed.isFailed());
        assertFalse(failed.isSuccess());
        assertTrue(missing.isMissing());
        assertFalse(missing.isSuccess());
    }

    @Test
    public void statusComparisonIsCaseInsensitive() {
        BackupJob job = BackupJob.fromJson(
                "{\"type\":\"backup_status\",\"job\":\"db\",\"status\":\"SUCCESS\"}");
        assertTrue(job.isSuccess());
    }

    // Routage en cascade : refuse les autres types.

    @Test
    public void rejectsLanStatusPayload() {
        String json = "{\"type\":\"lan_status\",\"hostname\":\"pc\",\"status\":\"up\"}";
        assertNull(BackupJob.fromJson(json));
    }

    @Test
    public void rejectsInternetStatusPayload() {
        String json = "{\"type\":\"internet_status\",\"name\":\"wan\",\"status\":\"up\"}";
        assertNull(BackupJob.fromJson(json));
    }

    @Test
    public void rejectsAlertPayload() {
        String json = "{\"type\":\"alert\",\"title\":\"x\",\"message\":\"y\"}";
        assertNull(BackupJob.fromJson(json));
    }

    @Test
    public void rejectsPayloadWithoutType() {
        assertNull(BackupJob.fromJson("{\"job\":\"nas\",\"status\":\"success\"}"));
    }

    @Test
    public void rejectsMalformedJson() {
        assertNull(BackupJob.fromJson("not json"));
        assertNull(BackupJob.fromJson("{"));
    }
}
