package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.widget.Button;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private LinearLayout navQuest, navWardrobe, navShop, navMe;
    private DatabaseHelper dbHelper;

    private ShopItem selectedShopItem = null;
    private ShopItem selectedWardrobeItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        dbHelper = new DatabaseHelper(this);
        contentFrame = findViewById(R.id.dashboard_content_frame);

        navQuest = findViewById(R.id.nav_quest);
        navWardrobe = findViewById(R.id.nav_wardrobe);
        navShop = findViewById(R.id.nav_shop);
        navMe = findViewById(R.id.nav_me);

        View rootLayout = findViewById(R.id.root_dashboard_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        navQuest.setOnClickListener(v -> selectTab("QUEST"));
        navWardrobe.setOnClickListener(v -> selectTab("WARDROBE"));
        navShop.setOnClickListener(v -> selectTab("SHOP"));
        navMe.setOnClickListener(v -> selectTab("ME"));

        if (checkNewDayQuestRouting()) {
            return;
        }

        checkDailyStreakPopup();

        selectTab("QUEST");
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

    private void selectTab(String tabName) {
        resetTabColors();
        contentFrame.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        switch (tabName) {
            case "QUEST":
                highlightTab(navQuest);
                View questView = inflater.inflate(R.layout.view_dashboard_quest, contentFrame, false);
                contentFrame.addView(questView);
                LinearLayout tasksContainer = questView.findViewById(R.id.container_active_dashboard_quests);
                populateQuestList(tasksContainer);
                break;

            case "WARDROBE":
                highlightTab(navWardrobe);
                View wardrobeView = inflater.inflate(R.layout.view_dashboard_wardrobe, contentFrame, false);
                contentFrame.addView(wardrobeView);

                ImageView ivWardrobePreview = wardrobeView.findViewById(R.id.iv_avatar_preview);
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

                        convertView.setOnClickListener(v -> {
                            selectedWardrobeItem = item;
                            btnEquipAction.setVisibility(View.VISIBLE);
                            if (item.getResName().contains("red")) {
                                ivWardrobePreview.setBackgroundColor(Color.RED);
                            } else if (item.getResName().contains("blue")) {
                                ivWardrobePreview.setBackgroundColor(Color.BLUE);
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
                ImageView ivShopPreview = shopView.findViewById(R.id.iv_avatar_preview);
                Button btnPurchaseAction = shopView.findViewById(R.id.btn_shop_action);
                android.widget.GridView gridShopItems = shopView.findViewById(R.id.grid_shop_items);

                btnPurchaseAction.setText("Purchase Item");
                btnPurchaseAction.setVisibility(View.GONE);

                TaskManager shopManager = new TaskManager(this);
                tvShopGoldBalance.setText("Gold: " + shopManager.getUserGoldBalance());

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
                        text2.setText(item.getPrice() + " Gold");

                        convertView.setOnClickListener(v -> {
                            selectedShopItem = item;
                            btnPurchaseAction.setVisibility(View.VISIBLE);
                            if (item.getResName().contains("red")) {
                                ivShopPreview.setBackgroundColor(Color.RED);
                            } else if (item.getResName().contains("blue")) {
                                ivShopPreview.setBackgroundColor(Color.BLUE);
                            }
                        });
                        return convertView;
                    }
                });

                btnPurchaseAction.setOnClickListener(v -> {
                    if (selectedShopItem != null) {
                        if (shopManager.purchaseShopItem(selectedShopItem)) {
                            Toast.makeText(this, "Purchased " + selectedShopItem.getName(), Toast.LENGTH_SHORT).show();
                            tvShopGoldBalance.setText("Gold: " + shopManager.getUserGoldBalance());
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
                break;
        }
    }

    private void loadMeTabDataData(View meView) {
        TextView tvProfileUsername = meView.findViewById(R.id.tv_profile_username);
        TextView tvProfileLevel = meView.findViewById(R.id.tv_profile_level);

        TaskManager profileManager = new TaskManager(this);
        Cursor profileCursor = profileManager.getUserProfile();

        if (profileCursor != null && profileCursor.moveToFirst()) {
            String username = profileCursor.getString(0);
            int level = profileCursor.getInt(1);
            int gold = profileCursor.getInt(2);
            int xp = profileCursor.getInt(3);

            tvProfileUsername.setText(username);
            tvProfileLevel.setText("Level " + level + " (" + xp + " / 100 XP) | Gold: " + gold);
            profileCursor.close();
        }
    }

    private void populateQuestList(LinearLayout container) {
        container.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] projection = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_UNIT,
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                projection,
                null, null, null, null, null
        );

        if (cursor != null) {
            int taskCount = 0;
            while (cursor.moveToNext()) {
                int taskId = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry._ID));
                String title = cursor.getString(1);
                int target = cursor.getInt(2);
                String unit = cursor.getString(3);
                int isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED));

                if (isCompleted == 0) {
                    taskCount++;
                }

                View row = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, container, false);
                TextView tvTitle = row.findViewById(R.id.tv_task_title);
                TextView tvTarget = row.findViewById(R.id.tv_task_target);
                View btnShuffleIcon = row.findViewById(R.id.btn_shuffle_item);

                btnShuffleIcon.setVisibility(View.GONE);
                tvTitle.setText(title);
                tvTarget.setText("Goal: " + target + " " + unit);

                if (isCompleted == 1) {
                    tvTitle.setTextColor(Color.GRAY);
                    tvTarget.setTextColor(Color.GRAY);
                    row.setBackgroundColor(Color.parseColor("#E5E7EB"));
                    row.setClickable(false);
                } else {
                    row.setOnClickListener(v -> {
                        TaskManager taskManager = new TaskManager(DashboardActivity.this);
                        taskManager.completeTask(taskId);
                        populateQuestList(container);
                    });
                }

                container.addView(row);
            }
            cursor.close();

            TextView tvHeader = findViewById(R.id.tv_quests_remaining_header);
            if (tvHeader != null) {
                tvHeader.setText(taskCount + " Quest(s) left today");
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