package com.stipasay.dagoal;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

public class SpotlightOverlayView extends FrameLayout {

    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private RectF currentRect = null;
    private ValueAnimator rectAnimator = null;
    private ValueAnimator pulseAnimator = null;
    private float pulseRadiusOffset = 0f;

    public SpotlightOverlayView(Context context) {
        super(context);
        init();
    }

    public SpotlightOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        dimPaint.setColor(Color.parseColor("#B3000000"));

        clearPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(6f);
        ringPaint.setColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.dagoal_gold_amber));

        setLayerType(LAYER_TYPE_HARDWARE, null);
        startPulsingAnimation();
    }

    private void startPulsingAnimation() {
        pulseAnimator = ValueAnimator.ofFloat(0f, 6f, 0f);
        pulseAnimator.setDuration(1500);
        pulseAnimator.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnimator.setRepeatMode(ValueAnimator.RESTART);
        pulseAnimator.addUpdateListener(animation -> {
            pulseRadiusOffset = (float) animation.getAnimatedValue();
            invalidate();
        });
        pulseAnimator.start();
    }

    public void setTargetView(View target) {
        if (target == null || target.getVisibility() != VISIBLE) {
            currentRect = null;
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

        RectF newRect = new RectF(left, top, right, bottom);

        if (currentRect == null) {
            currentRect = newRect;
            invalidate();
        } else {
            animateRectChange(currentRect, newRect);
        }
    }

    private void animateRectChange(RectF startRect, RectF endRect) {
        if (rectAnimator != null && rectAnimator.isRunning()) {
            rectAnimator.cancel();
        }

        final float startLeft = startRect.left;
        final float startTop = startRect.top;
        final float startRight = startRect.right;
        final float startBottom = startRect.bottom;

        rectAnimator = ValueAnimator.ofFloat(0f, 1f);
        rectAnimator.setDuration(280);
        rectAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        rectAnimator.addUpdateListener(animation -> {
            float fraction = animation.getAnimatedFraction();
            float l = startLeft + (endRect.left - startLeft) * fraction;
            float t = startTop + (endRect.top - startTop) * fraction;
            float r = startRight + (endRect.right - startRight) * fraction;
            float b = startBottom + (endRect.bottom - startBottom) * fraction;
            currentRect = new RectF(l, t, r, b);
            invalidate();
        });
        rectAnimator.start();
    }

    public RectF getTargetRect() {
        return currentRect;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnimator != null) pulseAnimator.cancel();
        if (rectAnimator != null) rectAnimator.cancel();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        canvas.drawRect(0, 0, getWidth(), getHeight(), dimPaint);

        if (currentRect != null) {
            canvas.drawRoundRect(currentRect, 24f, 24f, clearPaint);

            RectF ringRect = new RectF(
                    currentRect.left - pulseRadiusOffset / 2f,
                    currentRect.top - pulseRadiusOffset / 2f,
                    currentRect.right + pulseRadiusOffset / 2f,
                    currentRect.bottom + pulseRadiusOffset / 2f
            );
            canvas.drawRoundRect(ringRect, 24f, 24f, ringPaint);
        }

        super.dispatchDraw(canvas);
    }
}
