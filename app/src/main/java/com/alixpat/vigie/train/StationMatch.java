package com.alixpat.vigie.train;

import com.alixpat.vigie.model.LineNStation;

import java.util.Collection;

/**
 * Rapproche un nom de gare reçu de l'API du nom de gare connu du plan.
 *
 * <p>La correspondance se fait sur des <b>suites de mots</b> entières, jamais
 * sur un mot isolé : « Chaville Rive Gauche » et « Sèvres Rive Gauche »
 * partagent deux mots, et une recherche par mot-clé y place les trains de l'une
 * à l'autre. Quand deux gares sont aussi plausibles l'une que l'autre
 * (« Plaisir » → Plaisir - Les Clayes ou Plaisir - Grignon ?), la correspondance
 * est refusée : sur un plan, un train absent est moins grave qu'un train au
 * mauvais endroit.</p>
 */
public final class StationMatch {

    private StationMatch() {
    }

    /**
     * @param stopName   nom brut venu de l'API (accents et tirets tolérés)
     * @param candidates noms déjà normalisés par {@link LineNStation#normalize}
     * @return le candidat correspondant, ou null si aucun ne correspond ou si
     *         plusieurs correspondent aussi bien
     */
    public static String bestMatch(String stopName, Collection<String> candidates) {
        if (stopName == null || stopName.isEmpty() || candidates == null) return null;
        String normalized = LineNStation.normalize(stopName);
        if (normalized.isEmpty()) return null;

        for (String candidate : candidates) {
            if (normalized.equals(candidate)) return candidate;
        }

        String[] target = normalized.split("\\s+");
        String best = null;
        int bestScore = 0;
        boolean ambiguous = false;
        for (String candidate : candidates) {
            int score = score(target, candidate.split("\\s+"));
            if (score == 0) continue;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }
        return ambiguous ? null : best;
    }

    /**
     * @return 0 si les deux noms n'ont rien à voir, sinon le nombre de mots de
     *         la suite commune — plus il est grand, plus la gare est identifiée
     *         précisément
     */
    private static int score(String[] target, String[] candidate) {
        if (containsSequence(target, candidate)) return candidate.length;
        if (containsSequence(candidate, target)) return target.length;
        return 0;
    }

    /** La suite de mots {@code needle} apparaît-elle telle quelle dans {@code hay} ? */
    private static boolean containsSequence(String[] hay, String[] needle) {
        if (needle.length == 0 || needle.length > hay.length) return false;
        outer:
        for (int start = 0; start + needle.length <= hay.length; start++) {
            for (int i = 0; i < needle.length; i++) {
                if (!hay[start + i].equals(needle[i])) continue outer;
            }
            return true;
        }
        return false;
    }
}
