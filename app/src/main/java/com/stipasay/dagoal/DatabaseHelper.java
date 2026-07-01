package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dagoal.db";
    private static final int DATABASE_VERSION = 5;

    private static final String CREATE_TABLE_USER = "CREATE TABLE user (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT DEFAULT 'Adventurer', " +
            "level INTEGER DEFAULT 1, " +
            "gold INTEGER DEFAULT 0, " +
            "xp INTEGER DEFAULT 0);";

    private static final String CREATE_TABLE_PREFERENCES =
            "CREATE TABLE preferences (_id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER, activity_type TEXT, difficulty TEXT);";

    private static final String CREATE_TABLE_TEMPLATES = "CREATE TABLE " +
            DatabaseContract.TaskTemplateEntry.TABLE_NAME + " (" +
            DatabaseContract.TaskTemplateEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.TaskTemplateEntry.COLUMN_TITLE + " TEXT, " +
            DatabaseContract.TaskTemplateEntry.COLUMN_BASE_VALUE + " INTEGER, " +
            DatabaseContract.TaskTemplateEntry.COLUMN_UNIT + " TEXT, " +
            DatabaseContract.TaskTemplateEntry.COLUMN_CATEGORY + " TEXT, " +
            DatabaseContract.TaskTemplateEntry.COLUMN_SUB_CATEGORY + " TEXT);";

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

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER);
        db.execSQL(CREATE_TABLE_PREFERENCES);
        db.execSQL(CREATE_TABLE_TEMPLATES);
        db.execSQL(CREATE_TABLE_DAILY_TASKS);

        insertDefaultTemplates(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS user");
        db.execSQL("DROP TABLE IF EXISTS preferences");
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.TaskTemplateEntry.TABLE_NAME);
        db.execSQL("DROP TABLE IF EXISTS " + DatabaseContract.DailyTaskEntry.TABLE_NAME);
        onCreate(db);
    }

    private void insertDefaultTemplates(SQLiteDatabase db) {
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Walk and track your steps", 2000, "steps", "physical", "core_step"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Take an intentional walking break", 5, "minutes", "physical", "secondary_move"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Do light stretching stretching in place", 10, "minutes", "physical", "secondary_move"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Perform active jumping jacks", 20, "reps", "physical", "secondary_move"));

        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Lock your phone for focused work", 30, "minutes", "detox", "core_detox"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Keep your device locked during meals", 45, "minutes", "detox", "core_detox"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Turn off social notifications for focus", 60, "minutes", "detox", "core_detox"));

        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Sketch or doodle on a paper pad", 10, "minutes", "creative", "arts"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Organize your study desk or room space", 10, "minutes", "creative", "arts"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Write down your daily reflections", 3, "sentences", "creative", "journaling"));
        db.insert(DatabaseContract.TaskTemplateEntry.TABLE_NAME, null, createTemplateValues("Read a chapter of an offline novel", 15, "minutes", "creative", "journaling"));
    }

    private ContentValues createTemplateValues(String title, int baseValue, String unit, String cat, String subCat) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.TaskTemplateEntry.COLUMN_TITLE, title);
        values.put(DatabaseContract.TaskTemplateEntry.COLUMN_BASE_VALUE, baseValue);
        values.put(DatabaseContract.TaskTemplateEntry.COLUMN_UNIT, unit);
        values.put(DatabaseContract.TaskTemplateEntry.COLUMN_CATEGORY, cat);
        values.put(DatabaseContract.TaskTemplateEntry.COLUMN_SUB_CATEGORY, subCat);
        return values;
    }
}
