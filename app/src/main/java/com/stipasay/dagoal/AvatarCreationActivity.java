package com.stipasay.dagoal;

import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AvatarCreationActivity extends AppCompatActivity {

    private Button btnSaveAvatar;
    private Button tabHair, tabEyes, tabNose, tabMouth;
    private GridLayout gridAssets;
    private DatabaseHelper dbHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_creation);

        dbHelper = new DatabaseHelper(this);

        btnSaveAvatar = findViewById(R.id.btn_save_avatar);
        tabHair = findViewById(R.id.tab_hair);
        tabEyes = findViewById(R.id.tab_eyes);
        tabNose = findViewById(R.id.tab_nose);
        tabMouth = findViewById(R.id.tab_mouth);
        gridAssets = findViewById(R.id.grid_assets);

        View rootLayout = findViewById(android.R.id.content);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });

        tabHair.setOnClickListener(v -> switchTab("Hair", tabHair));
        tabEyes.setOnClickListener(v -> switchTab("Eyes", tabEyes));
        tabNose.setOnClickListener(v -> switchTab("Nose", tabNose));
        tabMouth.setOnClickListener(v -> switchTab("Mouth", tabMouth));

        switchTab("Hair", tabHair);

        btnSaveAvatar.setOnClickListener(v -> {
            TaskManager taskManager = new TaskManager(AvatarCreationActivity.this);
            taskManager.resetDailyQuests();
            taskManager.generateDailyTasks();

            android.content.SharedPreferences prefs = getSharedPreferences("DaGoalPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("isFirstRun", false).apply();

            Toast.makeText(AvatarCreationActivity.this, "Character Created! Onboarding Complete.", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(AvatarCreationActivity.this, DashboardActivity.class);
            startActivity(intent);
            finish();
        });
    }

    private void switchTab(String category, Button selectedTab) {
        tabHair.setBackgroundColor(Color.WHITE);
        tabEyes.setBackgroundColor(Color.WHITE);
        tabNose.setBackgroundColor(Color.WHITE);
        tabMouth.setBackgroundColor(Color.WHITE);
        selectedTab.setBackgroundColor(Color.parseColor("#A3B19B"));

        gridAssets.removeAllViews();

        for (int i = 0; i < 6; i++) {
            ImageView itemImage = new ImageView(this);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = dpToPx(90);
            params.height = dpToPx(90);
            params.setMargins(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));
            itemImage.setLayoutParams(params);
            itemImage.setBackgroundColor(Color.WHITE);
            itemImage.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8));

            if (category.equals("Hair")) {
                itemImage.setImageResource(android.R.drawable.ic_menu_gallery);
            } else {
                itemImage.setImageResource(android.R.drawable.ic_menu_manage);
            }

            int finalItemIndex = i;
            itemImage.setOnClickListener(view -> {
                Toast.makeText(this, "Selected " + category + " Option #" + (finalItemIndex + 1), Toast.LENGTH_SHORT).show();
            });

            gridAssets.addView(itemImage);
        }
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }
}