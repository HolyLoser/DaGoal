package com.stipasay.dagoal;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AvoidanceOverlayManager {

    public interface OnDismissListener {
        void onDismiss();
    }

    private final Context context;
    private final WindowManager windowManager;
    private View overlayView;

    public AvoidanceOverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(TaskManager.ActiveAvoidanceQuest quest, OnDismissListener dismissListener) {
        if (overlayView != null) {
            return;
        }

        if (!Settings.canDrawOverlays(context)) {
            dismissListener.onDismiss();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.overlay_avoidance_interrupt, null);

        TextView tvQuestTitle = overlayView.findViewById(R.id.tv_overlay_quest_title);
        Button btnIgnore = overlayView.findViewById(R.id.btn_overlay_ignore);
        Button btnUnderstand = overlayView.findViewById(R.id.btn_overlay_understand);

        tvQuestTitle.setText(quest.title);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                0,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.CENTER;

        windowManager.addView(overlayView, params);

        TaskManager taskManager = new TaskManager(context);
        updateIgnoreButtonLabel(btnIgnore, taskManager, quest.taskId);

        btnIgnore.setOnClickListener(v -> {
            taskManager.handleIgnorePressed(quest.taskId);
            dismiss();
            dismissListener.onDismiss();
        });

        btnUnderstand.setOnClickListener(v -> {
            Intent launchIntent = new Intent(context, DashboardActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
            dismiss();
            dismissListener.onDismiss();
        });
    }

    private void updateIgnoreButtonLabel(Button btnIgnore, TaskManager taskManager, int taskId) {
        int stage = taskManager.getIgnoreStage(taskId);
        String label;
        switch (stage) {
            case 0:
                label = "Ignore for 5 minutes";
                break;
            case 1:
                label = "Ignore for 10 minutes";
                break;
            case 2:
                label = "Ignore for 15 minutes";
                break;
            default:
                label = "Ignore for today";
                break;
        }
        btnIgnore.setText(label);
    }

    public void dismiss() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
    }
}