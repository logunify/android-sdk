package com.logunify.logging.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import com.logunify.logging.event.Event;
import com.logunify.logging.event.EventRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Persistent FIFO queue implementation with Sqlite.
 */
class SqliteEventQueue {
    private final static String TABLE_NAME = "preflight_events";
    private final static int DEFAULT_MAX_SIZE = 5000;
    private final SQLiteDatabase db;
    private final int maxSize;
    private static AtomicLong sizeCache;

    public static class EventsDB extends SQLiteOpenHelper {
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "schema_log_events";
        private static EventsDB dbInstance;

        private static final String COLUMN_SERIALIZED_EVENT = "serialized_event";
        private static final String COLUMN_PROJECT_NAME = "project_name";
        private static final String COLUMN_SCHEMA_NAME = "schema_name";

        public static synchronized EventsDB getInstance(Context context) {
            if (dbInstance == null) {
                dbInstance = new EventsDB(context);
            }
            return dbInstance;
        }

        private EventsDB(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                    String.format(
                            "CREATE TABLE %s (id INTEGER PRIMARY KEY AUTOINCREMENT, %s TEXT, %s TEXT, %s TEXT);",
                            TABLE_NAME,
                            COLUMN_SERIALIZED_EVENT,
                            COLUMN_PROJECT_NAME,
                            COLUMN_SCHEMA_NAME
                    )
            );
        }

        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME + ";");
            onCreate(db);
        }

        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    /**
     * Constructor.
     *
     * @param context android context
     */
    public SqliteEventQueue(Context context) {
        this(context, DEFAULT_MAX_SIZE);
    }

    /**
     * Constructor.
     *
     * @param context android context
     * @param maxSize max size of the queue, older records will be overwritten
     */
    public SqliteEventQueue(Context context, int maxSize) {
        Utils.requireNonNull(context);
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be greater than 0");
        }
        EventsDB dbHelper = EventsDB.getInstance(context);
        this.db = dbHelper.getWritableDatabase();
        this.maxSize = maxSize;
    }

    /**
     * Get size of the queue.
     *
     * @return size of the queue.
     */
    public long size() {
        if (sizeCache == null) {
            sizeCache = new AtomicLong(DatabaseUtils.queryNumEntries(db, TABLE_NAME));
        }
        return sizeCache.get();
    }

    /**
     * Pushes element to queue.
     */
    public void enqueue(Event event) {
        Utils.requireNonNull(event);
        db.execSQL(
                String.format(
                        "INSERT INTO %s (%s, %s, %s) VALUES (?, ?, ?)",
                        TABLE_NAME,
                        EventsDB.COLUMN_SERIALIZED_EVENT,
                        EventsDB.COLUMN_PROJECT_NAME,
                        EventsDB.COLUMN_SCHEMA_NAME
                ),
                new Object[]{
                        event.serialize(),
                        event.getProjectName(),
                        event.getSchemaName()
                });
        if (sizeCache != null) {
            sizeCache.incrementAndGet();
        }
        if (size() > maxSize) {
            remove(1);
        }
    }

    /**
     * Retrieves up to specified amount of elements from queue, without removing them.
     *
     * @param max max number of elements to return.
     * @return list of elements
     */
    public List<EventRecord> peek(int max) {
        if (max <= 0) {
            throw new IllegalArgumentException("max must be greater than 0");
        }

        List<EventRecord> results = new ArrayList<>();
        Cursor cursor = db.query(
                TABLE_NAME,
                new String[]{
                        EventsDB.COLUMN_SERIALIZED_EVENT,
                        EventsDB.COLUMN_PROJECT_NAME,
                        EventsDB.COLUMN_SCHEMA_NAME
                },
                null,
                null,
                null,
                null,
                "id asc",
                String.valueOf(max)
        );
        try {
            while (cursor.moveToNext()) {
                @SuppressLint("Range") String serializedEvent = cursor.getString(cursor.getColumnIndex(EventsDB.COLUMN_SERIALIZED_EVENT));
                @SuppressLint("Range") String projectName = cursor.getString(cursor.getColumnIndex(EventsDB.COLUMN_PROJECT_NAME));
                @SuppressLint("Range") String schemaName = cursor.getString(cursor.getColumnIndex(EventsDB.COLUMN_SCHEMA_NAME));

                EventRecord eventRecord = new EventRecord(serializedEvent, schemaName, projectName);
                results.add(eventRecord);
            }
        } finally {
            if (cursor != null && !cursor.isClosed()) {
                cursor.close();
            }
        }
        return results;
    }

    /**
     * Removes up to specified amount of elements from queue.
     *
     * @param n amount of elements to remove.
     */
    public void remove(int n) {
        String deleteQuery = String.format(Utils.DEFAULT_LOCALE,
                "DELETE FROM %s WHERE `id` IN (SELECT `id` FROM %s ORDER BY `id` ASC limit %d);",
                TABLE_NAME, TABLE_NAME, n);
        db.execSQL(deleteQuery);
        SQLiteStatement stmt = db.compileStatement("SELECT CHANGES()");
        long result = stmt.simpleQueryForLong();
        if (sizeCache != null) {
            sizeCache.getAndAdd(-1L * result);
        }
        stmt.close();
    }

    void clear() {
        String deleteQuery = String.format(Utils.DEFAULT_LOCALE, "DELETE FROM %s", TABLE_NAME);
        db.execSQL(deleteQuery);
        SQLiteStatement stmt = db.compileStatement("SELECT CHANGES()");
        long result = stmt.simpleQueryForLong();
        if (sizeCache != null) {
            sizeCache.set(0L);
        }
        stmt.close();
    }
}
