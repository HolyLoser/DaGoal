package com.stipasay.dagoal;

import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

/** Local profiles may be resumed only for the authenticated account that owns them. */
final class AccountSessionHelper {
    private AccountSessionHelper() {}

    static boolean ownsLocalProfile(SharedPreferences prefs, String authenticatedUid) {
        return authenticatedUid != null && !authenticatedUid.isEmpty()
                && !prefs.getBoolean("isGuestUser", false)
                && authenticatedUid.equals(prefs.getString("user_uid", ""));
    }

    static boolean canResumeOffline(SharedPreferences prefs, String uid, String authenticatedEmail, String requestedEmail) {
        return ownsLocalProfile(prefs, uid) && authenticatedEmail != null && requestedEmail != null
                && authenticatedEmail.equalsIgnoreCase(requestedEmail.trim())
                && authenticatedEmail.equalsIgnoreCase(prefs.getString("user_email", ""));
    }

    static boolean canResumeOffline(SharedPreferences prefs, FirebaseUser user, String email) {
        return user != null && canResumeOffline(prefs, user.getUid(), user.getEmail(), email);
    }

    static Class<?> nextScreen(SharedPreferences prefs) {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(new java.util.Date());
        return LoadingActivity.resolveStartupDestination(prefs, false, today);
    }

    static void restorePreviousAuthentication(FirebaseAuth auth, FirebaseUser previousUser) {
        auth.signOut();
        if (previousUser != null) auth.updateCurrentUser(previousUser)
                .addOnFailureListener(error -> android.util.Log.w("AccountSession", "Previous session could not be restored"));
    }
}
