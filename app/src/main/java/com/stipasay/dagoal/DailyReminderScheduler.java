package com.stipasay.dagoal;

import android.content.Intent;

/** Restores the same two daily reminders after app startup or device reboot. */
final class DailyReminderScheduler {
    static void schedule(android.content.Context context) {
        try {
            android.app.AlarmManager alarmManager = (android.app.AlarmManager) context.getSystemService(android.content.Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            java.util.Calendar calMidnight = java.util.Calendar.getInstance();
            calMidnight.set(java.util.Calendar.HOUR_OF_DAY, 0);
            calMidnight.set(java.util.Calendar.MINUTE, 0);
            calMidnight.set(java.util.Calendar.SECOND, 0);
            if (calMidnight.getTimeInMillis() <= System.currentTimeMillis()) {
                calMidnight.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            Intent intentMidnight = new android.content.Intent(context, DailyNotificationReceiver.class);
            intentMidnight.setAction(DailyNotificationReceiver.ACTION_MIDNIGHT_QUESTS);
            android.app.PendingIntent piMidnight = android.app.PendingIntent.getBroadcast(
                    context, 801, intentMidnight,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.setInexactRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calMidnight.getTimeInMillis(),
                    android.app.AlarmManager.INTERVAL_DAY,
                    piMidnight
            );

            java.util.Calendar calEvening = java.util.Calendar.getInstance();
            calEvening.set(java.util.Calendar.HOUR_OF_DAY, 20);
            calEvening.set(java.util.Calendar.MINUTE, 0);
            calEvening.set(java.util.Calendar.SECOND, 0);
            if (calEvening.getTimeInMillis() <= System.currentTimeMillis()) {
                calEvening.add(java.util.Calendar.DAY_OF_YEAR, 1);
            }

            Intent intentEvening = new android.content.Intent(context, DailyNotificationReceiver.class);
            intentEvening.setAction(DailyNotificationReceiver.ACTION_EVENING_REMINDER);
            android.app.PendingIntent piEvening = android.app.PendingIntent.getBroadcast(
                    context, 802, intentEvening,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
            );
            alarmManager.setInexactRepeating(
                    android.app.AlarmManager.RTC_WAKEUP,
                    calEvening.getTimeInMillis(),
                    android.app.AlarmManager.INTERVAL_DAY,
                    piEvening
            );
        } catch (Exception ignored) {}
    }

}
