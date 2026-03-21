package com.alixpat.vigie.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class LanHost {

    private String type;
    private String hostname;
    private String ip;
    private String status;
    private transient long lastUpdate;

    private static final Gson gson = new Gson();

    public String getType() {
        return type;
    }

    public String getHostname() {
        return hostname;
    }

    public String getIp() {
        return ip;
    }

    public String getStatus() {
        return status;
    }

    public boolean isUp() {
        return "up".equalsIgnoreCase(status);
    }

    public long getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public static LanHost fromJson(String json) {
        try {
            LanHost host = gson.fromJson(json, LanHost.class);
            if (host != null && "lan_status".equals(host.type)) {
                host.setLastUpdate(System.currentTimeMillis());
                return host;
            }
            return null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
