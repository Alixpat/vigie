package com.alixpat.vigie.train;

import java.util.Locale;

/**
 * Une direction de circulation sur la Ligne N : un sens (origine → destination)
 * + la liste de mots-clés permettant de reconnaître les trains qui vont
 * effectivement dans ce sens dans les réponses SIRI (le champ "destination"
 * d'un visit ne contient pas le terminus formel — il faut matcher).
 *
 * Extrait de TrainFragment pour rendre la logique testable et
 * faciliter, à terme, le passage à plusieurs lignes (cf. dette technique
 * "ligne hardcodée" dans le README).
 */
public final class LineNDirection {

    public static final LineNDirection ALLER = new LineNDirection(
            "Aller",
            "STIF:StopArea:SP:43111:",   // Clamart
            "STIF:StopArea:SP:43221:",   // Villepreux
            "Clamart",
            "Villepreux",
            new String[]{"villepreux", "plaisir", "dreux", "mantes"}
    );

    public static final LineNDirection RETOUR = new LineNDirection(
            "Retour",
            "STIF:StopArea:SP:43221:",   // Villepreux
            "STIF:StopArea:SP:43111:",   // Clamart
            "Villepreux",
            "Clamart",
            new String[]{
                    "paris", "montparnasse", "clamart", "meudon", "chaville",
                    "viroflay", "versailles", "sèvres", "sevres"
            }
    );

    private final String label;
    private final String originStopRef;
    private final String destinationStopRef;
    private final String originName;
    private final String destinationName;
    private final String[] destinationKeywords;
    private final String originStopId;

    private LineNDirection(String label,
                           String originStopRef, String destinationStopRef,
                           String originName, String destinationName,
                           String[] destinationKeywords) {
        this.label = label;
        this.originStopRef = originStopRef;
        this.destinationStopRef = destinationStopRef;
        this.originName = originName;
        this.destinationName = destinationName;
        this.destinationKeywords = destinationKeywords;
        this.originStopId = extractNumericId(originStopRef);
    }

    /** "STIF:StopArea:SP:43111:" → "43111" ; "" si illisible. */
    private static String extractNumericId(String stopRef) {
        if (stopRef == null || stopRef.isEmpty()) return "";
        String[] parts = stopRef.split(":");
        for (int p = parts.length - 1; p >= 0; p--) {
            if (!parts[p].isEmpty()) return parts[p];
        }
        return "";
    }

    public String getLabel() {
        return label;
    }

    public String getOriginStopRef() {
        return originStopRef;
    }

    /**
     * @return l'identifiant numérique de la gare d'origine ("43111" pour
     *         "STIF:StopArea:SP:43111:"). Sert de secours pour retrouver l'arrêt
     *         dans un parcours dont les noms de gares n'ont pas pu être résolus
     *         (l'estimated-timetable IDFM ne renvoie pas toujours StopPointName :
     *         les arrêts s'appellent alors "Arrêt 43111").
     */
    public String getOriginStopId() {
        return originStopId;
    }

    public String getDestinationStopRef() {
        return destinationStopRef;
    }

    public String getOriginName() {
        return originName;
    }

    public String getDestinationName() {
        return destinationName;
    }

    /**
     * @return true si le terminus annoncé d'un train (champ "destination" SIRI)
     *         correspond à un terminus dans ce sens de circulation.
     *         Matching case-insensitive, en mode {@code contains} (substring).
     *         Texte null ou vide → false.
     */
    public boolean matchesDestination(String trainDestination) {
        if (trainDestination == null || trainDestination.isEmpty()) return false;
        String lower = trainDestination.toLowerCase(Locale.FRENCH);
        for (String keyword : destinationKeywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
}
