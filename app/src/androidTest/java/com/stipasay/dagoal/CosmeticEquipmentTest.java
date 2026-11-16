package com.stipasay.dagoal;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

/** Uses separate test-only preference and database files, never user profile storage. */
@RunWith(AndroidJUnit4.class)
public class CosmeticEquipmentTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() {
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Context storage = target;
        context = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return storage.getSharedPreferences("cosmetic_test_" + name, mode);
            }
            @Override public File getDatabasePath(String name) {
                return storage.getDatabasePath("cosmetic_test_" + name);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory) {
                return storage.openOrCreateDatabase("cosmetic_test_" + name, mode, factory);
            }
            @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler handler) {
                return storage.openOrCreateDatabase("cosmetic_test_" + name, mode, factory, handler);
            }
        };
        prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        prefs.edit().clear().putBoolean("isGuestUser", true).commit();
        AvatarCompositor.clearCache();
    }

    private ShopItem item(int id) {
        for (ShopItem item : new TaskManager(context).getShopItems()) {
            if (item.getId() == id) return item;
        }
        throw new AssertionError("Missing catalog item " + id);
    }

    @Test public void equippedShirtSurvivesStaleAvatarSnapshotAndReload() {
        TaskManager.setEquippedItem(context, item(203));
        prefs.edit().putString("pref_avatar_clothes", "tank_top").commit();
        assertEquals("shirt_flame", AvatarConfig.loadFromPreferences(context).clothesAssetId);
        AvatarConfig.loadFromPreferences(context).saveToPreferences(context);
        assertEquals("shirt_flame", AvatarConfig.loadFromPreferences(context).clothesAssetId);
        TaskManager.setEquippedItem(context, item(204));
        assertEquals("shirt_frog", AvatarConfig.loadFromPreferences(context).clothesAssetId);
    }

    @Test public void slotsCoexistAndUnequipIndependently() {
        TaskManager.setEquippedItem(context, item(203));
        TaskManager.setEquippedItem(context, item(103));
        TaskManager.setEquippedItem(context, item(122));
        AvatarConfig config = AvatarConfig.loadFromPreferences(context);
        assertEquals("shirt_flame", config.clothesAssetId);
        assertEquals("accessory_glasses", config.glassesAssetId);
        assertEquals("accessory_knit_hat", config.hatAssetId);
        TaskManager.unequipItemInSlot(context, "clothes");
        prefs.edit().putString("pref_avatar_clothes", "shirt_flame").commit();
        config = AvatarConfig.loadFromPreferences(context);
        assertEquals("tank_top", config.clothesAssetId);
        assertEquals("accessory_glasses", config.glassesAssetId);
        TaskManager.unequipItemInSlot(context, "hat");
        config = AvatarConfig.loadFromPreferences(context);
        assertEquals("", config.hatAssetId);
        assertEquals("accessory_glasses", config.glassesAssetId);
        TaskManager.unequipItemInSlot(context, "glasses");
        assertEquals("", AvatarConfig.loadFromPreferences(context).glassesAssetId);
        TaskManager.setEquippedItem(context, null);
        assertEquals("tank_top", AvatarConfig.loadFromPreferences(context).clothesAssetId);
    }

    @Test public void everyCatalogCosmeticChangesRenderedAvatar() {
        Bitmap baseline = AvatarCompositor.renderAvatarBitmap(context,
                AvatarConfig.loadFromPreferences(context), 320).copy(Bitmap.Config.ARGB_8888, false);
        int checked = 0;
        for (ShopItem cosmetic : new TaskManager(context).getShopItems()) {
            TaskManager.setEquippedItem(context, null);
            TaskManager.setEquippedItem(context, cosmetic);
            Bitmap rendered = AvatarCompositor.renderAvatarBitmap(context,
                    AvatarConfig.loadFromPreferences(context), 320);
            assertNotNull(cosmetic.getName(), rendered);
            assertFalse("Invisible cosmetic: " + cosmetic.getName(), baseline.sameAs(rendered));
            checked++;
        }
        assertTrue("Expected coverage of clothes, glasses, hats and hair accessories", checked >= 50);
    }

    @Test public void glassesAreNotAlsoRenderedAsAHat() {
        TaskManager.setEquippedItem(context, item(103));
        AvatarConfig config = AvatarConfig.loadFromPreferences(context);
        Bitmap withLegacyAlias = AvatarCompositor.renderAvatarBitmap(context, config, 320)
                .copy(Bitmap.Config.ARGB_8888, false);
        config.accessoryAssetId = "";
        AvatarCompositor.clearCache();
        Bitmap withoutLegacyAlias = AvatarCompositor.renderAvatarBitmap(context, config, 320);
        assertTrue("Glasses must appear only in their own layer", withLegacyAlias.sameAs(withoutLegacyAlias));
    }

    @Test public void outfitsStayBelowFaceAtMultipleSizes() throws Exception {
        java.util.List<ShopItem> outfits = new java.util.ArrayList<>();
        for (ShopItem cosmetic : new TaskManager(context).getShopItems()) {
            if ("clothes".equals(TaskManager.getItemSlotType(cosmetic))) outfits.add(cosmetic);
        }
        assertEquals("Cover all catalog outfits", 20, outfits.size());
        Bitmap montage = Bitmap.createBitmap(5 * 160, 4 * 210, Bitmap.Config.ARGB_8888);
        android.graphics.Canvas preview = new android.graphics.Canvas(montage);
        preview.drawColor(android.graphics.Color.rgb(220, 204, 172));
        android.graphics.Paint label = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        label.setColor(android.graphics.Color.BLACK);
        label.setTextSize(10);
        for (int size : new int[]{160, 320, 640}) {
            AvatarConfig baselineConfig = new AvatarConfig();
            Bitmap baseline = AvatarCompositor.renderAvatarBitmap(context, baselineConfig, size);
            int faceBottom = (int) (size * (60f / 320f + 0.53f));
            int torsoBottom = Math.min(baseline.getHeight(), (int) (size * (60f / 320f + 0.88f)));
            int[] expectedFace = new int[size * faceBottom];
            baseline.getPixels(expectedFace, 0, size, 0, 0, size, faceBottom);
            int index = 0;
            for (ShopItem outfit : outfits) {
                AvatarConfig config = new AvatarConfig();
                config.clothesAssetId = outfit.getResName();
                Bitmap rendered = AvatarCompositor.renderAvatarBitmap(context, config, size);
                int[] actualFace = new int[size * faceBottom];
                rendered.getPixels(actualFace, 0, size, 0, 0, size, faceBottom);
                assertArrayEquals(outfit.getName() + " covers the face at " + size + "px",
                        expectedFace, actualFace);
                boolean torsoChanged = false;
                for (int y = faceBottom; y < torsoBottom && !torsoChanged; y++) {
                    for (int x = size / 3; x < size * 2 / 3; x++) {
                        if (baseline.getPixel(x, y) != rendered.getPixel(x, y)) {
                            torsoChanged = true;
                            break;
                        }
                    }
                }
                assertTrue(outfit.getName() + " must appear on the torso", torsoChanged);
                if (size == 160) {
                    int x = index % 5 * 160;
                    int y = index / 5 * 210;
                    preview.drawText(outfit.getName(), x + 3, y + 12, label);
                    preview.drawBitmap(rendered, x, y + 18, null);
                }
                index++;
            }
        }
        try (java.io.FileOutputStream output = new java.io.FileOutputStream(
                new File(context.getCacheDir(), "cosmetic-outfit-placement.png"))) {
            assertTrue(montage.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
    }

    @Test public void fullLengthShirtsCoverBothTorsoEdges() {
        Bitmap body = AvatarCompositor.generateSkinToneBitmap(context, 0);
        int checked = 0;
        for (ShopItem cosmetic : new TaskManager(context).getShopItems()) {
            String asset = cosmetic.getResName();
            if (!(asset.startsWith("shirt_") || asset.startsWith("puffy_")
                    || asset.startsWith("sleeveless_") || asset.startsWith("longsleeve_")
                    || asset.equals("long_sleeve_royal"))) continue;
            AvatarConfig light = new AvatarConfig();
            light.clothesAssetId = asset;
            light.skinIndex = 0;
            AvatarConfig dark = new AvatarConfig();
            dark.clothesAssetId = asset;
            dark.skinIndex = 10;
            int size = 640;
            Bitmap lightAvatar = AvatarCompositor.renderAvatarBitmap(context, light, size);
            Bitmap darkAvatar = AvatarCompositor.renderAvatarBitmap(context, dark, size);
            for (int sourceY = 820; sourceY <= 910; sourceY += 10) {
                int bodyY = Math.round(sourceY * body.getHeight() / 1080f);
                int left = body.getWidth() / 2;
                int right = left;
                while (left > 0 && android.graphics.Color.alpha(body.getPixel(left - 1, bodyY)) >= 200) left--;
                while (right < body.getWidth() - 1 && android.graphics.Color.alpha(body.getPixel(right + 1, bodyY)) >= 200) right++;
                int y = Math.round(size * (60f / 320f + sourceY / 1080f));
                int inset = Math.max(1, Math.round(size * 2f / 1080f));
                int leftX = Math.round(left * (float) size / body.getWidth()) + inset;
                int rightX = Math.round(right * (float) size / body.getWidth()) - inset;
                assertEquals(cosmetic.getName() + " exposes the left torso at " + sourceY,
                        lightAvatar.getPixel(leftX, y), darkAvatar.getPixel(leftX, y));
                assertEquals(cosmetic.getName() + " exposes the right torso at " + sourceY,
                        lightAvatar.getPixel(rightX, y), darkAvatar.getPixel(rightX, y));
            }
            checked++;
        }
        assertEquals("All full-length shirt variants must be checked", 12, checked);
    }
}
