package com.alixpat.vigie;

import android.content.Context;
import android.content.SharedPreferences;

public class BrokerConfig {

    private static final String PREFS_NAME = "vigie_prefs";
    private static final String KEY_BROKER_IP = "broker_ip";
    private static final String KEY_BROKER_PORT = "broker_port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IDFM_TOKEN = "idfm_token";

    private static final String DEFAULT_IP = "192.168.1.100";
    private static final int DEFAULT_PORT = 1883;

    private final SharedPreferences prefs;

    public BrokerConfig(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public String getBrokerIp() {
        return prefs.getString(KEY_BROKER_IP, DEFAULT_IP);
    }

    public int getBrokerPort() {
        return prefs.getInt(KEY_BROKER_PORT, DEFAULT_PORT);
    }

    public String getBrokerUri() {
        return "tcp://" + getBrokerIp() + ":" + getBrokerPort();
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public String getPassword() {
        return prefs.getString(KEY_PASSWORD, "");
    }

    public boolean hasCredentials() {
        String user = getUsername();
        return user != null && !user.isEmpty();
    }

    public String getIdfmToken() {
        return prefs.getString(KEY_IDFM_TOKEN, "");
    }

    public boolean hasIdfmToken() {
        String token = getIdfmToken();
        return token != null && !token.isEmpty();
    }

    public void save(String ip, int port, String username, String password) {
        prefs.edit()
                .putString(KEY_BROKER_IP, ip)
                .putInt(KEY_BROKER_PORT, port)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply();
    }

    public void saveIdfmToken(String token) {
        prefs.edit()
                .putString(KEY_IDFM_TOKEN, token)
                .apply();
    }
}
