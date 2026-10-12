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
        this.selectedColorName = "orange";
        this.selectedNoseColorName = "orange";
        this.selectedMouthColorName = "brown";
        this.selectedNoseShape = "triangle_nose";
        this.selectedMouthShape = "smile_01";
        this.selectedEyeColor = Color.parseColor("#1E1E1E");
        this.selectedEyeBase = "bigeye";
        this.cheeksIndex = 0;
        this.hairIndex = 0;
        this.selectedHairColor = Color.parseColor("#1E1E1E");
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
        this.selectedColorName = selectedColorName != null ? selectedColorName : "orange";
        this.selectedNoseColorName = selectedNoseColorName != null ? selectedNoseColorName : "orange";
        this.selectedMouthColorName = selectedMouthColorName != null ? selectedMouthColorName : "brown";
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
        this(skinIndex, selectedColorName, "orange", "brown", selectedNoseShape, selectedMouthShape, selectedEyeColor,
                selectedEyeBase, cheeksIndex, hairIndex, selectedHairColor, selectedHairBase, clothesAssetId, "", Color.parseColor("#1E1E1E"));
    }

    public AvatarConfig(int skinIndex, String selectedColorName, String selectedNoseShape,
                        String selectedMouthShape, int selectedEyeColor, String selectedEyeBase,
                        int cheeksIndex, int hairIndex, int selectedHairColor, String selectedHairBase) {
        this(skinIndex, selectedColorName, "orange", "brown", selectedNoseShape, selectedMouthShape, selectedEyeColor,
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
        String selectedColorName = prefs.getString("pref_avatar_color", "orange");
        String selectedNoseColorName = prefs.getString("pref_avatar_nose_color", "orange");
        String selectedMouthColorName = prefs.getString("pref_avatar_mouth_color", "brown");
        String selectedNoseShape = prefs.getString("pref_avatar_nose_shape", "triangle_nose");
        String selectedMouthShape = prefs.getString("pref_avatar_mouth_shape", "smile_01");
        int selectedEyeColor = prefs.getInt("pref_avatar_eye_color", Color.parseColor("#1E1E1E"));
        String selectedEyeBase = prefs.getString("pref_avatar_eye_base", "bigeye");
        int cheeksIndex = prefs.getInt("pref_avatar_cheeks", 0);
        int hairIndex = prefs.getInt("pref_avatar_hair", 0);
        int selectedHairColor = prefs.getInt("pref_avatar_hair_color", Color.parseColor("#1E1E1E"));
        String selectedHairBase = prefs.getString("pref_avatar_hair_base", "01");
        String clothesAssetId = prefs.getString("pref_avatar_clothes", "tank_top");

        // Equipped inventory is authoritative; a cloud avatar snapshot may contain older clothes.
        ShopItem equippedClothes = TaskManager.getEquippedItemForSlot(context, "clothes");
        if (equippedClothes != null && equippedClothes.getResName() != null
                && !equippedClothes.getResName().isEmpty()) {
            clothesAssetId = equippedClothes.getResName();
        } else if (prefs.contains("pref_equipped_clothes_id")) {
            clothesAssetId = "tank_top";
        }

        // Read equipped items for all 3 independent slots
        String glassesAssetId = "";
        int glassesColor = Color.parseColor("#1E1E1E");
        String hatAssetId = "";
        int hatColor = Color.parseColor("#1E1E1E");

        ShopItem equippedGlasses = TaskManager.getEquippedItemForSlot(context, "glasses");
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

    public static boolean hasAvatarDataInFirestoreDoc(com.google.firebase.firestore.DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return false;
        return doc.contains("avatar_skin_index") || doc.contains("avatar_color_name") || doc.contains("avatar_hair_base");
    }

    public static void saveUserAvatarToFirestore(Context context, String uid) {
        if (context == null || uid == null || uid.isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        boolean isGuest = prefs.getBoolean("isGuestUser", false);
        boolean isLoggedIn = prefs.getBoolean("isLoggedIn", false);
        if (isGuest || !isLoggedIn) {
            return;
        }
        AvatarConfig config = loadFromPreferences(context);
        config.saveToFirestore(uid);
    }

    public void saveToFirestore(String uid) {
        if (uid == null || uid.isEmpty()) return;
        try {
            com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("avatar_skin_index", skinIndex);
            map.put("avatar_color_name", selectedColorName != null ? selectedColorName : "orange");
            map.put("avatar_nose_color", selectedNoseColorName != null ? selectedNoseColorName : "orange");
            map.put("avatar_mouth_color", selectedMouthColorName != null ? selectedMouthColorName : "brown");
            map.put("avatar_nose_shape", selectedNoseShape != null ? selectedNoseShape : "triangle_nose");
            map.put("avatar_mouth_shape", selectedMouthShape != null ? selectedMouthShape : "smile_01");
            map.put("avatar_eye_color", selectedEyeColor);
            map.put("avatar_eye_base", selectedEyeBase != null ? selectedEyeBase : "bigeye");
            map.put("avatar_cheeks_index", cheeksIndex);
            map.put("avatar_hair_index", hairIndex);
            map.put("avatar_hair_color", selectedHairColor);
            map.put("avatar_hair_base", selectedHairBase != null ? selectedHairBase : "01");
            map.put("avatar_clothes", clothesAssetId != null ? clothesAssetId : "tank_top");

            firestore.collection("users").document(uid).set(map, com.google.firebase.firestore.SetOptions.merge());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void restoreUserAvatarFromFirestoreDoc(com.google.firebase.firestore.DocumentSnapshot doc, Context context) {
        if (doc == null || !doc.exists() || context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            if (doc.contains("avatar_skin_index")) {
                Long val = doc.getLong("avatar_skin_index");
                if (val != null) editor.putInt("pref_avatar_skin", val.intValue());
            }
            if (doc.contains("avatar_color_name")) {
                String val = doc.getString("avatar_color_name");
                if (val != null) editor.putString("pref_avatar_color", val);
            }
            if (doc.contains("avatar_nose_color")) {
                String val = doc.getString("avatar_nose_color");
                if (val != null) editor.putString("pref_avatar_nose_color", val);
            }
            if (doc.contains("avatar_mouth_color")) {
                String val = doc.getString("avatar_mouth_color");
                if (val != null) editor.putString("pref_avatar_mouth_color", val);
            }
            if (doc.contains("avatar_nose_shape")) {
                String val = doc.getString("avatar_nose_shape");
                if (val != null) editor.putString("pref_avatar_nose_shape", val);
            }
            if (doc.contains("avatar_mouth_shape")) {
                String val = doc.getString("avatar_mouth_shape");
                if (val != null) editor.putString("pref_avatar_mouth_shape", val);
            }
            if (doc.contains("avatar_eye_color")) {
                Long val = doc.getLong("avatar_eye_color");
                if (val != null) editor.putInt("pref_avatar_eye_color", val.intValue());
            }
            if (doc.contains("avatar_eye_base")) {
                String val = doc.getString("avatar_eye_base");
                if (val != null) editor.putString("pref_avatar_eye_base", val);
            }
            if (doc.contains("avatar_cheeks_index")) {
                Long val = doc.getLong("avatar_cheeks_index");
                if (val != null) editor.putInt("pref_avatar_cheeks", val.intValue());
            }
            if (doc.contains("avatar_hair_index")) {
                Long val = doc.getLong("avatar_hair_index");
                if (val != null) editor.putInt("pref_avatar_hair", val.intValue());
            }
            if (doc.contains("avatar_hair_color")) {
                Long val = doc.getLong("avatar_hair_color");
                if (val != null) editor.putInt("pref_avatar_hair_color", val.intValue());
            }
            if (doc.contains("avatar_hair_base")) {
                String val = doc.getString("avatar_hair_base");
                if (val != null) editor.putString("pref_avatar_hair_base", val);
            }
            if (doc.contains("avatar_clothes")) {
                String val = doc.getString("avatar_clothes");
                if (val != null) editor.putString("pref_avatar_clothes", val);
            }

            editor.apply();
            AvatarCompositor.clearCache();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
