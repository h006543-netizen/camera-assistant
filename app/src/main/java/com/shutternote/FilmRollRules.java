package com.shutternote;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Record-only validation; never changes exposure settings or camera profiles. */
final class FilmRollRules {
    static final long NO_DATE = Long.MIN_VALUE;

    private FilmRollRules() { }

    static int parseFrameCount(String value) {
        if (value == null || !value.trim().matches("[0-9]{1,3}")) return -1;
        int count = Integer.parseInt(value.trim());
        return count >= 1 && count <= 999 ? count : -1;
    }

    static boolean validFocalLength(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        if (!value.trim().matches("[0-9]+(?:[.,][0-9]+)?")) return false;
        try {
            double mm = Double.parseDouble(value.trim().replace(',', '.'));
            return Double.isFinite(mm) && mm > 0 && mm <= 2000;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    static boolean validPeriod(long start, long end) {
        if (start == NO_DATE || end == NO_DATE) return start == end;
        return end >= start;
    }

    static boolean validLabDates(long received, long completed) {
        return received == NO_DATE || completed == NO_DATE || completed >= received;
    }

    static String date(long utcDay) {
        if (utcDay == NO_DATE) return "";
        return formatDate("yyyy.MM.dd", utcDay);
    }

    static String period(long start, long end) {
        if (!validPeriod(start, end) || start == NO_DATE) return "";
        if (start == end) return date(start);
        String first = date(start), last = date(end);
        return first.substring(0, 4).equals(last.substring(0, 4))
                ? first + " ~ " + formatDate("MM.dd", end) : first + " ~ " + last;
    }

    private static String formatDate(String pattern, long value) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ROOT);
        // MaterialDatePicker returns a UTC calendar day, not a local instant.
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(value));
    }
}
