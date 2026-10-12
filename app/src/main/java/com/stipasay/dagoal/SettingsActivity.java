package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class SettingsActivity extends AppCompatActivity {

    private FrameLayout contentFrame;
    private TextView tvScreenTitle;
    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private String currentScreen = "MAIN";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_settings);

        View rootLayout = findViewById(R.id.root_settings_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);

        contentFrame = findViewById(R.id.settings_content_frame);
        tvScreenTitle = findViewById(R.id.tv_settings_screen_title);

        DebouncedOnClickListener.setSafeOnClickListener(findViewById(R.id.btn_settings_back), v -> handleSettingsBackNavigation());

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleSettingsBackNavigation();
            }
        });

        showMainMenu();
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        KeyboardUtils.hideKeyboardOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private void handleSettingsBackNavigation() {
        if (!"MAIN".equals(currentScreen)) {
            SoundEffectsHelper.playMenuClose(this);
            showMainMenu();
        } else {
            SoundEffectsHelper.playMenuClose(this);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!"MAIN".equals(currentScreen)) {
            showScreen(currentScreen);
        }
    }

    private void showMainMenu() {
        currentScreen = "MAIN";
        tvScreenTitle.setText("Settings");
        contentFrame.removeAllViews();
        View view = LayoutInflater.from(this).inflate(R.layout.view_settings_main, contentFrame, false);
        contentFrame.addView(view);

        DebouncedOnClickListener.setSafeOnClickListener(view.findViewById(R.id.row_settings_account), v -> showScreen("ACCOUNT"));
        DebouncedOnClickListener.setSafeOnClickListener(view.findViewById(R.id.row_settings_notifications), v -> showScreen("NOTIFICATIONS"));
        DebouncedOnClickListener.setSafeOnClickListener(view.findViewById(R.id.row_settings_privacy), v -> showScreen("PRIVACY"));
        DebouncedOnClickListener.setSafeOnClickListener(view.findViewById(R.id.row_settings_sound), v -> showScreen("SOUND"));
        DebouncedOnClickListener.setSafeOnClickListener(view.findViewById(R.id.row_settings_about), v -> showScreen("ABOUT"));
    }

    private void showScreen(String screen) {
        currentScreen = screen;
        contentFrame.removeAllViews();

        switch (screen) {
            case "ACCOUNT":
                tvScreenTitle.setText("Account");
                buildAccountScreen();
                break;
            case "NOTIFICATIONS":
                tvScreenTitle.setText("Notifications");
                buildNotificationsScreen();
                break;
            case "PRIVACY":
                tvScreenTitle.setText("Privacy & Permissions");
                buildPrivacyScreen();
                break;
            case "SOUND":
                tvScreenTitle.setText("Sound & Haptics");
                buildSoundScreen();
                break;
            case "ABOUT":
                tvScreenTitle.setText("About");
                buildAboutScreen();
                break;
        }
    }

    private View addSwitchRow(LinearLayout container, String title, String subtitle, boolean initialValue, SwitchCallback callback) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_switch_row, container, false);
        TextView tvTitle = row.findViewById(R.id.tv_switch_row_title);
        TextView tvSubtitle = row.findViewById(R.id.tv_switch_row_subtitle);
        Switch toggle = row.findViewById(R.id.switch_settings_toggle);

        tvTitle.setText(title);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        }
        toggle.setChecked(initialValue);
        toggle.setOnCheckedChangeListener((buttonView, isChecked) -> callback.onChanged(isChecked));

        container.addView(row);
        return row;
    }

    private View addInfoRow(LinearLayout container, String title, String value, Runnable onClick) {
        return addInfoRow(container, title, null, value, onClick);
    }

    private View addInfoRow(LinearLayout container, String title, String subtitle, String value, Runnable onClick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_settings_row, container, false);
        TextView tvTitle = row.findViewById(R.id.tv_settings_row_title);
        TextView tvSubtitle = row.findViewById(R.id.tv_settings_row_subtitle);
        TextView tvValue = row.findViewById(R.id.tv_settings_row_value);

        tvTitle.setText(title);
        if (subtitle != null && !subtitle.isEmpty()) {
            tvSubtitle.setText(subtitle);
            tvSubtitle.setVisibility(View.VISIBLE);
        } else if (tvSubtitle != null) {
            tvSubtitle.setVisibility(View.GONE);
        }

        if (value != null && !value.isEmpty()) {
            tvValue.setText(value);
            tvValue.setVisibility(View.VISIBLE);
        } else if (tvValue != null) {
            tvValue.setVisibility(View.GONE);
        }

        if (onClick != null) {
            DebouncedOnClickListener.setSafeOnClickListener(row, v -> onClick.run());
        } else {
            row.setClickable(false);
        }

        container.addView(row);
        return row;
    }

    private interface SwitchCallback {
        void onChanged(boolean isChecked);
    }

    private void buildAccountScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username, level FROM user WHERE _id = 1", null);
        String username = "Adventurer";
        int level = 1;
        if (cursor != null && cursor.moveToFirst()) {
            username = cursor.getString(0);
            level = cursor.getInt(1);
            cursor.close();
        }

        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        String savedEmail = prefs.getString("user_email", "Guest Mode (Offline)");
        com.google.firebase.auth.FirebaseUser firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

        String userEmail = "Guest Mode (Offline)";
        if (firebaseUser != null && firebaseUser.getEmail() != null && !firebaseUser.getEmail().isEmpty()) {
            userEmail = firebaseUser.getEmail();
        } else if (isLoggedIn && savedEmail != null && !savedEmail.isEmpty() && savedEmail.contains("@")) {
            userEmail = savedEmail;
        }

        if (firebaseUser == null) {
            addInfoRow(container, "Sync to Cloud", "Back up your progress to cloud", "", () -> showSignUpAndSyncDialog());
        }

        addInfoRow(container, "Username", username, () -> showEditUsernameDialog());
        addInfoRow(container, "Email", userEmail, null);
        addInfoRow(container, "Level", String.valueOf(level), null);

        addSwitchRow(container, "Show Network Badge on Shop", "Display online tag in Shop header",
                prefs.getBoolean("pref_show_network_badge", false),
                isChecked -> prefs.edit().putBoolean("pref_show_network_badge", isChecked).apply());

        if (isLoggedIn || firebaseUser != null) {
            addInfoRow(container, "Log Out", "Tap to Log Out", () -> {
                showCustomConfirmDialog(
                        R.drawable.ic_info_circle, android.graphics.Color.parseColor("#FEE2E2"), androidx.core.content.ContextCompat.getColor(this, R.color.dagoal_error),
                        "Log Out?",
                        "Are you sure you want to log out of your account?",
                        "Log Out", R.drawable.bg_button_pill_danger,
                        () -> {
                            new TaskManager(this).syncUserProfileToFirestore(this);
                            if (!DatabaseHelper.resetAccountAndUserData(this)) {
                                ToastUtils.showToast(this, getString(R.string.logout_backup_failed));
                                return;
                            }
                            try {
                                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
                            } catch (Exception ignored) {}

                            ToastUtils.showToast(this, "Logged out successfully.");
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        },
                        "Cancel", null
                );
            });
        }

        addInfoRow(container, "Delete Account", "", () -> {
            androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
            android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_delete_account, null);
            builder.setView(dialogView);

            androidx.appcompat.app.AlertDialog dialog = builder.create();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            android.widget.Button btnCancel = dialogView.findViewById(R.id.btn_delete_cancel);
            android.widget.Button btnConfirm = dialogView.findViewById(R.id.btn_delete_confirm);
            android.widget.EditText etInput = dialogView.findViewById(R.id.et_delete_confirm_input);

            btnConfirm.setEnabled(false);
            btnConfirm.setAlpha(0.4f);

            if (etInput != null) {
                etInput.addTextChangedListener(new android.text.TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                    @Override
                    public void onTextChanged(CharSequence s, int start, int before, int count) {
                        boolean isValid = s != null && "DELETE".equalsIgnoreCase(s.toString().trim());
                        btnConfirm.setEnabled(isValid);
                        btnConfirm.setAlpha(isValid ? 1.0f : 0.4f);
                    }

                    @Override
                    public void afterTextChanged(android.text.Editable s) {}
                });
            }

            btnCancel.setOnClickListener(v -> dialog.dismiss());

            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();

                String uid = prefs.getString("user_uid", null);
                com.google.firebase.auth.FirebaseUser fUser = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser();

                if (fUser != null && fUser.getEmail() != null) {
                    showReAuthAndDeleteDialog(fUser, uid);
                } else {
                    performFullAccountDeletion(fUser, uid);
                }
            });

            dialog.show();
        });

        contentFrame.addView(container);
    }

    private void performFullAccountDeletion(com.google.firebase.auth.FirebaseUser fUser, String targetUid) {
        String uid = targetUid;
        if ((uid == null || uid.isEmpty()) && fUser != null) {
            uid = fUser.getUid();
        }

        final String deletedAccountUid = uid;
        Runnable finishLocalCleanup = () -> {
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance().signOut();
            } catch (Exception ignored) {}

            getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).edit().remove("user_uid").commit();
            boolean localDataCleared = DatabaseHelper.resetAccountAndUserData(this, false);
            RecurringQuestBackup.forget(this, deletedAccountUid);

            ToastUtils.showToast(this, localDataCleared ? "Account deleted successfully." : getString(R.string.account_delete_local_failed));
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        };

        if (fUser == null) {
            if (uid == null || uid.isEmpty()) finishLocalCleanup.run();
            else ToastUtils.showToast(this,getString(R.string.account_delete_online_failed));
            return;
        }
        // A failed cloud/authentication deletion must not be reported as successful.
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(fUser.getUid()).delete()
                .addOnSuccessListener(ignored -> fUser.delete()
                        .addOnSuccessListener(done -> finishLocalCleanup.run())
                        .addOnFailureListener(error -> ToastUtils.showToast(this,getString(R.string.account_delete_online_failed))))
                .addOnFailureListener(error -> ToastUtils.showToast(this,getString(R.string.account_delete_online_failed)));
    }

    private void showReAuthAndDeleteDialog(com.google.firebase.auth.FirebaseUser fUser, String uid) {
        if (fUser == null || fUser.getEmail() == null) {
            performFullAccountDeletion(fUser, uid);
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_reauth_delete, null);

        EditText editPassword = dialogView.findViewById(R.id.edit_reauth_password);
        ImageView btnTogglePass = dialogView.findViewById(R.id.btn_toggle_reauth_password);

        Button btnSubmit = dialogView.findViewById(R.id.btn_reauth_submit);
        Button btnCancel = dialogView.findViewById(R.id.btn_reauth_cancel);

        final boolean[] isVisible = {false};
        if (btnTogglePass != null && editPassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnTogglePass, v -> {
                isVisible[0] = !isVisible[0];
                if (isVisible[0]) {
                    editPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePass.setImageResource(R.drawable.ic_eye_visible);
                } else {
                    editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePass.setImageResource(R.drawable.ic_eye_hidden);
                }
                editPassword.setSelection(editPassword.getText().length());
            });
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnCancel, v -> dialog.dismiss());
        }

        if (btnSubmit != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnSubmit, v -> {
                String password = editPassword != null ? editPassword.getText().toString().trim() : "";
                if (password.isEmpty()) {
                    ToastUtils.showToast(this, "Please enter your password.");
                    return;
                }

                btnSubmit.setEnabled(false);
                btnSubmit.setText("Verifying...");

                com.google.firebase.auth.AuthCredential credential =
                        com.google.firebase.auth.EmailAuthProvider.getCredential(fUser.getEmail(), password);

                fUser.reauthenticate(credential).addOnCompleteListener(reAuthTask -> {
                    if (reAuthTask.isSuccessful()) {
                        dialog.dismiss();
                        performFullAccountDeletion(fUser, uid);
                    } else {
                        btnSubmit.setEnabled(true);
                        btnSubmit.setText("Confirm & Delete");
                        Exception exc = reAuthTask.getException();
                        String err = exc != null ? exc.getMessage() : "Incorrect password";
                        if (exc instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) {
                            ToastUtils.showToast(this, "Incorrect password. Please try again.");
                        } else {
                            ToastUtils.showToast(this, "Verification failed: " + err);
                        }
                    }
                });
            });
        }

        dialog.show();
    }

    private void showEditUsernameDialog() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT username FROM user WHERE _id = 1", null);
        String currentUsername = "Adventurer";
        if (cursor != null && cursor.moveToFirst()) {
            currentUsername = cursor.getString(0);
            cursor.close();
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_username, null);
        EditText editUsername = dialogView.findViewById(R.id.edit_username_input);
        Button btnSave = dialogView.findViewById(R.id.btn_username_save);
        Button btnCancel = dialogView.findViewById(R.id.btn_username_cancel);

        if (editUsername != null) {
            editUsername.setText(currentUsername);
            android.text.InputFilter alphaNumericFilter = (source, start, end, dest, dstart, dend) -> {
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                        return "";
                    }
                }
                return null;
            };
            editUsername.setFilters(new android.text.InputFilter[]{
                    alphaNumericFilter,
                    new android.text.InputFilter.LengthFilter(16)
            });
            editUsername.setSelection(Math.min(currentUsername.length(), 16));
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnCancel, v -> dialog.dismiss());
        }

        if (btnSave != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnSave, v -> {
                String rawInput = editUsername != null ? editUsername.getText().toString().trim() : "";
                String sanitized = rawInput.replaceAll("[^a-zA-Z0-9]", "");
                if (sanitized.isEmpty()) {
                    ToastUtils.showToast(this, "Username can only contain letters (A-Z, a-z) and numbers (0-9).");
                    return;
                }
                if (sanitized.length() > 16) {
                    sanitized = sanitized.substring(0, 16);
                }
                String newUsername = sanitized;

                android.content.ContentValues values = new android.content.ContentValues();
                values.put("username", newUsername);
                values.put(DatabaseContract.UserEntry.COLUMN_NAME, newUsername);
                SQLiteDatabase writableDb = dbHelper.getWritableDatabase();
                DatabaseHelper.ensureUserTableExists(writableDb);
                writableDb.update("user", values, "_id = 1", null);

                prefs.edit().putString("user_nickname", newUsername).apply();

                String uid = prefs.getString("user_uid", null);
                if (uid != null && !uid.isEmpty()) {
                    try {
                        com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                        java.util.Map<String, Object> map = new java.util.HashMap<>();
                        map.put("username", newUsername);
                        firestore.collection("users").document(uid).set(map, com.google.firebase.firestore.SetOptions.merge());
                    } catch (Exception ignored) {}
                }

                ToastUtils.showToast(this, "Username updated.");
                dialog.dismiss();
                showScreen("ACCOUNT");
            });
        }

        dialog.show();
    }

    private void showSignUpAndSyncDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_signup_sync, null);

        EditText editEmail = dialogView.findViewById(R.id.edit_sync_email);
        EditText editPassword = dialogView.findViewById(R.id.edit_sync_password);
        EditText editConfirmPassword = dialogView.findViewById(R.id.edit_sync_confirm_password);

        ImageView btnTogglePass = dialogView.findViewById(R.id.btn_toggle_sync_password);
        ImageView btnToggleConfirm = dialogView.findViewById(R.id.btn_toggle_sync_confirm_password);

        Button btnSubmit = dialogView.findViewById(R.id.btn_sync_submit);
        Button btnCancel = dialogView.findViewById(R.id.btn_sync_cancel);

        final boolean[] isPassVisible = {false};
        if (btnTogglePass != null && editPassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnTogglePass, v -> {
                isPassVisible[0] = !isPassVisible[0];
                if (isPassVisible[0]) {
                    editPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnTogglePass.setImageResource(R.drawable.ic_eye_visible);
                } else {
                    editPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnTogglePass.setImageResource(R.drawable.ic_eye_hidden);
                }
                editPassword.setSelection(editPassword.getText().length());
            });
        }

        final boolean[] isConfirmVisible = {false};
        if (btnToggleConfirm != null && editConfirmPassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnToggleConfirm, v -> {
                isConfirmVisible[0] = !isConfirmVisible[0];
                if (isConfirmVisible[0]) {
                    editConfirmPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                    btnToggleConfirm.setImageResource(R.drawable.ic_eye_visible);
                } else {
                    editConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    btnToggleConfirm.setImageResource(R.drawable.ic_eye_hidden);
                }
                editConfirmPassword.setSelection(editConfirmPassword.getText().length());
            });
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnCancel != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnCancel, v -> dialog.dismiss());
        }

        if (btnSubmit != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnSubmit, v -> {
                String email = editEmail != null ? editEmail.getText().toString().trim() : "";
                String password = editPassword != null ? editPassword.getText().toString().trim() : "";
                String confirmPassword = editConfirmPassword != null ? editConfirmPassword.getText().toString().trim() : "";

                if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                    ToastUtils.showToast(this, "Please fill in all fields.");
                    return;
                }
                if (!password.equals(confirmPassword)) {
                    ToastUtils.showToast(this, "Passwords do not match.");
                    return;
                }
                if (password.length() < 6) {
                    ToastUtils.showToast(this, "Password must be at least 6 characters.");
                    return;
                }

                btnSubmit.setEnabled(false);
                btnSubmit.setText("Syncing...");

                com.google.firebase.auth.FirebaseAuth mAuth = com.google.firebase.auth.FirebaseAuth.getInstance();
                mAuth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            if (task.isSuccessful()) {
                                com.google.firebase.auth.FirebaseUser fUser = mAuth.getCurrentUser();
                                String uid = fUser != null ? fUser.getUid() : "";

                                SQLiteDatabase db = dbHelper.getReadableDatabase();
                                Cursor cursor = db.rawQuery("SELECT username, level, gold, xp, streak FROM user WHERE _id = 1", null);
                                String username = "Adventurer";
                                int level = 1, gold = 0, xp = 0, streak = 0;
                                if (cursor != null && cursor.moveToFirst()) {
                                    username = cursor.getString(0);
                                    level = cursor.getInt(1);
                                    gold = cursor.getInt(2);
                                    xp = cursor.getInt(3);
                                    streak = cursor.getInt(4);
                                    cursor.close();
                                }

                                prefs.edit()
                                        .putBoolean("isLoggedIn", true)
                                        .putBoolean("isGuestUser", false)
                                        .putString("user_email", email)
                                        .putString("user_uid", uid)
                                        .putString("user_nickname", username)
                                        .apply();

                                com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                                java.util.Map<String, Object> userMap = new java.util.HashMap<>();
                                userMap.put("email", email);
                                userMap.put("username", username);
                                userMap.put("level", level);
                                userMap.put("gold", gold);
                                userMap.put("xp", xp);
                                userMap.put("streak", streak);

                                firestore.collection("users").document(uid).set(userMap, com.google.firebase.firestore.SetOptions.merge());

                                TaskManager tm = new TaskManager(this);
                                tm.syncUserProfileToFirestore(this);
                                AvatarConfig.saveUserAvatarToFirestore(this, uid);

                                ToastUtils.showToast(this, "Account created and profile synced! ☁️");
                                dialog.dismiss();
                                showScreen("ACCOUNT");
                            } else {
                                btnSubmit.setEnabled(true);
                                btnSubmit.setText("Sign Up & Sync");
                                String err = task.getException() != null ? task.getException().getMessage() : "Registration failed";
                                ToastUtils.showToast(this, "Sign up failed: " + err);
                            }
                        });
            });
        }

        dialog.show();
    }

    private void buildNotificationsScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(container, "Step Tracking", "Android requires an ongoing notification while background step tracking is active. Turning this off disables step tracking entirely.",
                prefs.getBoolean("pref_notif_steps", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_steps", isChecked).apply());

        addSwitchRow(container, "Avoidance Monitoring", "Android requires an ongoing notification while app-avoidance monitoring is active. Turning this off disables avoidance enforcement entirely.",
                prefs.getBoolean("pref_notif_avoidance", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_avoidance", isChecked).apply());

        addSwitchRow(container, "Daily Streak Popup", "Show a popup when you open the app each day.",
                prefs.getBoolean("pref_notif_streak_popup", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_streak_popup", isChecked).apply());

        addSwitchRow(container, "Achievement & Level-Up Alerts", "Show a toast when you unlock an achievement or level up.",
                prefs.getBoolean("pref_notif_toasts", true),
                isChecked -> prefs.edit().putBoolean("pref_notif_toasts", isChecked).apply());

        contentFrame.addView(container);
    }

    private void buildPrivacyScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        boolean hasUsageAccess = AppMonitorService.hasUsageAccess(this);
        addInfoRow(container, "Usage Access", hasUsageAccess ? "Granted" : "Not granted", () -> {
            startActivity(new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS));
        });

        boolean hasOverlay = android.provider.Settings.canDrawOverlays(this);
        addInfoRow(container, "Display Over Other Apps", hasOverlay ? "Granted" : "Not granted", () -> {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:" + getPackageName())));
        });

        android.os.PowerManager powerManager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        boolean batteryExempt = powerManager != null && powerManager.isIgnoringBatteryOptimizations(getPackageName());
        addInfoRow(container, "Battery Optimization", batteryExempt ? "Exempted" : "Not exempted", () -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        });

        boolean hasActivityRecognition = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        addInfoRow(container, "Activity Recognition (Steps)", hasActivityRecognition ? "Granted" : "Not granted", null);

        addInfoRow(container, "Manage Blocked Apps", "Edit list", () -> {
            Intent intent = new Intent(this, AppSelectionActivity.class);
            intent.putExtra("FROM_SETTINGS", true);
            startActivity(intent);
        });

        contentFrame.addView(container);
    }

    private void buildSoundScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        addSwitchRow(container, "Sound Effects", "",
                prefs.getBoolean("pref_sound_effects", true),
                isChecked -> prefs.edit().putBoolean("pref_sound_effects", isChecked).apply());

        addSwitchRow(container, "Vibration on Quest Complete", "",
                prefs.getBoolean("pref_haptics", true),
                isChecked -> prefs.edit().putBoolean("pref_haptics", isChecked).apply());

        contentFrame.addView(container);
    }

    private void buildAboutScreen() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        String versionName = "1.0";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }

        addInfoRow(container, "Version", versionName, null);
        addInfoRow(container, "Send Feedback", "shaunocana2004@gmail.com", () -> {
            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setData(android.net.Uri.parse("mailto:shaunocana2004@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "DaGoal App Feedback");
            String deviceInfo = "\n\n--- App & Device Info ---\nApp Version: 1.0" +
                    "\nDevice: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL +
                    "\nAndroid OS: " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")";
            intent.putExtra(Intent.EXTRA_TEXT, "Hello DaGoal Developer,\n\nI have the following feedback regarding the app:\n" + deviceInfo);
            try {
                startActivity(Intent.createChooser(intent, "Send Feedback via Email"));
            } catch (Exception e) {
                ToastUtils.showToast(this, "No email app found on device.");
            }
        });
        addInfoRow(container, getString(R.string.privacy_policy_title), getString(R.string.privacy_policy_view), "",
                () -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));

        contentFrame.addView(container);
    }

    private void showCustomConfirmDialog(int iconRes, int badgeBgColor, int iconTintColor,
                                         String title, String message,
                                         String posText, int posBtnBgRes, Runnable onPositive,
                                         String negText, Runnable onNegative) {
        android.view.View dialogView = getLayoutInflater().inflate(R.layout.dialog_confirmation_prompt, null);

        android.view.View frameIconBg = dialogView.findViewById(R.id.frame_confirm_icon_bg);
        android.widget.ImageView ivIcon = dialogView.findViewById(R.id.iv_confirm_icon);
        android.widget.TextView tvTitle = dialogView.findViewById(R.id.tv_confirm_title);
        android.widget.TextView tvMessage = dialogView.findViewById(R.id.tv_confirm_message);
        android.widget.Button btnNeg = dialogView.findViewById(R.id.btn_confirm_negative);
        android.widget.Button btnPos = dialogView.findViewById(R.id.btn_confirm_positive);

        if (frameIconBg != null && badgeBgColor != 0) {
            androidx.core.view.ViewCompat.setBackgroundTintList(frameIconBg, android.content.res.ColorStateList.valueOf(badgeBgColor));
        }
        if (ivIcon != null) {
            ivIcon.setImageResource(iconRes != 0 ? iconRes : R.drawable.ic_info_circle);
            if (iconTintColor != 0) {
                androidx.core.widget.ImageViewCompat.setImageTintList(ivIcon, android.content.res.ColorStateList.valueOf(iconTintColor));
            }
        }

        if (tvTitle != null) tvTitle.setText(title);
        if (tvMessage != null) tvMessage.setText(message);

        if (btnNeg != null) {
            btnNeg.setText(negText != null ? negText : "Cancel");
        }
        if (btnPos != null) {
            btnPos.setText(posText != null ? posText : "Confirm");
            if (posBtnBgRes != 0) {
                btnPos.setBackgroundResource(posBtnBgRes);
            }
        }

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(this);
        builder.setView(dialogView);
        androidx.appcompat.app.AlertDialog dialog = builder.create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btnNeg != null) {
            btnNeg.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegative != null) onNegative.run();
            });
        }

        if (btnPos != null) {
            btnPos.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositive != null) onPositive.run();
            });
        }

        dialog.show();
    }
}
