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
                SoundEffectsHelper.playMenuClose(this);
                showMainMenu();
            } else {
                SoundEffectsHelper.playMenuClose(this);
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

        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedEmail = prefs.getString("user_email", "Guest Mode (Offline)");
        com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        addInfoRow(container, "Account", isLoggedIn ? savedEmail : "Guest Mode", null);
        if (firebaseUser != null) {
            addInfoRow(container, "Firebase Auth", "Authenticated (" + firebaseUser.getEmail() + ")", null);
        } else {
            addInfoRow(container, "Link Account to Cloud", "Sync guest profile to Email", () -> showLinkAccountDialog());
        }

        addInfoRow(container, "Username", username, () -> showEditUsernameDialog());
        addInfoRow(container, "Level", String.valueOf(level), null);

        boolean isOnline = OnlineShopManager.isNetworkAvailable(this);
        addInfoRow(container, "Cloud Sync Status", isOnline ? "🌐 Online (Connected)" : "📱 Offline Mode", null);
        addSwitchRow(container, "Show Network Badge on Shop", "Display online tag in Shop header",
                prefs.getBoolean("pref_show_network_badge", false),
                isChecked -> prefs.edit().putBoolean("pref_show_network_badge", isChecked).apply());

        if (isLoggedIn || firebaseUser != null) {
            addInfoRow(container, "Log Out", "Tap to Log Out", () -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Log Out?")
                        .setMessage("Are you sure you want to log out of your account?")
                        .setPositiveButton("Log Out", (d, w) -> {
                            try {
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                            } catch (Exception ignored) {}

                            prefs.edit()
                                    .putBoolean("isLoggedIn", false)
                                    .putBoolean("isGuestUser", false)
                                    .remove("user_email")
                                    .remove("user_uid")
                                    .apply();

                            AvatarCompositor.clearCache();

                            ToastUtils.showToast(this, "Logged out successfully.");
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        addInfoRow(container, "Reset Progress & Account", "Tap to reset", () -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Reset Progress & Delete Account?")
                    .setMessage("This will delete your account and erase all quests, achievements, gold, XP, and levels. This cannot be undone.")
                    .setPositiveButton("Reset & Delete", (d, w) -> {
                        String uid = prefs.getString("user_uid", null);
                        com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

                        Runnable performLocalReset = () -> {
                            try {
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                            } catch (Exception ignored) {}

                            SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
                            writableDb.execSQL("DROP TABLE IF EXISTS user");
                            writableDb.execSQL("DROP TABLE IF EXISTS daily_tasks");
                            writableDb.execSQL("DROP TABLE IF EXISTS achievements");
                            writableDb.execSQL("DROP TABLE IF EXISTS inventory");
                            writableDb.execSQL("DROP TABLE IF EXISTS inventory_consumables");
                            writableDb.execSQL("DROP TABLE IF EXISTS preferences");
                            writableDb.execSQL("DROP TABLE IF EXISTS task_templates");
                            writableDb.execSQL("DROP TABLE IF EXISTS blocked_apps");
                            writableDb.execSQL("DROP TABLE IF EXISTS streak_history");
                            dbHelper.onCreate(writableDb);

                            prefs.edit().clear().putBoolean("isFirstRun", true).apply();
                            AvatarCompositor.clearCache();

                            ToastUtils.showToast(this, "Account and progress reset.");
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        };

                        if (uid != null && !uid.isEmpty()) {
                            try {
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users").document(uid).delete();
                            } catch (Exception ignored) {}
                        }

                        if (fUser != null) {
                            fUser.delete().addOnCompleteListener(task -> performLocalReset.run());
                        } else {
                            performLocalReset.run();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        contentFrame.addView(container);
    }

    private void showEditUsernameDialog() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username FROM user WHERE _id = 1", null);
        String currentUsername = "Adventurer";
        if (cursor != null && cursor.moveToFirst()) {
            currentUsername = cursor.getString(0);
            cursor.close();
        }

        android.widget.EditText editUsername = new android.widget.EditText(this);
        editUsername.setText(currentUsername);
        editUsername.setSelection(currentUsername.length());
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        editUsername.setPadding(padding, padding, padding, padding);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit Username")
                .setView(editUsername)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newUsername = editUsername.getText().toString().trim();
                    if (newUsername.isEmpty()) {
                        ToastUtils.showToast(this, "Username cannot be empty.");
                        return;
                    }
                    if (newUsername.length() > 20) {
                        ToastUtils.showToast(this, "Username must be 20 characters or fewer.");
                        return;
                    }

                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put("username", newUsername);
                    values.put(DatabaseContract.UserEntry.COLUMN_NAME, newUsername);
                    SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
                    DatabaseHelper.ensureUserTableExists(writableDb);
                    writableDb.update("user", values, "_id = 1", null);

                    prefs.edit().putString("user_nickname", newUsername).apply();

                    String uid = prefs.getString("user_uid", null);
                    if (uid != null && !uid.isEmpty()) {
                        try {
                            com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                            java.util.Map<String, Object> map = new java.util.HashMap<>();
                            map.put("username", newUsername);
                            firestore.collection("users").document(uid).set(map, com.google.firebase.firestore.SetOptions.merge());
                        } catch (Exception ignored) {}
                    }

                    ToastUtils.showToast(this, "Username updated.");
                    showScreen("ACCOUNT");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLinkAccountDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        dialogLayout.setPadding(padding, padding, padding, padding);

        android.widget.EditText editEmail = new android.widget.EditText(this);
        editEmail.setHint("Email Address");
        editEmail.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        dialogLayout.addView(editEmail);

        android.widget.EditText editPassword = new android.widget.EditText(this);
        editPassword.setHint("Create Password");
        editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        dialogLayout.addView(editPassword);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Link Account to Email")
                .setMessage("Convert your offline guest profile into a cloud-synced account.")
                .setView(dialogLayout)
                .setPositiveButton("Link & Sync", (dialog, which) -> {
                    String email = editEmail.getText().toString().trim();
                    String password = editPassword.getText().toString().trim();

                    if (email.isEmpty() || password.isEmpty() || password.length() < 4) {
                        ToastUtils.showToast(this, "Please enter a valid email and 4+ char password.");
                        return;
                    }

                    com.google.firebase.auth.FirebaseAuth mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();
                    mAuth.createUserWithEmailAndPassword(email, password)
                            .addOnCompleteListener(this, task -> {
                                if (task.isSuccessful()) {
                                    com.google.firebase.auth.FirebaseUser fUser = mAuth.getCurrentUser();
                                    String uid = fUser != null ? fUser.getUid() : "";

                                    SQLiteDatabase db = dbHelper.getReadableDatabase();
                                    Cursor cursor = db.rawQuery("SELECT username, level, gold, xp, streak FROM user WHERE _id = 1", null);
                                    String username = "Adventurer";
                                    int level = 1, gold = 0, xp = 0, streak = 0;
                                    if (cursor != null && cursor.moveToFirst()) {
                                        username = cursor.getString(0);
                                        level = cursor.getInt(1);
                                        gold = cursor.getInt(2);
                                        xp = cursor.getInt(3);
                                        streak = cursor.getInt(4);
                                        cursor.close();
                                    }

                                    com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                    java.util.Map<String, Object> userMap = new java.util.HashMap<>();
                                    userMap.put("email", email);
                                    userMap.put("username", username);
                                    userMap.put("level", level);
                                    userMap.put("gold", gold);
                                    userMap.put("xp", xp);
                                    userMap.put("streak", streak);

                                    firestore.collection("users").document(uid).set(userMap);

                                    prefs.edit()
                                            .putBoolean("isLoggedIn", true)
                                            .putBoolean("isGuestUser", false)
                                            .putString("user_email", email)
                                            .putString("user_uid", uid)
                                            .putString("user_nickname", username)
                                            .apply();

                                    ToastUtils.showToast(this, "Account linked and synced to cloud!");
                                    showScreen("ACCOUNT");
                                } else {
                                    String errorMsg = task.getException() != null ? task.getException().getMessage() : "Linking failed";
                                    ToastUtils.showToast(this, "Notice: " + errorMsg);
                                }
                            });
                })
                .setNegativeButton("Cancel", null)
                .show();
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