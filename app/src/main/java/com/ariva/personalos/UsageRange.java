package com.ariva.personalos;

import java.util.Calendar;

public class UsageRange {
    public static final long DAY_MS = 24L * 60L * 60L * 1000L;

    public final long start;
    public final long end;

    public UsageRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public static UsageRange today() {
        long start = startOfDay(System.currentTimeMillis());
        return new UsageRange(start, start + DAY_MS);
    }

    public static UsageRange thisWeek() {
        long start = startOfWeek(System.currentTimeMillis());
        return new UsageRange(start, start + 7L * DAY_MS);
    }

    public static UsageRange thisMonth() {
        long start = startOfMonth(System.currentTimeMillis());
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        calendar.add(Calendar.MONTH, 1);
        return new UsageRange(start, calendar.getTimeInMillis());
    }

    public long dayCount() {
        return Math.max(1, (end - start) / DAY_MS);
    }

    public static long startOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long startOfWeek(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static long startOfMonth(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}
