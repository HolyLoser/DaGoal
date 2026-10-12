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
            tvTermsFooter.setText(R.string.privacy_policy_title);
            DebouncedOnClickListener.setSafeOnClickListener(tvTermsFooter, v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));
        }
    }

    private void processCreateAvatarGuest() {
        if (!DatabaseHelper.resetAccountAndUserData(this, false)) {
            ToastUtils.showToast(this, getString(R.string.account_switch_failed));
            return;
        }

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isGuestUser", true)
                .putBoolean("isOnboardingComplete", false)
                .putInt("pref_onboarding_step", 1)
                .putBoolean("isLoggedIn", false)
                .remove("user_uid")
                .remove("user_email")
                .remove("user_nickname")
                .apply();

        ensureGuestUserInDatabase();

        ToastUtils.showToast(this, "Starting Guest Avatar Creation...");

        Intent intent = new Intent(MainActivity.this, AvatarCreationActivity.class);
        startActivity(intent);
    }

    private void ensureGuestUserInDatabase() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("user", null, null);
        DatabaseHelper.ensureUserTableExists(db);
    }
}
