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
public class DataIntegrityRegressionTest {
 private Context context; private SQLiteDatabase db; private SharedPreferences prefs;
 @Before public void setUp() {
  Context storage=InstrumentationRegistry.getInstrumentation().getTargetContext();
  context=new ContextWrapper(storage) {
   @Override public Context getApplicationContext(){return this;}
   @Override public SharedPreferences getSharedPreferences(String n,int m){return storage.getSharedPreferences("data_integrity_test_"+n,m);}
   @Override public File getDatabasePath(String n){return storage.getDatabasePath("data_integrity_test_"+n);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f){return storage.openOrCreateDatabase("data_integrity_test_"+n,m,f);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return storage.openOrCreateDatabase("data_integrity_test_"+n,m,f,h);}
  };
  prefs=context.getSharedPreferences("DaGoalPrefs",0);prefs.edit().clear().putBoolean("isGuestUser",true).commit();
  db=new DatabaseHelper(context).getWritableDatabase();
  db.delete("user",null,null);db.delete("daily_tasks",null,null);db.delete("quest_reward_receipts",null,null);db.delete("inventory",null,null);db.delete("inventory_consumables",null,null);DatabaseHelper.ensureUserTableExists(db);db.execSQL("UPDATE achievements SET current_progress=0");
 }
 private int scalar(String sql){try(Cursor c=db.rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getInt(0);}}
 private int quest(){ContentValues v=new ContentValues();v.put("title","Isolated audit quest");v.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,1);v.put("current_value",0);v.put("reward_gold",10);v.put("reward_xp",15);return (int)db.insertOrThrow("daily_tasks",null,v);}
 @Test public void boostedCompletionUndoRestoresGold(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=quest();prefs.edit().putLong("pref_gold_boost_expiration_timestamp",System.currentTimeMillis()+60000).commit();
  TaskManager tm=new TaskManager(context);tm.completeTask(id);assertEquals(20,scalar("SELECT gold FROM user WHERE _id=1"));
  tm.uncompleteTask(id,10,15);assertEquals("Undo should restore the original gold balance",0,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void undoAfterLevelUpRestoresOriginalProgress(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int threshold=TaskManager.getRequiredXpForLevel(1);db.execSQL("UPDATE user SET xp=? WHERE _id=1",new Object[]{threshold-5});
  int id=quest();TaskManager tm=new TaskManager(context);tm.completeTask(id);assertEquals(2,scalar("SELECT level FROM user WHERE _id=1"));
  tm.uncompleteTask(id,10,15);assertEquals("Undo should restore the level before completion",1,scalar("SELECT level FROM user WHERE _id=1"));
 });}
 @Test public void repeatedCountUpdatesAwardQuestOnlyOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=quest();TaskManager tm=new TaskManager(context);tm.incrementQuestProgress(id);assertEquals(10,scalar("SELECT gold FROM user WHERE _id=1"));
  tm.incrementQuestProgress(id);assertEquals("An already completed quest must not award gold again",10,scalar("SELECT gold FROM user WHERE _id=1"));
 });}

 @Test public void undoUsesRecordedBoostEvenAfterBoostExpires(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=quest();prefs.edit().putLong("pref_gold_boost_expiration_timestamp",System.currentTimeMillis()+60000)
   .putLong("pref_xp_boost_expiration_timestamp",System.currentTimeMillis()+60000).commit();
  TaskManager tm=new TaskManager(context);tm.completeTask(id);
  prefs.edit().remove("pref_gold_boost_expiration_timestamp").remove("pref_xp_boost_expiration_timestamp").commit();
  tm.uncompleteTask(id,10,15);assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
  assertEquals(0,scalar("SELECT xp FROM user WHERE _id=1"));assertEquals(1,scalar("SELECT level FROM user WHERE _id=1"));
  tm.uncompleteTask(id,10,15);assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void undoOutOfOrderPreservesOtherCompletedQuests(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET xp=25 WHERE _id=1");int first=quest();int second=quest();TaskManager tm=new TaskManager(context);
  tm.completeTask(first);tm.completeTask(second);tm.uncompleteTask(first,10,15);
  assertEquals(2,scalar("SELECT level FROM user WHERE _id=1"));assertEquals(10,scalar("SELECT xp FROM user WHERE _id=1"));
  tm.uncompleteTask(second,10,15);assertEquals(1,scalar("SELECT level FROM user WHERE _id=1"));assertEquals(25,scalar("SELECT xp FROM user WHERE _id=1"));
 });}
 @Test public void undoAndRecompleteCannotFarmLevelGifts(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET level=2,xp=45,highest_rewarded_level=2 WHERE _id=1");int id=quest();TaskManager tm=new TaskManager(context);
  tm.completeTask(id);int gifts=scalar("SELECT SUM(quantity) FROM inventory_consumables");assertEquals(1,gifts);
  tm.uncompleteTask(id,10,15);tm.completeTask(id);assertEquals(gifts,scalar("SELECT SUM(quantity) FROM inventory_consumables"));
 });}
 @Test public void legacyCompletedQuestUsesSafeBaseRewardFallback(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=quest();db.execSQL("UPDATE daily_tasks SET is_completed=1 WHERE _id=?",new Object[]{id});
  db.execSQL("UPDATE user SET gold=10,xp=15 WHERE _id=1");new TaskManager(context).uncompleteTask(id,99999,99999);
  assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(0,scalar("SELECT xp FROM user WHERE _id=1"));
 });}
 @Test public void purchaseIsOwnedBeforeSynchronizationAndCannotChargeTwice(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET gold=1000 WHERE _id=1");final int[] syncs={0};
  TaskManager tm=new TaskManager(context){@Override public void syncUserProfileToFirestore(Context ignored){
   assertFalse("Sync must happen after commit",db.inTransaction());assertEquals(1,scalar("SELECT COUNT(*) FROM inventory WHERE item_id=203"));
   assertEquals(950,scalar("SELECT gold FROM user WHERE _id=1"));syncs[0]++;
  }};
  ShopItem shirt=new ShopItem(203,"Flame Shirt",50,"outfit","shirt_flame");assertTrue(tm.purchaseShopItem(shirt));assertEquals(1,syncs[0]);
  assertFalse(tm.purchaseShopItem(shirt));assertEquals(950,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(1,syncs[0]);
 });}
 @Test public void failedPurchaseRollsBackGold(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET gold=1000 WHERE _id=1");
  db.execSQL("CREATE TRIGGER reject_audit_purchase BEFORE INSERT ON inventory BEGIN SELECT RAISE(ABORT,'isolated test rejection'); END");
  try{assertFalse(new TaskManager(context).purchaseShopItem(new ShopItem(203,"Flame Shirt",50,"outfit","shirt_flame")));
   assertEquals(1000,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(0,scalar("SELECT COUNT(*) FROM inventory"));
  }finally{db.execSQL("DROP TRIGGER reject_audit_purchase");}
 });}
 @Test public void missingQuestCannotAwardDefaultRewards(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  new TaskManager(context).completeTask(Integer.MAX_VALUE);assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void offlineAccessRequiresMatchingAuthenticatedAccount(){
  prefs.edit().putBoolean("isGuestUser",false).putString("user_uid","account-a").putString("user_email","a@example.test").commit();
  assertFalse(AccountSessionHelper.canResumeOffline(prefs,(String)null,null,"a@example.test"));
  assertFalse(AccountSessionHelper.canResumeOffline(prefs,"account-b","b@example.test","b@example.test"));
  assertFalse(AccountSessionHelper.canResumeOffline(prefs,"account-a","a@example.test","b@example.test"));
  assertTrue(AccountSessionHelper.canResumeOffline(prefs,"account-a","A@example.test","a@example.test"));
  prefs.edit().putBoolean("isGuestUser",true).commit();assertFalse(AccountSessionHelper.ownsLocalProfile(prefs,"account-a"));
 }

 @Test public void concurrentCompletionAwardsOnlyOnce() throws Exception {
  int id=quest();java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
  java.util.concurrent.CountDownLatch start=new java.util.concurrent.CountDownLatch(1);
  try {
   java.util.concurrent.Future<?> first=pool.submit(()->{try{start.await();new TaskManager(context).completeTask(id);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   java.util.concurrent.Future<?> second=pool.submit(()->{try{start.await();new TaskManager(context).completeTask(id);}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   start.countDown();first.get(20,java.util.concurrent.TimeUnit.SECONDS);second.get(20,java.util.concurrent.TimeUnit.SECONDS);
   assertEquals(10,scalar("SELECT gold FROM user WHERE _id=1"));
   assertEquals(1,scalar("SELECT COUNT(*) FROM quest_reward_receipts WHERE task_id="+id));
  } finally {pool.shutdownNow();}
 }
}
