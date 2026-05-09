package com.alixpat.vigie.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Date;

public class DateFormatsTest {

    // 2026-03-12T08:14:03Z verified via TimeUnit / java.time.Instant.parse
    private static final long EPOCH_2026_03_12_08_14_03_UTC = 1773303243000L;

    // --- parsing ---

    @Test
    public void parsesIsoWithZuluOffset() {
        Date d = DateFormats.parseIsoDateTime("2026-03-12T08:14:03Z");
        assertNotNull(d);
        assertEquals(EPOCH_2026_03_12_08_14_03_UTC, d.getTime());
    }

    @Test
    public void parsesIsoWithExplicitOffset() {
        // 09:14:03+01:00 == 08:14:03Z
        Date d = DateFormats.parseIsoDateTime("2026-03-12T09:14:03+01:00");
        assertNotNull(d);
        assertEquals(EPOCH_2026_03_12_08_14_03_UTC, d.getTime());
    }

    @Test
    public void parsesIsoWithNegativeOffset() {
        // 03:14:03-05:00 == 08:14:03Z
        Date d = DateFormats.parseIsoDateTime("2026-03-12T03:14:03-05:00");
        assertNotNull(d);
        assertEquals(EPOCH_2026_03_12_08_14_03_UTC, d.getTime());
    }

    @Test
    public void stripsFractionalSecondsBeforeParsing() {
        // Le pattern "yyyy-MM-dd'T'HH:mm:ssXXX" ne supporte pas .SSS — DateFormats
        // doit le stripper, comme le faisait l'ancien TrainFragment.parseIsoDateTime.
        Date d = DateFormats.parseIsoDateTime("2026-03-12T08:14:03.456Z");
        assertNotNull(d);
        assertEquals(EPOCH_2026_03_12_08_14_03_UTC, d.getTime());
    }

    @Test
    public void parsesIsoWithoutOffsetIsRoundTrippable() {
        // Sans offset, le parseur l'interprète en zone locale. On teste le round-trip
        // pour rester indépendant de la TZ du runner CI.
        Date d = DateFormats.parseIsoDateTime("2026-03-12T08:14:03");
        assertNotNull(d);
        assertEquals("08:14:03", DateFormats.formatHhmmss(d));
    }

    @Test
    public void parseReturnsNullForNullOrEmpty() {
        assertNull(DateFormats.parseIsoDateTime(null));
        assertNull(DateFormats.parseIsoDateTime(""));
    }

    @Test
    public void parseReturnsNullForGarbage() {
        assertNull(DateFormats.parseIsoDateTime("not a date"));
        assertNull(DateFormats.parseIsoDateTime("2026"));
        assertNull(DateFormats.parseIsoDateTime("12/03/2026"));
    }

    @Test
    public void parseToMillisReturnsZeroForUnparseable() {
        assertEquals(0L, DateFormats.parseIsoToMillis(null));
        assertEquals(0L, DateFormats.parseIsoToMillis(""));
        assertEquals(0L, DateFormats.parseIsoToMillis("garbage"));
    }

    @Test
    public void parseToMillisReturnsEpochMillisOnSuccess() {
        assertEquals(EPOCH_2026_03_12_08_14_03_UTC,
                DateFormats.parseIsoToMillis("2026-03-12T08:14:03Z"));
    }

    // --- formatting ---
    // On évite de coder une heure absolue (dépendrait de la TZ du runner CI). À la
    // place : on vérifie le format via regex et on s'assure du round-trip parse↔format
    // en zone sans-offset.

    @Test
    public void formatHhmmShape() {
        Date now = new Date();
        String out = DateFormats.formatHhmm(now);
        assertTrue("expected HH:mm but was " + out, out.matches("\\d{2}:\\d{2}"));
    }

    @Test
    public void formatHhmmssShape() {
        Date now = new Date();
        String out = DateFormats.formatHhmmss(now);
        assertTrue("expected HH:mm:ss but was " + out, out.matches("\\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void formatColonSecondsShape() {
        Date now = new Date();
        String out = DateFormats.formatColonSeconds(now);
        assertTrue("expected :ss but was " + out, out.matches(":\\d{2}"));
    }

    @Test
    public void formatDdMmHhmmssShape() {
        Date now = new Date();
        String out = DateFormats.formatDdMmHhmmss(now);
        assertTrue("expected dd/MM HH:mm:ss but was " + out,
                out.matches("\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    public void localParseAndFormatRoundTrip() {
        // Indépendant de la TZ du runner : on parse une heure sans offset, on la formate,
        // on doit retomber sur la même heure.
        Date d = DateFormats.parseIsoDateTime("2026-03-12T08:14:03");
        assertEquals("08:14", DateFormats.formatHhmm(d));
        assertEquals("08:14:03", DateFormats.formatHhmmss(d));
    }

    @Test
    public void formattingIsThreadSafe() throws InterruptedException {
        // SimpleDateFormat aurait corrompu le résultat ici. DateTimeFormatter ne devrait pas.
        final int threads = 8;
        final int iterations = 1000;
        Thread[] workers = new Thread[threads];
        final boolean[] failed = {false};
        for (int t = 0; t < threads; t++) {
            workers[t] = new Thread(() -> {
                for (int i = 0; i < iterations; i++) {
                    String out = DateFormats.formatHhmmss(new Date(EPOCH_2026_03_12_08_14_03_UTC + i * 1000L));
                    if (out.length() != 8 || out.charAt(2) != ':' || out.charAt(5) != ':') {
                        failed[0] = true;
                        return;
                    }
                }
            });
            workers[t].start();
        }
        for (Thread w : workers) w.join();
        assertTrue("Thread-safety violation in DateFormats", !failed[0]);
    }
}
