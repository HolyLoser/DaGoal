package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class TaskManager {

    private final DatabaseHelper dbHelper;
    private final Context appContext;

    public TaskManager(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.appContext = context.getApplicationContext();
    }

    public void generateDailyTasks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        checkAndUpdateStreak(db, currentDate);

        if (areTasksAlreadyGenerated(db, currentDate)) {
            return;
        }

        db.delete(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, null);

        boolean avoidanceInserted = false;
        java.util.List<String[]> blockedApps = getBlockedApps(db);

        if (!blockedApps.isEmpty() && new Random().nextBoolean()) {
            double detoxMult = getMultiplier(db, "Detox Duration Multiplier");
            ContentValues values = buildAvoidanceValues(blockedApps, detoxMult, currentDate);
            db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
            avoidanceInserted = true;
        }

        int remainingSlots = avoidanceInserted ? 4 : 5;
        insertRandomAdditionalTasks(db, currentDate, remainingSlots);
    }

    public static String formatDurationMinutes(int totalMinutes) {
        if (totalMinutes < 60) {
            return totalMinutes + " minutes";
        }
        int hours = totalMinutes / 60;
        int mins = totalMinutes % 60;
        if (mins == 0) {
            return hours + "h";
        }
        return hours + "h " + mins + "m";
    }

    private void insertRandomAdditionalTasks(SQLiteDatabase db, String dateStr, int count) {
        for (int i = 0; i < count; i++) {
            String query = "SELECT title, base_value, unit, quest_type, sub_category FROM task_templates WHERE title NOT IN (SELECT title FROM " +
                    DatabaseContract.DailyTaskEntry.TABLE_NAME + " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?) ORDER BY RANDOM() LIMIT 1";

            Cursor cursor = db.rawQuery(query, new String[]{ dateStr });

            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                int baseValue = cursor.getInt(1);
                String unit = cursor.getString(2);
                String questType = cursor.getString(3);
                String subCategory = cursor.getString(4);
                cursor.close();

                double multiplier = getMultiplier(db, subCategory);
                int finalTarget = (int) (baseValue * multiplier);

                ContentValues values = new ContentValues();
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTarget);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, dateStr);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, questType);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, subCategory);

                db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
            } else {
                if (cursor != null) {
                    cursor.close();
                }
                break;
            }
        }
    }

    private ContentValues buildAvoidanceValues(java.util.List<String[]> blockedApps, double multiplier, String dateStr) {
        Random random = new Random();
        boolean useGroupQuest = random.nextBoolean();
        int baseMinutes = 180;
        int targetMinutes = (int) (baseMinutes * multiplier);

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, targetMinutes);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, "minutes");
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, dateStr);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP, 0L);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, "Detox Duration Multiplier");
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IGNORE_STAGE, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL, 0L);

        if (useGroupQuest) {
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid social media");
            values.putNull(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME);
        } else {
            String[] randomApp = blockedApps.get(random.nextInt(blockedApps.size()));
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid " + randomApp[1]);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME, randomApp[0]);
        }

        return values;
    }

    public boolean refreshAvoidanceQuest(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        java.util.List<String[]> blockedApps = getBlockedApps(db);

        if (blockedApps.isEmpty()) {
            return false;
        }

        double multiplier = getMultiplier(db, "Detox Duration Multiplier");
        ContentValues values = buildAvoidanceValues(blockedApps, multiplier, currentDate);

        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                values,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );

        return true;
    }

    private boolean hasAvoidanceQuestExcluding(SQLiteDatabase db, String dateStr, int excludeTaskId) {
        String query = "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ? AND " +
                DatabaseContract.DailyTaskEntry._ID + " != ?";
        Cursor cursor = db.rawQuery(query, new String[]{
                DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID, dateStr, String.valueOf(excludeTaskId)
        });
        boolean exists = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                exists = cursor.getInt(0) > 0;
            }
            cursor.close();
        }
        return exists;
    }

    public boolean shuffleQuest(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Cursor typeCursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                new String[]{ DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE },
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) },
                null, null, null
        );

        String questType = "";
        if (typeCursor != null && typeCursor.moveToFirst()) {
            questType = typeCursor.getString(0);
            typeCursor.close();
        }

        if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType)) {
            return refreshAvoidanceQuest(taskId);
        }

        java.util.List<String[]> blockedApps = getBlockedApps(db);
        boolean avoidanceExistsElsewhere = hasAvoidanceQuestExcluding(db, currentDate, taskId);

        if (!blockedApps.isEmpty() && !avoidanceExistsElsewhere && new Random().nextInt(3) == 0) {
            double multiplier = getMultiplier(db, "Detox Duration Multiplier");
            ContentValues avoidanceValues = buildAvoidanceValues(blockedApps, multiplier, currentDate);

            db.update(
                    DatabaseContract.DailyTaskEntry.TABLE_NAME,
                    avoidanceValues,
                    DatabaseContract.DailyTaskEntry._ID + " = ?",
                    new String[]{ String.valueOf(taskId) }
            );

            return true;
        }

        String query = "SELECT title, base_value, unit, quest_type, sub_category FROM task_templates WHERE title NOT IN (SELECT title FROM " +
                DatabaseContract.DailyTaskEntry.TABLE_NAME + " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?) ORDER BY RANDOM() LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{ currentDate });

        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(0);
            int baseValue = cursor.getInt(1);
            String unit = cursor.getString(2);
            String newQuestType = cursor.getString(3);
            String subCategory = cursor.getString(4);
            cursor.close();

            double multiplier = getMultiplier(db, subCategory);
            int finalTarget = (int) (baseValue * multiplier);

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTarget);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, newQuestType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, subCategory);
            values.putNull(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME);

            db.update(
                    DatabaseContract.DailyTaskEntry.TABLE_NAME,
                    values,
                    DatabaseContract.DailyTaskEntry._ID + " = ?",
                    new String[]{ String.valueOf(taskId) }
            );

            return true;
        } else {
            if (cursor != null) {
                cursor.close();
            }
            return false;
        }
    }

    private java.util.List<String[]> getBlockedApps(SQLiteDatabase db) {
        java.util.List<String[]> result = new java.util.ArrayList<>();

        Cursor cursor = db.query(
                DatabaseContract.BlockedAppEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME,
                        DatabaseContract.BlockedAppEntry.COLUMN_APP_NAME
                },
                null, null, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                result.add(new String[]{cursor.getString(0), cursor.getString(1)});
            }
            cursor.close();
        }

        return result;
    }

    private void checkAndUpdateStreak(SQLiteDatabase db, String currentDateStr) {
        Cursor cursor = db.rawQuery("SELECT last_completed_date, streak FROM user WHERE _id = 1", null);
        if (cursor != null && cursor.moveToFirst()) {
            String lastCompleted = cursor.getString(0);
            int currentStreak = cursor.getInt(1);
            cursor.close();

            ContentValues values = new ContentValues();

            if (lastCompleted == null || lastCompleted.isEmpty()) {
                values.put("streak", 1);
                values.put("last_completed_date", currentDateStr);
                db.update("user", values, "_id = 1", null);
                syncStreakAchievements(db, 1);
                return;
            }

            if (lastCompleted.equals(currentDateStr)) {
                return;
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date lastDate = sdf.parse(lastCompleted);
                Date todayDate = sdf.parse(currentDateStr);

                Calendar cal = Calendar.getInstance();
                if (lastDate != null) {
                    cal.setTime(lastDate);
                }

                cal.add(Calendar.DAY_OF_YEAR, 1);
                String expectedExtensionDate = sdf.format(cal.getTime());

                if (currentDateStr.equals(expectedExtensionDate)) {
                    int newStreak = currentStreak + 1;
                    values.put("streak", newStreak);
                    values.put("last_completed_date", currentDateStr);
                    db.update("user", values, "_id = 1", null);
                    syncStreakAchievements(db, newStreak);
                    Log.i("TaskManager", "Streak incremented to " + newStreak + " via daily login.");
                } else if (todayDate != null && todayDate.after(cal.getTime())) {
                    values.put("streak", 1);
                    values.put("last_completed_date", currentDateStr);
                    db.update("user", values, "_id = 1", null);
                    syncStreakAchievements(db, 1);
                    Log.i("TaskManager", "Streak reset to 1. Calendar day gap detected.");
                } else {
                    values.put("last_completed_date", currentDateStr);
                    db.update("user", values, "_id = 1", null);
                }
            } catch (Exception e) {
                Log.e("TaskManager", "Error parsing streak login sequence dates", e);
            }
        }
    }

    private boolean areTasksAlreadyGenerated(SQLiteDatabase db, String dateStr) {
        String query = "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{ dateStr });
        boolean generated = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                generated = cursor.getInt(0) > 0;
            }
            cursor.close();
        }
        return generated;
    }

    private double getMultiplier(SQLiteDatabase db, String type) {
        String query = "SELECT " + DatabaseContract.PreferenceEntry.COLUMN_DIFFICULTY +
                " FROM " + DatabaseContract.PreferenceEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.PreferenceEntry.COLUMN_ACTIVITY_TYPE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{ type });
        double val = 1.0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                String multiplierStr = cursor.getString(0);
                try {
                    val = Double.parseDouble(multiplierStr);
                } catch (NumberFormatException e) {
                    Log.w("TaskManager", "Preference for " + type + " is not a valid number: " + multiplierStr + ". Using default 1.0.");
                }
            }
            cursor.close();
        }
        return val;
    }

    private String getPreferenceString(SQLiteDatabase db, String type) {
        String query = "SELECT " + DatabaseContract.PreferenceEntry.COLUMN_DIFFICULTY +
                " FROM " + DatabaseContract.PreferenceEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.PreferenceEntry.COLUMN_ACTIVITY_TYPE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{ type });
        String val = "";
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                val = cursor.getString(0);
            }
            cursor.close();
        }
        return val;
    }

    public void completeTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String selection = DatabaseContract.DailyTaskEntry._ID + " = ?";
        String[] selectionArgs = { String.valueOf(taskId) };

        int rewardGold = 100;
        int rewardXp = 100;

        ContentValues taskValues = new ContentValues();
        taskValues.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 1);
        db.update(DatabaseContract.DailyTaskEntry.TABLE_NAME, taskValues, selection, selectionArgs);

        Cursor userCursor = db.rawQuery("SELECT gold, xp, streak FROM user WHERE _id = 1", null);
        int currentGold = 1000;
        int currentXp = 0;

        if (userCursor != null && userCursor.moveToFirst()) {
            currentGold = userCursor.getInt(0);
            currentXp = userCursor.getInt(1);
            userCursor.close();
        }

        int newGold = currentGold + rewardGold;
        int newXp = currentXp + rewardXp;
        int currentLevel = 1;

        Cursor levelCursor = db.rawQuery("SELECT level FROM user WHERE _id = 1", null);
        if (levelCursor != null && levelCursor.moveToFirst()) {
            currentLevel = levelCursor.getInt(0);
            levelCursor.close();
        }

        int levelBeforeThisCompletion = currentLevel;

        if (newXp >= 100) {
            currentLevel += 1;
            newXp = newXp - 100;
        }

        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, newGold);
        userValues.put(DatabaseContract.UserEntry.COLUMN_XP, newXp);
        userValues.put("level", currentLevel);

        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);

        if (currentLevel > levelBeforeThisCompletion) {
            grantLevelUpRewards(db, currentLevel);
        }

        incrementQuestCountAchievements(db);
    }

    private void grantLevelUpRewards(SQLiteDatabase db, int newLevel) {
        String rewardType = null;

        if (newLevel == 3) {
            rewardType = DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR;
        } else if (newLevel == 5) {
            rewardType = DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST;
        } else if (newLevel == 7) {
            rewardType = DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST;
        } else if (newLevel % 5 == 0) {
            rewardType = DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR;
        }

        if (rewardType == null) {
            return;
        }

        grantConsumable(db, rewardType, 1);
        showAchievementUnlockedToast("Level " + newLevel + " reward: " + formatConsumableName(rewardType));
    }

    private void grantConsumable(SQLiteDatabase db, String type, int amount) {
        Cursor cursor = db.query(
                DatabaseContract.InventoryConsumableEntry.TABLE_NAME,
                new String[]{ DatabaseContract.InventoryConsumableEntry._ID, DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY },
                DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE + " = ?",
                new String[]{ type },
                null, null, null
        );

        if (cursor != null && cursor.moveToFirst()) {
            int id = cursor.getInt(0);
            int currentQuantity = cursor.getInt(1);
            cursor.close();

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY, currentQuantity + amount);
            db.update(
                    DatabaseContract.InventoryConsumableEntry.TABLE_NAME,
                    values,
                    DatabaseContract.InventoryConsumableEntry._ID + " = ?",
                    new String[]{ String.valueOf(id) }
            );
        } else {
            if (cursor != null) {
                cursor.close();
            }
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE, type);
            values.put(DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY, amount);
            db.insert(DatabaseContract.InventoryConsumableEntry.TABLE_NAME, null, values);
        }
    }

    private String formatConsumableName(String type) {
        if (DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR.equals(type)) {
            return "Streak Protector";
        } else if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST.equals(type)) {
            return "XP Boost";
        } else if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST.equals(type)) {
            return "Gold Boost";
        }
        return type;
    }

    public static String getLevelTitle(int level) {
        if (level >= 15) {
            return "Trailblazer";
        } else if (level >= 10) {
            return "Wanderer";
        } else if (level >= 5) {
            return "Explorer";
        }
        return "Adventurer";
    }

    private void incrementQuestCountAchievements(SQLiteDatabase db) {
        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.AchievementEntry._ID,
                        DatabaseContract.AchievementEntry.COLUMN_TITLE,
                        DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS,
                        DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE
                },
                DatabaseContract.AchievementEntry.COLUMN_TYPE + " = ?",
                new String[]{ "QUEST_COUNT" },
                null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int achievementId = cursor.getInt(0);
                String title = cursor.getString(1);
                int oldProgress = cursor.getInt(2);
                int baseTarget = cursor.getInt(3);

                int maxThreshold = AchievementTierHelper.getMaxThreshold(baseTarget);
                if (oldProgress >= maxThreshold) {
                    continue;
                }

                int newProgress = Math.min(oldProgress + 1, maxThreshold);

                ContentValues values = new ContentValues();
                values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, newProgress);
                db.update(
                        DatabaseContract.AchievementEntry.TABLE_NAME,
                        values,
                        DatabaseContract.AchievementEntry._ID + " = ?",
                        new String[]{ String.valueOf(achievementId) }
                );

                int oldRank = AchievementTierHelper.getCurrentRankIndex(oldProgress, baseTarget);
                int newRank = AchievementTierHelper.getCurrentRankIndex(newProgress, baseTarget);
                if (newRank > oldRank) {
                    showAchievementUnlockedToast(title + " reached " + AchievementTierHelper.RANK_NAMES[newRank]);
                }
            }
            cursor.close();
        }
    }

    private void syncStreakAchievements(SQLiteDatabase db, int streakValue) {
        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.AchievementEntry._ID,
                        DatabaseContract.AchievementEntry.COLUMN_TITLE,
                        DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS,
                        DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE
                },
                DatabaseContract.AchievementEntry.COLUMN_TYPE + " = ?",
                new String[]{ "STREAK_COUNT" },
                null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int achievementId = cursor.getInt(0);
                String title = cursor.getString(1);
                int oldProgress = cursor.getInt(2);
                int baseTarget = cursor.getInt(3);

                int maxThreshold = AchievementTierHelper.getMaxThreshold(baseTarget);
                int newProgress = Math.min(streakValue, maxThreshold);

                ContentValues values = new ContentValues();
                values.put(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS, newProgress);
                db.update(
                        DatabaseContract.AchievementEntry.TABLE_NAME,
                        values,
                        DatabaseContract.AchievementEntry._ID + " = ?",
                        new String[]{ String.valueOf(achievementId) }
                );

                int oldRank = AchievementTierHelper.getCurrentRankIndex(oldProgress, baseTarget);
                int newRank = AchievementTierHelper.getCurrentRankIndex(newProgress, baseTarget);
                if (newRank > oldRank) {
                    showAchievementUnlockedToast(title + " reached " + AchievementTierHelper.RANK_NAMES[newRank]);
                }
            }
            cursor.close();
        }
    }

    private void showAchievementUnlockedToast(String title) {
        if (appContext != null) {
            Toast.makeText(appContext, "Achievement Unlocked: " + title + "!", Toast.LENGTH_LONG).show();
        }
    }

    public void updateTaskProgress(String questType, int newValue) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String[] projection = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED
        };

        String selection = DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?";
        String[] selectionArgs = { questType, currentDate };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection, selection, selectionArgs, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int taskId = cursor.getInt(0);
                int targetValue = cursor.getInt(1);
                int isCompleted = cursor.getInt(2);

                if (isCompleted == 1) {
                    continue;
                }

                int clampedValue = Math.min(newValue, targetValue);

                ContentValues progressValues = new ContentValues();
                progressValues.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, clampedValue);
                db.update(
                        DatabaseContract.DailyTaskEntry.TABLE_NAME,
                        progressValues,
                        DatabaseContract.DailyTaskEntry._ID + " = ?",
                        new String[]{ String.valueOf(taskId) }
                );

                if (clampedValue >= targetValue) {
                    completeTask(taskId);
                }
            }
            cursor.close();
        }
    }

    public void incrementQuestProgress(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE,
                        DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE
                },
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) },
                null, null, null
        );

        int currentValue = 0;
        int targetValue = 0;

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                currentValue = cursor.getInt(0);
                targetValue = cursor.getInt(1);
            }
            cursor.close();
        }

        int newValue = Math.min(currentValue + 1, targetValue);

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, newValue);
        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                values,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );

        if (newValue >= targetValue) {
            completeTask(taskId);
        }
    }

    public void startAvoidanceQuest(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP, System.currentTimeMillis());
        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                values,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );
    }

    public void checkAndCompleteAvoidanceQuests() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String[] projection = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP
        };

        String selection = DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " = 0";
        String[] selectionArgs = { DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID, currentDate };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection, selection, selectionArgs, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int taskId = cursor.getInt(0);
                int targetMinutes = cursor.getInt(1);
                long startTimestamp = cursor.getLong(2);

                if (startTimestamp <= 0) {
                    continue;
                }

                long elapsedMinutes = (System.currentTimeMillis() - startTimestamp) / 60000L;
                int clampedElapsed = (int) Math.min(elapsedMinutes, targetMinutes);

                ContentValues progressValues = new ContentValues();
                progressValues.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, clampedElapsed);
                db.update(
                        DatabaseContract.DailyTaskEntry.TABLE_NAME,
                        progressValues,
                        DatabaseContract.DailyTaskEntry._ID + " = ?",
                        new String[]{ String.valueOf(taskId) }
                );

                if (clampedElapsed >= targetMinutes) {
                    completeTask(taskId);
                }
            }
            cursor.close();
        }
    }

    public static class ActiveAvoidanceQuest {
        public int taskId;
        public String title;
        public java.util.List<String> targetPackages;
    }

    public java.util.List<ActiveAvoidanceQuest> getStartedAvoidanceQuests() {
        java.util.List<ActiveAvoidanceQuest> result = new java.util.ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String[] projection = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE,
                DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME
        };

        String selection = DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " = 0 AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP + " > 0";
        String[] selectionArgs = { DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID, currentDate };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection, selection, selectionArgs, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                ActiveAvoidanceQuest quest = new ActiveAvoidanceQuest();
                quest.taskId = cursor.getInt(0);
                quest.title = cursor.getString(1);
                String packageName = cursor.getString(2);

                quest.targetPackages = new java.util.ArrayList<>();
                if (packageName != null && !packageName.isEmpty()) {
                    quest.targetPackages.add(packageName);
                } else {
                    java.util.List<String[]> blockedApps = getBlockedApps(db);
                    for (String[] app : blockedApps) {
                        quest.targetPackages.add(app[0]);
                    }
                }

                result.add(quest);
            }
            cursor.close();
        }

        return result;
    }

    public int getIgnoreStage(int taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                new String[]{ DatabaseContract.DailyTaskEntry.COLUMN_IGNORE_STAGE },
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) },
                null, null, null
        );
        int stage = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                stage = cursor.getInt(0);
            }
            cursor.close();
        }
        return stage;
    }

    public long getSnoozeUntil(int taskId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                new String[]{ DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL },
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) },
                null, null, null
        );
        long snoozeUntil = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                snoozeUntil = cursor.getLong(0);
            }
            cursor.close();
        }
        return snoozeUntil;
    }

    public int handleIgnorePressed(int taskId) {
        int stage = getIgnoreStage(taskId);
        if (stage >= 3) {
            resetAvoidanceQuest(taskId);
            return -1;
        }

        int newStage = stage + 1;
        int minutes = newStage == 1 ? 5 : (newStage == 2 ? 10 : 15);
        long snoozeUntil = System.currentTimeMillis() + (long) minutes * 60000L;

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IGNORE_STAGE, newStage);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL, snoozeUntil);
        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                values,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );

        return minutes;
    }

    public void resetAvoidanceQuest(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP, 0L);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IGNORE_STAGE, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_SNOOZE_UNTIL, 0L);
        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                values,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );
    }

    public Cursor getUserProfile() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        checkAndUpdateStreak(db, todayDateStr);

        return db.rawQuery("SELECT username, level, gold, xp FROM user WHERE _id = 1", null);
    }

    public int getUserGoldBalance() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT gold FROM user WHERE _id = 1", null);
        int gold = 0;
        if (cursor != null && cursor.moveToFirst()) {
            gold = cursor.getInt(0);
            cursor.close();
        }
        return gold;
    }

    public java.util.List<ShopItem> getShopItems() {
        java.util.List<ShopItem> items = new java.util.ArrayList<>();
        items.add(new ShopItem(1, "Red Shirt", 50, "shirt", "shirt_red"));
        items.add(new ShopItem(2, "Blue Shirt", 75, "shirt", "shirt_blue"));
        items.add(new ShopItem(3, "Spiky Hair", 100, "hair", "hair_spiky"));
        return items;
    }

    public void resetDailyQuests() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
        db.update(DatabaseContract.DailyTaskEntry.TABLE_NAME, values, null, null);
    }

    public boolean purchaseShopItem(ShopItem item) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        int currentGold = getUserGoldBalance();
        if (currentGold < item.getPrice()) {
            return false;
        }

        int newGold = currentGold - item.getPrice();
        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, newGold);
        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);

        ContentValues invValues = new ContentValues();
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_ITEM_ID, item.getId());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_ITEM_NAME, item.getName());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_CATEGORY, item.getCategory());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_RES_NAME, item.getResName());
        db.insert(DatabaseContract.InventoryEntry.TABLE_NAME, null, invValues);

        return true;
    }

    public java.util.List<ShopItem> getOwnedItems() {
        java.util.List<ShopItem> items = new java.util.ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] columns = {
                DatabaseContract.InventoryEntry.COLUMN_ITEM_ID,
                DatabaseContract.InventoryEntry.COLUMN_ITEM_NAME,
                DatabaseContract.InventoryEntry.COLUMN_CATEGORY,
                DatabaseContract.InventoryEntry.COLUMN_RES_NAME
        };

        Cursor cursor = db.query(
                DatabaseContract.InventoryEntry.TABLE_NAME,
                columns, null, null, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String name = cursor.getString(1);
                String category = cursor.getString(2);
                String resName = cursor.getString(3);

                items.add(new ShopItem(id, name, 0, category, resName));
            }
            cursor.close();
        }
        return items;
    }
}