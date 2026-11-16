package com.stipasay.dagoal;

import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;

/** Emulator-only fixture for proving an APK certificate-rotation update keeps application data. */
public class ReleaseMigrationProbeTest {
 @Test public void migrationPreservesApplicationData(){
  String phase=InstrumentationRegistry.getArguments().getString("release_migration_phase");Assume.assumeTrue(phase!=null);
  Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
  if("write".equals(phase)){
   context.getSharedPreferences("release_key_migration",0).edit().putString("marker","preserve-this-installation").commit();
   try(DatabaseHelper helper=new DatabaseHelper(context)){android.database.sqlite.SQLiteDatabase db=helper.getWritableDatabase();DatabaseHelper.ensureUserTableExists(db);db.execSQL("UPDATE user SET gold=1234 WHERE _id=1");}
  }else{
   assertEquals("read",phase);assertEquals("preserve-this-installation",context.getSharedPreferences("release_key_migration",0).getString("marker",""));
   try(DatabaseHelper helper=new DatabaseHelper(context);android.database.Cursor cursor=helper.getReadableDatabase().rawQuery("SELECT gold FROM user WHERE _id=1",null)){assertTrue(cursor.moveToFirst());assertEquals(1234,cursor.getInt(0));}
  }
 }
}
