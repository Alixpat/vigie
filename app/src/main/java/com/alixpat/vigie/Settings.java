package com.alixpat.vigie;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Single SharedPreferences-backed settings store for the whole app.
 * Holds broker (IP/port/credentials) plus integration tokens (IDFM, TomTom).
 * Historically named {@code BrokerConfig} but it has long since outgrown that.
 * The underlying prefs file remains {@code vigie_prefs} — no data migration.
 */
public class Settings {

    private static final String PREFS_NAME = "vigie_prefs";
    private static final String KEY_BROKER_IP = "broker_ip";
    private static final String KEY_BROKER_PORT = "broker_port";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_IDFM_TOKEN = "idfm_token";
    private static final String KEY_TOMTOM_API_KEY = "tomtom_api_key";
    private static final String KEY_ALARM_ENABLED = "alarm_enabled";
    private static final String KEY_PINNED_TRAINS = "pinned_trains";

    private static final String DEFAULT_IP = "192.168.1.100";
    private static final int DEFAULT_PORT = 1883;

    private final SharedPreferences prefs;

    public Settings(Context context) {
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

    public String getTomTomApiKey() {
        return prefs.getString(KEY_TOMTOM_API_KEY, "");
    }

    public boolean hasTomTomApiKey() {
        String key = getTomTomApiKey();
        return key != null && !key.isEmpty();
    }

    public void saveTomTomApiKey(String apiKey) {
        prefs.edit()
                .putString(KEY_TOMTOM_API_KEY, apiKey)
                .apply();
    }

    /**
     * Les trains épinglés, encodés par
     * {@link com.alixpat.vigie.train.PinnedTrains#encode()}. Un Set de
     * SharedPreferences ne garde pas l'ordre : c'est l'instant d'épinglage porté
     * par chaque entrée qui le rétablit à la relecture.
     */
    public Set<String> getPinnedTrains() {
        return prefs.getStringSet(KEY_PINNED_TRAINS, Collections.<String>emptySet());
    }

    public void savePinnedTrains(Set<String> entries) {
        prefs.edit()
                // Copie défensive : SharedPreferences conserve la référence du Set
                // qu'on lui passe et le relit tel quel dans la même session.
                .putStringSet(KEY_PINNED_TRAINS, new HashSet<>(entries))
                .apply();
    }

    public boolean isAlarmEnabled() {
        return prefs.getBoolean(KEY_ALARM_ENABLED, false);
    }

    public void setAlarmEnabled(boolean enabled) {
        prefs.edit()
                .putBoolean(KEY_ALARM_ENABLED, enabled)
                .apply();
    }
}
