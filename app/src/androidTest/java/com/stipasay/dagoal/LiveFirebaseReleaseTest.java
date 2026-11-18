package com.stipasay.dagoal;

import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.*;
import com.google.firebase.firestore.*;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Opt-in live checks. Separate FirebaseApp instances never change the phone owner's session. */
@RunWith(AndroidJUnit4.class)
public class LiveFirebaseReleaseTest {
 private static <T> T await(Task<T> task) throws Exception {return Tasks.await(task,35,TimeUnit.SECONDS);}
 private FirebaseApp a,b,anonymous;
 private FirebaseAuth authA,authB;
 private FirebaseFirestore dbA,dbB;
 private Context isolated;
 private FirebaseApp secondary(String suffix){return FirebaseApp.initializeApp(InstrumentationRegistry.getInstrumentation().getTargetContext(),FirebaseApp.getInstance().getOptions(),"release-check-"+suffix+UUID.randomUUID());}
 private FirebaseFirestore firestore(FirebaseApp app){FirebaseFirestore db=FirebaseFirestore.getInstance(app);db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(false).build());return db;}
 @Before public void setup(){
  Assume.assumeTrue("Enable live_release_checks explicitly", "true".equals(InstrumentationRegistry.getArguments().getString("live_release_checks")));
  a=secondary("a");b=secondary("b");anonymous=secondary("anonymous");authA=FirebaseAuth.getInstance(a);authB=FirebaseAuth.getInstance(b);dbA=firestore(a);dbB=firestore(b);
  Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();String prefix="live_release_check_"+UUID.randomUUID()+"_";
  isolated=new ContextWrapper(base){
   @Override public Context getApplicationContext(){return this;}
   @Override public SharedPreferences getSharedPreferences(String n,int mode){return base.getSharedPreferences(prefix+n,mode);}
   @Override public File getDatabasePath(String n){return base.getDatabasePath(prefix+n);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f){return base.openOrCreateDatabase(prefix+n,mode,f);}
   @Override public SQLiteDatabase openOrCreateDatabase(String n,int mode,SQLiteDatabase.CursorFactory f,DatabaseErrorHandler h){return base.openOrCreateDatabase(prefix+n,mode,f,h);}
  };
  isolated.getSharedPreferences("DaGoalPrefs",0).edit().putBoolean("isGuestUser",true).commit();
  try(DatabaseHelper helper=new DatabaseHelper(isolated)){DatabaseHelper.ensureUserTableExists(helper.getWritableDatabase());}
 }
 private void mustDeny(Task<?> task) throws Exception {
  try {await(task);fail("Cross-account or unauthenticated profile access was permitted");}
  catch(java.util.concurrent.ExecutionException error){assertTrue("Expected Firestore permission denial",error.getCause() instanceof FirebaseFirestoreException);assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,((FirebaseFirestoreException)error.getCause()).getCode());}
 }
 @Test public void liveAuthenticationBackupIsolationAndDeletion() throws Exception {
  String emailA="dagoal-release-"+UUID.randomUUID()+"@example.invalid";
  String emailB="dagoal-release-"+UUID.randomUUID()+"@example.invalid";
  String password=UUID.randomUUID().toString()+"Aa7!";
  FirebaseUser userA=await(authA.createUserWithEmailAndPassword(emailA,password)).getUser();assertNotNull(userA);
  FirebaseUser userB=await(authB.createUserWithEmailAndPassword(emailB,password)).getUser();assertNotNull(userB);
  String uidA=userA.getUid();DocumentReference own=dbA.collection("users").document(uidA);
  Map<String,Object> schedule=new HashMap<>();String today=new java.text.SimpleDateFormat("yyyy-MM-dd",Locale.US).format(new Date());
  schedule.put("_id",1L);schedule.put("title","Release verification quest");schedule.put("is_custom",1L);schedule.put("repeat_interval",2L);schedule.put("repeat_unit","Day");schedule.put("repeat_start_date",today);schedule.put("task_date",today);schedule.put("repeat_occurrences_done",1L);schedule.put("final_target",1L);schedule.put("reward_gold",10L);schedule.put("reward_xp",15L);
  Map<String,Object> profile=new HashMap<>();profile.put("release_check",true);profile.put("username","Release verification");profile.put("level",1L);profile.put("gold",50L);profile.put("xp",0L);profile.put("streak",7L);profile.put("last_completed_date",today);profile.put(RecurringQuestBackup.CLOUD_FIELD,Collections.singletonList(schedule));
  await(own.set(profile));assertEquals(Long.valueOf(50),await(own.get(Source.SERVER)).getLong("gold"));
  Object restored=await(own.get(Source.SERVER)).get(RecurringQuestBackup.CLOUD_FIELD);assertTrue(restored instanceof List);assertTrue(RecurringQuestBackup.restoreSchedules((List<?>)restored,isolated));
  try(DatabaseHelper helper=new DatabaseHelper(isolated);android.database.Cursor cursor=helper.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM recurring_quest_schedules",null)){assertTrue(cursor.moveToFirst());assertEquals(1,cursor.getInt(0));}
  mustDeny(dbB.collection("users").document(uidA).get(Source.SERVER));
  mustDeny(dbB.collection("users").document(uidA).update("release_check_cross_write",true));
  FirebaseFirestore publicDb=firestore(anonymous);mustDeny(publicDb.collection("users").document(uidA).get(Source.SERVER));
  authA.signOut();assertEquals(uidA,await(authA.signInWithEmailAndPassword(emailA,password)).getUser().getUid());
  assertEquals(Long.valueOf(7),await(own.get(Source.SERVER)).getLong("streak"));
  await(own.delete());assertFalse(await(own.get(Source.SERVER)).exists());await(authA.getCurrentUser().delete());assertNull(authA.getCurrentUser());
 }
 @After public void cleanup() throws Exception {
  if(a==null)return;
  Exception failure=null;
  for(FirebaseApp app:new FirebaseApp[]{a,b}){
   FirebaseAuth auth=FirebaseAuth.getInstance(app);FirebaseUser user=auth.getCurrentUser();
   if(user!=null){try{await(FirebaseFirestore.getInstance(app).collection("users").document(user.getUid()).delete());}catch(Exception ignored){}
    try{await(user.delete());}catch(Exception error){failure=error;}}
   try{await(FirebaseFirestore.getInstance(app).terminate());}catch(Exception ignored){}app.delete();
  }
  try{await(FirebaseFirestore.getInstance(anonymous).terminate());}catch(Exception ignored){}anonymous.delete();
  if(failure!=null)throw new AssertionError("Temporary Firebase account cleanup failed; review the release check records",failure);
 }
}
