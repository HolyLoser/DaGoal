package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class SoundEffectsHelper {

    public static void playQuestComplete(Context context) {
        playSoundTone(context, ToneGenerator.TONE_PROP_BEEP, 150);
        triggerVibration(context, 100);
    }

    public static void playLevelUp(Context context) {
        playSoundTone(context, ToneGenerator.TONE_PROP_ACK, 300);
        triggerVibration(context, 200);
    }

    public static void playChestClaim(Context context) {
        playSoundTone(context, ToneGenerator.TONE_CDMA_PIP, 250);
        triggerVibration(context, 150);
    }

    private static void playSoundTone(Context context, int toneType, int durationMs) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("pref_sound_effects", true)) {
            return;
        }
        try {
            ToneGenerator toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
            toneGen.startTone(toneType, durationMs);
        } catch (Exception e) {
            Log.e("SoundEffectsHelper", "Error playing tone", e);
        }
    }

    private static void triggerVibration(Context context, long milliseconds) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("pref_haptics", true)) {
            return;
        }
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(milliseconds);
                }
            }
        } catch (Exception e) {
            Log.e("SoundEffectsHelper", "Error triggering vibration", e);
        }
    }
}
