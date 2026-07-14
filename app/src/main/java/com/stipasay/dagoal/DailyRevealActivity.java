package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyRevealActivity extends AppCompatActivity {

    private LinearLayout containerDailyTasks;
    private TextView tvShuffleCounter;
    private Button btnAcceptTasks;
    private DatabaseHelper dbHelper;
    private int availableShuffles = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_reveal);

        dbHelper = new DatabaseHelper(this);
        containerDailyTasks = findViewById(R.id.container_daily_tasks);
        tvShuffleCounter = findViewById(R.id.tv_shuffle_counter);
        btnAcceptTasks = findViewById(R.id.btn_accept_tasks);

        View rootLayout = findViewById(R.id.root_reveal_layout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        TaskManager taskManager = new TaskManager(this);
        taskManager.generateDailyTasks();

        loadDailyTasksFromDatabase();

        btnAcceptTasks.setOnClickListener(v -> {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            prefs.edit().putString("last_quest_generation_date", todayDateStr).apply();

            Intent intent = new Intent(DailyRevealActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void loadDailyTasksFromDatabase() {
        containerDailyTasks.removeAllViews();
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        String[] columns = {
                DatabaseContract.DailyTaskEntry._ID,
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE,
                DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE,
                DatabaseContract.DailyTaskEntry.COLUMN_UNIT
        };

        Cursor cursor = db.query(
                DatabaseContract.DailyTaskEntry.TABLE_NAME,
                columns, null, null, null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                int taskId = cursor.getInt(0);
                String title = cursor.getString(1);
                int targetValue = cursor.getInt(2);
                String unit = cursor.getString(3);

                View taskRow = LayoutInflater.from(this).inflate(R.layout.item_reveal_task, containerDailyTasks, false);

                TextView tvTitle = taskRow.findViewById(R.id.tv_task_title);
                TextView tvTarget = taskRow.findViewById(R.id.tv_task_target);
                CheckBox btnShuffle = taskRow.findViewById(R.id.btn_shuffle_item);

                tvTitle.setText(title);
                tvTarget.setText("Target: " + targetValue + " " + unit);

                // Using standard OnClickListener and resetting check state manually to keep execution clean
                btnShuffle.setOnClickListener(v -> {
                    btnShuffle.setChecked(false);
                    handleTaskShuffle(taskId, tvTitle, tvTarget);
                });

                containerDailyTasks.addView(taskRow);
            }
            cursor.close();
        }
    }

    private void handleTaskShuffle(int taskId, TextView tvTitle, TextView tvTarget) {
        if (availableShuffles <= 0) {
            Toast.makeText(this, "No shuffles remaining for today!", Toast.LENGTH_SHORT).show();
            return;
        }

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        String excludeQuery = "SELECT title, base_value, unit FROM task_templates WHERE " +
                DatabaseContract.TaskTemplateEntry.COLUMN_TITLE + " NOT IN (SELECT " +
                DatabaseContract.DailyTaskEntry.COLUMN_TITLE + " FROM " +
                DatabaseContract.DailyTaskEntry.TABLE_NAME + ") ORDER BY RANDOM() LIMIT 1";

        Cursor cursor = db.rawQuery(excludeQuery, null);

        if (cursor != null && cursor.moveToFirst()) {
            String newTitle = cursor.getString(0);
            int baseValue = cursor.getInt(1);
            String unit = cursor.getString(2);
            cursor.close();

            int finalTarget = baseValue;

            ContentValues values = new ContentValues();
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TITLE, newTitle);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_TARGET_VALUE, finalTarget);
            values.put(DatabaseContract.DailyTaskEntry.COLUMN_UNIT, unit);

            db.update(DatabaseContract.DailyTaskEntry.TABLE_NAME, values, "_id = ?", new String[]{String.valueOf(taskId)});

            tvTitle.setText(newTitle);
            tvTarget.setText("Target: " + finalTarget + " " + unit);

            availableShuffles--;
            tvShuffleCounter.setText("Free Shuffles available today: " + availableShuffles);
            Toast.makeText(this, "Task shuffled successfully!", Toast.LENGTH_SHORT).show();
        } else {
            if (cursor != null) {
                cursor.close();
            }
            Toast.makeText(this, "No alternative tasks found in templates!", Toast.LENGTH_SHORT).show();
        }
    }
}