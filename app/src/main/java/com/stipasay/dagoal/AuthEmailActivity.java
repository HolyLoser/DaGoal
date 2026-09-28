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
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class AuthEmailActivity extends AppCompatActivity {

    private EditText editAuthEmail;
    private EditText editAuthPassword;
    private ImageView btnToggleVisibility;
    private Button btnEmailLogin;
    private boolean isPasswordVisible = false;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_email);

        dbHelper = new DatabaseHelper(this);

        ImageButton btnBack = findViewById(R.id.btn_back_email);
        editAuthEmail = findViewById(R.id.edit_auth_email);
        editAuthPassword = findViewById(R.id.edit_auth_password);
        btnToggleVisibility = findViewById(R.id.btn_toggle_password_visibility);
        btnEmailLogin = findViewById(R.id.btn_email_login);
        TextView tvForgotPassword = findViewById(R.id.tv_forgot_password);
        TextView tvGoToSignUp = findViewById(R.id.tv_go_to_signup);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        if (btnToggleVisibility != null) {
            btnToggleVisibility.setOnClickListener(v -> togglePasswordVisibility());
        }

        if (tvForgotPassword != null) {
            tvForgotPassword.setOnClickListener(v -> handleForgotPassword());
        }

        if (tvGoToSignUp != null) {
            tvGoToSignUp.setOnClickListener(v -> {
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
            btnEmailLogin.setOnClickListener(v -> {
                v.setEnabled(false);
                processLoginAuthentication();
                v.postDelayed(() -> checkInputValidation(), 2000);
            });
        }
    }

    private void togglePasswordVisibility() {
        if (editAuthPassword == null) return;
        isPasswordVisible = !isPasswordVisible;

        if (isPasswordVisible) {
            editAuthPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        } else {
            editAuthPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }

        editAuthPassword.setSelection(editAuthPassword.getText().length());
    }

    private void checkInputValidation() {
        if (editAuthEmail == null || editAuthPassword == null || btnEmailLogin == null) return;

        String email = editAuthEmail.getText().toString().trim();
        String password = editAuthPassword.getText().toString().trim();

        boolean isValidEmail = !email.isEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
        boolean isValidPassword = password.length() >= 6;

        boolean canSubmit = isValidEmail && isValidPassword;
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

        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        mAuth.signInWithEmailAndPassword(email, password)
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
                                          .putString("user_email", email)
                                          .putString("user_uid", uid);

                                    if (doc != null && doc.exists()) {
                                        String username = doc.getString("username");
                                        if (username != null && !username.isEmpty()) {
                                            editor.putString("user_nickname", username);
                                            syncUserToLocalDb(username);
                                        }
                                    }
                                    editor.apply();

                                    ToastUtils.showToast(AuthEmailActivity.this, "Welcome back!");
                                    Intent intent = new Intent(AuthEmailActivity.this, DashboardActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                })
                                .addOnFailureListener(e -> showLowConnectivityDialog(email));
                    } else {
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
        new AlertDialog.Builder(this)
                .setTitle("Connectivity Notice")
                .setMessage("Due to low connectivity, we can't communicate with our online database. You can still proceed, but your progress will not be synced to the cloud right now. You can sync your account in settings later.")
                .setPositiveButton("Proceed Offline", (dialog, which) -> proceedWithLocalLogin(email))
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void proceedWithLocalLogin(String email) {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        boolean hasExistingAccount = checkExistingUserAccountInDb();

        prefs.edit()
                .putBoolean("isLoggedIn", true)
                .putBoolean("isGuestUser", false)
                .putString("user_email", email)
                .apply();

        if (hasExistingAccount) {
            prefs.edit().putBoolean("isOnboardingComplete", true).putBoolean("isFirstRun", false).apply();
            ToastUtils.showToast(this, "Welcome back!");
            Intent intent = new Intent(AuthEmailActivity.this, DashboardActivity.class);
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
