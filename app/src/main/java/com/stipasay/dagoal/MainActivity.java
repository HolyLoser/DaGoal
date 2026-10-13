package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private Button btnStartSignUp, btnStartLogin;
    private TextView tvTermsFooter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_main);

        View rootLayout = findViewById(R.id.root_main_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        btnStartSignUp = findViewById(R.id.btn_start_signup);
        btnStartLogin = findViewById(R.id.btn_start_login);
        tvTermsFooter = findViewById(R.id.tv_terms_footer);

        if (btnStartSignUp != null) {
            btnStartSignUp.setText("Create an Avatar");
            DebouncedOnClickListener.setSafeOnClickListener(btnStartSignUp, v -> processCreateAvatarGuest());
        }

        if (btnStartLogin != null) {
            btnStartLogin.setText("Login");
            DebouncedOnClickListener.setSafeOnClickListener(btnStartLogin, v -> {
                Intent intent = new Intent(MainActivity.this, AuthOptionsActivity.class);
                startActivity(intent);
            });
        }

        if (tvTermsFooter != null) {
            DebouncedOnClickListener.setSafeOnClickListener(tvTermsFooter, v ->
                    ToastUtils.showToast(this, "DaGoal Terms of Service & Privacy Policy")
            );
        }
    }

    private void processCreateAvatarGuest() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isGuestUser", true)
                .putBoolean("isLoggedIn", false)
                .apply();

        ensureGuestUserInDatabase();

        ToastUtils.showToast(this, "Starting Guest Avatar Creation...");

        Intent intent = new Intent(MainActivity.this, AvatarCreationActivity.class);
        startActivity(intent);
    }

    private void ensureGuestUserInDatabase() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);
        ContentValues values = new ContentValues();

        values.put(DatabaseContract.UserEntry.COLUMN_NAME, "Adventurer");
        values.put("username", "Adventurer");
        values.put(DatabaseContract.UserEntry.COLUMN_AGE, 20);
        values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);

        db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, values);
    }
}
