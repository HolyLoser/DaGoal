package com.stipasay.dagoal;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StreakActivity extends AppCompatActivity {

    private TextView tvDialogStreakCount;
    private Button btnStreakDialogDismiss;
    private View layoutStep1, layoutStep2;
    private LinearLayout option3, option5, option7, option14;
    private Button btnCommitGoal;
    private DatabaseHelper dbHelper;
    private int selectedTarget = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_daily_streak);

        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit().putString("last_streak_popup_date", todayDateStr).commit();

        dbHelper = new DatabaseHelper(this);
        tvDialogStreakCount = findViewById(R.id.tv_dialog_streak_count);
        btnStreakDialogDismiss = findViewById(R.id.btn_streak_dialog_dismiss);
        layoutStep1 = findViewById(R.id.layout_step1_streak);
        layoutStep2 = findViewById(R.id.layout_step2_prediction);

        option3 = findViewById(R.id.option_3_days);
        option5 = findViewById(R.id.option_5_days);
        option7 = findViewById(R.id.option_7_days);
        option14 = findViewById(R.id.option_14_days);
        btnCommitGoal = findViewById(R.id.btn_commit_goal);

        View rootLayout = findViewById(R.id.root_streak_dialog_layout);
        if (rootLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }

        int streakVal = getStreakCount();
        if (tvDialogStreakCount != null) {
            tvDialogStreakCount.setText(String.valueOf(streakVal));
        }
        populateWeeklyStreakRow(streakVal);

        android.widget.FrameLayout avatarContainer = findViewById(R.id.avatar_host_container);
        if (avatarContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarContainer);
        }

        btnStreakDialogDismiss.setOnClickListener(v -> {
            int currentTarget = TaskManager.getStreakPredictionTarget(this);
            if (currentTarget > 0) {
                finish();
            } else {
                showStep2PredictionGoal();
            }
        });

        if (option3 != null) option3.setOnClickListener(v -> selectOption(3));
        if (option5 != null) option5.setOnClickListener(v -> selectOption(5));
        if (option7 != null) option7.setOnClickListener(v -> selectOption(7));
        if (option14 != null) option14.setOnClickListener(v -> selectOption(14));

        if (btnCommitGoal != null) {
            btnCommitGoal.setOnClickListener(v -> {
                TaskManager.setStreakPredictionTarget(this, selectedTarget);
                ToastUtils.showToast(this, "Committed to a " + selectedTarget + "-Day Streak Goal! 🎯");

                int currentStreak = getStreakCount();
                TaskManager.checkAndClaimStreakPredictionReward(this, currentStreak);
                finish();
            });
        }
    }

    private void showStep2PredictionGoal() {
        if (layoutStep1 != null) layoutStep1.setVisibility(View.GONE);
        if (layoutStep2 != null) layoutStep2.setVisibility(View.VISIBLE);
        selectOption(3);
    }

    private void selectOption(int days) {
        this.selectedTarget = days;
        updateOptionStyle(option3, days == 3);
        updateOptionStyle(option5, days == 5);
        updateOptionStyle(option7, days == 7);
        updateOptionStyle(option14, days == 14);
    }

    private void updateOptionStyle(LinearLayout optionView, boolean isSelected) {
        if (optionView == null) return;
        if (isSelected) {
            optionView.setAlpha(1.0f);
            optionView.setElevation(8f);
        } else {
            optionView.setAlpha(0.6f);
            optionView.setElevation(0f);
        }
    }

    private int getStreakCount() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT streak FROM user WHERE _id = 1", null);
        int streak = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                streak = cursor.getInt(0);
            }
            cursor.close();
        }
        return streak;
    }

    private void populateWeeklyStreakRow(int currentStreak) {
        LinearLayout containerWeeklyRow = findViewById(R.id.container_streak_weekly_row);
        if (containerWeeklyRow == null) return;
        containerWeeklyRow.removeAllViews();

        TaskManager taskManager = new TaskManager(this);
        String activeStartDateStr = taskManager.getActiveStreakStartDate();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        java.util.Calendar startCal = java.util.Calendar.getInstance();

        if (activeStartDateStr != null && !activeStartDateStr.isEmpty()) {
            try {
                startCal.setTime(sdf.parse(activeStartDateStr));
            } catch (Exception ignored) {}
        }

        int startDayOfWeek = startCal.get(java.util.Calendar.DAY_OF_WEEK);

        containerWeeklyRow.setClipChildren(false);
        containerWeeklyRow.setClipToPadding(false);

        String[] allWeekdayLabels = { "S", "M", "T", "W", "Th", "F", "S" };
        float density = getResources().getDisplayMetrics().density;
        int chipSizePx = (int) (32 * density);
        int marginPx = (int) (4 * density);

        for (int i = 0; i < 7; i++) {
            int dayIndex = (startDayOfWeek - 1 + i) % 7;
            String label = allWeekdayLabels[dayIndex];

            TextView chip = new TextView(this);
            chip.setText(label);
            chip.setGravity(android.view.Gravity.CENTER);
            chip.setTextColor(android.graphics.Color.WHITE);
            chip.setTextSize(14);
            chip.setTypeface(null, android.graphics.Typeface.BOLD);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(chipSizePx, chipSizePx);
            params.setMargins(marginPx, 0, marginPx, 0);
            chip.setLayoutParams(params);

            boolean isActiveStreakDay = (i < currentStreak);
            if (isActiveStreakDay) {
                chip.setBackgroundResource(R.drawable.bg_calendar_active_border);
            } else {
                chip.setBackgroundResource(0);
            }

            containerWeeklyRow.addView(chip);
        }
    }
}
