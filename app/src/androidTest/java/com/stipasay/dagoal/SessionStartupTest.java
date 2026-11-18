package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Tests routing with dedicated preference files; never changes the user's session or Firebase account. */
@RunWith(AndroidJUnit4.class)
public class SessionStartupTest {
    private static final String TODAY = "2026-11-16";
    private SharedPreferences prefs;

    @Before public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = context.getSharedPreferences("session_startup_regression", Context.MODE_PRIVATE);
        assertTrue(prefs.edit().clear().commit());
    }

    private Class<?> destination(boolean firebase) {
        return LoadingActivity.resolveStartupDestination(prefs, firebase, TODAY);
    }

    @Test public void completedLoginReopensDashboardWithoutNetwork() {
        prefs.edit().putBoolean("isLoggedIn", true).putBoolean("isOnboardingComplete", true)
                .putString("last_quest_generation_date", TODAY).commit();
        assertEquals(DashboardActivity.class, destination(false));
    }

    @Test public void persistedFirebaseSessionRestoresStartupWithoutLocalLoginFlag() {
        prefs.edit().putBoolean("isOnboardingComplete", true)
                .putString("last_quest_generation_date", TODAY).commit();
        assertEquals(DashboardActivity.class, destination(true));
    }

    @Test public void legacyLoginWithRestoredAvatarDoesNotReturnToWelcome() {
        prefs.edit().putBoolean("isLoggedIn", true).putInt("pref_avatar_skin", 0)
                .putString("last_quest_generation_date", TODAY).commit();
        assertEquals(DashboardActivity.class, destination(false));
    }

    @Test public void newDayKeepsSessionAndShowsDailyReveal() {
        prefs.edit().putBoolean("isLoggedIn", true).putBoolean("isOnboardingComplete", true)
                .putString("last_quest_generation_date", "2026-11-15").commit();
        assertEquals(DailyRevealActivity.class, destination(false));
    }

    @Test public void unfinishedAccountResumesSavedSetupStep() {
        prefs.edit().putBoolean("isLoggedIn", true).putInt("pref_onboarding_step", 1).commit();
        assertEquals(AvatarCreationActivity.class, destination(false));
        prefs.edit().putInt("pref_avatar_skin", 0).putInt("pref_onboarding_step", 2).commit();
        assertEquals(ProfilingActivity.class, destination(false));
        prefs.edit().putInt("pref_onboarding_step", 3).commit();
        assertEquals(AppSelectionActivity.class, destination(false));
    }

    @Test public void guestSessionAlsoSurvivesReopening() {
        prefs.edit().putBoolean("isGuestUser", true).putBoolean("isOnboardingComplete", true)
                .putString("last_quest_generation_date", TODAY).commit();
        assertEquals(DashboardActivity.class, destination(false));
    }

    @Test public void loggedOutUserCannotResumeFromStaleAvatarData() {
        prefs.edit().putInt("pref_avatar_skin", 0).putBoolean("isOnboardingComplete", true).commit();
        assertEquals(MainActivity.class, destination(false));
    }

    @Test public void sessionSurvivesProcessRestart() {
        String phase = InstrumentationRegistry.getArguments().getString("session_phase");
        if (phase == null) return; // The two-phase process restart check is run separately.
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences persisted = context.getSharedPreferences("session_restart_regression", Context.MODE_PRIVATE);
        if ("write".equals(phase)) {
            assertTrue(persisted.edit().clear().putBoolean("isLoggedIn", true)
                    .putBoolean("isOnboardingComplete", true)
                    .putString("last_quest_generation_date", TODAY).commit());
        } else {
            assertEquals("read", phase);
            assertTrue("Saved session was lost after force-stop", persisted.getBoolean("isLoggedIn", false));
            assertEquals(DashboardActivity.class,
                    LoadingActivity.resolveStartupDestination(persisted, false, TODAY));
            assertTrue(persisted.edit().clear().commit());
        }
    }
}
