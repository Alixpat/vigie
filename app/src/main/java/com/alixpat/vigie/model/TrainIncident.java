package com.alixpat.vigie.model;

public class TrainIncident {

    private final String title;
    private final String message;
    private final String severity;
    private final String cause;
    private final String startTime;
    private final String endTime;

    public TrainIncident(String title, String message, String severity, String cause,
                         String startTime, String endTime) {
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.cause = cause;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getSeverity() { return severity; }
    public String getCause() { return cause; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }

    public String getSeverityEmoji() {
        if (severity == null) return "\u26A0\uFE0F";
        switch (severity.toLowerCase()) {
            case "blocking": return "\uD83D\uDED1";
            case "delays": return "\u23F0";
            case "reduced_service": return "\u26A0\uFE0F";
            case "information": return "\u2139\uFE0F";
            default: return "\u26A0\uFE0F";
        }
    }

    public String getSeverityLabel() {
        if (severity == null) return "Perturbation";
        switch (severity.toLowerCase()) {
            case "blocking": return "Interrompu";
            case "delays": return "Retards";
            case "reduced_service": return "Service réduit";
            case "information": return "Information";
            default: return "Perturbation";
        }
    }

    public int getSeverityColor() {
        if (severity == null) return 0xFFFF9800;
        switch (severity.toLowerCase()) {
            case "blocking": return 0xFFF44336;
            case "delays": return 0xFFFF9800;
            case "reduced_service": return 0xFFFF9800;
            case "information": return 0xFF2196F3;
            default: return 0xFFFF9800;
        }
    }
}
