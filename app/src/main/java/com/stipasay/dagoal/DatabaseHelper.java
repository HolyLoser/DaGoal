package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "dagoal.db";
    private static final int DATABASE_VERSION = 24;

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
            "last_completed_date TEXT DEFAULT '', " +
            "longest_streak INTEGER DEFAULT 0, " +
            DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_COUNT + " INTEGER DEFAULT 0, " +
            DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_WEEK_START + " TEXT DEFAULT '');";

    private static final String CREATE_TABLE_USER_IF_NOT_EXISTS = "CREATE TABLE IF NOT EXISTS user (" +
            "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "username TEXT DEFAULT 'Adventurer', " +
            "name TEXT, " +
            "age INTEGER, " +
            "level INTEGER DEFAULT 1, " +
            "gold INTEGER DEFAULT 0, " +
            "xp INTEGER DEFAULT 0, " +
            "streak INTEGER DEFAULT 0, " +
            "last_completed_date TEXT DEFAULT '', " +
            "longest_streak INTEGER DEFAULT 0, " +
            DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_COUNT + " INTEGER DEFAULT 0, " +
            DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_WEEK_START + " TEXT DEFAULT '');";

    public static void ensureUserTableExists(SQLiteDatabase db) {
        if (db != null) {
            db.execSQL(CREATE_TABLE_USER_IF_NOT_EXISTS);
            android.database.Cursor cursor = db.rawQuery("SELECT _id FROM user WHERE _id = 1", null);
            boolean exists = false;
            if (cursor != null) {
                exists = cursor.moveToFirst();
                cursor.close();
            }
            if (!exists) {
                ContentValues values = new ContentValues();
                values.put("_id", 1);
                values.put("username", "Adventurer");
                values.put("name", "Adventurer");
                values.put("age", 20);
                values.put("level", 1);
                values.put("gold", 0);
                values.put("xp", 0);
                values.put("streak", 0);
                db.insert("user", null, values);
            }
        }
    }

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
            DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_UNIT_TYPE + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_INTERVAL + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_UNIT + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_WEEKDAYS + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_TYPE + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_VALUE + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_START_DATE + " TEXT DEFAULT '', " +
            DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_OCCURRENCES_DONE + " INTEGER DEFAULT 0, " +
            DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER + " TEXT DEFAULT '');";

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
            "quest_type TEXT DEFAULT 'GENERIC', " +
            "difficulty_tier TEXT DEFAULT 'EASY');";

    private static final String CREATE_TABLE_BLOCKED_APPS = "CREATE TABLE " +
            DatabaseContract.BlockedAppEntry.TABLE_NAME + " (" +
            DatabaseContract.BlockedAppEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME + " TEXT, " +
            DatabaseContract.BlockedAppEntry.COLUMN_APP_NAME + " TEXT);";

    private static final String CREATE_TABLE_INVENTORY_CONSUMABLES = "CREATE TABLE " +
            DatabaseContract.InventoryConsumableEntry.TABLE_NAME + " (" +
            DatabaseContract.InventoryConsumableEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE + " TEXT, " +
            DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY + " INTEGER DEFAULT 0);";

    private static final String CREATE_TABLE_STREAK_HISTORY = "CREATE TABLE " +
            DatabaseContract.StreakHistoryEntry.TABLE_NAME + " (" +
            DatabaseContract.StreakHistoryEntry.COLUMN_DATE + " TEXT PRIMARY KEY, " +
            DatabaseContract.StreakHistoryEntry.COLUMN_STREAK_VALUE + " INTEGER DEFAULT 0, " +
            DatabaseContract.StreakHistoryEntry.COLUMN_CHEST_CLAIMED + " INTEGER DEFAULT 0);";

    private static final String CREATE_TABLE_ACHIEVEMENTS = "CREATE TABLE " +
            DatabaseContract.AchievementEntry.TABLE_NAME + " (" +
            DatabaseContract.AchievementEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
            DatabaseContract.AchievementEntry.COLUMN_TITLE + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_TYPE + " TEXT, " +
            DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS + " INTEGER DEFAULT 0, " +
            DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE + " INTEGER, " +
            DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI + " TEXT DEFAULT '\uD83C\uDFC6');";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE_USER);
        db.execSQL(CREATE_TABLE_DAILY_TASKS);
        db.execSQL(CREATE_TABLE_INVENTORY);
        db.execSQL(CREATE_TABLE_INVENTORY_CONSUMABLES);
        db.execSQL(CREATE_TABLE_PREFERENCES);
        db.execSQL(CREATE_TABLE_TASK_TEMPLATES);
        db.execSQL(CREATE_TABLE_ACHIEVEMENTS);
        db.execSQL(CREATE_TABLE_BLOCKED_APPS);
        db.execSQL(CREATE_TABLE_STREAK_HISTORY);
        seedTaskTemplates(db);
        seedAchievements(db);
    }

    public static void seedTaskTemplates(SQLiteDatabase db) {
        addTaskTemplate(db, "Physical Step Multiplier", "Walk steps", 5000, "steps", DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS, "Reach your randomized daily step goal");
        addTaskTemplate(db, "Physical Step Multiplier", "Clean bed sheets", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Change or clean your bed sheets for fresh sleep");
        addTaskTemplate(db, "Physical Step Multiplier", "Do laundry", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Wash, dry, or fold a load of laundry");
        addTaskTemplate(db, "Creative Activity Multiplier", "Write down goals for tomorrow", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Jot down 3 key goals or tasks for tomorrow");
        addTaskTemplate(db, "Creative Activity Multiplier", "Declutter 1 item on desk or room", 1, "item", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Organize or put away 1 item on your desk or room");
        addTaskTemplate(db, "Physical Step Multiplier", "Wash face", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Refresh your face with water or facial cleanser");
        addTaskTemplate(db, "Detox Duration Multiplier", "Put phone face down during a meal", 1, "meal", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Keep your phone face down and enjoy a phone-free meal");
        addTaskTemplate(db, "Creative Activity Multiplier", "Vacuum your room", 1, "room", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Vacuum or sweep your bedroom floor");
        addTaskTemplate(db, "Creative Activity Multiplier", "Take out the trash", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Empty and take out the household trash");

        addTaskTemplate(db, "Physical Step Multiplier", "Take deep breaths", 4, "reps", DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT, "Take 4 slow, deep, relaxing breaths");
        addTaskTemplate(db, "Physical Step Multiplier", "Do some stretches", 3, "reps", DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT, "Perform 3 light stretching movements");
        addTaskTemplate(db, "Physical Step Multiplier", "Hold a forearm plank for 15 seconds", 15, "seconds", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Hold a forearm plank for 15 seconds");
        addTaskTemplate(db, "Physical Step Multiplier", "Drink water", 6, "glasses", DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT, "Stay hydrated by drinking water throughout the day");
        addTaskTemplate(db, "Physical Step Multiplier", "Quick push-ups or squats", 15, "reps", DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT, "Complete 15 quick push-ups or bodyweight squats");
        addTaskTemplate(db, "Physical Step Multiplier", "Climb flights of stairs", 2, "flights", DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT, "Climb 2 flights of stairs");

        addTaskTemplate(db, "Creative Activity Multiplier", "Eat a fresh fruit", 1, "fruit", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Eat a fresh piece of fruit for a healthy snack");
        addTaskTemplate(db, "Creative Activity Multiplier", "Make a sandwich", 1, "sandwich", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Prepare a quick homemade sandwich");
        addTaskTemplate(db, "Creative Activity Multiplier", "Cook a meal", 1, "meal", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Cook a delicious homemade meal");
        addTaskTemplate(db, "Creative Activity Multiplier", "Try a new recipe", 1, "recipe", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Prepare and try a new dish or recipe");
        addTaskTemplate(db, "Creative Activity Multiplier", "Attempt a Wordle", 1, "game", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Play and attempt today's Wordle or word puzzle");
        addTaskTemplate(db, "Creative Activity Multiplier", "Do a power nap", 1, "nap", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Take a 20-30 minute nap break");
        addTaskTemplate(db, "Detox Duration Multiplier", "Avoid social media", 180, "minutes", DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID, "Warns you every time you open an app in your block list during active screen avoidance.");

        // Survey-Driven Creative & Hobby Quest Templates
        addTaskTemplate(db, "Creative Writing", "Write a 2-stanza poem about your day", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Express your thoughts or capture a moment today in 2 short poetic stanzas.");
        addTaskTemplate(db, "Creative Writing", "Create an original character profile & backstory", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Draft a character name, personality traits, and a short background story in your journal.");
        addTaskTemplate(db, "Art & Design", "Take a photo of nature as inspiration for a sketch", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Snap a photo of outdoor plants, sky, or scenery to use as visual drawing reference.");
        addTaskTemplate(db, "Art & Design", "Doodle a 5-minute warm-up sketch in your sketchbook", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Set a 5-minute timer and doodle simple shapes, faces, or objects in your sketchbook.");
        addTaskTemplate(db, "Literature", "Read 10 pages of your current book", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Unplug and read 10 pages of your current book without phone distractions.");
        addTaskTemplate(db, "Literature", "Write down 3 memorable quotes from your reading", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Note 3 inspiring or memorable quotes from your book into your journal.");
        addTaskTemplate(db, "Mind Games", "Solve a daily Wordle or word puzzle", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Exercise your vocabulary by solving today's Wordle or daily word game online.");
        addTaskTemplate(db, "Mind Games", "Complete a daily Sudoku grid", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Challenge your logic skills by solving 1 daily Sudoku puzzle grid.");
        addTaskTemplate(db, "Fitness", "Complete a 15-minute bodyweight workout", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Do a quick 15-minute workout consisting of bodyweight exercises or stretching.");
        addTaskTemplate(db, "Music", "Practice playing an instrument for 15 minutes", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Play scales, chords, or a favorite song on your instrument for 15 minutes.");
        addTaskTemplate(db, "Crafts", "Craft or assemble a physical project or origami", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Unplug and fold an origami figure or assemble a small physical craft project.");
        addTaskTemplate(db, "Home", "Clean and organize your study desk space", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Tidy up your desk, clear loose papers, and organize your study space.");
        addTaskTemplate(db, "Culinary", "Prepare a healthy homemade meal or snack", 1, "time", DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC, "Cook or assemble a fresh, nutritious homemade meal or healthy snack.");
    }

    public static void addTaskTemplate(SQLiteDatabase db, String category, String title, int baseValue, String unit, String questType, String description) {
        ContentValues values = new ContentValues();
        values.put("sub_category", category);
        values.put("title", title);
        values.put("base_value", baseValue);
        values.put("unit", unit);
        values.put("quest_type", questType);
        values.put("difficulty_tier", description);
        db.insert("task_templates", null, values);
    }

    private void seedAchievements(SQLiteDatabase db) {
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Quest Master");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Complete daily quests.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "QUEST_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 5);
        values.put(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI, "\uD83D\uDC51");
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Consistent");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Reach daily streak milestones.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "STREAK_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 7);
        values.put(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI, "\uD83D\uDD25");
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Self-Starter");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Complete custom goals you created yourself.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "CUSTOM_QUEST_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 5);
        values.put(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI, "\u270D\uFE0F");
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Fashionista");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Collect clothing and accessory items.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "CLOTHING_COLLECTION_COUNT");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 5);
        values.put(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI, "\uD83D\uDC55");
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
        values.clear();

        values.put(DatabaseContract.AchievementEntry.COLUMN_TITLE, "Legendary Adventurer");
        values.put(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION, "Reach character level ranks.");
        values.put(DatabaseContract.AchievementEntry.COLUMN_TYPE, "CHARACTER_LEVEL_RANK");
        values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, 0);
        values.put(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE, 5);
        values.put(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI, "\u2B50");
        db.insert(DatabaseContract.AchievementEntry.TABLE_NAME, null, values);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS daily_tasks");
        db.execSQL("DROP TABLE IF EXISTS task_templates");

        db.execSQL("CREATE TABLE IF NOT EXISTS daily_tasks (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, title TEXT, final_target INTEGER, unit TEXT, is_completed INTEGER DEFAULT 0, " +
                "task_date TEXT, reward_gold INTEGER, reward_xp INTEGER, quest_type TEXT, current_value INTEGER DEFAULT 0, " +
                "package_name TEXT, start_timestamp INTEGER DEFAULT 0, category_tag TEXT, ignore_stage INTEGER DEFAULT 0, " +
                "snooze_until INTEGER DEFAULT 0, is_custom INTEGER DEFAULT 0, unit_type TEXT, repeat_interval INTEGER DEFAULT 0, " +
                "repeat_unit TEXT, repeat_weekdays TEXT, repeat_end_type TEXT, repeat_end_value TEXT, repeat_start_date TEXT, " +
                "repeat_occurrences_done INTEGER DEFAULT 0, difficulty_tier TEXT);");

        db.execSQL("CREATE TABLE IF NOT EXISTS task_templates (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, base_value INTEGER, unit TEXT, " +
                "category_tag TEXT, sub_category TEXT, quest_type TEXT, difficulty_tier TEXT);");

        ensureUserTableExists(db);
        seedTaskTemplates(db);

        if (appContext != null) {
            android.content.SharedPreferences prefs = appContext.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            prefs.edit().remove("last_quest_generation_date").apply();
        }
    }
}