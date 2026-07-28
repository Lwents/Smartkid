package com.example.smartkid.feature.student.exam;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Keeps the countdown tied to the deadline issued by the server. */
final class ExamTiming {
    private ExamTiming() { }

    static int remainingSeconds(String deadlineAt, long nowMillis, int fallbackSeconds) {
        long deadlineMillis = parseIsoMillis(deadlineAt);
        if (deadlineMillis <= 0) return Math.max(1, fallbackSeconds);
        long remainingMillis = deadlineMillis - nowMillis;
        if (remainingMillis <= 0) return 1;
        return (int) Math.min(Integer.MAX_VALUE, (remainingMillis + 999L) / 1000L);
    }

    private static long parseIsoMillis(String raw) {
        if (raw == null || raw.trim().isEmpty()) return -1;
        try {
            String value = normalizeFraction(raw.trim());
            SimpleDateFormat parser = new SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
            parser.setLenient(false);
            Date parsed = parser.parse(value);
            return parsed == null ? -1 : parsed.getTime();
        } catch (Exception ignored) {
            return -1;
        }
    }

    private static String normalizeFraction(String value) {
        int zoneIndex = value.endsWith("Z") ? value.length() - 1 : -1;
        if (zoneIndex < 0) {
            int plus = value.indexOf('+', 19);
            int minus = value.indexOf('-', 19);
            zoneIndex = plus >= 0 ? plus : minus;
        }
        if (zoneIndex < 0) {
            value += "Z";
            zoneIndex = value.length() - 1;
        }
        int dot = value.indexOf('.', 19);
        if (dot < 0 || dot > zoneIndex) {
            return value.substring(0, zoneIndex) + ".000" + value.substring(zoneIndex);
        }
        String fraction = value.substring(dot + 1, zoneIndex);
        if (fraction.length() > 3) fraction = fraction.substring(0, 3);
        while (fraction.length() < 3) fraction += "0";
        return value.substring(0, dot + 1) + fraction + value.substring(zoneIndex);
    }
}
