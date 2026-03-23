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
            VigieMessage msg = gson.fromJson(json, VigieMessage.class);
            // Valider que le message contient au moins un titre ou un message
            if (msg != null && (msg.title != null || msg.message != null)) {
                return msg;
            }
            return null;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return "[" + type + "] " + title + " : " + message;
    }
}
