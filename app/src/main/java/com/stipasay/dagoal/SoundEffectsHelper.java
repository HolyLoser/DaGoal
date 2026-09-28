package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

public class SoundEffectsHelper {

    private static SoundPool soundPool;

    // --- Specific WAV Sound Triggers ---

    public static void playConfirm(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_confirm")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_BEEP, 150);
        }
        triggerVibration(context, 100);
    }

    public static void playCoin(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_coin")) {
            playSoundTone(context, ToneGenerator.TONE_CDMA_PIP, 200);
        }
        triggerVibration(context, 120);
    }

    public static void playPurchase(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_purchase")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_BEEP, 100);
        }
        triggerVibration(context, 80);
    }

    public static void playButton(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_button")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_BEEP, 80);
        }
        triggerVibration(context, 40);
    }

    public static void playHighlight(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_highlight")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_BEEP, 60);
        }
        triggerVibration(context, 30);
    }

    public static void playMenuOpen(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_menu_open")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_ACK, 120);
        }
        triggerVibration(context, 60);
    }

    public static void playMenuClose(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_menu_close")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_NACK, 120);
        }
        triggerVibration(context, 50);
    }

    public static void playCancel(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_cancel")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_NACK, 180);
        }
        triggerVibration(context, 100);
    }

    public static void playPause(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_pause")) {
            playSoundTone(context, ToneGenerator.TONE_CDMA_PIP, 150);
        }
        triggerVibration(context, 80);
    }

    public static void playWarning(Context context) {
        if (!isSoundEnabled(context)) return;
        if (!playRawSound(context, "sfx_warning")) {
            playSoundTone(context, ToneGenerator.TONE_PROP_NACK, 250);
        }
        triggerVibration(context, 200);
    }

    // --- Feature Aliases ---

    public static void playQuestComplete(Context context) {
        playConfirm(context);
    }

    public static void playLevelUp(Context context) {
        triggerVibration(context, 250);
    }

    public static void playChestClaim(Context context) {
        playCoin(context);
    }

    public static void playAchievementUnlock(Context context) {
        playConfirm(context);
    }

    public static void playPurchaseItem(Context context) {
        playPurchase(context);
    }

    // --- Engine Mechanics ---

    private static boolean isSoundEnabled(Context context) {
        if (context == null) return false;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("pref_sound_effects", true);
    }

    private static boolean playRawSound(Context context, String resourceName) {
        if (context == null) return false;
        try {
            int resId = context.getResources().getIdentifier(resourceName, "raw", context.getPackageName());
            if (resId <= 0) return false;

            if (soundPool == null) {
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                soundPool = new SoundPool.Builder()
                        .setMaxStreams(5)
                        .setAudioAttributes(audioAttributes)
                        .build();
            }

            int soundId = soundPool.load(context, resId, 1);
            soundPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0) {
                    pool.play(sampleId, 1.0f, 1.0f, 1, 0, 1.0f);
                }
            });
            return true;
        } catch (Exception e) {
            Log.e("SoundEffectsHelper", "Error playing raw sound: " + resourceName, e);
            return false;
        }
    }

    private static void playSoundTone(Context context, int toneType, int durationMs) {
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
