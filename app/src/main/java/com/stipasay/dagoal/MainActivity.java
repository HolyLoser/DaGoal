package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText editUsername, editAge;
    private RadioGroup rgGender;
    private Button btnProceed;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        boolean isFirstRun = prefs.getBoolean("isFirstRun", true);

        if (!isFirstRun) {
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
            String lastQuestDate = prefs.getString("last_quest_generation_date", "");

            if (!todayDateStr.equals(lastQuestDate)) {
                Intent intent = new Intent(this, DailyRevealActivity.class);
                startActivity(intent);
            } else {
                Intent intent = new Intent(this, DashboardActivity.class);
                startActivity(intent);
            }
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        dbHelper = new DatabaseHelper(this);

        editUsername = findViewById(R.id.edit_username);
        editAge = findViewById(R.id.edit_age);
        rgGender = findViewById(R.id.rg_gender);
        btnProceed = findViewById(R.id.btn_proceed);

        btnProceed.setOnClickListener(v -> processUserProfiling());
    }

    private void processUserProfiling() {
        String username = editUsername.getText().toString().trim();
        String ageStr = editAge.getText().toString().trim();

        int selectedGenderId = rgGender.getCheckedRadioButtonId();
        String gender = "";

        if (selectedGenderId != -1) {
            RadioButton rbSelected = findViewById(selectedGenderId);
            gender = rbSelected.getText().toString();
        }

        if (username.isEmpty() || ageStr.isEmpty() || gender.isEmpty()) {
            Toast.makeText(this, "Please fill in all profile fields.", Toast.LENGTH_SHORT).show();
            return;
        }

        int age;
        try {
            age = Integer.parseInt(ageStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter a valid age.", Toast.LENGTH_SHORT).show();
            return;
        }

        saveUserToLocalDatabase(username, age);

        Toast.makeText(this, "Profile Initialized Offline!", Toast.LENGTH_SHORT).show();

        Intent intent = new Intent(MainActivity.this, ProfilingActivity.class);
        startActivity(intent);
        finish();
    }

    private void saveUserToLocalDatabase(String name, int age) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.UserEntry.COLUMN_NAME, name);
        values.put(DatabaseContract.UserEntry.COLUMN_AGE, age);
        values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);

        db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, values);
    }
}