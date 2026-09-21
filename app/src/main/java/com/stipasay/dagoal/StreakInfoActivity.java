package com.stipasay.dagoal;

import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

public class StreakInfoActivity extends AppCompatActivity {

    private TaskManager taskManager;
    private GridView gridCalendar;
    private TextView tvCurrent;
    private TextView tvLongest;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_streak_info);

        taskManager = new TaskManager(this);
        gridCalendar = findViewById(R.id.grid_streak_calendar);
        tvCurrent = findViewById(R.id.tv_streak_info_current);
        tvLongest = findViewById(R.id.tv_streak_info_longest);

        findViewById(R.id.btn_streak_info_back).setOnClickListener(v -> finish());

        loadStreakData();
        buildCalendar();
    }

    private void loadStreakData() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        android.database.Cursor cursor = db.rawQuery("SELECT streak FROM user WHERE _id = 1", null);
        int currentStreak = 0;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                currentStreak = cursor.getInt(0);
            }
            cursor.close();
        }
        tvCurrent.setText(String.valueOf(currentStreak));
        tvLongest.setText(String.valueOf(taskManager.getLongestStreak()));
    }

    private void buildCalendar() {
        Calendar cal = Calendar.getInstance();
        int year = cal.get(Calendar.YEAR);
        int month = cal.get(Calendar.MONTH);

        Map<String, int[]> historyMap = taskManager.getStreakHistoryForMonth(year, month);

        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        android.database.Cursor userCursor = db.rawQuery("SELECT streak FROM user WHERE _id = 1", null);
        int currentStreak = 0;
        if (userCursor != null) {
            if (userCursor.moveToFirst()) {
                currentStreak = userCursor.getInt(0);
            }
            userCursor.close();
        }

        String activeStartDateStr = taskManager.getActiveStreakStartDate();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        Date activeStartDate = null;
        if (activeStartDateStr != null) {
            try {
                activeStartDate = sdf.parse(activeStartDateStr);
            } catch (Exception ignored) {}
        }

        final Date finalActiveStartDate = activeStartDate;
        final int finalCurrentStreak = currentStreak;

        Calendar firstDayCal = Calendar.getInstance();
        firstDayCal.set(year, month, 1);
        int startOffset = firstDayCal.get(Calendar.DAY_OF_WEEK) - 1;
        int daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH);

        java.util.List<Integer> cells = new java.util.ArrayList<>();
        for (int i = 0; i < startOffset; i++) {
            cells.add(0);
        }
        for (int day = 1; day <= daysInMonth; day++) {
            cells.add(day);
        }

        gridCalendar.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() { return cells.size(); }
            @Override
            public Object getItem(int position) { return cells.get(position); }
            @Override
            public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = LayoutInflater.from(StreakInfoActivity.this).inflate(R.layout.item_calendar_day, parent, false);
                }

                int dayNumber = cells.get(position);
                TextView tvDay = convertView.findViewById(R.id.tv_calendar_day_number);
                TextView tvChest = convertView.findViewById(R.id.tv_calendar_day_chest);
                View bgView = convertView.findViewById(R.id.view_calendar_day_bg);

                if (dayNumber == 0) {
                    tvDay.setText("");
                    tvChest.setVisibility(View.GONE);
                    bgView.setAlpha(0f);
                    convertView.setOnClickListener(null);
                    return convertView;
                }

                bgView.setAlpha(1f);
                tvDay.setText(String.valueOf(dayNumber));

                Calendar cellCal = Calendar.getInstance();
                cellCal.set(year, month, dayNumber, 0, 0, 0);
                cellCal.set(Calendar.MILLISECOND, 0);
                String cellDateStr = sdf.format(cellCal.getTime());

                int[] entry = historyMap.get(cellDateStr);
                if (entry != null && entry[0] > 0) {
                    androidx.core.view.ViewCompat.setBackgroundTintList(bgView,
                            android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#DCCCAC")));
                } else {
                    androidx.core.view.ViewCompat.setBackgroundTintList(bgView, null);
                }

                long activeDayDiff = -1;
                if (finalActiveStartDate != null) {
                    Calendar activeStartCal = Calendar.getInstance();
                    activeStartCal.setTime(finalActiveStartDate);
                    activeStartCal.set(Calendar.HOUR_OF_DAY, 0);
                    activeStartCal.set(Calendar.MINUTE, 0);
                    activeStartCal.set(Calendar.SECOND, 0);
                    activeStartCal.set(Calendar.MILLISECOND, 0);

                    long diffMs = cellCal.getTimeInMillis() - activeStartCal.getTimeInMillis();
                    activeDayDiff = Math.round((double) diffMs / (1000 * 60 * 60 * 24));
                }

                if (activeDayDiff >= 0) {
                    int streakDayNum = (int) activeDayDiff + 1;
                    boolean isChestMilestone = (streakDayNum % 3 == 0);
                    boolean isStreakStartDay = (streakDayNum == 1);
                    boolean isReached = (streakDayNum <= finalCurrentStreak);
                    boolean isClaimed = (entry != null && entry[1] == 1);

                    if (isChestMilestone) {
                        tvChest.setText("🎁");
                        tvChest.setVisibility(View.VISIBLE);

                        if (isReached) {
                            tvChest.setAlpha(isClaimed ? 0.4f : 1.0f);
                            if (!isClaimed) {
                                convertView.setOnClickListener(v -> {
                                    boolean success = taskManager.claimChest(cellDateStr);
                                    if (success) {
                                        ToastUtils.showToast(StreakInfoActivity.this, "Chest opened! +50 Gold, +40 XP");
                                        buildCalendar();
                                    } else {
                                        ToastUtils.showToast(StreakInfoActivity.this, "This chest is already claimed.");
                                    }
                                });
                            } else {
                                convertView.setOnClickListener(v -> ToastUtils.showToast(StreakInfoActivity.this, "Already claimed."));
                            }
                        } else {
                            // Future projected chest
                            tvChest.setAlpha(0.35f);
                            final int reqDay = streakDayNum;
                            convertView.setOnClickListener(v -> ToastUtils.showToast(StreakInfoActivity.this, "Reach day " + reqDay + " of your streak to unlock this chest!"));
                        }
                    } else if (isStreakStartDay) {
                        tvChest.setText("🔑");
                        tvChest.setVisibility(View.VISIBLE);
                        tvChest.setAlpha(0.8f);
                        convertView.setOnClickListener(v -> ToastUtils.showToast(StreakInfoActivity.this, "A new streak began here — reach day 3 for a chest!"));
                    } else {
                        tvChest.setVisibility(View.GONE);
                        convertView.setOnClickListener(null);
                    }
                } else {
                    // Past date before current active streak start
                    boolean isClaimedPastChest = (entry != null && entry[0] % 3 == 0 && entry[1] == 1);
                    boolean isPastStreakStart = (entry != null && entry[0] == 1);

                    if (isClaimedPastChest) {
                        tvChest.setText("🎁");
                        tvChest.setVisibility(View.VISIBLE);
                        tvChest.setAlpha(0.4f);
                        convertView.setOnClickListener(v -> ToastUtils.showToast(StreakInfoActivity.this, "Already claimed."));
                    } else if (isPastStreakStart) {
                        tvChest.setText("🔑");
                        tvChest.setVisibility(View.VISIBLE);
                        tvChest.setAlpha(0.4f);
                        convertView.setOnClickListener(v -> ToastUtils.showToast(StreakInfoActivity.this, "Past streak start."));
                    } else {
                        // Broken/unclaimed past chests are hidden completely
                        tvChest.setVisibility(View.GONE);
                        convertView.setOnClickListener(null);
                    }
                }

                return convertView;
            }
        });
    }
}
