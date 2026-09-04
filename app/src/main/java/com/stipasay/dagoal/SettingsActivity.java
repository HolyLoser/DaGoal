package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private TextView tvScreenTitle;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private String currentScreen = "MAIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_settings);

        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);

        contentFrame = findViewById(R.id.settings_content_frame);
        tvScreenTitle = findViewById(R.id.tv_settings_screen_title);

        findViewById(R.id.btn_settings_back).setOnClickListener(v -> {
            if (!"MAIN".equals(currentScreen)) {
                showMainMenu();
            } else {
                finish();
            }
        });

        showMainMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!"MAIN".equals(currentScreen)) {
            showScreen(currentScreen);
        }
    }

    private void showMainMenu() {
        currentScreen = "MAIN";
        tvScreenTitle.setText("Settings");
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.view_settings_main, contentFrame, false);
        contentFrame.addView(view);

        view.findViewById(R.id.row_settings_account).setOnClickListener(v -> showScreen("ACCOUNT"));
        view.findViewById(R.id.row_settings_notifications).setOnClickListener(v -> showScreen("NOTIFICATIONS"));
        view.findViewById(R.id.row_settings_privacy).setOnClickListener(v -> showScreen("PRIVACY"));
        view.findViewById(R.id.row_settings_sound).setOnClickListener(v -> showScreen("SOUND"));
        view.findViewById(R.id.row_settings_appearance).setOnClickListener(v -> showScreen("APPEARANCE"));
        view.findViewById(R.id.row_settings_about).setOnClickListener(v -> showScreen("ABOUT"));
    }

    private void showScreen(String screen) {
        currentScreen = screen;
        contentFrame.removeAllViews();

        switch (screen) {
            case "ACCOUNT":
                tvScreenTitle.setText("Account");
                buildAccountScreen();
                break;
            case "NOTIFICATIONS":
                tvScreenTitle.setText("Notifications");
                buildNotificationsScreen();
                break;
            case "PRIVACY":
                tvScreenTitle.setText("Privacy & Permissions");
                buildPrivacyScreen();
                break;
            case "SOUND":
                tvScreenTitle.setText("Sound & Haptics");
                buildSoundScreen();
                break;
            case "APPEARANCE":
                tvScreenTitle.setText("Appearance");
                buildAppearanceScreen();
                break;
            case "ABOUT":
                tvScreenTitle.setText("About");
                buildAboutScreen();
                break;
        }
    }

    private View addSwitchRow(LinearLayout container, String title, String subtitle, boolean initialValue, SwitchCallback callback) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_switch_row, container, false);
        TextView tvTitle = row.findViewById(R.id.tv_switch_row_title);
        TextView tvSubtitle = row.findViewById(R.id.tv_switch_row_subtitle);
        Switch toggle = row.findViewById(R.id.switch_settings_toggle);

        tvTitle.setText(title);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        }
        toggle.setChecked(initialValue);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onChanged(isChecked));

        container.addView(row);
        return row;
    }

    private View addInfoRow(LinearLayout container, String title, String value, Runnable onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, container, false);
        TextView tvTitle = row.findViewById(R.id.tv_settings_row_title);
        TextView tvValue = row.findViewById(R.id.tv_settings_row_value);

        tvTitle.setText(title);
        tvValue.setText(value);

        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        } else {
            row.setClickable(false);
        }

        container.addView(row);
        return row;
    }

    private interface SwitchCallback {
        void onChanged(boolean isChecked);
    }

    private void buildAccountScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username, level FROM user WHERE _id = 1", null);
        String username = "Adventurer";
        int level = 1;
        if (cursor != null && cursor.moveToFirst()) {
            username = cursor.getString(0);
            level = cursor.getInt(1);
            cursor.close();
        }

        addInfoRow(container, "Username", username, null);
        addInfoRow(container, "Level", String.valueOf(level), null);

        addInfoRow(container, "Reset Progress", "Tap to reset", () -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Reset Progress?")
                    .setMessage("This will erase all quests, achievements, gold, XP, and levels. This cannot be undone.")
                    .setPositiveButton("Reset", (d, w) -> {
                        SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
                        writableDb.execSQL("DROP TABLE IF EXISTS user");
                        writableDb.execSQL("DROP TABLE IF EXISTS daily_tasks");
                        writableDb.execSQL("DROP TABLE IF EXISTS achievements");
                        writableDb.execSQL("DROP TABLE IF EXISTS inventory");
                        writableDb.execSQL("DROP TABLE IF EXISTS blocked_apps");
                        prefs.edit().clear().apply();
                        ToastUtils.showToast(this, "Progress reset. Restarting app.");
                        Intent intent = new Intent(this, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        contentFrame.addView(container);
    }

    private void buildNotificationsScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(container, "Step Tracking", "Android requires an ongoing notification while background step tracking is active. Turning this off disables step tracking entirely.",
                prefs.getBoolean("pref_notif_steps", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_steps", isChecked).apply());

        addSwitchRow(container, "Avoidance Monitoring", "Android requires an ongoing notification while app-avoidance monitoring is active. Turning this off disables avoidance enforcement entirely.",
                prefs.getBoolean("pref_notif_avoidance", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_avoidance", isChecked).apply());

        addSwitchRow(container, "Daily Streak Popup", "Show a popup when you open the app each day.",
                prefs.getBoolean("pref_notif_streak_popup", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_streak_popup", isChecked).apply());

        addSwitchRow(container, "Achievement & Level-Up Alerts", "Show a toast when you unlock an achievement or level up.",
                prefs.getBoolean("pref_notif_toasts", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_toasts", isChecked).apply());

        contentFrame.addView(container);
    }

    private void buildPrivacyScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        boolean hasUsageAccess = AppMonitorService.hasUsageAccess(this);
        addInfoRow(container, "Usage Access", hasUsageAccess ? "Granted" : "Not granted", () -> {
            startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        boolean hasOverlay = android.provider.Settings.canDrawOverlays(this);
        addInfoRow(container, "Display Over Other Apps", hasOverlay ? "Granted" : "Not granted", () -> {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
        });

        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean batteryExempt = powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        addInfoRow(container, "Battery Optimization", batteryExempt ? "Exempted" : "Not exempted", () -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        });

        boolean hasActivityRecognition = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        addInfoRow(container, "Activity Recognition (Steps)", hasActivityRecognition ? "Granted" : "Not granted", null);

        addInfoRow(container, "Manage Blocked Apps", "Edit list", () -> {
            Intent intent = new Intent(this, AppSelectionActivity.class);
            intent.putExtra("FROM_SETTINGS", true);
            startActivity(intent);
        });

        contentFrame.addView(container);
    }

    private void buildSoundScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(container, "Sound Effects", "",
                prefs.getBoolean("pref_sound_effects", true),
                isChecked -> prefs.edit().putBoolean("pref_sound_effects", isChecked).apply());

        addSwitchRow(container, "Vibration on Quest Complete", "",
                prefs.getBoolean("pref_haptics", true),
                isChecked -> prefs.edit().putBoolean("pref_haptics", isChecked).apply());

        contentFrame.addView(container);
    }

    private void buildAppearanceScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(container, "Follow System Theme", "When off, DaGoal always uses its light theme regardless of your device's dark mode setting.",
                prefs.getBoolean("pref_follow_system_theme", false),
                isChecked -> {
                    prefs.edit().putBoolean("pref_follow_system_theme", isChecked).apply();
                    AppearanceHelper.applyPreferredNightMode(this);
                });

        contentFrame.addView(container);
    }

    private void buildAboutScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        String versionName = "1.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        addInfoRow(container, "Version", versionName, null);
        addInfoRow(container, "Send Feedback", "Open email", () -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "DaGoal Feedback");
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        });
        addInfoRow(container, "Privacy Policy", "Not yet published", null);

        contentFrame.addView(container);
    }
}