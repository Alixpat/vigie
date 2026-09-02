package com.alixpat.vigie.train;

import com.alixpat.vigie.model.LineNStation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Le tronçon de ligne entre mes deux gares, sous forme de suite ordonnée de
 * gares dans le sens de circulation (« corridor »).
 *
 * <p>Sert à situer un train sur mon segment quand son parcours ne suffit pas :
 * l'{@code estimated-timetable} ne décrit que les arrêts restants, donc ma gare
 * de départ en disparaît dès que le train l'a dépassée. Le prochain arrêt du
 * train, lui, y figure toujours — s'il tombe dans le corridor au-delà de ma gare
 * de départ, le train est physiquement entre mes deux gares.</p>
 *
 * <p>Le corridor est construit à partir de la géographie de la ligne N
 * ({@link LineNStation}) : tronc commun Paris → Saint-Cyr, prolongé par la
 * branche Mantes. Un couple de gares qui n'est pas décrit par cet axe (branche
 * Rambouillet, branche Dreux) donne un segment vide — l'appelant retombe alors
 * sur les horaires.</p>
 */
public final class LineSegment {

    private final List<String> stations;

    private LineSegment(List<String> stations) {
        this.stations = stations;
    }

    /**
     * @return le corridor de {@code originName} à {@code destinationName}, dans
     *         cet ordre ; vide si l'un des deux n'est pas sur l'axe Paris → Mantes
     */
    public static LineSegment between(String originName, String destinationName) {
        List<String> axis = new ArrayList<>();
        for (LineNStation station : LineNStation.getTrunk()) axis.add(station.getName());
        for (LineNStation station : LineNStation.getBranchMantes()) axis.add(station.getName());

        int from = indexIn(axis, originName);
        int to = indexIn(axis, destinationName);
        if (from < 0 || to < 0 || from == to) return new LineSegment(Collections.<String>emptyList());

        List<String> segment = new ArrayList<>(
                axis.subList(Math.min(from, to), Math.max(from, to) + 1));
        if (from > to) Collections.reverse(segment);
        return new LineSegment(Collections.unmodifiableList(segment));
    }

    /**
     * @return la position de la gare dans le corridor (0 = ma gare de départ,
     *         {@code size() - 1} = ma gare d'arrivée), ou -1 si elle n'y est pas
     */
    public int indexOf(String stopName) {
        return indexIn(stations, stopName);
    }

    public int size() {
        return stations.size();
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    /** Comparaison normalisée, en mode {@code contains} dans les deux sens. */
    private static int indexIn(List<String> names, String target) {
        if (names == null || target == null) return -1;
        String normalizedTarget = LineNStation.normalize(target);
        if (normalizedTarget.isEmpty()) return -1;
        for (int i = 0; i < names.size(); i++) {
            String candidate = LineNStation.normalize(names.get(i));
            if (candidate.isEmpty()) continue;
            if (candidate.equals(normalizedTarget)
                    || candidate.contains(normalizedTarget)
                    || normalizedTarget.contains(candidate)) {
                return i;
            }
        }
        return -1;
    }
}
