package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;

/** Records local cleanup separately so a restart can finish it. */
final class AccountDeletion {
    private static final String PENDING_UID = "pending_uid";

    private AccountDeletion() {}

    private static SharedPreferences journal(Context context) {
        return context.getSharedPreferences("AccountDeletion", Context.MODE_PRIVATE);
    }

    static String pendingUid(Context context) {
        return journal(context).getString(PENDING_UID, null);
    }

    static boolean recordRequest(Context context, String uid) {
        return journal(context).edit().putString(PENDING_UID, uid).commit();
    }

    static synchronized void clearRequest(Context context, String uid) {
        if (uid.equals(pendingUid(context))) journal(context).edit().remove(PENDING_UID).commit();
    }

    static synchronized boolean clearLocal(Context context, String uid) {
        if (uid != null) {
            if (!uid.equals(pendingUid(context))) return true;
            SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            String owner = prefs.getString("user_uid", null);
            if (prefs.getBoolean("isGuestUser", false) || (owner != null && !uid.equals(owner))) {
                RecurringQuestBackup.forget(context, uid);
                clearRequest(context, uid);
                return true;
            }
        }
        boolean cleared = DatabaseHelper.resetAccountAndUserData(context, false);
        RecurringQuestBackup.forget(context, uid);
        DailyReminderScheduler.cancel(context);
        if (cleared && uid != null) clearRequest(context, uid);
        return cleared;
    }
}
