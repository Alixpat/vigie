package com.alixpat.vigie.train;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class PinnedTrainsTest {

    private static final long T0 = 1_700_000_000_000L;
    private static final long MIN = 60_000L;
    private static final String REF_A = "SNCF:2024-07-01:135642:1187:Train";
    private static final String REF_B = "SNCF:2024-07-01:135644:1187:Train";

    @Test
    public void togglePinsThenUnpins() {
        PinnedTrains pinned = new PinnedTrains();
        assertFalse(pinned.isPinned(REF_A));

        assertTrue(pinned.toggle(REF_A, T0));
        assertTrue(pinned.isPinned(REF_A));
        assertEquals(1, pinned.size());

        assertFalse(pinned.toggle(REF_A, T0 + MIN));
        assertFalse(pinned.isPinned(REF_A));
        assertTrue(pinned.isEmpty());
    }

    @Test
    public void ignoresEmptyRef() {
        PinnedTrains pinned = new PinnedTrains();
        assertFalse(pinned.toggle("", T0));
        assertFalse(pinned.toggle(null, T0));
        assertTrue(pinned.isEmpty());
    }

    @Test
    public void survivesAnEncodeDecodeRoundTrip() {
        PinnedTrains pinned = new PinnedTrains();
        pinned.toggle(REF_A, T0);
        pinned.toggle(REF_B, T0 + MIN);

        PinnedTrains reloaded = PinnedTrains.decode(pinned.encode(), T0 + 2 * MIN);

        assertTrue(reloaded.isPinned(REF_A));
        assertTrue(reloaded.isPinned(REF_B));
        assertEquals(2, reloaded.size());
    }

    @Test
    public void restoresPinningOrderEvenFromAnUnorderedSet() {
        // SharedPreferences rend un Set : l'ordre d'itération n'est pas celui
        // de l'épinglage. Il doit être reconstruit depuis l'instant mémorisé.
        List<String> shuffled = new ArrayList<>(Arrays.asList(
                REF_B + "|" + (T0 + 5 * MIN),
                REF_A + "|" + T0));

        PinnedTrains reloaded = PinnedTrains.decode(shuffled, T0 + 10 * MIN);

        assertEquals(Arrays.asList(REF_A, REF_B), new ArrayList<>(reloaded.refs()));
    }

    @Test
    public void forgetsPinsTooOldToStillDesignateATrain() {
        // Un journeyRef IDFM est daté : passé la journée il ne résout plus rien.
        List<String> entries = Arrays.asList(
                REF_A + "|" + T0,
                REF_B + "|" + (T0 + PinnedTrains.MAX_AGE_MS - MIN));

        PinnedTrains reloaded = PinnedTrains.decode(entries, T0 + PinnedTrains.MAX_AGE_MS + MIN);

        assertFalse(reloaded.isPinned(REF_A));
        assertTrue(reloaded.isPinned(REF_B));
    }

    @Test
    public void purgeDropsExpiredPins() {
        PinnedTrains pinned = new PinnedTrains();
        pinned.toggle(REF_A, T0);

        pinned.purge(T0 + PinnedTrains.MAX_AGE_MS);
        assertTrue(pinned.isPinned(REF_A));

        pinned.purge(T0 + PinnedTrains.MAX_AGE_MS + 1);
        assertTrue(pinned.isEmpty());
    }

    @Test
    public void ignoresCorruptedEntries() {
        // Une préférence bricolée à la main ne doit pas faire tomber l'onglet.
        List<String> entries = Arrays.asList("", "sans-separateur", "|123", REF_A + "|pas-un-nombre",
                REF_B + "|" + T0);

        PinnedTrains reloaded = PinnedTrains.decode(entries, T0);

        assertEquals(Collections.singletonList(REF_B), new ArrayList<>(reloaded.refs()));
    }

    @Test
    public void decodeToleratesNull() {
        assertTrue(PinnedTrains.decode(null, T0).isEmpty());
    }

    @Test
    public void keepsRefsContainingTheSeparator() {
        // Improbable côté IDFM, mais l'encodage coupe au dernier séparateur
        // précisément pour que ça reste vrai.
        String weird = "SNCF|2024:135642";
        PinnedTrains pinned = new PinnedTrains();
        pinned.toggle(weird, T0);

        Set<String> encoded = pinned.encode();
        assertTrue(PinnedTrains.decode(encoded, T0).isPinned(weird));
    }
}
