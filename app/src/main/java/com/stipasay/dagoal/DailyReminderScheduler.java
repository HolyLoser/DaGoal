package com.stipasay.dagoal;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/** Restores the same two daily reminders after app startup or device reboot. */
final class DailyReminderScheduler {
    static boolean hasActiveSession(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("isGuestUser", false) || prefs.getBoolean("isLoggedIn", false);
    }

    static void cancel(Context context) {
        cancelAlarm(context, DailyNotificationReceiver.NOTIF_MIDNIGHT_ID, DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS);
        cancelAlarm(context, DailyNotificationReceiver.NOTIF_EVENING_ID, DailyNotificationReceiver.ACTION_EVENING_REMINDER);
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) {
            notifications.cancel(DailyNotificationReceiver.NOTIF_MIDNIGHT_ID);
            notifications.cancel(DailyNotificationReceiver.NOTIF_EVENING_ID);
        }
    }

    private static void cancelAlarm(Context context, int requestCode, String action) {
        Intent intent = new Intent(context, DailyNotificationReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pending == null) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
    }

    static void schedule(Context context) {
        if (!hasActiveSession(context)) return;
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        if (alarms == null) return;
        try {
            scheduleAlarm(context, alarms, DailyNotificationReceiver.NOTIF_MIDNIGHT_ID,
                    DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS, 0);
            scheduleAlarm(context, alarms, DailyNotificationReceiver.NOTIF_EVENING_ID,
                    DailyNotificationReceiver.ACTION_EVENING_REMINDER, 20);
        } catch (RuntimeException error) {
            Log.w("DailyReminder", "Could not schedule daily reminders", error);
        }
    }

    private static void scheduleAlarm(Context context, AlarmManager alarms, int requestCode,
                                      String action, int hour) {
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) {
            next.add(Calendar.DAY_OF_YEAR, 1);
        }

        Intent intent = new Intent(context, DailyNotificationReceiver.class).setAction(action);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setInexactRepeating(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(),
                AlarmManager.INTERVAL_DAY, pending);
    }
}
