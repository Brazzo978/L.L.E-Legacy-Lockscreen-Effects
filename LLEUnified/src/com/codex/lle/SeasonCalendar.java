package com.codex.lle;

import java.util.Calendar;

/** One season rule shared by the doodle, unlock renderer, lock audio and previews. */
final class SeasonCalendar {
    static final int EUROPE_AMERICA = 0;
    static final int CHINA_TRADITIONAL_APPROXIMATE = 1;

    static final int SPRING = 0;
    static final int SUMMER = 1;
    static final int AUTUMN = 2;
    static final int WINTER = 3;

    private SeasonCalendar() {
    }

    static int normalize(int profile) {
        return profile == CHINA_TRADITIONAL_APPROXIMATE
                ? profile : EUROPE_AMERICA;
    }

    static int resolve(int selectedSeason, int profile, Calendar today) {
        if (selectedSeason >= SPRING && selectedSeason <= WINTER) {
            return selectedSeason;
        }
        int month = today.get(Calendar.MONTH);
        if (normalize(profile) == CHINA_TRADITIONAL_APPROXIMATE) {
            // The traditional seasons start near Li Chun, Li Xia, Li Qiu and Li Dong.
            // Actual solar-term instants vary by year; these civil dates are approximate.
            int day = today.get(Calendar.DAY_OF_MONTH);
            int monthDay = (month + 1) * 100 + day;
            if (monthDay >= 1107 || monthDay < 204) {
                return WINTER;
            }
            if (monthDay < 505) {
                return SPRING;
            }
            if (monthDay < 807) {
                return SUMMER;
            }
            return AUTUMN;
        }
        int northern;
        if (month >= Calendar.MARCH && month <= Calendar.MAY) {
            northern = SPRING;
        } else if (month >= Calendar.JUNE && month <= Calendar.AUGUST) {
            northern = SUMMER;
        } else if (month >= Calendar.SEPTEMBER && month <= Calendar.NOVEMBER) {
            northern = AUTUMN;
        } else {
            northern = WINTER;
        }
        return northern;
    }

    static String seasonName(int season) {
        switch (season) {
            case SPRING:
                return "Spring";
            case SUMMER:
                return "Summer";
            case AUTUMN:
                return "Autumn";
            default:
                return "Winter";
        }
    }
}
