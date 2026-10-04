package com.stipasay.dagoal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DailyNotificationReceiver extends BroadcastReceiver {

    public static final String ACTION_MIDNIGHT_QUESTS = "com.stipasay.dagoal.ACTION_MIDNIGHT_QUESTS";
    public static final String ACTION_EVENING_REMINDER = "com.stipasay.dagoal.ACTION_EVENING_REMINDER";
    private static final String CHANNEL_ID = "dagoal_daily_notifications";
    private static final int NOTIF_MIDNIGHT_ID = 801;
    private static final int NOTIF_EVENING_ID = 802;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        createNotificationChannel(context);

        if (ACTION_MIDNIGHT_QUESTS.equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            sendNotification(context, NOTIF_MIDNIGHT_ID,
                    "New Daily Quests Available! 📜",
                    "Your new daily quests are ready! Open DaGoal to reveal your quests for today.");
        } else if (ACTION_EVENING_REMINDER.equals(action)) {
            if (hasIncompleteDailyQuestsToday(context)) {
                sendNotification(context, NOTIF_EVENING_ID,
                        "Quests Pending Completion! ⏳",
                        "You still have incomplete daily quests remaining today. Finish them before midnight to maintain your streak!");
            }
        }
    }

    private boolean hasIncompleteDailyQuestsToday(Context context) {
        try {
            DatabaseHelper dbHelper = new DatabaseHelper(context);
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

            Cursor cursor = db.rawQuery(
                    "SELECT COUNT(*) FROM daily_tasks WHERE task_date = ? AND is_completed = 0",
                    new String[]{ todayDateStr }
            );

            boolean hasIncomplete = false;
            if (cursor != null) {
                if (cursor.moveToFirst()) {
                    hasIncomplete = cursor.getInt(0) > 0;
                }
                cursor.close();
            }
            return hasIncomplete;
        } catch (Exception e) {
            return true;
        }
    }

    private void sendNotification(Context context, int notificationId, String title, String body) {
        Intent intent = new Intent(context, DashboardActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.quest_icon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(notificationId, builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Daily Quest Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for daily new quests and evening quest reminders.");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
