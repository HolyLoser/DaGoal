package com.stipasay.dagoal;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
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
    private OnDismissListener activeDismissListener = null;
    private final Handler tickerHandler = new Handler(Looper.getMainLooper());
    private Runnable tickerRunnable;

    public AvoidanceOverlayManager(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    public void show(TaskManager.ActiveAvoidanceQuest quest, OnDismissListener dismissListener) {
        if (overlayView != null) {
            return;
        }

        this.activeDismissListener = dismissListener;

        if (!Settings.canDrawOverlays(context)) {
            dismissListener.onDismiss();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(context);
        overlayView = inflater.inflate(R.layout.overlay_avoidance_interrupt, null);

        TextView tvQuestTitle = overlayView.findViewById(R.id.tv_overlay_quest_title);
        TextView tvQuestRewards = overlayView.findViewById(R.id.tv_overlay_quest_rewards);
        Button btnIgnore = overlayView.findViewById(R.id.btn_overlay_ignore);
        Button btnUnderstand = overlayView.findViewById(R.id.btn_overlay_understand);

        if (tvQuestTitle != null) {
            tvQuestTitle.setText(quest.getRemainingTimeFormatted());
        }

        if (tvQuestRewards != null) {
            tvQuestRewards.setText("🪙 " + quest.rewardGold + " Gold   •   ⚡ " + quest.rewardXp + " XP");
        }

        tickerRunnable = new Runnable() {
            @Override
            public void run() {
                if (overlayView != null && tvQuestTitle != null) {
                    tvQuestTitle.setText(quest.getRemainingTimeFormatted());
                    tickerHandler.postDelayed(this, 1000L);
                }
            }
        };
        tickerHandler.postDelayed(tickerRunnable, 1000L);

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
        SoundEffectsHelper.playWarning(context);

        TaskManager taskManager = new TaskManager(context);

        if (btnIgnore != null) {
            btnIgnore.setText("Dismiss");
            DebouncedOnClickListener.setSafeOnClickListener(btnIgnore, v -> {
                showDurationPickerDialog(quest, taskManager, dismissListener);
            });
        }

        DebouncedOnClickListener.setSafeOnClickListener(btnUnderstand, v -> {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(homeIntent);
            dismiss();
            dismissListener.onDismiss();
        });
    }

    private void showDurationPickerDialog(TaskManager.ActiveAvoidanceQuest quest, TaskManager taskManager, OnDismissListener dismissListener) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_ignore_duration_picker, null);

        Button btn5m = dialogView.findViewById(R.id.btn_picker_5m);
        Button btn10m = dialogView.findViewById(R.id.btn_picker_10m);
        Button btn15m = dialogView.findViewById(R.id.btn_picker_15m);
        Button btnStop = dialogView.findViewById(R.id.btn_picker_stop);

        int g5 = Math.max(0, quest.rewardGold - Math.max(1, (int) Math.round(quest.rewardGold * 0.15f)));
        int x5 = Math.max(0, quest.rewardXp - Math.max(1, (int) Math.round(quest.rewardXp * 0.15f)));

        int g10 = Math.max(0, quest.rewardGold - Math.max(1, (int) Math.round(quest.rewardGold * 0.35f)));
        int x10 = Math.max(0, quest.rewardXp - Math.max(1, (int) Math.round(quest.rewardXp * 0.35f)));

        int g15 = Math.max(0, quest.rewardGold - Math.max(1, (int) Math.round(quest.rewardGold * 0.50f)));
        int x15 = Math.max(0, quest.rewardXp - Math.max(1, (int) Math.round(quest.rewardXp * 0.50f)));

        if (btn5m != null) btn5m.setText("Ignore for 5 mins   (" + g5 + " Gold • " + x5 + " XP)");
        if (btn10m != null) btn10m.setText("Ignore for 10 mins   (" + g10 + " Gold • " + x10 + " XP)");
        if (btn15m != null) btn15m.setText("Ignore for 15 mins   (" + g15 + " Gold • " + x15 + " XP)");
        if (btnStop != null) btnStop.setText("Stop Quest   (Reset to 0 Progress)");

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(
                new android.view.ContextThemeWrapper(context, R.style.DaGoalDialogTheme)
        );
        builder.setView(dialogView);

        androidx.appcompat.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setType(type);
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        if (btn5m != null) {
            btn5m.setOnClickListener(v -> {
                dialog.dismiss();
                taskManager.customIgnorePressed(quest.taskId, 5, 0.15f);
                dismiss();
                dismissListener.onDismiss();
            });
        }

        if (btn10m != null) {
            btn10m.setOnClickListener(v -> {
                dialog.dismiss();
                taskManager.customIgnorePressed(quest.taskId, 10, 0.35f);
                dismiss();
                dismissListener.onDismiss();
            });
        }

        if (btn15m != null) {
            btn15m.setOnClickListener(v -> {
                dialog.dismiss();
                taskManager.customIgnorePressed(quest.taskId, 15, 0.50f);
                dismiss();
                dismissListener.onDismiss();
            });
        }

        if (btnStop != null) {
            btnStop.setOnClickListener(v -> {
                dialog.dismiss();
                taskManager.resetAvoidanceQuest(quest.taskId);
                ToastUtils.showToast(context, "Quest reset to 0 progress with full base rewards restored!");
                dismiss();
                dismissListener.onDismiss();
            });
        }

        dialog.show();
    }

    public void dismiss() {
        if (tickerHandler != null && tickerRunnable != null) {
            tickerHandler.removeCallbacks(tickerRunnable);
        }
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {
            }
            overlayView = null;
        }
        if (activeDismissListener != null) {
            OnDismissListener listener = activeDismissListener;
            activeDismissListener = null;
            listener.onDismiss();
        }
    }
}