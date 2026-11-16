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
public class ScheduleAndAccountRegressionTest {
 private Context context; private SQLiteDatabase db; private SharedPreferences prefs;
 @Before public void setUp() {
  Context storage=InstrumentationRegistry.getInstrumentation().getTargetContext();
  context=new ContextWrapper(storage) {
   @Override public Context getApplicationContext(){return this;}
   @Override public Object getSystemService(String n){return Context.CONNECTIVITY_SERVICE.equals(n)?null:super.getSystemService(n);}
   @Override public SharedPreferences getSharedPreferences(String n,int m){return storage.getSharedPreferences("schedule_account_test_"+n,m);}
   @Override public File getDatabasePath(String n){return storage.getDatabasePath("schedule_account_test_"+n);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f){return storage.openOrCreateDatabase("schedule_account_test_"+n,m,f);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return storage.openOrCreateDatabase("schedule_account_test_"+n,m,f,h);}
  };
  prefs=context.getSharedPreferences("DaGoalPrefs",0);prefs.edit().clear().putBoolean("isGuestUser",true).commit();
  db=new DatabaseHelper(context).getWritableDatabase();
  db.delete("recurring_quest_schedules",null,null);db.delete("daily_shuffle_usage",null,null);db.delete("streak_history",null,null);db.delete("user",null,null);db.delete("daily_tasks",null,null);db.delete("quest_reward_receipts",null,null);db.delete("inventory",null,null);db.delete("inventory_consumables",null,null);DatabaseHelper.ensureUserTableExists(db);db.execSQL("UPDATE achievements SET current_progress=0");
 }
 private int scalar(String sql){try(Cursor c=db.rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getInt(0);}}
 private int quest(){ContentValues v=new ContentValues();v.put("title","Isolated audit quest");v.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,1);v.put("current_value",0);v.put("reward_gold",10);v.put("reward_xp",15);return (int)db.insertOrThrow("daily_tasks",null,v);}


 @Test public void recurringQuestMustSurviveItsNonDueDay(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault());java.util.Calendar day=java.util.Calendar.getInstance();day.add(java.util.Calendar.DAY_OF_YEAR,-1);String yesterday=format.format(day.getTime());
  ContentValues v=new ContentValues();v.put("title","Audit repeat every two days");v.put("task_date",yesterday);v.put("is_custom",1);v.put("repeat_interval",2);v.put("repeat_unit","Day");v.put("repeat_start_date",yesterday);v.put("repeat_end_type","NEVER");v.put("repeat_end_value","");v.put("repeat_weekdays","");v.put("reward_gold",10);v.put("reward_xp",15);v.put("final_target",1);db.insertOrThrow("daily_tasks",null,v);
  new TaskManager(context).generateDailyTasks();assertEquals("Future recurring schedule must survive its non-due day",1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules WHERE title='Audit repeat every two days'"));
  assertEquals("Non-due quest must not appear in today's UI",0,scalar("SELECT COUNT(*) FROM daily_tasks WHERE title='Audit repeat every two days'"));
  day.add(java.util.Calendar.DAY_OF_YEAR,2);String tomorrow=format.format(day.getTime());
  try{java.lang.reflect.Method method=TaskManager.class.getDeclaredMethod("collectDueRecurringCustomQuests",SQLiteDatabase.class,String.class);method.setAccessible(true);java.util.List<?> due=(java.util.List<?>)method.invoke(new TaskManager(context),db,tomorrow);assertEquals(1,due.size());}catch(Exception e){throw new AssertionError(e);}
 });}
 @Test public void switchingAccountMustClearActiveBoost(){
  prefs.edit().putLong("pref_xp_boost_expiration_timestamp",System.currentTimeMillis()+60000).putString("pref_active_xp_boost_type","XP_BOOST").commit();
  DatabaseHelper.resetAccountAndUserData(context,false);assertFalse("Old account's boost remains active after reset",TaskManager.isXpBoostActive(context));
 }
 @Test public void switchingAccountMustClearChestProgress(){
  prefs.edit().putInt("pref_chest_bar_points",8).putInt("pref_chest_bar_current_tier",3).commit();
  DatabaseHelper.resetAccountAndUserData(context,false);assertEquals("New account inherits previous chest progress",0,TaskManager.getChestBarPoints(context));
 }
 @Test public void failedStreakChestMustRemainClaimable(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("INSERT INTO streak_history (history_date,streak_value,chest_claimed) VALUES('2026-11-17',3,0)");
  db.execSQL("CREATE TRIGGER reject_audit_reward BEFORE UPDATE ON user BEGIN SELECT RAISE(ABORT,'isolated save failure'); END");
  try{try{new TaskManager(context).claimChest("2026-11-17");}catch(android.database.sqlite.SQLiteException expected){}
   assertEquals("Claim marker was saved despite reward rollback",0,scalar("SELECT chest_claimed FROM streak_history WHERE history_date='2026-11-17'"));
  }finally{db.execSQL("DROP TRIGGER reject_audit_reward");}
 });}
 @Test public void shopRefreshTokenConsumptionMustSync(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("INSERT INTO inventory_consumables(type,quantity) VALUES('SHOP_REFRESH',1)");final int[] syncs={0};
  TaskManager tm=new TaskManager(context){@Override public void syncUserProfileToFirestore(Context c){syncs[0]++;}};
  assertTrue(tm.performShopRefresh(context));assertEquals(0,tm.getConsumableQuantity("SHOP_REFRESH"));assertEquals("Token use was not synced or marked pending",1,syncs[0]);
 });}

 private String today(){return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(new java.util.Date());}
 private int todayQuest(){int id=quest();db.execSQL("UPDATE daily_tasks SET task_date=? WHERE _id=?",new Object[]{today(),id});return id;}
 @Test public void shufflesPersistAcrossManagersAndUseFreeAllowanceBeforeTokens(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("INSERT INTO inventory_consumables(type,quantity) VALUES('QUEST_REFRESH',1)");TaskManager tm=new TaskManager(context);int id=todayQuest();
  int free=TaskManager.getDailyQuestSlotCount(1);assertEquals(free+1,tm.getRemainingDailyShuffles());
  for(int i=0;i<free;i++)assertTrue(new TaskManager(context).shuffleDailyQuest(id));
  assertEquals(1,new TaskManager(context).getRemainingDailyShuffles());assertEquals(1,tm.getConsumableQuantity("QUEST_REFRESH"));
  final int[] syncs={0};TaskManager synced=new TaskManager(context){@Override public void syncUserProfileToFirestore(Context c){syncs[0]++;}};
  assertTrue(synced.shuffleDailyQuest(id));assertEquals(1,syncs[0]);assertEquals(0,tm.getConsumableQuantity("QUEST_REFRESH"));assertEquals(0,new TaskManager(context).getRemainingDailyShuffles());assertFalse(tm.shuffleDailyQuest(id));
 });}
 @Test public void invalidShuffleDoesNotSpendAllowance(){
  TaskManager tm=new TaskManager(context);int allowance=tm.getRemainingDailyShuffles();assertFalse(tm.shuffleDailyQuest(Integer.MAX_VALUE));assertEquals(allowance,tm.getRemainingDailyShuffles());
 }
 @Test public void yesterdaysShufflesDoNotReduceTodaysAllowance(){
  db.execSQL("INSERT INTO daily_shuffle_usage(shuffle_date,used_count) VALUES('2020-01-01',99)");assertEquals(TaskManager.getDailyQuestSlotCount(1),new TaskManager(context).getRemainingDailyShuffles());
 }
 @Test public void accountResetKeepsAppearanceButClearsAccountCounters(){
  prefs.edit().putBoolean("pref_sound_enabled",false).putInt("pref_shop_reroll_count_today",5).putString("pref_shop_reroll_last_date",today()).putString(TaskManager.PENDING_PROFILE_SYNC,"old-account").commit();
  db.execSQL("INSERT INTO daily_shuffle_usage(shuffle_date,used_count) VALUES(?,3)",new Object[]{today()});DatabaseHelper.resetAccountAndUserData(context,false);
  assertFalse(prefs.getBoolean("pref_sound_enabled",true));assertFalse(prefs.contains(TaskManager.PENDING_PROFILE_SYNC));assertFalse(prefs.contains("pref_shop_reroll_count_today"));assertEquals(0,scalar("SELECT COUNT(*) FROM daily_shuffle_usage"));
 }
 @Test public void successfulStreakChestCannotBeClaimedAgain(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("INSERT INTO streak_history(history_date,streak_value,chest_claimed) VALUES('2026-11-17',3,0)");TaskManager tm=new TaskManager(context);assertTrue(tm.claimChest("2026-11-17"));assertFalse(tm.claimChest("2026-11-17"));assertEquals(50,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void deletingRecurringQuestDeletesItsSchedule(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=todayQuest();db.execSQL("UPDATE user SET gold=5 WHERE _id=1");db.execSQL("UPDATE daily_tasks SET is_custom=1,repeat_interval=2,repeat_unit='Day',repeat_start_date=? WHERE _id=?",new Object[]{today(),id});DatabaseHelper.archiveRecurringQuestSchedules(db);
  assertEquals(1,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));assertTrue(new TaskManager(context).removeQuest(id));assertEquals(0,scalar("SELECT COUNT(*) FROM recurring_quest_schedules"));
 });}
 @Test public void dueRecurringQuestPreservesDetailsAndOccursOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  java.util.Calendar day=java.util.Calendar.getInstance();day.add(java.util.Calendar.DAY_OF_YEAR,-2);String start=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(day.getTime());
  int id=quest();db.execSQL("UPDATE daily_tasks SET title='Repeat audit due',task_date=?,is_custom=1,repeat_interval=2,repeat_unit='Day',repeat_start_date=?,difficulty_tier='Saved description',package_name='saved.package',repeat_occurrences_done=1 WHERE _id=?",new Object[]{start,start,id});
  TaskManager tm=new TaskManager(context);tm.generateDailyTasks();assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks WHERE title='Repeat audit due' AND task_date='"+today()+"' AND difficulty_tier='Saved description' AND package_name='saved.package'"));
  tm.generateDailyTasks();assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks WHERE title='Repeat audit due'"));assertEquals(2,scalar("SELECT repeat_occurrences_done FROM recurring_quest_schedules"));
 });}
}
