package com.alixpat.vigie.train;

import com.alixpat.vigie.model.TrainIncident;

import java.util.Locale;

/**
 * Classification d'incidents transports à partir de texte libre (messages SIRI ou
 * line_reports Navitia). Logique extraite de TrainFragment pour pouvoir être testée
 * unitairement et ré-utilisée par les deux call sites de classification.
 *
 * Toutes les méthodes sont pures et appliquent {@code toLowerCase(Locale.FRENCH)}
 * en interne — l'appelant n'a pas à pré-normaliser.
 */
public final class IncidentClassifier {

    static final String[] TRAVAUX_KEYWORDS = {
            "travaux", "chantier", "maintenance", "planifi",
            "programme", "prévu"
    };
    static final String[] PERTURBATION_KEYWORDS = {
            "interrompu", "perturbé", "ralenti", "supprimé",
            "retard", "allongement", "dévié", "régulation"
    };
    static final String[] BLOCKING_KEYWORDS = {
            "interrompu", "supprimé", "immobilis"
    };

    private IncidentClassifier() {}

    public static boolean hasTravauxKeyword(String text) {
        return containsAny(text, TRAVAUX_KEYWORDS);
    }

    public static boolean hasPerturbationKeyword(String text) {
        return containsAny(text, PERTURBATION_KEYWORDS);
    }

    public static boolean hasBlockingKeyword(String text) {
        return containsAny(text, BLOCKING_KEYWORDS);
    }

    private static boolean containsAny(String text, String[] keywords) {
        if (text == null || text.isEmpty()) return false;
        String lower = text.toLowerCase(Locale.FRENCH);
        for (String keyword : keywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }

    /**
     * Classification d'un message SIRI (general-message) à partir du texte libre
     * et du nom de canal. Reproduit la logique du call site general-message :
     *
     * <ul>
     *   <li>Canal contenant "perturbation" OU mots-clés perturbation → PERTURBATION
     *       (severity = "blocking" si mots-clés blocking présents, sinon "delays")</li>
     *   <li>Sinon : TRAVAUX si mots-clés travaux, sinon INFORMATION ;
     *       severity = "information" dans les deux cas.</li>
     * </ul>
     */
    public static Classification classifyMessage(String text, String channel) {
        String channelLower = channel == null ? "" : channel.toLowerCase(Locale.FRENCH);
        boolean isPerturbationChannel = channelLower.contains("perturbation");

        if (isPerturbationChannel || hasPerturbationKeyword(text)) {
            String severity = hasBlockingKeyword(text) ? "blocking" : "delays";
            return new Classification(TrainIncident.TYPE_PERTURBATION, severity);
        }
        String type = hasTravauxKeyword(text)
                ? TrainIncident.TYPE_TRAVAUX
                : TrainIncident.TYPE_INFORMATION;
        return new Classification(type, "information");
    }

    public static final class Classification {
        public final String type;
        public final String severity;

        public Classification(String type, String severity) {
            this.type = type;
            this.severity = severity;
        }
    }
}
