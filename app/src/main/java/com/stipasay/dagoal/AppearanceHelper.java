package com.stipasay.dagoal;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

public class AppearanceHelper {

    public static void applyPreferredNightMode(Context context) {
        boolean followSystem = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE)
                .getBoolean("pref_follow_system_theme", false);

        if (followSystem) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}