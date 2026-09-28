package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoadingActivity extends AppCompatActivity {

    private static final long MIN_LOADING_TIME = 1500; // 1.5 seconds baseline

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        runStartupSequence();
    }

    private void runStartupSequence() {
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try (DatabaseHelper dbHelper = new DatabaseHelper(LoadingActivity.this)) {
                dbHelper.getReadableDatabase();
            }

            SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean("isFirstRun", true);

            Intent nextIntent = determineNextDestination(prefs, isFirstRun);

            long elapsedTime = System.currentTimeMillis() - startTime;
            long delay = Math.max(0, MIN_LOADING_TIME - elapsedTime);

            mainHandler.postDelayed(() -> {
                startActivity(nextIntent);
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
                finish();
                executor.shutdown();
            }, delay);
        });
    }

    private Intent determineNextDestination(SharedPreferences prefs, boolean isFirstRun) {
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        boolean isGuestUser = prefs.getBoolean("isGuestUser", false);
        boolean isOnboardingComplete = prefs.getBoolean("isOnboardingComplete", false);

        // ALWAYS route to MainActivity (Sign Up / Login page) unless the user is actively logged in AND onboarding is 100% complete
        if ((isLoggedIn || isGuestUser) && isOnboardingComplete) {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String lastQuestDate = prefs.getString("last_quest_generation_date", "");

            if (!todayDateStr.equals(lastQuestDate)) {
                return new Intent(this, DailyRevealActivity.class);
            } else {
                return new Intent(this, DashboardActivity.class);
            }
        }

        // Default: ALWAYS launch MainActivity (Login / Sign Up landing page)
        return new Intent(this, MainActivity.class);
    }
}
