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
}
