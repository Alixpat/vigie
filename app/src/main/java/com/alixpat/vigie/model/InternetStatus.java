package com.alixpat.vigie.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class InternetStatus {

    private String type;
    private String name;
    private String host;
    private String status;
    private Double latency_ms;
    private String last_downtime_start;
    private String last_downtime_end;
    private Double last_downtime_duration_minutes;
    private transient long lastUpdate;

    private static final Gson gson = new Gson();

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public String getStatus() {
        return status;
    }

    public boolean isUp() {
        return "up".equalsIgnoreCase(status);
    }

    public Double getLatencyMs() {
        return latency_ms;
    }

    public String getLastDowntimeStart() {
        return last_downtime_start;
    }

    public String getLastDowntimeEnd() {
        return last_downtime_end;
    }

    public Double getLastDowntimeDurationMinutes() {
        return last_downtime_duration_minutes;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public static InternetStatus fromJson(String json) {
        try {
            InternetStatus s = gson.fromJson(json, InternetStatus.class);
            if (s != null && "internet_status".equals(s.type)) {
                s.setLastUpdate(System.currentTimeMillis());
                return s;
            }
            return null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
