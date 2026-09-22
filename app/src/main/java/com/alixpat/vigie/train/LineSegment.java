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

    /** L'axe Paris → Mantes : tronc commun puis branche Mantes. */
    private static final List<String> AXIS_STATIONS = buildAxis();
    private static final LineSegment AXIS = new LineSegment(AXIS_STATIONS);

    private final List<String> stations;
    /** Les mêmes noms, normalisés une fois pour toutes : {@link #indexOf} tourne à chaque tick. */
    private final List<String> normalized;

    private LineSegment(List<String> stations) {
        this.stations = stations;
        List<String> names = new ArrayList<>(stations.size());
        for (String station : stations) names.add(LineNStation.normalize(station));
        this.normalized = names;
    }

    /**
     * L'axe Paris → Mantes en entier, dans l'ordre géographique.
     *
     * <p>Sert à situer une gare par rapport au parcours d'un train : savoir si
     * elle est <b>avant</b> le début du parcours connu (donc effacée par la
     * troncature de l'{@code estimated-timetable}) ou <b>dedans</b> (donc
     * volontairement sautée). Les deux cas n'ont pas la même conclusion, et les
     * confondre revient à déclarer « ne dessert pas mes gares » un train dont on
     * a simplement raté le début.</p>
     */
    public static LineSegment axis() {
        return AXIS;
    }

    private static List<String> buildAxis() {
        List<String> axis = new ArrayList<>();
        for (LineNStation station : LineNStation.getTrunk()) axis.add(station.getName());
        for (LineNStation station : LineNStation.getBranchMantes()) axis.add(station.getName());
        return Collections.unmodifiableList(axis);
    }

    /**
     * @return le corridor de {@code originName} à {@code destinationName}, dans
     *         cet ordre ; vide si l'un des deux n'est pas sur l'axe Paris → Mantes
     */
    public static LineSegment between(String originName, String destinationName) {
        int from = AXIS.indexOf(originName);
        int to = AXIS.indexOf(destinationName);
        if (from < 0 || to < 0 || from == to) return new LineSegment(Collections.<String>emptyList());

        List<String> segment = new ArrayList<>(
                AXIS_STATIONS.subList(Math.min(from, to), Math.max(from, to) + 1));
        if (from > to) Collections.reverse(segment);
        return new LineSegment(Collections.unmodifiableList(segment));
    }

    /**
     * @return la position de la gare dans le corridor (0 = ma gare de départ,
     *         {@code size() - 1} = ma gare d'arrivée ; pour {@link #axis()},
     *         0 = Paris), ou -1 si elle n'y est pas
     */
    public int indexOf(String stopName) {
        return indexIn(normalized, stopName);
    }

    public int size() {
        return stations.size();
    }

    /** Les gares du corridor, de ma gare de départ à ma gare d'arrivée. */
    public List<String> stations() {
        return stations;
    }

    public boolean isEmpty() {
        return stations.isEmpty();
    }

    /**
     * Comparaison normalisée, en mode {@code contains} dans les deux sens.
     *
     * @param normalizedNames noms déjà passés par {@link LineNStation#normalize}
     */
    private static int indexIn(List<String> normalizedNames, String target) {
        if (target == null) return -1;
        String normalizedTarget = LineNStation.normalize(target);
        if (normalizedTarget.isEmpty()) return -1;
        for (int i = 0; i < normalizedNames.size(); i++) {
            String candidate = normalizedNames.get(i);
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
