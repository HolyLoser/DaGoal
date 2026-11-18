package com.stipasay.dagoal;

import android.app.Activity;
import android.view.View;
import android.view.WindowManager;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public class AppearanceHelper {

    public static void applyPreferredNightMode(Activity activity) {
        if (activity == null) return;

        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        activity.getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
    }

    public static void setupEdgeToEdgeInsets(View rootLayout) {
        if (rootLayout == null) return;

        ResponsiveLayoutHelper.attach(rootLayout);
        int initLeft = rootLayout.getPaddingLeft();
        int initTop = rootLayout.getPaddingTop();
        int initRight = rootLayout.getPaddingRight();
        int initBottom = rootLayout.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            Insets keyboard = insets.getInsets(WindowInsetsCompat.Type.ime());
            int formMargin = rootLayout instanceof android.widget.ScrollView
                    && "responsive_form".equals(rootLayout.getTag())
                    ? Math.max(0, (rootLayout.getWidth() - Math.round(560 * rootLayout.getResources().getDisplayMetrics().density)) / 2) : 0;
            v.setPadding(
                    Math.max(initLeft, formMargin) + systemBars.left,
                    initTop + systemBars.top,
                    Math.max(initRight, formMargin) + systemBars.right,
                    initBottom + Math.max(systemBars.bottom, keyboard.bottom)
            );
            ResponsiveLayoutHelper.update(rootLayout);
            return insets;
        });
        ViewCompat.requestApplyInsets(rootLayout);
    }

    public static String formatCompactNumber(long value) {
        if (value < 0) return "0";
        if (value <= 99_999) {
            return String.format(java.util.Locale.getDefault(), "%,d", value);
        }
        if (value < 1_000_000) {
            long kVal = value / 1000;
            return kVal + "k";
        }

        // Millions (1M to 999M)
        if (value < 1_000_000_000L) {
            double mVal = value / 1_000_000.0;
            if (mVal == (long) mVal) {
                return (long) mVal + "M";
            }
            if (value < 10_000_000L) {
                if (Math.round(mVal * 100.0) % 10 == 0) {
                    return String.format(java.util.Locale.US, "%.1fM", mVal);
                }
                return String.format(java.util.Locale.US, "%.2fM", mVal);
            }
            if (value < 100_000_000L) {
                return String.format(java.util.Locale.US, "%.1fM", mVal);
            }
            return (long) mVal + "M";
        }

        // Billions (1B to 999B)
        if (value < 1_000_000_000_000L) {
            double bVal = value / 1_000_000_000.0;
            if (bVal == (long) bVal) {
                return (long) bVal + "B";
            }
            if (value < 10_000_000_000L) {
                if (Math.round(bVal * 100.0) % 10 == 0) {
                    return String.format(java.util.Locale.US, "%.1fB", bVal);
                }
                return String.format(java.util.Locale.US, "%.2fB", bVal);
            }
            if (value < 100_000_000_000L) {
                return String.format(java.util.Locale.US, "%.1fB", bVal);
            }
            return (long) bVal + "B";
        }

        // Trillions (1T+)
        double tVal = value / 1_000_000_000_000.0;
        if (tVal == (long) tVal) {
            return (long) tVal + "T";
        }
        if (value < 10_000_000_000_000L) {
            if (Math.round(tVal * 100.0) % 10 == 0) {
                return String.format(java.util.Locale.US, "%.1fT", tVal);
            }
            return String.format(java.util.Locale.US, "%.2fT", tVal);
        }
        if (value < 100_000_000_000_000L) {
            return String.format(java.util.Locale.US, "%.1fT", tVal);
        }
        return (long) tVal + "T";
    }
}
