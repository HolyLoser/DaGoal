package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthPasswordActivity extends AppCompatActivity {

    private EditText editAuthPassword;
    private Button btnPasswordLogin;
    private TextView btnToggleVisibility;
    private boolean isPasswordVisible = false;
    private String userEmail = "";
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_password);

        dbHelper = new DatabaseHelper(this);
        userEmail = getIntent().getStringExtra("extra_email");
        if (userEmail == null) userEmail = "user@dagoal.com";

        ImageButton btnBack = findViewById(R.id.btn_back_password);
        editAuthPassword = findViewById(R.id.edit_auth_password);
        btnPasswordLogin = findViewById(R.id.btn_password_login);
        btnToggleVisibility = findViewById(R.id.btn_toggle_password_visibility);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnToggleVisibility != null) {
            btnToggleVisibility.setOnClickListener(v -> togglePasswordVisibility());
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v ->
                    ToastUtils.showToast(this, "Password reset link sent to " + userEmail)
            );
        }

        if (editAuthPassword != null) {
            editAuthPassword.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    boolean hasPassword = s != null && s.length() > 0;
                    if (btnPasswordLogin != null) {
                        btnPasswordLogin.setEnabled(hasPassword);
                        btnPasswordLogin.setBackgroundResource(hasPassword ? R.drawable.bg_auth_pill_green : R.drawable.bg_auth_pill_disabled);
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            editAuthPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    if (btnPasswordLogin != null && btnPasswordLogin.isEnabled()) {
                        processLoginAuthentication();
                    }
                    return true;
                }
                return false;
            });
        }

        if (btnPasswordLogin != null) {
            btnPasswordLogin.setOnClickListener(v -> {
                v.setEnabled(false);
                processLoginAuthentication();
                v.postDelayed(() -> {
                    if (editAuthPassword != null && editAuthPassword.getText().length() > 0) {
                        v.setEnabled(true);
                    }
                }, 2000);
            });
        }
    }

    private void togglePasswordVisibility() {
        if (editAuthPassword == null) return;
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            editAuthPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            btnToggleVisibility.setText("👁️‍🗨️");
        } else {
            editAuthPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            btnToggleVisibility.setText("👁️");
        }

        editAuthPassword.setSelection(editAuthPassword.getText().length());
    }

    private void processLoginAuthentication() {
        if (editAuthPassword == null) return;
        String password = editAuthPassword.getText().toString().trim();

        if (password.isEmpty()) {
            ToastUtils.showToast(this, "Please enter your password.");
            return;
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(userEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        String uid = firebaseUser != null ? firebaseUser.getUid() : "";

                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                        firestore.collection("users").document(uid).get()
                                .addOnSuccessListener(doc -> {
                                    SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putBoolean("isLoggedIn", true)
                                          .putBoolean("isGuestUser", false)
                                          .putString("user_email", userEmail)
                                          .putString("user_uid", uid);

                                    if (doc != null && doc.exists()) {
                                        String username = doc.getString("username");
                                        if (username != null && !username.isEmpty()) {
                                            editor.putString("user_nickname", username);
                                            syncUserToLocalDb(username);
                                        }
                                    }
                                    editor.apply();

                                    ToastUtils.showToast(AuthPasswordActivity.this, "Welcome back!");
                                    Intent intent = new Intent(AuthPasswordActivity.this, DashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> proceedWithLocalLogin());
                    } else {
                        Exception exception = task.getException();
                        String errorMsg = exception != null ? exception.getMessage() : "Authentication failed";

                        if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException
                                || (errorMsg != null && (errorMsg.contains("user-not-found") || errorMsg.contains("no user record") || errorMsg.contains("user has been deleted")))) {
                            ToastUtils.showToast(AuthPasswordActivity.this, "No account found for " + userEmail + ". Please Sign Up first.");
                        } else if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            ToastUtils.showToast(AuthPasswordActivity.this, "Incorrect password. Please try again.");
                        } else {
                            ToastUtils.showToast(AuthPasswordActivity.this, "Notice: " + errorMsg + ". Trying local mode.");
                            proceedWithLocalLogin();
                        }
                    }
                });
    }

    private void proceedWithLocalLogin() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        boolean hasExistingAccount = checkExistingUserAccountInDb();

        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putBoolean("isGuestUser", false)
                .putString("user_email", userEmail)
                .apply();

        if (hasExistingAccount) {
            prefs.edit().putBoolean("isOnboardingComplete", true).putBoolean("isFirstRun", false).apply();
            ToastUtils.showToast(this, "Welcome back!");
            Intent intent = new Intent(AuthPasswordActivity.this, DashboardActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            prefs.edit().putInt("pref_onboarding_step", 1).putBoolean("isOnboardingComplete", false).apply();
            createInitialUserAccountInDb();
            ToastUtils.showToast(this, "Account Created! Let's set up your avatar.");
            Intent intent = new Intent(AuthPasswordActivity.this, AvatarCreationActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private boolean checkExistingUserAccountInDb() {
        try {
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            DatabaseHelper.ensureUserTableExists(db);
            Cursor cursor = db.rawQuery("SELECT _id FROM user WHERE _id = 1", null);
            boolean exists = cursor != null && cursor.getCount() > 0;
            if (cursor != null) cursor.close();
            return exists;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void syncUserToLocalDb(String username) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);
        ContentValues values = new ContentValues();
        values.put(DatabaseContract.UserEntry.COLUMN_NAME, username);
        values.put("username", username);

        Cursor checkCursor = db.rawQuery("SELECT _id FROM user WHERE _id = 1", null);
        if (checkCursor != null && checkCursor.moveToFirst()) {
            db.update(DatabaseContract.UserEntry.TABLE_NAME, values, "_id = 1", null);
            checkCursor.close();
        } else {
            values.put("_id", 1);
            values.put(DatabaseContract.UserEntry.COLUMN_AGE, 22);
            values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
            values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
            values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);
            db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, values);
            if (checkCursor != null) checkCursor.close();
        }
    }

    private void createInitialUserAccountInDb() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);
        ContentValues values = new ContentValues();

        String defaultUsername = userEmail.contains("@") ? userEmail.split("@")[0] : "Adventurer";
        values.put(DatabaseContract.UserEntry.COLUMN_NAME, defaultUsername);
        values.put("username", defaultUsername);
        values.put(DatabaseContract.UserEntry.COLUMN_AGE, 22);
        values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);

        db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, values);
    }
}
