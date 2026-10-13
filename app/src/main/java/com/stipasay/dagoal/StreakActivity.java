package com.stipasay.dagoal;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
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

        DebouncedOnClickListener.setSafeOnClickListener(btnStreakDialogDismiss, v -> {
            int currentTarget = TaskManager.getStreakPredictionTarget(this);
            if (currentTarget > 0) {
                finish();
            } else {
                showStep2PredictionGoal();
            }
        });

        if (option3 != null) DebouncedOnClickListener.setSafeOnClickListener(option3, 200L, v -> selectOption(3));
        if (option5 != null) DebouncedOnClickListener.setSafeOnClickListener(option5, 200L, v -> selectOption(5));
        if (option7 != null) DebouncedOnClickListener.setSafeOnClickListener(option7, 200L, v -> selectOption(7));
        if (option14 != null) DebouncedOnClickListener.setSafeOnClickListener(option14, 200L, v -> selectOption(14));

        if (btnCommitGoal != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnCommitGoal, v -> {
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

        TextView tvText = null;
        if (optionView.getChildCount() >= 2 && optionView.getChildAt(1) instanceof TextView) {
            tvText = (TextView) optionView.getChildAt(1);
        }

        if (isSelected) {
            optionView.setBackgroundResource(R.drawable.bg_streak_option_selected);
            if (tvText != null) {
                tvText.setTextColor(android.graphics.Color.WHITE);
            }
            optionView.setAlpha(1.0f);
            optionView.setElevation(6f);
        } else {
            optionView.setBackgroundResource(R.drawable.bg_streak_option_unselected);
            if (tvText != null) {
                tvText.setTextColor(android.graphics.Color.parseColor("#334327"));
            }
            optionView.setAlpha(1.0f);
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
                Date parsedDate = sdf.parse(activeStartDateStr);
                if (parsedDate != null) {
                    startCal.setTime(parsedDate);
                }
            } catch (Exception ignored) {}
        }

        int startDayOfWeek = startCal.get(java.util.Calendar.DAY_OF_WEEK);

        String[] allWeekdayLabels = { "S", "M", "T", "W", "Th", "F", "S" };
        float density = getResources().getDisplayMetrics().density;
        int itemWidthPx = (int) (36 * density);
        int marginPx = (int) (2 * density);

        for (int i = 0; i < 7; i++) {
            int dayIndex = (startDayOfWeek - 1 + i) % 7;
            String label = allWeekdayLabels[dayIndex];

            LinearLayout dayItem = new LinearLayout(this);
            dayItem.setOrientation(LinearLayout.VERTICAL);
            dayItem.setGravity(android.view.Gravity.CENTER);

            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(itemWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.setMargins(marginPx, 0, marginPx, 0);
            dayItem.setLayoutParams(itemParams);

            boolean isActiveStreakDay = (i < currentStreak);

            if (isActiveStreakDay) {
                ImageView flameView = new ImageView(this);
                flameView.setImageResource(R.drawable.ic_streak_flame);
                flameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams flameParams = new LinearLayout.LayoutParams((int) (26 * density), (int) (26 * density));
                flameView.setLayoutParams(flameParams);
                dayItem.addView(flameView);

                TextView tvLabel = new TextView(this);
                tvLabel.setText(label);
                tvLabel.setGravity(android.view.Gravity.CENTER);
                tvLabel.setTextColor(android.graphics.Color.WHITE);
                tvLabel.setTextSize(13);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                dayItem.addView(tvLabel);
            } else {
                View dotView = new View(this);
                dotView.setBackgroundResource(R.drawable.bg_streak_dot_inactive);
                LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams((int) (10 * density), (int) (10 * density));
                dotParams.setMargins(0, (int) (8 * density), 0, (int) (8 * density));
                dotView.setLayoutParams(dotParams);
                dayItem.addView(dotView);

                TextView tvLabel = new TextView(this);
                tvLabel.setText(label);
                tvLabel.setGravity(android.view.Gravity.CENTER);
                tvLabel.setTextColor(android.graphics.Color.parseColor("#80FFFFFF"));
                tvLabel.setTextSize(13);
                tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                dayItem.addView(tvLabel);
            }

            containerWeeklyRow.addView(dayItem);
        }
    }
}
