package com.alixpat.vigie.model;

/**
 * Représente un arrêt dans le parcours d'un train,
 * avec les horaires prévus et estimés.
 */
public class TrainStop {

    private final String stopName;
    private final long aimedArrivalMillis;
    private final long expectedArrivalMillis;
    private final long aimedDepartureMillis;
    private final long expectedDepartureMillis;
    private final String platformName;
    private final boolean isDeparture;
    private final boolean isArrival;

    public TrainStop(String stopName,
                     long aimedArrivalMillis, long expectedArrivalMillis,
                     long aimedDepartureMillis, long expectedDepartureMillis,
                     String platformName,
                     boolean isDeparture, boolean isArrival) {
        this.stopName = stopName;
        this.aimedArrivalMillis = aimedArrivalMillis;
        this.expectedArrivalMillis = expectedArrivalMillis;
        this.aimedDepartureMillis = aimedDepartureMillis;
        this.expectedDepartureMillis = expectedDepartureMillis;
        this.platformName = platformName;
        this.isDeparture = isDeparture;
        this.isArrival = isArrival;
    }

    public String getStopName() { return stopName; }
    public long getAimedArrivalMillis() { return aimedArrivalMillis; }
    public long getExpectedArrivalMillis() { return expectedArrivalMillis; }
    public long getAimedDepartureMillis() { return aimedDepartureMillis; }
    public long getExpectedDepartureMillis() { return expectedDepartureMillis; }
    public String getPlatformName() { return platformName; }
    public boolean isDeparture() { return isDeparture; }
    public boolean isArrival() { return isArrival; }

    /**
     * Retourne le meilleur timestamp de passage à cet arrêt.
     * Priorité : expectedDeparture > aimedDeparture > expectedArrival > aimedArrival
     */
    public long getBestTimeMillis() {
        if (expectedDepartureMillis > 0) return expectedDepartureMillis;
        if (aimedDepartureMillis > 0) return aimedDepartureMillis;
        if (expectedArrivalMillis > 0) return expectedArrivalMillis;
        return aimedArrivalMillis;
    }

    /**
     * Retourne le meilleur timestamp d'arrivée à cet arrêt.
     */
    public long getBestArrivalMillis() {
        if (expectedArrivalMillis > 0) return expectedArrivalMillis;
        if (aimedArrivalMillis > 0) return aimedArrivalMillis;
        if (expectedDepartureMillis > 0) return expectedDepartureMillis;
        return aimedDepartureMillis;
    }

    /**
     * Détermine le statut de cet arrêt par rapport à l'instant présent.
     */
    public StopStatus getStatus() {
        long now = System.currentTimeMillis();
        long arrivalTime = getBestArrivalMillis();
        long departureTime = getBestTimeMillis();

        if (departureTime > 0 && now > departureTime) {
            return StopStatus.PASSED;
        }
        if (arrivalTime > 0 && now >= arrivalTime && (departureTime <= 0 || now <= departureTime)) {
            return StopStatus.CURRENT;
        }
        if (isArrival && arrivalTime > 0 && now >= arrivalTime) {
            return StopStatus.CURRENT;
        }
        return StopStatus.UPCOMING;
    }

    public enum StopStatus {
        PASSED,
        CURRENT,
        UPCOMING
    }
}
