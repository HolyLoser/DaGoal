package com.stipasay.dagoal;

import android.content.Context;
import android.os.SystemClock;
import android.provider.Settings;

/** Stores a boot-scoped elapsed time in the existing SQLite timestamp fields. */
final class QuestElapsedClock {
    private static final long MARKER = 1L << 62;
    private static final int TIME_BITS = 40;
    private static final long TIME_MASK = (1L << TIME_BITS) - 1;

    private QuestElapsedClock() {}

    static long now(Context context) {
        return bootPrefix(context) | SystemClock.elapsedRealtime();
    }

    private static long bootPrefix(Context context) {
        long boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, 0);
        return MARKER | (boot << TIME_BITS);
    }

    static boolean isCurrentBoot(Context context, long stamp) {
        return (stamp & ~TIME_MASK) == bootPrefix(context);
    }

    static long uptime(long stamp) {
        return stamp & TIME_MASK;
    }

    static long elapsedSeconds(Context context, long stamp) {
        if (!isCurrentBoot(context, stamp)) return 0;
        return Math.max(0, SystemClock.elapsedRealtime() - uptime(stamp)) / 1000L;
    }
}
