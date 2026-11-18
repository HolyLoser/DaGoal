package com.stipasay.dagoal;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/** Fault injection against the isolated emulator only. */
public class ReleaseRecoveryProbeTest {
    private Context context;

    @Before public void requireIsolatedEmulator() {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("release_recovery_probe")));
        Assume.assumeTrue(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish"));
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test public void unreadableDatabaseOffersRetryAndRecovers() throws Exception {
        File directory = context.getDatabasePath("dagoal.db").getParentFile();
        File saved = new File(directory.getParentFile(), "databases-recovery-probe");
        assertFalse(saved.exists());
        assertTrue(directory.renameTo(saved));
        try {
            assertTrue(directory.createNewFile());
            try (ActivityScenario<LoadingActivity> scenario = ActivityScenario.launch(LoadingActivity.class)) {
                AtomicBoolean errorShown = new AtomicBoolean();
                long deadline = SystemClock.elapsedRealtime() + 5000;
                while (!errorShown.get() && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity(activity -> errorShown.set(
                            activity.findViewById(R.id.loading_error).getVisibility() == View.VISIBLE));
                    SystemClock.sleep(100);
                }
                assertTrue("Startup did not show a recovery screen", errorShown.get());
                assertTrue(directory.delete());
                assertTrue(saved.renameTo(directory));
                android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
                SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
                boolean hasFirebaseSession = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null;
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(new java.util.Date());
                Class<?> destination = LoadingActivity.resolveStartupDestination(prefs, hasFirebaseSession, today);
                android.app.Instrumentation.ActivityMonitor navigation = instrumentation.addMonitor(
                        destination.getName(), new android.app.Instrumentation.ActivityResult(0, null), true);
                try {
                    scenario.onActivity(activity -> {
                        activity.findViewById(R.id.btn_loading_retry).performClick();
                        assertEquals(View.VISIBLE, activity.findViewById(R.id.loading_indicator).getVisibility());
                    });
                    deadline = SystemClock.elapsedRealtime() + 12000;
                    while (navigation.getHits() == 0 && SystemClock.elapsedRealtime() < deadline) {
                        SystemClock.sleep(100);
                    }
                    assertEquals("Retry should launch the normal startup destination", 1, navigation.getHits());
                } finally {
                    instrumentation.removeMonitor(navigation);
                }
            }
        } finally {
            if (saved.exists()) {
                if (directory.isFile()) assertTrue(directory.delete());
                assertTrue(saved.renameTo(directory));
            }
        }
    }

    @Test public void logoutCancelsExistingRemindersAndPreventsRescheduling() {
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        boolean guest = prefs.getBoolean("isGuestUser", false);
        boolean loggedIn = prefs.getBoolean("isLoggedIn", false);
        try {
            prefs.edit().putBoolean("isGuestUser", true).commit();
            DailyReminderScheduler.schedule(context);
            assertNotNull(alarm(801, DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS));
            assertNotNull(alarm(802, DailyNotificationReceiver.ACTION_EVENING_REMINDER));
            prefs.edit().putBoolean("isGuestUser", false).putBoolean("isLoggedIn", false).commit();
            DailyReminderScheduler.cancel(context);
            DailyReminderScheduler.schedule(context);
            assertNull(alarm(801, DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS));
            assertNull(alarm(802, DailyNotificationReceiver.ACTION_EVENING_REMINDER));
        } finally {
            prefs.edit().putBoolean("isGuestUser", guest).putBoolean("isLoggedIn", loggedIn).commit();
            DailyReminderScheduler.schedule(context);
        }
    }

    private PendingIntent alarm(int id, String action) {
        return PendingIntent.getBroadcast(context, id, new Intent(context, DailyNotificationReceiver.class).setAction(action),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE);
    }
}
