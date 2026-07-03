package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StreakActivity extends AppCompatActivity {

    private TextView tvDialogStreakCount;
    private Button btnStreakDialogDismiss;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_daily_streak);

        dbHelper = new DatabaseHelper(this);
        tvDialogStreakCount = findViewById(R.id.tv_dialog_streak_count);
        btnStreakDialogDismiss = findViewById(R.id.btn_streak_dialog_dismiss);

        validateStreakBaseline();

        int streakVal = getStreakCount();
        tvDialogStreakCount.setText(String.valueOf(streakVal));

        btnStreakDialogDismiss.setOnClickListener(v -> {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            prefs.edit().putString("last_streak_popup_date", todayDateStr).apply();
            finish();
        });
    }

    private void validateStreakBaseline() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        Cursor cursor = db.rawQuery("SELECT last_completed_date, streak FROM user WHERE _id = 1", null);
        if (cursor != null && cursor.moveToFirst()) {
            String lastCompleted = cursor.getString(0);
            int currentStreak = cursor.getInt(1);
            cursor.close();

            if (currentStreak == 0 || lastCompleted == null || lastCompleted.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put("streak", 1);
                db.update("user", values, "_id = 1", null);
                return;
            }

            if (lastCompleted.equals(todayDateStr)) {
                return;
            }

            try {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date lastDate = sdf.parse(lastCompleted);

                Calendar cal = Calendar.getInstance();
                if (lastDate != null) {
                    cal.setTime(lastDate);
                }
                cal.add(Calendar.DAY_OF_YEAR, 1);
                String expectedExtensionDate = sdf.format(cal.getTime());

                Date todayDate = sdf.parse(todayDateStr);

                if (todayDate != null && !todayDateStr.equals(expectedExtensionDate) && todayDate.after(cal.getTime())) {
                    ContentValues values = new ContentValues();
                    values.put("streak", 1);
                    db.update("user", values, "_id = 1", null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
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
}