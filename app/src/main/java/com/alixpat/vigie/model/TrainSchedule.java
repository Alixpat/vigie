package com.alixpat.vigie.model;

public class TrainSchedule {

    private final String destination;
    private final String aimedDepartureTime;
    private final String expectedDepartureTime;
    private final String arrivalTime;
    private final String departureStatus;
    private final String platformName;
    private final int delayMinutes;

    public TrainSchedule(String destination, String aimedDepartureTime,
                         String expectedDepartureTime, String arrivalTime,
                         String departureStatus, String platformName,
                         int delayMinutes) {
        this.destination = destination;
        this.aimedDepartureTime = aimedDepartureTime;
        this.expectedDepartureTime = expectedDepartureTime;
        this.arrivalTime = arrivalTime != null ? arrivalTime : "";
        this.departureStatus = departureStatus;
        this.platformName = platformName;
        this.delayMinutes = delayMinutes;
    }

    public String getDestination() { return destination; }
    public String getAimedDepartureTime() { return aimedDepartureTime; }
    public String getExpectedDepartureTime() { return expectedDepartureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public String getDepartureStatus() { return departureStatus; }
    public String getPlatformName() { return platformName; }
    public int getDelayMinutes() { return delayMinutes; }

    public boolean isOnTime() {
        return delayMinutes == 0 && !"cancelled".equalsIgnoreCase(departureStatus);
    }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(departureStatus);
    }

    public boolean isDelayed() {
        return delayMinutes > 0 && !isCancelled();
    }

    public String getStatusLabel() {
        if (isCancelled()) return "Supprimé";
        if (isDelayed()) return "+" + delayMinutes + " min";
        return "À l'heure";
    }

    public int getStatusColor() {
        if (isCancelled()) return 0xFFF44336;
        if (isDelayed()) return 0xFFFF9800;
        return 0xFF4CAF50;
    }

    public String getStatusEmoji() {
        if (isCancelled()) return "\u274C";
        if (isDelayed()) return "\u23F0";
        return "\u2705";
    }
}
