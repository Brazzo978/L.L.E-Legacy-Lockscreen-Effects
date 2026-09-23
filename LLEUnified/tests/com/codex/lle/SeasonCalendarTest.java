package com.codex.lle;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/** The calendar choice must agree across automatic doodle, effect, audio and preview. */
public final class SeasonCalendarTest {
    private SeasonCalendarTest() {
    }

    public static void main(String[] args) {
        assertSeason("Italy in mid-August", SeasonCalendar.SUMMER,
                SeasonCalendar.EUROPE_AMERICA, 2026, 8, 15);
        assertSeason("China in mid-August", SeasonCalendar.AUTUMN,
                SeasonCalendar.CHINA_TRADITIONAL_APPROXIMATE, 2026, 8, 15);
        assertSeason("Italy starts autumn Sep 1", SeasonCalendar.AUTUMN,
                SeasonCalendar.EUROPE_AMERICA, 2026, 9, 1);
        assertSeason("Italy stays summer Aug 31", SeasonCalendar.SUMMER,
                SeasonCalendar.EUROPE_AMERICA, 2026, 8, 31);
        assertSeason("China starts spring near Li Chun", SeasonCalendar.SPRING,
                SeasonCalendar.CHINA_TRADITIONAL_APPROXIMATE, 2026, 2, 4);
        assertSeason("China starts summer near Li Xia", SeasonCalendar.SUMMER,
                SeasonCalendar.CHINA_TRADITIONAL_APPROXIMATE, 2026, 5, 5);
        assertSeason("China starts autumn near Li Qiu", SeasonCalendar.AUTUMN,
                SeasonCalendar.CHINA_TRADITIONAL_APPROXIMATE, 2026, 8, 7);
        assertSeason("China starts winter near Li Dong", SeasonCalendar.WINTER,
                SeasonCalendar.CHINA_TRADITIONAL_APPROXIMATE, 2026, 11, 7);
        assertSeason("explicit winter is not remapped", SeasonCalendar.WINTER,
                SeasonCalendar.EUROPE_AMERICA, 2026, 8, 15,
                SeasonCalendar.WINTER);
        if (SeasonCalendar.normalize(99) != SeasonCalendar.EUROPE_AMERICA) {
            throw new AssertionError("unknown calendar must fall back to Europe/America");
        }
    }

    private static void assertSeason(String label, int expected, int profile,
            int year, int month, int day) {
        assertSeason(label, expected, profile, year, month, day, -1);
    }

    private static void assertSeason(String label, int expected, int profile,
            int year, int month, int day, int selected) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        calendar.clear();
        calendar.set(year, month - 1, day);
        int actual = SeasonCalendar.resolve(selected, profile, calendar);
        if (actual != expected) {
            throw new AssertionError(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
