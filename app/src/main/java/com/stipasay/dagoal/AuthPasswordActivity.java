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
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_auth_password);

        View rootLayout = findViewById(R.id.root_auth_password_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);
        userEmail = getIntent().getStringExtra("extra_email");
        if (userEmail == null) userEmail = "user@dagoal.com";

        ImageButton btnBack = findViewById(R.id.btn_back_password);
        editAuthPassword = findViewById(R.id.edit_auth_password);
        btnPasswordLogin = findViewById(R.id.btn_password_login);
        btnToggleVisibility = findViewById(R.id.btn_toggle_password_visibility);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);

        if (btnBack != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnBack, v -> finish());
        }

        if (btnToggleVisibility != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnToggleVisibility, v -> togglePasswordVisibility());
        }

        if (tvForgotPassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(tvForgotPassword, v -> sendPasswordResetEmail());
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
            DebouncedOnClickListener.setSafeOnClickListener(btnPasswordLogin, v -> {
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

    private void sendPasswordResetEmail() {
        if (userEmail == null || userEmail.isEmpty() || !userEmail.contains("@")) {
            ToastUtils.showToast(this, "Please enter a valid email address first.");
            return;
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.sendPasswordResetEmail(userEmail)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        ToastUtils.showToast(this, "Password reset email sent to " + userEmail + ". Please check your inbox!");
                    } else {
                        String err = task.getException() != null ? task.getException().getMessage() : "Failed to send reset email";
                        ToastUtils.showToast(this, "Notice: " + err);
                    }
                });
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

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        KeyboardUtils.hideKeyboardOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private void processLoginAuthentication() {
        if (editAuthPassword == null) return;
        String password = editAuthPassword.getText().toString().trim();

        if (password.isEmpty()) {
            ToastUtils.showToast(this, "Please enter your password.");
            return;
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser previousAuthenticatedUser = mAuth.getCurrentUser();
        mAuth.signInWithEmailAndPassword(userEmail, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        String uid = firebaseUser != null ? firebaseUser.getUid() : "";

                        SharedPreferences currentPrefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
                        if (AccountSessionHelper.ownsLocalProfile(currentPrefs, uid)) {
                            currentPrefs.edit().putString("user_email", userEmail).apply();
                            proceedWithLocalLogin(null);
                            new TaskManager(this).syncUserProfileToFirestore(this);
                            AvatarConfig.saveUserAvatarToFirestore(this, uid);
                            return;
                        }
                        AvatarConfig localAvatarBackup = AvatarConfig.loadFromPreferences(AuthPasswordActivity.this);

                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                        firestore.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER)
                                .addOnSuccessListener(doc -> {
                                    // Replace a different account only after its server profile is available.
                                    if (!DatabaseHelper.resetAccountAndUserData(AuthPasswordActivity.this, false)) {
                                        AccountSessionHelper.restorePreviousAuthentication(mAuth, previousAuthenticatedUser);
                                        ToastUtils.showToast(this, getString(R.string.login_profile_load_failed));
                                        return;
                                    }
                                    SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
                                    SharedPreferences.Editor editor = prefs.edit();
                                    editor.putBoolean("isLoggedIn", true)
                                          .putBoolean("isOnboardingComplete", true)
                                          .putBoolean("isFirstRun", false)
                                          .putBoolean("isGuestUser", false)
                                          .putString("user_email", userEmail)
                                          .putString("user_uid", uid);

                                    if (doc != null && doc.exists()) {
                                        String username = doc.getString("username");
                                        if (username != null && !username.isEmpty()) {
                                            editor.putString("user_nickname", username);
                                        }
                                        syncUserToLocalDbFromDoc(doc, userEmail);
                                        TaskManager.restoreUserInventoryAndEquippedFromFirestoreDoc(doc, AuthPasswordActivity.this);
                                        if (AvatarConfig.hasAvatarDataInFirestoreDoc(doc)) {
                                            AvatarConfig.restoreUserAvatarFromFirestoreDoc(doc, AuthPasswordActivity.this);
                                        } else if (localAvatarBackup != null) {
                                            localAvatarBackup.saveToPreferences(AuthPasswordActivity.this);
                                            localAvatarBackup.saveToFirestore(uid);
                                        }
                                    } else {
                                        syncUserToLocalDbFromDoc(null, userEmail);
                                        if (localAvatarBackup != null) {
                                            localAvatarBackup.saveToPreferences(AuthPasswordActivity.this);
                                            localAvatarBackup.saveToFirestore(uid);
                                        }
                                    }
                                    boolean schedulesRestored = RecurringQuestBackup.restoreAfterLogin(doc != null ? doc.get(RecurringQuestBackup.CLOUD_FIELD) : null, this, uid);
                                    editor.apply();
                                    if (schedulesRestored && RecurringQuestBackup.hasPending(this, uid)) new TaskManager(this).syncUserProfileToFirestore(this);

                                    ToastUtils.showToast(AuthPasswordActivity.this, schedulesRestored ? "Welcome back!" : getString(R.string.schedule_restore_failed));
                                    Intent intent = new Intent(AuthPasswordActivity.this, AccountSessionHelper.nextScreen(prefs));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    AccountSessionHelper.restorePreviousAuthentication(mAuth, previousAuthenticatedUser);
                                    ToastUtils.showToast(this, getString(R.string.login_profile_load_failed));
                                });
                    } else {
                        Exception exception = task.getException();
                        String errorMsg = exception != null ? exception.getMessage() : "Authentication failed";

                        if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException
                                || (errorMsg != null && (errorMsg.contains("user-not-found") || errorMsg.contains("no user record") || errorMsg.contains("user has been deleted")))) {
                            ToastUtils.showToast(AuthPasswordActivity.this, "No account found for " + userEmail + ". Please Sign Up first.");
                        } else if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            ToastUtils.showToast(AuthPasswordActivity.this, "Incorrect password. Please try again.");
                        } else {
                            proceedWithLocalLogin(null);
                        }
                    }
                });
    }

    private void proceedWithLocalLogin(AvatarConfig localAvatarBackup) {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        FirebaseUser cachedUser = FirebaseAuth.getInstance().getCurrentUser();
        if (!AccountSessionHelper.canResumeOffline(prefs, cachedUser, userEmail)) {
            ToastUtils.showToast(this, getString(R.string.login_offline_requires_session));
            return;
        }
        boolean hasExistingAccount = checkExistingUserAccountInDb();

        if (localAvatarBackup != null) {
            localAvatarBackup.saveToPreferences(this);
        }

        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putBoolean("isGuestUser", false)
                .putString("user_email", userEmail)
                .apply();

        if (hasExistingAccount) {

            ToastUtils.showToast(this, "Welcome back!");
            Intent intent = new Intent(AuthPasswordActivity.this, AccountSessionHelper.nextScreen(prefs));
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

    private void syncUserToLocalDbFromDoc(com.google.firebase.firestore.DocumentSnapshot doc, String defaultEmail) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);
        ContentValues values = new ContentValues();

        String username = doc != null ? doc.getString("username") : null;
        if (username == null || username.isEmpty()) {
            username = defaultEmail != null && defaultEmail.contains("@") ? defaultEmail.split("@")[0] : "Adventurer";
        }
        values.put(DatabaseContract.UserEntry.COLUMN_NAME, username);
        values.put("username", username);

        if (doc != null && doc.exists()) {
            if (doc.contains("gold")) {
                Long g = doc.getLong("gold");
                if (g != null) values.put(DatabaseContract.UserEntry.COLUMN_GOLD, g.intValue());
            }
            if (doc.contains("level")) {
                Long l = doc.getLong("level");
                if (l != null) values.put("level", l.intValue());
                Long highestRewarded = doc.getLong("highest_rewarded_level");
                if (highestRewarded != null || l != null) values.put("highest_rewarded_level",
                        Math.max(highestRewarded != null ? highestRewarded.intValue() : 1, l != null ? l.intValue() : 1));
            }
            if (doc.contains("xp")) {
                Long x = doc.getLong("xp");
                if (x != null) values.put(DatabaseContract.UserEntry.COLUMN_XP, x.intValue());
            }
            if (doc.contains("streak")) {
                Long s = doc.getLong("streak");
                if (s != null) values.put(DatabaseContract.UserEntry.COLUMN_STREAK, s.intValue());
            }
            String lastStreakDate = doc.getString("last_completed_date");
            if (lastStreakDate != null && !lastStreakDate.isEmpty()) values.put("last_completed_date", lastStreakDate);
            Long longest = doc.getLong("longest_streak");
            if (longest != null) values.put("longest_streak", longest.intValue());
            Long customCount = doc.getLong("custom_quest_count");
            if (customCount != null) values.put("custom_quest_count", customCount.intValue());
            String customWeek = doc.getString("custom_quest_week_start");
            if (customWeek != null) values.put("custom_quest_week_start", customWeek);
        }

        Cursor checkCursor = db.rawQuery("SELECT _id FROM user WHERE _id = 1", null);
        if (checkCursor != null && checkCursor.moveToFirst()) {
            db.update(DatabaseContract.UserEntry.TABLE_NAME, values, "_id = 1", null);
            checkCursor.close();
        } else {
            values.put("_id", 1);
            if (!values.containsKey("gold")) values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
            if (!values.containsKey("level")) values.put("level", 1);
            if (!values.containsKey("xp")) values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
            if (!values.containsKey("streak")) values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);
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
