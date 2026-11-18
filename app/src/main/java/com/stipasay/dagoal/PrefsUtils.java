package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsUtils {
    private static final String PREF_NAME = "DaGoalPrefs";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isFirstRun(Context context) {
        return getPrefs(context).getBoolean("isFirstRun", true);
    }

    public static void setFirstRun(Context context, boolean isFirstRun) {
        getPrefs(context).edit().putBoolean("isFirstRun", isFirstRun).apply();
    }

    public static String getString(Context context, String key, String defaultValue) {
        return getPrefs(context).getString(key, defaultValue);
    }

    public static void putString(Context context, String key, String value) {
        getPrefs(context).edit().putString(key, value).apply();
    }
}
