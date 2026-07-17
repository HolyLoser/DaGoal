package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dagoal.db";
    private static final int DATABASE_VERSION = 14;

    private final Context appContext;

    private static final String CREATE_TABLE_USER = "CREATE TABLE user (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT DEFAULT 'Adventurer', " +
            "name TEXT, " +
            "age INTEGER, " +
            "level INTEGER DEFAULT 1, " +
            "gold INTEGER DEFAULT 0, " +
            "xp INTEGER DEFAULT 0, " +
            "streak INTEGER DEFAULT 0, " +
            "last_completed_date TEXT DEFAULT '');";

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
            DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP + " INTEGER DEFAULT 15, " +
            DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " TEXT DEFAULT 'GENERIC', " +
            DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME + " TEXT, " +
            DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_IGNORE_STAGE + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL + " INTEGER DEFAULT 0);";

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
            "unit TEXT, " +
            "quest_type TEXT DEFAULT 'GENERIC');";

    private static final String CREATE_TABLE_BLOCKED_APPS = "CREATE TABLE " +
            DatabaseContract.BlockedAppEntry.TABLE_NAME + " (" +
            DatabaseContract.BlockedAppEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME + " TEXT, " +
            DatabaseContract.BlockedAppEntry.COLUMN_APP_NAME + " TEXT);";

    private static final String CREATE_TABLE_ACHIEVEMENTS = "CREATE TABLE " +
            DatabaseContract.AchievementEntry.TABLE_NAME + " (" +
            DatabaseContract.AchievementEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.AchievementEntry.COLUMN_TITLE + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_TYPE + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS + " INTEGER DEFAULT 0, " +
            DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE + " INTEGER);";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER);
        db.execSQL(CREATE_TABLE_DAILY_TASKS);
        db.execSQL(CREATE_TABLE_INVENTORY);
        db.execSQL(CREATE_TABLE_PREFERENCES);
        db.execSQL(CREATE_TABLE_TASK_TEMPLATES);
        db.execSQL(CREATE_TABLE_ACHIEVEMENTS);
        db.execSQL(CREATE_TABLE_BLOCKED_APPS);
        seedTaskTemplates(db);
        seedAchievements(db);
    }

    private void seedTaskTemplates(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put("sub_category", "Physical Step Multiplier");
        values.put("title", "Walk steps");
        values.put("base_value", 5000);
        values.put("unit", "steps");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Physical Step Multiplier");
        values.put("title", "Do stretching exercise");
        values.put("base_value", 10);
        values.put("unit", "minutes");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Physical Step Multiplier");
        values.put("title", "Jumping jacks routine");
        values.put("base_value", 30);
        values.put("unit", "reps");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Detox Duration Multiplier");
        values.put("title", "Reduce screen time");
        values.put("base_value", 60);
        values.put("unit", "minutes");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Detox Duration Multiplier");
        values.put("title", "No social media apps");
        values.put("base_value", 2);
        values.put("unit", "hours");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Detox Duration Multiplier");
        values.put("title", "Stay away from PC or Console gaming");
        values.put("base_value", 3);
        values.put("unit", "hours");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Creative Activity Multiplier");
        values.put("title", "Read a book");
        values.put("base_value", 20);
        values.put("unit", "pages");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Creative Activity Multiplier");
        values.put("title", "Practice programming syntax layout");
        values.put("base_value", 30);
        values.put("unit", "minutes");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
        values.clear();

        values.put("sub_category", "Creative Activity Multiplier");
        values.put("title", "Sketch or draw something down");
        values.put("base_value", 1);
        values.put("unit", "drawing");
        values.put("quest_type", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC);
        db.insert("task_templates", null, values);
    }

    private void seedAchievements(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "First Steps");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Complete 5 daily quests.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "QUEST_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 5);
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Quest Master");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Complete 25 daily quests.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "QUEST_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 25);
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Consistent");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Reach a 7-day streak.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "STREAK_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 7);
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS user");
        db.execSQL("DROP TABLE IF EXISTS daily_tasks");
        db.execSQL("DROP TABLE IF EXISTS inventory");
        db.execSQL("DROP TABLE IF EXISTS preferences");
        db.execSQL("DROP TABLE IF EXISTS task_templates");
        db.execSQL("DROP TABLE IF EXISTS achievements");
        db.execSQL("DROP TABLE IF EXISTS blocked_apps");
        onCreate(db);

        if (appContext != null) {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("isFirstRun", true)
                    .remove("last_quest_generation_date")
                    .apply();
        }
    }
}