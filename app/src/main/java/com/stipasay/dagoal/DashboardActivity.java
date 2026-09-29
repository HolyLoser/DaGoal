package com.stipasay.dagoal;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
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
    private LinearLayout currentActiveQuestContainer;
    private LinearLayout currentCompletedQuestContainer;
    private Handler avoidanceTickHandler = new Handler(Looper.getMainLooper());
    private Runnable avoidanceTickRunnable;

    private ShopItem selectedShopItem = null;
    private ShopItem selectedWardrobeItem = null;

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
                updateGlobalAvatarHeader();
                if (currentActiveQuestContainer != null && currentCompletedQuestContainer != null) {
                    populateQuestLists(currentActiveQuestContainer, currentCompletedQuestContainer);
                }
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

    private void showManualGoalDialog(int level, LinearLayout activeContainer, LinearLayout completedContainer) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_custom_quest, null);
        android.widget.EditText editTitle = dialogView.findViewById(R.id.edit_custom_quest_title);
        android.widget.EditText editTarget = dialogView.findViewById(R.id.edit_custom_quest_target);
        android.widget.EditText editUnitLabel = dialogView.findViewById(R.id.edit_custom_quest_unit_label);
        android.widget.Spinner spinnerUnitType = dialogView.findViewById(R.id.spinner_custom_quest_unit_type);
        android.widget.Spinner spinnerDurationUnit = dialogView.findViewById(R.id.spinner_custom_quest_duration_unit);
        LinearLayout rowTime = dialogView.findViewById(R.id.row_custom_quest_time);
        TextView tvTimeValue = dialogView.findViewById(R.id.tv_custom_quest_time_value);
        LinearLayout rowRepeat = dialogView.findViewById(R.id.row_custom_quest_repeat);
        TextView tvRepeatValue = dialogView.findViewById(R.id.tv_custom_quest_repeat_value);
        LinearLayout rowRepeatEnds = dialogView.findViewById(R.id.row_custom_quest_repeat_ends);
        TextView tvRepeatEndsValue = dialogView.findViewById(R.id.tv_custom_quest_repeat_ends_value);
        TextView tvRewardPreview = dialogView.findViewById(R.id.tv_custom_quest_reward_preview);
        android.widget.SeekBar seekBarGold = dialogView.findViewById(R.id.seekbar_custom_quest_gold);
        Button btnCreate = dialogView.findViewById(R.id.btn_create_custom_quest);

        android.widget.ArrayAdapter<String> unitAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{ "Tap to complete", "Steps", "Repetition", "Duration" });
        spinnerUnitType.setAdapter(unitAdapter);

        android.widget.ArrayAdapter<String> durationUnitAdapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                new String[]{ "Minutes", "Hours", "Seconds" });
        spinnerDurationUnit.setAdapter(durationUnitAdapter);

        spinnerUnitType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selection = (String) parent.getItemAtPosition(position);

                editTarget.setVisibility(View.VISIBLE);
                editUnitLabel.setVisibility(View.GONE);
                spinnerDurationUnit.setVisibility(View.GONE);

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
                        editTarget.setHint("Amount of time");
                        spinnerDurationUnit.setVisibility(View.VISIBLE);
                        break;
                }
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        int[] selectedTimeMinutes = { 0 };
        int[] selectedRepeatInterval = { 0 };
        String[] selectedRepeatUnit = { "" };
        boolean[] selectedWeekdays = new boolean[7];
        String[] selectedRepeatEndType = { "" };
        String[] selectedRepeatEndValue = { "" };

        rowTime.setOnClickListener(v -> {
            java.util.Calendar cal = java.util.Calendar.getInstance();
            android.app.TimePickerDialog timePickerDialog = new android.app.TimePickerDialog(
                    this,
                    (view, hourOfDay, minute) -> {
                        selectedTimeMinutes[0] = hourOfDay * 60 + minute;
                        tvTimeValue.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute));
                    },
                    cal.get(java.util.Calendar.HOUR_OF_DAY),
                    cal.get(java.util.Calendar.MINUTE),
                    true
            );
            timePickerDialog.show();
        });

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
                } else {
                    unitLabel = ((String) spinnerDurationUnit.getSelectedItem()).toLowerCase(Locale.getDefault());
                    unitType = DatabaseContract.DailyTaskEntry.UNIT_TYPE_DURATION;
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

            int goldPicked = goldMin + seekBarGold.getProgress();

            TaskManager taskManager = new TaskManager(this);
            boolean success = taskManager.createCustomQuest(
                    title, target, unitLabel, goldPicked, level,
                    unitType, selectedRepeatInterval[0], selectedRepeatUnit[0],
                    weekdaysBuilder.toString(), selectedRepeatEndType[0], selectedRepeatEndValue[0]
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


    private void selectTab(String tabName) {
        SoundEffectsHelper.playHighlight(this);
        resetTabColors();
        contentFrame.removeAllViews();
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

        switch (tabName) {
            case "QUEST":
                highlightTab(navQuest);
                View questView = inflater.inflate(R.layout.view_dashboard_quest, contentFrame, false);
                contentFrame.addView(questView);
                setupDragHandle(questView);

                LinearLayout activeContainer = questView.findViewById(R.id.container_active_dashboard_quests);
                LinearLayout completedContainer = questView.findViewById(R.id.container_completed_dashboard_quests);
                currentActiveQuestContainer = activeContainer;
                currentCompletedQuestContainer = completedContainer;

                TaskManager tickManager = new TaskManager(this);
                tickManager.checkAndCompleteAvoidanceQuests();

                populateQuestLists(activeContainer, completedContainer);
                updateChestBarUI();
                checkAndShowChestTierPrompt();
                startAvoidanceTicker();

                Button btnAddGoal = questView.findViewById(R.id.btn_dashboard_add_goal);
                if (btnAddGoal != null) {
                    btnAddGoal.setOnClickListener(v -> showAddGoalChooserDialog(activeContainer, completedContainer));
                }
                break;

            case "WARDROBE":
                highlightTab(navWardrobe);
                View wardrobeView = inflater.inflate(R.layout.view_dashboard_wardrobe, contentFrame, false);
                contentFrame.addView(wardrobeView);

                Button btnEquipAction = wardrobeView.findViewById(R.id.btn_wardrobe_action);
                android.widget.GridView gridWardrobeItems = wardrobeView.findViewById(R.id.grid_wardrobe_items);

                btnEquipAction.setText("Equip Item");
                btnEquipAction.setVisibility(View.GONE);

                refreshWardrobeAvatarPreview(wardrobeView, btnEquipAction);

                TaskManager wardrobeManager = new TaskManager(this);
                java.util.List<ShopItem> ownedList = wardrobeManager.getOwnedItems();

                gridWardrobeItems.setAdapter(new android.widget.BaseAdapter() {
                    @Override
                    public int getCount() { return ownedList.size(); }
                    @Override
                    public Object getItem(int position) { return ownedList.get(position); }
                    @Override
                    public long getItemId(int position) { return ownedList.get(position).getId(); }
                    @Override
                    public View getView(int position, View convertView, android.view.ViewGroup parent) {
                        if (convertView == null) {
                            convertView = LayoutInflater.from(DashboardActivity.this).inflate(R.layout.item_shop_grid, parent, false);
                        }
                        ShopItem item = ownedList.get(position);

                        View badgeBg = convertView.findViewById(R.id.view_shop_badge_bg);
                        TextView tvEmoji = convertView.findViewById(R.id.tv_shop_item_emoji);
                        View lockOverlay = convertView.findViewById(R.id.view_shop_lock_overlay);
                        ImageView ivLockIcon = convertView.findViewById(R.id.iv_shop_lock_icon);
                        TextView tvName = convertView.findViewById(R.id.tv_shop_item_name);
                        TextView tvMeta = convertView.findViewById(R.id.tv_shop_item_meta);

                        int tierColor = getShopTierColor(item.getRarityTier());
                        androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(tierColor));

                        tvEmoji.setText(item.getIconEmoji());
                        tvName.setText(item.getName());

                        ShopItem currentlyEquipped = TaskManager.getEquippedItem(DashboardActivity.this);
                        boolean isEquipped = (currentlyEquipped != null && currentlyEquipped.getId() == item.getId());

                        if (isEquipped) {
                            tvMeta.setText("EQUIPPED");
                            tvMeta.setTextColor(android.graphics.Color.parseColor("#546B41"));
                        } else {
                            tvMeta.setText(item.getRarityTier());
                            tvMeta.setTextColor(tierColor);
                        }

                        lockOverlay.setVisibility(View.GONE);
                        ivLockIcon.setVisibility(View.GONE);

                        convertView.setOnClickListener(v -> {
                            selectedWardrobeItem = item;
                            btnEquipAction.setVisibility(View.VISIBLE);
                            ShopItem eq = TaskManager.getEquippedItem(DashboardActivity.this);
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
                        ShopItem currentlyEquipped = TaskManager.getEquippedItem(this);
                        if (currentlyEquipped != null && currentlyEquipped.getId() == selectedWardrobeItem.getId()) {
                            TaskManager.setEquippedItem(this, null);
                            ToastUtils.showToast(this, "Unequipped: " + selectedWardrobeItem.getName());
                        } else {
                            TaskManager.setEquippedItem(this, selectedWardrobeItem);
                            ToastUtils.showToast(this, "Equipped: " + selectedWardrobeItem.getName());
                        }
                        AvatarCompositor.clearCache();
                        refreshWardrobeAvatarPreview(wardrobeView, btnEquipAction);
                        updateGlobalAvatarHeader();
                        if (gridWardrobeItems.getAdapter() != null) {
                            ((android.widget.BaseAdapter) gridWardrobeItems.getAdapter()).notifyDataSetChanged();
                        }
                    }
                });
                break;

            case "SHOP":
                highlightTab(navShop);
                View shopView = inflater.inflate(R.layout.view_dashboard_shop, contentFrame, false);
                contentFrame.addView(shopView);

                TextView tvShopGoldBalance = shopView.findViewById(R.id.tv_shop_gold_balance);
                TextView tvStatusBadge = shopView.findViewById(R.id.tv_shop_status_badge);
                Button btnRefreshShop = shopView.findViewById(R.id.btn_refresh_shop);
                Button btnPurchaseAction = shopView.findViewById(R.id.btn_shop_action);
                android.widget.GridView gridShopItems = shopView.findViewById(R.id.grid_shop_items);

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
                            int cost = shopManager.getShopRefreshCost(DashboardActivity.this);
                            if (cost == 0) {
                                btnRefreshShop.setText("🔄 Refresh (Token)");
                                btnRefreshShop.setEnabled(true);
                            } else if (cost > 0) {
                                btnRefreshShop.setText("🔄 Refresh (" + cost + "g)");
                                btnRefreshShop.setEnabled(true);
                            } else {
                                btnRefreshShop.setText("🔄 Maxed (5/5)");
                                btnRefreshShop.setEnabled(false);
                            }
                        }
                    }
                };
                updateRefreshButtonState.run();

                java.util.List<ShopItem> shopList = OnlineShopManager.getDynamicShopItems(this);

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
                        View lockOverlay = convertView.findViewById(R.id.view_shop_lock_overlay);
                        ImageView ivLockIcon = convertView.findViewById(R.id.iv_shop_lock_icon);
                        TextView tvName = convertView.findViewById(R.id.tv_shop_item_name);
                        TextView tvMeta = convertView.findViewById(R.id.tv_shop_item_meta);

                        int tierColor = getShopTierColor(item.getRarityTier());
                        androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(tierColor));

                        tvEmoji.setText(item.getIconEmoji());
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
                View meView = inflater.inflate(R.layout.view_dashboard_me, contentFrame, false);
                contentFrame.addView(meView);
                loadMeTabDataData(meView);
                populateAchievementsList(meView);
                wireSettingsButton(meView);
                break;
        }
    }

    private void updateGlobalAvatarHeader() {
        if (avatarHostContainer != null) {
            AvatarCompositor.clearCache();
            AvatarHelper.renderUserAvatar(this, avatarHostContainer);
        } else {
            FrameLayout host = findViewById(R.id.avatar_host_container);
            if (host != null) {
                AvatarCompositor.clearCache();
                AvatarHelper.renderUserAvatar(this, host);
            }
        }
        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();
        if (profileCursor != null && profileCursor.moveToFirst()) {
            int level = profileCursor.getInt(1);
            int gold = profileCursor.getInt(2);
            int xp = profileCursor.getInt(3);

            if (tvGlobalLevel != null) {
                tvGlobalLevel.setText(getString(R.string.lvl_placeholder, level));
            }
            if (tvGlobalGold != null) {
                tvGlobalGold.setText(getString(R.string.gold_placeholder, gold));
            }
            if (tvGlobalXp != null) {
                tvGlobalXp.setText(getString(R.string.xp_placeholder, xp, 100));
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
    }

    private void refreshWardrobeAvatarPreview(View wardrobeView, Button btnEquipAction) {
        if (wardrobeView == null) return;
        AvatarCompositor.clearCache();
        FrameLayout avatarHost = wardrobeView.findViewById(R.id.avatar_me_host);
        if (avatarHost != null) {
            AvatarHelper.renderUserAvatarFaceOnly(this, avatarHost);
        }

        TextView tvName = wardrobeView.findViewById(R.id.tv_equipped_item_name);
        TextView tvStatus = wardrobeView.findViewById(R.id.tv_equipped_item_status);

        ShopItem equipped = TaskManager.getEquippedItem(this);
        if (equipped != null) {
            if (tvName != null) tvName.setText("Equipped: " + equipped.getName());
            if (tvStatus != null) tvStatus.setText(equipped.getRarityTier() + " • Tap an item below to change");
            if (btnEquipAction != null && selectedWardrobeItem != null && selectedWardrobeItem.getId() == equipped.getId()) {
                btnEquipAction.setText("Unequip " + equipped.getName());
            }
        } else {
            if (tvName != null) tvName.setText("Equipped: None");
            if (tvStatus != null) tvStatus.setText("Select an item below to equip");
            if (btnEquipAction != null && selectedWardrobeItem != null) {
                btnEquipAction.setText("Equip " + selectedWardrobeItem.getName());
            }
        }
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

            if (tvProfileUsername != null) tvProfileUsername.setText(username + " • " + title);
            if (tvProfileLevel != null) tvProfileLevel.setText(getString(R.string.level_info, level, xp, 100, gold));
            profileCursor.close();
        }

        View cardStreak = meView.findViewById(R.id.card_streak);
        if (cardStreak != null) {
            cardStreak.setOnClickListener(v -> startActivity(new Intent(this, StreakInfoActivity.class)));
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
        int streakProtectors = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR);
        int xpBoosts = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
        int goldBoosts = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);

        String currentDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        boolean xpActive = TaskManager.isXpBoostActive(this, currentDateStr);
        boolean goldActive = TaskManager.isGoldBoostActive(this, currentDateStr);

        StringBuilder sb = new StringBuilder();
        if (xpActive || goldActive) {
            sb.append("Active today: ");
            if (xpActive) sb.append("⚡ 2x XP ");
            if (goldActive) sb.append("🪙 2x Gold");
            sb.append(" • ");
        }
        int totalQty = streakProtectors + xpBoosts + goldBoosts;
        sb.append(totalQty).append(" item(s) in bag");

        tvConsumablesSummary.setText(sb.toString());
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

        TextView tvQtyStreak = dialogView.findViewById(R.id.tv_qty_streak_protector);
        TextView tvQtyXp = dialogView.findViewById(R.id.tv_qty_xp_boost);
        TextView tvQtyGold = dialogView.findViewById(R.id.tv_qty_gold_boost);
        Button btnActivateXp = dialogView.findViewById(R.id.btn_activate_xp_boost);
        Button btnActivateGold = dialogView.findViewById(R.id.btn_activate_gold_boost);
        Button btnClose = dialogView.findViewById(R.id.btn_close_consumables);

        Runnable refreshDialogUI = new Runnable() {
            @Override
            public void run() {
                int qtyStreak = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_STREAK_PROTECTOR);
                int qtyXp = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_XP_BOOST);
                int qtyGold = taskManager.getConsumableQuantity(DatabaseContract.InventoryConsumableEntry.TYPE_GOLD_BOOST);

                boolean xpActive = TaskManager.isXpBoostActive(DashboardActivity.this, currentDateStr);
                boolean goldActive = TaskManager.isGoldBoostActive(DashboardActivity.this, currentDateStr);

                if (tvQtyStreak != null) tvQtyStreak.setText("x" + qtyStreak);
                if (tvQtyXp != null) tvQtyXp.setText("Available: x" + qtyXp);
                if (tvQtyGold != null) tvQtyGold.setText("Available: x" + qtyGold);

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

                    if (rankIndex < 0) {
                        tvRankLabel.setText("Unranked");
                        tvRankLabel.setTextColor(Color.parseColor("#A0AEC0"));
                    } else {
                        tvRankLabel.setText(AchievementTierHelper.RANK_NAMES[rankIndex] + " " + (rankIndex + 1));
                        tvRankLabel.setTextColor(badgeColor);
                    }

                    convertView.setOnClickListener(v -> showAchievementDetailDialog(title, desc, currentProgress, baseTarget));

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
                                       boolean isCustom, boolean isCompleted,
                                       LinearLayout activeContainer, LinearLayout completedContainer) {
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

        String unitText = "minutes".equalsIgnoreCase(unit) ? TaskManager.formatDurationMinutes(target) : target + " " + unit;
        tvDescription.setText("Goal: " + unitText);

        String tierLabel = difficultyTier == null || difficultyTier.isEmpty() ? "Custom" :
                (difficultyTier.substring(0, 1).toUpperCase(Locale.getDefault()) + difficultyTier.substring(1).toLowerCase(Locale.getDefault()));
        tvDifficulty.setText("Quest Difficulty: " + tierLabel);
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

    private void showAchievementDetailDialog(String title, String description, int currentProgress, int baseTarget) {
        SoundEffectsHelper.playMenuOpen(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_achievement_detail, null);

        View badgeBg = dialogView.findViewById(R.id.view_dialog_badge_bg);
        TextView tvDesc = dialogView.findViewById(R.id.tv_dialog_achievement_desc);
        TextView tvRankName = dialogView.findViewById(R.id.tv_dialog_rank_name);
        android.widget.ProgressBar pbProgress = dialogView.findViewById(R.id.pb_dialog_rank_progress);
        TextView tvProgressLabel = dialogView.findViewById(R.id.tv_dialog_progress_label);

        int badgeColor = AchievementTierHelper.getBadgeColor(currentProgress, baseTarget);
        androidx.core.view.ViewCompat.setBackgroundTintList(badgeBg, android.content.res.ColorStateList.valueOf(badgeColor));

        tvDesc.setText(title + " — " + description);
        tvRankName.setText(AchievementTierHelper.getRankName(currentProgress, baseTarget));
        pbProgress.setProgress(AchievementTierHelper.getProgressPercentToNextRank(currentProgress, baseTarget));

        if (AchievementTierHelper.isMaxRank(currentProgress, baseTarget)) {
            tvProgressLabel.setText("Max rank reached!");
        } else {
            int remaining = AchievementTierHelper.getRemainingToNextRank(currentProgress, baseTarget);
            tvProgressLabel.setText(remaining + " more to reach next rank");
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

        TextView tvIcon = findViewById(R.id.tv_chest_bar_icon);
        TextView tvPoints = findViewById(R.id.tv_chest_bar_points);
        View viewFill = findViewById(R.id.view_chest_bar_fill);
        View viewTrack = findViewById(R.id.view_chest_bar_track);

        int points = TaskManager.getChestBarPoints(this);
        int tier = TaskManager.getChestBarCurrentTier(this);
        int currentTarget = TaskManager.getChestBarTargetPoints(this);

        if (tvPoints != null) {
            tvPoints.setText(points + "/" + currentTarget);
        }

        if (tvIcon != null) {
            tvIcon.setText("🎁");
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

    private void checkAndShowChestTierPrompt() {
        int points = TaskManager.getChestBarPoints(this);
        int tier = TaskManager.getChestBarCurrentTier(this);
        int target = TaskManager.getChestBarTargetPoints(this);

        if (points >= target) {
            if (tier >= 3) {
                TaskManager.claimChestTier(this, 3);
                updateChestBarUI();
            } else {
                showChestTierDialog(tier);
            }
        }
    }

    private void showChestTierDialog(int tier) {
        SoundEffectsHelper.playMenuOpen(this);
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chest_tier_prompt, null);
        builder.setView(dialogView);

        android.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tv_chest_dialog_title);
        TextView tvLoot = dialogView.findViewById(R.id.tv_chest_dialog_loot);
        Button btnUnlock = dialogView.findViewById(R.id.btn_unlock_chest_now);
        Button btnKeepGoing = dialogView.findViewById(R.id.btn_keep_completing_quests);

        if (tier == 1) {
            if (tvTitle != null) tvTitle.setText("🪵 Tier 1 Wooden Chest Ready!");
            if (tvLoot != null) tvLoot.setText("Unlock now for +30 Gold and +20 XP, or decline and push for 5 new quests to reach Tier 2 Silver Chest!");
        } else if (tier == 2) {
            if (tvTitle != null) tvTitle.setText("🪙 Tier 2 Silver Chest Ready!");
            if (tvLoot != null) tvLoot.setText("Unlock now for +80 Gold, +60 XP, and 1 XP Boost, or decline and push for 8 new quests to reach Tier 3 Gold Chest!");
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                SoundEffectsHelper.playCoin(DashboardActivity.this);
                TaskManager.claimChestTier(DashboardActivity.this, tier);
                dialog.dismiss();
                updateChestBarUI();
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
        if (parentView != null && parentView.getParent() != null) {
            tvHeader = ((View)parentView.getParent()).findViewById(R.id.tv_quests_remaining_header);
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
                DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection,
                null, null, null, null, null
        );

        int totalCount = 0;
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

                boolean isStepTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS.equals(questType);
                boolean isAvoidanceTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_SCREEN_AVOID.equals(questType);
                boolean isIncrementTracked = DatabaseContract.DailyTaskEntry.QUEST_TYPE_INCREMENT.equals(questType);

                int isCustomFlag = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_IS_CUSTOM));
                boolean isCustomQuest = isCustomFlag == 1;
                String difficultyTier = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_DIFFICULTY_TIER));
                boolean rowCompleted = isCompleted == 1;

                View row = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, (isCompleted == 1) ? completedContainer : activeContainer, false);
                row.setOnClickListener(v -> showQuestDetailDialog(
                        taskId, title, target, unit, questType, difficultyTier, currentValue,
                        rewardGold, rewardXp, isCustomQuest, rowCompleted, activeContainer, completedContainer
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

                tvTitle.setText(title);
                cbComplete.setVisibility(View.GONE);
                ivAutoTrackedIcon.setVisibility(View.GONE);
                tvTargetPill.setVisibility(View.GONE);
                btnStartAvoidance.setVisibility(View.GONE);
                btnIncrementProgress.setVisibility(View.GONE);
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
                    cbComplete.setVisibility(View.VISIBLE);
                }

                if (isCompleted == 1) {
                    tvTitle.setTextColor(Color.GRAY);
                    tvTarget.setTextColor(Color.GRAY);
                    row.setAlpha(0.6f);
                    cbComplete.setVisibility(View.GONE);
                    btnStartAvoidance.setVisibility(View.GONE);
                    btnIncrementProgress.setVisibility(View.GONE);
                    btnCompletedLabel.setVisibility(View.VISIBLE);
                    completedContainer.addView(row);
                } else {
                    uncompletedCount++;
                    cbComplete.setChecked(false);

                    if (!isStepTracked && !isAvoidanceTracked && !isIncrementTracked) {
                        cbComplete.setEnabled(true);
                        cbComplete.setClickable(true);
                        cbComplete.setOnClickListener(v -> {
                            TaskManager taskManager = new TaskManager(DashboardActivity.this);
                            taskManager.completeTask(taskId);
                            populateQuestLists(activeContainer, completedContainer);
                        });
                    }
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