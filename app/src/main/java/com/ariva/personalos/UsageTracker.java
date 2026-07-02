package com.ariva.personalos;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Process;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class UsageTracker {
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final long HOUR_MS = 60L * 60L * 1000L;

    private UsageTracker() {
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) {
            return false;
        }
        int mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static void syncRecentDays(Context context, int days) {
        long today = UsageRange.startOfDay(System.currentTimeMillis());
        long start = today - Math.max(0, days - 1) * DAY_MS;
        syncRange(context, start, System.currentTimeMillis());
    }

    public static void syncRange(Context context, long startInclusive, long endExclusive) {
        if (!hasUsageAccess(context) || endExclusive <= startInclusive) {
            return;
        }

        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) {
            return;
        }

        long clearStart = UsageRange.startOfDay(startInclusive);
        long clearEnd = UsageRange.startOfDay(endExclusive) + DAY_MS;
        UsageDbHelper db = new UsageDbHelper(context);
        db.clearForDates(clearStart, clearEnd);

        Map<String, Long> openStarts = new HashMap<>();
        UsageEvents events = manager.queryEvents(startInclusive, endExclusive);
        UsageEvents.Event event = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(event);
            String packageName = event.getPackageName();
            if (packageName == null || packageName.equals(context.getPackageName())) {
                continue;
            }

            int type = event.getEventType();
            if (isForegroundEvent(type)) {
                if (!openStarts.containsKey(packageName)) {
                    openStarts.put(packageName, event.getTimeStamp());
                }
            } else if (isBackgroundEvent(type)) {
                Long openStart = openStarts.remove(packageName);
                if (openStart != null && event.getTimeStamp() > openStart) {
                    recordSession(context, db, packageName, openStart, event.getTimeStamp());
                }
            }
        }

        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : openStarts.entrySet()) {
            long end = Math.min(now, endExclusive);
            if (end > entry.getValue()) {
                recordSession(context, db, entry.getKey(), entry.getValue(), end);
            }
        }

        rebuildAggregates(db, clearStart, clearEnd);
        db.close();
    }

    private static void recordSession(Context context, UsageDbHelper db, String packageName, long start, long end) {
        String appName = loadAppName(context, packageName);
        String category = inferCategory(context, packageName);
        db.upsertApp(packageName, appName, category, end, isInstalled(context, packageName));

        long cursor = start;
        while (cursor < end) {
            long dateStart = UsageRange.startOfDay(cursor);
            long segmentEnd = Math.min(end, dateStart + DAY_MS);
            db.addSession(packageName, cursor, segmentEnd, segmentEnd - cursor, dateStart);
            cursor = segmentEnd;
        }
    }

    private static void rebuildAggregates(UsageDbHelper db, long startDateInclusive, long endDateExclusive) {
        android.database.Cursor sessions = db.getSessions(startDateInclusive, endDateExclusive);
        Map<String, DayBucket> dayBuckets = new HashMap<>();
        Map<String, Long> hourBuckets = new HashMap<>();
        try {
            while (sessions.moveToNext()) {
                String packageName = sessions.getString(sessions.getColumnIndexOrThrow("package_name"));
                long start = sessions.getLong(sessions.getColumnIndexOrThrow("start_time"));
                long end = sessions.getLong(sessions.getColumnIndexOrThrow("end_time"));
                long dateStart = sessions.getLong(sessions.getColumnIndexOrThrow("date_start"));
                String dayKey = packageName + "|" + dateStart;
                DayBucket day = dayBuckets.get(dayKey);
                if (day == null) {
                    day = new DayBucket(packageName, dateStart);
                    dayBuckets.put(dayKey, day);
                }
                day.totalMs += end - start;
                day.launchCount += 1;
                day.firstOpen = day.firstOpen == 0 ? start : Math.min(day.firstOpen, start);
                day.lastOpen = Math.max(day.lastOpen, start);

                long cursor = start;
                while (cursor < end) {
                    long hourStart = startOfHour(cursor);
                    long segmentEnd = Math.min(end, hourStart + HOUR_MS);
                    int hour = hourOfDay(cursor);
                    String hourKey = packageName + "|" + dateStart + "|" + hour;
                    Long current = hourBuckets.get(hourKey);
                    hourBuckets.put(hourKey, (current == null ? 0L : current) + (segmentEnd - cursor));
                    cursor = segmentEnd;
                }
            }
        } finally {
            sessions.close();
        }

        for (DayBucket bucket : dayBuckets.values()) {
            db.addDailyUsage(bucket.packageName, bucket.dateStart, bucket.totalMs,
                    bucket.launchCount, bucket.firstOpen, bucket.lastOpen);
        }
        for (Map.Entry<String, Long> entry : hourBuckets.entrySet()) {
            String[] parts = entry.getKey().split("\\|");
            db.addHourlyUsage(parts[0], Long.parseLong(parts[1]), Integer.parseInt(parts[2]), entry.getValue());
        }
    }

    private static boolean isForegroundEvent(int type) {
        return type == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                type == UsageEvents.Event.ACTIVITY_RESUMED;
    }

    private static boolean isBackgroundEvent(int type) {
        return type == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                type == UsageEvents.Event.ACTIVITY_PAUSED;
    }

    private static String loadAppName(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            CharSequence label = pm.getApplicationLabel(info);
            return label == null ? packageName : label.toString();
        } catch (PackageManager.NameNotFoundException ex) {
            return packageName;
        }
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getApplicationInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ex) {
            return false;
        }
    }

    private static String inferCategory(Context context, String packageName) {
        PackageManager pm = context.getPackageManager();
        try {
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                switch (info.category) {
                    case ApplicationInfo.CATEGORY_GAME:
                        return "Games";
                    case ApplicationInfo.CATEGORY_AUDIO:
                    case ApplicationInfo.CATEGORY_VIDEO:
                    case ApplicationInfo.CATEGORY_IMAGE:
                        return "Media";
                    case ApplicationInfo.CATEGORY_PRODUCTIVITY:
                        return "Productivity";
                    case ApplicationInfo.CATEGORY_SOCIAL:
                        return "Social";
                    case ApplicationInfo.CATEGORY_NEWS:
                        return "News";
                    case ApplicationInfo.CATEGORY_MAPS:
                        return "Maps";
                    default:
                        break;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        String lower = packageName.toLowerCase(Locale.US);
        if (lower.contains("instagram") || lower.contains("facebook") || lower.contains("whatsapp") ||
                lower.contains("telegram") || lower.contains("snapchat") || lower.contains("twitter")) {
            return "Social";
        }
        if (lower.contains("youtube") || lower.contains("netflix") || lower.contains("spotify")) {
            return "Media";
        }
        if (lower.contains("game")) {
            return "Games";
        }
        return "Other";
    }

    private static long startOfHour(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static int hourOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        return calendar.get(Calendar.HOUR_OF_DAY);
    }

    private static class DayBucket {
        final String packageName;
        final long dateStart;
        long totalMs;
        int launchCount;
        long firstOpen;
        long lastOpen;

        DayBucket(String packageName, long dateStart) {
            this.packageName = packageName;
            this.dateStart = dateStart;
        }
    }
}
