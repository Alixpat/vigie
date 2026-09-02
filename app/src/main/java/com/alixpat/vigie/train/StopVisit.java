package com.alixpat.vigie.train;

import java.util.Date;

/**
 * Un passage de train à un arrêt, tel que renvoyé par l'endpoint SIRI
 * {@code stop-monitoring} (un {@code MonitoredStopVisit}).
 *
 * Structure volontairement plate (champs publics) : c'est un sac de données de
 * parsing, pas un modèle métier. Extrait de {@code TrainFragment.RawStopVisit}
 * pour que la construction des trajets (cf. {@link OngoingTrains}) soit
 * testable hors Android.
 */
public final class StopVisit {

    public String journeyRef;
    public String destination;
    public Date aimedDeparture;
    public Date expectedDeparture;
    public Date aimedArrival;
    public Date expectedArrival;
    public String departureStatus;
    public String platform;
    public String trainNumber;
    public String missionName;

    /** Meilleure heure d'arrivée connue à cet arrêt : théorique, sinon estimée, sinon départ. */
    public long bestArrivalMillis() {
        if (aimedArrival != null) return aimedArrival.getTime();
        if (expectedArrival != null) return expectedArrival.getTime();
        if (aimedDeparture != null) return aimedDeparture.getTime();
        return 0L;
    }

    public boolean isCancelled() {
        return "cancelled".equalsIgnoreCase(departureStatus);
    }
}
