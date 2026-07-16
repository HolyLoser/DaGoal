package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

public class TaskManager {

    private final DatabaseHelper dbHelper;

    public TaskManager(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public void generateDailyTasks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        checkAndUpdateStreak(db, currentDate);

        if (areTasksAlreadyGenerated(db, currentDate)) {
            return;
        }

        double physicalMult = getMultiplier(db, "Physical Step Multiplier");
        double detoxMult = getMultiplier(db, "Detox Duration Multiplier");
        double creativeMult = getMultiplier(db, "Creative Activity Multiplier");

        db.delete(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, null);

        scaleAndInsertTask(db, "Physical Step Multiplier", physicalMult, currentDate);
        insertDetoxOrAvoidanceTask(db, detoxMult, currentDate);
        scaleAndInsertTask(db, "Creative Activity Multiplier", creativeMult, currentDate);
    }

    private void insertDetoxOrAvoidanceTask(SQLiteDatabase db, double multiplier, String dateStr) {
        java.util.List<String[]> blockedApps = getBlockedApps(db);

        if (blockedApps.isEmpty()) {
            scaleAndInsertTask(db, "Detox Duration Multiplier", multiplier, dateStr);
            return;
        }

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

        if (useGroupQuest) {
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid social media");
            values.putNull(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME);
        } else {
            String[] randomApp = blockedApps.get(random.nextInt(blockedApps.size()));
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid " + randomApp[1]);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME, randomApp[0]);
        }

        db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
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
                    Log.i("TaskManager", "Streak incremented to " + newStreak + " via daily login.");
                } else if (todayDate != null && todayDate.after(cal.getTime())) {
                    values.put("streak", 1);
                    values.put("last_completed_date", currentDateStr);
                    db.update("user", values, "_id = 1", null);
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

    private void scaleAndInsertTask(SQLiteDatabase db, String subCategory, double multiplier, String dateStr) {
        String[] columns = {
                DatabaseContract.TaskTemplateEntry.COLUMN_TITLE,
                DatabaseContract.TaskTemplateEntry.COLUMN_BASE_VALUE,
                DatabaseContract.TaskTemplateEntry.COLUMN_UNIT,
                DatabaseContract.TaskTemplateEntry.COLUMN_QUEST_TYPE
        };

        String selection = DatabaseContract.TaskTemplateEntry.COLUMN_SUB_CATEGORY + " = ?";
        String[] selectionArgs = { subCategory };

        Cursor cursor = db.query(
                DatabaseContract.TaskTemplateEntry.TABLE_NAME,
                columns, selection, selectionArgs, null, null, "RANDOM()", "1"
        );

        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(0);
            int baseValue = cursor.getInt(1);
            String unit = cursor.getString(2);
            String questType = cursor.getString(3);
            cursor.close();

            int finalTargetValue = (int) (baseValue * multiplier);

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTargetValue);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, dateStr);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, questType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);

            db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
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
        int rewardXp = 15;

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

        if (newXp >= 100) {
            currentLevel += 1;
            newXp = newXp - 100;
        }

        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, newGold);
        userValues.put(DatabaseContract.UserEntry.COLUMN_XP, newXp);
        userValues.put("level", currentLevel);

        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
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