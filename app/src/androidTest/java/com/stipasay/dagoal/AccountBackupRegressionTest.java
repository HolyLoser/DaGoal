package com.stipasay.dagoal;
import android.content.*;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class AccountBackupRegressionTest {
 private Context context; private SQLiteDatabase db; private SharedPreferences prefs;
 @Before public void setUp() {
  Context storage=InstrumentationRegistry.getInstrumentation().getTargetContext();
  context=new ContextWrapper(storage) {
   @Override public Context getApplicationContext(){return this;}
   @Override public Object getSystemService(String n){return Context.CONNECTIVITY_SERVICE.equals(n)?null:super.getSystemService(n);}
   @Override public SharedPreferences getSharedPreferences(String n,int m){return storage.getSharedPreferences("account_backup_test_"+n,m);}
   @Override public File getDatabasePath(String n){return storage.getDatabasePath("account_backup_test_"+n);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f){return storage.openOrCreateDatabase("account_backup_test_"+n,m,f);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return storage.openOrCreateDatabase("account_backup_test_"+n,m,f,h);}
  };
  context.getSharedPreferences("DaGoalScheduleBackups",0).edit().clear().commit();
  prefs=context.getSharedPreferences("DaGoalPrefs",0);prefs.edit().clear().putBoolean("isGuestUser",true).commit();
  db=new DatabaseHelper(context).getWritableDatabase();
  db.delete("recurring_quest_schedules",null,null);db.delete("daily_shuffle_usage",null,null);db.delete("streak_history",null,null);db.delete("user",null,null);db.delete("daily_tasks",null,null);db.delete("quest_reward_receipts",null,null);db.delete("inventory",null,null);db.delete("inventory_consumables",null,null);DatabaseHelper.ensureUserTableExists(db);db.execSQL("UPDATE achievements SET current_progress=0");
 }
 private int scalar(String sql){try(Cursor c=db.rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getInt(0);}}
 private int quest(){ContentValues v=new ContentValues();v.put("title","Isolated audit quest");v.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,1);v.put("current_value",0);v.put("reward_gold",10);v.put("reward_xp",15);return (int)db.insertOrThrow("daily_tasks",null,v);}



 @Test public void repeatedGenerationMustPreserveTodaysRecurringOnlyQuests(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  java.util.Calendar cal=java.util.Calendar.getInstance();cal.add(java.util.Calendar.DAY_OF_YEAR,-2);String start=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(cal.getTime());
  for(int i=0;i<3;i++){int id=quest();db.execSQL("UPDATE daily_tasks SET title=?,task_date=?,is_custom=1,repeat_interval=2,repeat_unit='Day',repeat_start_date=?,repeat_occurrences_done=1 WHERE _id=?",new Object[]{"Audit recurring "+i,start,start,id});}
  TaskManager tm=new TaskManager(context);tm.generateDailyTasks();assertEquals(3,scalar("SELECT COUNT(*) FROM daily_tasks WHERE is_custom=1"));
  tm.generateDailyTasks();assertEquals("Reopening reveal deleted today's three recurring quests",3,scalar("SELECT COUNT(*) FROM daily_tasks WHERE is_custom=1"));
 });}
 @Test public void failedCustomQuestSaveMustNotUseWeeklySlot(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("CREATE TRIGGER reject_custom_quest BEFORE INSERT ON daily_tasks BEGIN SELECT RAISE(ABORT,'isolated save failure'); END");
  try{TaskManager tm=new TaskManager(context);boolean result=tm.createCustomQuest("Audit failed save","description",1,"meal",5,1,DatabaseContract.DailyTaskEntry.UNIT_TYPE_GENERIC,0,"Day","","NEVER","");
   assertFalse("Failed insert returned success and weekly slots used="+tm.getCustomQuestsUsedThisWeek(),result);
  }finally{db.execSQL("DROP TRIGGER reject_custom_quest");}
 });}
 @Test public void restoredCloudStreakMustNotResetOnFirstProfileRead(){
  db.execSQL("UPDATE user SET streak=7,last_completed_date='' WHERE _id=1");
  try(Cursor profile=new TaskManager(context).getUserProfile()){assertTrue(profile.moveToFirst());assertEquals("Restored streak was reset because login does not restore last_completed_date",7,profile.getInt(4));}
 }

 private String today(){return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(new java.util.Date());}
 private int scheduleQuest(){int id=quest();db.execSQL("UPDATE daily_tasks SET title='Saved recurring quest',is_custom=1,task_date=?,repeat_interval=2,repeat_unit='Day',repeat_start_date=?,difficulty_tier='Original description',repeat_occurrences_done=1 WHERE _id=?",new Object[]{today(),today(),id});DatabaseHelper.archiveRecurringQuestSchedules(db);return id;}
 @Test public void schedulePayloadRestoresTodaysProgressWithoutAwardingAgain(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=scheduleQuest();db.execSQL("UPDATE daily_tasks SET current_value=1 WHERE _id=?",new Object[]{id});
  java.util.List<java.util.Map<String,Object>> payload=RecurringQuestBackup.exportSchedules(context);
  db.delete("daily_tasks",null,null);db.delete("recurring_quest_schedules",null,null);
  assertTrue(RecurringQuestBackup.restoreSchedules(payload,context));assertEquals(1,scalar("SELECT current_value FROM daily_tasks WHERE is_custom=1"));
  assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
  new TaskManager(context).generateDailyTasks();assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks WHERE is_custom=1"));
 });}
 @Test public void completedRecurringBackupRetainsExactBoostedUndo(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=scheduleQuest();prefs.edit().putLong("pref_gold_boost_expiration_timestamp",System.currentTimeMillis()+60000).commit();TaskManager tm=new TaskManager(context);tm.completeTask(id);
  java.util.List<java.util.Map<String,Object>> payload=RecurringQuestBackup.exportSchedules(context);db.delete("daily_tasks",null,null);db.delete("quest_reward_receipts",null,null);
  assertTrue(RecurringQuestBackup.restoreSchedules(payload,context));assertEquals(20,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(1,scalar("SELECT is_completed FROM daily_tasks WHERE is_custom=1"));
  int restored=scalar("SELECT _id FROM daily_tasks WHERE is_custom=1");tm.uncompleteTask(restored,10,15);assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void pendingLocalBackupIsOwnedByUidAndPreservesProgress(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  scheduleQuest();db.execSQL("UPDATE user SET gold=77 WHERE _id=1");db.execSQL("INSERT INTO inventory_consumables(type,quantity) VALUES('XP_BOOST',2)");
  RecurringQuestBackup.saveLocal(context,"account-a",RecurringQuestBackup.exportSchedules(context),"revision-a");DatabaseHelper.resetAccountAndUserData(context,false);
  assertTrue(RecurringQuestBackup.restoreAfterLogin(java.util.Collections.emptyList(),context,"account-b"));assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(0,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));
  assertTrue(RecurringQuestBackup.restoreAfterLogin(java.util.Collections.emptyList(),context,"account-a"));assertEquals(77,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(2,new TaskManager(context).getConsumableQuantity("XP_BOOST"));assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));
 });}
 @Test public void acknowledgedCacheDoesNotOverrideNewCloudSnapshot(){
  scheduleQuest();java.util.List<java.util.Map<String,Object>> payload=RecurringQuestBackup.exportSchedules(context);RecurringQuestBackup.saveLocal(context,"a",payload,"one");RecurringQuestBackup.acknowledge(context,"a","one");
  payload.get(0).put("title","New cloud title");assertTrue(RecurringQuestBackup.restoreAfterLogin(payload,context,"a"));
  try(Cursor c=db.rawQuery("SELECT title FROM recurring_quest_schedules",null)){assertTrue(c.moveToFirst());assertEquals("New cloud title",c.getString(0));}
 }
 @Test public void oldAcknowledgementCannotClearNewerLocalBackup(){
  scheduleQuest();java.util.List<java.util.Map<String,Object>> rows=RecurringQuestBackup.exportSchedules(context);RecurringQuestBackup.saveLocal(context,"a",rows,"one");RecurringQuestBackup.saveLocal(context,"a",rows,"two");
  RecurringQuestBackup.acknowledge(context,"a","one");assertTrue(RecurringQuestBackup.hasPending(context,"a"));RecurringQuestBackup.acknowledge(context,"a","two");assertFalse(RecurringQuestBackup.hasPending(context,"a"));
 }
 @Test public void malformedScheduleBackupDoesNotClearExistingData(){
  scheduleQuest();java.util.List<java.util.Map<String,Object>> rows=RecurringQuestBackup.exportSchedules(context);rows.get(0).put("_id","invalid");assertFalse(RecurringQuestBackup.restoreSchedules(rows,context));assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks"));
 }
 @Test public void absentLegacyCloudFieldKeepsSchedules(){
  scheduleQuest();assertTrue(RecurringQuestBackup.restoreAfterLogin(null,context,"legacy-account"));assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));
 }
 @Test public void deletingAccountForgetsItsLocalBackup(){
  scheduleQuest();RecurringQuestBackup.saveLocal(context,"deleted",RecurringQuestBackup.exportSchedules(context),"one");RecurringQuestBackup.forget(context,"deleted");assertFalse(RecurringQuestBackup.hasPending(context,"deleted"));assertFalse(context.getSharedPreferences("DaGoalScheduleBackups",0).contains("rows:deleted"));assertFalse(context.getSharedPreferences("DaGoalScheduleBackups",0).contains("state:deleted"));
 }
 @Test public void deletingAllAcceptedDailyQuestsDoesNotRegenerateThem(){
  prefs.edit().putString("last_quest_generation_date",today()).commit();new TaskManager(context).generateDailyTasks();assertEquals(0,scalar("SELECT COUNT(*) FROM daily_tasks"));
 }
 @Test public void failedScheduleArchiveRollsBackQuestAndWeeklySlot(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("CREATE TRIGGER reject_archive BEFORE INSERT ON recurring_quest_schedules BEGIN SELECT RAISE(ABORT,'isolated archive failure'); END");
  try{TaskManager tm=new TaskManager(context);assertFalse(tm.createCustomQuest("Atomic recurring","desc",1,"meal",5,1,DatabaseContract.DailyTaskEntry.UNIT_TYPE_GENERIC,2,"Day","","NEVER",""));assertEquals(0,scalar("SELECT COUNT(*) FROM daily_tasks"));assertEquals(0,tm.getCustomQuestsUsedThisWeek());}
  finally{db.execSQL("DROP TRIGGER reject_archive");}
 });}
 @Test public void customQuestCreationArchivesAndChargesWeeklySlotOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  TaskManager tm=new TaskManager(context);assertTrue(tm.createCustomQuest("Saved now","desc",1,"meal",5,1,DatabaseContract.DailyTaskEntry.UNIT_TYPE_GENERIC,2,"Day","","NEVER",""));assertEquals(1,tm.getCustomQuestsUsedThisWeek());assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));
  assertFalse(tm.createCustomQuest("Excess","desc",1,"meal",5,1,DatabaseContract.DailyTaskEntry.UNIT_TYPE_GENERIC,2,"Day","","NEVER",""));assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks"));
 });}
 @Test public void restoredStreakDateIncrementsOnceOnTheNextDay(){
  java.util.Calendar cal=java.util.Calendar.getInstance();cal.add(java.util.Calendar.DAY_OF_YEAR,-1);String yesterday=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(cal.getTime());db.execSQL("UPDATE user SET streak=7,last_completed_date=? WHERE _id=1",new Object[]{yesterday});
  TaskManager tm=new TaskManager(context);try(Cursor profile=tm.getUserProfile()){assertTrue(profile.moveToFirst());assertEquals(8,profile.getInt(4));}try(Cursor profile=tm.getUserProfile()){assertTrue(profile.moveToFirst());assertEquals(8,profile.getInt(4));}
 }
 @Test public void resetBackupFailureKeepsTheOriginalAccount(){
  scheduleQuest();prefs.edit().putBoolean("isGuestUser",false).putString("user_uid","original-account").commit();db.execSQL("UPDATE user SET gold=77 WHERE _id=1");
  db.execSQL("CREATE TRIGGER reject_reset_backup BEFORE INSERT ON recurring_quest_schedules BEGIN SELECT RAISE(ABORT,'isolated backup failure'); END");
  try{assertFalse(DatabaseHelper.resetAccountAndUserData(context,false));assertEquals("original-account",prefs.getString("user_uid",""));assertEquals(77,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks"));}
  finally{db.execSQL("DROP TRIGGER reject_reset_backup");}
 }
}
