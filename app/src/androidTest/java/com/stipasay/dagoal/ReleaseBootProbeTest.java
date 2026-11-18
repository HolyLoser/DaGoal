package com.stipasay.dagoal;

import android.content.Context;
import android.os.Build;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;

/** Explicit emulator-only setup for a real reboot test; never seeds a user's physical phone. */
public class ReleaseBootProbeTest {
 @Test public void prepareReminderRebootCheck(){
  Assume.assumeTrue("Explicit boot check only", "true".equals(InstrumentationRegistry.getArguments().getString("release_boot_probe")));
  Assume.assumeTrue(Build.HARDWARE.contains("ranchu")||Build.HARDWARE.contains("goldfish"));
  Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();context.getSharedPreferences("DaGoalPrefs",0).edit().putBoolean("isGuestUser",true).commit();DailyReminderScheduler.schedule(context);
 }
}
