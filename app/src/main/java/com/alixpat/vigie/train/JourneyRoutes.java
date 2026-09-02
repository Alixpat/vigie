package com.alixpat.vigie.train;

import com.alixpat.vigie.model.LineNStation;
import com.alixpat.vigie.model.TrainStop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mémoire du parcours de chaque train (clé = journeyRef).
 *
 * <p>Raison d'être : l'{@code estimated-timetable} d'IDFM ne décrit qu'un train
 * <b>tel qu'il lui reste à circuler</b>. Les {@code RecordedCalls} (arrêts déjà
 * desservis) sont facultatifs dans le profil SIRI et IDFM ne les renvoie pas
 * systématiquement — un train qui vient de quitter Clamart n'a donc plus Clamart
 * dans ses {@code EstimatedCalls}. Écraser le parcours en cache à chaque
 * rafraîchissement revient à effacer le début du trajet au fur et à mesure que le
 * train avance : on ne peut alors plus savoir qu'il dessert ma gare de départ, ni
 * à quelle heure il en est parti, et il disparaît de la section « En circulation
 * sur mon trajet » dès qu'il a dépassé ma gare.</p>
 *
 * <p>La solution est de <b>fusionner</b> : on garde les arrêts vus lors des
 * rafraîchissements précédents et on met à jour ceux que la nouvelle réponse
 * décrit encore (horaires estimés, voie).</p>
 */
public final class JourneyRoutes {

    /** Au-delà de cet âge (après le dernier arrêt), un parcours est oublié. */
    public static final long MAX_AGE_MS = 3 * 60 * 60_000L;

    private JourneyRoutes() {}

    /**
     * Fusionne le parcours fraîchement reçu avec celui déjà mémorisé.
     *
     * @param known  parcours déjà connu (peut être null)
     * @param fresh  parcours tel que renvoyé par l'estimated-timetable (peut être null)
     * @return un parcours trié chronologiquement, jamais null : l'union des deux,
     *         les arrêts communs étant repris de {@code fresh} (données temps réel
     *         les plus fraîches)
     */
    public static List<TrainStop> merge(List<TrainStop> known, List<TrainStop> fresh) {
        Map<String, TrainStop> byStop = new LinkedHashMap<>();
        addAll(byStop, known);
        addAll(byStop, fresh);   // écrase les arrêts communs avec la version fraîche

        List<TrainStop> merged = new ArrayList<>(byStop.values());
        Collections.sort(merged, (a, b) ->
                Long.compare(orderingTimeOf(a), orderingTimeOf(b)));
        return withEndsMarked(merged);
    }

    /**
     * Repositionne les drapeaux origine / terminus : chaque réponse marque comme
     * « premier arrêt » le premier de ce qu'il lui reste à parcourir, ce qui est
     * faux dès que le train est en route. Après fusion, seuls les deux bouts du
     * parcours reconstitué portent ces drapeaux.
     */
    private static List<TrainStop> withEndsMarked(List<TrainStop> stops) {
        List<TrainStop> marked = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            boolean isFirst = i == 0;
            boolean isLast = i == stops.size() - 1;
            TrainStop stop = stops.get(i);
            if (stop.isDeparture() == isFirst && stop.isArrival() == isLast) {
                marked.add(stop);
            } else {
                marked.add(new TrainStop(stop.getStopName(), stop.getStopRef(),
                        stop.getAimedArrivalMillis(), stop.getExpectedArrivalMillis(),
                        stop.getAimedDepartureMillis(), stop.getExpectedDepartureMillis(),
                        stop.getPlatformName(), isFirst, isLast));
            }
        }
        return marked;
    }

    private static void addAll(Map<String, TrainStop> byStop, List<TrainStop> stops) {
        if (stops == null) return;
        for (TrainStop stop : stops) {
            if (stop == null) continue;
            String key = keyOf(stop);
            if (key.isEmpty()) continue;
            byStop.put(key, stop);
        }
    }

    /**
     * Clé d'identité d'un arrêt dans un parcours : l'identifiant numérique de
     * l'arrêt quand il est connu (stable même si le nom n'a pas pu être résolu),
     * sinon le nom normalisé.
     */
    static String keyOf(TrainStop stop) {
        String numericId = extractNumericId(stop.getStopRef());
        if (!numericId.isEmpty()) return numericId;
        String name = LineNStation.normalize(stop.getStopName());
        // "Arrêt 43111" quand ni StopPointName ni le cache Navitia n'ont résolu le nom.
        String fromName = trailingDigits(name);
        return !fromName.isEmpty() ? fromName : name;
    }

    /** Instant utilisé pour ordonner le parcours : arrivée si connue, sinon départ. */
    private static long orderingTimeOf(TrainStop stop) {
        long arrival = stop.getBestArrivalMillis();
        return arrival > 0 ? arrival : stop.getBestTimeMillis();
    }

    /**
     * Oublie les parcours dont le dernier arrêt est trop ancien : sans cela, la
     * mémoire grossirait sans fin et de vieux trajets pourraient être confondus
     * avec ceux du jour.
     *
     * @return {@code routes}, pour chaîner
     */
    public static Map<String, List<TrainStop>> purge(Map<String, List<TrainStop>> routes,
                                                     long now, long maxAgeMs) {
        if (routes == null) return null;
        Iterator<Map.Entry<String, List<TrainStop>>> it = routes.entrySet().iterator();
        while (it.hasNext()) {
            List<TrainStop> stops = it.next().getValue();
            if (stops == null || stops.isEmpty()) {
                it.remove();
                continue;
            }
            long last = 0L;
            for (TrainStop stop : stops) {
                last = Math.max(last, Math.max(stop.getBestTimeMillis(), stop.getBestArrivalMillis()));
            }
            if (last <= 0 || now - last > maxAgeMs) it.remove();
        }
        return routes;
    }

    /** "STIF:StopPoint:Q:43111:" → "43111" ; "" si illisible. */
    static String extractNumericId(String stopRef) {
        if (stopRef == null || stopRef.isEmpty()) return "";
        String[] parts = stopRef.split(":");
        for (int p = parts.length - 1; p >= 0; p--) {
            if (!parts[p].isEmpty()) return parts[p];
        }
        return "";
    }

    /** Suite de chiffres terminant la chaîne ("arret 43111" → "43111") ; "" sinon. */
    private static String trailingDigits(String text) {
        if (text == null) return "";
        int end = text.length();
        int start = end;
        while (start > 0 && Character.isDigit(text.charAt(start - 1))) start--;
        if (start == end || start == 0) return "";
        return text.substring(start);
    }
}
