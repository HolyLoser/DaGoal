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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthEmailActivity extends AppCompatActivity {

    private EditText editAuthEmail;
    private EditText editAuthPassword;
    private ImageView btnToggleVisibility;
    private Button btnEmailLogin;
    private TextView tvPasswordHint;
    private boolean isPasswordVisible = false;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_auth_email);

        View rootLayout = findViewById(R.id.root_auth_email_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btn_back_email);
        editAuthEmail = findViewById(R.id.edit_auth_email);
        editAuthPassword = findViewById(R.id.edit_auth_password);
        btnToggleVisibility = findViewById(R.id.btn_toggle_password_visibility);
        btnEmailLogin = findViewById(R.id.btn_email_login);
        tvPasswordHint = findViewById(R.id.tv_password_hint);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView tvGoToSignUp = findViewById(R.id.tv_go_to_signup);

        if (btnBack != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnBack, v -> finish());
        }

        if (btnToggleVisibility != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnToggleVisibility, v -> togglePasswordVisibility());
        }

        if (tvForgotPassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(tvForgotPassword, v -> handleForgotPassword());
        }

        if (tvGoToSignUp != null) {
            DebouncedOnClickListener.setSafeOnClickListener(tvGoToSignUp, v -> {
                Intent intent = new Intent(AuthEmailActivity.this, SignUpActivity.class);
                startActivity(intent);
            });
        }

        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkInputValidation();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (editAuthEmail != null) editAuthEmail.addTextChangedListener(inputWatcher);
        if (editAuthPassword != null) {
            editAuthPassword.addTextChangedListener(inputWatcher);
            editAuthPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    if (btnEmailLogin != null && btnEmailLogin.isEnabled()) {
                        processLoginAuthentication();
                    }
                    return true;
                }
                return false;
            });
        }

        if (btnEmailLogin != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnEmailLogin, v -> processLoginAuthentication());
        }
    }

    private void togglePasswordVisibility() {
        if (editAuthPassword == null) return;
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            editAuthPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            if (btnToggleVisibility != null) {
                btnToggleVisibility.setImageResource(R.drawable.ic_eye_visible);
            }
        } else {
            editAuthPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            if (btnToggleVisibility != null) {
                btnToggleVisibility.setImageResource(R.drawable.ic_eye_hidden);
            }
        }

        editAuthPassword.setSelection(editAuthPassword.getText().length());
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        KeyboardUtils.hideKeyboardOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private void checkInputValidation() {
        if (editAuthEmail == null || editAuthPassword == null || btnEmailLogin == null) return;

        String email = editAuthEmail.getText().toString().trim();
        String password = editAuthPassword.getText().toString().trim();

        boolean isValidEmail = !email.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean isValidPassword = password.length() >= 6;

        if (tvPasswordHint != null) {
            int targetColor = isValidPassword
                    ? android.graphics.Color.parseColor("#A7F3D0")
                    : androidx.core.content.ContextCompat.getColor(this, R.color.dagoal_background_light);
            tvPasswordHint.setTextColor(targetColor);
            androidx.core.widget.TextViewCompat.setCompoundDrawableTintList(
                    tvPasswordHint,
                    android.content.res.ColorStateList.valueOf(targetColor)
            );
        }

        boolean canSubmit = isValidEmail && isValidPassword;
        btnEmailLogin.setText("Login");
        btnEmailLogin.setEnabled(canSubmit);
        btnEmailLogin.setBackgroundResource(canSubmit ? R.drawable.bg_auth_pill_green : R.drawable.bg_auth_pill_disabled);
    }

    private void handleForgotPassword() {
        if (editAuthEmail == null) return;
        String email = editAuthEmail.getText().toString().trim();

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ToastUtils.showToast(this, "Please enter a valid email address first.");
            return;
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(aVoid -> ToastUtils.showToast(this, "Password reset link sent to " + email))
                .addOnFailureListener(e -> ToastUtils.showToast(this, "Failed to send reset email: " + e.getMessage()));
    }

    private void processLoginAuthentication() {
        if (editAuthEmail == null || editAuthPassword == null) return;

        String email = editAuthEmail.getText().toString().trim();
        String password = editAuthPassword.getText().toString().trim();

        if (btnEmailLogin != null) {
            btnEmailLogin.setText("Logging in...");
            btnEmailLogin.setEnabled(false);
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        FirebaseUser previousAuthenticatedUser = mAuth.getCurrentUser();
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        String uid = firebaseUser != null ? firebaseUser.getUid() : "";

                        SharedPreferences currentPrefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
                        if (AccountSessionHelper.ownsLocalProfile(currentPrefs, uid)) {
                            currentPrefs.edit().putString("user_email", email).apply();
                            proceedWithLocalLogin(email);
                            new TaskManager(this).syncUserProfileToFirestore(this);
                            AvatarConfig.saveUserAvatarToFirestore(this, uid);
                            return;
                        }
                        AvatarConfig localAvatarBackup = AvatarConfig.loadFromPreferences(AuthEmailActivity.this);

                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                        firestore.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER)
                                .addOnSuccessListener(doc -> {
                                    // Replace a different account only after its server profile is available.
                                    if (!DatabaseHelper.resetAccountAndUserData(AuthEmailActivity.this, false)) {
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
                                          .putString("user_email", email)
                                          .putString("user_uid", uid);

                                    if (doc != null && doc.exists()) {
                                        String username = doc.getString("username");
                                        if (username != null && !username.isEmpty()) {
                                            editor.putString("user_nickname", username);
                                        }
                                        syncUserToLocalDbFromDoc(doc, email);
                                        TaskManager.restoreUserInventoryAndEquippedFromFirestoreDoc(doc, AuthEmailActivity.this);
                                        if (AvatarConfig.hasAvatarDataInFirestoreDoc(doc)) {
                                            AvatarConfig.restoreUserAvatarFromFirestoreDoc(doc, AuthEmailActivity.this);
                                        } else if (localAvatarBackup != null) {
                                            localAvatarBackup.saveToPreferences(AuthEmailActivity.this);
                                            localAvatarBackup.saveToFirestore(uid);
                                        }
                                    } else {
                                        syncUserToLocalDbFromDoc(null, email);
                                        if (localAvatarBackup != null) {
                                            localAvatarBackup.saveToPreferences(AuthEmailActivity.this);
                                            localAvatarBackup.saveToFirestore(uid);
                                        }
                                    }
                                    boolean schedulesRestored = RecurringQuestBackup.restoreAfterLogin(doc != null ? doc.get(RecurringQuestBackup.CLOUD_FIELD) : null, this, uid);
                                    editor.apply();
                                    if (schedulesRestored && RecurringQuestBackup.hasPending(this, uid)) new TaskManager(this).syncUserProfileToFirestore(this);

                                    ToastUtils.showToast(AuthEmailActivity.this, schedulesRestored ? "Welcome back!" : getString(R.string.schedule_restore_failed));
                                    Intent intent = new Intent(AuthEmailActivity.this, AccountSessionHelper.nextScreen(prefs));
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> {
                                    AccountSessionHelper.restorePreviousAuthentication(mAuth, previousAuthenticatedUser);
                                    checkInputValidation();
                                    ToastUtils.showToast(this, getString(R.string.login_profile_load_failed));
                                });
                    } else {
                        checkInputValidation();
                        Exception exception = task.getException();
                        String errorMsg = exception != null ? exception.getMessage() : "Authentication failed";

                        if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException
                                || (errorMsg != null && (errorMsg.contains("user-not-found") || errorMsg.contains("no user record")))) {
                            ToastUtils.showToast(AuthEmailActivity.this, "No account found for " + email + ". Please Sign Up first.");
                        } else if (exception instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            ToastUtils.showToast(AuthEmailActivity.this, "Incorrect password. Please try again.");
                        } else {
                            showLowConnectivityDialog(email);
                        }
                    }
                });
    }

    private void showLowConnectivityDialog(String email) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirmation_prompt, null);

        View frameIconBg = dialogView.findViewById(R.id.frame_confirm_icon_bg);
        ImageView ivIcon = dialogView.findViewById(R.id.iv_confirm_icon);
        TextView tvTitle = dialogView.findViewById(R.id.tv_confirm_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_confirm_message);
        Button btnNeg = dialogView.findViewById(R.id.btn_confirm_negative);
        Button btnPos = dialogView.findViewById(R.id.btn_confirm_positive);

        if (frameIconBg != null) {
            androidx.core.view.ViewCompat.setBackgroundTintList(frameIconBg, android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#FEF3C7")));
        }
        if (ivIcon != null) {
            ivIcon.setImageResource(R.drawable.ic_info_circle);
            androidx.core.widget.ImageViewCompat.setImageTintList(ivIcon, android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.dagoal_primary_dark)));
        }

        SharedPreferences saved = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        boolean canResume = AccountSessionHelper.canResumeOffline(saved, FirebaseAuth.getInstance().getCurrentUser(), email);
        if (tvTitle != null) tvTitle.setText("Connectivity Notice");
        if (tvMessage != null) tvMessage.setText(getString(canResume ? R.string.login_saved_session_offline : R.string.login_offline_requires_session));

        if (btnNeg != null) btnNeg.setText("Cancel");
        if (btnPos != null) {
            btnPos.setText(canResume ? "Proceed Offline" : "Try Again");
            btnPos.setBackgroundResource(R.drawable.bg_button_primary_pill);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnNeg != null) DebouncedOnClickListener.setSafeOnClickListener(btnNeg, v -> dialog.dismiss());
        if (btnPos != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnPos, v -> {
                dialog.dismiss();
                if (canResume) proceedWithLocalLogin(email);
                else processLoginAuthentication();
            });
        }

        dialog.show();
    }

    private void proceedWithLocalLogin(String email) {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        FirebaseUser cachedUser = FirebaseAuth.getInstance().getCurrentUser();
        if (!AccountSessionHelper.canResumeOffline(prefs, cachedUser, email)) {
            ToastUtils.showToast(this, getString(R.string.login_offline_requires_session));
            return;
        }
        boolean hasExistingAccount = checkExistingUserAccountInDb();

        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putBoolean("isGuestUser", false)
                .putString("user_email", email)
                .apply();

        if (hasExistingAccount) {

            ToastUtils.showToast(this, "Welcome back!");
            Intent intent = new Intent(AuthEmailActivity.this, AccountSessionHelper.nextScreen(prefs));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            prefs.edit().putInt("pref_onboarding_step", 1).putBoolean("isOnboardingComplete", false).apply();
            createInitialUserAccountInDb(email);
            ToastUtils.showToast(this, "Account Ready! Let's set up your avatar.");
            Intent intent = new Intent(AuthEmailActivity.this, AvatarCreationActivity.class);
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

    private void createInitialUserAccountInDb(String email) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        DatabaseHelper.ensureUserTableExists(db);
        ContentValues values = new ContentValues();

        String defaultUsername = email.contains("@") ? email.split("@")[0] : "Adventurer";
        values.put(DatabaseContract.UserEntry.COLUMN_NAME, defaultUsername);
        values.put("username", defaultUsername);
        values.put(DatabaseContract.UserEntry.COLUMN_AGE, 22);
        values.put(DatabaseContract.UserEntry.COLUMN_XP, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_GOLD, 0);
        values.put(DatabaseContract.UserEntry.COLUMN_STREAK, 0);

        db.insert(DatabaseContract.UserEntry.TABLE_NAME, null, values);
    }
}
