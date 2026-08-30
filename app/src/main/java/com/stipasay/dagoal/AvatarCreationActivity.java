package com.stipasay.dagoal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AvatarCreationActivity extends AppCompatActivity {

    private Button btnSaveAvatar;
    private ImageButton tabHair, tabEyes, tabNose, tabMouth, tabCheeks, tabSkin;
    private GridLayout gridAssets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_creation);

        btnSaveAvatar = findViewById(R.id.btn_save_avatar);
        tabHair = findViewById(R.id.tab_hair);
        tabEyes = findViewById(R.id.tab_eyes);
        tabNose = findViewById(R.id.tab_nose);
        tabMouth = findViewById(R.id.tab_mouth);
        tabCheeks = findViewById(R.id.tab_cheeks);
        tabSkin = findViewById(R.id.tab_skin);
        gridAssets = findViewById(R.id.grid_assets);

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

        tabHair.setOnClickListener(v -> switchTab("Hair", tabHair));
        tabEyes.setOnClickListener(v -> switchTab("Eyes", tabEyes));
        tabNose.setOnClickListener(v -> switchTab("Nose", tabNose));
        tabMouth.setOnClickListener(v -> switchTab("Mouth", tabMouth));
        if (tabCheeks != null) tabCheeks.setOnClickListener(v -> switchTab("Cheeks", tabCheeks));
        if (tabSkin != null) tabSkin.setOnClickListener(v -> switchTab("Skin", tabSkin));

        // Initial selection
        tabHair.setSelected(true);
        switchTab("Hair", tabHair);

        btnSaveAvatar.setOnClickListener(v -> {
            TaskManager taskManager = new TaskManager(AvatarCreationActivity.this);
            taskManager.resetDailyQuests();
            taskManager.generateDailyTasks();

            android.content.SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("isFirstRun", false).apply();

            ToastUtils.showToast(AvatarCreationActivity.this, "Character Created! Onboarding Complete.");

            Intent intent = new Intent(AvatarCreationActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void switchTab(String category, ImageButton selectedTab) {
        tabHair.setSelected(false);
        tabEyes.setSelected(false);
        tabNose.setSelected(false);
        tabMouth.setSelected(false);
        if (tabCheeks != null) tabCheeks.setSelected(false);
        if (tabSkin != null) tabSkin.setSelected(false);
        
        selectedTab.setSelected(true);

        gridAssets.removeAllViews();

        for (int i = 0; i < 6; i++) {
            ImageView itemImage = new ImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dpToPx(90);
            params.height = dpToPx(90);
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            itemImage.setLayoutParams(params);
            itemImage.setBackgroundResource(R.drawable.bg_avatar_asset_item);
            itemImage.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

            if (category.equals("Hair")) {
                itemImage.setImageResource(android.R.drawable.ic_menu_gallery);
            } else {
                itemImage.setImageResource(android.R.drawable.ic_menu_manage);
            }

            int finalItemIndex = i;
            itemImage.setOnClickListener(view -> {
                ToastUtils.showToast(this, "Selected " + category + " Option #" + (finalItemIndex + 1));
            });

            gridAssets.addView(itemImage);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}