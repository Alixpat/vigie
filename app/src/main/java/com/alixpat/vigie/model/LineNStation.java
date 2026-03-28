package com.alixpat.vigie.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Données statiques de toutes les gares de la ligne N du Transilien,
 * organisées par branche pour le tracé schématique.
 */
public class LineNStation {

    public static final int BRANCH_TRUNK = 0;          // Paris → Saint-Cyr
    public static final int BRANCH_RAMBOUILLET = 1;     // SQY → Rambouillet
    public static final int BRANCH_MANTES = 2;          // Fontenay → Mantes-la-Jolie
    public static final int BRANCH_DREUX = 3;           // Montfort → Dreux

    private final String name;
    private final int branch;
    private final int order;

    public LineNStation(String name, int branch, int order) {
        this.name = name;
        this.branch = branch;
        this.order = order;
    }

    public String getName() { return name; }
    public int getBranch() { return branch; }
    public int getOrder() { return order; }

    /**
     * Retourne la liste ordonnée de toutes les gares du tronc commun.
     */
    public static List<LineNStation> getTrunk() {
        return Arrays.asList(
                new LineNStation("Paris Montparnasse", BRANCH_TRUNK, 0),
                new LineNStation("Vanves - Malakoff", BRANCH_TRUNK, 1),
                new LineNStation("Clamart", BRANCH_TRUNK, 2),
                new LineNStation("Meudon", BRANCH_TRUNK, 3),
                new LineNStation("Bellevue", BRANCH_TRUNK, 4),
                new LineNStation("Sèvres Rive Gauche", BRANCH_TRUNK, 5),
                new LineNStation("Chaville Rive Gauche", BRANCH_TRUNK, 6),
                new LineNStation("Viroflay Rive Gauche", BRANCH_TRUNK, 7),
                new LineNStation("Versailles Chantiers", BRANCH_TRUNK, 8),
                new LineNStation("Saint-Cyr", BRANCH_TRUNK, 9)
        );
    }

    /**
     * Branche sud : Saint-Cyr → Rambouillet
     */
    public static List<LineNStation> getBranchRambouillet() {
        return Arrays.asList(
                new LineNStation("Saint-Quentin-en-Yvelines", BRANCH_RAMBOUILLET, 0),
                new LineNStation("Trappes", BRANCH_RAMBOUILLET, 1),
                new LineNStation("La Verrière", BRANCH_RAMBOUILLET, 2),
                new LineNStation("Coignières", BRANCH_RAMBOUILLET, 3),
                new LineNStation("Les Essarts-le-Roi", BRANCH_RAMBOUILLET, 4),
                new LineNStation("Le Perray", BRANCH_RAMBOUILLET, 5),
                new LineNStation("Rambouillet", BRANCH_RAMBOUILLET, 6)
        );
    }

    /**
     * Branche ouest : Saint-Cyr → Plaisir-Grignon → Mantes-la-Jolie
     */
    public static List<LineNStation> getBranchMantes() {
        return Arrays.asList(
                new LineNStation("Fontenay-le-Fleury", BRANCH_MANTES, 0),
                new LineNStation("Villepreux - Les Clayes", BRANCH_MANTES, 1),
                new LineNStation("Plaisir - Les Clayes", BRANCH_MANTES, 2),
                new LineNStation("Plaisir - Grignon", BRANCH_MANTES, 3),
                new LineNStation("Beynes", BRANCH_MANTES, 4),
                new LineNStation("Mareil-sur-Mauldre", BRANCH_MANTES, 5),
                new LineNStation("Maule", BRANCH_MANTES, 6),
                new LineNStation("Nézel - Aulnay", BRANCH_MANTES, 7),
                new LineNStation("Épône - Mézières", BRANCH_MANTES, 8),
                new LineNStation("Mantes-la-Jolie", BRANCH_MANTES, 9)
        );
    }

    /**
     * Branche ouest : Plaisir-Grignon → Dreux
     */
    public static List<LineNStation> getBranchDreux() {
        return Arrays.asList(
                new LineNStation("Montfort-l'Amaury - Méré", BRANCH_DREUX, 0),
                new LineNStation("Villiers - Neauphle - Pontchartrain", BRANCH_DREUX, 1),
                new LineNStation("Garancières - La Queue", BRANCH_DREUX, 2),
                new LineNStation("Orgerus - Béhoust", BRANCH_DREUX, 3),
                new LineNStation("Tacoignières - Richebourg", BRANCH_DREUX, 4),
                new LineNStation("Houdan", BRANCH_DREUX, 5),
                new LineNStation("Marchezais - Broué", BRANCH_DREUX, 6),
                new LineNStation("Dreux", BRANCH_DREUX, 7)
        );
    }

    /**
     * Retourne toutes les gares dans l'ordre séquentiel global.
     */
    public static List<LineNStation> getAll() {
        List<LineNStation> all = new ArrayList<>();
        all.addAll(getTrunk());
        all.addAll(getBranchRambouillet());
        all.addAll(getBranchMantes());
        all.addAll(getBranchDreux());
        return Collections.unmodifiableList(all);
    }

    /**
     * Normalise un nom de gare pour la comparaison (minuscules, sans accents simplifiés).
     */
    public static String normalize(String name) {
        if (name == null) return "";
        return name.toLowerCase(java.util.Locale.FRENCH)
                .replace("é", "e").replace("è", "e").replace("ê", "e")
                .replace("à", "a").replace("â", "a")
                .replace("ô", "o").replace("î", "i").replace("ù", "u")
                .replace("ç", "c")
                .replace(" - ", " ").replace("-", " ")
                .replace("gare de ", "").replace("gare du ", "")
                .trim();
    }

    /**
     * Recherche une gare par son nom (correspondance partielle).
     * Retourne la gare trouvée ou null.
     */
    public static LineNStation findByName(String stopName) {
        if (stopName == null || stopName.isEmpty()) return null;
        String normalizedSearch = normalize(stopName);

        for (LineNStation station : getAll()) {
            String normalizedStation = normalize(station.getName());
            if (normalizedStation.equals(normalizedSearch)) return station;
            if (normalizedStation.contains(normalizedSearch)) return station;
            if (normalizedSearch.contains(normalizedStation)) return station;
        }

        // Recherche par mot-clé principal
        for (LineNStation station : getAll()) {
            String[] words = normalize(station.getName()).split("\\s+");
            for (String word : words) {
                if (word.length() > 3 && normalizedSearch.contains(word)) return station;
            }
        }
        return null;
    }
}
