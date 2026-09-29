package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AvatarConfig {
    public int skinIndex;
    public String selectedColorName;
    public String selectedNoseColorName;
    public String selectedMouthColorName;
    public String selectedNoseShape;
    public String selectedMouthShape;
    public int selectedEyeColor;
    public String selectedEyeBase;
    public int cheeksIndex;
    public int hairIndex;
    public int selectedHairColor;
    public String selectedHairBase;
    public String clothesAssetId;
    public String accessoryAssetId;
    public int accessoryColor;
    public String glassesAssetId;
    public int glassesColor;
    public String hatAssetId;
    public int hatColor;

    public AvatarConfig() {
        this.skinIndex = 0;
        this.selectedColorName = "peach";
        this.selectedNoseColorName = "black";
        this.selectedMouthColorName = "peach";
        this.selectedNoseShape = "triangle_nose";
        this.selectedMouthShape = "smile_01";
        this.selectedEyeColor = Color.parseColor("#1E90FF");
        this.selectedEyeBase = "bigeye";
        this.cheeksIndex = 0;
        this.hairIndex = 0;
        this.selectedHairColor = Color.parseColor("#3B2219");
        this.selectedHairBase = "01";
        this.clothesAssetId = "tank_top";
        this.accessoryAssetId = "";
        this.accessoryColor = Color.parseColor("#1E1E1E");
        this.glassesAssetId = "";
        this.glassesColor = Color.parseColor("#1E1E1E");
        this.hatAssetId = "";
        this.hatColor = Color.parseColor("#1E1E1E");
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseColorName, String selectedMouthColorName,
                        String selectedNoseShape, String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId, String glassesAssetId, int glassesColor, String hatAssetId, int hatColor) {
        this.skinIndex = skinIndex;
        this.selectedColorName = selectedColorName != null ? selectedColorName : "peach";
        this.selectedNoseColorName = selectedNoseColorName != null ? selectedNoseColorName : "black";
        this.selectedMouthColorName = selectedMouthColorName != null ? selectedMouthColorName : "peach";
        this.selectedNoseShape = selectedNoseShape != null ? selectedNoseShape : "triangle_nose";
        this.selectedMouthShape = selectedMouthShape != null ? selectedMouthShape : "smile_01";
        this.selectedEyeColor = selectedEyeColor;
        this.selectedEyeBase = selectedEyeBase != null ? selectedEyeBase : "bigeye";
        this.cheeksIndex = cheeksIndex;
        this.hairIndex = hairIndex;
        this.selectedHairColor = selectedHairColor;
        this.selectedHairBase = selectedHairBase != null ? selectedHairBase : "01";
        this.clothesAssetId = clothesAssetId != null ? clothesAssetId : "tank_top";
        this.glassesAssetId = glassesAssetId != null ? glassesAssetId : "";
        this.glassesColor = glassesColor;
        this.hatAssetId = hatAssetId != null ? hatAssetId : "";
        this.hatColor = hatColor;
        this.accessoryAssetId = this.glassesAssetId;
        this.accessoryColor = this.glassesColor;
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseColorName, String selectedMouthColorName,
                        String selectedNoseShape, String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId, String accessoryAssetId, int accessoryColor) {
        this(skinIndex, selectedColorName, selectedNoseColorName, selectedMouthColorName, selectedNoseShape, selectedMouthShape,
                selectedEyeColor, selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId,
                accessoryAssetId, accessoryColor, "", Color.parseColor("#1E1E1E"));
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId, String accessoryAssetId, int accessoryColor) {
        this(skinIndex, selectedColorName, "black", "peach", selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId, accessoryAssetId, accessoryColor);
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseColorName, String selectedMouthColorName,
                        String selectedNoseShape, String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId) {
        this(skinIndex, selectedColorName, selectedNoseColorName, selectedMouthColorName, selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId, "", Color.parseColor("#1E1E1E"));
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId) {
        this(skinIndex, selectedColorName, "black", "peach", selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId, "", Color.parseColor("#1E1E1E"));
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase) {
        this(skinIndex, selectedColorName, "black", "peach", selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, "tank_top", "", Color.parseColor("#1E1E1E"));
    }

    public static int parseAccessoryColor(String colorName) {
        if ("purple".equalsIgnoreCase(colorName)) return Color.parseColor("#800080");
        if ("yellow".equalsIgnoreCase(colorName)) return Color.parseColor("#FFD700");
        if ("red".equalsIgnoreCase(colorName)) return Color.parseColor("#E53935");
        if ("blue".equalsIgnoreCase(colorName)) return Color.parseColor("#1E88E5");
        if ("black".equalsIgnoreCase(colorName)) return Color.parseColor("#1E1E1E");
        if ("orange".equalsIgnoreCase(colorName)) return Color.parseColor("#FFA500");
        if ("pink".equalsIgnoreCase(colorName)) return Color.parseColor("#FF69B4");
        if ("straw".equalsIgnoreCase(colorName)) return Color.parseColor("#E6C280");
        return Color.parseColor("#1E1E1E");
    }

    public static AvatarConfig loadFromPreferences(Context context) {
        if (context == null) return new AvatarConfig();
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);

        int skinIndex = prefs.getInt("pref_avatar_skin", 0);
        String selectedColorName = prefs.getString("pref_avatar_color", "peach");
        String selectedNoseColorName = prefs.getString("pref_avatar_nose_color", "black");
        String selectedMouthColorName = prefs.getString("pref_avatar_mouth_color", selectedColorName);
        String selectedNoseShape = prefs.getString("pref_avatar_nose_shape", "triangle_nose");
        String selectedMouthShape = prefs.getString("pref_avatar_mouth_shape", "smile_01");
        int selectedEyeColor = prefs.getInt("pref_avatar_eye_color", Color.parseColor("#1E90FF"));
        String selectedEyeBase = prefs.getString("pref_avatar_eye_base", "bigeye");
        int cheeksIndex = prefs.getInt("pref_avatar_cheeks", 0);
        int hairIndex = prefs.getInt("pref_avatar_hair", 0);
        int selectedHairColor = prefs.getInt("pref_avatar_hair_color", Color.parseColor("#3B2219"));
        String selectedHairBase = prefs.getString("pref_avatar_hair_base", "01");
        String clothesAssetId = prefs.getString("pref_avatar_clothes", "tank_top");

        // Read equipped items for all 3 independent slots
        String glassesAssetId = "";
        int glassesColor = Color.parseColor("#1E1E1E");
        String hatAssetId = "";
        int hatColor = Color.parseColor("#1E1E1E");

        ShopItem equippedGlasses = TaskManager.getEquippedItemForSlot(context, "glasses");
        if (equippedGlasses == null) equippedGlasses = TaskManager.getEquippedItem(context);
        if (equippedGlasses != null) {
            String resName = equippedGlasses.getResName();
            if (resName != null) {
                int lastUnderscore = resName.lastIndexOf('_');
                if (lastUnderscore > 0 && resName.startsWith("accessory_")) {
                    glassesAssetId = resName.substring(0, lastUnderscore);
                    glassesColor = parseAccessoryColor(resName.substring(lastUnderscore + 1));
                } else {
                    glassesAssetId = resName;
                }
            }
        }

        ShopItem equippedHat = TaskManager.getEquippedItemForSlot(context, "hat");
        if (equippedHat != null) {
            String resName = equippedHat.getResName();
            if (resName != null) {
                int lastUnderscore = resName.lastIndexOf('_');
                if (lastUnderscore > 0 && resName.startsWith("accessory_")) {
                    hatAssetId = resName.substring(0, lastUnderscore);
                    hatColor = parseAccessoryColor(resName.substring(lastUnderscore + 1));
                } else {
                    hatAssetId = resName;
                }
            }
        }

        return new AvatarConfig(skinIndex, selectedColorName, selectedNoseColorName, selectedMouthColorName,
                selectedNoseShape, selectedMouthShape, selectedEyeColor, selectedEyeBase, cheeksIndex, hairIndex,
                selectedHairColor, selectedHairBase, clothesAssetId, glassesAssetId, glassesColor, hatAssetId, hatColor);
    }

    public void saveToPreferences(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("pref_avatar_skin", skinIndex)
                .putString("pref_avatar_color", selectedColorName)
                .putString("pref_avatar_nose_color", selectedNoseColorName)
                .putString("pref_avatar_mouth_color", selectedMouthColorName)
                .putString("pref_avatar_nose_shape", selectedNoseShape)
                .putString("pref_avatar_mouth_shape", selectedMouthShape)
                .putInt("pref_avatar_eye_color", selectedEyeColor)
                .putString("pref_avatar_eye_base", selectedEyeBase)
                .putInt("pref_avatar_cheeks", cheeksIndex)
                .putInt("pref_avatar_hair", hairIndex)
                .putInt("pref_avatar_hair_color", selectedHairColor)
                .putString("pref_avatar_hair_base", selectedHairBase)
                .putString("pref_avatar_clothes", clothesAssetId)
                .remove("pref_avatar_accessory")
                .remove("pref_avatar_accessory_color")
                .apply();
    }

    public String getCacheKey(int outputSizePx) {
        return skinIndex + "_" + selectedColorName + "_" + selectedNoseColorName + "_" + selectedMouthColorName + "_" + selectedNoseShape + "_"
                + selectedMouthShape + "_" + selectedEyeColor + "_" + selectedEyeBase + "_"
                + cheeksIndex + "_" + hairIndex + "_" + selectedHairColor + "_" + selectedHairBase + "_"
                + clothesAssetId + "_" + glassesAssetId + "_" + glassesColor + "_" + hatAssetId + "_" + hatColor + "_" + outputSizePx;
    }
}
