package com.alixpat.vigie.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Formats de date partagés. {@link java.time.format.DateTimeFormatter} est
 * thread-safe par design — pas besoin de {@link ThreadLocal} comme avec
 * {@link java.text.SimpleDateFormat}.
 *
 * Les helpers acceptent des {@link Date} pour rester compatibles avec les
 * call sites historiques (Paho, JSON parsing, etc.) ; conversion vers la
 * timezone locale en interne.
 */
public final class DateFormats {

    private DateFormats() {}

    private static final ZoneId LOCAL_ZONE = ZoneId.systemDefault();

    private static final DateTimeFormatter HHMM           = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter COLON_SS       = DateTimeFormatter.ofPattern(":ss");
    private static final DateTimeFormatter HHMMSS         = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DDMM_HHMMSS    = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");
    private static final DateTimeFormatter ISO_WITH_TZ    = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");
    private static final DateTimeFormatter ISO_NO_TZ      = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** {@code "HH:mm"} en zone locale. */
    public static String formatHhmm(Date d) {
        return format(d, HHMM);
    }

    /** {@code ":ss"} (suffixe pour afficher les secondes après {@code HH:mm}). */
    public static String formatColonSeconds(Date d) {
        return format(d, COLON_SS);
    }

    /** {@code "HH:mm:ss"} en zone locale. */
    public static String formatHhmmss(Date d) {
        return format(d, HHMMSS);
    }

    /** {@code "dd/MM HH:mm:ss"} en zone locale. */
    public static String formatDdMmHhmmss(Date d) {
        return format(d, DDMM_HHMMSS);
    }

    private static String format(Date d, DateTimeFormatter fmt) {
        return fmt.format(d.toInstant().atZone(LOCAL_ZONE));
    }

    /**
     * Parse un timestamp ISO 8601 en gérant trois variantes :
     * avec offset ({@code +HH:mm} / {@code -HH:mm} / {@code Z}), sans offset,
     * et avec millisecondes (qui sont strippées — les patterns ne les supportent pas).
     *
     * Reproduit le comportement du précédent {@code TrainFragment.parseIsoDateTime}.
     *
     * @return un {@link Date} en epoch millis, ou {@code null} si non parseable.
     */
    public static Date parseIsoDateTime(String isoStr) {
        if (isoStr == null || isoStr.isEmpty()) return null;

        String cleaned = stripFractionalSeconds(isoStr);

        if (hasOffset(cleaned)) {
            try {
                return Date.from(OffsetDateTime.parse(cleaned, ISO_WITH_TZ).toInstant());
            } catch (DateTimeParseException ignored) {}
        }

        String noTz = stripOffset(cleaned);
        try {
            return Date.from(LocalDateTime.parse(noTz, ISO_NO_TZ).atZone(LOCAL_ZONE).toInstant());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Comme {@link #parseIsoDateTime} mais retourne 0 si non parseable (pratique pour comparer). */
    public static long parseIsoToMillis(String isoStr) {
        Date d = parseIsoDateTime(isoStr);
        return d != null ? d.getTime() : 0L;
    }

    private static String stripFractionalSeconds(String s) {
        int dotIdx = s.indexOf('.');
        if (dotIdx < 0) return s;
        int tzStart = s.indexOf('+', dotIdx);
        if (tzStart < 0) tzStart = s.indexOf('Z', dotIdx);
        if (tzStart < 0) tzStart = s.indexOf('-', dotIdx + 1);
        if (tzStart > 0) {
            return s.substring(0, dotIdx) + s.substring(tzStart);
        }
        return s.substring(0, dotIdx);
    }

    private static boolean hasOffset(String s) {
        return s.contains("+") || s.endsWith("Z") || s.matches(".*-\\d{2}:\\d{2}$");
    }

    private static String stripOffset(String s) {
        if (s.contains("+")) {
            return s.substring(0, s.lastIndexOf('+'));
        }
        if (s.endsWith("Z")) {
            return s.substring(0, s.length() - 1);
        }
        if (s.matches(".*-\\d{2}:\\d{2}$")) {
            return s.substring(0, s.length() - 6);
        }
        return s;
    }
}
