package com.shutternote;

import static org.junit.Assert.*;
import org.junit.Test;
import java.util.Calendar;
import java.util.TimeZone;

public class FilmRollRulesTest {
    private static long day(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.clear(); calendar.set(year, month - 1, day);
        return calendar.getTimeInMillis();
    }

    @Test public void customFramesAllowHalfFrameAndMediumFormatCountsWithoutAutomaticChanges() {
        for (String count : new String[]{"1", "12", "16", "24", "36", "48", "72", "999", " 36 "})
            assertEquals(Integer.parseInt(count.trim()), FilmRollRules.parseFrameCount(count));
        for (String invalid : new String[]{"", "0", "1000", "-1", "1.5", "NaN", "24컷", "999999999999"})
            assertEquals(invalid, -1, FilmRollRules.parseFrameCount(invalid));
        assertEquals(-1, FilmRollRules.parseFrameCount(null));
    }

    @Test public void sameDayAndRangeAreDifferentDisplayValues() {
        long start = day(2026, 10, 3), end = day(2026, 10, 11);
        assertEquals("2026.10.03", FilmRollRules.period(start, start));
        assertEquals("2026.10.03 ~ 10.11", FilmRollRules.period(start, end));
        assertEquals("2026.12.31 ~ 2027.01.01", FilmRollRules.period(day(2026, 12, 31), day(2027, 1, 1)));
    }

    @Test public void utcCalendarDatesDoNotMoveInNegativeOrPositiveTimezones() {
        TimeZone previous = TimeZone.getDefault();
        try {
            for (String zone : new String[]{"Pacific/Honolulu", "Asia/Seoul", "Pacific/Kiritimati"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                assertEquals("2026.10.03", FilmRollRules.date(day(2026, 10, 3)));
                assertEquals("1970.01.01", FilmRollRules.date(0));
            }
        } finally { TimeZone.setDefault(previous); }
    }

    @Test public void optionalDatesAreAllowedButIncompleteOrReversedPeriodIsRejected() {
        long absent = FilmRollRules.NO_DATE, start = day(2026, 10, 3), end = day(2026, 10, 11);
        assertTrue(FilmRollRules.validPeriod(absent, absent));
        assertTrue(FilmRollRules.validPeriod(start, start));
        assertTrue(FilmRollRules.validPeriod(start, end));
        assertFalse(FilmRollRules.validPeriod(start, absent));
        assertFalse(FilmRollRules.validPeriod(absent, end));
        assertFalse(FilmRollRules.validPeriod(end, start));
        assertTrue(FilmRollRules.validLabDates(absent, end));
        assertTrue(FilmRollRules.validLabDates(start, absent));
        assertTrue(FilmRollRules.validLabDates(start, start));
        assertFalse(FilmRollRules.validLabDates(end, start));
    }

    @Test public void optionalFocalLengthAcceptsOnlyFinitePositiveNumericValues() {
        for (String valid : new String[]{"", "35", "37.5", "37,5", "2000", "0.5"}) assertTrue(valid, FilmRollRules.validFocalLength(valid));
        for (String invalid : new String[]{"0", "-35", "35mm", "NaN", "Infinity", "2001", "1e3", "1..2"})
            assertFalse(invalid, FilmRollRules.validFocalLength(invalid));
    }
}
