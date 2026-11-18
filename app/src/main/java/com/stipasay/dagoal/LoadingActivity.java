package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoadingActivity extends AppCompatActivity {

    private static final long MIN_LOADING_TIME = 1500; // 1.5 seconds baseline

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loading);

        findViewById(R.id.btn_loading_retry).setOnClickListener(view -> runStartupSequence());
        runStartupSequence();
    }

    private void runStartupSequence() {
        findViewById(R.id.loading_indicator).setVisibility(View.VISIBLE);
        findViewById(R.id.loading_error).setVisibility(View.GONE);
        long startTime = System.currentTimeMillis();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler mainHandler = new Handler(Looper.getMainLooper());

        executor.execute(() -> {
            try (DatabaseHelper dbHelper = new DatabaseHelper(LoadingActivity.this)) {
                dbHelper.getReadableDatabase();
            } catch (SQLiteException error) {
                Log.e("LoadingActivity", "Could not open the local database", error);
                mainHandler.post(() -> showStartupError(R.string.loading_database_error));
                return;
            }

            if (!recoverAccountDeletion()) {
                mainHandler.post(() -> showStartupError(R.string.account_delete_recovery_retry));
                return;
            }
            Intent nextIntent = determineNextDestination();

            long elapsedTime = System.currentTimeMillis() - startTime;
            long delay = Math.max(0, MIN_LOADING_TIME - elapsedTime);

            mainHandler.postDelayed(() -> {
                if (isFinishing() || isDestroyed()) return;
                startActivity(nextIntent);
                overridePendingTransition(R.anim.slide_in_bottom, R.anim.slide_out_top);
                finish();
            }, delay);
        });
        executor.shutdown();
    }

    private void showStartupError(int message) {
        if (isFinishing() || isDestroyed()) return;
        TextView errorMessage = findViewById(R.id.loading_error_message);
        errorMessage.setText(message);
        findViewById(R.id.loading_indicator).setVisibility(View.GONE);
        findViewById(R.id.loading_error).setVisibility(View.VISIBLE);
    }

    private boolean recoverAccountDeletion() {
        String pendingUid = AccountDeletion.pendingUid(this);
        if (pendingUid == null) return true;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return AccountDeletion.clearLocal(this, pendingUid);
        if (!pendingUid.equals(user.getUid())) return true;

        try {
            Tasks.await(user.reload(), 8, TimeUnit.SECONDS);
            AccountDeletion.clearRequest(this, pendingUid);
            return true;
        } catch (java.util.concurrent.ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof FirebaseAuthInvalidUserException
                    && "ERROR_USER_NOT_FOUND".equals(((FirebaseAuthInvalidUserException) cause).getErrorCode())) {
                auth.signOut();
                return AccountDeletion.clearLocal(this, pendingUid);
            }
            return false;
        } catch (java.util.concurrent.TimeoutException timeout) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Intent determineNextDestination() {
        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        Class<?> destination = resolveStartupDestination(prefs, user != null, today);

        // Restore local session metadata from Firebase's persisted session, without a network request.
        if (user != null && !prefs.getBoolean("isGuestUser", false)) {
            SharedPreferences.Editor editor = prefs.edit()
                    .putBoolean("isLoggedIn", true)
                    .putString("user_uid", user.getUid());
            if (user.getEmail() != null) editor.putString("user_email", user.getEmail());
            editor.apply();
        }
        if (destination == DashboardActivity.class || destination == DailyRevealActivity.class) {
            // Repair returning accounts affected by older login flows that cleared this marker.
            prefs.edit().putBoolean("isOnboardingComplete", true)
                    .putBoolean("isFirstRun", false).apply();
        }
        return new Intent(this, destination);
    }

    static Class<?> resolveStartupDestination(SharedPreferences prefs, boolean hasFirebaseSession, String today) {
        boolean guest = prefs.getBoolean("isGuestUser", false);
        boolean hasSession = prefs.getBoolean("isLoggedIn", false) || guest || hasFirebaseSession;
        if (!hasSession) return MainActivity.class;

        boolean complete = prefs.getBoolean("isOnboardingComplete", false);
        int step = prefs.getInt("pref_onboarding_step", 0);
        // Legacy successful logins restored an avatar but omitted the onboarding-complete flag.
        boolean returningAccount = !guest && step == 0 && prefs.contains("pref_avatar_skin");
        if (!complete && !returningAccount) {
            if (step >= 3) return AppSelectionActivity.class;
            if (step == 2) return ProfilingActivity.class;
            return AvatarCreationActivity.class;
        }
        return today.equals(prefs.getString("last_quest_generation_date", ""))
                ? DashboardActivity.class : DailyRevealActivity.class;
    }
}
