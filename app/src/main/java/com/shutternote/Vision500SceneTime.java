package com.shutternote;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Weak local capture-time evidence; never reads the processing clock or file modification time. */
final class Vision500SceneTime {
    static final String RECIPE_SUFFIX = "-scene-time-v1";
    static boolean validHour(double hour) { return Double.isFinite(hour) && hour >= 0 && hour < 24; }

    static double dayWeight(double photo, double captureHour) {
        if (!Double.isFinite(photo) || photo < 0 || photo > 1) throw new IllegalArgumentException("Invalid scene weight");
        if (!validHour(captureHour)) return photo;
        double clock = .5 + .5 * Math.cos((captureHour - 12) * Math.PI / 12);
        // At most 10%; strong photographic evidence suppresses time and preserves pure branches.
        double influence = .10 * 4 * photo * (1 - photo);
        double adjusted = photo + influence * (clock - photo);
        if (photo < .5) return Math.min(adjusted, Math.nextDown(.5));
        if (photo > .5) return Math.max(adjusted, Math.nextUp(.5));
        return photo; // A tie remains ambiguous; time alone does not choose the scene.
    }

    static double fromSnapshot(long capturedAt, int offsetMinutes) {
        if (capturedAt <= 0 || offsetMinutes < -720 || offsetMinutes > 840) return Double.NaN;
        long seconds = Math.floorMod(capturedAt / 1000, 86400);
        return Math.floorMod(seconds + offsetMinutes * 60L, 86400) / 3600.;
    }

    static double fromExif(String date, String offset) {
        if (date == null || !date.matches("[0-9]{4}:[0-9]{2}:[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}") ||
                offset == null || !offset.matches("[+-][0-9]{2}:[0-9]{2}")) return Double.NaN;
        int hours = Integer.parseInt(offset.substring(1, 3)), minutes = Integer.parseInt(offset.substring(4, 6));
        int total = (hours * 60 + minutes) * (offset.charAt(0) == '-' ? -1 : 1);
        if (minutes > 59 || total < -720 || total > 840) return Double.NaN;
        SimpleDateFormat format = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.ROOT);
        format.setLenient(false); format.setTimeZone(TimeZone.getTimeZone("GMT"));
        ParsePosition position = new ParsePosition(0); Date parsed = format.parse(date, position);
        if (parsed == null || position.getIndex() != date.length()) return Double.NaN;
        Calendar local = Calendar.getInstance(TimeZone.getTimeZone("GMT"), Locale.ROOT); local.setTime(parsed);
        // DateTimeOriginal already represents local wall time. Do not convert using today's zone.
        return local.get(Calendar.HOUR_OF_DAY) + local.get(Calendar.MINUTE) / 60. + local.get(Calendar.SECOND) / 3600.;
    }
}
