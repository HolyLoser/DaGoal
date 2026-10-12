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

        int initLeft = rootLayout.getPaddingLeft();
        int initTop = rootLayout.getPaddingTop();
        int initRight = rootLayout.getPaddingRight();
        int initBottom = rootLayout.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(
                    initLeft + systemBars.left,
                    initTop + systemBars.top,
                    initRight + systemBars.right,
                    initBottom + systemBars.bottom
            );
            return insets;
        });
    }
}
