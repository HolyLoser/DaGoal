package com.stipasay.dagoal;

import android.widget.LinearLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.runner.RunWith;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class QuestDialogInteractionTest {
 private SharedPreferences routing;
 private java.util.Map<String,?> originalRouting;
 private final String[] routingKeys={"isGuestUser","isOnboardingComplete","last_quest_generation_date","has_seen_first_time_permissions","has_seen_tutorial_prompt","batteryOptPromptShown","pref_notif_streak_popup"};
 @Before public void prepareRouting(){
  routing=InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("DaGoalPrefs",Context.MODE_PRIVATE);originalRouting=routing.getAll();
  String today=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.getDefault()).format(new java.util.Date());
  routing.edit().putBoolean("isGuestUser",true).putBoolean("isOnboardingComplete",true).putString("last_quest_generation_date",today)
   .putBoolean("has_seen_first_time_permissions",true).putBoolean("has_seen_tutorial_prompt",true).putBoolean("batteryOptPromptShown",true).putBoolean("pref_notif_streak_popup",false).commit();
 }
 @After public void restoreRouting(){
  if(routing==null)return;SharedPreferences.Editor editor=routing.edit();
  for(String key:routingKeys){Object value=originalRouting.get(key);if(value==null)editor.remove(key);else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);else editor.putString(key,(String)value);}editor.commit();
 }
 private void open(DashboardActivity activity){
  try{
   java.lang.reflect.Method method=DashboardActivity.class.getDeclaredMethod("showQuestDetailDialog",int.class,String.class,int.class,String.class,String.class,String.class,int.class,int.class,int.class,boolean.class,boolean.class,String.class,LinearLayout.class,LinearLayout.class);
   method.setAccessible(true);
   method.invoke(activity,Integer.MAX_VALUE,"Quest dialog close regression",1,"meal","GENERIC","Cook a delicious homemade meal",0,10,15,false,false,"",new LinearLayout(activity),new LinearLayout(activity));
  }catch(Exception e){throw new AssertionError(e);}
 }
 @Test public void closeDismissesAndDeleteExplainsFeeBeforeCancellation(){
  try(ActivityScenario<DashboardActivity> scenario=ActivityScenario.launch(DashboardActivity.class)){
   scenario.onActivity(this::open);
   onView(withId(R.id.tv_quest_detail_delete_cost)).inRoot(isDialog()).check(matches(withText("Deleting this quest costs 1 Gold.")));
   onView(withId(R.id.btn_quest_detail_close)).inRoot(isDialog()).perform(click());
   onView(withText("Quest dialog close regression")).check(doesNotExist());
   scenario.onActivity(this::open);
   onView(withId(R.id.btn_quest_detail_delete)).inRoot(isDialog()).perform(click());
   onView(withText("Delete this quest?")).inRoot(isDialog()).check(matches(isDisplayed()));
   onView(withText("Cancel")).inRoot(isDialog()).perform(click());
   onView(withId(R.id.btn_quest_detail_close)).inRoot(isDialog()).perform(click());
  }
 }
}
