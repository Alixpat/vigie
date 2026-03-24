package com.alixpat.vigie.model;

public class TrainIncident {

    public static final String TYPE_PERTURBATION = "perturbation";
    public static final String TYPE_INFORMATION = "information";
    public static final String TYPE_TRAVAUX = "travaux";

    private final String title;
    private final String message;
    private final String severity;
    private final String cause;
    private final String startTime;
    private final String endTime;
    private final String type;

    public TrainIncident(String title, String message, String severity, String cause,
                         String startTime, String endTime) {
        this(title, message, severity, cause, startTime, endTime, TYPE_PERTURBATION);
    }

    public TrainIncident(String title, String message, String severity, String cause,
                         String startTime, String endTime, String type) {
        this.title = title;
        this.message = message;
        this.severity = severity;
        this.cause = cause;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type != null ? type : TYPE_PERTURBATION;
    }

    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getSeverity() { return severity; }
    public String getCause() { return cause; }
    public String getStartTime() { return startTime; }
    public String getEndTime() { return endTime; }
    public String getType() { return type; }

    public boolean isPerturbation() {
        return TYPE_PERTURBATION.equals(type);
    }

    public boolean isTravaux() {
        return TYPE_TRAVAUX.equals(type);
    }

    public boolean isInformation() {
        return TYPE_INFORMATION.equals(type);
    }

    public String getSeverityEmoji() {
        if (isTravaux()) return "\uD83D\uDEA7"; // construction sign
        if (isInformation()) return "\u2139\uFE0F"; // info
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
        if (isTravaux()) return "Travaux";
        if (isInformation()) return "Information";
        if (severity == null) return "Perturbation";
        switch (severity.toLowerCase()) {
            case "blocking": return "Interrompu";
            case "delays": return "Retards";
            case "reduced_service": return "Service r\u00e9duit";
            case "information": return "Information";
            default: return "Perturbation";
        }
    }

    public int getSeverityColor() {
        if (isTravaux()) return 0xFF9C27B0; // Purple for planned works
        if (isInformation()) return 0xFF2196F3; // Blue
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
