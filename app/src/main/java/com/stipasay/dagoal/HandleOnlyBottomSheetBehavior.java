package com.stipasay.dagoal;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class HandleOnlyBottomSheetBehavior<V extends View> extends BottomSheetBehavior<V> {

    private View dragHandle;
    private boolean touchStartedOnHandle = false;

    public HandleOnlyBottomSheetBehavior() {
        super();
    }

    public HandleOnlyBottomSheetBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setDragHandle(View handle) {
        this.dragHandle = handle;
    }

    private boolean isTouchOnHandle(MotionEvent event) {
        if (dragHandle == null) {
            return false;
        }
        float density = dragHandle.getResources().getDisplayMetrics().density;
        int horizontalPadding = (int) (60 * density);
        int verticalPadding = (int) (24 * density);

        int[] location = new int[2];
        dragHandle.getLocationOnScreen(location);
        Rect handleRect = new Rect(location[0], location[1], location[0] + dragHandle.getWidth(), location[1] + dragHandle.getHeight());
        handleRect.inset(-horizontalPadding, -verticalPadding);
        return handleRect.contains((int) event.getRawX(), (int) event.getRawY());
    }

    @Override
    public boolean onInterceptTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            touchStartedOnHandle = isTouchOnHandle(event);
        }
        if (!touchStartedOnHandle) {
            return false;
        }
        return super.onInterceptTouchEvent(parent, child, event);
    }

    @Override
    public boolean onTouchEvent(CoordinatorLayout parent, V child, MotionEvent event) {
        if (!touchStartedOnHandle) {
            return false;
        }
        return super.onTouchEvent(parent, child, event);
    }

    @Override
    public boolean onStartNestedScroll(CoordinatorLayout coordinatorLayout, V child, View directTargetChild, View target, int axes, int type) {
        return false;
    }
}