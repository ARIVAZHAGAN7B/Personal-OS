package com.ariva.personalos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

public class UsageDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "personal_usage.db";
    public static final int DB_VERSION = 2;

    public UsageDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE usage_apps (" +
                "package_name TEXT PRIMARY KEY," +
                "app_name TEXT NOT NULL," +
                "category TEXT NOT NULL DEFAULT 'Other'," +
                "first_seen_at INTEGER NOT NULL," +
                "last_seen_at INTEGER NOT NULL," +
                "is_installed INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE usage_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT NOT NULL," +
                "start_time INTEGER NOT NULL," +
                "end_time INTEGER NOT NULL," +
                "duration_ms INTEGER NOT NULL," +
                "date_start INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE daily_app_usage (" +
                "package_name TEXT NOT NULL," +
                "date_start INTEGER NOT NULL," +
                "total_foreground_ms INTEGER NOT NULL DEFAULT 0," +
                "launch_count INTEGER NOT NULL DEFAULT 0," +
                "first_open_time INTEGER," +
                "last_open_time INTEGER," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY(package_name, date_start))");
        db.execSQL("CREATE TABLE hourly_app_usage (" +
                "package_name TEXT NOT NULL," +
                "date_start INTEGER NOT NULL," +
                "hour INTEGER NOT NULL," +
                "foreground_ms INTEGER NOT NULL DEFAULT 0," +
                "PRIMARY KEY(package_name, date_start, hour))");
        createUsageLimitsTable(db);
        db.execSQL("CREATE INDEX idx_usage_sessions_date ON usage_sessions(date_start)");
        db.execSQL("CREATE INDEX idx_usage_sessions_package ON usage_sessions(package_name)");
        db.execSQL("CREATE INDEX idx_daily_usage_date ON daily_app_usage(date_start)");
        db.execSQL("CREATE INDEX idx_hourly_usage_date ON hourly_app_usage(date_start, hour)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createUsageLimitsTable(db);
        }
    }

    private void createUsageLimitsTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS app_usage_limits (" +
                "package_name TEXT PRIMARY KEY," +
                "daily_limit_ms INTEGER NOT NULL)");
    }

    public void upsertApp(String packageName, String appName, String category, long seenAt, boolean installed) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues insertValues = new ContentValues();
        insertValues.put("package_name", packageName);
        insertValues.put("app_name", appName);
        insertValues.put("category", category);
        insertValues.put("first_seen_at", seenAt);
        insertValues.put("last_seen_at", seenAt);
        insertValues.put("is_installed", installed ? 1 : 0);
        long inserted = db.insertWithOnConflict("usage_apps", null, insertValues, SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues updateValues = new ContentValues();
        updateValues.put("app_name", appName);
        updateValues.put("category", category);
        updateValues.put("last_seen_at", seenAt);
        updateValues.put("is_installed", installed ? 1 : 0);
        db.update("usage_apps", updateValues, "package_name = ?", new String[]{packageName});

        if (inserted != -1) {
            ContentValues firstSeen = new ContentValues();
            firstSeen.put("first_seen_at", seenAt);
            db.update("usage_apps", firstSeen, "package_name = ?", new String[]{packageName});
        }
    }

    public void clearForDates(long startDateInclusive, long endDateExclusive) {
        String[] args = new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)};
        SQLiteDatabase db = getWritableDatabase();
        db.delete("usage_sessions", "date_start >= ? AND date_start < ?", args);
        db.delete("daily_app_usage", "date_start >= ? AND date_start < ?", args);
        db.delete("hourly_app_usage", "date_start >= ? AND date_start < ?", args);
    }

    public void addSession(String packageName, long startTime, long endTime, long durationMs, long dateStart) {
        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("start_time", startTime);
        values.put("end_time", endTime);
        values.put("duration_ms", durationMs);
        values.put("date_start", dateStart);
        getWritableDatabase().insert("usage_sessions", null, values);
    }

    public Cursor getSessions(long startDateInclusive, long endDateExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT package_name, start_time, end_time, date_start FROM usage_sessions " +
                        "WHERE date_start >= ? AND date_start < ? ORDER BY start_time",
                new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)});
    }

    public void addDailyUsage(String packageName, long dateStart, long totalMs, int launchCount,
                              long firstOpenTime, long lastOpenTime) {
        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("date_start", dateStart);
        values.put("total_foreground_ms", totalMs);
        values.put("launch_count", launchCount);
        values.put("first_open_time", firstOpenTime > 0 ? firstOpenTime : null);
        values.put("last_open_time", lastOpenTime > 0 ? lastOpenTime : null);
        values.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict(
                "daily_app_usage",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void addHourlyUsage(String packageName, long dateStart, int hour, long foregroundMs) {
        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("date_start", dateStart);
        values.put("hour", hour);
        values.put("foreground_ms", foregroundMs);
        getWritableDatabase().insertWithOnConflict(
                "hourly_app_usage",
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public Cursor getTopApps(long startDateInclusive, long endDateExclusive, int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT d.package_name, COALESCE(a.app_name, d.package_name) AS app_name, " +
                        "COALESCE(a.category, 'Other') AS category, COALESCE(a.is_installed, 0) AS is_installed, " +
                        "SUM(d.total_foreground_ms) AS total_ms, " +
                        "SUM(d.launch_count) AS launches, " +
                        "MIN(d.first_open_time) AS first_open_time, " +
                        "MAX(d.last_open_time) AS last_open_time " +
                        "FROM daily_app_usage d " +
                        "LEFT JOIN usage_apps a ON a.package_name = d.package_name " +
                        "WHERE d.date_start >= ? AND d.date_start < ? " +
                        "GROUP BY d.package_name, a.app_name, a.category, a.is_installed " +
                        "ORDER BY total_ms DESC LIMIT ?",
                new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive), String.valueOf(limit)});
    }

    public Cursor getAppUsageSummary(String packageName, long startDateInclusive, long endDateExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT d.package_name, COALESCE(a.app_name, d.package_name) AS app_name, " +
                        "COALESCE(a.category, 'Other') AS category, COALESCE(a.is_installed, 0) AS is_installed, " +
                        "COALESCE(SUM(d.total_foreground_ms), 0) AS total_ms, " +
                        "COALESCE(SUM(d.launch_count), 0) AS launches, " +
                        "MIN(d.first_open_time) AS first_open_time, " +
                        "MAX(d.last_open_time) AS last_open_time, " +
                        "COUNT(CASE WHEN d.total_foreground_ms > 0 THEN 1 END) AS active_days " +
                        "FROM daily_app_usage d " +
                        "LEFT JOIN usage_apps a ON a.package_name = d.package_name " +
                        "WHERE d.package_name = ? AND d.date_start >= ? AND d.date_start < ? " +
                        "GROUP BY d.package_name, a.app_name, a.category, a.is_installed",
                new String[]{packageName, String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)});
    }

    public Cursor getFrequentOpenApps(long startDateInclusive, long endDateExclusive, int limit) {
        return getReadableDatabase().rawQuery(
                "SELECT d.package_name, COALESCE(a.app_name, d.package_name) AS app_name, " +
                        "COALESCE(a.category, 'Other') AS category, COALESCE(a.is_installed, 0) AS is_installed, " +
                        "SUM(d.total_foreground_ms) AS total_ms, SUM(d.launch_count) AS launches, " +
                        "MIN(d.first_open_time) AS first_open_time, MAX(d.last_open_time) AS last_open_time " +
                        "FROM daily_app_usage d LEFT JOIN usage_apps a ON a.package_name = d.package_name " +
                        "WHERE d.date_start >= ? AND d.date_start < ? " +
                        "GROUP BY d.package_name, a.app_name, a.category, a.is_installed " +
                        "HAVING launches >= 15 ORDER BY launches DESC LIMIT ?",
                new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive), String.valueOf(limit)});
    }

    public long getDailyLimit(String packageName) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT daily_limit_ms FROM app_usage_limits WHERE package_name = ?",
                new String[]{packageName});
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            cursor.close();
        }
    }

    public void setDailyLimit(String packageName, long dailyLimitMs) {
        SQLiteDatabase db = getWritableDatabase();
        if (dailyLimitMs <= 0) {
            db.delete("app_usage_limits", "package_name = ?", new String[]{packageName});
            return;
        }
        ContentValues values = new ContentValues();
        values.put("package_name", packageName);
        values.put("daily_limit_ms", dailyLimitMs);
        db.insertWithOnConflict("app_usage_limits", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void markInstalled(String packageName, boolean installed) {
        ContentValues values = new ContentValues();
        values.put("is_installed", installed ? 1 : 0);
        getWritableDatabase().update("usage_apps", values, "package_name = ?", new String[]{packageName});
    }

    public Cursor getDailyUsageForApp(String packageName, long startDateInclusive, long endDateExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT date_start, total_foreground_ms, launch_count, first_open_time, last_open_time " +
                        "FROM daily_app_usage " +
                        "WHERE package_name = ? AND date_start >= ? AND date_start < ? " +
                        "ORDER BY date_start",
                new String[]{packageName, String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)});
    }

    public Map<String, Long> getSummary(long startDateInclusive, long endDateExclusive) {
        HashMap<String, Long> summary = new HashMap<>();
        summary.put("total_ms", 0L);
        summary.put("launches", 0L);
        summary.put("app_count", 0L);
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(total_foreground_ms), 0) AS total_ms, " +
                        "COALESCE(SUM(launch_count), 0) AS launches, " +
                        "COUNT(DISTINCT package_name) AS app_count " +
                        "FROM daily_app_usage WHERE date_start >= ? AND date_start < ?",
                new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)});
        try {
            if (cursor.moveToFirst()) {
                summary.put("total_ms", cursor.getLong(cursor.getColumnIndexOrThrow("total_ms")));
                summary.put("launches", cursor.getLong(cursor.getColumnIndexOrThrow("launches")));
                summary.put("app_count", cursor.getLong(cursor.getColumnIndexOrThrow("app_count")));
            }
        } finally {
            cursor.close();
        }
        return summary;
    }

    public Cursor getHourlyUsage(long dateStart) {
        return getReadableDatabase().rawQuery(
                "SELECT hour, SUM(foreground_ms) AS total_ms FROM hourly_app_usage " +
                        "WHERE date_start = ? GROUP BY hour ORDER BY hour",
                new String[]{String.valueOf(dateStart)});
    }

    public Cursor getCategoryUsage(long startDateInclusive, long endDateExclusive) {
        return getReadableDatabase().rawQuery(
                "SELECT COALESCE(a.category, 'Other') AS category, " +
                        "SUM(d.total_foreground_ms) AS total_ms, SUM(d.launch_count) AS launches " +
                        "FROM daily_app_usage d " +
                        "LEFT JOIN usage_apps a ON a.package_name = d.package_name " +
                        "WHERE d.date_start >= ? AND d.date_start < ? " +
                        "GROUP BY COALESCE(a.category, 'Other') ORDER BY total_ms DESC",
                new String[]{String.valueOf(startDateInclusive), String.valueOf(endDateExclusive)});
    }

    public long getLastSyncTime() {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(MAX(updated_at), 0) FROM daily_app_usage",
                null);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        } finally {
            cursor.close();
        }
    }
}
