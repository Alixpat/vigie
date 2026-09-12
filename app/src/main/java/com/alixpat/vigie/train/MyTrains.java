package com.alixpat.vigie.train;

import com.alixpat.vigie.model.TrainStop;

import java.util.List;
import java.util.Map;

/**
 * Sépare les trains qui me concernent du reste du trafic de la ligne N.
 *
 * <p>Le plan affiche toute la ligne : trente gares, quatre branches, et jusqu'à
 * une cinquantaine de trains dont la plupart ne passent jamais par chez moi
 * (branches Rambouillet et Dreux, semi-directs qui sautent mes gares). « Mes »
 * trains sont ceux qui desservent <b>mes deux gares</b> — les seuls que je
 * puisse prendre pour aller de l'une à l'autre.</p>
 *
 * <p>La difficulté est la même que pour {@link OngoingTrains} : l'
 * {@code estimated-timetable} ne décrit un train que tel qu'il lui reste à
 * circuler, donc ma gare de départ sort de son parcours dès qu'il l'a franchie —
 * et le juger sur le seul parcours le ferait basculer dans « les autres » au
 * moment précis où il roule chez moi. On croise donc deux sources : le parcours
 * connu, et la mémoire des passages observés au {@code stop-monitoring} de mes
 * deux gares ({@link OngoingTrains#rememberOriginVisits}). Avoir été annoncé à
 * une de mes gares vaut preuve qu'il la dessert, même quand l'API ne le dit
 * plus.</p>
 *
 * <p>Compromis assumé, le même qu'ailleurs : un train qui a franchi mes deux
 * gares avant que l'app ne l'observe passe dans « les autres ». Il est alors
 * derrière moi de toute façon — le cas gênant serait l'inverse, et la mémoire
 * des passages l'écarte.</p>
 */
public final class MyTrains {

    private MyTrains() {}

    /** @return true si le parcours connu dessert cette gare. */
    public static boolean serves(List<TrainStop> stops, String stationName, String stationId) {
        return OngoingTrains.indexOfStop(stops, stationName, stationId) >= 0;
    }

    /**
     * @param stops             parcours connu du train (peut être null)
     * @param direction         un sens de mon trajet : ses deux bouts sont mes gares
     * @param seenAtOrigin      le train a été observé au stop-monitoring de la première
     * @param seenAtDestination idem pour la seconde
     * @return true si le train dessert mes deux gares
     */
    public static boolean servesMyStations(List<TrainStop> stops, LineNDirection direction,
                                           boolean seenAtOrigin, boolean seenAtDestination) {
        boolean atOrigin = seenAtOrigin
                || serves(stops, direction.getOriginName(), direction.getOriginStopId());
        boolean atDestination = seenAtDestination
                || serves(stops, direction.getDestinationName(), direction.getDestinationStopId());
        return atOrigin && atDestination;
    }

    /**
     * Variante à partir des mémoires de passage, telles que le fragment les tient.
     *
     * @param journeyRef   identifiant du train
     * @param seenAtOrigin passages mémorisés à la première de mes gares (peut être null)
     * @param seenAtDest   passages mémorisés à la seconde (peut être null)
     */
    public static boolean servesMyStations(String journeyRef, List<TrainStop> stops,
                                           LineNDirection direction,
                                           Map<String, StopVisit> seenAtOrigin,
                                           Map<String, StopVisit> seenAtDest) {
        return servesMyStations(stops, direction,
                seenAtOrigin != null && seenAtOrigin.containsKey(journeyRef),
                seenAtDest != null && seenAtDest.containsKey(journeyRef));
    }
}
