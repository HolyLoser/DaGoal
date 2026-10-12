package com.stipasay.dagoal;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
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
    private static final long POLL_INTERVAL_MS = 3000;

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
        TaskManager taskManager = new TaskManager(this);
        List<TaskManager.ActiveAvoidanceQuest> activeQuests = taskManager.getStartedAvoidanceQuests();

        if (activeQuests.isEmpty()) {
            stopSelf();
            return;
        }

        String foregroundPackage = getForegroundPackageName();
        long now = System.currentTimeMillis();

        Set<String> allBlockedPackages = new HashSet<>();
        for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
            if (quest.targetPackages != null) {
                allBlockedPackages.addAll(quest.targetPackages);
            }
        }

        if (foregroundPackage == null || !allBlockedPackages.contains(foregroundPackage)) {
            for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
                long snoozeUntil = taskManager.getSnoozeUntil(quest.taskId);
                if (snoozeUntil > now) {
                    long unusedMs = snoozeUntil - now;
                    taskManager.cancelSnoozeAndResumeTimer(quest.taskId, unusedMs);
                }
            }

            lastDetectedPackage = null;
            if (currentlyInterruptingTaskId != null) {
                if (overlayManager != null) {
                    overlayManager.dismiss();
                }
                currentlyInterruptingTaskId = null;
            }
            return;
        }

        if (!foregroundPackage.equals(lastDetectedPackage)) {
            lastDetectedPackage = foregroundPackage;
            packageFirstDetectedTime = now;
        }

        if (currentlyInterruptingTaskId != null) {
            boolean stillInBlockedApp = false;
            for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
                if (quest.taskId == currentlyInterruptingTaskId && quest.targetPackages != null && quest.targetPackages.contains(foregroundPackage)) {
                    stillInBlockedApp = true;
                    break;
                }
            }
            if (!stillInBlockedApp) {
                if (overlayManager != null) {
                    overlayManager.dismiss();
                }
                currentlyInterruptingTaskId = null;
            }
        }

        for (TaskManager.ActiveAvoidanceQuest quest : activeQuests) {
            boolean isBlockedAppInForeground = quest.targetPackages != null && quest.targetPackages.contains(foregroundPackage);
            long snoozeUntil = taskManager.getSnoozeUntil(quest.taskId);

            if (!isBlockedAppInForeground) {
                if (snoozeUntil > now) {
                    long unusedMs = snoozeUntil - now;
                    taskManager.cancelSnoozeAndResumeTimer(quest.taskId, unusedMs);
                }
                continue;
            }

            if (now < snoozeUntil) {
                long delay = snoozeUntil - now;
                pollHandler.postDelayed(this::checkForegroundApp, Math.min(delay + 100, POLL_INTERVAL_MS));
                continue;
            }

            boolean isIgnoreExpiry = (snoozeUntil > 0);
            if (!isIgnoreExpiry && (now - packageFirstDetectedTime < 3000L)) {
                long delay = 3000L - (now - packageFirstDetectedTime);
                pollHandler.postDelayed(this::checkForegroundApp, Math.max(100L, delay));
                continue;
            }

            if (currentlyInterruptingTaskId == null) {
                currentlyInterruptingTaskId = quest.taskId;
                overlayManager.show(quest, () -> currentlyInterruptingTaskId = null);
            }
        }
    }

    private String getForegroundPackageName() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usageStatsManager == null) {
            return null;
        }

        long endTime = System.currentTimeMillis();
        long startTime = endTime - 60000;

        String foregroundPackage = null;
        long maxForegroundTime = 0;

        try {
            UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, endTime);
            if (usageEvents != null) {
                UsageEvents.Event event = new UsageEvents.Event();
                java.util.Map<String, Long> lastForegroundTimes = new java.util.HashMap<>();
                java.util.Map<String, Long> lastBackgroundTimes = new java.util.HashMap<>();

                while (usageEvents.hasNextEvent()) {
                    usageEvents.getNextEvent(event);
                    String pkg = event.getPackageName();
                    int type = event.getEventType();
                    long time = event.getTimeStamp();

                    if (type == 1 || type == 7) {
                        lastForegroundTimes.put(pkg, time);
                    } else if (type == 2 || type == 8) {
                        lastBackgroundTimes.put(pkg, time);
                    }
                }

                for (java.util.Map.Entry<String, Long> entry : lastForegroundTimes.entrySet()) {
                    String pkg = entry.getKey();
                    long fgTime = entry.getValue();
                    Long bgTimeObj = lastBackgroundTimes.get(pkg);
                    long bgTime = bgTimeObj != null ? bgTimeObj : 0L;

                    if (fgTime > bgTime && fgTime > maxForegroundTime) {
                        maxForegroundTime = fgTime;
                        foregroundPackage = pkg;
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (foregroundPackage != null && !foregroundPackage.equals(getPackageName())) {
            return foregroundPackage;
        }

        // Fallback using queryUsageStats
        try {
            List<android.app.usage.UsageStats> statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    endTime - 120000,
                    endTime
            );
            if (statsList != null && !statsList.isEmpty()) {
                android.app.usage.UsageStats recentStats = null;
                for (android.app.usage.UsageStats stats : statsList) {
                    if (recentStats == null || stats.getLastTimeUsed() > recentStats.getLastTimeUsed()) {
                        recentStats = stats;
                    }
                }
                if (recentStats != null && (endTime - recentStats.getLastTimeUsed()) < 60000) {
                    String pkg = recentStats.getPackageName();
                    if (!pkg.equals(getPackageName())) {
                        return pkg;
                    }
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