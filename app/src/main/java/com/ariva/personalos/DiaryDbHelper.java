package com.ariva.personalos;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class DiaryDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "personal_diary.db";
    private static final int DB_VERSION = 1;

    DiaryDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE diary_entries (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "entry_date INTEGER NOT NULL UNIQUE," +
                "body TEXT NOT NULL," +
                "created_at INTEGER NOT NULL," +
                "updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_diary_entries_date ON diary_entries(entry_date DESC)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    void saveEntry(long entryDate, String body) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        Cursor existing = db.rawQuery(
                "SELECT created_at FROM diary_entries WHERE entry_date = ?",
                new String[]{String.valueOf(entryDate)});
        long createdAt = now;
        try {
            if (existing.moveToFirst()) {
                createdAt = existing.getLong(0);
            }
        } finally {
            existing.close();
        }

        ContentValues values = new ContentValues();
        values.put("entry_date", entryDate);
        values.put("body", body);
        values.put("created_at", createdAt);
        values.put("updated_at", now);
        db.insertWithOnConflict("diary_entries", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    String getEntry(long entryDate) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT body FROM diary_entries WHERE entry_date = ?",
                new String[]{String.valueOf(entryDate)});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : "";
        } finally {
            cursor.close();
        }
    }

    boolean hasEntry(long entryDate) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM diary_entries WHERE entry_date = ? LIMIT 1",
                new String[]{String.valueOf(entryDate)});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    Cursor getEntries() {
        return getReadableDatabase().rawQuery(
                "SELECT entry_date, body, updated_at FROM diary_entries ORDER BY entry_date DESC",
                null);
    }

    void deleteEntry(long entryDate) {
        getWritableDatabase().delete(
                "diary_entries", "entry_date = ?", new String[]{String.valueOf(entryDate)});
    }
}
