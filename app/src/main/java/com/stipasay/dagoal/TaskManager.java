package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TaskManager {

    private final DatabaseHelper dbHelper;

    public TaskManager(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public void generateDailyTasks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        if (areTasksAlreadyGenerated(db, currentDate)) {
            return;
        }

        double physicalMult = getMultiplier(db, "Physical Step Multiplier");
        double detoxMult = getMultiplier(db, "Detox Duration Multiplier");
        double creativeMult = getMultiplier(db, "Creative Activity Multiplier");
        String preferredHobby = getPreferenceString(db, "Preferred Offline Hobby Type");

        db.delete(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, null);

        if (physicalMult == 1.0) {
            scaleAndInsertTask(db, "core_step", physicalMult, currentDate);
            scaleAndInsertTask(db, "secondary_move", physicalMult, currentDate);
            scaleAndInsertTask(db, "core_detox", detoxMult, currentDate);
            scaleAndInsertTask(db, "arts", creativeMult, currentDate);
        } else {
            scaleAndInsertTask(db, "core_step", physicalMult, currentDate);
            scaleAndInsertTask(db, "core_detox", detoxMult, currentDate);

            if (preferredHobby.contains("Journaling") || preferredHobby.contains("Writing")) {
                scaleAndInsertTask(db, "journaling", creativeMult, currentDate);
            } else {
                scaleAndInsertTask(db, "arts", creativeMult, currentDate);
            }

            scaleAndInsertTask(db, "journaling", creativeMult, currentDate);
        }
    }

    private void scaleAndInsertTask(SQLiteDatabase db, String subCategory, double multiplier, String dateStr) {
        String[] columns = {
                DatabaseContract.TaskTemplateEntry.COLUMN_TITLE,
                DatabaseContract.TaskTemplateEntry.COLUMN_BASE_VALUE,
                DatabaseContract.TaskTemplateEntry.COLUMN_UNIT
        };

        String selection = DatabaseContract.TaskTemplateEntry.COLUMN_SUB_CATEGORY + " = ?";
        String[] selectionArgs = { subCategory };

        Cursor cursor = db.query(
                DatabaseContract.TaskTemplateEntry.TABLE_NAME,
                columns, selection, selectionArgs, null, null, null, "1"
        );

        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(0);
            int baseValue = cursor.getInt(1);
            String unit = cursor.getString(2);
            cursor.close();

            int finalTargetValue = (int) (baseValue * multiplier);

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTargetValue);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, dateStr);

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

        String[] columns = {
                DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD,
                DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP
        };
        String selection = DatabaseContract.DailyTaskEntry._ID + " = ?";
        String[] selectionArgs = { String.valueOf(taskId) };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                columns, selection, selectionArgs, null, null, null
        );

        int rewardGold = 0;
        int rewardXp = 0;

        if (cursor != null && cursor.moveToFirst()) {
            rewardGold = cursor.getInt(0);
            rewardXp = cursor.getInt(1);
            cursor.close();
        }

        ContentValues taskValues = new ContentValues();
        taskValues.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 1);
        db.update(DatabaseContract.DailyTaskEntry.TABLE_NAME, taskValues, selection, selectionArgs);

        Cursor userCursor = db.rawQuery("SELECT gold, xp FROM user WHERE _id = 1", null);
        int currentGold = 0;
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

    public Cursor getUserProfile() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
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
}
