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
    }

    public String getLabel() {
        return label;
    }

    public String getOriginStopRef() {
        return originStopRef;
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
