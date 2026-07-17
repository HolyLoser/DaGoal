package com.stipasay.dagoal;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.widget.CheckBox;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
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
    private TextView tvGlobalLevel;
    private View panelAvatarHost;
    private View rootLayout;
    private View bottomNavBar;
    private int contentFrameHeightPx;

    private ActivityResultLauncher<String[]> stepPermissionLauncher;
    private BroadcastReceiver taskProgressReceiver;
    private LinearLayout currentActiveQuestContainer;
    private LinearLayout currentCompletedQuestContainer;
    private Handler avoidanceTickHandler = new Handler(Looper.getMainLooper());
    private Runnable avoidanceTickRunnable;

    private ShopItem selectedShopItem = null;
    private ShopItem selectedWardrobeItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        dbHelper = new DatabaseHelper(this);
        contentFrame = findViewById(R.id.dashboard_content_frame);
        contentFrameHeightPx = contentFrame.getLayoutParams().height;

        navQuest = findViewById(R.id.nav_quest);
        navWardrobe = findViewById(R.id.nav_wardrobe);
        navShop = findViewById(R.id.nav_shop);
        navMe = findViewById(R.id.nav_me);

        imgGlobalAvatar = findViewById(R.id.img_global_dashboard_avatar);
        tvGlobalLevel = findViewById(R.id.tv_global_dashboard_lvl);
        panelAvatarHost = findViewById(R.id.panel_avatar_host);
        bottomNavBar = findViewById(R.id.bottom_nav_bar);

        rootLayout = findViewById(R.id.root_dashboard_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), 0, v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        stepPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> checkAndRequestStepPermissions()
        );

        taskProgressReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
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
        startAvoidanceServiceIfNeeded();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(taskProgressReceiver);
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

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
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
        if (!AppMonitorService.hasUsageAccess(this) || !android.provider.Settings.canDrawOverlays(this)) {
            return;
        }

        TaskManager taskManager = new TaskManager(this);
        if (!taskManager.getStartedAvoidanceQuests().isEmpty()) {
            AppMonitorService.start(this);
        }
    }
    private void checkAndRequestStepPermissions() {
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
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        String lastPopupDate = prefs.getString("last_streak_popup_date", "");

        if (!todayDateStr.equals(lastPopupDate)) {
            Intent intent = new Intent(this, StreakActivity.class);
            startActivity(intent);
        }
    }

    private void animateContentFrameHeight(int targetHeightPx) {
        CoordinatorLayout.LayoutParams contentParams = (CoordinatorLayout.LayoutParams) contentFrame.getLayoutParams();
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
                CoordinatorLayout.LayoutParams contentParams = (CoordinatorLayout.LayoutParams) contentFrame.getLayoutParams();
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
        resetTabColors();
        contentFrame.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        updateGlobalAvatarHeader();

        currentActiveQuestContainer = null;
        currentCompletedQuestContainer = null;
        stopAvoidanceTicker();

        CoordinatorLayout.LayoutParams contentParams = (CoordinatorLayout.LayoutParams) contentFrame.getLayoutParams();
        float density = getResources().getDisplayMetrics().density;

        if (tabName.equals("ME")) {
            if (panelAvatarHost != null) panelAvatarHost.setVisibility(View.GONE);
            contentParams.height = CoordinatorLayout.LayoutParams.MATCH_PARENT;
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
                startAvoidanceTicker();
                break;

            case "WARDROBE":
                highlightTab(navWardrobe);
                View wardrobeView = inflater.inflate(R.layout.view_dashboard_wardrobe, contentFrame, false);
                contentFrame.addView(wardrobeView);

                Button btnEquipAction = wardrobeView.findViewById(R.id.btn_wardrobe_action);
                android.widget.GridView gridWardrobeItems = wardrobeView.findViewById(R.id.grid_wardrobe_items);

                btnEquipAction.setText("Equip Item");
                btnEquipAction.setVisibility(View.GONE);

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
                            convertView = LayoutInflater.from(DashboardActivity.this).inflate(android.R.layout.simple_list_item_1, parent, false);
                        }
                        ShopItem item = ownedList.get(position);
                        TextView text1 = convertView.findViewById(android.R.id.text1);
                        text1.setText(item.getName());
                        text1.setTextColor(Color.BLACK);
                        text1.setGravity(android.view.Gravity.CENTER);

                        convertView.setOnClickListener(v -> {
                            selectedWardrobeItem = item;
                            btnEquipAction.setVisibility(View.VISIBLE);
                            if (imgGlobalAvatar != null) {
                                if (item.getResName().contains("red")) {
                                    imgGlobalAvatar.setBackgroundColor(Color.RED);
                                } else if (item.getResName().contains("blue")) {
                                    imgGlobalAvatar.setBackgroundColor(Color.BLUE);
                                }
                            }
                        });
                        return convertView;
                    }
                });

                btnEquipAction.setOnClickListener(v -> {
                    if (selectedWardrobeItem != null) {
                        Toast.makeText(this, "Equipped: " + selectedWardrobeItem.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
                break;

            case "SHOP":
                highlightTab(navShop);
                View shopView = inflater.inflate(R.layout.view_dashboard_shop, contentFrame, false);
                contentFrame.addView(shopView);

                TextView tvShopGoldBalance = shopView.findViewById(R.id.tv_shop_gold_balance);
                Button btnPurchaseAction = shopView.findViewById(R.id.btn_shop_action);
                android.widget.GridView gridShopItems = shopView.findViewById(R.id.grid_shop_items);

                btnPurchaseAction.setText("Purchase Item");
                btnPurchaseAction.setVisibility(View.GONE);

                TaskManager shopManager = new TaskManager(this);

                if (tvShopGoldBalance != null) {
                    tvShopGoldBalance.setText("Gold: " + shopManager.getUserGoldBalance());
                }

                java.util.List<ShopItem> shopList = shopManager.getShopItems();

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
                            convertView = LayoutInflater.from(DashboardActivity.this).inflate(android.R.layout.simple_list_item_2, parent, false);
                        }
                        ShopItem item = shopList.get(position);
                        TextView text1 = convertView.findViewById(android.R.id.text1);
                        TextView text2 = convertView.findViewById(android.R.id.text2);

                        text1.setText(item.getName());
                        text1.setTextColor(Color.BLACK);
                        text1.setTypeface(null, Typeface.BOLD);

                        text2.setText(item.getPrice() + " Gold");
                        text2.setTextColor(Color.parseColor("#778A66"));

                        convertView.setOnClickListener(v -> {
                            selectedShopItem = item;
                            btnPurchaseAction.setVisibility(View.VISIBLE);
                            if (imgGlobalAvatar != null) {
                                if (item.getResName().contains("red")) {
                                    imgGlobalAvatar.setBackgroundColor(Color.RED);
                                } else if (item.getResName().contains("blue")) {
                                    imgGlobalAvatar.setBackgroundColor(Color.BLUE);
                                }
                            }
                        });
                        return convertView;
                    }
                });

                btnPurchaseAction.setOnClickListener(v -> {
                    if (selectedShopItem != null) {
                        if (shopManager.purchaseShopItem(selectedShopItem)) {
                            Toast.makeText(this, "Purchased " + selectedShopItem.getName(), Toast.LENGTH_SHORT).show();
                            if (tvShopGoldBalance != null) {
                                tvShopGoldBalance.setText("Gold: " + shopManager.getUserGoldBalance());
                            }
                            btnPurchaseAction.setVisibility(View.GONE);
                        } else {
                            Toast.makeText(this, "Not enough Gold!", Toast.LENGTH_SHORT).show();
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
                break;
        }
    }

    private void updateGlobalAvatarHeader() {
        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();
        if (profileCursor != null && profileCursor.moveToFirst()) {
            int level = profileCursor.getInt(1);
            if (tvGlobalLevel != null) {
                tvGlobalLevel.setText("LVL " + String.format(Locale.getDefault(), "%02d", level));
            }
            profileCursor.close();
        }
    }

    private void loadMeTabDataData(View meView) {
        TextView tvProfileUsername = meView.findViewById(R.id.tv_profile_username);
        TextView tvProfileLevel = meView.findViewById(R.id.tv_profile_level);
        TextView tvProfileStreak = meView.findViewById(R.id.tv_profile_streak);

        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();

        if (profileCursor != null && profileCursor.moveToFirst()) {
            String username = profileCursor.getString(0);
            int level = profileCursor.getInt(1);
            int gold = profileCursor.getInt(2);
            int xp = profileCursor.getInt(3);

            if (tvProfileUsername != null) tvProfileUsername.setText(username);
            if (tvProfileLevel != null) tvProfileLevel.setText("Level " + level + " (" + xp + " / 100 XP) | Gold: " + gold);
            profileCursor.close();
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor streakCursor = db.rawQuery("SELECT streak FROM user WHERE _id = 1", null);
        if (streakCursor != null && streakCursor.moveToFirst()) {
            int streak = streakCursor.getInt(0);
            if (tvProfileStreak != null) tvProfileStreak.setText(streak + " Streak Day(s)");
            streakCursor.close();
        }
    }

    private void populateAchievementsList(View meView) {
        android.widget.ListView listAchievements = meView.findViewById(R.id.list_profile_achievements);
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.query(
                DatabaseContract.AchievementEntry.TABLE_NAME,
                null, null, null, null, null, null
        );

        if (cursor != null) {
            listAchievements.setAdapter(new android.widget.BaseAdapter() {
                @Override
                public int getCount() { return cursor.getCount(); }
                @Override
                public Object getItem(int position) { return null; }
                @Override
                public long getItemId(int position) { return position; }
                @Override
                public View getView(int position, View convertView, android.view.ViewGroup parent) {
                    if (convertView == null) {
                        convertView = LayoutInflater.from(DashboardActivity.this).inflate(R.layout.item_achievement, parent, false);
                    }
                    cursor.moveToPosition(position);

                    String title = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_TITLE));
                    String desc = cursor.getString(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_DESCRIPTION));
                    int currentProgress = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_CURRENT_PROGRESS));
                    int targetValue = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.AchievementEntry.COLUMN_TARGET_VALUE));

                    TextView tvTitle = convertView.findViewById(R.id.tv_achievement_title);
                    TextView tvDesc = convertView.findViewById(R.id.tv_achievement_description);
                    TextView tvProgressFraction = convertView.findViewById(R.id.tv_achievement_progress_text);
                    android.widget.ProgressBar pbProgress = convertView.findViewById(R.id.pb_achievement_progress);

                    tvTitle.setText(title);
                    tvDesc.setText(desc);
                    tvProgressFraction.setText(currentProgress + "/" + targetValue);

                    int progressPercent = 0;
                    if (targetValue > 0) {
                        progressPercent = (int) (((double) currentProgress / targetValue) * 100);
                    }
                    pbProgress.setProgress(Math.min(progressPercent, 100));

                    return convertView;
                }
            });
        }
    }

    private void populateQuestLists(LinearLayout activeContainer, LinearLayout completedContainer) {
        activeContainer.removeAllViews();
        completedContainer.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

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
                DatabaseContract.DailyTaskEntry.COLUMN_START_TIMESTAMP
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection,
                null, null, null, null, null
        );

        if (cursor != null) {
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

                View row = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, null, false);
                TextView tvTitle = row.findViewById(R.id.tv_task_title);
                TextView tvTarget = row.findViewById(R.id.tv_task_target);
                CheckBox cbComplete = row.findViewById(R.id.btn_shuffle_item);
                ImageView ivAutoTrackedIcon = row.findViewById(R.id.iv_auto_tracked_icon);
                Button btnStartAvoidance = row.findViewById(R.id.btn_start_avoidance);

                tvTitle.setText(title);
                cbComplete.setVisibility(View.GONE);
                ivAutoTrackedIcon.setVisibility(View.GONE);
                btnStartAvoidance.setVisibility(View.GONE);

                if (isStepTracked) {
                    tvTarget.setText(currentValue + " / " + target + " " + unit + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                    ivAutoTrackedIcon.setVisibility(View.VISIBLE);
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
                    }
                } else {
                    String targetText = "minutes".equalsIgnoreCase(unit) ? TaskManager.formatDurationMinutes(target) : target + " " + unit;
                    tvTarget.setText("Goal: " + targetText + " | " + rewardXp + " XP / " + rewardGold + " Gold");
                    cbComplete.setVisibility(View.VISIBLE);
                }

                if (isCompleted == 1) {
                    tvTitle.setTextColor(Color.GRAY);
                    tvTarget.setTextColor(Color.GRAY);
                    row.setBackgroundResource(R.drawable.bg_task_card);
                    row.setAlpha(0.6f);
                    cbComplete.setChecked(true);
                    cbComplete.setEnabled(false);
                    btnStartAvoidance.setVisibility(View.GONE);
                    completedContainer.addView(row);
                } else {
                    uncompletedCount++;
                    cbComplete.setChecked(false);

                    if (!isStepTracked && !isAvoidanceTracked) {
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

            TextView tvHeader = findViewById(R.id.tv_quests_remaining_header);
            if (tvHeader != null) {
                tvHeader.setText(uncompletedCount + " Quest(s) left today");
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
        if (isActive) {
            text.setTextColor(Color.WHITE);
            text.setTypeface(null, Typeface.BOLD);
            icon.setAlpha(1.0f);
        } else {
            text.setTextColor(Color.parseColor("#E2E8F0"));
            text.setTypeface(null, Typeface.NORMAL);
            icon.setAlpha(0.6f);
        }
    }
}