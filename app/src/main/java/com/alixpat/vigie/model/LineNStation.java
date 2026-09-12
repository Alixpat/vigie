package com.alixpat.vigie.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Données statiques de toutes les gares de la ligne N du Transilien.
 *
 * <p>La branche et le rang d'une gare ne sont pas portés par la gare : ce sont
 * les quatre listes ci-dessous, ordonnées, qui les disent. Le plan comme le
 * corridor {@code LineSegment} lisent la géographie de la ligne dans ces listes.</p>
 */
public class LineNStation {

    private final String name;

    public LineNStation(String name) {
        this.name = name;
    }

    public String getName() { return name; }

    /**
     * Retourne la liste ordonnée de toutes les gares du tronc commun.
     */
    public static List<LineNStation> getTrunk() {
        return Arrays.asList(
                new LineNStation("Paris Montparnasse"),
                new LineNStation("Vanves - Malakoff"),
                new LineNStation("Clamart"),
                new LineNStation("Meudon"),
                new LineNStation("Bellevue"),
                new LineNStation("Sèvres Rive Gauche"),
                new LineNStation("Chaville Rive Gauche"),
                new LineNStation("Viroflay Rive Gauche"),
                new LineNStation("Versailles Chantiers"),
                new LineNStation("Saint-Cyr")
        );
    }

    /**
     * Branche sud : Saint-Cyr → Rambouillet
     */
    public static List<LineNStation> getBranchRambouillet() {
        return Arrays.asList(
                new LineNStation("Saint-Quentin-en-Yvelines"),
                new LineNStation("Trappes"),
                new LineNStation("La Verrière"),
                new LineNStation("Coignières"),
                new LineNStation("Les Essarts-le-Roi"),
                new LineNStation("Le Perray"),
                new LineNStation("Rambouillet")
        );
    }

    /**
     * Branche ouest : Saint-Cyr → Plaisir-Grignon → Mantes-la-Jolie
     */
    public static List<LineNStation> getBranchMantes() {
        return Arrays.asList(
                new LineNStation("Fontenay-le-Fleury"),
                new LineNStation("Villepreux - Les Clayes"),
                new LineNStation("Plaisir - Les Clayes"),
                new LineNStation("Plaisir - Grignon"),
                new LineNStation("Beynes"),
                new LineNStation("Mareil-sur-Mauldre"),
                new LineNStation("Maule"),
                new LineNStation("Nézel - Aulnay"),
                new LineNStation("Épône - Mézières"),
                new LineNStation("Mantes-la-Jolie")
        );
    }

    /**
     * Branche ouest : Plaisir-Grignon → Dreux
     */
    public static List<LineNStation> getBranchDreux() {
        return Arrays.asList(
                new LineNStation("Montfort-l'Amaury - Méré"),
                new LineNStation("Villiers - Neauphle - Pontchartrain"),
                new LineNStation("Garancières - La Queue"),
                new LineNStation("Orgerus - Béhoust"),
                new LineNStation("Tacoignières - Richebourg"),
                new LineNStation("Houdan"),
                new LineNStation("Marchezais - Broué"),
                new LineNStation("Dreux")
        );
    }

    /** Toutes les gares de la ligne, tronc puis branches. */
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
}
