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

        // Start initialization sequence
        runStartupSequence();
    }

    private void runStartupSequence() {
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            // --- Realistic Async Initialization Tasks ---
            
            // 1. Priming the Database (triggers onCreate/onUpgrade in background)
            try (DatabaseHelper dbHelper = new DatabaseHelper(LoadingActivity.this)) {
                dbHelper.getReadableDatabase();
            }

            // 2. Load and validate basic session/preference state
            SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            boolean isFirstRun = prefs.getBoolean("isFirstRun", true);

            // 3. Determine next destination
            Intent nextIntent = determineNextDestination(prefs, isFirstRun);

            // Calculate remaining time to satisfy the minimum baseline
            long elapsedTime = System.currentTimeMillis() - startTime;
            long delay = Math.max(0, MIN_LOADING_TIME - elapsedTime);

            // Return to main thread for navigation after the delay
            mainHandler.postDelayed(() -> {
                startActivity(nextIntent);
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
                finish();
                executor.shutdown();
            }, delay);
        });
    }

    private Intent determineNextDestination(SharedPreferences prefs, boolean isFirstRun) {
        if (isFirstRun) {
            return new Intent(this, MainActivity.class);
        } else {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String lastQuestDate = prefs.getString("last_quest_generation_date", "");

            if (!todayDateStr.equals(lastQuestDate)) {
                return new Intent(this, DailyRevealActivity.class);
            } else {
                return new Intent(this, DashboardActivity.class);
            }
        }
    }
}
