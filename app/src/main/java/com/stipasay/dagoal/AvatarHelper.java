package com.stipasay.dagoal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.Log;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

public class AvatarHelper {

    private static final int TAG_GENERATION_KEY = 1073741824;

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

        int currentGen = getNextGeneration(container);

        container.post(() -> {
            if (!isLatestGeneration(container, currentGen)) {
                Log.d("AvatarDebug", "renderUserAvatar: STALE generation " + currentGen + " discarded.");
                return;
            }
            renderUserAvatarInternal(context, container, currentGen);
        });
    }

    public static void renderUserAvatarFaceOnly(Context context, FrameLayout container) {
        if (container == null || context == null) return;

        int currentGen = getNextGeneration(container);

        container.post(() -> {
            if (!isLatestGeneration(container, currentGen)) {
                Log.d("AvatarDebug", "renderUserAvatarFaceOnly: STALE generation " + currentGen + " discarded.");
                return;
            }

            container.setClipChildren(true);
            container.setClipToPadding(true);

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

                ImageView ivAvatar = null;
                if (container.getChildCount() > 0 && container.getChildAt(0) instanceof ImageView) {
                    ivAvatar = (ImageView) container.getChildAt(0);
                } else {
                    container.removeAllViews();
                    ivAvatar = new ImageView(context);
                    ivAvatar.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
                    ivAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    container.addView(ivAvatar);
                }
                ivAvatar.setImageBitmap(circleBitmap);

                String resourceName = "unknown";
                try {
                    resourceName = context.getResources().getResourceEntryName(container.getId());
                } catch (Exception ignored) {}
                Log.d("AvatarDebug", "renderUserAvatarFaceOnly: ATOMIC ADD view=" + resourceName + " childCount=" + container.getChildCount() + " gen=" + currentGen);
            }
        });
    }

    public static void alignBitmapToBottom(ImageView imageView, Bitmap bitmap) {
        if (imageView == null || bitmap == null || bitmap.isRecycled()) return;
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setImageBitmap(bitmap);
        imageView.post(() -> {
            int viewWidth = imageView.getWidth();
            int viewHeight = imageView.getHeight();
            if (viewWidth <= 0 || viewHeight <= 0) return;

            int bmWidth = bitmap.getWidth();
            int bmHeight = bitmap.getHeight();

            float scale = Math.min((float) viewWidth / bmWidth, (float) viewHeight / bmHeight);
            float dx = (viewWidth - bmWidth * scale) / 2.0f;
            float dy = viewHeight - (bmHeight * scale);

            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(scale, scale);
            matrix.postTranslate(dx, dy);
            imageView.setImageMatrix(matrix);
        });
    }

    private static void renderUserAvatarInternal(Context context, FrameLayout container, int generation) {
        if (container == null || context == null) return;
        if (!isLatestGeneration(container, generation)) return;

        container.setClipChildren(false);
        container.setClipToPadding(false);

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

        int canonical320Px = Math.round(dpToPx(context, 320f));
        int targetSizePx = Math.max(Math.max(w, h), canonical320Px);

        AvatarConfig config = AvatarConfig.loadFromPreferences(context);
        Bitmap compositedBitmap = AvatarCompositor.renderAvatarBitmap(context, config, targetSizePx);

        if (compositedBitmap != null && !compositedBitmap.isRecycled()) {
            ImageView ivAvatar = null;
            if (container.getChildCount() > 0 && container.getChildAt(0) instanceof ImageView) {
                ivAvatar = (ImageView) container.getChildAt(0);
            } else {
                container.removeAllViews();
                ivAvatar = new ImageView(context);
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
                params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
                ivAvatar.setLayoutParams(params);
                container.addView(ivAvatar);
            }
            alignBitmapToBottom(ivAvatar, compositedBitmap);

            String resourceName = "unknown";
            try {
                resourceName = context.getResources().getResourceEntryName(container.getId());
            } catch (Exception ignored) {}
            Log.d("AvatarDebug", "renderUserAvatarInternal: ATOMIC ADD view=" + resourceName + " childCount=" + container.getChildCount() + " gen=" + generation);
        }
    }

    private static int getNextGeneration(FrameLayout container) {
        Object tag = container.getTag(TAG_GENERATION_KEY);
        int currentGen = (tag instanceof Integer) ? (Integer) tag : 0;
        int nextGen = currentGen + 1;
        container.setTag(TAG_GENERATION_KEY, nextGen);
        return nextGen;
    }

    private static boolean isLatestGeneration(FrameLayout container, int generation) {
        Object tag = container.getTag(TAG_GENERATION_KEY);
        if (tag instanceof Integer) {
            return ((Integer) tag) == generation;
        }
        return true;
    }

    private static float dpToPx(Context context, float dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
