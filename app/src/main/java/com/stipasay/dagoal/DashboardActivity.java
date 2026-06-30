package com.stipasay.dagoal;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class DashboardActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private LinearLayout navQuest, navWardrobe, navShop, navMe;
    private DatabaseHelper dbHelper;

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

        // SYSTEM OVERLAY BUGFIX: Safe padding calculation step for system device control bar heights
        View rootLayout = findViewById(R.id.root_dashboard_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        // Set Tab Click Listeners
        navQuest.setOnClickListener(v -> selectTab("QUEST"));
        navWardrobe.setOnClickListener(v -> selectTab("WARDROBE"));
        navShop.setOnClickListener(v -> selectTab("SHOP"));
        navMe.setOnClickListener(v -> selectTab("ME"));

        // Default to loading the Quest view layer immediately upon landing
        selectTab("QUEST");
    }

    private void selectTab(String tabName) {
        // Reset navigation highlights across panels
        resetTabColors();

        // Clear the active content container
        contentFrame.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // Switch screens based on the selected tab keyword
        switch (tabName) {
            case "QUEST":
                highlightTab(navQuest);
                View questView = inflater.inflate(R.layout.view_dashboard_quest, contentFrame, false);
                contentFrame.addView(questView);

                // Populate active database tasks into quest container
                LinearLayout tasksContainer = questView.findViewById(R.id.container_active_dashboard_quests);
                populateQuestList(tasksContainer);
                break;

            case "WARDROBE":
                highlightTab(navWardrobe);
                View wardrobeView = inflater.inflate(R.layout.view_dashboard_wardrobe, contentFrame, false);
                contentFrame.addView(wardrobeView);

                // Grid data binding will be wired here later
                break;

            case "SHOP":
                highlightTab(navShop);
                View shopView = inflater.inflate(R.layout.view_dashboard_shop, contentFrame, false);
                contentFrame.addView(shopView);

                // Gold balance display and grid handling will be wired here later
                break;

            case "ME":
                highlightTab(navMe);
                View meView = inflater.inflate(R.layout.view_dashboard_me, contentFrame, false);
                contentFrame.addView(meView);

                // Level stats and achievement array loops will be wired here later
                break;
        }
    }

    private void populateQuestList(LinearLayout container) {
        container.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] columns = {
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_UNIT
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                columns, null, null, null, null, null
        );

        if (cursor != null) {
            int taskCount = 0;
            while (cursor.moveToNext()) {
                taskCount++;
                String title = cursor.getString(0);
                int target = cursor.getInt(1);
                String unit = cursor.getString(2);

                // Use subcomponent view rows matching the layout items panel styling
                View row = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, container, false);
                TextView tvTitle = row.findViewById(R.id.tv_task_title);
                TextView tvTarget = row.findViewById(R.id.tv_task_target);
                View btnShuffleIcon = row.findViewById(R.id.btn_shuffle_item);

                // Hide the raw alternate shuffle toggle icon from displaying inside the dashboard list view area panel node
                btnShuffleIcon.setVisibility(View.GONE);

                tvTitle.setText(title);
                tvTarget.setText("Goal: " + target + " " + unit);

                container.addView(row);
            }
            cursor.close();

            // Dynamic header counter calculation text field adjustment state check step
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