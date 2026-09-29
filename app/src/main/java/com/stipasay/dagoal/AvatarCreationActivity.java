package com.stipasay.dagoal;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AvatarCreationActivity extends AppCompatActivity {

    private Button btnSaveAvatar;
    private ImageButton tabHair, tabEyes, tabNose, tabMouth, tabCheeks, tabSkin;
    private GridLayout gridAssets;

    private View layoutEyesContainer, layoutCheeksContainer;
    private ImageView ivLayerHairBack, ivLayerHairFront, ivLayerHairAcc, ivLayerEyeLeft, ivLayerEyeRight, ivLayerNose, ivLayerMouth, ivLayerCheekLeft, ivLayerCheekRight;

    private String activeCategory = "Skin";
    private String selectedColorName = "peach"; // black, brown, darkred, orange, peach
    private String selectedNoseColorName = "black";
    private String selectedMouthColorName = "peach";
    private String selectedNoseShape = "triangle_nose"; // triangle_nose, square_nose, oblong_nose
    private String selectedMouthShape = "smile_01"; // smile_01, smile_02, w_01, w_02
    private int selectedEyeColor = Color.parseColor("#1E90FF");
    private String selectedEyeBase = "bigeye";

    private int selectedHairColor = Color.parseColor("#3B2219"); // Dark Brown default
    private String selectedHairBase = "01"; // "01" .. "10"

    private int selectedHairIndex = 0;
    private int selectedEyesIndex = 0;
    private int selectedNoseIndex = 0;
    private int selectedMouthIndex = 0;
    private int selectedCheeksIndex = 0;
    private int selectedSkinIndex = 0;

    private final String[] noseShapes = {"triangle_nose", "square_nose", "oblong_nose"};
    private final String[] mouthShapes = {"smile_01", "smile_02", "w_01", "w_02", "smile_bucktooth"};
    private final String[] eyeBases = {
            "bigeye",
            "bigeyes2",
            "chill",
            "paceye",
            "smallpupil",
            "brow_teardrop_fully_open",
            "brow_teardrop_lash_long",
            "brow_teardrop_lash_short",
            "brow_teardrop_closed",
            "crescent_lashline_fully_open",
            "crescent_lashline_lash_long",
            "crescent_lashline_lash_short",
            "crescent_shaded_fully_open",
            "crescent_shaded_lash_long",
            "crescent_shaded_lash_short",
            "oval_split_highlight_lash_long",
            "oval_split_highlight_lash_short",
            "ring_solid_fully_open",
            "ring_solid_lash_short"
    };
    private final String[] hairBases = {
            "01", "02", "03", "04", "05", "06", "07", "08", "09", "10",
            "11", "12", "13", "14", "15", "16", "17", "18", "19", "20",
            "21", "22", "23", "24", "25", "26", "27", "28", "29", "30"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_avatar_creation);

        btnSaveAvatar = findViewById(R.id.btn_save_avatar);
        tabHair = findViewById(R.id.tab_hair);
        tabEyes = findViewById(R.id.tab_eyes);
        tabNose = findViewById(R.id.tab_nose);
        tabMouth = findViewById(R.id.tab_mouth);
        tabCheeks = findViewById(R.id.tab_cheeks);
        tabSkin = findViewById(R.id.tab_skin);
        gridAssets = findViewById(R.id.grid_assets);

        layoutEyesContainer = findViewById(R.id.layout_eyes_container);
        layoutCheeksContainer = findViewById(R.id.layout_cheeks_container);

        ivLayerHairBack = findViewById(R.id.iv_layer_hair_back);
        ivLayerHairFront = findViewById(R.id.iv_layer_hair_front);
        ivLayerHairAcc = findViewById(R.id.iv_layer_hair_acc);

        ivLayerEyeLeft = findViewById(R.id.iv_layer_eye_left);
        ivLayerEyeRight = findViewById(R.id.iv_layer_eye_right);
        ivLayerNose = findViewById(R.id.iv_layer_nose);
        ivLayerMouth = findViewById(R.id.iv_layer_mouth);
        ivLayerCheekLeft = findViewById(R.id.iv_layer_cheek_left);
        ivLayerCheekRight = findViewById(R.id.iv_layer_cheek_right);

        ImageButton btnBack = findViewById(R.id.btn_back_avatar);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        View rootLayout = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        // Left Color Bar Swatches
        View swatchBlack = findViewById(R.id.color_swatch_black);
        View swatchBrown = findViewById(R.id.color_swatch_brown);
        View swatchDarkRed = findViewById(R.id.color_swatch_darkred);
        View swatchOrange = findViewById(R.id.color_swatch_orange);
        View swatchPeach = findViewById(R.id.color_swatch_peach);
        TextView swatchEyePicker = findViewById(R.id.color_swatch_eye_picker);
        TextView swatchHairPicker = findViewById(R.id.color_swatch_hair_picker);

        if (swatchBlack != null) swatchBlack.setOnClickListener(v -> selectColor("black"));
        if (swatchBrown != null) swatchBrown.setOnClickListener(v -> selectColor("brown"));
        if (swatchDarkRed != null) swatchDarkRed.setOnClickListener(v -> selectColor("darkred"));
        if (swatchOrange != null) swatchOrange.setOnClickListener(v -> selectColor("orange"));
        if (swatchPeach != null) swatchPeach.setOnClickListener(v -> selectColor("peach"));
        if (swatchEyePicker != null) swatchEyePicker.setOnClickListener(v -> showEyeColorPickerDialog());
        if (swatchHairPicker != null) swatchHairPicker.setOnClickListener(v -> showHairColorPickerDialog());

        // Right Sidebar Category Tabs
        tabHair.setOnClickListener(v -> switchTab("Hair", tabHair));
        tabEyes.setOnClickListener(v -> switchTab("Eyes", tabEyes));
        tabNose.setOnClickListener(v -> switchTab("Nose", tabNose));
        tabMouth.setOnClickListener(v -> switchTab("Mouth", tabMouth));
        if (tabCheeks != null) tabCheeks.setOnClickListener(v -> switchTab("Cheeks", tabCheeks));
        if (tabSkin != null) tabSkin.setOnClickListener(v -> switchTab("Skin", tabSkin));

        // Pre-load saved preferences if editing existing avatar
        AvatarConfig savedConfig = AvatarConfig.loadFromPreferences(this);
        this.selectedSkinIndex = savedConfig.skinIndex;
        this.selectedColorName = savedConfig.selectedColorName;
        this.selectedNoseColorName = savedConfig.selectedNoseColorName;
        this.selectedMouthColorName = savedConfig.selectedMouthColorName;
        this.selectedNoseShape = savedConfig.selectedNoseShape;
        this.selectedMouthShape = savedConfig.selectedMouthShape;
        this.selectedEyeColor = savedConfig.selectedEyeColor;
        this.selectedEyeBase = savedConfig.selectedEyeBase;
        this.selectedCheeksIndex = savedConfig.cheeksIndex;
        this.selectedHairIndex = savedConfig.hairIndex;
        this.selectedHairColor = savedConfig.selectedHairColor;
        this.selectedHairBase = savedConfig.selectedHairBase;

        for (int i = 0; i < mouthShapes.length; i++) {
            if (mouthShapes[i].equalsIgnoreCase(savedConfig.selectedMouthShape)) {
                this.selectedMouthIndex = i;
                break;
            }
        }
        for (int i = 0; i < noseShapes.length; i++) {
            if (noseShapes[i].equalsIgnoreCase(savedConfig.selectedNoseShape)) {
                this.selectedNoseIndex = i;
                break;
            }
        }
        for (int i = 0; i < eyeBases.length; i++) {
            if (eyeBases[i].equalsIgnoreCase(savedConfig.selectedEyeBase)) {
                this.selectedEyesIndex = i;
                break;
            }
        }

        boolean isEditMode = getIntent().getBooleanExtra("extra_edit_mode", false);
        if (isEditMode && btnSaveAvatar != null) {
            btnSaveAvatar.setText("Save Changes");
        }

        // Apply saved features on live preview canvas
        updateEyeLayers();
        updateHairLayers();
        updateBlushLayers();
        applyAssetSelection("Nose", selectedNoseIndex);
        applyAssetSelection("Mouth", selectedMouthIndex);
        applyAssetSelection("Skin", selectedSkinIndex);

        // Initial selection: Skin Category
        if (tabSkin != null) {
            tabSkin.setSelected(true);
            switchTab("Skin", tabSkin);
        } else {
            tabHair.setSelected(true);
            switchTab("Hair", tabHair);
        }

        btnSaveAvatar.setOnClickListener(v -> saveCustomizationAndProceed());
    }

    private void selectColor(String colorName) {
        if ("Nose".equals(activeCategory)) {
            this.selectedNoseColorName = colorName;
        } else if ("Mouth".equals(categoryForSelectColor())) {
            this.selectedMouthColorName = colorName;
        } else {
            this.selectedColorName = colorName;
        }
        ToastUtils.showToast(this, "Color: " + colorName.toUpperCase() + " Selected!");

        // Refresh category grid and live feature layer preview
        ImageButton currentTab = getTabButton(activeCategory);
        switchTab(activeCategory, currentTab);

        if ("Cheeks".equals(activeCategory)) {
            updateBlushLayers();
        } else {
            int activeIndex = "Nose".equals(activeCategory) ? selectedNoseIndex : ("Mouth".equals(activeCategory) ? selectedMouthIndex : 0);
            applyAssetSelection(activeCategory, activeIndex);
        }
    }

    private String categoryForSelectColor() {
        return activeCategory;
    }

    private void showEyeColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_eye_color_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View preview = dialogView.findViewById(R.id.view_eye_color_preview);
        SeekBar sbRed = dialogView.findViewById(R.id.sb_eye_red);
        SeekBar sbGreen = dialogView.findViewById(R.id.sb_eye_green);
        SeekBar sbBlue = dialogView.findViewById(R.id.sb_eye_blue);
        TextView tvRed = dialogView.findViewById(R.id.tv_eye_red_val);
        TextView tvGreen = dialogView.findViewById(R.id.tv_eye_green_val);
        TextView tvBlue = dialogView.findViewById(R.id.tv_eye_blue_val);
        Button btnApply = dialogView.findViewById(R.id.btn_apply_eye_color);

        int initR = Color.red(selectedEyeColor);
        int initG = Color.green(selectedEyeColor);
        int initB = Color.blue(selectedEyeColor);

        if (sbRed != null) sbRed.setProgress(initR);
        if (sbGreen != null) sbGreen.setProgress(initG);
        if (sbBlue != null) sbBlue.setProgress(initB);

        Runnable updatePreview = () -> {
            int r = (sbRed != null) ? sbRed.getProgress() : 30;
            int g = (sbGreen != null) ? sbGreen.getProgress() : 144;
            int b = (sbBlue != null) ? sbBlue.getProgress() : 255;

            if (tvRed != null) tvRed.setText(String.valueOf(r));
            if (tvGreen != null) tvGreen.setText(String.valueOf(g));
            if (tvBlue != null) tvBlue.setText(String.valueOf(b));

            int c = Color.rgb(r, g, b);
            if (preview != null) {
                preview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c));
            }
        };

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updatePreview.run(); }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        if (sbRed != null) sbRed.setOnSeekBarChangeListener(listener);
        if (sbGreen != null) sbGreen.setOnSeekBarChangeListener(listener);
        if (sbBlue != null) sbBlue.setOnSeekBarChangeListener(listener);

        setupPresetColor(dialogView, R.id.preset_blue, Color.parseColor("#1E90FF"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_green, Color.parseColor("#2E8B57"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_brown, Color.parseColor("#8B4513"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hazel, Color.parseColor("#DAA520"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_purple, Color.parseColor("#8A2BE2"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_crimson, Color.parseColor("#DC143C"), sbRed, sbGreen, sbBlue, updatePreview);

        updatePreview.run();

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                int r = (sbRed != null) ? sbRed.getProgress() : 30;
                int g = (sbGreen != null) ? sbGreen.getProgress() : 144;
                int b = (sbBlue != null) ? sbBlue.getProgress() : 255;
                selectedEyeColor = Color.rgb(r, g, b);

                updateEyeLayers();

                // Refresh grid thumbnails to show tinted eyes
                ImageButton currentTab = getTabButton(activeCategory);
                switchTab(activeCategory, currentTab);

                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void showHairColorPickerDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_hair_color_picker, null);
        builder.setView(dialogView);

        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        View preview = dialogView.findViewById(R.id.view_hair_color_preview);
        SeekBar sbRed = dialogView.findViewById(R.id.sb_hair_red);
        SeekBar sbGreen = dialogView.findViewById(R.id.sb_hair_green);
        SeekBar sbBlue = dialogView.findViewById(R.id.sb_hair_blue);
        TextView tvRed = dialogView.findViewById(R.id.tv_hair_red_val);
        TextView tvGreen = dialogView.findViewById(R.id.tv_hair_green_val);
        TextView tvBlue = dialogView.findViewById(R.id.tv_hair_blue_val);
        Button btnApply = dialogView.findViewById(R.id.btn_apply_hair_color);

        int initR = Color.red(selectedHairColor);
        int initG = Color.green(selectedHairColor);
        int initB = Color.blue(selectedHairColor);

        if (sbRed != null) sbRed.setProgress(initR);
        if (sbGreen != null) sbGreen.setProgress(initG);
        if (sbBlue != null) sbBlue.setProgress(initB);

        Runnable updatePreview = () -> {
            int r = (sbRed != null) ? sbRed.getProgress() : 59;
            int g = (sbGreen != null) ? sbGreen.getProgress() : 34;
            int b = (sbBlue != null) ? sbBlue.getProgress() : 25;

            if (tvRed != null) tvRed.setText(String.valueOf(r));
            if (tvGreen != null) tvGreen.setText(String.valueOf(g));
            if (tvBlue != null) tvBlue.setText(String.valueOf(b));

            int c = Color.rgb(r, g, b);
            if (preview != null) {
                preview.setBackgroundTintList(android.content.res.ColorStateList.valueOf(c));
            }
        };

        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updatePreview.run(); }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        if (sbRed != null) sbRed.setOnSeekBarChangeListener(listener);
        if (sbGreen != null) sbGreen.setOnSeekBarChangeListener(listener);
        if (sbBlue != null) sbBlue.setOnSeekBarChangeListener(listener);

        setupPresetColor(dialogView, R.id.preset_hair_black, Color.parseColor("#1E1E1E"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hair_brown, Color.parseColor("#3B2219"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hair_blonde, Color.parseColor("#E6BE75"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hair_auburn, Color.parseColor("#A52A2A"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hair_silver, Color.parseColor("#D3D3D3"), sbRed, sbGreen, sbBlue, updatePreview);
        setupPresetColor(dialogView, R.id.preset_hair_pink, Color.parseColor("#FFB6C1"), sbRed, sbGreen, sbBlue, updatePreview);

        updatePreview.run();

        if (btnApply != null) {
            btnApply.setOnClickListener(v -> {
                int r = (sbRed != null) ? sbRed.getProgress() : 59;
                int g = (sbGreen != null) ? sbGreen.getProgress() : 34;
                int b = (sbBlue != null) ? sbBlue.getProgress() : 25;
                selectedHairColor = Color.rgb(r, g, b);

                updateHairLayers();

                // Refresh grid thumbnails
                ImageButton currentTab = getTabButton(activeCategory);
                switchTab(activeCategory, currentTab);

                dialog.dismiss();
            });
        }

        dialog.show();
    }

    private void setupPresetColor(View dialogView, int viewId, int color, SeekBar sbR, SeekBar sbG, SeekBar sbB, Runnable update) {
        View preset = dialogView.findViewById(viewId);
        if (preset != null) {
            preset.setOnClickListener(v -> {
                if (sbR != null) sbR.setProgress(Color.red(color));
                if (sbG != null) sbG.setProgress(Color.green(color));
                if (sbB != null) sbB.setProgress(Color.blue(color));
                update.run();
            });
        }
    }

    private Bitmap getTintedEyeBitmap(int drawableRes, int targetColor) {
        Drawable drawable = ContextCompat.getDrawable(this, drawableRes);
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

    private int getEyeSizeDp(String eyeBase) {
        if ("bigeye".equals(eyeBase) || "bigeyes2".equals(eyeBase) || "chill".equals(eyeBase) || "paceye".equals(eyeBase) || "smallpupil".equals(eyeBase)) {
            return 72;
        }
        return 60;
    }

    private int getEyeContainerWidthDp(String eyeBase) {
        if ("bigeye".equals(eyeBase) || "bigeyes2".equals(eyeBase) || "chill".equals(eyeBase) || "brow_teardrop_lash_long".equals(eyeBase)) {
            return 145;
        }
        if ("smallpupil".equals(eyeBase)) {
            return 135;
        }
        return 132;
    }

    private void updateEyeLayers() {
        if (layoutEyesContainer == null) return;
        layoutEyesContainer.setVisibility(View.VISIBLE);

        String leftName = selectedEyeBase + "_a";
        String rightName = selectedEyeBase + "_b";

        if ("ring_solid_lash_long".equals(selectedEyeBase)) {
            leftName = "ring_solid_lash_long_a_512x512";
        }

        int leftRes = getResources().getIdentifier(leftName, "drawable", getPackageName());
        int rightRes = getResources().getIdentifier(rightName, "drawable", getPackageName());

        if (leftRes == 0) leftRes = R.drawable.eye_category_icon;
        if (rightRes == 0) rightRes = leftRes;

        Bitmap leftBitmap = getTintedEyeBitmap(leftRes, selectedEyeColor);
        Bitmap rightBitmap = getTintedEyeBitmap(rightRes, selectedEyeColor);

        int eyeSizeDp = getEyeSizeDp(selectedEyeBase);
        int eyeContainerWidthDp = getEyeContainerWidthDp(selectedEyeBase);

        if (layoutEyesContainer != null) {
            android.view.ViewGroup.LayoutParams p = layoutEyesContainer.getLayoutParams();
            if (p != null) {
                p.width = dpToPx(eyeContainerWidthDp);
                layoutEyesContainer.setLayoutParams(p);
            }
        }

        if (ivLayerEyeLeft != null) {
            android.view.ViewGroup.LayoutParams p = ivLayerEyeLeft.getLayoutParams();
            if (p != null) {
                p.width = dpToPx(eyeSizeDp);
                p.height = dpToPx(eyeSizeDp);
                ivLayerEyeLeft.setLayoutParams(p);
            }
            ivLayerEyeLeft.clearColorFilter();
            if (leftBitmap != null) ivLayerEyeLeft.setImageBitmap(leftBitmap);
            else ivLayerEyeLeft.setImageResource(leftRes);
        }

        if (ivLayerEyeRight != null) {
            android.view.ViewGroup.LayoutParams p = ivLayerEyeRight.getLayoutParams();
            if (p != null) {
                p.width = dpToPx(eyeSizeDp);
                p.height = dpToPx(eyeSizeDp);
                ivLayerEyeRight.setLayoutParams(p);
            }
            ivLayerEyeRight.clearColorFilter();
            if (rightBitmap != null) ivLayerEyeRight.setImageBitmap(rightBitmap);
            else ivLayerEyeRight.setImageResource(rightRes);
        }
    }

    private void updateHairLayers() {
        if (ivLayerHairBack == null) return;

        if (selectedHairIndex == 0) {
            ivLayerHairBack.setVisibility(View.GONE);
            if (ivLayerHairFront != null) ivLayerHairFront.setVisibility(View.GONE);
            if (ivLayerHairAcc != null) ivLayerHairAcc.setVisibility(View.GONE);
            return;
        }

        float universalHairOffsetY = -dpToPx(39);
        float universalHairOffsetX = -dpToPx(1.0f);

        String num = selectedHairBase;
        int frontRes = getResources().getIdentifier("hairstyle_" + num + "a", "drawable", getPackageName());
        int backRes = getResources().getIdentifier("hairstyle_" + num + "b", "drawable", getPackageName());

        Bitmap frontBitmap = (frontRes != 0) ? getTintedEyeBitmap(frontRes, selectedHairColor) : null;
        Bitmap backBitmap = (backRes != 0) ? getTintedEyeBitmap(backRes, selectedHairColor) : null;

        // Layer 0: Back Hair (_b)
        if (ivLayerHairBack != null) {
            ivLayerHairBack.setTranslationY(universalHairOffsetY);
            ivLayerHairBack.setTranslationX(universalHairOffsetX);
            ivLayerHairBack.setScaleX(1.0f);
            ivLayerHairBack.setScaleY(1.0f);
            ivLayerHairBack.clearColorFilter();
            if (backBitmap != null) {
                ivLayerHairBack.setImageBitmap(backBitmap);
                ivLayerHairBack.setVisibility(View.VISIBLE);
            } else {
                ivLayerHairBack.setVisibility(View.GONE);
            }
        }

        // Layer 6: Front Hair / Bangs (_a)
        if (ivLayerHairFront != null) {
            ivLayerHairFront.setTranslationY(universalHairOffsetY);
            ivLayerHairFront.setTranslationX(universalHairOffsetX);
            ivLayerHairFront.setScaleX(1.0f);
            ivLayerHairFront.setScaleY(1.0f);
            ivLayerHairFront.clearColorFilter();
            if (frontBitmap != null) {
                ivLayerHairFront.setImageBitmap(frontBitmap);
                ivLayerHairFront.setVisibility(View.VISIBLE);
            } else {
                ivLayerHairFront.setVisibility(View.GONE);
            }
        }

        // Hide Hair Accessory on character canvas (_c is strictly for grid thumbnail previews)
        if (ivLayerHairAcc != null) {
            ivLayerHairAcc.setVisibility(View.GONE);
        }
    }

    private int getSwatchColorInt(String colorName) {
        if ("black".equalsIgnoreCase(colorName)) return Color.parseColor("#1E1E1E");
        if ("brown".equalsIgnoreCase(colorName)) return Color.parseColor("#5C3A21");
        if ("darkred".equalsIgnoreCase(colorName)) return Color.parseColor("#C82828");
        if ("orange".equalsIgnoreCase(colorName)) return Color.parseColor("#FF7F50");
        return Color.parseColor("#FF8A8A"); // Peach / Flush default
    }

    private int getBlushColorInt(String colorName) {
        if ("darkred".equalsIgnoreCase(colorName)) return Color.parseColor("#C82828");
        if ("orange".equalsIgnoreCase(colorName)) return Color.parseColor("#FF8C00");
        return Color.parseColor("#FF8A8A"); // Peach / Rosy default
    }

    private Bitmap generateBlushBitmap(int colorInt, int styleIndex) {
        int widthPx = 110;
        int heightPx = 75;
        float featherFactor = 0.75f;
        int alpha = 180;

        if (styleIndex == 0) { // Matte / Sharp Edge
            featherFactor = 0.25f;
            alpha = 210;
        } else if (styleIndex == 1) { // Medium Soft
            featherFactor = 0.5f;
            alpha = 195;
        } else if (styleIndex == 2) { // Airbrush Soft
            featherFactor = 0.75f;
            alpha = 180;
        } else if (styleIndex == 3) { // Ultra-Soft Glow
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

    private void updateBlushLayers() {
        if (layoutCheeksContainer == null) return;

        if (selectedCheeksIndex == 0) {
            layoutCheeksContainer.setVisibility(View.GONE);
            return;
        }

        layoutCheeksContainer.setVisibility(View.VISIBLE);

        int colorInt = getBlushColorInt(selectedColorName);
        Bitmap blushBitmap = generateBlushBitmap(colorInt, selectedCheeksIndex - 1);

        if (ivLayerCheekLeft != null) {
            ivLayerCheekLeft.setImageBitmap(blushBitmap);
        }
        if (ivLayerCheekRight != null) {
            ivLayerCheekRight.setImageBitmap(blushBitmap);
        }
    }

    private ImageButton getTabButton(String category) {
        if ("Skin".equals(category) && tabSkin != null) return tabSkin;
        if ("Hair".equals(category)) return tabHair;
        if ("Eyes".equals(category)) return tabEyes;
        if ("Nose".equals(category)) return tabNose;
        if ("Mouth".equals(category)) return tabMouth;
        if ("Cheeks".equals(category) && tabCheeks != null) return tabCheeks;
        return tabHair;
    }

    private void switchTab(String category, ImageButton selectedTab) {
        SoundEffectsHelper.playHighlight(this);
        this.activeCategory = category;

        View sidebarColors = findViewById(R.id.sidebar_colors);
        View swatchBlack = findViewById(R.id.color_swatch_black);
        View swatchBrown = findViewById(R.id.color_swatch_brown);
        View swatchDarkRed = findViewById(R.id.color_swatch_darkred);
        View swatchOrange = findViewById(R.id.color_swatch_orange);
        View swatchPeach = findViewById(R.id.color_swatch_peach);
        View swatchEyePicker = findViewById(R.id.color_swatch_eye_picker);
        View swatchHairPicker = findViewById(R.id.color_swatch_hair_picker);

        if (sidebarColors != null) {
            boolean hasColors = "Nose".equals(category) || "Mouth".equals(category) || "Eyes".equals(category) || "Cheeks".equals(category) || "Hair".equals(category);
            sidebarColors.setVisibility(hasColors ? View.VISIBLE : View.GONE);

            if ("Eyes".equals(category)) {
                if (swatchBlack != null) swatchBlack.setVisibility(View.GONE);
                if (swatchBrown != null) swatchBrown.setVisibility(View.GONE);
                if (swatchDarkRed != null) swatchDarkRed.setVisibility(View.GONE);
                if (swatchOrange != null) swatchOrange.setVisibility(View.GONE);
                if (swatchPeach != null) swatchPeach.setVisibility(View.GONE);
                if (swatchHairPicker != null) swatchHairPicker.setVisibility(View.GONE);
                if (swatchEyePicker != null) swatchEyePicker.setVisibility(View.VISIBLE);
            } else if ("Hair".equals(category)) {
                if (swatchBlack != null) swatchBlack.setVisibility(View.GONE);
                if (swatchBrown != null) swatchBrown.setVisibility(View.GONE);
                if (swatchDarkRed != null) swatchDarkRed.setVisibility(View.GONE);
                if (swatchOrange != null) swatchOrange.setVisibility(View.GONE);
                if (swatchPeach != null) swatchPeach.setVisibility(View.GONE);
                if (swatchEyePicker != null) swatchEyePicker.setVisibility(View.GONE);
                if (swatchHairPicker != null) swatchHairPicker.setVisibility(View.VISIBLE);
            } else {
                if (swatchEyePicker != null) swatchEyePicker.setVisibility(View.GONE);
                if (swatchHairPicker != null) swatchHairPicker.setVisibility(View.GONE);
                if ("Nose".equals(category) || "Cheeks".equals(category)) {
                    if (swatchBlack != null) swatchBlack.setVisibility(View.GONE);
                    if (swatchBrown != null) swatchBrown.setVisibility(View.GONE);
                    if (swatchDarkRed != null) swatchDarkRed.setVisibility(View.VISIBLE);
                    if (swatchOrange != null) swatchOrange.setVisibility(View.VISIBLE);
                    if (swatchPeach != null) swatchPeach.setVisibility(View.VISIBLE);
                    if ("black".equals(selectedColorName) || "brown".equals(selectedColorName)) {
                        selectedColorName = "peach";
                    }
                } else if ("Mouth".equals(category)) {
                    if (swatchBlack != null) swatchBlack.setVisibility(View.VISIBLE);
                    if (swatchBrown != null) swatchBrown.setVisibility(View.VISIBLE);
                    if (swatchDarkRed != null) swatchDarkRed.setVisibility(View.VISIBLE);
                    if (swatchOrange != null) swatchOrange.setVisibility(View.VISIBLE);
                    if (swatchPeach != null) swatchPeach.setVisibility(View.VISIBLE);
                }
            }
        }

        tabHair.setSelected(false);
        tabEyes.setSelected(false);
        tabNose.setSelected(false);
        tabMouth.setSelected(false);
        if (tabCheeks != null) tabCheeks.setSelected(false);
        if (tabSkin != null) tabSkin.setSelected(false);

        if (selectedTab != null) selectedTab.setSelected(true);
        gridAssets.removeAllViews();

        if ("Hair".equals(category)) {
            java.util.List<String> availableHairstyles = new java.util.ArrayList<>();
            for (String baseNum : hairBases) {
                int aRes = getResources().getIdentifier("hairstyle_" + baseNum + "a", "drawable", getPackageName());
                int bRes = getResources().getIdentifier("hairstyle_" + baseNum + "b", "drawable", getPackageName());
                if (aRes != 0 || bRes != 0) {
                    availableHairstyles.add(baseNum);
                }
            }

            int totalCount = availableHairstyles.size() + 1;

            for (int i = 0; i < totalCount; i++) {
                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                if (i == 0) {
                    itemImage.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    itemImage.setColorFilter(Color.parseColor("#888888"));
                } else {
                    String baseNum = availableHairstyles.get(i - 1);
                    int cRes = getResources().getIdentifier("hairstyle_" + baseNum + "c", "drawable", getPackageName());
                    int bRes = getResources().getIdentifier("hairstyle_" + baseNum + "b", "drawable", getPackageName());
                    int aRes = getResources().getIdentifier("hairstyle_" + baseNum + "a", "drawable", getPackageName());
                    int previewRes = (cRes != 0) ? cRes : ((bRes != 0) ? bRes : aRes);
                    if (previewRes == 0) previewRes = R.drawable.hair_category_icon;

                    Bitmap tintedHair = getTintedEyeBitmap(previewRes, selectedHairColor);
                    itemImage.clearColorFilter();
                    if (tintedHair != null) itemImage.setImageBitmap(tintedHair);
                    else itemImage.setImageResource(previewRes);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    selectedHairIndex = finalIndex;
                    if (finalIndex > 0) {
                        selectedHairBase = availableHairstyles.get(finalIndex - 1);
                    }
                    applyAssetSelection(category, finalIndex);
                    ToastUtils.showToast(this, finalIndex == 0 ? "Hair Off" : ("Hairstyle #" + finalIndex + " Selected!"));
                });

                gridAssets.addView(itemImage);
            }
        } else if ("Eyes".equals(category)) {
            // Filter and render available eye asset options dynamically
            java.util.List<String> availableEyeBases = new java.util.ArrayList<>();
            for (String baseName : eyeBases) {
                String leftName = baseName + "_a";
                if ("ring_solid_lash_long".equals(baseName)) {
                    leftName = "ring_solid_lash_long_a_512x512";
                }
                int leftRes = getResources().getIdentifier(leftName, "drawable", getPackageName());
                if (leftRes != 0) {
                    availableEyeBases.add(baseName);
                }
            }

            for (int i = 0; i < availableEyeBases.size(); i++) {
                String eyeBase = availableEyeBases.get(i);
                String previewName = eyeBase + "_a";
                if ("ring_solid_lash_long".equals(eyeBase)) {
                    previewName = "ring_solid_lash_long_a_512x512";
                }

                int eyeRes = getResources().getIdentifier(previewName, "drawable", getPackageName());
                if (eyeRes == 0) eyeRes = R.drawable.eye_category_icon;

                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                Bitmap tintedEye = getTintedEyeBitmap(eyeRes, selectedEyeColor);
                itemImage.clearColorFilter();
                if (tintedEye != null) {
                    itemImage.setImageBitmap(tintedEye);
                } else {
                    itemImage.setImageResource(eyeRes);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    selectedEyeBase = eyeBase;
                    applyAssetSelection(category, finalIndex);
                    ToastUtils.showToast(this, "Eye Style #" + (finalIndex + 1) + " Selected!");
                });

                gridAssets.addView(itemImage);
            }
        } else if ("Nose".equals(category)) {
            // Render 3 nose shape choices (triangle_nose, square_nose, oblong_nose)
            int tintColor = getSwatchColorInt(selectedNoseColorName);
            for (int i = 0; i < noseShapes.length; i++) {
                String shapeName = noseShapes[i];
                int shapeRes = getResources().getIdentifier(shapeName, "drawable", getPackageName());
                if (shapeRes == 0) shapeRes = R.drawable.nose_category_icon;

                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                boolean isSmallNose = "square_nose".equals(shapeName);
                int paddingDp = isSmallNose ? 14 : 8;
                itemImage.setPadding(dpToPx(paddingDp), dpToPx(paddingDp), dpToPx(paddingDp), dpToPx(paddingDp));

                Bitmap tintedNose = getTintedEyeBitmap(shapeRes, tintColor);
                itemImage.clearColorFilter();
                if (tintedNose != null) {
                    itemImage.setImageBitmap(tintedNose);
                } else {
                    itemImage.setImageResource(shapeRes);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    selectedNoseShape = shapeName;
                    applyAssetSelection(category, finalIndex);
                    ToastUtils.showToast(this, "Nose Style #" + (finalIndex + 1) + " Selected!");
                });

                gridAssets.addView(itemImage);
            }
        } else if ("Mouth".equals(category)) {
            // Render 5 mouth choices (smile_01, smile_02, w_01, w_02, smile_bucktooth)
            int tintColor = getSwatchColorInt(selectedMouthColorName);
            for (int i = 0; i < mouthShapes.length; i++) {
                String mouthShapeName = mouthShapes[i];
                int mouthRes = getResources().getIdentifier(mouthShapeName, "drawable", getPackageName());
                if (mouthRes == 0) mouthRes = R.drawable.lips_category_icon;

                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                Bitmap tintedMouth = getTintedEyeBitmap(mouthRes, tintColor);
                itemImage.clearColorFilter();
                if (tintedMouth != null) {
                    itemImage.setImageBitmap(tintedMouth);
                } else {
                    itemImage.setImageResource(mouthRes);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    selectedMouthShape = mouthShapeName;
                    applyAssetSelection(category, finalIndex);
                    ToastUtils.showToast(this, "Mouth Style #" + (finalIndex + 1) + " Selected!");
                });

                gridAssets.addView(itemImage);
            }
        } else if ("Cheeks".equals(category)) {
            // Render 5 blush style choices (None, Soft Airbrush, Matte Oval, Cute Round, Sun-Kissed)
            int colorInt = getBlushColorInt(selectedColorName);
            for (int i = 0; i < 5; i++) {
                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                if (i == 0) {
                    itemImage.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    itemImage.setColorFilter(Color.parseColor("#888888"));
                } else {
                    Bitmap previewBlush = generateBlushBitmap(colorInt, i - 1);
                    itemImage.clearColorFilter();
                    itemImage.setImageBitmap(previewBlush);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    selectedCheeksIndex = finalIndex;
                    applyAssetSelection(category, finalIndex);
                    ToastUtils.showToast(this, finalIndex == 0 ? "Blush Off" : ("Blush Style #" + finalIndex + " Selected!"));
                });

                gridAssets.addView(itemImage);
            }
        } else {
            int count = "Skin".equals(category) ? 11 : 6;
            int iconRes = getCategoryIconRes(category);

            for (int i = 0; i < count; i++) {
                ImageView itemImage = new ImageView(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                        GridLayout.spec(i / 3),
                        GridLayout.spec(i % 3)
                );
                params.width = dpToPx(85);
                params.height = dpToPx(85);
                params.setMargins(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6));
                params.setGravity(android.view.Gravity.CENTER);
                itemImage.setLayoutParams(params);
                itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
                itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

                if ("Skin".equals(category)) {
                    int skinRes = getSkinDrawableRes(i);
                    itemImage.setImageResource(skinRes);
                    itemImage.clearColorFilter();
                } else {
                    itemImage.setImageResource(iconRes);
                }

                int finalIndex = i;
                itemImage.setOnClickListener(v -> {
                    applyAssetSelection(category, finalIndex);
                    String label = "Skin".equals(category) ? ("Skin Tone #" + (finalIndex + 1)) : (category + " #" + (finalIndex + 1));
                    ToastUtils.showToast(this, label + " Selected!");
                });

                gridAssets.addView(itemImage);
            }
        }
    }

    private int getShapeDrawableRes(String color, String shape) {
        String resName = color.toLowerCase() + "_" + shape.toLowerCase();
        int resId = getResources().getIdentifier(resName, "drawable", getPackageName());
        if (resId != 0) {
            return resId;
        }
        return R.drawable.nose_category_icon;
    }

    private int getSkinDrawableRes(int index) {
        int skinNum = index + 1;
        int resId = getResources().getIdentifier("skin_tone" + skinNum, "drawable", getPackageName());
        if (resId != 0) {
            return resId;
        }
        int fallbackId = getResources().getIdentifier("skin" + skinNum, "drawable", getPackageName());
        if (fallbackId != 0) {
            return fallbackId;
        }
        return R.drawable.sample_avatar;
    }

    private int getCategoryIconRes(String category) {
        if ("Hair".equals(category)) return R.drawable.hair_category_icon;
        if ("Eyes".equals(category)) return R.drawable.eye_category_icon;
        if ("Nose".equals(category)) return R.drawable.nose_category_icon;
        if ("Mouth".equals(category)) return R.drawable.lips_category_icon;
        if ("Cheeks".equals(category)) return R.drawable.blush_category_icon;
        if ("Skin".equals(category)) return R.drawable.skin_category_icon;
        return R.drawable.hair_category_icon;
    }

    private void applyAssetSelection(String category, int index) {
        if ("Hair".equals(category)) {
            selectedHairIndex = index;
            if (index > 0 && index <= hairBases.length) {
                selectedHairBase = hairBases[index - 1];
            }
            updateHairLayers();
        } else if ("Eyes".equals(category)) {
            selectedEyesIndex = index;
            if (index >= 0 && index < eyeBases.length) {
                selectedEyeBase = eyeBases[index];
            }
            updateEyeLayers();
        } else if ("Nose".equals(category)) {
            selectedNoseIndex = index;
            if (index >= 0 && index < noseShapes.length) {
                selectedNoseShape = noseShapes[index];
            }
            if (ivLayerNose != null) {
                int shapeRes = getResources().getIdentifier(selectedNoseShape, "drawable", getPackageName());
                if (shapeRes == 0) shapeRes = R.drawable.nose_category_icon;

                int tintColor = getSwatchColorInt(selectedNoseColorName);
                Bitmap tintedNose = getTintedEyeBitmap(shapeRes, tintColor);
                ivLayerNose.clearColorFilter();
                if (tintedNose != null) ivLayerNose.setImageBitmap(tintedNose);
                else ivLayerNose.setImageResource(shapeRes);

                boolean isSmallNose = "square_nose".equals(selectedNoseShape);
                int noseSizeDp = isSmallNose ? 34 : 50;

                android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) ivLayerNose.getLayoutParams();
                if (params != null) {
                    params.width = dpToPx(noseSizeDp);
                    params.height = dpToPx(noseSizeDp);
                    params.bottomMargin = dpToPx(48);
                    ivLayerNose.setLayoutParams(params);
                }

                ivLayerNose.setVisibility(View.VISIBLE);
            }
        } else if ("Mouth".equals(category)) {
            selectedMouthIndex = index;
            if (index >= 0 && index < mouthShapes.length) {
                selectedMouthShape = mouthShapes[index];
            }
            if (ivLayerMouth != null) {
                int mouthRes = getResources().getIdentifier(selectedMouthShape, "drawable", getPackageName());
                if (mouthRes == 0) mouthRes = R.drawable.lips_category_icon;

                int tintColor = getSwatchColorInt(selectedMouthColorName);
                Bitmap tintedMouth = getTintedEyeBitmap(mouthRes, tintColor);
                ivLayerMouth.clearColorFilter();
                if (tintedMouth != null) ivLayerMouth.setImageBitmap(tintedMouth);
                else ivLayerMouth.setImageResource(mouthRes);

                boolean isBucktooth = "smile_bucktooth".equals(selectedMouthShape);
                int mouthSizeDp = isBucktooth ? 84 : 60;
                int mouthBottomMarginDp = isBucktooth ? 28 : 24;

                android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) ivLayerMouth.getLayoutParams();
                if (params != null) {
                    params.width = dpToPx(mouthSizeDp);
                    params.height = dpToPx(mouthSizeDp);
                    params.bottomMargin = dpToPx(mouthBottomMarginDp);
                    ivLayerMouth.setLayoutParams(params);
                }

                ivLayerMouth.setVisibility(View.VISIBLE);
            }
        } else if ("Cheeks".equals(category)) {
            selectedCheeksIndex = index;
            updateBlushLayers();
        } else if ("Skin".equals(category)) {
            selectedSkinIndex = index;
            int skinRes = getSkinDrawableRes(index);
            ImageView ivLayerBody = findViewById(R.id.iv_layer_body);
            if (ivLayerBody != null) {
                ivLayerBody.setImageResource(skinRes);
                ivLayerBody.clearColorFilter();
            }
        }
    }

    private void saveCustomizationAndProceed() {
        View avatarSquareCanvas = findViewById(R.id.avatar_square_canvas);
        if (avatarSquareCanvas == null) {
            avatarSquareCanvas = findViewById(R.id.preview_container);
        }
        saveAvatarSnapshotImage(avatarSquareCanvas);

        AvatarConfig config = new AvatarConfig(
                selectedSkinIndex,
                selectedColorName,
                selectedNoseColorName,
                selectedMouthColorName,
                selectedNoseShape,
                selectedMouthShape,
                selectedEyeColor,
                selectedEyeBase,
                selectedCheeksIndex,
                selectedHairIndex,
                selectedHairColor,
                selectedHairBase,
                "tank_top"
        );
        config.saveToPreferences(this);
        AvatarCompositor.clearCache();

        SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
        prefs.edit().putInt("pref_onboarding_step", 2).apply();

        String uid = prefs.getString("user_uid", null);
        if (uid != null && !uid.isEmpty()) {
            try {
                com.google.firebase.firestore.FirebaseFirestore firestore = com.google.firebase.firestore.FirebaseFirestore.getInstance();
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                map.put("avatar_skin_index", selectedSkinIndex);
                map.put("avatar_color_name", selectedColorName);
                map.put("avatar_nose_shape", selectedNoseShape);
                map.put("avatar_mouth_shape", selectedMouthShape);
                map.put("avatar_eye_color", selectedEyeColor);
                map.put("avatar_eye_base", selectedEyeBase);
                map.put("avatar_cheeks_index", selectedCheeksIndex);
                map.put("avatar_hair_index", selectedHairIndex);
                map.put("avatar_hair_color", selectedHairColor);
                map.put("avatar_hair_base", selectedHairBase);
                firestore.collection("users").document(uid).set(map, com.google.firebase.firestore.SetOptions.merge());
            } catch (Exception ignored) {}
        }

        ToastUtils.showToast(this, "Character Customization Saved!");

        boolean isEditMode = getIntent().getBooleanExtra("extra_edit_mode", false);
        if (isEditMode) {
            finish();
        } else {
            Intent intent = new Intent(AvatarCreationActivity.this, ProfilingActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void saveAvatarSnapshotImage(View avatarSquareView) {
        if (avatarSquareView == null) return;

        try {
            int width = avatarSquareView.getWidth() > 0 ? avatarSquareView.getWidth() : dpToPx(320);
            int height = avatarSquareView.getHeight() > 0 ? avatarSquareView.getHeight() : dpToPx(320);

            Bitmap viewBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(viewBitmap);
            avatarSquareView.draw(canvas);

            // Scale to native 1080x1080 high-res PNG matching base skin tone artwork resolution
            Bitmap highResBitmap = Bitmap.createScaledBitmap(viewBitmap, 1080, 1080, true);

            java.io.File avatarFile = new java.io.File(getFilesDir(), "avatar_user_snapshot.png");
            java.io.FileOutputStream fos = new java.io.FileOutputStream(avatarFile);
            highResBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dpToPx((float) dp));
    }

    private float dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        return dp * density;
    }
}
