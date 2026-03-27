package com.alixpat.vigie.model;

public class TrainSchedule {

    private final String destination;
    private final String aimedDepartureTime;
    private final String expectedDepartureTime;
    private final String arrivalTime;
    private final String departureStatus;
    private final String platformName;
    private final int delayMinutes;
    private final String journeyRef;
    private final long aimedDepartureMillis;
    private final long arrivalMillis;
    private final String originStation;

    public TrainSchedule(String destination, String aimedDepartureTime,
                         String expectedDepartureTime, String arrivalTime,
                         String departureStatus, String platformName,
                         int delayMinutes) {
        this(destination, aimedDepartureTime, expectedDepartureTime, arrivalTime,
                departureStatus, platformName, delayMinutes, "", 0, 0, "");
    }

    public TrainSchedule(String destination, String aimedDepartureTime,
                         String expectedDepartureTime, String arrivalTime,
                         String departureStatus, String platformName,
                         int delayMinutes, String journeyRef,
                         long aimedDepartureMillis, long arrivalMillis,
                         String originStation) {
        this.destination = destination;
        this.aimedDepartureTime = aimedDepartureTime;
        this.expectedDepartureTime = expectedDepartureTime;
        this.arrivalTime = arrivalTime != null ? arrivalTime : "";
        this.departureStatus = departureStatus;
        this.platformName = platformName;
        this.delayMinutes = delayMinutes;
        this.journeyRef = journeyRef;
        this.aimedDepartureMillis = aimedDepartureMillis;
        this.arrivalMillis = arrivalMillis;
        this.originStation = originStation;
    }

    public String getDestination() { return destination; }
    public String getAimedDepartureTime() { return aimedDepartureTime; }
    public String getExpectedDepartureTime() { return expectedDepartureTime; }
    public String getArrivalTime() { return arrivalTime; }
    public String getDepartureStatus() { return departureStatus; }
    public String getPlatformName() { return platformName; }
    public int getDelayMinutes() { return delayMinutes; }
    public String getJourneyRef() { return journeyRef; }
    public long getAimedDepartureMillis() { return aimedDepartureMillis; }
    public long getArrivalMillis() { return arrivalMillis; }
    public String getOriginStation() { return originStation; }

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

    /**
     * Calcule le temps de trajet entre le départ et l'arrivée.
     * @return durée formatée (ex: "32 min", "1h12") ou null si indisponible
     */
    public String getTravelTime() {
        if (aimedDepartureMillis <= 0 || arrivalMillis <= 0) {
            return null;
        }
        long durationMillis = arrivalMillis - aimedDepartureMillis;
        if (durationMillis <= 0) {
            return null;
        }
        long totalMinutes = durationMillis / 60_000;
        if (totalMinutes < 60) {
            return totalMinutes + " min";
        }
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        if (minutes == 0) {
            return hours + "h";
        }
        return hours + "h" + String.format("%02d", minutes);
    }

    public String getStatusEmoji() {
        if (isCancelled()) return "\u274C";
        if (isDelayed()) return "\u23F0";
        return "\u2705";
    }

    /**
     * Estime la position actuelle du train entre l'origine et la destination
     * en se basant sur les horaires de départ et d'arrivée.
     *
     * @return description textuelle de la position estimée
     */
    public String estimatePosition() {
        if (isCancelled()) {
            return "Train supprimé";
        }

        long now = System.currentTimeMillis();
        long effectiveDeparture = aimedDepartureMillis;
        if (delayMinutes > 0) {
            effectiveDeparture += delayMinutes * 60_000L;
        }

        if (effectiveDeparture <= 0) {
            return "Position inconnue";
        }

        long diffToDepart = effectiveDeparture - now;
        long minutesToDepart = diffToDepart / 60_000;

        if (diffToDepart > 0) {
            if (minutesToDepart <= 0) {
                return "En gare de " + originStation + " - départ imminent";
            } else if (minutesToDepart <= 5) {
                return "En gare de " + originStation + " - départ dans " + minutesToDepart + " min";
            } else {
                return "En approche de " + originStation + " - départ dans " + minutesToDepart + " min";
            }
        }

        // Le train est parti
        if (arrivalMillis > 0 && now < arrivalMillis) {
            long totalTravel = arrivalMillis - effectiveDeparture;
            long elapsed = now - effectiveDeparture;
            if (totalTravel > 0) {
                int progress = (int) ((elapsed * 100) / totalTravel);
                progress = Math.min(progress, 100);
                return "En route vers " + destination + " (" + progress + "% du trajet)";
            }
        }

        if (arrivalMillis > 0 && now >= arrivalMillis) {
            return "Arrivé à " + destination;
        }

        long minutesSinceDepart = (now - effectiveDeparture) / 60_000;
        return "Parti de " + originStation + " il y a " + minutesSinceDepart + " min";
    }
}
