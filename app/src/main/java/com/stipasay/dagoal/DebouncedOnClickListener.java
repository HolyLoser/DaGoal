package com.stipasay.dagoal;

import android.os.SystemClock;
import android.view.View;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reusable OnClickListener that prevents rapid multiple clicks (double-clicking / click spamming).
 */
public abstract class DebouncedOnClickListener implements View.OnClickListener {

    public static final long DEFAULT_DEBOUNCE_INTERVAL_MS = 500L;
    public static final long MIN_GLOBAL_INTERVAL_MS = 250L;

    private static final Map<View, Long> lastViewClickMap = new WeakHashMap<>();
    private static long lastGlobalClickTime = 0L;

    private final long debounceIntervalMs;

    public DebouncedOnClickListener() {
        this(DEFAULT_DEBOUNCE_INTERVAL_MS);
    }

    public DebouncedOnClickListener(long debounceIntervalMs) {
        this.debounceIntervalMs = debounceIntervalMs;
    }

    public abstract void onDebouncedClick(View v);

    @Override
    public final void onClick(View v) {
        long currentTime = SystemClock.elapsedRealtime();

        // 1. Check global interval across all buttons (prevent multi-finger simultaneous button spamming)
        if (currentTime - lastGlobalClickTime < MIN_GLOBAL_INTERVAL_MS) {
            return;
        }

        // 2. Check per-view interval (prevent rapid double tapping on the same view)
        Long lastViewTime = lastViewClickMap.get(v);
        if (lastViewTime != null && (currentTime - lastViewTime < debounceIntervalMs)) {
            return;
        }

        // Update timestamps
        lastGlobalClickTime = currentTime;
        if (v != null) {
            lastViewClickMap.put(v, currentTime);
        }

        onDebouncedClick(v);
    }

    public static View.OnClickListener wrap(View.OnClickListener listener) {
        return wrap(DEFAULT_DEBOUNCE_INTERVAL_MS, listener);
    }

    public static View.OnClickListener wrap(long intervalMs, View.OnClickListener listener) {
        if (listener == null) return null;
        if (listener instanceof DebouncedOnClickListener) return listener;
        return new DebouncedOnClickListener(intervalMs) {
            @Override
            public void onDebouncedClick(View v) {
                listener.onClick(v);
            }
        };
    }

    public static void setSafeOnClickListener(View view, View.OnClickListener listener) {
        setSafeOnClickListener(view, DEFAULT_DEBOUNCE_INTERVAL_MS, listener);
    }

    public static void setSafeOnClickListener(View view, long intervalMs, View.OnClickListener listener) {
        if (view == null) return;
        if (listener == null) {
            view.setOnClickListener(null);
            return;
        }
        view.setOnClickListener(wrap(intervalMs, listener));
    }
}
