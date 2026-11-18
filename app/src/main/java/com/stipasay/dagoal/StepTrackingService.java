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
import android.content.pm.PackageManager;

public class StepTrackingService extends Service implements SensorEventListener {

    private static final String CHANNEL_ID = "dagoal_step_tracking_channel";
    private static final int NOTIFICATION_ID = 501;
    private static final String PREFS_NAME = "DaGoalStepPrefs";

    private SensorManager sensorManager;
    private Sensor stepCounterSensor;
    private Sensor stepDetectorSensor;

    @Override
    public void onCreate() {
        super.onCreate();
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
            stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        createNotificationChannel();

        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int lastKnownSteps = 0;
        if (todayDateStr.equals(prefs.getString("last_reported_date", ""))) {
            lastKnownSteps = prefs.getInt("last_reported_steps", 0);
        }

        startForeground(NOTIFICATION_ID, buildNotificationWithSteps(lastKnownSteps));

        if (hasIncompleteStepQuestToday()) {
            if (sensorManager != null) {
                if (stepCounterSensor != null) {
                    sensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST);
                } else if (stepDetectorSensor != null) {
                    sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
        } else {
            stopSelf();
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
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        int stepsToday;

        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            stepsToday = recordCounterSteps(prefs,todayDateStr,(int)event.values[0]);
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            stepsToday = recordDetectedSteps(prefs,todayDateStr,Math.round(event.values[0]));
        } else {
            return;
        }

        TaskManager taskManager = new TaskManager(this);
        taskManager.updateTaskProgress(DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS, stepsToday);

        Intent progressIntent = new Intent("com.stipasay.dagoal.TASK_PROGRESS_UPDATED").setPackage(getPackageName());
        sendBroadcast(progressIntent);

        if (hasIncompleteStepQuestToday()) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, buildNotificationWithSteps(stepsToday));
            }
        } else {
            stopSelf();
        }
    }

    static int recordCounterSteps(SharedPreferences prefs,String date,int rawTotal) {
        int last=date.equals(prefs.getString("last_reported_date",""))?prefs.getInt("last_reported_steps",0):0;
        int baseline=prefs.getInt("baseline_steps",-1);
        int offset=prefs.getInt("baseline_offset",0);
        int previousRaw=prefs.getInt("last_counter_total",baseline);
        if(!date.equals(prefs.getString("baseline_date",""))) {baseline=rawTotal;offset=0;}
        else if(baseline<0 || rawTotal<previousRaw) {baseline=rawTotal;offset=last;}
        int steps=offset+Math.max(0,rawTotal-baseline);
        prefs.edit().putString("baseline_date",date).putInt("baseline_steps",baseline).putInt("baseline_offset",offset).putInt("last_counter_total",rawTotal)
                .putString("last_reported_date",date).putInt("last_reported_steps",steps).apply();
        return steps;
    }

    static int recordDetectedSteps(SharedPreferences prefs,String date,int delta) {
        int last=date.equals(prefs.getString("last_reported_date",""))?prefs.getInt("last_reported_steps",0):0;
        int steps=last+Math.max(0,delta);
        prefs.edit().putString("last_reported_date",date).putInt("last_reported_steps",steps).apply();
        return steps;
    }

    private boolean hasIncompleteStepQuestToday() {
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        String query = "SELECT COUNT(*) FROM " + DatabaseContract.DailyTaskEntry.TABLE_NAME +
                " WHERE " + DatabaseContract.DailyTaskEntry.COLUMN_QUEST_TYPE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_DATE + " = ? AND " +
                DatabaseContract.DailyTaskEntry.COLUMN_IS_COMPLETED + " = 0";

        android.database.Cursor cursor = db.rawQuery(query, new String[]{
                DatabaseContract.DailyTaskEntry.QUEST_TYPE_STEPS, todayDateStr
        });

        boolean hasIncomplete = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                hasIncomplete = cursor.getInt(0) > 0;
            }
            cursor.close();
        }
        return hasIncomplete;
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

    public static int getStepsToday(Context context) {
        if (context == null) return 0;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (todayDateStr.equals(prefs.getString("last_reported_date", ""))) {
            return prefs.getInt("last_reported_steps", 0);
        }
        return 0;
    }

    public static void start(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        Intent intent = new Intent(context, StepTrackingService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, StepTrackingService.class);
        context.stopService(intent);
    }
}