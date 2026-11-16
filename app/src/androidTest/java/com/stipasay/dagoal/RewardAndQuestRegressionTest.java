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
public class RewardAndQuestRegressionTest {
 private Context context; private SQLiteDatabase db; private SharedPreferences prefs;
 @Before public void setUp() {
  Context storage=InstrumentationRegistry.getInstrumentation().getTargetContext();
  context=new ContextWrapper(storage) {
   @Override public Context getApplicationContext(){return this;}
   @Override public Object getSystemService(String n){return Context.CONNECTIVITY_SERVICE.equals(n)?null:super.getSystemService(n);}
   @Override public SharedPreferences getSharedPreferences(String n,int m){return storage.getSharedPreferences("reward_quest_test_"+n,m);}
   @Override public File getDatabasePath(String n){return storage.getDatabasePath("reward_quest_test_"+n);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f){return storage.openOrCreateDatabase("reward_quest_test_"+n,m,f);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int m,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return storage.openOrCreateDatabase("reward_quest_test_"+n,m,f,h);}
  };
  prefs=context.getSharedPreferences("DaGoalPrefs",0);prefs.edit().clear().putBoolean("isGuestUser",true).commit();
  db=new DatabaseHelper(context).getWritableDatabase();
  db.delete("user",null,null);db.delete("daily_tasks",null,null);db.delete("quest_reward_receipts",null,null);db.delete("inventory",null,null);db.delete("inventory_consumables",null,null);DatabaseHelper.ensureUserTableExists(db);db.execSQL("UPDATE achievements SET current_progress=0");
 }
 private int scalar(String sql){try(Cursor c=db.rawQuery(sql,null)){assertTrue(c.moveToFirst());return c.getInt(0);}}
 private int quest(){ContentValues v=new ContentValues();v.put("title","Isolated audit quest");v.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,1);v.put("current_value",0);v.put("reward_gold",10);v.put("reward_xp",15);return (int)db.insertOrThrow("daily_tasks",null,v);}

 private void stock(String type,int qty){db.execSQL("INSERT INTO inventory_consumables(type,quantity) VALUES(?,?)",new Object[]{type,qty});}
 @Test public void xp15ActivatesAndPreservesXp2(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  stock(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5,1);stock(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST,1);
  TaskManager tm=new TaskManager(context);assertTrue(tm.activateXpBoostWithType(context,DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5));
  assertTrue(TaskManager.isXpBoostActive(context));assertEquals(1,tm.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST));
  assertEquals(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5,prefs.getString("pref_active_xp_boost_type",""));
  assertFalse(tm.activateXpBoostWithType(context,DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5));
 });}
 @Test public void gold15ActivatesWithoutGold2(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  stock(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5,1);TaskManager tm=new TaskManager(context);
  assertTrue(tm.activateGoldBoostWithType(context,DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5));assertTrue(TaskManager.isGoldBoostActive(context));
  int id=quest();tm.completeTask(id);assertEquals(15,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void activationSyncsAfterConsumption(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  stock(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5,1);final int[] syncs={0};
  TaskManager tm=new TaskManager(context){@Override public void syncUserProfileToFirestore(Context c){assertTrue(getAllConsumablesMap().isEmpty());assertTrue(TaskManager.isXpBoostActive(context));syncs[0]++;}};
  assertTrue(tm.activateXpBoostWithType(context,DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5));assertEquals(1,syncs[0]);
 });}
 @Test public void streakXpLevelsUpAndClaimsOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET xp=25 WHERE _id=1");TaskManager.setStreakPredictionTarget(context,3);
  assertTrue(TaskManager.checkAndClaimStreakPredictionReward(context,3));assertEquals(2,scalar("SELECT level FROM user WHERE _id=1"));assertEquals(35,scalar("SELECT xp FROM user WHERE _id=1"));
  assertFalse(TaskManager.checkAndClaimStreakPredictionReward(context,3));assertEquals(50,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void chestXpGrantsLevelGiftOnlyOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET level=2,xp=45,highest_rewarded_level=2 WHERE _id=1");TaskManager.ChestLootResult loot=new TaskManager.ChestLootResult();loot.xp=10;
  TaskManager tm=new TaskManager(context);tm.awardChestLoot(loot);assertEquals(3,scalar("SELECT level FROM user WHERE _id=1"));
  assertEquals(1,tm.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR));assertEquals(3,scalar("SELECT highest_rewarded_level FROM user WHERE _id=1"));
  tm.completeTask(quest());assertEquals(1,tm.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR));
 });}
 @Test public void claimPaysExactlyDisplayedLootAndRejectsRepeat(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  prefs.edit().putInt("pref_chest_bar_points",3).commit();TaskManager.ChestLootResult loot=TaskManager.claimChestLoot(context,1);assertNotNull(loot);
  assertEquals(loot.gold,scalar("SELECT gold FROM user WHERE _id=1"));
  int totalXp=scalar("SELECT xp FROM user WHERE _id=1");int level=scalar("SELECT level FROM user WHERE _id=1");for(int i=1;i<level;i++)totalXp+=TaskManager.getRequiredXpForLevel(i);assertEquals(loot.xp,totalXp);
  assertNull(TaskManager.claimChestLoot(context,1));assertEquals(loot.gold,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(0,TaskManager.getChestBarPoints(context));
 });}
 @Test public void incompleteOrWrongTierCannotBeClaimed(){
  assertNull(TaskManager.claimChestLoot(context,1));prefs.edit().putInt("pref_chest_bar_points",3).commit();assertNull(TaskManager.claimChestLoot(context,4));assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
 }
 @Test public void oldTierUndoPreservesNewTierPoint(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  TaskManager tm=new TaskManager(context);int old=quest();tm.completeTask(old);tm.completeTask(quest());tm.completeTask(quest());TaskManager.advanceToNextChestTier(context);
  tm.completeTask(quest());tm.uncompleteTask(old,10,15);assertEquals(1,TaskManager.getChestBarPoints(context));
 });}
 @Test public void sameTierUndoRemovesOnlyItsOwnPoint(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  TaskManager tm=new TaskManager(context);int id=quest();tm.completeTask(id);tm.uncompleteTask(id,10,15);assertEquals(0,TaskManager.getChestBarPoints(context));
 });}
 @Test public void consumedTypeIsRemovedByAuthoritativeSnapshot(){
  stock("XP_BOOST",2);stock("GOLD_BOOST",3);java.util.Map<String,Integer> remote=new java.util.HashMap<>();remote.put("GOLD_BOOST",1);
  TaskManager.restoreConsumablesFromSnapshot(remote,context);assertEquals(0,new TaskManager(context).getConsumableQuantity("XP_BOOST"));assertEquals(1,new TaskManager(context).getConsumableQuantity("GOLD_BOOST"));
 }
 @Test public void emptySnapshotClearsSpentInventory(){
  stock("XP_BOOST",2);TaskManager.restoreConsumablesFromSnapshot(java.util.Collections.emptyMap(),context);assertEquals(0,scalar("SELECT COUNT(*) FROM inventory_consumables"));
 }
 @Test public void malformedSnapshotDoesNotClearLocalInventory(){
  stock("XP_BOOST",2);TaskManager.restoreConsumablesFromSnapshot(java.util.Collections.singletonMap("XP_BOOST","invalid"),context);assertEquals(2,new TaskManager(context).getConsumableQuantity("XP_BOOST"));
 }
 @Test public void deleteChargesOneGoldOnlyOnce(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  db.execSQL("UPDATE user SET gold=5 WHERE _id=1");int id=quest();TaskManager tm=new TaskManager(context);assertTrue(tm.removeQuest(id));assertEquals(4,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(0,scalar("SELECT COUNT(*) FROM daily_tasks"));
  assertFalse(tm.removeQuest(id));assertEquals(4,scalar("SELECT gold FROM user WHERE _id=1"));
 });}
 @Test public void insufficientGoldKeepsQuestAndBalance(){
  int id=quest();assertFalse(new TaskManager(context).removeQuest(id));assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks"));assertEquals(0,scalar("SELECT gold FROM user WHERE _id=1"));
 }
 @Test public void completedQuestCannotBeDeletedForFee(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
  int id=quest();TaskManager tm=new TaskManager(context);tm.completeTask(id);assertFalse(tm.removeQuest(id));assertEquals(10,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(1,scalar("SELECT COUNT(*) FROM quest_reward_receipts"));
 });}
 @Test public void failedDeletionRollsBackFee(){
  db.execSQL("UPDATE user SET gold=5 WHERE _id=1");int id=quest();db.execSQL("CREATE TRIGGER reject_quest_delete BEFORE DELETE ON daily_tasks BEGIN SELECT RAISE(ABORT,'isolated rejection'); END");
  try{assertFalse(new TaskManager(context).removeQuest(id));assertEquals(5,scalar("SELECT gold FROM user WHERE _id=1"));assertEquals(1,scalar("SELECT COUNT(*) FROM daily_tasks"));}finally{db.execSQL("DROP TRIGGER reject_quest_delete");}
 }
 @Test public void olderCloudAcknowledgementCannotClearNewProgress(){
  String old=TaskManager.markProfileSyncPending(prefs);String current=TaskManager.markProfileSyncPending(prefs);
  TaskManager.acknowledgeProfileSync(prefs,old);assertEquals(current,prefs.getString(TaskManager.PENDING_PROFILE_SYNC,""));
  TaskManager.acknowledgeProfileSync(prefs,current);assertFalse(prefs.contains(TaskManager.PENDING_PROFILE_SYNC));
 }
}
