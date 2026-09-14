package com.alixpat.vigie.train;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Les trains que j'ai épinglés pour les suivre, qu'ils soient déjà en
 * circulation ou encore à venir.
 *
 * <p>Un train est désigné par son {@code journeyRef} — le même identifiant que
 * partout ailleurs dans le module train, donc un épinglage retrouve son train
 * qu'il vienne du stop-monitoring ou de l'estimated-timetable. Cet identifiant
 * est <b>daté</b> côté IDFM : il ne survit pas à la journée. On mémorise donc
 * l'instant de l'épinglage et on oublie au-delà de {@link #MAX_AGE_MS}, sans
 * quoi la liste se remplirait de références mortes que plus aucun train ne
 * viendrait résoudre.</p>
 *
 * <p>Classe volontairement sans dépendance Android : la persistance (un
 * {@code Set<String>} de SharedPreferences) se fait par {@link #encode()} /
 * {@link #decode(Collection, long)}, testables sur la JVM.</p>
 */
public final class PinnedTrains {

    /** Au-delà de cet âge, un épinglage est oublié : son journeyRef est périmé. */
    public static final long MAX_AGE_MS = 6 * 60 * 60_000L;

    private static final String SEPARATOR = "|";

    /** journeyRef → instant d'épinglage, dans l'ordre d'épinglage. */
    private final Map<String, Long> pinnedAt = new LinkedHashMap<>();

    public PinnedTrains() {}

    /**
     * Relit les épinglages persistés, en écartant au passage ceux qui sont
     * périmés ou illisibles.
     *
     * @param entries lignes {@code "journeyRef|instant"} (peut être null)
     * @param now     instant de référence en epoch millis
     */
    public static PinnedTrains decode(Collection<String> entries, long now) {
        PinnedTrains pinned = new PinnedTrains();
        if (entries == null) return pinned;
        // Les Set de SharedPreferences ne gardent pas l'ordre : on rétablit
        // l'ordre d'épinglage, seul ordre qui ait un sens pour l'utilisateur.
        List<String> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, (a, b) -> Long.compare(timestampOf(a), timestampOf(b)));
        for (String entry : sorted) {
            String ref = refOf(entry);
            long at = timestampOf(entry);
            if (ref.isEmpty() || at <= 0) continue;
            pinned.pinnedAt.put(ref, at);
        }
        return pinned.purge(now);
    }

    /** @return les épinglages sous la forme persistable {@code "journeyRef|instant"} */
    public Set<String> encode() {
        Set<String> entries = new LinkedHashSet<>();
        for (Map.Entry<String, Long> entry : pinnedAt.entrySet()) {
            entries.add(entry.getKey() + SEPARATOR + entry.getValue());
        }
        return entries;
    }

    public boolean isPinned(String journeyRef) {
        return journeyRef != null && pinnedAt.containsKey(journeyRef);
    }

    /**
     * Épingle le train s'il ne l'était pas, le désépingle sinon.
     *
     * @return l'état après bascule : true si le train est désormais suivi
     */
    public boolean toggle(String journeyRef, long now) {
        if (journeyRef == null || journeyRef.isEmpty()) return false;
        if (pinnedAt.remove(journeyRef) != null) return false;
        pinnedAt.put(journeyRef, now);
        return true;
    }

    public void unpin(String journeyRef) {
        pinnedAt.remove(journeyRef);
    }

    /** Les trains suivis, dans l'ordre où ils ont été épinglés. */
    public Set<String> refs() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(pinnedAt.keySet()));
    }

    public int size() {
        return pinnedAt.size();
    }

    public boolean isEmpty() {
        return pinnedAt.isEmpty();
    }

    /**
     * Oublie les épinglages trop anciens pour désigner encore un train réel.
     *
     * @return {@code this}, pour chaîner
     */
    public PinnedTrains purge(long now) {
        Iterator<Map.Entry<String, Long>> it = pinnedAt.entrySet().iterator();
        while (it.hasNext()) {
            Long at = it.next().getValue();
            if (at == null || now - at > MAX_AGE_MS) it.remove();
        }
        return this;
    }

    private static String refOf(String entry) {
        if (entry == null) return "";
        int cut = entry.lastIndexOf(SEPARATOR);
        return cut > 0 ? entry.substring(0, cut) : "";
    }

    private static long timestampOf(String entry) {
        if (entry == null) return 0L;
        int cut = entry.lastIndexOf(SEPARATOR);
        if (cut < 0 || cut == entry.length() - 1) return 0L;
        try {
            return Long.parseLong(entry.substring(cut + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
