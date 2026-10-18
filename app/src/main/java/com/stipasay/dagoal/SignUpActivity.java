package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {

    private EditText editEmail, editPassword;
    private ImageView btnTogglePassword;
    private Button btnCreateAccount;
    private boolean isPasswordVisible = false;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_sign_up);

        View rootLayout = findViewById(R.id.root_signup_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btn_back_signup);
        editEmail = findViewById(R.id.edit_signup_email);
        editPassword = findViewById(R.id.edit_signup_password);
        btnTogglePassword = findViewById(R.id.btn_toggle_signup_password);
        btnCreateAccount = findViewById(R.id.btn_create_account);
        TextView tvGoToLogin = findViewById(R.id.tv_go_to_login);

        if (btnBack != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnBack, v -> finish());
        }

        if (btnTogglePassword != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnTogglePassword, v -> togglePasswordVisibility());
        }

        if (tvGoToLogin != null) {
            DebouncedOnClickListener.setSafeOnClickListener(tvGoToLogin, v -> finish());
        }

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkValidation();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        if (editEmail != null) editEmail.addTextChangedListener(watcher);
        if (editPassword != null) {
            editPassword.addTextChangedListener(watcher);
            editPassword.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER && event.getAction() == android.view.KeyEvent.ACTION_DOWN)) {
                    if (btnCreateAccount != null && btnCreateAccount.isEnabled()) {
                        processAccountRegistration();
                    }
                    return true;
                }
                return false;
            });
        }

        if (btnCreateAccount != null) {
            DebouncedOnClickListener.setSafeOnClickListener(btnCreateAccount, v -> {
                v.setEnabled(false);
                processAccountRegistration();
                v.postDelayed(() -> checkValidation(), 2000);
            });
        }
    }

    private void togglePasswordVisibility() {
        if (editPassword == null) return;
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            editPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            editPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        editPassword.setSelection(editPassword.getText().length());
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        KeyboardUtils.hideKeyboardOnTouchOutside(this, ev);
        return super.dispatchTouchEvent(ev);
    }

    private void checkValidation() {
        if (editEmail == null || editPassword == null || btnCreateAccount == null) return;

        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        boolean isValidEmail = !email.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean isValidPassword = password.length() >= 6;

        boolean canSubmit = isValidEmail && isValidPassword;
        btnCreateAccount.setEnabled(canSubmit);
        btnCreateAccount.setBackgroundResource(canSubmit ? R.drawable.bg_auth_pill_green : R.drawable.bg_auth_pill_disabled);
    }

    private void processAccountRegistration() {
        if (editEmail == null || editPassword == null) return;

        String email = editEmail.getText().toString().trim();
        String password = editPassword.getText().toString().trim();

        if (email.isEmpty()) {
            ToastUtils.showToast(this, "Please enter your email address.");
            return;
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            ToastUtils.showToast(this, "Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            ToastUtils.showToast(this, "Password must be at least 6 characters.");
            return;
        }

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        FirebaseUser firebaseUser = mAuth.getCurrentUser();
                        String uid = firebaseUser != null ? firebaseUser.getUid() : "";

                        // Store user document in Firestore cloud
                        FirebaseFirestore firestore = FirebaseFirestore.getInstance();
                        Map<String, Object> userMap = new HashMap<>();
                        String defaultUsername = email.contains("@") ? email.split("@")[0] : "Adventurer";
                        userMap.put("email", email);
                        userMap.put("username", defaultUsername);
                        userMap.put("level", 1);
                        userMap.put("gold", 0);
                        userMap.put("xp", 0);
                        userMap.put("streak", 0);

                        firestore.collection("users").document(uid).set(userMap);

                        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
                        prefs.edit()
                                .putBoolean("isLoggedIn", true)
                                .putBoolean("isGuestUser", false)
                                .putString("user_email", email)
                                .putString("user_uid", uid)
                                .putString("user_nickname", defaultUsername)
                                .apply();

                        createInitialUserAccountInDb(email);

                        ToastUtils.showToast(SignUpActivity.this, "Account Created! Let's set up your avatar.");

                        Intent intent = new Intent(SignUpActivity.this, AvatarCreationActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        Exception exception = task.getException();
                        String errorMsg = exception != null ? exception.getMessage() : "Registration failed";

                        if (exception instanceof com.google.firebase.auth.FirebaseAuthUserCollisionException
                                || (errorMsg != null && (errorMsg.contains("already in use") || errorMsg.contains("already exists")))) {
                            ToastUtils.showToast(SignUpActivity.this, "An account with this email already exists. Please Login instead.");
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

        if (tvTitle != null) tvTitle.setText("Connectivity Notice");
        if (tvMessage != null) tvMessage.setText("Due to low connectivity, we can't communicate with our online database. You can still proceed, but your progress will not be synced to the cloud right now. You can sync your account in settings later.");

        if (btnNeg != null) btnNeg.setText("Cancel");
        if (btnPos != null) {
            btnPos.setText("Proceed Offline");
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
                proceedOfflineRegistration(email);
            });
        }

        dialog.show();
    }

    private void proceedOfflineRegistration(String email) {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putBoolean("isGuestUser", false)
                .putString("user_email", email)
                .apply();

        createInitialUserAccountInDb(email);

        Intent intent = new Intent(SignUpActivity.this, AvatarCreationActivity.class);
        startActivity(intent);
        finish();
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
