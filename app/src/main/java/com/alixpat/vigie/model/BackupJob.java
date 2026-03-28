package com.alixpat.vigie.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class BackupJob {

    private String type;
    private String job;
    private String status;
    private String detail;
    private String last_run;
    private transient long lastUpdate;

    private static final Gson gson = new Gson();

    public String getType() {
        return type;
    }

    public String getJob() {
        return job;
    }

    public String getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }

    public String getLastRun() {
        return last_run;
    }

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    public boolean isMissing() {
        return "missing".equalsIgnoreCase(status);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public static BackupJob fromJson(String json) {
        try {
            BackupJob backup = gson.fromJson(json, BackupJob.class);
            if (backup != null && "backup_status".equals(backup.type)) {
                backup.setLastUpdate(System.currentTimeMillis());
                return backup;
            }
            return null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
