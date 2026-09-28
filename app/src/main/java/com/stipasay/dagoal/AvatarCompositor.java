package com.stipasay.dagoal;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class AvatarCompositor {

    private static final int MAX_CACHE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB LruCache
    private static final LruCache<String, Bitmap> sBitmapCache = new LruCache<String, Bitmap>(MAX_CACHE_SIZE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
            return bitmap.getByteCount();
        }
    };

    private static final int[][] sSampledSkinPalettes = new int[11][2];
    private static boolean sPalettesSampled = false;

    private static final int[][] SKIN_TONE_PALETTES = new int[][]{
        { Color.parseColor("#FFF0E5"), Color.parseColor("#E6D3C8") }, // 1: Very Fair / Porcelain
        { Color.parseColor("#FDDFD0"), Color.parseColor("#E2C3B4") }, // 2: Fair Warm
        { Color.parseColor("#F5CBA7"), Color.parseColor("#DBAE8F") }, // 3: Peach
        { Color.parseColor("#E5B898"), Color.parseColor("#CB9C7E") }, // 4: Warm Beige
        { Color.parseColor("#D4A373"), Color.parseColor("#B9885E") }, // 5: Golden Olive
        { Color.parseColor("#C68B59"), Color.parseColor("#AB7041") }, // 6: Bronze
        { Color.parseColor("#A0653B"), Color.parseColor("#864D28") }, // 7: Warm Chestnut
        { Color.parseColor("#834A29"), Color.parseColor("#6A3415") }, // 8: Deep Caramel
        { Color.parseColor("#63381B"), Color.parseColor("#4A250F") }, // 9: Rich Espresso
        { Color.parseColor("#48250F"), Color.parseColor("#331606") }, // 10: Dark Umber
        { Color.parseColor("#2E1405"), Color.parseColor("#1C0A02") }  // 11: Deep Ebony
    };

    public static void clearCache() {
        sBitmapCache.evictAll();
    }

    private static void sampleExactSkinPalettesIfNeeded(Context context) {
        if (sPalettesSampled || context == null) return;
        synchronized (sSampledSkinPalettes) {
            if (sPalettesSampled) return;
            for (int i = 0; i < 11; i++) {
                int skinNum = i + 1;
                int resId = context.getResources().getIdentifier("skin_tone" + skinNum, "drawable", context.getPackageName());
                if (resId == 0) resId = R.drawable.skin_tone1;

                Drawable d = ContextCompat.getDrawable(context, resId);
                if (d != null) {
                    Bitmap bmp;
                    if (d instanceof BitmapDrawable) {
                        bmp = ((BitmapDrawable) d).getBitmap();
                    } else {
                        bmp = Bitmap.createBitmap(d.getIntrinsicWidth() > 0 ? d.getIntrinsicWidth() : 200,
                                d.getIntrinsicHeight() > 0 ? d.getIntrinsicHeight() : 200,
                                Bitmap.Config.ARGB_8888);
                        Canvas c = new Canvas(bmp);
                        d.setBounds(0, 0, c.getWidth(), c.getHeight());
                        d.draw(c);
                    }

                    int w = bmp.getWidth();
                    int h = bmp.getHeight();
                    int[] pixels = new int[w * h];
                    bmp.getPixels(pixels, 0, w, 0, 0, w, h);

                    int mainR = 0, mainG = 0, mainB = 0, mainCount = 0;
                    int shadowR = 0, shadowG = 0, shadowB = 0, shadowCount = 0;

                    for (int p : pixels) {
                        int a = (p >> 24) & 0xff;
                        if (a < 100) continue;
                        int r = (p >> 16) & 0xff;
                        int g = (p >> 8) & 0xff;
                        int b = p & 0xff;

                        if (r < 50 && g < 50 && b < 50) continue;

                        int brightness = (r + g + b) / 3;
                        if (brightness > 130) {
                            mainR += r; mainG += g; mainB += b; mainCount++;
                        } else {
                            shadowR += r; shadowG += g; shadowB += b; shadowCount++;
                        }
                    }

                    if (mainCount > 0) {
                        sSampledSkinPalettes[i][0] = Color.rgb(mainR / mainCount, mainG / mainCount, mainB / mainCount);
                    } else {
                        sSampledSkinPalettes[i][0] = SKIN_TONE_PALETTES[i][0];
                    }

                    if (shadowCount > 0) {
                        sSampledSkinPalettes[i][1] = Color.rgb(shadowR / shadowCount, shadowG / shadowCount, shadowB / shadowCount);
                    } else {
                        sSampledSkinPalettes[i][1] = SKIN_TONE_PALETTES[i][1];
                    }
                } else {
                    sSampledSkinPalettes[i][0] = SKIN_TONE_PALETTES[i][0];
                    sSampledSkinPalettes[i][1] = SKIN_TONE_PALETTES[i][1];
                }
            }
            sPalettesSampled = true;
        }
    }

    public static Bitmap renderAvatarBitmap(Context context, AvatarConfig config, int outputSizePx) {
        if (context == null || config == null || outputSizePx <= 0) return null;

        String cacheKey = config.getCacheKey(outputSizePx);
        Bitmap cached = sBitmapCache.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            Log.d("AvatarDebug", "renderAvatarBitmap HIT cacheKey=" + cacheKey + " hash=" + System.identityHashCode(cached));
            return cached;
        }

        Log.d("AvatarDebug", "renderAvatarBitmap MISS cacheKey=" + cacheKey + " accessoryAssetId=" + config.accessoryAssetId);

        float density = context.getResources().getDisplayMetrics().density;
        float outputSizeDp = outputSizePx / density;
        float scale = outputSizeDp / 320.0f; // Scale factor relative to 320dp canonical canvas

        float topPaddingPx = dpToPx(60.0f, density) * scale;
        int bitmapWidth = outputSizePx;
        int bitmapHeight = Math.round(outputSizePx + topPaddingPx);

        Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);

        float hairTranslationY = topPaddingPx - dpToPx(58.0f, density) * scale;
        float hairTranslationX = -dpToPx(2.0f, density) * scale;

        // 0. Layer 0: Back Hair (_b)
        if (config.hairIndex > 0) {
            int backRes = context.getResources().getIdentifier("hairstyle_" + config.selectedHairBase + "b", "drawable", context.getPackageName());
            if (backRes != 0) {
                Bitmap backBitmap = generateTintedBitmap(context, backRes, config.selectedHairColor);
                drawLayerFittingCanvas(canvas, backBitmap, backRes, context, paint, outputSizePx, hairTranslationX, hairTranslationY);
            }
        }

        // 1. Layer 1: Body Skin Tone (Dynamic Tinting of base_skin.png)
        Bitmap skinBitmap = generateSkinToneBitmap(context, config.skinIndex);
        if (skinBitmap != null) {
            drawLayerFittingCanvas(canvas, skinBitmap, R.drawable.base_skin, context, paint, outputSizePx, 0, topPaddingPx);
        } else {
            drawDrawableFittingCanvas(canvas, context, R.drawable.base_skin, paint, outputSizePx, 0, topPaddingPx);
        }

        // 1.5 Layer 1.5: Clothes / Wardrobe Outfit
        int clothesRes = context.getResources().getIdentifier(config.clothesAssetId, "drawable", context.getPackageName());
        if (clothesRes == 0) clothesRes = R.drawable.tank_top;
        drawDrawableFittingCanvas(canvas, context, clothesRes, paint, outputSizePx, 0, topPaddingPx);

        // 2. Layer 2: Eyes (Paired Left _a & Right _b)
        int baseEyeSizeDp = getEyeSizeDp(config.selectedEyeBase);
        int baseContainerWidthDp = getEyeContainerWidthDp(config.selectedEyeBase);

        float eyesBoxWidthPx = dpToPx(baseContainerWidthDp, density) * scale;
        float eyesBoxHeightPx = dpToPx(65.0f, density) * scale;
        float eyesBottomMarginPx = dpToPx(65.0f, density) * scale;
        float eyeSizePx = dpToPx(baseEyeSizeDp, density) * scale;

        float eyesBoxLeft = (outputSizePx - eyesBoxWidthPx) / 2.0f;
        float eyesBoxTop = topPaddingPx + (outputSizePx - eyesBoxHeightPx) / 2.0f - eyesBottomMarginPx;

        String leftName = config.selectedEyeBase + "_a";
        String rightName = config.selectedEyeBase + "_b";
        if ("ring_solid_lash_long".equals(config.selectedEyeBase)) {
            leftName = "ring_solid_lash_long_a_512x512";
        }

        int leftRes = context.getResources().getIdentifier(leftName, "drawable", context.getPackageName());
        int rightRes = context.getResources().getIdentifier(rightName, "drawable", context.getPackageName());
        if (leftRes == 0) leftRes = R.drawable.eye_category_icon;
        if (rightRes == 0) rightRes = leftRes;

        Bitmap leftBitmap = generateTintedBitmap(context, leftRes, config.selectedEyeColor);
        Bitmap rightBitmap = generateTintedBitmap(context, rightRes, config.selectedEyeColor);

        RectF leftRect = new RectF(eyesBoxLeft, eyesBoxTop + (eyesBoxHeightPx - eyeSizePx) / 2.0f,
                eyesBoxLeft + eyeSizePx, eyesBoxTop + (eyesBoxHeightPx + eyeSizePx) / 2.0f);
        RectF rightRect = new RectF(eyesBoxLeft + eyesBoxWidthPx - eyeSizePx, eyesBoxTop + (eyesBoxHeightPx - eyeSizePx) / 2.0f,
                eyesBoxLeft + eyesBoxWidthPx, eyesBoxTop + (eyesBoxHeightPx + eyeSizePx) / 2.0f);

        if (leftBitmap != null) canvas.drawBitmap(leftBitmap, null, leftRect, paint);
        else drawDrawableInRect(canvas, context, leftRes, leftRect, paint);

        if (rightBitmap != null) canvas.drawBitmap(rightBitmap, null, rightRect, paint);
        else drawDrawableInRect(canvas, context, rightRes, rightRect, paint);

        // 3. Layer 3: Cheeks / Blush
        if (config.cheeksIndex > 0) {
            float cheeksBoxWidthPx = dpToPx(176.0f, density) * scale;
            float cheeksBoxHeightPx = dpToPx(40.0f, density) * scale;
            float cheeksBottomMarginPx = dpToPx(35.0f, density) * scale;
            float cheekWidthPx = dpToPx(50.0f, density) * scale;
            float cheekHeightPx = dpToPx(35.0f, density) * scale;

            float cheeksBoxLeft = (outputSizePx - cheeksBoxWidthPx) / 2.0f;
            float cheeksBoxTop = topPaddingPx + (outputSizePx - cheeksBoxHeightPx) / 2.0f - cheeksBottomMarginPx;

            int blushColor = getBlushColorInt(config.selectedColorName);
            Bitmap blushBitmap = generateBlushBitmap(blushColor, config.cheeksIndex - 1);

            if (blushBitmap != null) {
                RectF cLeftRect = new RectF(cheeksBoxLeft, cheeksBoxTop + (cheeksBoxHeightPx - cheekHeightPx) / 2.0f,
                        cheeksBoxLeft + cheekWidthPx, cheeksBoxTop + (cheeksBoxHeightPx + cheekHeightPx) / 2.0f);
                RectF cRightRect = new RectF(cheeksBoxLeft + cheeksBoxWidthPx - cheekWidthPx, cheeksBoxTop + (cheeksBoxHeightPx - cheekHeightPx) / 2.0f,
                        cheeksBoxLeft + cheeksBoxWidthPx, cheeksBoxTop + (cheeksBoxHeightPx + cheekHeightPx) / 2.0f);

                canvas.drawBitmap(blushBitmap, null, cLeftRect, paint);
                canvas.drawBitmap(blushBitmap, null, cRightRect, paint);
            }
        }

        // 4. Layer 4: Nose (Independent selectedNoseColorName)
        boolean isSmallNose = "square_nose".equals(config.selectedNoseShape);
        float baseNoseSizeDp = isSmallNose ? 34.0f : 50.0f;
        float noseSizePx = dpToPx(baseNoseSizeDp, density) * scale;
        float noseBottomMarginPx = dpToPx(48.0f, density) * scale;

        float noseLeft = (outputSizePx - noseSizePx) / 2.0f;
        float noseTop = topPaddingPx + (outputSizePx - noseSizePx) / 2.0f - noseBottomMarginPx;
        RectF noseRect = new RectF(noseLeft, noseTop, noseLeft + noseSizePx, noseTop + noseSizePx);

        int noseRes = context.getResources().getIdentifier(config.selectedNoseShape, "drawable", context.getPackageName());
        if (noseRes == 0) noseRes = R.drawable.nose_category_icon;

        if ("black".equalsIgnoreCase(config.selectedNoseColorName)) {
            drawDrawableInRect(canvas, context, noseRes, noseRect, paint);
        } else {
            int noseColor = getSwatchColorInt(config.selectedNoseColorName);
            Bitmap tintedNose = generateTintedBitmap(context, noseRes, noseColor);
            if (tintedNose != null) canvas.drawBitmap(tintedNose, null, noseRect, paint);
            else drawDrawableInRect(canvas, context, noseRes, noseRect, paint);
        }

        // 5. Layer 5: Mouth (Independent selectedMouthColorName)
        float mouthSizePx = dpToPx(60.0f, density) * scale;
        float mouthBottomMarginPx = dpToPx(24.0f, density) * scale;

        float mouthLeft = (outputSizePx - mouthSizePx) / 2.0f;
        float mouthTop = topPaddingPx + (outputSizePx - mouthSizePx) / 2.0f - mouthBottomMarginPx;
        RectF mouthRect = new RectF(mouthLeft, mouthTop, mouthLeft + mouthSizePx, mouthTop + mouthSizePx);

        int mouthRes = context.getResources().getIdentifier(config.selectedMouthShape, "drawable", context.getPackageName());
        if (mouthRes == 0) mouthRes = R.drawable.lips_category_icon;

        if ("black".equalsIgnoreCase(config.selectedMouthColorName)) {
            drawDrawableInRect(canvas, context, mouthRes, mouthRect, paint);
        } else {
            int mouthColor = getSwatchColorInt(config.selectedMouthColorName);
            Bitmap tintedMouth = generateTintedBitmap(context, mouthRes, mouthColor);
            if (tintedMouth != null) canvas.drawBitmap(tintedMouth, null, mouthRect, paint);
            else drawDrawableInRect(canvas, context, mouthRes, mouthRect, paint);
        }

        // 5.5 Layer 5.5: Glasses Accessories (Over the Eyes at 0.76f scale)
        if (config.accessoryAssetId != null && !config.accessoryAssetId.isEmpty()) {
            boolean isHat = config.accessoryAssetId.contains("hat");
            if (!isHat) {
                int accessoryRes = context.getResources().getIdentifier(config.accessoryAssetId, "drawable", context.getPackageName());
                if (accessoryRes != 0) {
                    Bitmap accessoryBitmap = generateAccessoryBitmap(context, accessoryRes, config.accessoryColor);

                    float accessoryScale = 0.76f;
                    float scaledSize = outputSizePx * accessoryScale;
                    float offsetX = (outputSizePx - scaledSize) / 2.0f + hairTranslationX;
                    float offsetY = (outputSizePx - scaledSize) / 2.0f + hairTranslationY;
                    RectF accessoryRect = new RectF(offsetX, offsetY, offsetX + scaledSize, offsetY + scaledSize);

                    if (accessoryBitmap != null) {
                        canvas.drawBitmap(accessoryBitmap, null, accessoryRect, paint);
                    } else {
                        drawDrawableInRect(canvas, context, accessoryRes, accessoryRect, paint);
                    }
                }
            }
        }

        // 6. Layer 6: Front Hair / Bangs (_a)
        if (config.hairIndex > 0) {
            int frontRes = context.getResources().getIdentifier("hairstyle_" + config.selectedHairBase + "a", "drawable", context.getPackageName());
            if (frontRes != 0) {
                Bitmap frontBitmap = generateTintedBitmap(context, frontRes, config.selectedHairColor);
                drawLayerFittingCanvas(canvas, frontBitmap, frontRes, context, paint, outputSizePx, hairTranslationX, hairTranslationY);
            }
        }

        // 6.5 Layer 6.5: Hair Accessories (Hair Bow, Hair Flowers - on top of hair)
        if (config.accessoryAssetId != null && config.accessoryAssetId.contains("hair")) {
            int hairAccRes = context.getResources().getIdentifier(config.accessoryAssetId, "drawable", context.getPackageName());
            if (hairAccRes != 0) {
                Bitmap hairAccBitmap = generateAccessoryBitmap(context, hairAccRes, config.accessoryColor);
                if (hairAccBitmap != null) {
                    drawLayerFittingCanvas(canvas, hairAccBitmap, hairAccRes, context, paint, outputSizePx, hairTranslationX, hairTranslationY);
                } else {
                    drawLayerFittingCanvas(canvas, null, hairAccRes, context, paint, outputSizePx, hairTranslationX, hairTranslationY);
                }
            }
        }

        // 7. Layer 7: Hat Accessories (On Top of Head/Hair at 1.15f scale, higher elevation)
        if (config.accessoryAssetId != null && config.accessoryAssetId.contains("hat")) {
            int hatRes = context.getResources().getIdentifier(config.accessoryAssetId, "drawable", context.getPackageName());
            if (hatRes != 0) {
                Bitmap hatBitmap = generateAccessoryBitmap(context, hatRes, config.accessoryColor);

                float hatScale = 1.40f;
                float scaledHatSize = outputSizePx * hatScale;
                float hatOffsetX = (outputSizePx - scaledHatSize) / 2.0f + hairTranslationX;
                float hatOffsetY = hairTranslationY - dpToPx(155.0f, density) * scale;
                RectF hatRect = new RectF(hatOffsetX, hatOffsetY, hatOffsetX + scaledHatSize, hatOffsetY + scaledHatSize);

                if (hatBitmap != null) {
                    canvas.drawBitmap(hatBitmap, null, hatRect, paint);
                } else {
                    drawDrawableInRect(canvas, context, hatRes, hatRect, paint);
                }
            }
        }

        sBitmapCache.put(cacheKey, bitmap);
        return bitmap;
    }

    public static Bitmap generateSkinToneBitmap(Context context, int skinIndex) {
        if (context == null) return null;
        sampleExactSkinPalettesIfNeeded(context);

        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.base_skin);
        if (drawable == null) return null;

        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        } else {
            int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 320;
            int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 320;
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        int clampedIndex = Math.max(0, Math.min(skinIndex, sSampledSkinPalettes.length - 1));
        int mainColor = sSampledSkinPalettes[clampedIndex][0];
        int shadowColor = sSampledSkinPalettes[clampedIndex][1];

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xff;
            if (a == 0) continue;

            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            // Outlines
            if (r < 50 && g < 50 && b < 50) {
                continue;
            }

            // Pure White -> Main Skin
            if (r > 220 && g > 220 && b > 220) {
                pixels[i] = (a << 24) | (Color.red(mainColor) << 16) | (Color.green(mainColor) << 8) | Color.blue(mainColor);
            } else {
                // Light Gray -> Darker Chest / Shadow
                pixels[i] = (a << 24) | (Color.red(shadowColor) << 16) | (Color.green(shadowColor) << 8) | Color.blue(shadowColor);
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    public static Bitmap generateAccessoryBitmap(Context context, int drawableRes, int targetColor) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableRes);
        if (drawable == null) return null;

        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        } else {
            int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 320;
            int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 320;
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        String resEntryName = "";
        try {
            resEntryName = context.getResources().getResourceEntryName(drawableRes);
        } catch (Exception ignored) {}

        boolean isSunglasses = resEntryName.contains("sunglasses");
        boolean isHueTintedAccessory = resEntryName.contains("hat") || resEntryName.contains("hair");

        float[] targetHsv = new float[3];
        Color.colorToHSV(targetColor, targetHsv);
        float targetHue = targetHsv[0];
        float targetSat = targetHsv[1];

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xff;
            if (a < 30) continue;

            if (isHueTintedAccessory) {
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = p & 0xff;

                boolean isGrayOrWhiteFabric = (r > 40) && (Math.abs(r - g) < 40) && (Math.abs(g - b) < 40);

                if (isGrayOrWhiteFabric) {
                    float[] pixelHsv = new float[3];
                    Color.colorToHSV(p, pixelHsv);
                    pixelHsv[0] = targetHue;
                    if (targetSat > 0) {
                        pixelHsv[1] = Math.max(pixelHsv[1], targetSat * 0.80f);
                    }
                    pixels[i] = Color.HSVToColor(a, pixelHsv);
                } else {
                    pixels[i] = p;
                }
            } else if (isSunglasses) {
                int r = (p >> 16) & 0xff;
                int g = (p >> 8) & 0xff;
                int b = p & 0xff;

                boolean isLightFrame = (r > 100 && g > 100 && b > 100);
                if (isLightFrame && a >= 50) {
                    float[] pixelHsv = new float[3];
                    Color.colorToHSV(p, pixelHsv);
                    pixelHsv[0] = targetHue;
                    if (targetSat > 0) {
                        pixelHsv[1] = Math.max(pixelHsv[1], targetSat * 0.85f);
                    }
                    pixels[i] = Color.HSVToColor(a, pixelHsv);
                } else {
                    pixels[i] = p;
                }
            } else {
                float[] pixelHsv = new float[3];
                Color.colorToHSV(p, pixelHsv);
                pixelHsv[0] = targetHue;
                if (targetSat > 0) {
                    pixelHsv[1] = Math.max(pixelHsv[1], targetSat * 0.85f);
                }
                pixels[i] = Color.HSVToColor(a, pixelHsv);
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static void drawLayerFittingCanvas(Canvas canvas, Bitmap bitmap, int drawableRes, Context context, Paint paint, int outputSizePx, float transX, float transY) {
        RectF rect = new RectF(transX, transY, outputSizePx + transX, outputSizePx + transY);
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, null, rect, paint);
        } else {
            drawDrawableInRect(canvas, context, drawableRes, rect, paint);
        }
    }

    private static void drawDrawableFittingCanvas(Canvas canvas, Context context, int drawableRes, Paint paint, int outputSizePx, float transX, float transY) {
        RectF rect = new RectF(transX, transY, outputSizePx + transX, outputSizePx + transY);
        drawDrawableInRect(canvas, context, drawableRes, rect, paint);
    }

    private static void drawDrawableInRect(Canvas canvas, Context context, int drawableRes, RectF rect, Paint paint) {
        Drawable d = ContextCompat.getDrawable(context, drawableRes);
        if (d != null) {
            d.setBounds((int) rect.left, (int) rect.top, (int) rect.right, (int) rect.bottom);
            d.draw(canvas);
        }
    }

    public static Bitmap generateTintedBitmap(Context context, int drawableRes, int targetColor) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableRes);
        if (drawable == null) return null;

        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable) {
            bitmap = ((BitmapDrawable) drawable).getBitmap().copy(Bitmap.Config.ARGB_8888, true);
        } else {
            int w = drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 200;
            int h = drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 200;
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        int targetR = Color.red(targetColor);
        int targetG = Color.green(targetColor);
        int targetB = Color.blue(targetColor);

        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int a = (p >> 24) & 0xff;
            if (a < 30) continue;

            int r = (p >> 16) & 0xff;
            int g = (p >> 8) & 0xff;
            int b = p & 0xff;

            boolean isWhite = (r > 200 && g > 200 && b > 200);

            if (isWhite) {
                pixels[i] = p;
            } else {
                pixels[i] = (a << 24) | (targetR << 16) | (targetG << 8) | targetB;
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private static int getEyeSizeDp(String eyeBase) {
        if ("bigeye".equals(eyeBase) || "bigeyes2".equals(eyeBase) || "chill".equals(eyeBase) || "paceye".equals(eyeBase) || "smallpupil".equals(eyeBase)) {
            return 72;
        }
        return 60;
    }

    private static int getEyeContainerWidthDp(String eyeBase) {
        if ("bigeye".equals(eyeBase) || "bigeyes2".equals(eyeBase) || "chill".equals(eyeBase) || "brow_teardrop_lash_long".equals(eyeBase)) {
            return 145;
        }
        if ("smallpupil".equals(eyeBase)) {
            return 135;
        }
        return 132;
    }

    private static Bitmap generateBlushBitmap(int colorInt, int styleIndex) {
        int widthPx = 110;
        int heightPx = 75;
        float featherFactor = 0.75f;
        int alpha = 180;

        if (styleIndex == 0) {
            featherFactor = 0.25f;
            alpha = 210;
        } else if (styleIndex == 1) {
            featherFactor = 0.5f;
            alpha = 195;
        } else if (styleIndex == 2) {
            featherFactor = 0.75f;
            alpha = 180;
        } else if (styleIndex == 3) {
            featherFactor = 0.95f;
            alpha = 150;
        }

        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        float centerX = widthPx / 2.0f;
        float centerY = heightPx / 2.0f;
        float radius = Math.min(centerX, centerY);

        canvas.save();
        canvas.scale(1.0f, (float) heightPx / (float) widthPx, centerX, centerY);

        int r = Color.red(colorInt);
        int g = Color.green(colorInt);
        int b = Color.blue(colorInt);

        int centerColor = Color.argb(alpha, r, g, b);
        int edgeColor = Color.argb(0, r, g, b);

        float innerStop = Math.max(0.05f, 1.0f - featherFactor);

        RadialGradient shader = new RadialGradient(
                centerX, centerY, radius,
                new int[]{centerColor, centerColor, edgeColor},
                new float[]{0.0f, innerStop, 1.0f},
                Shader.TileMode.CLAMP
        );

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(shader);
        canvas.drawCircle(centerX, centerY, radius, paint);
        canvas.restore();

        return bitmap;
    }

    private static int getSwatchColorInt(String colorName) {
        if ("black".equalsIgnoreCase(colorName)) return Color.parseColor("#1E1E1E");
        if ("brown".equalsIgnoreCase(colorName)) return Color.parseColor("#5C3A21");
        if ("darkred".equalsIgnoreCase(colorName)) return Color.parseColor("#C82828");
        if ("orange".equalsIgnoreCase(colorName)) return Color.parseColor("#FF7F50");
        return Color.parseColor("#FF8A8A");
    }

    private static int getBlushColorInt(String colorName) {
        if ("darkred".equalsIgnoreCase(colorName)) return Color.parseColor("#C82828");
        if ("orange".equalsIgnoreCase(colorName)) return Color.parseColor("#FF8C00");
        return Color.parseColor("#FF8A8A");
    }

    private static float dpToPx(float dp, float density) {
        return dp * density;
    }
}
