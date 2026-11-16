package com.stipasay.dagoal;

import android.content.*;
import android.os.SystemClock;
import android.security.NetworkSecurityPolicy;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReleaseRuntimeTest {
 private SharedPreferences prefs;
 @Before public void setup(){prefs=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("release_step_regression",0);prefs.edit().clear().commit();}
 @Test public void cleartextHttpIsDisabled(){assertFalse(NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("example.com"));}
 @Test public void counterSurvivesRebootAndResetsAtMidnight(){
  assertEquals(0,StepTrackingService.recordCounterSteps(prefs,"2026-11-17",0));assertEquals(100,StepTrackingService.recordCounterSteps(prefs,"2026-11-17",100));
  assertEquals(100,StepTrackingService.recordCounterSteps(prefs,"2026-11-17",0));assertEquals(104,StepTrackingService.recordCounterSteps(prefs,"2026-11-17",4));
  assertEquals(0,StepTrackingService.recordCounterSteps(prefs,"2026-11-18",8));assertEquals(2,StepTrackingService.recordCounterSteps(prefs,"2026-11-18",10));
 }
 @Test public void detectorDoesNotCarryYesterdaySteps(){assertEquals(7,StepTrackingService.recordDetectedSteps(prefs,"2026-11-17",7));assertEquals(1,StepTrackingService.recordDetectedSteps(prefs,"2026-11-18",1));}
 @Test public void migratedCounterPrefsRetainProgress(){prefs.edit().putString("baseline_date","2026-11-17").putInt("baseline_steps",200).putString("last_reported_date","2026-11-17").putInt("last_reported_steps",50).commit();assertEquals(51,StepTrackingService.recordCounterSteps(prefs,"2026-11-17",251));}
 @Test public void systemBackUsesQuestionNavigationWithoutFinishing(){
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();Intent intent=new Intent(context,ProfilingActivity.class).putExtra("EXTRA_START_LAST_QUESTION",true);
  try(ActivityScenario<ProfilingActivity> scenario=ActivityScenario.launch(intent)){
   scenario.onActivity(activity->{try{
    java.lang.reflect.Field index=ProfilingActivity.class.getDeclaredField("currentQuestionIndex");index.setAccessible(true);assertEquals(11,index.getInt(activity));
    activity.getOnBackPressedDispatcher().onBackPressed();assertEquals(10,index.getInt(activity));assertFalse(activity.isFinishing());
   }catch(Exception error){throw new AssertionError(error);}});
  }
 }
 @Test public void privacyPolicyLoadsOfflineWithScriptsDisabled(){
  try(ActivityScenario<PrivacyPolicyActivity> scenario=ActivityScenario.launch(PrivacyPolicyActivity.class)){
   java.util.concurrent.atomic.AtomicReference<String> title=new java.util.concurrent.atomic.AtomicReference<>();long deadline=SystemClock.elapsedRealtime()+12000;
   do{scenario.onActivity(activity->{WebView web=activity.findViewById(R.id.web_privacy_policy);assertFalse(web.getSettings().getJavaScriptEnabled());assertTrue(web.getSettings().getBlockNetworkLoads());title.set(web.getTitle());});if("DaGoal Privacy Policy".equals(title.get()))break;SystemClock.sleep(100);}while(SystemClock.elapsedRealtime()<deadline);
   assertEquals("DaGoal Privacy Policy",title.get());
   // The document title can arrive before WebView presents its first rendered frame.
   InstrumentationRegistry.getInstrumentation().waitForIdleSync();
   SystemClock.sleep(1500);
   android.graphics.Bitmap screenshot=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
   if(screenshot!=null){try(java.io.FileOutputStream stream=new java.io.FileOutputStream(new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"release-privacy-policy.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);}catch(java.io.IOException error){throw new AssertionError(error);}finally{screenshot.recycle();}}
  }
 }
}
