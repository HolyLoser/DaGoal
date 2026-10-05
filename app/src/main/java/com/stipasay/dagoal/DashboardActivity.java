package com.stipasay.dagoal;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private static final float COLLAPSED_HEIGHT_DP = 300f;
    private static final float EXPANDED_HEIGHT_DP = 600f;

    private FrameLayout contentFrame;
    private LinearLayout navQuest, navWardrobe, navShop, navMe;
    private DatabaseHelper dbHelper;

    private ImageView imgGlobalAvatar;
    private TextView tvGlobalLevel, tvGlobalXp, tvGlobalGold;
    private View panelAvatarHost;
    private FrameLayout avatarHostContainer;
    private View rootLayout;
    private View bottomNavBar;
    private int contentFrameHeightPx;

    private ActivityResultLauncher<String[]> stepPermissionLauncher;
    private BroadcastReceiver taskProgressReceiver;
    private SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener;
    private com.google.firebase.firestore.ListenerRegistration firestoreUserListener;
    private String currentActiveTab = "QUEST";
    private String selectedWardrobeCategoryFilter = "clothes";
    private android.view.GestureDetector dashboardGestureDetector;
    private LinearLayout currentActiveQuestContainer;
    private LinearLayout currentCompletedQuestContainer;
    private Handler avoidanceTickHandler = new Handler(Looper.getMainLooper());
    private Runnable avoidanceTickRunnable;
    private Handler shopTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable shopTimerRunnable;

    private ShopItem selectedShopItem = null;
    private ShopItem selectedWardrobeItem = null;

    private View tabViewQuest = null;
    private View tabViewWardrobe = null;
    private View tabViewShop = null;
    private View tabViewMe = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_dashboard);

        dbHelper = new DatabaseHelper(this);
        contentFrame = findViewById(R.id.dashboard_content_frame);
        contentFrameHeightPx = contentFrame.getLayoutParams().height;

        navQuest = findViewById(R.id.nav_quest);
        navWardrobe = findViewById(R.id.nav_wardrobe);
        navShop = findViewById(R.id.nav_shop);
        navMe = findViewById(R.id.nav_me);

        FrameLayout avatarHostContainer = findViewById(R.id.avatar_host_container);
        if (avatarHostContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarHostContainer);
        }
        tvGlobalLevel = findViewById(R.id.tv_global_dashboard_lvl);
        tvGlobalXp = findViewById(R.id.tv_global_dashboard_xp);
        tvGlobalGold = findViewById(R.id.tv_global_dashboard_gold);
        panelAvatarHost = findViewById(R.id.panel_avatar_host);
        avatarHostContainer = findViewById(R.id.avatar_host_container);
        bottomNavBar = findViewById(R.id.bottom_nav_bar);

        rootLayout = findViewById(R.id.root_dashboard_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        scheduleDailyNotifications();
        checkFirstTimeUserTutorialPrompt();

        stepPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    boolean allGranted = true;
                    for (Boolean granted : result.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (allGranted) {
                        startStepTrackingIfNeeded();
                    }
                }
        );

        taskProgressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                runOnUiThread(() -> {
                    updateGlobalAvatarHeader();
                    if (currentActiveQuestContainer != null && currentCompletedQuestContainer != null) {
                        populateQuestLists(currentActiveQuestContainer, currentCompletedQuestContainer);
                    }
                });
            }
        };

        navQuest.setOnClickListener(v -> selectTab("QUEST"));
        navWardrobe.setOnClickListener(v -> selectTab("WARDROBE"));
        navShop.setOnClickListener(v -> selectTab("SHOP"));
        navMe.setOnClickListener(v -> selectTab("ME"));

        if (checkNewDayQuestRouting()) {
            return;
        }

        checkDailyStreakPopup();
        checkAndRequestStepPermissions();
        checkAndRequestAvoidancePermissions();
        startAvoidanceServiceIfNeeded();
        setupDashboardSwipeGestures();

        getCachedSampledBackground(R.drawable.bg_home);
        getCachedSampledBackground(R.drawable.bg_wardrobe);
        getCachedSampledBackground(R.drawable.bg_shop);

        selectTab("QUEST");
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter("com.stipasay.dagoal.TASK_PROGRESS_UPDATED");
        ContextCompat.registerReceiver(this, taskProgressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        if (prefChangeListener == null) {
            prefChangeListener = (sharedPreferences, key) -> {
                if ("pref_equipped_item_id".equals(key)) {
                    AvatarCompositor.clearCache();
                    updateGlobalAvatarHeader();
                    FrameLayout avatarMeHost = findViewById(R.id.avatar_me_host);
                    if (avatarMeHost != null) {
                        AvatarHelper.renderUserAvatarFaceOnly(this, avatarMeHost);
                    }
                }
            };
        }
        getSharedPreferences("DaGoalPrefs", MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(prefChangeListener);

        FrameLayout avatarHostContainer = findViewById(R.id.avatar_host_container);
        if (avatarHostContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarHostContainer);
        }
        FrameLayout avatarMeHost = findViewById(R.id.avatar_me_host);
        if (avatarMeHost != null) {
            AvatarHelper.renderUserAvatarFaceOnly(this, avatarMeHost);
        }

        if (currentActiveQuestContainer != null && currentCompletedQuestContainer != null) {
            TaskManager refreshManager = new TaskManager(this);
            refreshManager.checkAndCompleteAvoidanceQuests();
            populateQuestLists(currentActiveQuestContainer, currentCompletedQuestContainer);
        }

        startAvoidanceServiceIfNeeded();
        requestBatteryOptimizationExemption();
        startFirestoreUserProfileListener();
    }

    private void startFirestoreUserProfileListener() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        String uid = prefs.getString("user_uid", null);
        if (uid == null || uid.isEmpty()) {
            com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();
            if (fUser != null) {
                uid = fUser.getUid();
                prefs.edit().putString("user_uid", uid).apply();
            }
        }
        if (uid == null || uid.isEmpty()) return;

        try {
            com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            firestoreUserListener = firestore.collection("users").document(uid)
                    .addSnapshotListener((snapshot, e) -> {
                        if (e != null || snapshot == null || !snapshot.exists()) return;

                        Long cloudGold = snapshot.getLong("gold");
                        Long cloudLevel = snapshot.getLong("level");
                        Long cloudXp = snapshot.getLong("xp");
                        Long cloudStreak = snapshot.getLong("streak");
                        String cloudUsername = snapshot.getString("username");

                        if (cloudGold != null || cloudLevel != null || cloudXp != null) {
                            SQLiteDatabase db = dbHelper.getWritableDatabase();
                            ContentValues userValues = new ContentValues();
                            if (cloudGold != null) userValues.put(DatabaseContract.UserEntry.COLUMN_GOLD, cloudGold.intValue());
                            if (cloudLevel != null) userValues.put("level", cloudLevel.intValue());
                            if (cloudXp != null) userValues.put(DatabaseContract.UserEntry.COLUMN_XP, cloudXp.intValue());
                            if (cloudStreak != null) userValues.put(DatabaseContract.UserEntry.COLUMN_STREAK, cloudStreak.intValue());
                            if (cloudUsername != null && !cloudUsername.isEmpty()) userValues.put("username", cloudUsername);

                            db.update("user", userValues, "_id = 1", null);
                            updateGlobalAvatarHeader();

                            android.widget.GridView gridShop = findViewById(R.id.grid_shop_items);
                            if (gridShop != null && gridShop.getAdapter() != null) {
                                ((android.widget.BaseAdapter) gridShop.getAdapter()).notifyDataSetChanged();
                            }
                            android.widget.GridView gridWardrobe = findViewById(R.id.grid_wardrobe_items);
                            if (gridWardrobe != null && gridWardrobe.getAdapter() != null) {
                                ((android.widget.BaseAdapter) gridWardrobe.getAdapter()).notifyDataSetChanged();
                            }
                        }
                    });
        } catch (Exception ignored) {}
    }

    private void stopFirestoreUserProfileListener() {
        if (firestoreUserListener != null) {
            firestoreUserListener.remove();
            firestoreUserListener = null;
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).getBoolean("batteryOptPromptShown", false)) {
            return;
        }

        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
            getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).edit().putBoolean("batteryOptPromptShown", true).apply();
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void startShopTimerTicker(TextView tvTimer, Runnable onShuffle) {
        stopShopTimerTicker();
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long sixHours = 6 * 60 * 60 * 1000L;
        long targetTime = prefs.getLong("pref_next_shop_rotation_timestamp", 0);

        if (targetTime <= now || (targetTime - now) > sixHours) {
            targetTime = now + sixHours;
            prefs.edit().putLong("pref_next_shop_rotation_timestamp", targetTime).apply();
        }

        final long nextRotation = targetTime;

        shopTimerRunnable = new Runnable() {
            @Override
            public void run() {
                long remaining = nextRotation - System.currentTimeMillis();
                if (remaining <= 0) {
                    prefs.edit().putLong("pref_next_shop_rotation_timestamp", System.currentTimeMillis() + sixHours).apply();
                    if (onShuffle != null) onShuffle.run();
                } else {
                    long hours = (remaining / (1000 * 60 * 60)) % 24;
                    long minutes = (remaining / (1000 * 60)) % 60;
                    long seconds = (remaining / 1000) % 60;
                    if (tvTimer != null) {
                        tvTimer.setText(String.format(Locale.getDefault(), "⏱️ %02dh %02dm %02ds", hours, minutes, seconds));
                    }
                    shopTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        shopTimerHandler.post(shopTimerRunnable);
    }

    private void stopShopTimerTicker() {
        if (shopTimerRunnable != null) {
            shopTimerHandler.removeCallbacks(shopTimerRunnable);
            shopTimerRunnable = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(taskProgressReceiver);
        if (prefChangeListener != null) {
            getSharedPreferences("DaGoalPrefs", MODE_PRIVATE)
                    .unregisterOnSharedPreferenceChangeListener(prefChangeListener);
        }
        stopFirestoreUserProfileListener();
        stopAvoidanceTicker();
        stopShopTimerTicker();
        stopBoostTimerTicker();
    }
    private void checkAndRequestAvoidancePermissions() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String query = "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ?";
        Cursor cursor = db.rawQuery(query, new String[]{
                DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID, todayDateStr
        });

        boolean hasAvoidanceQuestToday = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                hasAvoidanceQuestToday = cursor.getInt(0) > 0;
            }
            cursor.close();
        }

        if (!hasAvoidanceQuestToday) {
            return;
        }

        boolean hasUsageAccess = AppMonitorService.hasUsageAccess(this);
        boolean hasOverlayPermission = android.provider.Settings.canDrawOverlays(this);

        if (hasUsageAccess && hasOverlayPermission) {
            return;
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme);
        builder.setTitle("Permissions needed");
        builder.setMessage("To enforce your avoidance quests, DaGoal needs Usage Access and Display Over Other Apps permissions.");

        if (!hasUsageAccess) {
            builder.setPositiveButton("Grant Usage Access", (dialog, which) -> {
                startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
            });
        } else {
            builder.setPositiveButton("Grant Overlay Permission", (dialog, which) -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }

        builder.setNegativeButton("Later", null);
        builder.show();
    }

    private void startAvoidanceServiceIfNeeded() {
        if (!getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).getBoolean("pref_notif_avoidance", true)) {
            return;
        }
        if (!AppMonitorService.hasUsageAccess(this) || !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }

        TaskManager taskManager = new TaskManager(this);
        if (!taskManager.getStartedAvoidanceQuests().isEmpty()) {
            AppMonitorService.start(this);
        }
    }
    private void checkAndRequestStepPermissions() {
        if (!getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).getBoolean("pref_notif_steps", true)) {
            return;
        }

        List<String> permissionsNeeded = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACTIVITY_RECOGNITION);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            stepPermissionLauncher.launch(permissionsNeeded.toArray(new String[0]));
            return;
        }

        startStepTrackingIfNeeded();
    }

    private void startStepTrackingIfNeeded() {
        if (!getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).getBoolean("pref_notif_steps", true)) {
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String query = "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " = 0";
        Cursor cursor = db.rawQuery(query, new String[]{
                DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS, todayDateStr
        });

        boolean hasIncompleteStepQuest = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                hasIncompleteStepQuest = cursor.getInt(0) > 0;
            }
            cursor.close();
        }

        if (hasIncompleteStepQuest) {
            StepTrackingService.start(this);
        }
    }

    private int getShopTierColor(String tier) {
        switch (tier) {
            case "UNCOMMON":
                return AchievementTierHelper.RANK_COLORS[1];
            case "RARE":
                return AchievementTierHelper.RANK_COLORS[2];
            case "EPIC":
                return AchievementTierHelper.RANK_COLORS[3];
            default:
                return AchievementTierHelper.RANK_COLORS[0];
        }
    }

    private int getCurrentUserLevel() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT level FROM user WHERE _id = 1", null);
        int level = 1;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                level = cursor.getInt(0);
            }
            cursor.close();
        }
        return level;
    }

    private static final boolean DEBUG_UNLOCK_CUSTOM_QUESTS_AT_LEVEL_1 = true;

    private void showAddGoalChooserDialog(LinearLayout activeContainer, LinearLayout completedContainer) {
        int level = getCurrentUserLevel();
        TaskManager taskManager = new TaskManager(this);
        int remaining = taskManager.getRemainingCustomQuests(level);

        if (!DEBUG_UNLOCK_CUSTOM_QUESTS_AT_LEVEL_1) {
            if (level < 10) {
                ToastUtils.showToast(this, "Custom quests unlock at Level 10");
                return;
            }
            if (remaining <= 0) {
                ToastUtils.showToast(this, "No custom quests remaining this week");
                return;
            }
        }

        showManualGoalDialog(level, activeContainer, completedContainer);
    }

    private void showCustomDurationPickerDialog(int[] durationHms, TextView tvDisplay) {
        SoundEffectsHelper.playMenuOpen(this);
        LinearLayout dialogView = new LinearLayout(this);
        dialogView.setOrientation(LinearLayout.HORIZONTAL);
        dialogView.setGravity(android.view.Gravity.CENTER);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        dialogView.setPadding(p, p, p, p);

        android.widget.NumberPicker pHours = new android.widget.NumberPicker(this);
        pHours.setMinValue(0);
        pHours.setMaxValue(23);
        pHours.setValue(durationHms[0]);

        android.widget.NumberPicker pMins = new android.widget.NumberPicker(this);
        pMins.setMinValue(0);
        pMins.setMaxValue(59);
        pMins.setValue(durationHms[1]);

        android.widget.NumberPicker pSecs = new android.widget.NumberPicker(this);
        pSecs.setMinValue(0);
        pSecs.setMaxValue(59);
        pSecs.setValue(durationHms[2]);

        TextView lH = new TextView(this); lH.setText(" h "); lH.setTextSize(16);
        TextView lM = new TextView(this); lM.setText(" m "); lM.setTextSize(16);
        TextView lS = new TextView(this); lS.setText(" s"); lS.setTextSize(16);

        dialogView.addView(pHours);
        dialogView.addView(lH);
        dialogView.addView(pMins);
        dialogView.addView(lM);
        dialogView.addView(pSecs);
        dialogView.addView(lS);

        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setTitle("Select Duration")
                .setView(dialogView)
                .setPositiveButton("OK", (d, w) -> {
                    durationHms[0] = pHours.getValue();
                    durationHms[1] = pMins.getValue();
                    durationHms[2] = pSecs.getValue();
                    if (tvDisplay != null) {
                        tvDisplay.setText(String.format(Locale.getDefault(), "%02dh %02dm %02ds", durationHms[0], durationHms[1], durationHms[2]));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showManualGoalDialog(int level, LinearLayout activeContainer, LinearLayout completedContainer) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_custom_quest, null);
        android.widget.EditText editTitle = dialogView.findViewById(R.id.edit_custom_quest_title);
        android.widget.EditText editTarget = dialogView.findViewById(R.id.edit_custom_quest_target);
        android.widget.EditText editUnitLabel = dialogView.findViewById(R.id.edit_custom_quest_unit_label);
        android.widget.Spinner spinnerUnitType = dialogView.findViewById(R.id.spinner_custom_quest_unit_type);
        LinearLayout rowDuration = dialogView.findViewById(R.id.row_custom_quest_duration);
        TextView tvDurationValue = dialogView.findViewById(R.id.tv_custom_quest_duration_value);

        LinearLayout rowRepeat = dialogView.findViewById(R.id.row_custom_quest_repeat);
        TextView tvRepeatValue = dialogView.findViewById(R.id.tv_custom_quest_repeat_value);
        LinearLayout rowRepeatEnds = dialogView.findViewById(R.id.row_custom_quest_repeat_ends);
        TextView tvRepeatEndsValue = dialogView.findViewById(R.id.tv_custom_quest_repeat_ends_value);
        TextView tvRewardPreview = dialogView.findViewById(R.id.tv_custom_quest_reward_preview);
        android.widget.SeekBar seekBarGold = dialogView.findViewById(R.id.seekbar_custom_quest_gold);
        Button btnCreate = dialogView.findViewById(R.id.btn_create_custom_quest);

        int[] durationHms = { 0, 30, 0 };
        if (rowDuration != null) {
            rowDuration.setOnClickListener(v -> showCustomDurationPickerDialog(durationHms, tvDurationValue));
        }

        android.widget.ArrayAdapter<String> unitAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{ "Tap to complete", "Steps", "Repetition", "Duration" });
        spinnerUnitType.setAdapter(unitAdapter);

        Button btnCustomBlockApps = dialogView.findViewById(R.id.btn_custom_block_apps);
        java.util.List<String> selectedBlockedPackages = new java.util.ArrayList<>();

        spinnerUnitType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selection = (String) parent.getItemAtPosition(position);

                editTarget.setVisibility(View.VISIBLE);
                editUnitLabel.setVisibility(View.GONE);
                if (rowDuration != null) rowDuration.setVisibility(View.GONE);
                if (btnCustomBlockApps != null) btnCustomBlockApps.setVisibility(View.GONE);

                switch (selection) {
                    case "Tap to complete":
                        editTarget.setVisibility(View.GONE);
                        break;
                    case "Steps":
                        editTarget.setHint("Number of steps");
                        break;
                    case "Repetition":
                        editTarget.setHint("Number of times");
                        editUnitLabel.setVisibility(View.VISIBLE);
                        break;
                    case "Duration":
                        editTarget.setVisibility(View.GONE);
                        if (rowDuration != null) rowDuration.setVisibility(View.VISIBLE);
                        if (btnCustomBlockApps != null) btnCustomBlockApps.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        if (btnCustomBlockApps != null) {
            btnCustomBlockApps.setOnClickListener(v -> showCustomAppBlockerDialog(selectedBlockedPackages));
        }

        int[] selectedRepeatInterval = { 0 };
        String[] selectedRepeatUnit = { "" };
        boolean[] selectedWeekdays = new boolean[7];
        String[] selectedRepeatEndType = { "" };
        String[] selectedRepeatEndValue = { "" };

        rowRepeat.setOnClickListener(v -> {
            View repeatDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_repeat_picker, null);
            android.widget.NumberPicker pickerInterval = repeatDialogView.findViewById(R.id.picker_repeat_interval);
            android.widget.NumberPicker pickerRepeatUnit = repeatDialogView.findViewById(R.id.picker_repeat_unit);
            LinearLayout containerWeekdays = repeatDialogView.findViewById(R.id.container_repeat_weekdays);
            Button btnRepeatCancel = repeatDialogView.findViewById(R.id.btn_repeat_cancel);
            Button btnRepeatOk = repeatDialogView.findViewById(R.id.btn_repeat_ok);

            pickerInterval.setMinValue(1);
            pickerInterval.setMaxValue(365);
            pickerInterval.setValue(selectedRepeatInterval[0] > 0 ? selectedRepeatInterval[0] : 1);

            String[] repeatUnitOptions = { "Day", "Week", "Month", "Year" };
            int initialUnitIndex = 1;
            for (int i = 0; i < repeatUnitOptions.length; i++) {
                if (repeatUnitOptions[i].equals(selectedRepeatUnit[0])) {
                    initialUnitIndex = i;
                }
            }
            pickerRepeatUnit.setMinValue(0);
            pickerRepeatUnit.setMaxValue(repeatUnitOptions.length - 1);
            pickerRepeatUnit.setDisplayedValues(repeatUnitOptions);
            pickerRepeatUnit.setValue(initialUnitIndex);

            TextView tvWeekdayLabel = repeatDialogView.findViewById(R.id.tv_repeat_weekday_label);
            pickerRepeatUnit.setOnValueChangedListener((picker, oldVal, newVal) -> {
                boolean isDay = "Day".equalsIgnoreCase(repeatUnitOptions[newVal]);
                if (tvWeekdayLabel != null) {
                    tvWeekdayLabel.setVisibility(isDay ? View.GONE : View.VISIBLE);
                }
                if (containerWeekdays != null) {
                    containerWeekdays.setVisibility(isDay ? View.GONE : View.VISIBLE);
                }
            });

            String[] weekdayLabels = { "S", "M", "T", "W", "T", "F", "S" };
            TextView[] weekdayViews = new TextView[7];
            for (int i = 0; i < 7; i++) {
                TextView chip = new TextView(this);
                chip.setText(weekdayLabels[i]);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setTextColor(Color.WHITE);
                chip.setBackgroundResource(R.drawable.bg_weekday_chip);
                chip.setSelected(selectedWeekdays[i]);
                LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, (int) (36 * getResources().getDisplayMetrics().density));
                chipParams.width = (int) (36 * getResources().getDisplayMetrics().density);
                chipParams.setMarginEnd((int) (6 * getResources().getDisplayMetrics().density));
                chip.setLayoutParams(chipParams);
                final int index = i;
                chip.setOnClickListener(cv -> {
                    selectedWeekdays[index] = !selectedWeekdays[index];
                    chip.setSelected(selectedWeekdays[index]);
                });
                weekdayViews[i] = chip;
                containerWeekdays.addView(chip);
            }

            androidx.appcompat.app.AlertDialog repeatDialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                    .setView(repeatDialogView)
                    .create();

            btnRepeatCancel.setOnClickListener(cv -> repeatDialog.dismiss());
            btnRepeatOk.setOnClickListener(cv -> {
                selectedRepeatInterval[0] = pickerInterval.getValue();
                selectedRepeatUnit[0] = repeatUnitOptions[pickerRepeatUnit.getValue()];
                tvRepeatValue.setText("Every " + selectedRepeatInterval[0] + " " + selectedRepeatUnit[0].toLowerCase(Locale.getDefault()) +
                        (selectedRepeatInterval[0] > 1 ? "s" : ""));
                repeatDialog.dismiss();
            });

            repeatDialog.show();
        });

        rowRepeatEnds.setOnClickListener(v -> {
            View endsDialogView = LayoutInflater.from(this).inflate(R.layout.dialog_repeat_ends, null);
            TextView tabDate = endsDialogView.findViewById(R.id.tab_end_by_date);
            TextView tabCount = endsDialogView.findViewById(R.id.tab_end_by_count);
            android.widget.CalendarView calendarView = endsDialogView.findViewById(R.id.calendar_repeat_end_date);
            android.widget.NumberPicker countPicker = endsDialogView.findViewById(R.id.picker_repeat_end_count);
            Button btnEndsCancel = endsDialogView.findViewById(R.id.btn_repeat_ends_cancel);
            Button btnEndsOk = endsDialogView.findViewById(R.id.btn_repeat_ends_ok);

            countPicker.setMinValue(1);
            countPicker.setMaxValue(365);
            countPicker.setValue(2);

            String[] activeTab = { "DATE" };
            if ("COUNT".equals(selectedRepeatEndType[0])) {
                activeTab[0] = "COUNT";
            }

            Runnable refreshTabs = () -> {
                boolean isDate = "DATE".equals(activeTab[0]);
                tabDate.setTextColor(isDate ? Color.parseColor("#556B43") : Color.parseColor("#A0AEC0"));
                tabCount.setTextColor(isDate ? Color.parseColor("#A0AEC0") : Color.parseColor("#556B43"));
                calendarView.setVisibility(isDate ? View.VISIBLE : View.GONE);
                countPicker.setVisibility(isDate ? View.GONE : View.VISIBLE);
            };
            refreshTabs.run();

            tabDate.setOnClickListener(tv -> {
                activeTab[0] = "DATE";
                refreshTabs.run();
            });

            tabCount.setOnClickListener(tv -> {
                activeTab[0] = "COUNT";
                refreshTabs.run();
            });

            if ("COUNT".equals(selectedRepeatEndType[0]) && !selectedRepeatEndValue[0].isEmpty()) {
                try {
                    countPicker.setValue(Integer.parseInt(selectedRepeatEndValue[0]));
                } catch (NumberFormatException ignored) {
                }
            }

            androidx.appcompat.app.AlertDialog endsDialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                    .setView(endsDialogView)
                    .create();

            Button btnEndsClear = endsDialogView.findViewById(R.id.btn_repeat_ends_clear);
            if (btnEndsClear != null) {
                btnEndsClear.setOnClickListener(cv -> {
                    selectedRepeatEndType[0] = "NEVER";
                    selectedRepeatEndValue[0] = "";
                    tvRepeatEndsValue.setText("Does not end");
                    endsDialog.dismiss();
                });
            }

            btnEndsCancel.setOnClickListener(cv -> endsDialog.dismiss());

            btnEndsOk.setOnClickListener(cv -> {
                if ("DATE".equals(activeTab[0])) {
                    long selectedMillis = calendarView.getDate();
                    java.util.Calendar cal = java.util.Calendar.getInstance();
                    cal.setTimeInMillis(selectedMillis);
                    int year = cal.get(java.util.Calendar.YEAR);
                    int month = cal.get(java.util.Calendar.MONTH) + 1;
                    int day = cal.get(java.util.Calendar.DAY_OF_MONTH);

                    selectedRepeatEndType[0] = "DATE";
                    selectedRepeatEndValue[0] = year + "-" + month + "-" + day;
                    tvRepeatEndsValue.setText("Ends " + month + "/" + day + "/" + year);
                } else {
                    selectedRepeatEndType[0] = "COUNT";
                    selectedRepeatEndValue[0] = String.valueOf(countPicker.getValue());
                    tvRepeatEndsValue.setText("Ends after " + countPicker.getValue() + " times");
                }
                endsDialog.dismiss();
            });

            endsDialog.show();
        });

        int goldMax = TaskManager.getCustomQuestGoldMax(level);
        int goldMin = TaskManager.CUSTOM_QUEST_GOLD_MIN;
        seekBarGold.setMax(goldMax - goldMin);

        updateRewardPreview(tvRewardPreview, goldMin, level);

        seekBarGold.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                int goldPicked = goldMin + progress;
                updateRewardPreview(tvRewardPreview, goldPicked, level);
            }
            @Override
            public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .create();

        btnCreate.setOnClickListener(v -> {
            String title = editTitle.getText().toString().trim();
            String unitTypeSelection = (String) spinnerUnitType.getSelectedItem();

            if (title.isEmpty()) {
                ToastUtils.showToast(this, "Please fill in a title.");
                return;
            }

            int target = 1;
            String unitLabel = "";
            String unitType = "";

            if ("Tap to complete".equals(unitTypeSelection)) {
                unitType = DatabaseContract.DailyTaskEntry.UNIT_TYPE_GENERIC;
                unitLabel = "";
            } else if ("Duration".equals(unitTypeSelection)) {
                target = durationHms[0] * 60 + durationHms[1] + (durationHms[2] > 0 ? 1 : 0);
                if (target <= 0) target = 1;
                unitLabel = "minutes";
                unitType = DatabaseContract.DailyTaskEntry.UNIT_TYPE_DURATION;
            } else {
                String targetStr = editTarget.getText().toString().trim();
                if (targetStr.isEmpty()) {
                    ToastUtils.showToast(this, "Please fill in a target.");
                    return;
                }
                try {
                    target = Integer.parseInt(targetStr);
                    if (target <= 0) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException e) {
                    ToastUtils.showToast(this, "Target must be a positive number.");
                    return;
                }

                if ("Steps".equals(unitTypeSelection)) {
                    unitLabel = "steps";
                    unitType = DatabaseContract.DailyTaskEntry.UNIT_TYPE_STEPS;
                } else if ("Repetition".equals(unitTypeSelection)) {
                    String customLabel = editUnitLabel.getText().toString().trim();
                    if (customLabel.isEmpty()) {
                        ToastUtils.showToast(this, "Please describe what you're counting.");
                        return;
                    }
                    unitLabel = customLabel;
                    unitType = DatabaseContract.DailyTaskEntry.UNIT_TYPE_REPETITION;
                }
            }

            StringBuilder weekdaysBuilder = new StringBuilder();
            for (int i = 0; i < selectedWeekdays.length; i++) {
                if (selectedWeekdays[i]) {
                    if (weekdaysBuilder.length() > 0) {
                        weekdaysBuilder.append(",");
                    }
                    weekdaysBuilder.append(i);
                }
            }

            android.widget.EditText editDescription = dialogView.findViewById(R.id.edit_custom_quest_description);
            String description = editDescription != null ? editDescription.getText().toString().trim() : "";

            int goldPicked = goldMin + seekBarGold.getProgress();

            StringBuilder blockedCsvBuilder = new StringBuilder();
            for (String pkg : selectedBlockedPackages) {
                if (blockedCsvBuilder.length() > 0) blockedCsvBuilder.append(",");
                blockedCsvBuilder.append(pkg);
            }

            TaskManager taskManager = new TaskManager(this);
            boolean success = taskManager.createCustomQuest(
                    title, description, target, unitLabel, goldPicked, level,
                    unitType, selectedRepeatInterval[0], selectedRepeatUnit[0],
                    weekdaysBuilder.toString(), selectedRepeatEndType[0], selectedRepeatEndValue[0],
                    blockedCsvBuilder.toString()
            );

            if (success) {
                ToastUtils.showToast(this, "Custom quest created!");
                populateQuestLists(activeContainer, completedContainer);
                dialog.dismiss();
            } else {
                ToastUtils.showToast(this, "No custom quests remaining this week.");
            }
        });

        dialog.show();
    }

    private void updateRewardPreview(TextView tvRewardPreview, int goldPicked, int level) {
        int xp = TaskManager.computeCustomQuestXp(goldPicked, level);
        tvRewardPreview.setText(getString(R.string.reward_preview, goldPicked, xp));
    }

    private boolean checkNewDayQuestRouting() {
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        String lastQuestDate = prefs.getString("last_quest_generation_date", "");

        if (!todayDateStr.equals(lastQuestDate)) {
            Intent intent = new Intent(this, DailyRevealActivity.class);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    private void checkDailyStreakPopup() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        if (!prefs.getBoolean("pref_notif_streak_popup", true)) {
            return;
        }

        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastPopupDate = prefs.getString("last_streak_popup_date", "");

        if (!todayDateStr.equals(lastPopupDate)) {
            prefs.edit().putString("last_streak_popup_date", todayDateStr).commit();
            Intent intent = new Intent(this, StreakActivity.class);
            startActivity(intent);
        }
    }

    private void animateContentFrameHeight(int targetHeightPx) {
        ConstraintLayout.LayoutParams contentParams = (ConstraintLayout.LayoutParams) contentFrame.getLayoutParams();
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(contentFrameHeightPx, targetHeightPx);
        animator.setDuration(220);
        animator.addUpdateListener(animation -> {
            int animatedHeight = (int) animation.getAnimatedValue();
            contentParams.height = animatedHeight;
            contentFrame.setLayoutParams(contentParams);
        });
        animator.start();
        contentFrameHeightPx = targetHeightPx;
    }

    private void setupDragHandle(View questView) {
        View dragHandle = questView.findViewById(R.id.drag_handle_touch_area);
        if (dragHandle == null) {
            return;
        }

        dragHandle.setOnTouchListener(new View.OnTouchListener() {
            float startY;
            int startHeightPx;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                ConstraintLayout.LayoutParams contentParams = (ConstraintLayout.LayoutParams) contentFrame.getLayoutParams();
                float density = getResources().getDisplayMetrics().density;
                int collapsedPx = (int) (COLLAPSED_HEIGHT_DP * density);
                int expandedPx = (int) (EXPANDED_HEIGHT_DP * density);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startY = event.getRawY();
                        startHeightPx = contentFrameHeightPx;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaY = startY - event.getRawY();
                        int newHeight = (int) (startHeightPx + deltaY);

                        if (newHeight < collapsedPx) {
                            newHeight = collapsedPx;
                        }
                        if (newHeight > expandedPx) {
                            newHeight = expandedPx;
                        }

                        contentFrameHeightPx = newHeight;
                        contentParams.height = newHeight;
                        contentFrame.setLayoutParams(contentParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        int midpointPx = (collapsedPx + expandedPx) / 2;
                        int snapTargetPx = (contentFrameHeightPx >= midpointPx) ? expandedPx : collapsedPx;
                        animateContentFrameHeight(snapTargetPx);
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void startAvoidanceTicker() {
        stopAvoidanceTicker();
        avoidanceTickRunnable = new Runnable() {
            @Override
            public void run() {
                TaskManager taskManager = new TaskManager(DashboardActivity.this);
                taskManager.checkAndCompleteAvoidanceQuests();
                if (currentActiveQuestContainer != null && currentCompletedQuestContainer != null) {
                    populateQuestLists(currentActiveQuestContainer, currentCompletedQuestContainer);
                }
                avoidanceTickHandler.postDelayed(this, 60000);
            }
        };
        avoidanceTickHandler.postDelayed(avoidanceTickRunnable, 60000);
    }

    private void stopAvoidanceTicker() {
        if (avoidanceTickRunnable != null) {
            avoidanceTickHandler.removeCallbacks(avoidanceTickRunnable);
            avoidanceTickRunnable = null;
        }
    }


    private void setupDashboardSwipeGestures() {
        dashboardGestureDetector = new android.view.GestureDetector(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_THRESHOLD = 100;
            private static final int SWIPE_VELOCITY_THRESHOLD = 100;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX < 0) {
                        if ("QUEST".equalsIgnoreCase(currentActiveTab)) {
                            selectTab("WARDROBE");
                            return true;
                        } else if ("WARDROBE".equalsIgnoreCase(currentActiveTab)) {
                            selectTab("SHOP");
                            return true;
                        }
                    } else {
                        if ("SHOP".equalsIgnoreCase(currentActiveTab)) {
                            selectTab("WARDROBE");
                            return true;
                        } else if ("WARDROBE".equalsIgnoreCase(currentActiveTab)) {
                            selectTab("QUEST");
                            return true;
                        }
                    }
                }
                return false;
            }
        });
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (dashboardGestureDetector != null) {
            dashboardGestureDetector.onTouchEvent(ev);
        }
        return super.dispatchTouchEvent(ev);
    }

    private int getTabIndex(String tabName) {
        if ("WARDROBE".equalsIgnoreCase(tabName)) return 1;
        if ("SHOP".equalsIgnoreCase(tabName)) return 2;
        if ("ME".equalsIgnoreCase(tabName)) return 3;
        return 0; // QUEST
    }

    private final java.util.Map<Integer, Bitmap> sBgBitmapCache = new java.util.HashMap<>();

    private Bitmap getCachedSampledBackground(int resId) {
        if (sBgBitmapCache.containsKey(resId)) {
            Bitmap b = sBgBitmapCache.get(resId);
            if (b != null && !b.isRecycled()) return b;
        }
        int displayWidth = getResources().getDisplayMetrics().widthPixels;
        int displayHeight = getResources().getDisplayMetrics().heightPixels;
        Bitmap sampled = decodeSampledBitmapFromResource(getResources(), resId, displayWidth / 2, displayHeight / 2);
        if (sampled != null) {
            sBgBitmapCache.put(resId, sampled);
        }
        return sampled;
    }

    public static Bitmap decodeSampledBitmapFromResource(android.content.res.Resources res, int resId, int reqWidth, int reqHeight) {
        final android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeResource(res, resId, options);

        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;
        return android.graphics.BitmapFactory.decodeResource(res, resId, options);
    }

    public static int calculateInSampleSize(android.graphics.BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private void showCustomAppBlockerDialog(java.util.List<String> selectedPackages) {
        SoundEffectsHelper.playMenuOpen(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_custom_app_blocker, null);
        LinearLayout container = dialogView.findViewById(R.id.container_custom_block_apps);
        Button btnClear = dialogView.findViewById(R.id.btn_block_apps_clear);
        Button btnDone = dialogView.findViewById(R.id.btn_block_apps_done);

        java.util.List<CheckBox> checkBoxes = new java.util.ArrayList<>();
        android.content.pm.PackageManager pm = getPackageManager();
        java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA);

        if (container != null) {
            container.removeAllViews();
            for (android.content.pm.ApplicationInfo app : apps) {
                if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
                String appName = pm.getApplicationLabel(app).toString();
                String pkgName = app.packageName;

                CheckBox cb = new CheckBox(this);
                cb.setText(appName);
                cb.setTextColor(Color.parseColor("#2D3748"));
                float density = getResources().getDisplayMetrics().density;
                int padPx = (int) (8 * density);
                cb.setPadding(padPx, padPx, padPx, padPx);
                cb.setTag(pkgName);
                if (selectedPackages.contains(pkgName)) {
                    cb.setChecked(true);
                }
                checkBoxes.add(cb);
                container.addView(cb);
            }
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setView(dialogView)
                .create();

        if (btnClear != null) {
            btnClear.setOnClickListener(v -> {
                for (CheckBox cb : checkBoxes) cb.setChecked(false);
            });
        }

        if (btnDone != null) {
            btnDone.setOnClickListener(v -> {
                selectedPackages.clear();
                for (CheckBox cb : checkBoxes) {
                    if (cb.isChecked() && cb.getTag() != null) {
                        selectedPackages.add((String) cb.getTag());
                    }
                }
                ToastUtils.showToast(this, selectedPackages.size() + " app(s) selected for blocking.");
                dialog.dismiss();
            });
        }
        dialog.show();
    }

    private void updateDashboardBackground(String tabName, boolean movingRight) {
        if ("ME".equalsIgnoreCase(tabName)) return;

        ImageView ivActive = findViewById(R.id.iv_dashboard_bg_active);
        if (ivActive == null) return;

        int targetRes = R.drawable.bg_home;
        if ("WARDROBE".equalsIgnoreCase(tabName)) {
            targetRes = R.drawable.bg_wardrobe;
        } else if ("SHOP".equalsIgnoreCase(tabName)) {
            targetRes = R.drawable.bg_shop;
        }

        Bitmap bgBitmap = getCachedSampledBackground(targetRes);
        if (bgBitmap != null) {
            ivActive.setImageBitmap(bgBitmap);
        } else {
            ivActive.setImageResource(targetRes);
        }
    }

    private void selectTab(String tabName) {
        int oldIndex = getTabIndex(currentActiveTab);
        int newIndex = getTabIndex(tabName);
        boolean movingRight = newIndex >= oldIndex;

        currentActiveTab = tabName;
        updateDashboardBackground(tabName, movingRight);
        SoundEffectsHelper.playHighlight(this);
        resetTabColors();

        LayoutInflater inflater = LayoutInflater.from(this);
        updateGlobalAvatarHeader();

        currentActiveQuestContainer = null;
        currentCompletedQuestContainer = null;
        stopAvoidanceTicker();

        ConstraintLayout.LayoutParams contentParams = (ConstraintLayout.LayoutParams) contentFrame.getLayoutParams();
        float density = getResources().getDisplayMetrics().density;

        if (tabName.equals("ME")) {
            if (panelAvatarHost != null) panelAvatarHost.setVisibility(View.GONE);
            contentParams.height = ConstraintLayout.LayoutParams.MATCH_PARENT;
        } else {
            if (panelAvatarHost != null) panelAvatarHost.setVisibility(View.VISIBLE);
            contentFrameHeightPx = (int) (COLLAPSED_HEIGHT_DP * density);
            contentParams.height = contentFrameHeightPx;
        }
        contentFrame.setLayoutParams(contentParams);

        contentFrame.setTranslationX(0);
        contentFrame.setAlpha(1.0f);

        View cardChestBar = findViewById(R.id.card_chest_bar);
        View panelSidebar = findViewById(R.id.panel_wardrobe_sidebar);

        if ("QUEST".equals(tabName)) {
            if (cardChestBar != null) cardChestBar.setVisibility(View.VISIBLE);
            if (panelSidebar != null) panelSidebar.setVisibility(View.GONE);
        } else if ("WARDROBE".equals(tabName)) {
            if (cardChestBar != null) cardChestBar.setVisibility(View.GONE);
            if (panelSidebar != null) panelSidebar.setVisibility(View.VISIBLE);
        } else {
            if (cardChestBar != null) cardChestBar.setVisibility(View.GONE);
            if (panelSidebar != null) panelSidebar.setVisibility(View.GONE);
        }

        if (tabViewQuest != null) tabViewQuest.setVisibility("QUEST".equals(tabName) ? View.VISIBLE : View.GONE);
        if (tabViewWardrobe != null) tabViewWardrobe.setVisibility("WARDROBE".equals(tabName) ? View.VISIBLE : View.GONE);
        if (tabViewShop != null) tabViewShop.setVisibility("SHOP".equals(tabName) ? View.VISIBLE : View.GONE);
        if (tabViewMe != null) tabViewMe.setVisibility("ME".equals(tabName) ? View.VISIBLE : View.GONE);

        switch (tabName) {
            case "QUEST":
                highlightTab(navQuest);
                if (tabViewQuest == null) {
                    tabViewQuest = inflater.inflate(R.layout.view_dashboard_quest, contentFrame, false);
                    contentFrame.addView(tabViewQuest);
                    setupDragHandle(tabViewQuest);

                    Button btnAddGoal = tabViewQuest.findViewById(R.id.btn_dashboard_add_goal);
                    if (btnAddGoal != null) {
                        btnAddGoal.setOnClickListener(v -> {
                            LinearLayout activeC = tabViewQuest.findViewById(R.id.container_active_dashboard_quests);
                            LinearLayout completedC = tabViewQuest.findViewById(R.id.container_completed_dashboard_quests);
                            showAddGoalChooserDialog(activeC, completedC);
                        });
                    }
                }
                tabViewQuest.setVisibility(View.VISIBLE);

                LinearLayout activeContainer = tabViewQuest.findViewById(R.id.container_active_dashboard_quests);
                LinearLayout completedContainer = tabViewQuest.findViewById(R.id.container_completed_dashboard_quests);
                currentActiveQuestContainer = activeContainer;
                currentCompletedQuestContainer = completedContainer;

                TaskManager tickManager = new TaskManager(this);
                tickManager.checkAndCompleteAvoidanceQuests();

                populateQuestLists(activeContainer, completedContainer);
                updateChestBarUI();
                checkAndShowChestTierPrompt();
                startAvoidanceTicker();
                break;

            case "WARDROBE":
                highlightTab(navWardrobe);
                if (tabViewWardrobe == null) {
                    tabViewWardrobe = inflater.inflate(R.layout.view_dashboard_wardrobe, contentFrame, false);
                    contentFrame.addView(tabViewWardrobe);
                    setupDragHandle(tabViewWardrobe);
                }
                tabViewWardrobe.setVisibility(View.VISIBLE);

                Button btnEquipAction = tabViewWardrobe.findViewById(R.id.btn_wardrobe_action);
                android.widget.GridView gridWardrobeItems = tabViewWardrobe.findViewById(R.id.grid_wardrobe_items);

                btnEquipAction.setText("Equip Item");
                btnEquipAction.setVisibility(View.GONE);

                TaskManager wardrobeManager = new TaskManager(this);

                View btnFilterHat = findViewById(R.id.btn_wardrobe_filter_hat);
                View btnFilterGlasses = findViewById(R.id.btn_wardrobe_filter_glasses);
                View btnFilterClothes = findViewById(R.id.btn_wardrobe_filter_clothes);
                View bgFilterHat = findViewById(R.id.view_bg_filter_hat);
                View bgFilterGlasses = findViewById(R.id.view_bg_filter_glasses);
                View bgFilterClothes = findViewById(R.id.view_bg_filter_clothes);

                ImageView ivSidebarHat = findViewById(R.id.iv_sidebar_icon_hat);
                ImageView ivSidebarGlasses = findViewById(R.id.iv_sidebar_icon_glasses);
                ImageView ivSidebarClothes = findViewById(R.id.iv_sidebar_icon_clothes);

                Runnable updateSidebarItemPreviews = new Runnable() {
                    @Override
                    public void run() {
                        ShopItem eqHat = TaskManager.getEquippedItemForSlot(DashboardActivity.this, "hat");
                        if (eqHat != null && ivSidebarHat != null) {
                            bindShopItemImagePreview(eqHat, ivSidebarHat, null);
                        } else if (ivSidebarHat != null) {
                            ivSidebarHat.setImageResource(R.drawable.ic_unequipped_x);
                            ivSidebarHat.setVisibility(View.VISIBLE);
                        }

                        ShopItem eqGlasses = TaskManager.getEquippedItemForSlot(DashboardActivity.this, "glasses");
                        if (eqGlasses != null && ivSidebarGlasses != null) {
                            bindShopItemImagePreview(eqGlasses, ivSidebarGlasses, null);
                        } else if (ivSidebarGlasses != null) {
                            ivSidebarGlasses.setImageResource(R.drawable.ic_unequipped_x);
                            ivSidebarGlasses.setVisibility(View.VISIBLE);
                        }

                        ShopItem eqClothes = TaskManager.getEquippedItemForSlot(DashboardActivity.this, "clothes");
                        if (eqClothes != null && !"tank_top".equalsIgnoreCase(eqClothes.getResName()) && ivSidebarClothes != null) {
                            bindShopItemImagePreview(eqClothes, ivSidebarClothes, null);
                        } else if (ivSidebarClothes != null) {
                            ivSidebarClothes.setImageResource(R.drawable.ic_unequipped_x);
                            ivSidebarClothes.setVisibility(View.VISIBLE);
                        }
                    }
                };

                java.util.List<ShopItem> allOwned = wardrobeManager.getOwnedItems();
                java.util.List<ShopItem> filteredOwned = new java.util.ArrayList<>();

                Runnable applyWardrobeFilter = new Runnable() {
                    @Override
                    public void run() {
                        filteredOwned.clear();
                        for (ShopItem item : allOwned) {
                            String itemSlot = TaskManager.getItemSlotType(item);
                            if (selectedWardrobeCategoryFilter.equalsIgnoreCase(itemSlot)) {
                                filteredOwned.add(item);
                            }
                        }
                        java.util.Collections.sort(filteredOwned, (a, b) -> {
                            boolean eqA = isItemEquippedInSlot(a);
                            boolean eqB = isItemEquippedInSlot(b);
                            if (eqA && !eqB) return -1;
                            if (!eqA && eqB) return 1;
                            return 0;
                        });

                        int greenColor = Color.parseColor("#546B41");
                        int grayColor = Color.parseColor("#CBD5E1");
                        if (bgFilterHat != null) androidx.core.view.ViewCompat.setBackgroundTintList(bgFilterHat, android.content.res.ColorStateList.valueOf("hat".equalsIgnoreCase(selectedWardrobeCategoryFilter) ? greenColor : grayColor));
                        if (bgFilterGlasses != null) androidx.core.view.ViewCompat.setBackgroundTintList(bgFilterGlasses, android.content.res.ColorStateList.valueOf("glasses".equalsIgnoreCase(selectedWardrobeCategoryFilter) ? greenColor : grayColor));
                        if (bgFilterClothes != null) androidx.core.view.ViewCompat.setBackgroundTintList(bgFilterClothes, android.content.res.ColorStateList.valueOf("clothes".equalsIgnoreCase(selectedWardrobeCategoryFilter) ? greenColor : grayColor));

                        if (gridWardrobeItems.getAdapter() != null) {
                            ((android.widget.BaseAdapter) gridWardrobeItems.getAdapter()).notifyDataSetChanged();
                        }
                    }
                };

                if (btnFilterHat != null) btnFilterHat.setOnClickListener(v -> { selectedWardrobeCategoryFilter = "hat"; applyWardrobeFilter.run(); });
                if (btnFilterGlasses != null) btnFilterGlasses.setOnClickListener(v -> { selectedWardrobeCategoryFilter = "glasses"; applyWardrobeFilter.run(); });
                if (btnFilterClothes != null) btnFilterClothes.setOnClickListener(v -> { selectedWardrobeCategoryFilter = "clothes"; applyWardrobeFilter.run(); });

                gridWardrobeItems.setAdapter(new android.widget.BaseAdapter() {
                    @Override
                    public int getCount() { return filteredOwned.size(); }
                    @Override
                    public Object getItem(int position) { return filteredOwned.get(position); }
                    @Override
                    public long getItemId(int position) { return filteredOwned.get(position).getId(); }
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        if (convertView == null) {
                            convertView = LayoutInflater.from(DashboardActivity.this).inflate(R.layout.item_shop_grid, parent, false);
                        }
                        ShopItem item = filteredOwned.get(position);

                        View badgeBg = convertView.findViewById(R.id.view_shop_badge_bg);
                        TextView tvEmoji = convertView.findViewById(R.id.tv_shop_item_emoji);
                        ImageView ivItemImage = convertView.findViewById(R.id.iv_shop_item_image);
                        View lockOverlay = convertView.findViewById(R.id.view_shop_lock_overlay);
                        ImageView ivLockIcon = convertView.findViewById(R.id.iv_shop_lock_icon);
                        TextView tvName = convertView.findViewById(R.id.tv_shop_item_name);
                        TextView tvMeta = convertView.findViewById(R.id.tv_shop_item_meta);

                        bindShopItemImagePreview(item, ivItemImage, tvEmoji);
                        tvName.setText(item.getName());

                        boolean isEquipped = isItemEquippedInSlot(item);

                        if (isEquipped) {
                            tvMeta.setText("EQUIPPED");
                            tvMeta.setTextColor(android.graphics.Color.parseColor("#2D5A27"));
                            androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#546B41")));
                        } else {
                            tvMeta.setText(item.getRarityTier());
                            tvMeta.setTextColor(android.graphics.Color.parseColor("#64748B"));
                            androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#CBD5E1")));
                        }

                        lockOverlay.setVisibility(View.GONE);
                        ivLockIcon.setVisibility(View.GONE);

                        convertView.setOnClickListener(v -> {
                            selectedWardrobeItem = item;
                            btnEquipAction.setVisibility(View.VISIBLE);
                            String itemSlot = TaskManager.getItemSlotType(item);
                            ShopItem eq = TaskManager.getEquippedItemForSlot(DashboardActivity.this, itemSlot);
                            if (eq != null && eq.getId() == item.getId()) {
                                btnEquipAction.setText("Unequip " + item.getName());
                            } else {
                                btnEquipAction.setText("Equip " + item.getName());
                            }
                        });
                        return convertView;
                    }
                });

                btnEquipAction.setOnClickListener(v -> {
                    if (selectedWardrobeItem != null) {
                        String itemSlot = TaskManager.getItemSlotType(selectedWardrobeItem);
                        ShopItem currentlyEquipped = TaskManager.getEquippedItemForSlot(this, itemSlot);
                        if (currentlyEquipped != null && currentlyEquipped.getId() == selectedWardrobeItem.getId()) {
                            TaskManager.unequipItemInSlot(this, itemSlot);
                            ToastUtils.showToast(this, "Unequipped: " + selectedWardrobeItem.getName());
                        } else {
                            TaskManager.setEquippedItem(this, selectedWardrobeItem);
                            ToastUtils.showToast(this, "Equipped: " + selectedWardrobeItem.getName());
                        }
                        AvatarCompositor.clearCache();
                        updateGlobalAvatarHeader();
                        updateSidebarItemPreviews.run();
                        applyWardrobeFilter.run();
                    }
                });

                updateSidebarItemPreviews.run();
                applyWardrobeFilter.run();
                break;

            case "SHOP":
                highlightTab(navShop);
                if (tabViewShop == null) {
                    tabViewShop = inflater.inflate(R.layout.view_dashboard_shop, contentFrame, false);
                    contentFrame.addView(tabViewShop);
                    setupDragHandle(tabViewShop);
                }
                tabViewShop.setVisibility(View.VISIBLE);

                TextView tvShopGoldBalance = tabViewShop.findViewById(R.id.tv_shop_gold_balance);
                TextView tvStatusBadge = tabViewShop.findViewById(R.id.tv_shop_status_badge);
                TextView tvShopTimer = tabViewShop.findViewById(R.id.tv_shop_timer);
                Button btnRefreshShop = tabViewShop.findViewById(R.id.btn_refresh_shop);
                Button btnPurchaseAction = tabViewShop.findViewById(R.id.btn_shop_action);
                android.widget.GridView gridShopItems = tabViewShop.findViewById(R.id.grid_shop_items);

                btnPurchaseAction.setText("Purchase Item");
                btnPurchaseAction.setVisibility(View.GONE);

                TaskManager shopManager = new TaskManager(this);
                int shopUserLevel = getCurrentUserLevel();

                boolean isOnline = OnlineShopManager.isNetworkAvailable(this);
                boolean showBadge = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).getBoolean("pref_show_network_badge", false);
                if (tvStatusBadge != null) {
                    if (showBadge) {
                        tvStatusBadge.setVisibility(View.VISIBLE);
                        tvStatusBadge.setText(isOnline ? "🌐 Online" : "📱 Offline");
                    } else {
                        tvStatusBadge.setVisibility(View.GONE);
                    }
                }

                Runnable updateRefreshButtonState = new Runnable() {
                    @Override
                    public void run() {
                        if (btnRefreshShop != null) {
                            int rerollCount = shopManager.getShopRerollCountToday(DashboardActivity.this);
                            int tokenQty = shopManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH);

                            if (rerollCount >= 5) {
                                btnRefreshShop.setText("🔄 Maxed (5/5)");
                                btnRefreshShop.setEnabled(false);
                                btnRefreshShop.setAlpha(0.6f);
                            } else if (tokenQty > 0) {
                                btnRefreshShop.setText("🔄 Refresh (1 Token) (" + rerollCount + "/5)");
                                btnRefreshShop.setEnabled(true);
                                btnRefreshShop.setAlpha(1.0f);
                            } else {
                                int cost = shopManager.getShopRefreshCost(DashboardActivity.this);
                                btnRefreshShop.setText("🔄 Refresh (" + cost + "g) (" + rerollCount + "/5)");
                                btnRefreshShop.setEnabled(true);
                                btnRefreshShop.setAlpha(1.0f);
                            }
                        }
                    }
                };
                updateRefreshButtonState.run();

                java.util.List<ShopItem> shopList = OnlineShopManager.getDynamicShopItems(this);

                startShopTimerTicker(tvShopTimer, () -> {
                    OnlineShopManager.forceShopRotationRefresh(this);
                    shopList.clear();
                    shopList.addAll(OnlineShopManager.getDynamicShopItems(this));
                    if (gridShopItems.getAdapter() != null) {
                        ((android.widget.BaseAdapter) gridShopItems.getAdapter()).notifyDataSetChanged();
                    }
                    updateRefreshButtonState.run();
                });

                gridShopItems.setAdapter(new android.widget.BaseAdapter() {
                    @Override
                    public int getCount() { return shopList.size(); }
                    @Override
                    public Object getItem(int position) { return shopList.get(position); }
                    @Override
                    public long getItemId(int position) { return shopList.get(position).getId(); }
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        if (convertView == null) {
                            convertView = LayoutInflater.from(DashboardActivity.this).inflate(R.layout.item_shop_grid, parent, false);
                        }
                        ShopItem item = shopList.get(position);
                        boolean isOwned = shopManager.isItemOwned(item.getId());
                        boolean isLocked = getCurrentUserLevel() < item.getRequiredLevel();

                        View badgeBg = convertView.findViewById(R.id.view_shop_badge_bg);
                        TextView tvEmoji = convertView.findViewById(R.id.tv_shop_item_emoji);
                        ImageView ivItemImage = convertView.findViewById(R.id.iv_shop_item_image);
                        View lockOverlay = convertView.findViewById(R.id.view_shop_lock_overlay);
                        ImageView ivLockIcon = convertView.findViewById(R.id.iv_shop_lock_icon);
                        TextView tvName = convertView.findViewById(R.id.tv_shop_item_name);
                        TextView tvMeta = convertView.findViewById(R.id.tv_shop_item_meta);

                        int tierColor = getShopTierColor(item.getRarityTier());
                        androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(tierColor));

                        bindShopItemImagePreview(item, ivItemImage, tvEmoji);
                        tvName.setText(item.getName());

                        if (isOwned) {
                            tvMeta.setText("OWNED");
                            tvMeta.setTextColor(Color.parseColor("#546B41"));
                            lockOverlay.setVisibility(View.VISIBLE);
                            ivLockIcon.setVisibility(View.GONE);
                        } else if (isLocked) {
                            tvMeta.setText("Level " + item.getRequiredLevel());
                            tvMeta.setTextColor(Color.parseColor("#A0AEC0"));
                            lockOverlay.setVisibility(View.VISIBLE);
                            ivLockIcon.setVisibility(View.VISIBLE);
                        } else {
                            tvMeta.setText(item.getPrice() + " Gold");
                            tvMeta.setTextColor(tierColor);
                            lockOverlay.setVisibility(View.GONE);
                            ivLockIcon.setVisibility(View.GONE);
                        }

                        convertView.setOnClickListener(v -> {
                            if (isOwned) {
                                ToastUtils.showToast(DashboardActivity.this, "You already own " + item.getName() + "!");
                                btnPurchaseAction.setVisibility(View.GONE);
                                return;
                            }
                            if (isLocked) {
                                ToastUtils.showToast(DashboardActivity.this, "Unlocks at Level " + item.getRequiredLevel());
                                btnPurchaseAction.setVisibility(View.GONE);
                                return;
                            }
                            selectedShopItem = item;
                            btnPurchaseAction.setVisibility(View.VISIBLE);
                            btnPurchaseAction.setText("Purchase " + item.getName() + " (" + item.getPrice() + "g)");
                        });
                        return convertView;
                    }
                });

                if (btnRefreshShop != null) {
                    btnRefreshShop.setOnClickListener(v -> {
                        if (shopManager.performShopRefresh(this)) {
                            shopList.clear();
                            shopList.addAll(OnlineShopManager.getDynamicShopItems(this));
                            if (gridShopItems.getAdapter() != null) {
                                ((android.widget.BaseAdapter) gridShopItems.getAdapter()).notifyDataSetChanged();
                            }
                            updateRefreshButtonState.run();
                            updateGlobalAvatarHeader();
                        }
                    });
                }

                btnPurchaseAction.setOnClickListener(v -> {
                    if (selectedShopItem != null) {
                        if (shopManager.isItemOwned(selectedShopItem.getId())) {
                            ToastUtils.showToast(this, "You already own " + selectedShopItem.getName() + "!");
                            btnPurchaseAction.setVisibility(View.GONE);
                            return;
                        }
                        if (shopManager.purchaseShopItem(selectedShopItem)) {
                            ToastUtils.showToast(this, "Purchased " + selectedShopItem.getName());
                            updateGlobalAvatarHeader();
                            btnPurchaseAction.setVisibility(View.GONE);
                            if (gridShopItems.getAdapter() != null) {
                                ((android.widget.BaseAdapter) gridShopItems.getAdapter()).notifyDataSetChanged();
                            }
                        } else {
                            ToastUtils.showToast(this, "Not enough Gold!");
                        }
                    }
                });
                break;

            case "ME":
                highlightTab(navMe);
                if (tabViewMe == null) {
                    tabViewMe = inflater.inflate(R.layout.view_dashboard_me, contentFrame, false);
                    contentFrame.addView(tabViewMe);
                }
                tabViewMe.setVisibility(View.VISIBLE);

                loadMeTabDataData(tabViewMe);
                populateAchievementsList(tabViewMe);
                wireSettingsButton(tabViewMe);
                break;
        }
    }

    private final static android.util.LruCache<String, Bitmap> sAccessoryBitmapCache = new android.util.LruCache<>(200);

    private void bindShopItemImagePreview(ShopItem item, ImageView ivItemImage, TextView tvEmoji) {
        if (item == null) return;
        String resName = item.getResName();
        if (resName != null && !resName.isEmpty() && ivItemImage != null) {
            Bitmap cached = sAccessoryBitmapCache.get(resName);
            if (cached != null && !cached.isRecycled()) {
                ivItemImage.setImageBitmap(cached);
                ivItemImage.setVisibility(View.VISIBLE);
                if (tvEmoji != null) tvEmoji.setVisibility(View.GONE);
                return;
            }

            int drawableRes = 0;
            int tintColor = 0;

            if (resName.startsWith("accessory_")) {
                int lastUnderscore = resName.lastIndexOf('_');
                if (lastUnderscore > 0) {
                    String baseAsset = resName.substring(0, lastUnderscore);
                    String colorName = resName.substring(lastUnderscore + 1);
                    drawableRes = getResources().getIdentifier(baseAsset, "drawable", getPackageName());
                    tintColor = AvatarConfig.parseAccessoryColor(colorName);
                } else {
                    drawableRes = getResources().getIdentifier(resName, "drawable", getPackageName());
                }
            } else {
                drawableRes = getResources().getIdentifier(resName, "drawable", getPackageName());
            }

            if (drawableRes != 0) {
                if (tintColor != 0) {
                    Bitmap tintedBitmap = AvatarCompositor.generateAccessoryBitmap(this, drawableRes, tintColor);
                    if (tintedBitmap != null) {
                        sAccessoryBitmapCache.put(resName, tintedBitmap);
                        ivItemImage.setImageBitmap(tintedBitmap);
                    } else {
                        ivItemImage.setImageResource(drawableRes);
                    }
                } else {
                    ivItemImage.setImageResource(drawableRes);
                }
                ivItemImage.setVisibility(View.VISIBLE);
                if (tvEmoji != null) tvEmoji.setVisibility(View.GONE);
                return;
            }
        }

        if (ivItemImage != null) ivItemImage.setVisibility(View.GONE);
        if (tvEmoji != null) {
            tvEmoji.setText(item.getIconEmoji());
            tvEmoji.setVisibility(View.VISIBLE);
        }
    }

    private boolean isItemEquippedInSlot(ShopItem item) {
        if (item == null) return false;
        String slot = TaskManager.getItemSlotType(item);
        ShopItem equipped = TaskManager.getEquippedItemForSlot(this, slot);
        return equipped != null && equipped.getId() == item.getId();
    }

    private Handler boostTimerHandler = new Handler(Looper.getMainLooper());
    private Runnable boostTimerRunnable;

    private void updateActiveBoostTickerUI() {
        stopBoostTimerTicker();

        View containerXp = findViewById(R.id.xp_boost_badge_container);
        ImageView ivIconXp = findViewById(R.id.iv_global_dashboard_xp_boost_icon);
        TextView tvTimerXp = findViewById(R.id.tv_global_dashboard_xp_boost_timer);

        View containerGold = findViewById(R.id.gold_boost_badge_container);
        ImageView ivIconGold = findViewById(R.id.iv_global_dashboard_gold_boost_icon);
        TextView tvTimerGold = findViewById(R.id.tv_global_dashboard_gold_boost_timer);

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        long xpExp = prefs.getLong("pref_xp_boost_expiration_timestamp", 0);
        long goldExp = prefs.getLong("pref_gold_boost_expiration_timestamp", 0);
        long now = System.currentTimeMillis();

        boolean xpActive = now < xpExp;
        boolean goldActive = now < goldExp;

        if (containerXp != null) containerXp.setVisibility(xpActive ? View.VISIBLE : View.GONE);
        if (containerGold != null) containerGold.setVisibility(goldActive ? View.VISIBLE : View.GONE);

        if (xpActive && ivIconXp != null) {
            String typeXp = prefs.getString("pref_active_xp_boost_type", DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
            int iconRes = DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5.equals(typeXp) ? R.drawable.icon_1_5x_xp : R.drawable.icon_2x_xp;
            ivIconXp.setImageResource(iconRes);
        }

        if (goldActive && ivIconGold != null) {
            String typeGold = prefs.getString("pref_active_gold_boost_type", DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);
            int iconRes = DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5.equals(typeGold) ? R.drawable.icon_1_5x_gold : R.drawable.icon_2x_gold;
            ivIconGold.setImageResource(iconRes);
        }

        if (!xpActive && !goldActive) return;

        boostTimerRunnable = new Runnable() {
            @Override
            public void run() {
                long currentNow = System.currentTimeMillis();
                long remXp = xpExp - currentNow;
                long remGold = goldExp - currentNow;

                if (remXp <= 0 && containerXp != null) containerXp.setVisibility(View.GONE);
                if (remGold <= 0 && containerGold != null) containerGold.setVisibility(View.GONE);

                if (remXp > 0 && tvTimerXp != null) {
                    long h = (remXp / (1000 * 60 * 60)) % 24;
                    long m = (remXp / (1000 * 60)) % 60;
                    long s = (remXp / 1000) % 60;
                    tvTimerXp.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
                }

                if (remGold > 0 && tvTimerGold != null) {
                    long h = (remGold / (1000 * 60 * 60)) % 24;
                    long m = (remGold / (1000 * 60)) % 60;
                    long s = (remGold / 1000) % 60;
                    tvTimerGold.setText(String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
                }

                if (remXp > 0 || remGold > 0) {
                    boostTimerHandler.postDelayed(this, 1000);
                }
            }
        };
        boostTimerHandler.post(boostTimerRunnable);
    }

    private void stopBoostTimerTicker() {
        if (boostTimerRunnable != null) {
            boostTimerHandler.removeCallbacks(boostTimerRunnable);
            boostTimerRunnable = null;
        }
    }

    private void updateGlobalAvatarHeader() {
        updateActiveBoostTickerUI();
        if (avatarHostContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarHostContainer);
        } else {
            FrameLayout host = findViewById(R.id.avatar_host_container);
            if (host != null) {
                AvatarHelper.renderUserAvatar(this, host);
            }
        }

        TextView tvLvl = findViewById(R.id.tv_global_dashboard_lvl);
        TextView tvGold = findViewById(R.id.tv_global_dashboard_gold);
        android.widget.ProgressBar pbXp = findViewById(R.id.pb_global_dashboard_xp);

        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();
        if (profileCursor != null && profileCursor.moveToFirst()) {
            int level = profileCursor.getInt(1);
            int gold = profileCursor.getInt(2);
            int xp = profileCursor.getInt(3);
            int maxXp = TaskManager.getRequiredXpForLevel(level);

            if (tvLvl != null) {
                tvLvl.setText(getString(R.string.lvl_placeholder, level));
            }
            if (tvGold != null) {
                tvGold.setText(getString(R.string.gold_placeholder, gold));
            }
            if (pbXp != null) {
                pbXp.setMax(maxXp);
                pbXp.setProgress(xp);
            }
            profileCursor.close();
        }
    }

    private void wireSettingsButton(View meView) {
        android.widget.ImageButton btnSettings = meView.findViewById(R.id.btn_open_settings);
        if (btnSettings != null) {
            btnSettings.setOnClickListener(v -> {
                SoundEffectsHelper.playMenuOpen(this);
                startActivity(new Intent(this, SettingsActivity.class));
            });
        }

        View btnTutorial = meView.findViewById(R.id.btn_app_tutorial);
        if (btnTutorial != null) {
            btnTutorial.setOnClickListener(v -> {
                SoundEffectsHelper.playMenuOpen(this);
                showAppTutorialDialog();
            });
        }
    }

    private void showAppTutorialDialog() {
        SoundEffectsHelper.playMenuOpen(this);

        android.view.ViewGroup decorView = (android.view.ViewGroup) getWindow().getDecorView();
        SpotlightOverlayView spotlightView = new SpotlightOverlayView(this);
        spotlightView.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
        ));
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_spotlight_tutorial, spotlightView, false);
        spotlightView.addView(dialogView);
        decorView.addView(spotlightView);

        TextView tvTitle = dialogView.findViewById(R.id.tv_spotlight_step_title);
        TextView tvDesc = dialogView.findViewById(R.id.tv_spotlight_step_desc);
        Button btnPrev = dialogView.findViewById(R.id.btn_spotlight_prev);
        Button btnNext = dialogView.findViewById(R.id.btn_spotlight_next);

        String[] stepTitles = {
                "1. QUEST Tab & Add Goal 📜",
                "2. WARDROBE Tab 💇",
                "3. SHOP Catalog 🛒",
                "4. Chest Milestone Bar 🎁",
                "5. ME Tab & Info Icon ℹ️"
        };

        String[] stepDescs = {
                "Tap QUEST to view your Daily Quests and create Custom Goals tailored to your self-care journey!",
                "Tap WARDROBE to customize your character avatar with hairstyles, skin tones, hats, glasses, and outfits!",
                "Tap SHOP to spend earned Gold and unlock new clothing and accessory items!",
                "Every 3 daily quests completed fills your Chest Bar to unlock Wooden, Silver, Gold, and Platinum Chests!",
                "Tap ME to view your Level, Streaks, Achievements, and relaunch this tutorial anytime using the Info icon ℹ️!"
        };

        int[] currentStep = { 0 };

        Runnable dismissTutorial = () -> {
            SoundEffectsHelper.playMenuClose(this);
            decorView.removeView(spotlightView);
        };

        Runnable refreshStep = () -> {
            int step = currentStep[0];
            if (tvTitle != null) tvTitle.setText(stepTitles[step]);
            if (tvDesc != null) tvDesc.setText(stepDescs[step]);

            View targetView = null;
            if (step == 0) {
                selectTab("QUEST");
                targetView = navQuest;
            } else if (step == 1) {
                selectTab("WARDROBE");
                targetView = navWardrobe;
            } else if (step == 2) {
                selectTab("SHOP");
                targetView = navShop;
            } else if (step == 3) {
                selectTab("QUEST");
                targetView = findViewById(R.id.card_chest_bar);
            } else if (step == 4) {
                selectTab("ME");
                targetView = navMe;
            }

            final View finalTarget = targetView;
            spotlightView.post(() -> spotlightView.setTargetView(finalTarget));

            if (btnPrev != null) btnPrev.setVisibility(step > 0 ? View.VISIBLE : View.GONE);
            if (btnNext != null) btnNext.setText(step == stepTitles.length - 1 ? "Got It!" : "Next");
        };

        refreshStep.run();

        if (btnPrev != null) {
            btnPrev.setOnClickListener(v -> {
                if (currentStep[0] > 0) {
                    currentStep[0]--;
                    refreshStep.run();
                }
            });
        }

        if (btnNext != null) {
            btnNext.setOnClickListener(v -> {
                if (currentStep[0] < stepTitles.length - 1) {
                    currentStep[0]++;
                    refreshStep.run();
                } else {
                    dismissTutorial.run();
                }
            });
        }
    }

    private void checkFirstTimeUserTutorialPrompt() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        boolean hasSeenTutorialPrompt = prefs.getBoolean("has_seen_tutorial_prompt", false);
        if (!hasSeenTutorialPrompt) {
            prefs.edit().putBoolean("has_seen_tutorial_prompt", true).apply();
            new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                    .setTitle("Welcome to DaGoal! ℹ️")
                    .setMessage("New to DaGoal? You can tap the Info icon (ℹ️) on the ME tab anytime to view the interactive App Tutorial!")
                    .setPositiveButton("View Tutorial Now", (dialog, which) -> showAppTutorialDialog())
                    .setNegativeButton("Got It", null)
                    .show();
        }
    }

    private void scheduleDailyNotifications() {
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            java.util.Calendar calMidnight = java.util.Calendar.getInstance();
            calMidnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
            calMidnight.set(java.util.Calendar.MINUTE, 0);
            calMidnight.set(java.util.Calendar.SECOND, 0);
            if (calMidnight.getTimeInMillis() <= System.currentTimeMillis()) {
                calMidnight.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            Intent intentMidnight = new Intent(this, DailyNotificationReceiver.class);
            intentMidnight.setAction(DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS);
            android.app.PendingIntent piMidnight = android.app.PendingIntent.getBroadcast(
                    this, 801, intentMidnight,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.setInexactRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calMidnight.getTimeInMillis(),
                    android.app.AlarmManager.INTERVAL_DAY,
                    piMidnight
            );

            java.util.Calendar calEvening = java.util.Calendar.getInstance();
            calEvening.set(java.util.Calendar.HOUR_OF_DAY, 20);
            calEvening.set(java.util.Calendar.MINUTE, 0);
            calEvening.set(java.util.Calendar.SECOND, 0);
            if (calEvening.getTimeInMillis() <= System.currentTimeMillis()) {
                calEvening.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            Intent intentEvening = new Intent(this, DailyNotificationReceiver.class);
            intentEvening.setAction(DailyNotificationReceiver.ACTION_EVENING_REMINDER);
            android.app.PendingIntent piEvening = android.app.PendingIntent.getBroadcast(
                    this, 802, intentEvening,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.setInexactRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calEvening.getTimeInMillis(),
                    android.app.AlarmManager.INTERVAL_DAY,
                    piEvening
            );
        } catch (Exception ignored) {}
    }

    private void loadMeTabDataData(View meView) {
        if (meView == null) return;
        TextView tvProfileUsername = meView.findViewById(R.id.tv_profile_username);
        TextView tvProfileLevel = meView.findViewById(R.id.tv_profile_level);
        TextView tvProfileStreak = meView.findViewById(R.id.tv_profile_streak);

        FrameLayout avatarMeHost = meView.findViewById(R.id.avatar_me_host);
        if (avatarMeHost != null) {
            AvatarHelper.renderUserAvatarFaceOnly(this, avatarMeHost);
        }

        View layoutProfileAvatarHost = meView.findViewById(R.id.layout_profile_avatar_host);
        if (layoutProfileAvatarHost != null) {
            layoutProfileAvatarHost.setOnClickListener(v -> {
                Intent intent = new Intent(this, AvatarCreationActivity.class);
                intent.putExtra("extra_edit_mode", true);
                startActivity(intent);
            });
        }

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        String savedNickname = prefs.getString("user_nickname", null);

        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();

        if (profileCursor != null && profileCursor.moveToFirst()) {
            String dbUsername = profileCursor.getString(0);
            String username = (savedNickname != null && !savedNickname.isEmpty()) ? savedNickname : (dbUsername != null && !dbUsername.isEmpty() ? dbUsername : "Adventurer");
            int level = profileCursor.getInt(1);
            int gold = profileCursor.getInt(2);
            int xp = profileCursor.getInt(3);

            String title = TaskManager.getLevelTitle(level);

            int maxXp = TaskManager.getRequiredXpForLevel(level);

            if (tvProfileUsername != null) tvProfileUsername.setText(username + " • " + title);
            if (tvProfileLevel != null) tvProfileLevel.setText("Level " + level + "\n" + xp + " / " + maxXp + " XP");
            profileCursor.close();
        }

        View cardStreak = meView.findViewById(R.id.card_streak);
        if (cardStreak != null) {
            cardStreak.setOnClickListener(v -> startActivity(new Intent(this, StreakInfoActivity.class)));
        }

        View badgeStreakUnclaimed = meView.findViewById(R.id.badge_streak_unclaimed_chest);
        if (badgeStreakUnclaimed != null) {
            boolean hasReward = TaskManager.hasUnclaimedStreakReward(this);
            badgeStreakUnclaimed.setVisibility(hasReward ? View.VISIBLE : View.GONE);
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor streakCursor = db.rawQuery("SELECT streak FROM user WHERE _id = 1", null);
        if (streakCursor != null && streakCursor.moveToFirst()) {
            int streak = streakCursor.getInt(0);
            if (tvProfileStreak != null) tvProfileStreak.setText(getString(R.string.streak_days, streak));
            streakCursor.close();
        }

        View cardConsumables = meView.findViewById(R.id.card_consumables);
        if (cardConsumables != null) {
            cardConsumables.setOnClickListener(v -> showConsumablesDialog(meView));
        }
        updateConsumablesCardSummary(meView);
    }

    private void updateConsumablesCardSummary(View meView) {
        if (meView == null) return;
        TextView tvConsumablesSummary = meView.findViewById(R.id.tv_consumables_summary);
        if (tvConsumablesSummary == null) return;

        TaskManager taskManager = new TaskManager(this);
        int xp15 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5);
        int gold15 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
        int xp20 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
        int gold20 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);

        int totalBoosts = xp15 + gold15 + xp20 + gold20;
        tvConsumablesSummary.setText(totalBoosts + " Active / Available Boost(s)");
    }

    private void showConsumablesDialog(View meView) {
        SoundEffectsHelper.playMenuOpen(this);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_consumables, null);
        builder.setView(dialogView);

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TaskManager taskManager = new TaskManager(this);
        String currentDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        TextView tvQtyXp15 = dialogView.findViewById(R.id.tv_qty_xp_boost_1_5);
        TextView tvQtyGold15 = dialogView.findViewById(R.id.tv_qty_gold_boost_1_5);
        TextView tvQtyXp = dialogView.findViewById(R.id.tv_qty_xp_boost);
        TextView tvQtyGold = dialogView.findViewById(R.id.tv_qty_gold_boost);

        Button btnActivateXp15 = dialogView.findViewById(R.id.btn_activate_xp_boost_1_5);
        Button btnActivateGold15 = dialogView.findViewById(R.id.btn_activate_gold_boost_1_5);
        Button btnActivateXp = dialogView.findViewById(R.id.btn_activate_xp_boost);
        Button btnActivateGold = dialogView.findViewById(R.id.btn_activate_gold_boost);
        Button btnClose = dialogView.findViewById(R.id.btn_close_consumables);

        Runnable refreshDialogUI = new Runnable() {
            @Override
            public void run() {
                int qtyXp15 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5);
                int qtyGold15 = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
                int qtyXp = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
                int qtyGold = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);

                boolean xpActive = TaskManager.isXpBoostActive(DashboardActivity.this, currentDateStr);
                boolean goldActive = TaskManager.isGoldBoostActive(DashboardActivity.this, currentDateStr);

                if (tvQtyXp15 != null) tvQtyXp15.setText("Available: x" + qtyXp15);
                if (tvQtyGold15 != null) tvQtyGold15.setText("Available: x" + qtyGold15);
                if (tvQtyXp != null) tvQtyXp.setText("Available: x" + qtyXp);
                if (tvQtyGold != null) tvQtyGold.setText("Available: x" + qtyGold);

                if (btnActivateXp15 != null) {
                    btnActivateXp15.setText("Activate");
                    btnActivateXp15.setEnabled(qtyXp15 > 0 && !xpActive);
                }
                if (btnActivateGold15 != null) {
                    btnActivateGold15.setText("Activate");
                    btnActivateGold15.setEnabled(qtyGold15 > 0 && !goldActive);
                }

                if (btnActivateXp != null) {
                    if (xpActive) {
                        btnActivateXp.setText("Active Today");
                        btnActivateXp.setEnabled(false);
                    } else {
                        btnActivateXp.setText("Activate");
                        btnActivateXp.setEnabled(qtyXp > 0);
                    }
                }

                if (btnActivateGold != null) {
                    if (goldActive) {
                        btnActivateGold.setText("Active Today");
                        btnActivateGold.setEnabled(false);
                    } else {
                        btnActivateGold.setText("Activate");
                        btnActivateGold.setEnabled(qtyGold > 0);
                    }
                }

                updateConsumablesCardSummary(meView);
            }
        };

        refreshDialogUI.run();

        if (btnActivateXp15 != null) {
            btnActivateXp15.setOnClickListener(v -> {
                SoundEffectsHelper.playButton(this);
                taskManager.useConsumable(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5);
                taskManager.activateXpBoost(this);
                ToastUtils.showToast(this, "⚡ 1.5x XP Boost Activated!");
                refreshDialogUI.run();
            });
        }

        if (btnActivateGold15 != null) {
            btnActivateGold15.setOnClickListener(v -> {
                SoundEffectsHelper.playButton(this);
                taskManager.useConsumable(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5);
                taskManager.activateGoldBoost(this);
                ToastUtils.showToast(this, "💰 1.5x Gold Boost Activated!");
                refreshDialogUI.run();
            });
        }

        if (btnActivateXp != null) {
            btnActivateXp.setOnClickListener(v -> {
                SoundEffectsHelper.playButton(this);
                if (taskManager.activateXpBoost(this)) {
                    ToastUtils.showToast(this, "⚡ 2x XP Boost Activated for Today!");
                    refreshDialogUI.run();
                }
            });
        }

        if (btnActivateGold != null) {
            btnActivateGold.setOnClickListener(v -> {
                SoundEffectsHelper.playButton(this);
                if (taskManager.activateGoldBoost(this)) {
                    ToastUtils.showToast(this, "🪙 2x Gold Boost Activated for Today!");
                    refreshDialogUI.run();
                }
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> {
                SoundEffectsHelper.playMenuClose(this);
                dialog.dismiss();
            });
        }
        dialog.setOnDismissListener(d -> SoundEffectsHelper.playMenuClose(this));

        dialog.show();
    }

    private void populateAchievementsList(View meView) {
        android.widget.GridView gridAchievements = meView.findViewById(R.id.list_profile_achievements);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                null, null, null, null, null, null
        );

        if (cursor != null) {
            gridAchievements.setAdapter(new android.widget.BaseAdapter() {
                @Override
                public int getCount() { return cursor.getCount(); }
                @Override
                public Object getItem(int position) { return null; }
                @Override
                public long getItemId(int position) { return position; }
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    if (convertView == null) {
                        convertView = LayoutInflater.from(DashboardActivity.this).inflate(R.layout.item_achievement_grid, parent, false);
                    }
                    cursor.moveToPosition(position);

                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_TITLE));
                    String desc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION));
                    int currentProgress = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS));
                    int baseTarget = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE));
                    String emoji = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_ICON_EMOJI));

                    View badgeBg = convertView.findViewById(R.id.view_achievement_badge_bg);
                    TextView tvRankBadge = convertView.findViewById(R.id.tv_achievement_rank_badge);
                    TextView tvEmoji = convertView.findViewById(R.id.tv_achievement_emoji);
                    TextView tvGridTitle = convertView.findViewById(R.id.tv_achievement_grid_title);
                    TextView tvRankLabel = convertView.findViewById(R.id.tv_achievement_rank_label);

                    int badgeColor = AchievementTierHelper.getBadgeColor(currentProgress, baseTarget);
                    androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(badgeColor));

                    int rankIndex = AchievementTierHelper.getCurrentRankIndex(currentProgress, baseTarget);
                    tvRankBadge.setText(rankIndex < 0 ? "-" : String.valueOf(rankIndex + 1));

                    tvEmoji.setText(emoji);
                    tvGridTitle.setText(title);

                    TextView tvGridDesc = convertView.findViewById(R.id.tv_achievement_grid_desc);
                    if (tvGridDesc != null) {
                        tvGridDesc.setText(desc);
                    }

                    if (rankIndex < 0) {
                        tvRankLabel.setText("Unranked");
                        tvRankLabel.setTextColor(Color.parseColor("#A0AEC0"));
                    } else {
                        tvRankLabel.setText(AchievementTierHelper.RANK_NAMES[rankIndex]);
                        tvRankLabel.setTextColor(badgeColor);
                    }

                    convertView.setOnClickListener(v -> showAchievementDetailDialog(title, desc, currentProgress, baseTarget, emoji));

                    return convertView;
                }
            });
        }
    }

    private String getQuestTypeEmoji(String questType) {
        if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(questType)) {
            return "\uD83D\uDC63";
        } else if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT.equals(questType)) {
            return "\u2795";
        } else if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType)) {
            return "\u23F1\uFE0F";
        } else {
            return "\uD83D\uDCCB";
        }
    }

    private void showQuestDetailDialog(int taskId, String title, int target, String unit, String questType,
                                       String difficultyTier, int currentValue, int rewardGold, int rewardXp,
                                       boolean isCustom, boolean isCompleted, String packageName,
                                       LinearLayout activeContainer, LinearLayout completedContainer) {
        if (isCompleted) {
            new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                    .setTitle("Undo Quest Completion?")
                    .setMessage("Would you like to undo completing this quest? Doing so will revert its progress and refund its rewards.")
                    .setPositiveButton("Undo", (dialog, which) -> {
                        SoundEffectsHelper.playButton(this);
                        TaskManager taskManager = new TaskManager(this);
                        taskManager.uncompleteTask(taskId, rewardGold, rewardXp);
                        updateChestBarUI();
                        populateQuestLists(activeContainer, completedContainer);
                        ToastUtils.showToast(this, "Quest un-completed. Reverted rewards.");
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return;
        }
        SoundEffectsHelper.playMenuOpen(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_quest_detail, null);

        TextView tvEmoji = dialogView.findViewById(R.id.tv_quest_detail_emoji);
        TextView tvTitle = dialogView.findViewById(R.id.tv_quest_detail_title);
        TextView tvDescription = dialogView.findViewById(R.id.tv_quest_detail_description);
        ImageButton btnAction = dialogView.findViewById(R.id.btn_quest_detail_action);
        TextView tvDifficulty = dialogView.findViewById(R.id.tv_quest_detail_difficulty);
        TextView tvReward = dialogView.findViewById(R.id.tv_quest_detail_reward);
        android.widget.ProgressBar pbProgress = dialogView.findViewById(R.id.pb_quest_detail_progress);
        TextView tvProgressText = dialogView.findViewById(R.id.tv_quest_detail_progress_text);
        Button btnClose = dialogView.findViewById(R.id.btn_quest_detail_close);

        tvEmoji.setText(getQuestTypeEmoji(questType));
        tvTitle.setText(title);

        TaskManager tm = new TaskManager(this);
        String blockedAppsList = tm.getCustomAppNamesFormatted(packageName);

        if ("Avoid social media".equalsIgnoreCase(title) || (DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType) && (difficultyTier == null || difficultyTier.isEmpty() || difficultyTier.startsWith("EASY") || difficultyTier.startsWith("HARD")))) {
            tvDescription.setText("Warns you every time you open an app in your block list during active screen avoidance.\nBlocked apps: " + blockedAppsList);
        } else if (DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType)) {
            tvDescription.setText(difficultyTier + "\nBlocked apps: " + blockedAppsList);
        } else if (difficultyTier != null && !difficultyTier.isEmpty() && !difficultyTier.startsWith("EASY") && !difficultyTier.startsWith("MEDIUM") && !difficultyTier.startsWith("HARD")) {
            tvDescription.setText(difficultyTier);
        } else {
            String unitText = "minutes".equalsIgnoreCase(unit) ? TaskManager.formatDurationMinutes(target) : target + " " + unit;
            tvDescription.setText("Goal: " + unitText);
        }
        if (tvDifficulty != null) {
            tvDifficulty.setVisibility(View.GONE);
        }
        tvReward.setText("Reward: " + rewardGold + " Gold / " + rewardXp + " XP");

        int safeTarget = Math.max(target, 1);
        int percent = (int) (((double) currentValue / safeTarget) * 100);
        pbProgress.setMax(100);
        pbProgress.setProgress(Math.min(percent, 100));
        tvProgressText.setText(currentValue + "/" + target);

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setView(dialogView)
                .create();

        if (isCompleted) {
            btnAction.setVisibility(View.GONE);
        } else {
            btnAction.setImageResource(isCustom ? android.R.drawable.ic_menu_delete : android.R.drawable.ic_menu_close_clear_cancel);

            btnAction.setOnClickListener(v -> {
                String confirmTitle = isCustom ? "Delete this quest?" : "Forfeit this quest?";
                String confirmMessage = isCustom
                        ? "This will remove the quest. Deleting a custom quest still uses up this week's custom quest slot."
                        : "This quest will be removed and the slot will stay empty for the rest of today.";

                new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                        .setTitle(confirmTitle)
                        .setMessage(confirmMessage)
                        .setPositiveButton(isCustom ? "Delete" : "Forfeit", (d, w) -> {
                            TaskManager taskManager = new TaskManager(DashboardActivity.this);
                            taskManager.removeQuest(taskId);
                            populateQuestLists(activeContainer, completedContainer);
                            dialog.dismiss();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        btnClose.setOnClickListener(v -> {
            SoundEffectsHelper.playMenuClose(this);
            dialog.dismiss();
        });
        dialog.setOnDismissListener(d -> SoundEffectsHelper.playMenuClose(this));
        dialog.show();
    }

    private void showAchievementDetailDialog(String title, String description, int currentProgress, int baseTarget, String emoji) {
        SoundEffectsHelper.playMenuOpen(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_achievement_detail, null);

        View badgeBg = dialogView.findViewById(R.id.view_dialog_badge_bg);
        TextView tvEmoji = dialogView.findViewById(R.id.tv_dialog_achievement_emoji);
        TextView tvDesc = dialogView.findViewById(R.id.tv_dialog_achievement_desc);
        TextView tvRankName = dialogView.findViewById(R.id.tv_dialog_rank_name);
        android.widget.ProgressBar pbProgress = dialogView.findViewById(R.id.pb_dialog_rank_progress);
        TextView tvProgressLabel = dialogView.findViewById(R.id.tv_dialog_progress_label);

        int badgeColor = AchievementTierHelper.getBadgeColor(currentProgress, baseTarget);
        androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(badgeColor));

        if (tvEmoji != null) {
            tvEmoji.setText(emoji);
            tvEmoji.setVisibility(View.VISIBLE);
        }

        tvDesc.setText(title + "\n" + description);
        tvRankName.setText(AchievementTierHelper.getRankName(currentProgress, baseTarget));
        pbProgress.setProgress(AchievementTierHelper.getProgressPercentToNextRank(currentProgress, baseTarget));

        if (AchievementTierHelper.isMaxRank(currentProgress, baseTarget)) {
            tvProgressLabel.setText("Max rank reached!");
        } else {
            int remaining = AchievementTierHelper.getRemainingToNextRank(currentProgress, baseTarget);
            int nextTarget = AchievementTierHelper.getNextTarget(currentProgress, baseTarget);
            String nextRank = AchievementTierHelper.getRankName(nextTarget, baseTarget);
            tvProgressLabel.setText(remaining + " more to reach " + nextRank);
        }

        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setView(dialogView)
                .setNegativeButton("Close", (d, w) -> SoundEffectsHelper.playMenuClose(this))
                .create();
        dialog.setOnDismissListener(d -> SoundEffectsHelper.playMenuClose(this));
        dialog.show();
    }

    private void updateChestBarUI() {
        View cardChestBar = findViewById(R.id.card_chest_bar);
        if (cardChestBar == null) return;

        ImageView ivIcon = findViewById(R.id.iv_chest_bar_icon);
        TextView tvPoints = findViewById(R.id.tv_chest_bar_points);
        View viewFill = findViewById(R.id.view_chest_bar_fill);
        View viewTrack = findViewById(R.id.view_chest_bar_track);

        int points = TaskManager.getChestBarPoints(this);
        int tier = TaskManager.getChestBarCurrentTier(this);
        int currentTarget = TaskManager.getChestBarTargetPoints(this);

        if (tvPoints != null) {
            tvPoints.setText(points + "/" + currentTarget);
        }

        if (ivIcon != null) {
            int chestDrawable = R.drawable.chest_wood;
            if (tier == 2) chestDrawable = R.drawable.chest_silver;
            else if (tier == 3) chestDrawable = R.drawable.chest_gold;
            else if (tier == 4) chestDrawable = R.drawable.chest_plat;
            ivIcon.setImageResource(chestDrawable);
        }

        if (viewFill != null && viewTrack != null) {
            int trackHeightPx = viewTrack.getHeight();
            if (trackHeightPx <= 0) {
                trackHeightPx = (int) (140 * getResources().getDisplayMetrics().density);
            }
            double fillRatio = Math.min(1.0, (double) points / currentTarget);
            int fillHeightPx = (int) (fillRatio * trackHeightPx);

            android.view.ViewGroup.LayoutParams params = viewFill.getLayoutParams();
            params.height = fillHeightPx;
            viewFill.setLayoutParams(params);

            if (tier == 1) {
                viewFill.setBackgroundColor(Color.parseColor("#8A2BE2"));
            } else if (tier == 2) {
                viewFill.setBackgroundColor(Color.parseColor("#1E90FF"));
            } else {
                viewFill.setBackgroundColor(Color.parseColor("#FFD700"));
            }
        }

        cardChestBar.setOnClickListener(v -> {
            if (points >= currentTarget) {
                showChestTierDialog(tier);
            } else {
                ToastUtils.showToast(this, "Complete quests to fill the Tier " + tier + " chest bar! (" + points + "/" + currentTarget + " pts)");
            }
        });
    }

    private void showCustomQuestsInfoDialog() {
        SoundEffectsHelper.playMenuOpen(this);
        int level = getCurrentUserLevel();
        int allowance = TaskManager.getCustomQuestAllowance(level);

        new androidx.appcompat.app.AlertDialog.Builder(this, R.style.DaGoalDialogTheme)
                .setTitle("🎯 Custom Quest Slots Info")
                .setMessage("Custom Quests allow you to create personalized real-life goals with your choice of Gold and XP rewards!\n\n" +
                        "• Slot Unlock Schedule: You start with 1 slot at Level 1, gaining +1 slot every 5 Levels (up to 10 slots at Level 45+).\n\n" +
                        "• Recurring Quests & Slots: Creating a recurring custom quest (daily or weekly) consumes 1 custom quest slot for as long as it repeats.\n\n" +
                        "• Weekly Refill: Custom quest allowances refill automatically every Monday morning at 00:00 AM.\n\n" +
                        "Current Level: " + level + "\n" +
                        "Total Slots: " + allowance + " Custom Quest(s)")
                .setPositiveButton("Got It!", null)
                .show();
    }

    private void checkAndShowChestTierPrompt() {
        int points = TaskManager.getChestBarPoints(this);
        int tier = TaskManager.getChestBarCurrentTier(this);
        int target = TaskManager.getChestBarTargetPoints(this);

        if (points >= target) {
            if (tier >= 4) {
                TaskManager.claimChestTier(this, 4);
                showChestLootRevealDialog(4);
            } else {
                showChestTierDialog(tier);
            }
        }
    }

    private View createLootCardView(int iconRes, String amountText, String nameText) {
        View card = getLayoutInflater().inflate(R.layout.item_chest_loot_card, null, false);
        ImageView ivIcon = card.findViewById(R.id.iv_loot_card_icon);
        TextView tvAmount = card.findViewById(R.id.tv_loot_card_amount);
        TextView tvName = card.findViewById(R.id.tv_loot_card_name);

        if (ivIcon != null && iconRes != 0) ivIcon.setImageResource(iconRes);
        if (tvAmount != null) tvAmount.setText(amountText);
        if (tvName != null) tvName.setText(nameText);

        return card;
    }

    private int getConsumableIconRes(String type) {
        if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST_1_5.equals(type)) return R.drawable.icon_1_5x_xp;
        if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST_1_5.equals(type)) return R.drawable.icon_1_5x_gold;
        if (DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST.equals(type)) return R.drawable.icon_2x_xp;
        if (DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST.equals(type)) return R.drawable.icon_2x_gold;
        if (DatabaseContract.InventoryConsumableEntry.TYPE_SHOP_REFRESH.equals(type)) return R.drawable.shop_icon;
        if (DatabaseContract.InventoryConsumableEntry.TYPE_QUEST_REFRESH.equals(type)) return R.drawable.shuffle_icon;
        return R.drawable.exp_icon;
    }

    private void showChestLootRevealDialog(int tier) {
        TaskManager tm = new TaskManager(this);
        TaskManager.ChestLootResult loot = TaskManager.generateRandomChestLoot(this, tier);
        tm.awardChestLoot(loot);

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chest_loot_reveal, null);
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivChestImage = dialogView.findViewById(R.id.iv_loot_dialog_chest_image);
        LinearLayout row1 = dialogView.findViewById(R.id.container_loot_cards_row1);
        LinearLayout row2 = dialogView.findViewById(R.id.container_loot_cards_row2);
        Button btnCollect = dialogView.findViewById(R.id.btn_claim_loot_done);

        int chestDrawable = R.drawable.chest_wood;
        if (tier == 2) chestDrawable = R.drawable.chest_silver;
        else if (tier == 3) chestDrawable = R.drawable.chest_gold;
        else if (tier == 4) chestDrawable = R.drawable.chest_plat;

        if (ivChestImage != null) ivChestImage.setImageResource(chestDrawable);

        if (row1 != null && row2 != null) {
            row1.removeAllViews();
            row2.removeAllViews();
            row2.setVisibility(View.GONE);

            java.util.List<View> cardViews = new java.util.ArrayList<>();
            cardViews.add(createLootCardView(R.drawable.coins_icon, "+" + loot.gold, "gold"));
            cardViews.add(createLootCardView(R.drawable.exp_icon, "+" + loot.xp, "xp"));

            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            for (String type : loot.consumables) {
                counts.put(type, counts.getOrDefault(type, 0) + 1);
            }

            for (java.util.Map.Entry<String, Integer> entry : counts.entrySet()) {
                String type = entry.getKey();
                int qty = entry.getValue();
                int iconRes = getConsumableIconRes(type);
                String nameStr = TaskManager.getConsumableDisplayName(type).toLowerCase(java.util.Locale.getDefault());
                cardViews.add(createLootCardView(iconRes, "+" + qty, nameStr));
            }

            if (cardViews.size() <= 3) {
                for (View c : cardViews) row1.addView(c);
            } else {
                row2.setVisibility(View.VISIBLE);
                for (int i = 0; i < cardViews.size(); i++) {
                    if (i < 2) row1.addView(cardViews.get(i));
                    else row2.addView(cardViews.get(i));
                }
            }
        }

        SoundEffectsHelper.playCoin(this);

        if (btnCollect != null) {
            btnCollect.setOnClickListener(v -> {
                dialog.dismiss();
                updateGlobalAvatarHeader();
                updateChestBarUI();
            });
        }
        dialog.show();
    }

    private void showChestTierDialog(int tier) {
        SoundEffectsHelper.playMenuOpen(this);
        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chest_tier_prompt, null);
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_chest_dialog_title);
        TextView tvLoot = dialogView.findViewById(R.id.tv_chest_dialog_loot);
        Button btnUnlock = dialogView.findViewById(R.id.btn_unlock_chest_now);
        Button btnKeepGoing = dialogView.findViewById(R.id.btn_keep_completing_quests);

        if (tier == 1) {
            if (tvTitle != null) tvTitle.setText("Tier 1 Wooden Chest Ready!");
        } else if (tier == 2) {
            if (tvTitle != null) tvTitle.setText("Tier 2 Silver Chest Ready!");
        } else if (tier == 3) {
            if (tvTitle != null) tvTitle.setText("Tier 3 Gold Chest Ready!");
        }

        if (tvLoot != null) {
            tvLoot.setText("You can choose to unlock it now, or keep completing quests for better rewards.");
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                dialog.dismiss();
                TaskManager.claimChestTier(DashboardActivity.this, tier);
                showChestLootRevealDialog(tier);
            });
        }

        if (btnKeepGoing != null) {
            btnKeepGoing.setOnClickListener(v -> {
                SoundEffectsHelper.playButton(DashboardActivity.this);
                TaskManager.advanceToNextChestTier(DashboardActivity.this);
                dialog.dismiss();
                updateChestBarUI();
                ToastUtils.showToast(DashboardActivity.this, "Pushed for Tier " + (tier + 1) + "! Bar reset to 0/" + TaskManager.getChestBarTargetPoints(DashboardActivity.this) + " points.");
            });
        }

        dialog.setOnDismissListener(d -> SoundEffectsHelper.playMenuClose(this));
        dialog.show();
    }

    private void populateQuestLists(LinearLayout activeContainer, LinearLayout completedContainer) {
        updateGlobalAvatarHeader();
        activeContainer.removeAllViews();
        completedContainer.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        updateChestBarUI();
        checkAndShowChestTierPrompt();

        View parentView = (View) activeContainer.getParent();
        TextView tvEmpty = null;
        if (parentView != null) {
            tvEmpty = parentView.findViewById(R.id.tv_no_quests_available);
        }

        // tv_quests_remaining_header is usually in the grandparent layout
        TextView tvHeader = null;
        TextView tvCustomCounter = null;
        if (parentView != null && parentView.getParent() != null) {
            View questRoot = (View) parentView.getParent();
            tvHeader = questRoot.findViewById(R.id.tv_quests_remaining_header);
            tvCustomCounter = questRoot.findViewById(R.id.tv_custom_quest_counter);
        }

        String[] projection = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_UNIT,
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED,
                DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD,
                DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP,
                DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE,
                DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP,
                DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM,
                DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER,
                DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection,
                null, null, null, null, null
        );

        int totalCount = 0;
        int activeCustomCount = 0;
        if (cursor != null) {
            totalCount = cursor.getCount();
            int uncompletedCount = 0;
            while (cursor.moveToNext()) {
                int taskId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry._ID));
                String title = cursor.getString(1);
                int target = cursor.getInt(2);
                String unit = cursor.getString(3);
                int isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED));
                int rewardGold = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_GOLD));
                int rewardXp = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_REWARD_XP));
                String questType = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE));
                int currentValue = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_CURRENT_VALUE));
                long startTimestamp = cursor.getLong(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP));
                String packageName = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_PACKAGE_NAME));

                boolean isStepTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(questType);
                boolean isAvoidanceTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType);
                boolean isIncrementTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT.equals(questType);

                int isCustomFlag = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM));
                boolean isCustomQuest = isCustomFlag == 1;
                String difficultyTier = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER));
                boolean rowCompleted = isCompleted == 1;

                if (isCustomQuest && !rowCompleted) {
                    activeCustomCount++;
                }

                View row = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, (isCompleted == 1) ? completedContainer : activeContainer, false);
                row.setOnClickListener(v -> showQuestDetailDialog(
                        taskId, title, target, unit, questType, difficultyTier, currentValue,
                        rewardGold, rewardXp, isCustomQuest, rowCompleted, packageName, activeContainer, completedContainer
                ));
                TextView tvTitle = row.findViewById(R.id.tv_task_title);
                TextView tvTarget = row.findViewById(R.id.tv_task_target);
                TextView tvTargetPill = row.findViewById(R.id.tv_task_target_pill);
                CheckBox cbComplete = row.findViewById(R.id.btn_shuffle_item);
                ImageView ivAutoTrackedIcon = row.findViewById(R.id.iv_auto_tracked_icon);
                Button btnStartAvoidance = row.findViewById(R.id.btn_start_avoidance);
                Button btnIncrementProgress = row.findViewById(R.id.btn_increment_progress);
                android.widget.ProgressBar pbProgress = row.findViewById(R.id.pb_task_progress);
                Button btnCompletedLabel = row.findViewById(R.id.btn_quest_completed_label);

                Button btnManualComplete = row.findViewById(R.id.btn_manual_complete);
                ImageButton btnCardShuffle = row.findViewById(R.id.btn_card_shuffle);

                tvTitle.setText(title);
                cbComplete.setVisibility(View.GONE);
                ivAutoTrackedIcon.setVisibility(View.GONE);
                tvTargetPill.setVisibility(View.GONE);
                btnStartAvoidance.setVisibility(View.GONE);
                btnIncrementProgress.setVisibility(View.GONE);
                if (btnManualComplete != null) btnManualComplete.setVisibility(View.GONE);
                if (btnCardShuffle != null) btnCardShuffle.setVisibility(View.GONE);
                pbProgress.setVisibility(View.GONE);
                btnCompletedLabel.setVisibility(View.GONE);

                if (isStepTracked) {
                    tvTarget.setText(currentValue + " / " + target + " " + unit + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                    ivAutoTrackedIcon.setVisibility(View.VISIBLE);
                    pbProgress.setVisibility(View.VISIBLE);
                    pbProgress.setMax(target);
                    pbProgress.setProgress(currentValue);

                    // Show distance pill (rough estimation: 1 step = 0.00075 km)
                    double km = target * 0.00075;
                    tvTargetPill.setText(String.format(java.util.Locale.getDefault(), "%.1f km", km));
                    tvTargetPill.setVisibility(View.VISIBLE);
                } else if (isIncrementTracked) {
                    tvTarget.setText(currentValue + " / " + target + " " + unit + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                    pbProgress.setVisibility(View.VISIBLE);
                    pbProgress.setMax(target);
                    pbProgress.setProgress(currentValue);
                    if (isCompleted == 0) {
                        btnIncrementProgress.setVisibility(View.VISIBLE);
                        btnIncrementProgress.setOnClickListener(v -> {
                            TaskManager taskManager = new TaskManager(DashboardActivity.this);
                            taskManager.incrementQuestProgress(taskId);
                            populateQuestLists(activeContainer, completedContainer);
                        });
                    }
                } else if (isAvoidanceTracked) {
                    if (startTimestamp <= 0) {
                        tvTarget.setText("Goal: " + TaskManager.formatDurationMinutes(target) + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                        btnStartAvoidance.setVisibility(View.VISIBLE);
                        btnStartAvoidance.setOnClickListener(v -> {
                            TaskManager taskManager = new TaskManager(DashboardActivity.this);
                            taskManager.startAvoidanceQuest(taskId);
                            startAvoidanceServiceIfNeeded();
                            populateQuestLists(activeContainer, completedContainer);
                        });
                    } else {
                        int remainingMinutes = Math.max(target - currentValue, 0);
                        tvTarget.setText(TaskManager.formatDurationMinutes(remainingMinutes) + " remaining | " + rewardXp + " XP / " + rewardGold + " Gold");
                        pbProgress.setVisibility(View.VISIBLE);
                        pbProgress.setMax(target);
                        pbProgress.setProgress(currentValue);
                    }
                } else {
                    String targetText = "minutes".equalsIgnoreCase(unit) ? TaskManager.formatDurationMinutes(target) : target + " " + unit;
                    tvTarget.setText("Goal: " + targetText + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                    if (isCompleted == 0 && btnManualComplete != null) {
                        btnManualComplete.setVisibility(View.VISIBLE);
                        btnManualComplete.setOnClickListener(v -> {
                            TaskManager taskManager = new TaskManager(DashboardActivity.this);
                            taskManager.completeTask(taskId);
                            populateQuestLists(activeContainer, completedContainer);
                        });
                    }
                }

                if (btnCardShuffle != null) {
                    btnCardShuffle.setVisibility(View.GONE);
                }

                if (isCompleted == 1) {
                    tvTitle.setTextColor(Color.GRAY);
                    tvTarget.setTextColor(Color.GRAY);
                    row.setAlpha(0.6f);
                    if (btnManualComplete != null) btnManualComplete.setVisibility(View.GONE);
                    if (btnCardShuffle != null) btnCardShuffle.setVisibility(View.GONE);
                    btnStartAvoidance.setVisibility(View.GONE);
                    btnIncrementProgress.setVisibility(View.GONE);
                    btnCompletedLabel.setVisibility(View.VISIBLE);
                    btnCompletedLabel.setOnClickListener(v -> {
                        new androidx.appcompat.app.AlertDialog.Builder(DashboardActivity.this, R.style.DaGoalDialogTheme)
                                .setTitle("Undo Quest Completion?")
                                .setMessage("Would you like to undo completing this quest? Doing so will revert its progress and refund its rewards.")
                                .setPositiveButton("Undo", (dialog, which) -> {
                                    SoundEffectsHelper.playButton(DashboardActivity.this);
                                    TaskManager taskManager = new TaskManager(DashboardActivity.this);
                                    taskManager.uncompleteTask(taskId, rewardGold, rewardXp);
                                    updateChestBarUI();
                                    populateQuestLists(activeContainer, completedContainer);
                                    ToastUtils.showToast(DashboardActivity.this, "Quest un-completed. Reverted rewards.");
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    });
                    completedContainer.addView(row);
                } else {
                    uncompletedCount++;
                    activeContainer.addView(row);
                }
            }
            cursor.close();

            // Update Empty State visibility
            if (tvEmpty != null) {
                tvEmpty.setVisibility(totalCount == 0 ? View.VISIBLE : View.GONE);
            }

            if (tvHeader != null) {
                tvHeader.setText(getString(R.string.quests_left_today, uncompletedCount));
            }
            if (tvCustomCounter != null) {
                TaskManager customManager = new TaskManager(this);
                int level = getCurrentUserLevel();
                int allowance = TaskManager.getCustomQuestAllowance(level);
                int used = customManager.getCustomQuestsUsedThisWeek();
                int remainingSlots = Math.max(allowance - used, 0);
                tvCustomCounter.setText(remainingSlots + "/" + allowance + " Custom Slots");
                tvCustomCounter.setOnClickListener(v -> showCustomQuestsInfoDialog());
            }
        }
    }

    private void resetTabColors() {
        setTabStyle(navQuest, false);
        setTabStyle(navWardrobe, false);
        setTabStyle(navShop, false);
        setTabStyle(navMe, false);
    }

    private void highlightTab(LinearLayout layout) {
        setTabStyle(layout, true);
    }

    private void setTabStyle(LinearLayout layout, boolean isActive) {
        ImageView icon = (ImageView) layout.getChildAt(0);
        TextView text = (TextView) layout.getChildAt(1);

        int primaryColor = ContextCompat.getColor(this, R.color.dagoal_primary);

        if (isActive) {
            layout.setBackgroundResource(R.drawable.bg_nav_item_selected);
            text.setTextColor(primaryColor);
            text.setTypeface(null, Typeface.BOLD);
            icon.setColorFilter(primaryColor);
            icon.setAlpha(1.0f);
        } else {
            layout.setBackgroundResource(R.drawable.bg_nav_item_inactive);
            text.setTextColor(primaryColor);
            text.setTypeface(null, Typeface.NORMAL);
            icon.setColorFilter(primaryColor);
            icon.setAlpha(0.6f);
        }
    }
}