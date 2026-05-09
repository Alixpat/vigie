package com.alixpat.vigie.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.util.Map;

public class SensorStatus {

    private String type;
    private String emetteur;
    private String name;
    private String kind;
    private String device_id;
    private Map<String, Object> decoded;
    private Integer rssi;
    private Double snr;
    private Integer f_cnt;
    private Double battery_percent;
    private String received_at;
    private transient long lastUpdate;

    private static final Gson gson = new Gson();

    public String getType() {
        return type;
    }

    public String getEmetteur() {
        return emetteur;
    }

    public String getName() {
        return name;
    }

    public String getKind() {
        return kind;
    }

    public String getDeviceId() {
        return device_id;
    }

    public Map<String, Object> getDecoded() {
        return decoded;
    }

    public Integer getRssi() {
        return rssi;
    }

    public Double getSnr() {
        return snr;
    }

    public Integer getFCnt() {
        return f_cnt;
    }

    public Double getBatteryPercent() {
        return battery_percent;
    }

    public String getReceivedAt() {
        return received_at;
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public static SensorStatus fromJson(String json) {
        try {
            SensorStatus s = gson.fromJson(json, SensorStatus.class);
            if (s != null && "sensor_status".equals(s.type)) {
                s.setLastUpdate(System.currentTimeMillis());
                return s;
            }
            return null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
