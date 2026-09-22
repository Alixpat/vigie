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
 * {@code estimated-timetable} ne décrit un train que tel qu'il lui <b>reste</b>
 * à circuler. Une gare absente du parcours connu a donc deux explications
 * opposées, et c'est la position de cette gare sur la ligne qui tranche :</p>
 *
 * <ul>
 *   <li>elle est <b>en amont</b> du premier arrêt connu : le train l'a franchie
 *       avant qu'on ne l'observe, et l'API ne la décrit plus. On ne sait pas
 *       s'il s'y est arrêté — {@link Service#UPSTREAM} ;</li>
 *   <li>elle est <b>dans</b> le parcours connu, ou au-delà de son terminus : là
 *       l'API est complète et son silence vaut réponse, le train ne la dessert
 *       pas — {@link Service#SKIPPED}.</li>
 * </ul>
 *
 * <p>Confondre les deux, c'est marquer « ne dessert pas mes gares » un train qui
 * roule vers ma gare d'arrivée simplement parce qu'il a déjà quitté l'autre :
 * le seul fait qu'il ait un arrêt précédent hors du parcours connu ne dit rien
 * de ce qu'il dessert. À la mémoire des passages observés au
 * {@code stop-monitoring} de mes deux gares ({@link OngoingTrains#rememberOriginVisits})
 * — qui, elle, prouve la desserte — s'ajoute donc cette lecture géographique du
 * parcours.</p>
 *
 * <p>Compromis assumé : un semi-direct qui a sauté ma gare de départ avant
 * qu'on ne l'observe est classé {@link Verdict#PROBABLY}, donc dessiné comme un
 * de mes trains. Sur le plan, un train de trop se voit et se corrige d'un coup
 * d'œil ; un de mes trains dessiné en trafic anonyme, non.</p>
 */
public final class MyTrains {

    private MyTrains() {}

    /** Ce que le parcours connu dit d'une gare donnée. */
    public enum Service {
        /** Le train s'y arrête : c'est écrit dans le parcours, ou on l'y a vu. */
        SERVED,
        /** Le train ne s'y arrête pas : l'API décrit cette portion et l'ignore. */
        SKIPPED,
        /** En amont du parcours décrit : le train est passé par là, sans qu'on sache s'il s'y est arrêté. */
        UPSTREAM,
        /** Parcours muet ou illisible : on ne sait même pas où le train se situe. */
        UNKNOWN
    }

    /** Ce train me sert-il ? */
    public enum Verdict {
        /** Prouvé : il dessert mes deux gares. */
        SERVES,
        /** Plausible : une de mes gares est hors de la portion décrite du parcours. */
        PROBABLY,
        /** Prouvé : il saute une de mes gares, ou n'y va pas. */
        NO;

        /** @return true si ce train est à dessiner comme un des miens. */
        public boolean isMine() {
            return this != NO;
        }
    }

    /** @return true si le parcours connu dessert cette gare. */
    public static boolean serves(List<TrainStop> stops, String stationName, String stationId) {
        return OngoingTrains.indexOfStop(stops, stationName, stationId) >= 0;
    }

    /**
     * Ce que le parcours connu dit d'une gare : desservie, sautée, ou hors de la
     * portion décrite.
     *
     * <p>Le parcours est lu sur l'axe Paris → Mantes ({@link LineSegment#axis()}).
     * Son premier arrêt connu marque le début de ce que l'API décrit — en amont,
     * on ne sait rien ; son dernier marque le terminus — au-delà, le train ne va
     * pas. Entre les deux, le silence de l'API est une réponse : le train ne
     * s'arrête pas là.</p>
     *
     * @param stops       parcours connu du train (peut être null)
     * @param stationName nom de la gare cherchée
     * @param stationId   son identifiant numérique, pour les parcours dont les
     *                    noms n'ont pas été résolus ("Arrêt 43111")
     */
    public static Service serviceAt(List<TrainStop> stops, String stationName, String stationId) {
        if (serves(stops, stationName, stationId)) return Service.SERVED;
        if (stops == null || stops.isEmpty()) return Service.UNKNOWN;

        LineSegment axis = LineSegment.axis();
        int target = axis.indexOf(stationName);
        if (target < 0) return Service.UNKNOWN;   // gare hors de l'axe : pas mon affaire

        int first = -1;
        int last = -1;
        for (TrainStop stop : stops) {
            int index = axis.indexOf(stop.getStopName());
            if (index < 0) continue;              // arrêt d'une autre branche, ou nom non résolu
            if (first < 0) first = index;
            last = index;
        }
        // Moins de deux points de repère sur l'axe : le sens de marche est
        // indécidable, donc la position de ma gare aussi.
        if (first < 0 || first == last) return Service.UNKNOWN;

        boolean forward = last > first;
        boolean beyondStart = forward ? target > first : target < first;
        // Au-delà du départ décrit : l'API couvre cette portion (jusqu'à son
        // terminus compris) et ne mentionne pas ma gare — il ne s'y arrête pas.
        return beyondStart ? Service.SKIPPED : Service.UPSTREAM;
    }

    /**
     * Le classement détaillé, pour afficher « dessert mes gares » et « sans
     * doute » différemment — une déduction ne se présente pas comme un fait.
     *
     * @param stops             parcours connu du train (peut être null)
     * @param direction         un sens de mon trajet : ses deux bouts sont mes gares
     * @param seenAtOrigin      le train a été observé au stop-monitoring de la première
     * @param seenAtDestination idem pour la seconde
     */
    public static Verdict verdict(List<TrainStop> stops, LineNDirection direction,
                                  boolean seenAtOrigin, boolean seenAtDestination) {
        Service origin = seenAtOrigin
                ? Service.SERVED
                : serviceAt(stops, direction.getOriginName(), direction.getOriginStopId());
        Service destination = seenAtDestination
                ? Service.SERVED
                : serviceAt(stops, direction.getDestinationName(), direction.getDestinationStopId());

        if (origin == Service.SKIPPED || destination == Service.SKIPPED) return Verdict.NO;
        if (origin == Service.SERVED && destination == Service.SERVED) return Verdict.SERVES;
        // Une gare prouvée et l'autre seulement en amont du parcours décrit :
        // c'est un de mes trains, sauf preuve du contraire. Un parcours muet, en
        // revanche, ne prouve rien du tout — une seule de mes gares ne suffit pas.
        boolean oneServed = origin == Service.SERVED || destination == Service.SERVED;
        boolean oneUpstream = origin == Service.UPSTREAM || destination == Service.UPSTREAM;
        return oneServed && oneUpstream ? Verdict.PROBABLY : Verdict.NO;
    }

    /**
     * Variante à partir des mémoires de passage, telles que le fragment les tient.
     *
     * @param journeyRef   identifiant du train
     * @param seenAtOrigin passages mémorisés à la première de mes gares (peut être null)
     * @param seenAtDest   passages mémorisés à la seconde (peut être null)
     */
    public static Verdict verdict(String journeyRef, List<TrainStop> stops,
                                  LineNDirection direction,
                                  Map<String, StopVisit> seenAtOrigin,
                                  Map<String, StopVisit> seenAtDest) {
        return verdict(stops, direction,
                seenAtOrigin != null && seenAtOrigin.containsKey(journeyRef),
                seenAtDest != null && seenAtDest.containsKey(journeyRef));
    }
}
