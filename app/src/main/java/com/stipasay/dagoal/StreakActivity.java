package com.stipasay.dagoal;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
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

        View rootLayout = findViewById(R.id.root_streak_dialog_layout);
        if (rootLayout != null) {
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
                androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
                return insets;
            });
        }

        int streakVal = getStreakCount();
        tvDialogStreakCount.setText(String.valueOf(streakVal));

        btnStreakDialogDismiss.setOnClickListener(v -> {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            prefs.edit().putString("last_streak_popup_date", todayDateStr).apply();
            finish();
        });
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