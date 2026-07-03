package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dagoal.db";
    private static final int DATABASE_VERSION = 9; // Incremented version to apply table modification

    private static final String CREATE_TABLE_USER = "CREATE TABLE user (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT DEFAULT 'Adventurer', " +
            "name TEXT, " +
            "age INTEGER, " +
            "level INTEGER DEFAULT 1, " +
            "gold INTEGER DEFAULT 0, " +
            "xp INTEGER DEFAULT 0, " +
            "streak INTEGER DEFAULT 0, " +
            "last_completed_date TEXT DEFAULT '');"; // Track streak completion timeline

    private static final String CREATE_TABLE_PREFERENCES = "CREATE TABLE preferences (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "user_id INTEGER, " +
            "activity_type TEXT, " +
            "difficulty TEXT);";

    private static final String CREATE_TABLE_DAILY_TASKS = "CREATE TABLE " +
            DatabaseContract.DailyTaskEntry.TABLE_NAME + " (" +
            DatabaseContract.DailyTaskEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.DailyTaskEntry.COLUMN_USER_REF + " INTEGER, " +
            DatabaseContract.DailyTaskEntry.COLUMN_TITLE + " TEXT, " +
            DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE + " INTEGER, " +
            DatabaseContract.DailyTaskEntry.COLUMN_UNIT + " TEXT, " +
            DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_DATE + " TEXT, " +
            DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD + " INTEGER DEFAULT 10, " +
            DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP + " INTEGER DEFAULT 15);";

    private static final String CREATE_TABLE_INVENTORY = "CREATE TABLE " +
            DatabaseContract.InventoryEntry.TABLE_NAME + " (" +
            DatabaseContract.InventoryEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.InventoryEntry.COLUMN_ITEM_ID + " INTEGER, " +
            DatabaseContract.InventoryEntry.COLUMN_ITEM_NAME + " TEXT, " +
            DatabaseContract.InventoryEntry.COLUMN_CATEGORY + " TEXT, " +
            DatabaseContract.InventoryEntry.COLUMN_RES_NAME + " TEXT);";

    private static final String CREATE_TABLE_TASK_TEMPLATES = "CREATE TABLE task_templates (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "sub_category TEXT, " +
            "title TEXT, " +
            "base_value INTEGER, " +
            "unit TEXT);";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER);
        db.execSQL(CREATE_TABLE_DAILY_TASKS);
        db.execSQL(CREATE_TABLE_INVENTORY);
        db.execSQL(CREATE_TABLE_PREFERENCES);
        db.execSQL(CREATE_TABLE_TASK_TEMPLATES);
        seedTaskTemplates(db);
    }

    private void seedTaskTemplates(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put("sub_category", "Physical Step Multiplier");
        values.put("title", "Walk steps");
        values.put("base_value", 5000);
        values.put("unit", "steps");
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Detox Duration Multiplier");
        values.put("title", "Reduce screen time");
        values.put("base_value", 60);
        values.put("unit", "minutes");
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Creative Activity Multiplier");
        values.put("title", "Read a book");
        values.put("base_value", 20);
        values.put("unit", "pages");
        db.insert("task_templates", null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS user");
        db.execSQL("DROP TABLE IF EXISTS daily_tasks");
        db.execSQL("DROP TABLE IF EXISTS inventory");
        db.execSQL("DROP TABLE IF EXISTS preferences");
        db.execSQL("DROP TABLE IF EXISTS task_templates");
        onCreate(db);
    }
}