package com.stipasay.dagoal;

import android.content.*;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class DatabaseMigrationRegressionTest {
    private Context isolated(String prefix) {
        Context storage = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return new ContextWrapper(storage) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String n, int m) { return storage.getSharedPreferences(prefix + n, m); }
            @Override public File getDatabasePath(String n) { return storage.getDatabasePath(prefix + n); }
            @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f) {
                return storage.openOrCreateDatabase(prefix + n, m, f);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String n, int m, SQLiteDatabase.CursorFactory f, DatabaseErrorHandler h) {
                return storage.openOrCreateDatabase(prefix + n, m, f, h);
            }
        };
    }

    private int scalar(SQLiteDatabase db, String query) {
        try (Cursor cursor = db.rawQuery(query, null)) { assertTrue(cursor.moveToFirst()); return cursor.getInt(0); }
    }

    private void checkUpgrade(int version) throws Exception {
        String prefix = "migration_regression_" + version + "_" + System.nanoTime() + "_";
        Context context = isolated(prefix);
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", 0);
        prefs.edit().putString("last_quest_generation_date", "2026-11-17").commit();
        try (SQLiteDatabase db = context.openOrCreateDatabase("dagoal.db", 0, null)) {
            String[] fields = {"CREATE_TABLE_USER", "CREATE_TABLE_DAILY_TASKS", "CREATE_TABLE_TASK_TEMPLATES",
                    "CREATE_TABLE_INVENTORY", "CREATE_TABLE_INVENTORY_CONSUMABLES", "CREATE_TABLE_PREFERENCES",
                    "CREATE_TABLE_ACHIEVEMENTS", "CREATE_TABLE_BLOCKED_APPS", "CREATE_TABLE_STREAK_HISTORY"};
            for (String name : fields) {
                Field field = DatabaseHelper.class.getDeclaredField(name);
                field.setAccessible(true);
                String sql = ((String) field.get(null)).replace(", highest_rewarded_level INTEGER DEFAULT 1", "");
                if (version >= 25) sql = (String) field.get(null);
                sql = sql.replace(", recurring_schedule_id INTEGER DEFAULT 0", "");
                if (version == 23) sql = sql.replace("repeat_occurrences_done INTEGER DEFAULT 0, ", "");
                db.execSQL(sql);
            }
            db.execSQL("INSERT INTO user (_id,username,level,gold,xp) VALUES (1,'Saved profile',3,1234,25)");
            db.execSQL("INSERT INTO daily_tasks (_id,title,is_custom,is_completed,reward_gold,reward_xp) VALUES (42,'Keep this custom quest',1,1,10,15)");
            db.execSQL("INSERT INTO inventory (item_id,item_name,category,res_name) VALUES (203,'Flame Shirt','outfit','shirt_flame')");
            db.execSQL("INSERT INTO inventory_consumables (type,quantity) VALUES ('gold_boost',2)");
            db.execSQL("INSERT INTO preferences (user_id,activity_type,difficulty) VALUES (1,'Saved preference','2')");
            if (version >= 25) {
                db.execSQL("CREATE TABLE quest_reward_receipts(task_id INTEGER PRIMARY KEY,awarded_gold INTEGER NOT NULL,awarded_xp INTEGER NOT NULL,chest_point_awarded INTEGER DEFAULT 0)");
                db.execSQL("INSERT INTO quest_reward_receipts VALUES(42,20,30,1)");
            }
            if (version == 26) db.execSQL("UPDATE daily_tasks SET repeat_interval=2, repeat_unit='Day', repeat_start_date='2026-11-17', task_date='2026-11-17' WHERE _id=42");
            db.setVersion(version);
        }
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase upgraded = helper.getWritableDatabase();
            assertEquals(27, upgraded.getVersion());
            assertEquals(1234, scalar(upgraded, "SELECT gold FROM user WHERE _id=1"));
            assertEquals(25, scalar(upgraded, "SELECT xp FROM user WHERE _id=1"));
            assertEquals(3, scalar(upgraded, "SELECT highest_rewarded_level FROM user WHERE _id=1"));
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM daily_tasks WHERE _id=42 AND is_completed=1 AND is_custom=1"));
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM inventory WHERE item_id=203"));
            assertEquals(2, scalar(upgraded, "SELECT quantity FROM inventory_consumables"));
            assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM preferences"));
            assertEquals(version >= 25 ? 1 : 0, scalar(upgraded, "SELECT COUNT(*) FROM quest_reward_receipts"));
            if (version >= 25) {
                assertEquals(20, scalar(upgraded, "SELECT awarded_gold FROM quest_reward_receipts WHERE task_id=42"));
                assertEquals(-1, scalar(upgraded, "SELECT chest_cycle FROM quest_reward_receipts WHERE task_id=42"));
            }
            assertEquals("2026-11-17", prefs.getString("last_quest_generation_date", ""));
            if (version == 26) {
                assertEquals(1, scalar(upgraded, "SELECT COUNT(*) FROM recurring_quest_schedules WHERE _id=42 AND repeat_interval=2"));
                assertEquals(42, scalar(upgraded, "SELECT recurring_schedule_id FROM daily_tasks WHERE _id=42"));
            }
            if (version == 23) assertEquals(0, scalar(upgraded, "SELECT repeat_occurrences_done FROM daily_tasks WHERE _id=42"));
        }
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(prefix + "dagoal.db");
        prefs.edit().clear().commit();
    }

    @Test public void version26UpgradePreservesExistingData() throws Exception { checkUpgrade(26); }
    @Test public void version25UpgradePreservesRewardReceipts() throws Exception { checkUpgrade(25); }
    @Test public void version24UpgradePreservesExistingData() throws Exception { checkUpgrade(24); }
    @Test public void olderUpgradeAddsMissingColumnsWithoutDeletingData() throws Exception { checkUpgrade(23); }
    @Test public void freshInstallCreatesRewardReceiptSchema() {
        String prefix = "migration_fresh_" + System.nanoTime() + "_";
        try (DatabaseHelper helper = new DatabaseHelper(isolated(prefix))) {
            SQLiteDatabase db = helper.getWritableDatabase();
            DatabaseHelper.ensureUserTableExists(db);
            assertEquals(27, db.getVersion());
            assertEquals(1, scalar(db, "SELECT highest_rewarded_level FROM user WHERE _id=1"));
            assertEquals(0, scalar(db, "SELECT COUNT(*) FROM quest_reward_receipts"));
        }
        InstrumentationRegistry.getInstrumentation().getTargetContext().deleteDatabase(prefix + "dagoal.db");
    }
}
