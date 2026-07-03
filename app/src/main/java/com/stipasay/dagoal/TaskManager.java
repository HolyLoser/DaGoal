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

public class TaskManager {

    private final DatabaseHelper dbHelper;

    public TaskManager(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public void generateDailyTasks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Check for broken streaks before generating tasks for the new day
        checkAndUpdateStreak(db, currentDate);

        if (areTasksAlreadyGenerated(db, currentDate)) {
            return;
        }

        double physicalMult = getMultiplier(db, "Physical Step Multiplier");
        double detoxMult = getMultiplier(db, "Detox Duration Multiplier");
        double creativeMult = getMultiplier(db, "Creative Activity Multiplier");

        db.delete(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, null);

        scaleAndInsertTask(db, "Physical Step Multiplier", physicalMult, currentDate);
        scaleAndInsertTask(db, "Detox Duration Multiplier", detoxMult, currentDate);
        scaleAndInsertTask(db, "Creative Activity Multiplier", creativeMult, currentDate);
    }

    private void checkAndUpdateStreak(SQLiteDatabase db, String currentDateStr) {
        Cursor cursor = db.rawQuery("SELECT last_completed_date, streak FROM user WHERE _id = 1", null);
        if (cursor != null && cursor.moveToFirst()) {
            String lastCompleted = cursor.getString(0);
            cursor.close();

            if (lastCompleted == null || lastCompleted.isEmpty()) {
                return;
            }

            if (lastCompleted.equals(currentDateStr)) {
                return; // Already completed tasks today
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date lastDate = sdf.parse(lastCompleted);
                Date todayDate = sdf.parse(currentDateStr);

                Calendar cal = Calendar.getInstance();
                if (lastDate != null) {
                    cal.setTime(lastDate);
                }
                cal.add(Calendar.DAY_OF_YEAR, 1); // Fixed variable reference name
                String expectedExtensionDate = sdf.format(cal.getTime());

                // If today is past the expected continuation day, the streak was broken
                if (todayDate != null && !currentDateStr.equals(expectedExtensionDate) && todayDate.after(cal.getTime())) {
                    ContentValues values = new ContentValues();
                    values.put("streak", 0);
                    db.update("user", values, "_id = 1", null);
                    Log.i("TaskManager", "Streak reset to 0. A day was missed.");
                }
            } catch (Exception e) {
                Log.e("TaskManager", "Error parsing streak dates", e);
            }
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
        int currentStreak = 0;

        if (userCursor != null && userCursor.moveToFirst()) {
            currentGold = userCursor.getInt(0);
            currentXp = userCursor.getInt(1);
            currentStreak = userCursor.getInt(2); // Fixed variable reference target matching name
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

        // Check if all active tasks for today are now complete
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Cursor pendingTasksCursor = db.rawQuery(
                "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                        " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " = 0", null);

        if (pendingTasksCursor != null) {
            if (pendingTasksCursor.moveToFirst() && pendingTasksCursor.getInt(0) == 0) {
                // No uncompleted tasks left today. Increment the daily streak.
                currentStreak += 1;
                userValues.put("streak", currentStreak);
                userValues.put("last_completed_date", currentDate);
            }
            pendingTasksCursor.close();
        }

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