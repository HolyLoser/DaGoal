package com.stipasay.dagoal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class AvatarHelper {

    public static class HairConfig {
        public float offsetY;
        public float scale;
        public boolean renderFrontOnly;

        public HairConfig(float offsetY, float scale, boolean renderFrontOnly) {
            this.offsetY = offsetY;
            this.scale = scale;
            this.renderFrontOnly = renderFrontOnly;
        }
    }

    public static HairConfig getHairConfig(String hairNum) {
        if ("02".equals(hairNum)) {
            return new HairConfig(-25.0f, 0.95f, true);
        } else if ("03".equals(hairNum)) {
            return new HairConfig(-35.0f, 1.15f, false);
        } else if ("04".equals(hairNum)) {
            return new HairConfig(-30.0f, 1.0f, false);
        }
        return new HairConfig(-73.5f, 1.0f, false);
    }

    public static void renderUserAvatar(Context context, FrameLayout container) {
        if (container == null || context == null) return;
        container.removeAllViews();
        container.setClipChildren(false);
        container.setClipToPadding(false);

        container.post(() -> renderUserAvatarInternal(context, container));
    }

    public static void renderUserAvatarFaceOnly(Context context, FrameLayout container) {
        if (container == null || context == null) return;
        container.removeAllViews();
        container.setClipChildren(true);
        container.setClipToPadding(true);

        container.post(() -> {
            int w = container.getWidth();
            int h = container.getHeight();
            if (w <= 0 || h <= 0) {
                h = Math.round(dpToPx(context, 84f));
                w = h;
            }

            int sizePx = Math.max(128, Math.min(w, h));
            AvatarConfig config = AvatarConfig.loadFromPreferences(context);
            Bitmap fullBitmap = AvatarCompositor.renderAvatarBitmap(context, config, sizePx * 2);

            if (fullBitmap != null && !fullBitmap.isRecycled()) {
                Bitmap circleBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(circleBitmap);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

                float radius = sizePx / 2.0f;

                // 1. Draw dark green circle background
                paint.setColor(Color.parseColor("#2D5A27"));
                canvas.drawCircle(radius, radius, radius, paint);

                // 2. Draw zoomed face bitmap clipped strictly to circular path
                canvas.save();
                Path clipPath = new Path();
                clipPath.addCircle(radius, radius, radius - dpToPx(context, 2.5f), Path.Direction.CW);
                canvas.clipPath(clipPath);

                float scale = 1.30f;
                float aspect = (float) fullBitmap.getHeight() / (float) fullBitmap.getWidth();
                float destWidth = sizePx * scale;
                float destHeight = destWidth * aspect;
                float transY = dpToPx(context, 14.0f) * (sizePx / 320.0f);

                RectF destRect = new RectF(
                        radius - destWidth / 2.0f,
                        radius - destHeight / 2.0f + transY,
                        radius + destWidth / 2.0f,
                        radius + destHeight / 2.0f + transY
                );

                canvas.drawBitmap(fullBitmap, null, destRect, paint);
                canvas.restore();

                // 3. Draw clean circular border
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dpToPx(context, 3.0f));
                paint.setColor(Color.parseColor("#2D5A27"));
                canvas.drawCircle(radius, radius, radius - dpToPx(context, 1.5f), paint);

                ImageView ivAvatar = new ImageView(context);
                ivAvatar.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                ivAvatar.setImageBitmap(circleBitmap);

                container.addView(ivAvatar);
            }
        });
    }

    private static void renderUserAvatarInternal(Context context, FrameLayout container) {
        if (container == null || context == null) return;
        container.removeAllViews();

        int w = container.getWidth();
        int h = container.getHeight();
        if (w <= 0 || h <= 0) {
            if (container.getLayoutParams() != null && container.getLayoutParams().height > 0) {
                h = container.getLayoutParams().height;
                w = container.getLayoutParams().width > 0 ? container.getLayoutParams().width : h;
            } else {
                h = Math.round(dpToPx(context, 260f));
                w = h;
            }
        }

        int targetSizePx = Math.max(w, h);
        if (targetSizePx <= 0) targetSizePx = Math.round(dpToPx(context, 320f));

        AvatarConfig config = AvatarConfig.loadFromPreferences(context);
        Bitmap compositedBitmap = AvatarCompositor.renderAvatarBitmap(context, config, targetSizePx);

        if (compositedBitmap != null && !compositedBitmap.isRecycled()) {
            ImageView ivAvatar = new ImageView(context);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            ivAvatar.setLayoutParams(params);
            ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            ivAvatar.setImageBitmap(compositedBitmap);
            container.addView(ivAvatar);
        }
    }

    private static float dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
