package com.stipasay.dagoal;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AppMonitorService extends Service {

    private static final String CHANNEL_ID = "dagoal_app_monitor_channel";
    private static final int NOTIFICATION_ID = 601;
    private static final long POLL_INTERVAL_MS = 800;

    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private AvoidanceOverlayManager overlayManager;
    private Integer currentlyInterruptingTaskId = null;

    @Override
    public void onCreate() {
        super.onCreate();
        overlayManager = new AvoidanceOverlayManager(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        startPolling();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (overlayManager != null) {
            overlayManager.dismiss();
        }
    }

    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                checkForegroundApp();
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private String lastDetectedPackage = null;
    private long packageFirstDetectedTime = 0L;

    private void checkForegroundApp() {
        pollHandler.removeCallbacksAndMessages(null);

        TaskManager taskManager = new TaskManager(this);
        List<TaskManager.ActiveAvoidanceQuest> activeQuests = taskManager.getStartedAvoidanceQuests();

        if (activeQuests.isEmpty()) {
            stopSelf();
            return;
        }

        long now = System.currentTimeMillis();

        if (currentlyInterruptingTaskId != null) {
            pollHandler.postDelayed(this::checkForegroundApp, POLL_INTERVAL_MS);
            return;
        }

        String foregroundPackage = getForegroundPackageName();

        Set<String> allBlockedPackages = new HashSet<>();
        for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
            if (quest.targetPackages != null) {
                allBlockedPackages.addAll(quest.targetPackages);
            }
        }

        if (foregroundPackage != null && allBlockedPackages.contains(foregroundPackage)) {
            if (!foregroundPackage.equals(lastDetectedPackage)) {
                lastDetectedPackage = foregroundPackage;
                packageFirstDetectedTime = now;
            }

            for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
                boolean isBlockedAppInForeground = quest.targetPackages != null && quest.targetPackages.contains(foregroundPackage);
                long snoozeUntil = taskManager.getSnoozeUntil(quest.taskId);

                if (!isBlockedAppInForeground || now < snoozeUntil) {
                    continue;
                }

                if (currentlyInterruptingTaskId == null) {
                    currentlyInterruptingTaskId = quest.taskId;
                    overlayManager.show(quest, () -> currentlyInterruptingTaskId = null);
                }
            }
        } else {
            lastDetectedPackage = null;
        }

        pollHandler.postDelayed(this::checkForegroundApp, POLL_INTERVAL_MS);
    }

    private String getForegroundPackageName() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return null;
        }

        long now = System.currentTimeMillis();
        long startTime = now - 600000L;

        try {
            List<android.app.usage.UsageStats> statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    now
            );
            if (statsList != null && !statsList.isEmpty()) {
                android.app.usage.UsageStats recentStats = null;
                for (android.app.usage.UsageStats stats : statsList) {
                    if (stats == null || stats.getPackageName() == null) continue;
                    if (getPackageName().equalsIgnoreCase(stats.getPackageName())) continue;

                    if (recentStats == null || stats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                        recentStats = stats;
                    }
                }
                if (recentStats != null) {
                    return recentStats.getPackageName();
                }
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "App Avoidance Monitoring",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, DashboardActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("DaGoal is watching for blocked apps")
                .setContentText("Avoidance quest active")
                .setSmallIcon(android.R.drawable.ic_menu_agenda)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, AppMonitorService.class);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, AppMonitorService.class);
        context.stopService(intent);
    }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        } else {
            mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}