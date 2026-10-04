package com.stipasay.dagoal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

public class SpotlightOverlayView extends FrameLayout {

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RectF targetRect = null;

    public SpotlightOverlayView(Context context) {
        super(context);
        init();
    }

    public SpotlightOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dimPaint.setColor(Color.parseColor("#B0000000"));

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(8f);
        ringPaint.setColor(Color.parseColor("#FFD700"));

        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setTargetView(View target) {
        if (target == null || target.getVisibility() != VISIBLE) {
            targetRect = null;
            invalidate();
            return;
        }

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);

        int[] parentLoc = new int[2];
        getLocationOnScreen(parentLoc);

        float left = loc[0] - parentLoc[0] - 12;
        float top = loc[1] - parentLoc[1] - 12;
        float right = left + target.getWidth() + 24;
        float bottom = top + target.getHeight() + 24;

        targetRect = new RectF(left, top, right, bottom);
        invalidate();
    }

    public RectF getTargetRect() {
        return targetRect;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        if (targetRect != null) {
            canvas.drawRoundRect(targetRect, 24f, 24f, clearPaint);
            canvas.drawRoundRect(targetRect, 24f, 24f, ringPaint);
        }

        super.dispatchDraw(canvas);
    }
}
