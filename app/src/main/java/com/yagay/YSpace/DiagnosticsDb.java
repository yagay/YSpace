package com.yagay.YSpace;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class DiagnosticsDb extends SQLiteOpenHelper {
    static final class PackageRow {
        final String packageName;
        final int totalCount;
        final int realCount;
        final long lastTime;
        final long lastObserverTime;

        PackageRow(String packageName, int totalCount, int realCount,
                   long lastTime, long lastObserverTime) {
            this.packageName = packageName;
            this.totalCount = totalCount;
            this.realCount = realCount;
            this.lastTime = lastTime;
            this.lastObserverTime = lastObserverTime;
        }

        boolean observerInjected() {
            return lastObserverTime > 0;
        }
    }

    static final class PackageStatus {
        final String packageName;
        final int totalCount;
        final int realCount;
        final int sessionCount;
        final long lastTime;
        final long lastObserverTime;

        PackageStatus(String packageName, int totalCount, int realCount,
                      int sessionCount, long lastTime, long lastObserverTime) {
            this.packageName = packageName;
            this.totalCount = totalCount;
            this.realCount = realCount;
            this.sessionCount = sessionCount;
            this.lastTime = lastTime;
            this.lastObserverTime = lastObserverTime;
        }

        boolean observerInjected() {
            return lastObserverTime > 0;
        }
    }

    static final class EventRow {
        final long ts;
        final String session;
        final String packageName;
        final String process;
        final String type;
        final String object;
        final String api;
        final String result;

        EventRow(long ts, String session, String packageName, String process,
                 String type, String object, String api, String result) {
            this.ts = ts;
            this.session = session;
            this.packageName = packageName;
            this.process = process;
            this.type = type;
            this.object = object;
            this.api = api;
            this.result = result;
        }
    }

    private static volatile DiagnosticsDb instance;

    static DiagnosticsDb get(Context context) {
        DiagnosticsDb local = instance;
        if (local == null) {
            synchronized (DiagnosticsDb.class) {
                local = instance;
                if (local == null) instance = local = new DiagnosticsDb(context.getApplicationContext());
            }
        }
        return local;
    }

    private DiagnosticsDb(Context context) {
        super(context, "runtime-observer.db", null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "ts INTEGER NOT NULL," +
                "session TEXT NOT NULL," +
                "source_package TEXT NOT NULL," +
                "process TEXT," +
                "type TEXT NOT NULL," +
                "object TEXT," +
                "api TEXT," +
                "result TEXT)");
        db.execSQL("CREATE INDEX idx_events_pkg_ts ON events(source_package, ts DESC)");
        db.execSQL("CREATE INDEX idx_events_session ON events(session)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    void insert(Intent intent) {
        ContentValues v = new ContentValues();
        v.put("ts", intent.getLongExtra("ts", System.currentTimeMillis()));
        v.put("session", clip(intent.getStringExtra("session"), 160));
        v.put("source_package", clip(intent.getStringExtra("source_package"), 240));
        v.put("process", clip(intent.getStringExtra("process"), 240));
        v.put("type", clip(intent.getStringExtra("type"), 80));
        v.put("object", clip(intent.getStringExtra("object"), 1600));
        v.put("api", clip(intent.getStringExtra("api"), 600));
        v.put("result", clip(intent.getStringExtra("result"), 800));
        getWritableDatabase().insert("events", null, v);
    }

    List<PackageRow> packages() {
        ArrayList<PackageRow> rows = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT source_package, COUNT(*), " +
                "SUM(CASE WHEN type NOT IN ('session','observer') THEN 1 ELSE 0 END), " +
                "MAX(ts), " +
                "MAX(CASE WHEN type='observer' THEN ts ELSE 0 END) " +
                "FROM events GROUP BY source_package ORDER BY MAX(ts) DESC",
                null)) {
            while (c.moveToNext()) {
                rows.add(new PackageRow(
                        c.getString(0), c.getInt(1), c.getInt(2),
                        c.getLong(3), c.getLong(4)));
            }
        }
        return rows;
    }

    PackageStatus status(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return new PackageStatus("", 0, 0, 0, 0, 0);
        }

        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT COUNT(*), " +
                "SUM(CASE WHEN type NOT IN ('session','observer') THEN 1 ELSE 0 END), " +
                "COUNT(DISTINCT CASE WHEN type='session' THEN session END), " +
                "MAX(ts), " +
                "MAX(CASE WHEN type='observer' THEN ts ELSE 0 END) " +
                "FROM events WHERE source_package=?",
                new String[]{packageName})) {
            if (c.moveToFirst()) {
                return new PackageStatus(
                        packageName,
                        c.getInt(0),
                        c.isNull(1) ? 0 : c.getInt(1),
                        c.isNull(2) ? 0 : c.getInt(2),
                        c.isNull(3) ? 0 : c.getLong(3),
                        c.isNull(4) ? 0 : c.getLong(4));
            }
        }

        return new PackageStatus(packageName, 0, 0, 0, 0, 0);
    }

    List<EventRow> events(String packageName, int limit) {
        ArrayList<EventRow> rows = new ArrayList<>();
        String lim = String.valueOf(Math.max(1, Math.min(limit, 2000)));
        try (Cursor c = getReadableDatabase().query(
                "events",
                new String[]{"ts","session","source_package","process","type","object","api","result"},
                "source_package=?",
                new String[]{packageName},
                null, null, "ts DESC, id DESC", lim)) {
            while (c.moveToNext()) {
                rows.add(new EventRow(
                        c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getString(6), c.getString(7)));
            }
        }
        return rows;
    }

    void clear(String packageName) {
        if (packageName == null) getWritableDatabase().delete("events", null, null);
        else getWritableDatabase().delete("events", "source_package=?", new String[]{packageName});
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
