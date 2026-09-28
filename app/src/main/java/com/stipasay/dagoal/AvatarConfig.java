package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

public class AvatarConfig {
    public int skinIndex;
    public String selectedColorName;
    public String selectedNoseShape;
    public String selectedMouthShape;
    public int selectedEyeColor;
    public String selectedEyeBase;
    public int cheeksIndex;
    public int hairIndex;
    public int selectedHairColor;
    public String selectedHairBase;
    public String clothesAssetId;

    public AvatarConfig() {
        this.skinIndex = 0;
        this.selectedColorName = "peach";
        this.selectedNoseShape = "triangle_nose";
        this.selectedMouthShape = "smile_01";
        this.selectedEyeColor = Color.parseColor("#1E90FF");
        this.selectedEyeBase = "bigeye";
        this.cheeksIndex = 0;
        this.hairIndex = 0;
        this.selectedHairColor = Color.parseColor("#3B2219");
        this.selectedHairBase = "01";
        this.clothesAssetId = "tank_top";
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase,
                        String clothesAssetId) {
        this.skinIndex = skinIndex;
        this.selectedColorName = selectedColorName != null ? selectedColorName : "peach";
        this.selectedNoseShape = selectedNoseShape != null ? selectedNoseShape : "triangle_nose";
        this.selectedMouthShape = selectedMouthShape != null ? selectedMouthShape : "smile_01";
        this.selectedEyeColor = selectedEyeColor;
        this.selectedEyeBase = selectedEyeBase != null ? selectedEyeBase : "bigeye";
        this.cheeksIndex = cheeksIndex;
        this.hairIndex = hairIndex;
        this.selectedHairColor = selectedHairColor;
        this.selectedHairBase = selectedHairBase != null ? selectedHairBase : "01";
        this.clothesAssetId = clothesAssetId != null ? clothesAssetId : "tank_top";
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase) {
        this(skinIndex, selectedColorName, selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, "tank_top");
    }

    public static AvatarConfig loadFromPreferences(Context context) {
        if (context == null) return new AvatarConfig();
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);

        int skinIndex = prefs.getInt("pref_avatar_skin", 0);
        String selectedColorName = prefs.getString("pref_avatar_color", "peach");
        String selectedNoseShape = prefs.getString("pref_avatar_nose_shape", "triangle_nose");
        String selectedMouthShape = prefs.getString("pref_avatar_mouth_shape", "smile_01");
        int selectedEyeColor = prefs.getInt("pref_avatar_eye_color", Color.parseColor("#1E90FF"));
        String selectedEyeBase = prefs.getString("pref_avatar_eye_base", "bigeye");
        int cheeksIndex = prefs.getInt("pref_avatar_cheeks", 0);
        int hairIndex = prefs.getInt("pref_avatar_hair", 0);
        int selectedHairColor = prefs.getInt("pref_avatar_hair_color", Color.parseColor("#3B2219"));
        String selectedHairBase = prefs.getString("pref_avatar_hair_base", "01");
        String clothesAssetId = prefs.getString("pref_avatar_clothes", "tank_top");

        return new AvatarConfig(skinIndex, selectedColorName, selectedNoseShape, selectedMouthShape,
                selectedEyeColor, selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId);
    }

    public void saveToPreferences(Context context) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        prefs.edit()
                .putInt("pref_avatar_skin", skinIndex)
                .putString("pref_avatar_color", selectedColorName)
                .putString("pref_avatar_nose_shape", selectedNoseShape)
                .putString("pref_avatar_mouth_shape", selectedMouthShape)
                .putInt("pref_avatar_eye_color", selectedEyeColor)
                .putString("pref_avatar_eye_base", selectedEyeBase)
                .putInt("pref_avatar_cheeks", cheeksIndex)
                .putInt("pref_avatar_hair", hairIndex)
                .putInt("pref_avatar_hair_color", selectedHairColor)
                .putString("pref_avatar_hair_base", selectedHairBase)
                .putString("pref_avatar_clothes", clothesAssetId)
                .apply();
    }

    public String getCacheKey(int outputSizePx) {
        return skinIndex + "_" + selectedColorName + "_" + selectedNoseShape + "_"
                + selectedMouthShape + "_" + selectedEyeColor + "_" + selectedEyeBase + "_"
                + cheeksIndex + "_" + hairIndex + "_" + selectedHairColor + "_" + selectedHairBase + "_"
                + clothesAssetId + "_" + outputSizePx;
    }
}
