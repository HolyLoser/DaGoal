package com.stipasay.dagoal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class StepTrackingService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "dagoal_step_tracking_channel";
    private static final int NOTIFICATION_ID = 501;
    private static final String PREFS_NAME = "DaGoalStepPrefs";

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotificationWithSteps(0));

        if (sensorManager != null && stepCounterSensor != null) {
            sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_STEP_COUNTER) {
            return;
        }

        int totalStepsSinceBoot = (int) event.values[0];
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String baselineDate = prefs.getString("baseline_date", "");
        int baselineSteps = prefs.getInt("baseline_steps", -1);

        if (!todayDateStr.equals(baselineDate) || baselineSteps < 0) {
            baselineSteps = totalStepsSinceBoot;
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("baseline_date", todayDateStr);
            editor.putInt("baseline_steps", baselineSteps);
            editor.apply();
        }

        int stepsToday = totalStepsSinceBoot - baselineSteps;
        if (stepsToday < 0) {
            stepsToday = 0;
        }

        TaskManager taskManager = new TaskManager(this);
        taskManager.updateTaskProgress(DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS, stepsToday);

        Intent progressIntent = new Intent("com.stipasay.dagoal.TASK_PROGRESS_UPDATED");
        sendBroadcast(progressIntent);

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, buildNotificationWithSteps(stepsToday));
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Step Tracking",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Tracks your daily steps for DaGoal quests");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotificationWithSteps(int steps) {
        Intent launchIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DaGoal is tracking your steps")
                .setContentText(steps + " steps today")
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, StepTrackingService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, StepTrackingService.class);
        context.stopService(intent);
    }
}