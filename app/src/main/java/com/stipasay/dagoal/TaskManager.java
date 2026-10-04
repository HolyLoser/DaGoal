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
import android.widget.Toast;

public class TaskManager {

    private static final boolean DEBUG_FAST_LEVELING = false;
    private static final int DEBUG_REWARD_MULTIPLIER = 5;

    private final DatabaseHelper dbHelper;
    private final Context appContext;

    public TaskManager(Context context) {
        this.dbHelper = new DatabaseHelper(context);
        this.appContext = context.getApplicationContext();
    }

    private static final int BASE_DAILY_QUEST_SLOTS = 3;
    private static final int MAX_DAILY_QUEST_SLOTS = 5;
    private static final int LEVELS_PER_EXTRA_SLOT = 5;

    public static int getDailyQuestSlotCount(int level) {
        int bonusSlots = level / LEVELS_PER_EXTRA_SLOT;
        int totalSlots = BASE_DAILY_QUEST_SLOTS + bonusSlots;
        return Math.min(totalSlots, MAX_DAILY_QUEST_SLOTS);
    }

    private int getCurrentUserLevelInternal(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT level FROM user WHERE _id = 1", null);
        int level = 1;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                level = cursor.getInt(0);
            }
            cursor.close();
        }
        return level;
    }

    private void purgeLegacyCringeQuests(SQLiteDatabase db) {
        if (db == null) return;
        try {
            db.execSQL("DELETE FROM task_templates WHERE title IN (" +
                    "'Write journal entries', 'Read a book', 'Practice programming syntax layout', " +
                    "'Reduce screen time', 'No social media apps', 'Stay away from PC or Console gaming', " +
                    "'Sketch or draw something down', 'Jumping jacks routine', 'Do stretching exercise')");

            db.execSQL("DELETE FROM daily_tasks WHERE title IN (" +
                    "'Write journal entries', 'Read a book', 'Practice programming syntax layout', " +
                    "'Reduce screen time', 'No social media apps', 'Stay away from PC or Console gaming', " +
                    "'Sketch or draw something down', 'Jumping jacks routine', 'Do stretching exercise')");

            Cursor templateCursor = db.rawQuery("SELECT COUNT(*) FROM task_templates", null);
            if (templateCursor != null) {
                if (templateCursor.moveToFirst() && templateCursor.getInt(0) < 10) {
                    DatabaseHelper.seedTaskTemplates(db);
                }
                templateCursor.close();
            }
        } catch (Exception ignored) {}
    }

    public void generateDailyTasks() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        purgeLegacyCringeQuests(db);

        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        checkAndUpdateStreak(db, currentDate);

        Cursor checkCringe = db.rawQuery("SELECT COUNT(*) FROM daily_tasks WHERE is_custom = 0", null);
        boolean hasCringeTasks = false;
        if (checkCringe != null) {
            if (checkCringe.moveToFirst() && checkCringe.getInt(0) == 0) {
                hasCringeTasks = true;
            }
            checkCringe.close();
        }

        if (areTasksAlreadyGenerated(db, currentDate) && !hasCringeTasks) {
            return;
        }

        java.util.List<ContentValues> dueRecurringQuests = collectDueRecurringCustomQuests(db, currentDate);

        db.delete(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, null);

        for (ContentValues recurringValues : dueRecurringQuests) {
            db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, recurringValues);
        }

        int currentLevel = getCurrentUserLevelInternal(db);
        int totalSlots = getDailyQuestSlotCount(currentLevel);
        int usedSlots = dueRecurringQuests.size();

        boolean avoidanceInserted = false;
        java.util.List<String[]> blockedApps = getBlockedApps(db);

        if (!blockedApps.isEmpty() && new Random().nextBoolean() && usedSlots < totalSlots) {
            double detoxMult = getMultiplier(db, "Detox Duration Multiplier");
            ContentValues values = buildAvoidanceValues(blockedApps, detoxMult, currentDate);
            db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
            avoidanceInserted = true;
            usedSlots++;
        }

        int remainingSlots = Math.max(totalSlots - usedSlots, 0);
        insertRandomAdditionalTasks(db, currentDate, remainingSlots);
    }

    private java.util.List<ContentValues> collectDueRecurringCustomQuests(SQLiteDatabase db, String currentDate) {
        java.util.List<ContentValues> result = new java.util.ArrayList<>();

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                null,
                DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM + " = 1 AND " +
                        DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_INTERVAL + " > 0",
                null, null, null, null
        );

        if (cursor == null) {
            return result;
        }

        while (cursor.moveToNext()) {
            String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_TITLE));
            int target = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE));
            String unit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_UNIT));
            String questType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE));
            int rewardGold = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD));
            int rewardXp = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP));
            String unitType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_UNIT_TYPE));
            int repeatInterval = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_INTERVAL));
            String repeatUnit = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_UNIT));
            String repeatWeekdays = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_WEEKDAYS));
            String repeatEndType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_TYPE));
            String repeatEndValue = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_VALUE));
            String repeatStartDate = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_START_DATE));
            int occurrencesDone = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_OCCURRENCES_DONE));

            if (repeatStartDate == null || repeatStartDate.isEmpty()) {
                continue;
            }

            if ("COUNT".equals(repeatEndType) && !repeatEndValue.isEmpty()) {
                try {
                    int maxOccurrences = Integer.parseInt(repeatEndValue);
                    if (occurrencesDone >= maxOccurrences) {
                        continue;
                    }
                } catch (NumberFormatException ignored) {
                }
            }

            if ("DATE".equals(repeatEndType) && !repeatEndValue.isEmpty()) {
                if (currentDate.compareTo(normalizeDateString(repeatEndValue)) > 0) {
                    continue;
                }
            }

            if (!isRecurrenceDueToday(repeatStartDate, currentDate, repeatInterval, repeatUnit, repeatWeekdays)) {
                continue;
            }

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, target);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, currentDate);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, questType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, "Custom");
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD, rewardGold);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP, rewardXp);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM, 1);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT_TYPE, unitType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_INTERVAL, repeatInterval);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_UNIT, repeatUnit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_WEEKDAYS, repeatWeekdays);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_TYPE, repeatEndType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_VALUE, repeatEndValue);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_START_DATE, repeatStartDate);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_OCCURRENCES_DONE, occurrencesDone + 1);

            result.add(values);
        }
        cursor.close();

        return result;
    }

    private String normalizeDateString(String rawDate) {
        try {
            String[] parts = rawDate.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int day = Integer.parseInt(parts[2]);
            return String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month, day);
        } catch (Exception e) {
            return rawDate;
        }
    }

    private boolean isRecurrenceDueToday(String startDateStr, String todayStr, int interval, String unit, String weekdaysCsv) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Calendar startCal = Calendar.getInstance();
            startCal.setTime(sdf.parse(normalizeDateString(startDateStr)));
            Calendar todayCal = Calendar.getInstance();
            todayCal.setTime(sdf.parse(todayStr));

            if (todayCal.before(startCal)) {
                return false;
            }

            long dayDiffMillis = todayCal.getTimeInMillis() - startCal.getTimeInMillis();
            int daysBetween = (int) (dayDiffMillis / (1000L * 60 * 60 * 24));

            if ("Day".equals(unit)) {
                return daysBetween % interval == 0;
            } else if ("Week".equals(unit)) {
                if (weekdaysCsv != null && !weekdaysCsv.isEmpty()) {
                    int todayWeekdayIndex = todayCal.get(Calendar.DAY_OF_WEEK) - 1;
                    boolean matchesWeekday = false;
                    for (String part : weekdaysCsv.split(",")) {
                        if (String.valueOf(todayWeekdayIndex).equals(part.trim())) {
                            matchesWeekday = true;
                            break;
                        }
                    }
                    if (!matchesWeekday) {
                        return false;
                    }
                    int weekIndex = daysBetween / 7;
                    return weekIndex % interval == 0;
                } else {
                    return daysBetween % (7 * interval) == 0;
                }
            } else if ("Month".equals(unit)) {
                if (todayCal.get(Calendar.DAY_OF_MONTH) != startCal.get(Calendar.DAY_OF_MONTH)) {
                    return false;
                }
                int monthsBetween = (todayCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR)) * 12
                        + (todayCal.get(Calendar.MONTH) - startCal.get(Calendar.MONTH));
                return monthsBetween % interval == 0;
            } else if ("Year".equals(unit)) {
                if (todayCal.get(Calendar.MONTH) != startCal.get(Calendar.MONTH)
                        || todayCal.get(Calendar.DAY_OF_MONTH) != startCal.get(Calendar.DAY_OF_MONTH)) {
                    return false;
                }
                int yearsBetween = todayCal.get(Calendar.YEAR) - startCal.get(Calendar.YEAR);
                return yearsBetween % interval == 0;
            }

            return false;
        } catch (Exception e) {
            Log.e("TaskManager", "Error evaluating recurrence", e);
            return false;
        }
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

    private int[] computeScaledQuestReward(int baseGold, int baseXp, int userLevel, float scaleMultiplier) {
        int scaledGold = Math.round((baseGold + (userLevel - 1) * 2) * scaleMultiplier);
        int scaledXp = Math.round((baseXp + (userLevel - 1) * 3) * scaleMultiplier);
        if (DEBUG_FAST_LEVELING) {
            scaledGold *= DEBUG_REWARD_MULTIPLIER;
            scaledXp *= DEBUG_REWARD_MULTIPLIER;
        }
        return new int[]{ Math.max(scaledGold, 5), Math.max(scaledXp, 5) };
    }

    private void applyTierReward(ContentValues values, String tier) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        int userLevel = getCurrentUserLevelInternal(db);
        int baseGold = DatabaseContract.DailyTaskEntry.TIER_HARD.equals(tier) ? 35 : (DatabaseContract.DailyTaskEntry.TIER_MEDIUM.equals(tier) ? 20 : 10);
        int baseXp = DatabaseContract.DailyTaskEntry.TIER_HARD.equals(tier) ? 50 : (DatabaseContract.DailyTaskEntry.TIER_MEDIUM.equals(tier) ? 30 : 15);
        int[] reward = computeScaledQuestReward(baseGold, baseXp, userLevel, 1.0f);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD, reward[0]);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP, reward[1]);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER, tier);
    }

    private void insertRandomAdditionalTasks(SQLiteDatabase db, String dateStr, int count) {
        int userLevel = getCurrentUserLevelInternal(db);

        for (int i = 0; i < count; i++) {
            String query = "SELECT title, base_value, unit, quest_type, sub_category, difficulty_tier FROM task_templates WHERE title NOT IN (SELECT title FROM " +
                    DatabaseContract.DailyTaskEntry.TABLE_NAME + " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?) ORDER BY RANDOM() LIMIT 1";

            Cursor cursor = db.rawQuery(query, new String[]{ dateStr });

            if (cursor != null && cursor.moveToFirst()) {
                String title = cursor.getString(0);
                int baseValue = cursor.getInt(1);
                String unit = cursor.getString(2);
                String newQuestType = cursor.getString(3);
                String subCategory = cursor.getString(4);
                String tier = cursor.getString(5);
                cursor.close();

                double multiplier = getMultiplier(db, subCategory);
                int finalTarget = (int) (baseValue * multiplier);
                float stepRatio = 1.0f;

                if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(newQuestType) || "Walk steps".equalsIgnoreCase(title)) {
                    finalTarget = 3000 + new Random().nextInt(7001);
                    stepRatio = finalTarget / 3000.0f;
                } else if ("day".equalsIgnoreCase(unit)) {
                    finalTarget = 1;
                }

                int baseGold = DatabaseContract.DailyTaskEntry.TIER_HARD.equals(tier) ? 35 : (DatabaseContract.DailyTaskEntry.TIER_MEDIUM.equals(tier) ? 20 : 10);
                int baseXp = DatabaseContract.DailyTaskEntry.TIER_HARD.equals(tier) ? 50 : (DatabaseContract.DailyTaskEntry.TIER_MEDIUM.equals(tier) ? 30 : 15);
                int[] scaledReward = computeScaledQuestReward(baseGold, baseXp, userLevel, stepRatio);

                ContentValues values = new ContentValues();
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTarget);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, dateStr);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, newQuestType);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, subCategory);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER, tier);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD, scaledReward[0]);
                values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP, scaledReward[1]);

                db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);
            } else {
                if (cursor != null) {
                    cursor.close();
                }
                break;
            }
        }
    }

    public String getCustomAppNamesFormatted(String packagesCsv) {
        if (packagesCsv == null || packagesCsv.trim().isEmpty()) {
            return getBlockedAppNamesFormatted();
        }
        android.content.pm.PackageManager pm = appContext.getPackageManager();
        StringBuilder sb = new StringBuilder();
        for (String pkg : packagesCsv.split(",")) {
            String p = pkg.trim();
            if (p.isEmpty()) continue;
            try {
                android.content.pm.ApplicationInfo appInfo = pm.getApplicationInfo(p, 0);
                String label = pm.getApplicationLabel(appInfo).toString();
                if (sb.length() > 0) sb.append(", ");
                sb.append(label);
            } catch (Exception e) {
                if (sb.length() > 0) sb.append(", ");
                sb.append(p);
            }
        }
        return sb.length() > 0 ? sb.toString() : getBlockedAppNamesFormatted();
    }

    public String getBlockedAppNamesFormatted() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        java.util.List<String[]> blockedApps = getBlockedApps(db);
        if (blockedApps.isEmpty()) {
            return "None selected";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blockedApps.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(blockedApps.get(i)[1]);
        }
        return sb.toString();
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
        applyTierReward(values, DatabaseContract.DailyTaskEntry.TIER_HARD);

        if (useGroupQuest) {
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid social media");
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER, "Warns you every time you open an app in your block list during active screen avoidance.");
            values.putNull(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME);
        } else {
            String[] randomApp = blockedApps.get(random.nextInt(blockedApps.size()));
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, "Avoid " + randomApp[1]);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER, "Stay off " + randomApp[1] + " while the screen avoidance timer is active.");
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

        String query = "SELECT title, base_value, unit, quest_type, sub_category, difficulty_tier FROM task_templates WHERE title NOT IN (SELECT title FROM " +
                DatabaseContract.DailyTaskEntry.TABLE_NAME + " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?) ORDER BY RANDOM() LIMIT 1";

        Cursor cursor = db.rawQuery(query, new String[]{ currentDate });

        if (cursor != null && cursor.moveToFirst()) {
            String title = cursor.getString(0);
            int baseValue = cursor.getInt(1);
            String unit = cursor.getString(2);
            String newQuestType = cursor.getString(3);
            String subCategory = cursor.getString(4);
            String tier = cursor.getString(5);
            cursor.close();

            double multiplier = getMultiplier(db, subCategory);
            int finalTarget = (int) (baseValue * multiplier);
            if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(newQuestType) || "Walk steps".equalsIgnoreCase(title)) {
                finalTarget = 3000 + new java.util.Random().nextInt(7001);
            } else if ("day".equalsIgnoreCase(unit)) {
                finalTarget = 1;
            }

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTarget);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, newQuestType);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, subCategory);
            values.putNull(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME);
            applyTierReward(values, tier);

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
                recordStreakHistory(db, currentDateStr, 1);
                updateLongestStreak(db, 1);
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
                    recordStreakHistory(db, currentDateStr, newStreak);
                    updateLongestStreak(db, newStreak);
                    checkAndClaimStreakPredictionReward(appContext, newStreak);
                    Log.i("TaskManager", "Streak incremented to " + newStreak + " via daily login.");
                } else if (todayDate != null && todayDate.after(cal.getTime())) {
                    int protectorQty = getConsumableQuantity(db, DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR);
                    if (protectorQty > 0) {
                        useConsumable(db, DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR);
                        values.put("last_completed_date", currentDateStr);
                        db.update("user", values, "_id = 1", null);
                        recordStreakHistory(db, currentDateStr, currentStreak);
                        showAchievementUnlockedToast("Streak Protector used! Your streak was saved.");
                        Log.i("TaskManager", "Streak Protector consumed. Streak preserved at " + currentStreak);
                    } else {
                        values.put("streak", 1);
                        values.put("last_completed_date", currentDateStr);
                        db.update("user", values, "_id = 1", null);
                        syncStreakAchievements(db, 1);
                        recordStreakHistory(db, currentDateStr, 1);
                        setStreakPredictionTarget(appContext, 0);
                        Log.i("TaskManager", "Streak reset to 1. Calendar day gap detected.");
                    }
                } else {
                    values.put("last_completed_date", currentDateStr);
                    db.update("user", values, "_id = 1", null);
                }
            } catch (Exception e) {
                Log.e("TaskManager", "Error parsing streak login sequence dates", e);
            }
        }
    }

    private void recordStreakHistory(SQLiteDatabase db, String dateStr, int streakValue) {
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.StreakHistoryEntry.COLUMN_DATE, dateStr);
        values.put(DatabaseContract.StreakHistoryEntry.COLUMN_STREAK_VALUE, streakValue);
        values.put(DatabaseContract.StreakHistoryEntry.COLUMN_CHEST_CLAIMED, 0);
        db.insertWithOnConflict(DatabaseContract.StreakHistoryEntry.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private void updateLongestStreak(SQLiteDatabase db, int currentStreak) {
        Cursor cursor = db.rawQuery("SELECT longest_streak FROM user WHERE _id = 1", null);
        int longest = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                longest = cursor.getInt(0);
            }
            cursor.close();
        }
        if (currentStreak > longest) {
            ContentValues values = new ContentValues();
            values.put("longest_streak", currentStreak);
            db.update("user", values, "_id = 1", null);
        }
    }

    public int getLongestStreak() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT longest_streak FROM user WHERE _id = 1", null);
        int longest = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                longest = cursor.getInt(0);
            }
            cursor.close();
        }
        return longest;
    }

    public java.util.Map<String, int[]> getStreakHistoryForMonth(int year, int month) {
        java.util.Map<String, int[]> result = new java.util.HashMap<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String monthPrefix = String.format(Locale.getDefault(), "%04d-%02d", year, month + 1);

        Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseContract.StreakHistoryEntry.COLUMN_DATE + ", " +
                        DatabaseContract.StreakHistoryEntry.COLUMN_STREAK_VALUE + ", " +
                        DatabaseContract.StreakHistoryEntry.COLUMN_CHEST_CLAIMED +
                        " FROM " + DatabaseContract.StreakHistoryEntry.TABLE_NAME +
                        " WHERE " + DatabaseContract.StreakHistoryEntry.COLUMN_DATE + " LIKE ?",
                new String[]{ monthPrefix + "%" }
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                int streakValue = cursor.getInt(1);
                int claimed = cursor.getInt(2);
                result.put(date, new int[]{ streakValue, claimed });
            }
            cursor.close();
        }

        return result;
    }

    public boolean claimChest(String dateStr) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.query(
                DatabaseContract.StreakHistoryEntry.TABLE_NAME,
                new String[]{ DatabaseContract.StreakHistoryEntry.COLUMN_STREAK_VALUE, DatabaseContract.StreakHistoryEntry.COLUMN_CHEST_CLAIMED },
                DatabaseContract.StreakHistoryEntry.COLUMN_DATE + " = ?",
                new String[]{ dateStr },
                null, null, null
        );

        int streakValue = 0;
        int claimed = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                streakValue = cursor.getInt(0);
                claimed = cursor.getInt(1);
            }
            cursor.close();
        }

        if (streakValue <= 0 || streakValue % 3 != 0 || claimed == 1) {
            return false;
        }

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.StreakHistoryEntry.COLUMN_CHEST_CLAIMED, 1);
        db.update(
                DatabaseContract.StreakHistoryEntry.TABLE_NAME,
                values,
                DatabaseContract.StreakHistoryEntry.COLUMN_DATE + " = ?",
                new String[]{ dateStr }
        );

        ContentValues userValues = new ContentValues();
        Cursor userCursor = db.rawQuery("SELECT gold, xp FROM user WHERE _id = 1", null);
        int currentGold = 0;
        int currentXp = 0;
        if (userCursor != null && userCursor.moveToFirst()) {
            currentGold = userCursor.getInt(0);
            currentXp = userCursor.getInt(1);
            userCursor.close();
        }
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, currentGold + 50);
        userValues.put(DatabaseContract.UserEntry.COLUMN_XP, currentXp + 40);
        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
        syncUserProfileToFirestore(appContext);

        SoundEffectsHelper.playChestClaim(appContext);

        return true;
    }

    public void uncompleteTask(int taskId, int rewardGold, int rewardXp) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues taskValues = new ContentValues();
        taskValues.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
        taskValues.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
        taskValues.put(DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP, 0L);
        db.update(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                taskValues,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );

        Cursor userCursor = db.rawQuery("SELECT gold, xp FROM user WHERE _id = 1", null);
        if (userCursor != null) {
            if (userCursor.moveToFirst()) {
                int currentGold = userCursor.getInt(0);
                int currentXp = userCursor.getInt(1);

                int newGold = Math.max(0, currentGold - rewardGold);
                int newXp = Math.max(0, currentXp - rewardXp);

                ContentValues userValues = new ContentValues();
                userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, newGold);
                userValues.put(DatabaseContract.UserEntry.COLUMN_XP, newXp);
                db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
            }
            userCursor.close();
        }
        decrementChestBarPoints(appContext);
        syncUserProfileToFirestore(appContext);
    }

    public String getActiveStreakStartDate() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT streak, last_completed_date FROM user WHERE _id = 1", null);
        if (cursor != null && cursor.moveToFirst()) {
            int streak = cursor.getInt(0);
            String lastCompleted = cursor.getString(1);
            cursor.close();

            if (streak > 0 && lastCompleted != null && !lastCompleted.isEmpty()) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    Date lastDate = sdf.parse(lastCompleted);
                    if (lastDate != null) {
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(lastDate);
                        cal.add(Calendar.DAY_OF_YEAR, -(streak - 1));
                        return sdf.format(cal.getTime());
                    }
                } catch (Exception e) {
                    Log.e("TaskManager", "Error parsing active streak start date", e);
                }
            }
        }
        return null;
    }

    public static int getStreakPredictionTarget(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("pref_streak_prediction_target", 0);
    }

    public static boolean isStreakPredictionClaimed(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("pref_streak_prediction_claimed", false);
    }

    public static void setStreakPredictionTarget(Context context, int target) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("pref_streak_prediction_target", target)
                .putBoolean("pref_streak_prediction_claimed", false)
                .apply();
    }

    public static boolean hasUnclaimedStreakReward(Context context) {
        if (context == null) return false;
        int target = getStreakPredictionTarget(context);
        if (target <= 0 || isStreakPredictionClaimed(context)) {
            return false;
        }
        TaskManager tm = new TaskManager(context);
        Cursor cursor = tm.getUserProfile();
        int streak = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                if (cursor.getColumnCount() >= 5) {
                    streak = cursor.getInt(4);
                }
            }
            cursor.close();
        }
        return streak >= target;
    }

    public static boolean checkAndClaimStreakPredictionReward(Context context, int currentStreak) {
        int target = getStreakPredictionTarget(context);
        if (target <= 0 || isStreakPredictionClaimed(context)) {
            return false;
        }

        if (currentStreak >= target) {
            TaskManager tm = new TaskManager(context);
            SQLiteDatabase db = tm.dbHelper.getWritableDatabase();

            int bonusGold = 50;
            int bonusXp = 40;
            String consumableType = null;

            if (target == 5) {
                bonusGold = 80;
                bonusXp = 60;
                consumableType = DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR;
            } else if (target == 7) {
                bonusGold = 120;
                bonusXp = 100;
                consumableType = DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST;
            } else if (target >= 14) {
                bonusGold = 300;
                bonusXp = 250;
                consumableType = DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST;
            }

            Cursor userCursor = db.rawQuery("SELECT gold, xp FROM user WHERE _id = 1", null);
            int currentGold = 0;
            int currentXp = 0;
            if (userCursor != null && userCursor.moveToFirst()) {
                currentGold = userCursor.getInt(0);
                currentXp = userCursor.getInt(1);
                userCursor.close();
            }

            ContentValues userValues = new ContentValues();
            userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, currentGold + bonusGold);
            userValues.put(DatabaseContract.UserEntry.COLUMN_XP, currentXp + bonusXp);
            db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
            tm.syncUserProfileToFirestore(context);

            if (consumableType != null) {
                tm.grantConsumable(db, consumableType, 1);
            }

            android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putBoolean("pref_streak_prediction_claimed", true)
                    .putInt("pref_streak_prediction_target", 0)
                    .apply();

            SoundEffectsHelper.playChestClaim(context);
            ToastUtils.showToast(context, "🎯 " + target + "-Day Streak Goal Reached! Bonus Chest Rewards Claimed!");
            return true;
        }
        return false;
    }

    public static int getChestBarCurrentTier(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("pref_chest_bar_current_tier", 1);
    }

    public static int getChestBarTargetPoints(Context context) {
        int tier = getChestBarCurrentTier(context);
        if (tier == 1) return 3;
        if (tier == 2) return 5;
        if (tier == 3) return 8;
        return 10;
    }

    public static int getChestBarPoints(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getInt("pref_chest_bar_points", 0);
    }

    public static void advanceToNextChestTier(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        int currentTier = prefs.getInt("pref_chest_bar_current_tier", 1);
        int nextTier = Math.min(currentTier + 1, 4);
        prefs.edit()
                .putInt("pref_chest_bar_current_tier", nextTier)
                .putInt("pref_chest_bar_points", 0)
                .apply();
    }

    public static void decrementChestBarPoints(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        int current = prefs.getInt("pref_chest_bar_points", 0);
        int updated = Math.max(0, current - 1);
        prefs.edit().putInt("pref_chest_bar_points", updated).apply();
    }

    public static void incrementChestBarPoints(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        int target = getChestBarTargetPoints(context);
        int current = prefs.getInt("pref_chest_bar_points", 0);
        int updated = Math.min(current + 1, target);
        prefs.edit().putInt("pref_chest_bar_points", updated).apply();
    }

    public static boolean claimChestTier(Context context, int tier) {
        TaskManager tm = new TaskManager(context);
        SQLiteDatabase db = tm.dbHelper.getWritableDatabase();

        int bonusGold = 30;
        int bonusXp = 20;
        String consumableType = null;

        if (tier == 2) {
            bonusGold = 80;
            bonusXp = 60;
            consumableType = DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST;
        } else if (tier >= 3) {
            bonusGold = 200;
            bonusXp = 150;
            consumableType = DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST;
        }

        Cursor userCursor = db.rawQuery("SELECT gold, xp FROM user WHERE _id = 1", null);
        int currentGold = 0;
        int currentXp = 0;
        if (userCursor != null && userCursor.moveToFirst()) {
            currentGold = userCursor.getInt(0);
            currentXp = userCursor.getInt(1);
            userCursor.close();
        }

        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, currentGold + bonusGold);
        userValues.put(DatabaseContract.UserEntry.COLUMN_XP, currentXp + bonusXp);
        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
        tm.syncUserProfileToFirestore(context);

        if (consumableType != null) {
            tm.grantConsumable(db, consumableType, 1);
        }

        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("pref_chest_bar_current_tier", 1)
                .putInt("pref_chest_bar_points", 0)
                .apply();

        SoundEffectsHelper.playChestClaim(context);
        OnlineShopManager.logChestClaimOnline(context, tier, bonusGold, bonusXp, consumableType);
        ToastUtils.showToast(context, "🎁 Chest Opened! +" + bonusGold + " Gold, +" + bonusXp + " XP Granted!");
        return true;
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

    public void awardChestLoot(ChestLootResult loot) {
        if (loot == null) return;
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.rawQuery("SELECT gold, xp, level FROM user WHERE _id = 1", null);
        int gold = 0, xp = 0, level = 1;
        if (cursor != null && cursor.moveToFirst()) {
            gold = cursor.getInt(0);
            xp = cursor.getInt(1);
            level = cursor.getInt(2);
            cursor.close();
        }

        int newGold = gold + loot.gold;
        int newXp = xp + loot.xp;
        int maxXp = getRequiredXpForLevel(level);

        while (newXp >= maxXp) {
            newXp -= maxXp;
            level++;
            maxXp = getRequiredXpForLevel(level);
        }

        ContentValues userValues = new ContentValues();
        userValues.put("gold", newGold);
        userValues.put("xp", newXp);
        userValues.put("level", level);
        db.update("user", userValues, "_id = 1", null);

        for (String type : loot.consumables) {
            grantConsumable(db, type, 1);
        }
        decrementChestBarPoints(appContext);
        syncUserProfileToFirestore(appContext);
    }

    public static String getConsumableDisplayName(String type) {
        if (DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR.equals(type)) return "Streak Shield";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST.equals(type)) return "2x XP Boost";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST.equals(type)) return "2x Gold Boost";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH.equals(type)) return "Shop Refresh Token";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_QUEST_REFRESH.equals(type)) return "Quest Refresh Token";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5.equals(type)) return "1.5x XP Boost";
        if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5.equals(type)) return "1.5x Gold Boost";
        return "Bonus Reward";
    }

    public static int getRequiredXpForLevel(int level) {
        if (level <= 1) return 30;
        if (level == 2) return 50;
        if (level == 3) return 80;
        if (level == 4) return 100;
        return 100 + (level - 4) * 25;
    }

    public static class ChestLootResult {
        public int gold;
        public int xp;
        public java.util.List<String> consumables = new java.util.ArrayList<>();
    }

    public static ChestLootResult generateRandomChestLoot(Context context, int tier) {
        ChestLootResult result = new ChestLootResult();
        java.util.Random rnd = new java.util.Random();

        if (tier == 1) {
            result.gold = 10 + rnd.nextInt(21);
            result.xp = 10 + rnd.nextInt(16);
            if (rnd.nextBoolean()) {
                result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_QUEST_REFRESH);
            }
        } else if (tier == 2) {
            result.gold = 30 + rnd.nextInt(31);
            result.xp = 30 + rnd.nextInt(21);
            int count = 1 + rnd.nextInt(3);
            for (int i = 0; i < count; i++) {
                int roll = rnd.nextInt(3);
                if (roll == 0) result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_QUEST_REFRESH);
                else if (roll == 1) result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH);
                else result.consumables.add(rnd.nextBoolean() ? DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5 : DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
            }
        } else if (tier == 3) {
            result.gold = 60 + rnd.nextInt(61);
            result.xp = 50 + rnd.nextInt(51);
            int count = 2 + rnd.nextInt(3);
            for (int i = 0; i < count; i++) {
                int roll = rnd.nextInt(4);
                if (roll == 0) result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_QUEST_REFRESH);
                else if (roll == 1) result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH);
                else if (roll == 2) result.consumables.add(rnd.nextBoolean() ? DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5 : DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
                else result.consumables.add(rnd.nextBoolean() ? DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST : DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);
            }
        } else {
            result.gold = 120 + rnd.nextInt(131);
            result.xp = 100 + rnd.nextInt(101);
            int count = 3 + rnd.nextInt(3);
            for (int i = 0; i < count; i++) {
                int roll = rnd.nextInt(3);
                if (roll == 0) result.consumables.add(rnd.nextBoolean() ? DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5 : DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
                else if (roll == 1) result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
                else result.consumables.add(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);
            }
        }
        return result;
    }

    public void completeTask(int taskId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String selection = DatabaseContract.DailyTaskEntry._ID + " = ?";
        String[] selectionArgs = { String.valueOf(taskId) };

        int rewardGold = 10;
        int rewardXp = 15;
        boolean isCustom = false;

        Cursor taskCursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM,
                        DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD,
                        DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP
                },
                selection, selectionArgs, null, null, null
        );

        if (taskCursor != null && taskCursor.moveToFirst()) {
            isCustom = taskCursor.getInt(0) == 1;
            rewardGold = taskCursor.getInt(1);
            rewardXp = taskCursor.getInt(2);
            taskCursor.close();
        }

        String currentDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        android.content.SharedPreferences prefs = appContext.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        if (isGoldBoostActive(appContext)) {
            String activeType = prefs.getString("pref_active_gold_boost_type", "");
            if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5.equals(activeType)) {
                rewardGold = (int) Math.round(rewardGold * 1.5);
            } else {
                rewardGold *= 2;
            }
        }
        if (isXpBoostActive(appContext)) {
            String activeType = prefs.getString("pref_active_xp_boost_type", "");
            if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5.equals(activeType)) {
                rewardXp = (int) Math.round(rewardXp * 1.5);
            } else {
                rewardXp *= 2;
            }
        }

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
        int maxXp = getRequiredXpForLevel(currentLevel);

        while (newXp >= maxXp) {
            newXp -= maxXp;
            currentLevel++;
            maxXp = getRequiredXpForLevel(currentLevel);
        }

        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, newGold);
        userValues.put(DatabaseContract.UserEntry.COLUMN_XP, newXp);
        userValues.put("level", currentLevel);

        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);
        syncUserProfileToFirestore(appContext);

        if (currentLevel > levelBeforeThisCompletion) {
            grantLevelUpRewards(db, currentLevel);
            SoundEffectsHelper.playLevelUp(appContext);
        } else {
            SoundEffectsHelper.playQuestComplete(appContext);
        }

        incrementCounterAchievements(db, "QUEST_COUNT");
        updateCounterAchievementValue(db, "CHARACTER_LEVEL_RANK", currentLevel);
        if (isCustom) {
            incrementCounterAchievements(db, "CUSTOM_QUEST_COUNT");
        }
        incrementChestBarPoints(appContext);
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

    public int getConsumableQuantity(String type) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        return getConsumableQuantity(db, type);
    }

    public int getConsumableQuantity(SQLiteDatabase db, String type) {
        Cursor cursor = db.query(
                DatabaseContract.InventoryConsumableEntry.TABLE_NAME,
                new String[]{ DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY },
                DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE + " = ?",
                new String[]{ type },
                null, null, null
        );
        int quantity = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                quantity = cursor.getInt(0);
            }
            cursor.close();
        }
        return quantity;
    }

    public void useConsumable(String type) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        useConsumable(db, type);
    }

    public void useConsumable(SQLiteDatabase db, String type) {
        int currentQty = getConsumableQuantity(db, type);
        if (currentQty <= 0) return;

        int newQty = currentQty - 1;
        if (newQty > 0) {
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.InventoryConsumableEntry.COLUMN_QUANTITY, newQty);
            db.update(
                    DatabaseContract.InventoryConsumableEntry.TABLE_NAME,
                    values,
                    DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE + " = ?",
                    new String[]{ type }
            );
        } else {
            db.delete(
                    DatabaseContract.InventoryConsumableEntry.TABLE_NAME,
                    DatabaseContract.InventoryConsumableEntry.COLUMN_TYPE + " = ?",
                    new String[]{ type }
            );
        }
    }

    public static boolean isXpBoostActive(Context context) {
        if (context == null) return false;
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        long exp = prefs.getLong("pref_xp_boost_expiration_timestamp", 0);
        return System.currentTimeMillis() < exp;
    }

    public static boolean isXpBoostActive(Context context, String unusedDateStr) {
        return isXpBoostActive(context);
    }

    public static boolean isGoldBoostActive(Context context) {
        if (context == null) return false;
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        long exp = prefs.getLong("pref_gold_boost_expiration_timestamp", 0);
        return System.currentTimeMillis() < exp;
    }

    public static boolean isGoldBoostActive(Context context, String unusedDateStr) {
        return isGoldBoostActive(context);
    }

    public boolean activateXpBoost(Context context) {
        return activateXpBoostWithType(context, DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
    }

    public boolean activateXpBoostWithType(Context context, String boostType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int qty = getConsumableQuantity(db, boostType);
        if (qty > 0) {
            useConsumable(db, boostType);
            long sixHoursMs = 6 * 60 * 60 * 1000L;
            long expiration = System.currentTimeMillis() + sixHoursMs;
            android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong("pref_xp_boost_expiration_timestamp", expiration)
                    .putString("pref_active_xp_boost_type", boostType)
                    .apply();
            return true;
        }
        return false;
    }

    public boolean activateGoldBoost(Context context) {
        return activateGoldBoostWithType(context, DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);
    }

    public boolean activateGoldBoostWithType(Context context, String boostType) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int qty = getConsumableQuantity(db, boostType);
        if (qty > 0) {
            useConsumable(db, boostType);
            long sixHoursMs = 6 * 60 * 60 * 1000L;
            long expiration = System.currentTimeMillis() + sixHoursMs;
            android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            prefs.edit()
                    .putLong("pref_gold_boost_expiration_timestamp", expiration)
                    .putString("pref_active_gold_boost_type", boostType)
                    .apply();
            return true;
        }
        return false;
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

    private void updateCounterAchievementValue(SQLiteDatabase db, String achievementType, int targetValue) {
        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.AchievementEntry._ID,
                        DatabaseContract.AchievementEntry.COLUMN_TITLE,
                        DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS,
                        DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE
                },
                DatabaseContract.AchievementEntry.COLUMN_TYPE + " = ?",
                new String[]{ achievementType },
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

                int newProgress = Math.min(Math.max(oldProgress, targetValue), maxThreshold);

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

    private void incrementCounterAchievements(SQLiteDatabase db, String achievementType) {
        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                new String[]{
                        DatabaseContract.AchievementEntry._ID,
                        DatabaseContract.AchievementEntry.COLUMN_TITLE,
                        DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS,
                        DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE
                },
                DatabaseContract.AchievementEntry.COLUMN_TYPE + " = ?",
                new String[]{ achievementType },
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
        if (appContext == null) {
            return;
        }
        SoundEffectsHelper.playAchievementUnlock(appContext);
        boolean toastsEnabled = appContext.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE)
                .getBoolean("pref_notif_toasts", true);
        if (toastsEnabled) {
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
        SoundEffectsHelper.playButton(appContext);
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
        SoundEffectsHelper.playPause(appContext);
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

    public void removeQuest(int taskId) {
        SoundEffectsHelper.playCancel(appContext);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                DatabaseContract.DailyTaskEntry._ID + " = ?",
                new String[]{ String.valueOf(taskId) }
        );
    }

    public static final int CUSTOM_QUEST_GOLD_MIN = 5;

    public static int getCustomQuestAllowance(int level) {
        return Math.min(1 + (level / 5), 10);
    }

    public static int getCustomQuestGoldMax(int level) {
        return 10 + (level - 1) * 5;
    }

    public static int computeCustomQuestXp(int goldPicked, int level) {
        int goldMax = getCustomQuestGoldMax(level);
        int rawXp = (int) Math.round((goldMax - goldPicked + CUSTOM_QUEST_GOLD_MIN) * 1.2);
        return Math.max(rawXp, 5);
    }

    private String getCurrentWeekStartDate() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        return sdf.format(cal.getTime());
    }

    public int getCustomQuestsUsedThisWeek() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentWeekStart = getCurrentWeekStartDate();

        Cursor cursor = db.rawQuery(
                "SELECT " + DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_COUNT + ", " +
                        DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_WEEK_START + " FROM user WHERE _id = 1",
                null
        );

        int usedCount = 0;
        String storedWeekStart = "";

        if (cursor != null) {
            if (cursor.moveToFirst()) {
                usedCount = cursor.getInt(0);
                storedWeekStart = cursor.getString(1);
            }
            cursor.close();
        }

        if (!currentWeekStart.equals(storedWeekStart)) {
            ContentValues values = new ContentValues();
            values.put(DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_COUNT, 0);
            values.put(DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_WEEK_START, currentWeekStart);
            db.update(DatabaseContract.UserEntry.TABLE_NAME, values, "_id = 1", null);
            return 0;
        }

        return usedCount;
    }

    public int getRemainingCustomQuests(int level) {
        int allowance = getCustomQuestAllowance(level);
        int used = getCustomQuestsUsedThisWeek();
        return Math.max(allowance - used, 0);
    }

    public boolean createCustomQuest(String title, String description, int target, String unit, int goldPicked, int level, String unitType, int repeatInterval, String repeatUnit, String repeatWeekdays, String repeatEndType, String repeatEndValue) {
        return createCustomQuest(title, description, target, unit, goldPicked, level, unitType, repeatInterval, repeatUnit, repeatWeekdays, repeatEndType, repeatEndValue, null);
    }

    public boolean createCustomQuest(String title, String description, int target, String unit, int goldPicked, int level, String unitType, int repeatInterval, String repeatUnit, String repeatWeekdays, String repeatEndType, String repeatEndValue, String customBlockedPackagesCsv) {
        if (getRemainingCustomQuests(level) <= 0) {
            return false;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String currentDate = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int xpReward = computeCustomQuestXp(goldPicked, level);

        String questType = DatabaseContract.DailyTaskEntry.QUEST_TYPE_GENERIC;
        if (DatabaseContract.DailyTaskEntry.UNIT_TYPE_STEPS.equals(unitType)) {
            questType = DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS;
        } else if (DatabaseContract.DailyTaskEntry.UNIT_TYPE_REPETITION.equals(unitType)) {
            questType = DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT;
        } else if (DatabaseContract.DailyTaskEntry.UNIT_TYPE_DURATION.equals(unitType)) {
            questType = DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID;
        }

        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_USER_REF, 1);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, title);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER, description);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, target);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_DATE, currentDate);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE, questType);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE, 0);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_CATEGORY_TAG, "Custom");
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD, goldPicked);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP, xpReward);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM, 1);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT_TYPE, unitType);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_INTERVAL, repeatInterval);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_UNIT, repeatUnit);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_WEEKDAYS, repeatWeekdays);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_TYPE, repeatEndType);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_END_VALUE, repeatEndValue);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_START_DATE, currentDate);
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_REPEAT_OCCURRENCES_DONE, 1);

        if (customBlockedPackagesCsv != null && !customBlockedPackagesCsv.trim().isEmpty()) {
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME, customBlockedPackagesCsv.trim());
        }

        db.insert(DatabaseContract.DailyTaskEntry.TABLE_NAME, null, values);

        ContentValues userValues = new ContentValues();
        userValues.put(DatabaseContract.UserEntry.COLUMN_CUSTOM_QUEST_COUNT, getCustomQuestsUsedThisWeek() + 1);
        db.update(DatabaseContract.UserEntry.TABLE_NAME, userValues, "_id = 1", null);

        if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(questType) && appContext != null) {
            try {
                android.content.Intent stepServiceIntent = new android.content.Intent(appContext, StepTrackingService.class);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    appContext.startForegroundService(stepServiceIntent);
                } else {
                    appContext.startService(stepServiceIntent);
                }
            } catch (Exception ignored) {}
        }

        return true;
    }

    public Cursor getUserProfile() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        checkAndUpdateStreak(db, todayDateStr);

        return db.rawQuery("SELECT username, level, gold, xp, streak FROM user WHERE _id = 1", null);
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

        // Classic Glasses Variants (IDs 101 - 105)
        items.add(new ShopItem(101, "Purple Classic Glasses", 50, "accessory", "accessory_glasses_purple", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(102, "Yellow Classic Glasses", 50, "accessory", "accessory_glasses_yellow", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(103, "Black Classic Glasses", 50, "accessory", "accessory_glasses_black", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(104, "Red Classic Glasses", 50, "accessory", "accessory_glasses_red", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(105, "Blue Classic Glasses", 50, "accessory", "accessory_glasses_blue", "COMMON", 1, "\uD83D\uDC53"));

        // Reading Glasses Variants (IDs 106 - 110)
        items.add(new ShopItem(106, "Purple Reading Glasses", 50, "accessory", "accessory_reading_glasses_purple", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(107, "Yellow Reading Glasses", 50, "accessory", "accessory_reading_glasses_yellow", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(108, "Black Reading Glasses", 50, "accessory", "accessory_reading_glasses_black", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(109, "Red Reading Glasses", 50, "accessory", "accessory_reading_glasses_red", "COMMON", 1, "\uD83D\uDC53"));
        items.add(new ShopItem(110, "Blue Reading Glasses", 50, "accessory", "accessory_reading_glasses_blue", "COMMON", 1, "\uD83D\uDC53"));

        // Sunglasses Variants (IDs 111 - 115)
        items.add(new ShopItem(111, "Purple Sunglasses", 50, "accessory", "accessory_sunglasses_purple", "COMMON", 1, "\uD83D\uDD76"));
        items.add(new ShopItem(112, "Yellow Sunglasses", 50, "accessory", "accessory_sunglasses_yellow", "COMMON", 1, "\uD83D\uDD76"));
        items.add(new ShopItem(113, "Black Sunglasses", 50, "accessory", "accessory_sunglasses_black", "COMMON", 1, "\uD83D\uDD76"));
        items.add(new ShopItem(114, "Red Sunglasses", 50, "accessory", "accessory_sunglasses_red", "COMMON", 1, "\uD83D\uDD76"));
        items.add(new ShopItem(115, "Blue Sunglasses", 50, "accessory", "accessory_sunglasses_blue", "COMMON", 1, "\uD83D\uDD76"));

        // Tulip Hat Variants (IDs 116 - 118)
        items.add(new ShopItem(116, "Orange Tulip Hat", 60, "accessory", "accessory_tulip_hat_orange", "UNCOMMON", 1, "\uD83C\uDF37"));
        items.add(new ShopItem(117, "Purple Tulip Hat", 60, "accessory", "accessory_tulip_hat_purple", "UNCOMMON", 1, "\uD83C\uDF37"));
        items.add(new ShopItem(118, "Pink Tulip Hat", 60, "accessory", "accessory_tulip_hat_pink", "UNCOMMON", 1, "\uD83C\uDF37"));

        // Flower Hat Variants (IDs 119 - 121)
        items.add(new ShopItem(119, "Purple Flower Hat", 60, "accessory", "accessory_flower_hat_purple", "UNCOMMON", 1, "\uD83C\uDF38"));
        items.add(new ShopItem(120, "Pink Flower Hat", 60, "accessory", "accessory_flower_hat_pink", "UNCOMMON", 1, "\uD83C\uDF38"));
        items.add(new ShopItem(121, "Straw Flower Hat", 60, "accessory", "accessory_flower_hat_straw", "UNCOMMON", 1, "\uD83C\uDF38"));

        // Knit Hat Variants (IDs 122 - 124)
        items.add(new ShopItem(122, "Red Knit Hat", 60, "accessory", "accessory_knit_hat_red", "UNCOMMON", 1, "\uD83E\uDDE2"));
        items.add(new ShopItem(123, "Yellow Knit Hat", 60, "accessory", "accessory_knit_hat_yellow", "UNCOMMON", 1, "\uD83E\uDDE2"));
        items.add(new ShopItem(124, "Blue Knit Hat", 60, "accessory", "accessory_knit_hat_blue", "UNCOMMON", 1, "\uD83E\uDDE2"));

        // Hair Bow Variants (IDs 125 - 129)
        items.add(new ShopItem(125, "Red Hair Bow", 55, "accessory", "accessory_hair_bow_red", "COMMON", 1, "\uD83C\uDF80"));
        items.add(new ShopItem(126, "Blue Hair Bow", 55, "accessory", "accessory_hair_bow_blue", "COMMON", 1, "\uD83C\uDF80"));
        items.add(new ShopItem(127, "Yellow Hair Bow", 55, "accessory", "accessory_hair_bow_yellow", "COMMON", 1, "\uD83C\uDF80"));
        items.add(new ShopItem(128, "Pink Hair Bow", 55, "accessory", "accessory_hair_bow_pink", "COMMON", 1, "\uD83C\uDF80"));
        items.add(new ShopItem(129, "Purple Hair Bow", 55, "accessory", "accessory_hair_bow_purple", "COMMON", 1, "\uD83C\uDF80"));

        // Hair Flower 1 Variants (IDs 130 - 134)
        items.add(new ShopItem(130, "Red Hair Flower 1", 55, "accessory", "accessory_hair_flower1_red", "COMMON", 1, "\uD83C\uDF3A"));
        items.add(new ShopItem(131, "Blue Hair Flower 1", 55, "accessory", "accessory_hair_flower1_blue", "COMMON", 1, "\uD83C\uDF3A"));
        items.add(new ShopItem(132, "Yellow Hair Flower 1", 55, "accessory", "accessory_hair_flower1_yellow", "COMMON", 1, "\uD83C\uDF3A"));
        items.add(new ShopItem(133, "Pink Hair Flower 1", 55, "accessory", "accessory_hair_flower1_pink", "COMMON", 1, "\uD83C\uDF3A"));
        items.add(new ShopItem(134, "Purple Hair Flower 1", 55, "accessory", "accessory_hair_flower1_purple", "COMMON", 1, "\uD83C\uDF3A"));

        // Hair Butterfly Variants (IDs 135 - 139)
        items.add(new ShopItem(135, "Red Hair Butterfly", 55, "accessory", "accessory_hair_butterfly_red", "COMMON", 1, "\uD83E\uDD8B"));
        items.add(new ShopItem(136, "Blue Hair Butterfly", 55, "accessory", "accessory_hair_butterfly_blue", "COMMON", 1, "\uD83E\uDD8B"));
        items.add(new ShopItem(137, "Yellow Hair Butterfly", 55, "accessory", "accessory_hair_butterfly_yellow", "COMMON", 1, "\uD83E\uDD8B"));
        items.add(new ShopItem(138, "Pink Hair Butterfly", 55, "accessory", "accessory_hair_butterfly_pink", "COMMON", 1, "\uD83E\uDD8B"));
        items.add(new ShopItem(139, "Purple Hair Butterfly", 55, "accessory", "accessory_hair_butterfly_purple", "COMMON", 1, "\uD83E\uDD8B"));

        // Outfits & Clothing (COMMON Tier - IDs 203 - 208)
        items.add(new ShopItem(203, "Flame Shirt", 50, "outfit", "shirt_flame", "COMMON", 1, "\uD83D\uDD25"));
        items.add(new ShopItem(204, "Frog Shirt", 50, "outfit", "shirt_frog", "COMMON", 1, "\uD83D\uDC38"));
        items.add(new ShopItem(205, "Hawaiian Shirt", 50, "outfit", "shirt_hawaiian", "COMMON", 1, "\uD83C\uDF3A"));
        items.add(new ShopItem(206, "Flan Crop Top", 50, "outfit", "crop_flan", "COMMON", 1, "\uD83C\uDF6E"));
        items.add(new ShopItem(207, "Flower Crop Top", 50, "outfit", "crop_flower", "COMMON", 1, "\uD83C\uDF38"));
        items.add(new ShopItem(208, "Watermelon Crop Top", 50, "outfit", "crop_watermelon", "COMMON", 1, "\uD83C\uDF49"));

        // Puffy & Sleeveless Shirts (UNCOMMON Tier - IDs 209 - 213)
        items.add(new ShopItem(209, "Puffy Red Shirt", 120, "outfit", "puffy_red", "UNCOMMON", 15, "\uD83D\uDC54"));
        items.add(new ShopItem(210, "Puffy Green Shirt", 120, "outfit", "puffy_green", "UNCOMMON", 15, "\uD83D\uDC54"));
        items.add(new ShopItem(211, "Puffy Pink Shirt", 120, "outfit", "puffy_pink", "UNCOMMON", 15, "\uD83D\uDC54"));
        items.add(new ShopItem(212, "Sleeveless Pink Shirt", 120, "outfit", "sleeveless_pink", "UNCOMMON", 15, "\uD83E\uDDE5"));
        items.add(new ShopItem(213, "Sleeveless Shirt", 120, "outfit", "sleeveless_shirt", "UNCOMMON", 15, "\uD83E\uDDE5"));

        // Rare Outfits (RARE Tier - IDs 214 - 218)
        items.add(new ShopItem(214, "Green Long Dress", 300, "outfit", "long_dress_green", "RARE", 25, "\uD83D\uDC57"));
        items.add(new ShopItem(215, "Long Sleeve Hoodie", 300, "outfit", "longsleeve_hoodie", "RARE", 25, "\uD83E\uDDE5"));
        items.add(new ShopItem(216, "Rainbow Long Sleeve", 300, "outfit", "longsleeve_rainbow", "RARE", 25, "\uD83C\uDF08"));
        items.add(new ShopItem(217, "Long Sleeve Sweater", 300, "outfit", "longsleeve_sweater", "RARE", 25, "\uD83E\uDDF6"));
        items.add(new ShopItem(218, "Uniform Long Dress", 300, "outfit", "long_dress_uniform", "RARE", 25, "\uD83E\uDD4B"));

        // Epic / Rarest Outfits (EPIC Tier - IDs 219 - 222)
        items.add(new ShopItem(219, "Cupcake Dress", 600, "outfit", "cupcake_dress", "EPIC", 40, "\uD83E\uDDC1"));
        items.add(new ShopItem(220, "Green Cupcake Dress", 600, "outfit", "cupcake_green", "EPIC", 40, "\uD83E\uDDC1"));
        items.add(new ShopItem(221, "Royal Cupcake Dress", 600, "outfit", "cupcake_royal", "EPIC", 40, "\uD83D\uDC51"));
        items.add(new ShopItem(222, "Royal Long Sleeve", 600, "outfit", "long_sleeve_royal", "EPIC", 40, "\uD83D\uDC51"));

        return items;
    }

    public void resetDailyQuests() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED, 0);
        db.update(DatabaseContract.DailyTaskEntry.TABLE_NAME, values, null, null);
    }

    public int getShopRefreshCost(Context context) {
        if (getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH) > 0) {
            return 0;
        }
        return 30;
    }

    public boolean performShopRefresh(Context context) {
        int cost = getShopRefreshCost(context);
        if (cost == 0) {
            useConsumable(DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH);
            OnlineShopManager.forceShopRotationRefresh(context);
            ToastUtils.showToast(context, "Shop Refreshed using Refresh Token! 🔄");
            return true;
        } else {
            int gold = getUserGoldBalance();
            if (gold >= cost) {
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues values = new ContentValues();
                values.put("gold", gold - cost);
                db.update("user", values, "_id = 1", null);
                syncUserProfileToFirestore(context);

                OnlineShopManager.forceShopRotationRefresh(context);
                ToastUtils.showToast(context, "Shop Refreshed! (-" + cost + "g) 🔄");
                return true;
            } else {
                ToastUtils.showToast(context, "Not enough Gold to refresh shop!");
                return false;
            }
        }
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
        syncUserProfileToFirestore(appContext);

        ContentValues invValues = new ContentValues();
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_ITEM_ID, item.getId());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_ITEM_NAME, item.getName());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_CATEGORY, item.getCategory());
        invValues.put(DatabaseContract.InventoryEntry.COLUMN_RES_NAME, item.getResName());
        db.insert(DatabaseContract.InventoryEntry.TABLE_NAME, null, invValues);

        incrementCounterAchievements(db, "CLOTHING_COLLECTION_COUNT");
        SoundEffectsHelper.playPurchaseItem(appContext);

        return true;
    }

    public java.util.List<ShopItem> getOwnedItems() {
        java.util.List<ShopItem> ownedList = new java.util.ArrayList<>();
        java.util.Map<Integer, ShopItem> shopMap = new java.util.HashMap<>();
        for (ShopItem shopItem : getShopItems()) {
            shopMap.put(shopItem.getId(), shopItem);
        }

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
                if (id == 202) continue;

                String name = cursor.getString(1);
                String category = cursor.getString(2);
                String resName = cursor.getString(3);

                if (shopMap.containsKey(id)) {
                    ownedList.add(shopMap.get(id));
                } else {
                    ownedList.add(new ShopItem(id, name, 0, category, resName));
                }
            }
            cursor.close();
        }
        return ownedList;
    }

    public boolean isItemOwned(int itemId) {
        for (ShopItem owned : getOwnedItems()) {
            if (owned.getId() == itemId) {
                return true;
            }
        }
        return false;
    }

    public void syncUserProfileToFirestore(Context context) {
        if (context == null) return;
        try {
            android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            String uid = prefs.getString("user_uid", null);
            if (uid == null || uid.isEmpty()) {
                com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
                if (fUser != null) {
                    uid = fUser.getUid();
                    prefs.edit().putString("user_uid", uid).apply();
                }
            }
            if (uid == null || uid.isEmpty()) return;

            if (!OnlineShopManager.isNetworkAvailable(context)) return;

            Cursor cursor = getUserProfile();
            if (cursor != null && cursor.moveToFirst()) {
                String username = cursor.getString(0);
                int level = cursor.getInt(1);
                int gold = cursor.getInt(2);
                int xp = cursor.getInt(3);
                int streak = cursor.getInt(4);
                cursor.close();

                com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("username", username);
                map.put("level", level);
                map.put("gold", gold);
                map.put("xp", xp);
                map.put("streak", streak);
                map.put("last_synced", new java.util.Date());

                firestore.collection("users").document(uid)
                        .set(map, com.google.firebase.firestore.SetOptions.merge())
                        .addOnSuccessListener(aVoid -> Log.i("TaskManager", "User profile synced to Firestore: Lvl " + level + " Gold " + gold))
                        .addOnFailureListener(e -> Log.e("TaskManager", "Failed to sync user profile to Firestore", e));
            }
        } catch (Exception e) {
            Log.e("TaskManager", "Firestore sync exception", e);
        }
    }

    public static String getItemSlotType(ShopItem item) {
        if (item == null) return "glasses";
        String cat = item.getCategory();
        String resName = item.getResName() != null ? item.getResName().toLowerCase() : "";

        if ("outfit".equalsIgnoreCase(cat) || "shirt".equalsIgnoreCase(cat) || "pants".equalsIgnoreCase(cat)) {
            return "clothes";
        }
        if (resName.contains("hat") || resName.contains("hair")) {
            return "hat";
        }
        return "glasses";
    }

    public static ShopItem getEquippedItemForSlot(Context context, String slotType) {
        if (context == null) return null;
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        String prefKey = "pref_equipped_glasses_id";
        if ("clothes".equals(slotType)) prefKey = "pref_equipped_clothes_id";
        else if ("hat".equals(slotType)) prefKey = "pref_equipped_hat_id";

        int equippedId = prefs.getInt(prefKey, -1);
        if (equippedId <= 0) return null;

        TaskManager tm = new TaskManager(context);
        for (ShopItem item : tm.getOwnedItems()) {
            if (item.getId() == equippedId) return item;
        }
        for (ShopItem item : tm.getShopItems()) {
            if (item.getId() == equippedId) return item;
        }
        return null;
    }

    public static ShopItem getEquippedItem(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        int equippedId = prefs.getInt("pref_equipped_item_id", -1);
        if (equippedId <= 0) {
            return null;
        }
        TaskManager tm = new TaskManager(context);
        for (ShopItem item : tm.getOwnedItems()) {
            if (item.getId() == equippedId) {
                return item;
            }
        }
        for (ShopItem item : tm.getShopItems()) {
            if (item.getId() == equippedId) {
                return item;
            }
        }
        return null;
    }

    public static void setEquippedItem(Context context, ShopItem item) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        if (item == null) {
            editor.remove("pref_equipped_item_id")
                    .remove("pref_equipped_clothes_id")
                    .remove("pref_equipped_glasses_id")
                    .remove("pref_equipped_hat_id")
                    .remove("pref_avatar_accessory")
                    .remove("pref_avatar_accessory_color")
                    .putString("pref_avatar_clothes", "tank_top")
                    .apply();
            Log.d("AvatarDebug", "setEquippedItem: NULL (unequipped all)");
        } else {
            String slotType = getItemSlotType(item);
            if ("clothes".equals(slotType)) {
                editor.putInt("pref_equipped_clothes_id", item.getId())
                        .putString("pref_avatar_clothes", item.getResName());
            } else if ("hat".equals(slotType)) {
                editor.putInt("pref_equipped_hat_id", item.getId());
            } else {
                editor.putInt("pref_equipped_glasses_id", item.getId());
            }
            editor.putInt("pref_equipped_item_id", item.getId()).apply();
            Log.d("AvatarDebug", "setEquippedItem: slot=" + slotType + " itemId=" + item.getId() + " resName=" + item.getResName());
        }
        AvatarCompositor.clearCache();
    }

    public static void unequipItemInSlot(Context context, String slotType) {
        if (context == null) return;
        android.content.SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        if ("clothes".equals(slotType)) {
            editor.remove("pref_equipped_clothes_id").putString("pref_avatar_clothes", "tank_top");
        } else if ("hat".equals(slotType)) {
            editor.remove("pref_equipped_hat_id");
        } else {
            editor.remove("pref_equipped_glasses_id");
        }
        editor.apply();
        AvatarCompositor.clearCache();
    }
}