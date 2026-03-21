package com.alixpat.vigie.model;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

public class VigieMessage {

    private String type;
    private String title;
    private String message;
    private String priority;

    private static final Gson gson = new Gson();

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getPriority() {
        return priority;
    }

    public boolean isHighPriority() {
        return "high".equalsIgnoreCase(priority);
    }

    /**
     * Parse un payload JSON en VigieMessage.
     * Retourne null si le JSON est invalide.
     */
    public static VigieMessage fromJson(String json) {
        try {
            return gson.fromJson(json, VigieMessage.class);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "[" + type + "] " + title + " : " + message;
    }
}
