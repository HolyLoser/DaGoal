package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AppSelectionActivity extends AppCompatActivity {

    private LinearLayout containerAppChecklist;
    private Button btnContinue;
    private View btnBack;
    private DatabaseHelper dbHelper;
    private final List<CheckBox> checkboxRefs = new ArrayList<>();
    private final List<String> packageNameRefs = new ArrayList<>();
    private final List<String> appNameRefs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_app_selection);

        dbHelper = new DatabaseHelper(this);
        containerAppChecklist = findViewById(R.id.container_app_checklist);
        btnContinue = findViewById(R.id.btn_app_selection_continue);
        btnBack = findViewById(R.id.btn_app_selection_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        android.widget.FrameLayout avatarContainer = findViewById(R.id.avatar_host_container);
        if (avatarContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarContainer);
        }

        loadInstalledApps();

        btnContinue.setOnClickListener(v -> saveSelectedAppsAndProceed());
    }

    private void loadInstalledApps() {
        PackageManager packageManager = getPackageManager();
        Set<String> alreadyBlockedPackages = new HashSet<>();
        SQLiteDatabase readDb = dbHelper.getReadableDatabase();
        Cursor blockedCursor = readDb.query(DatabaseContract.BlockedAppEntry.TABLE_NAME,
                new String[]{ DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME }, null, null, null, null, null);
        if (blockedCursor != null) {
            while (blockedCursor.moveToNext()) {
                alreadyBlockedPackages.add(blockedCursor.getString(0));
            }
            blockedCursor.close();
        }

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(launcherIntent, 0);
        String ownPackageName = getPackageName();

        List<ResolveInfo> filteredApps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolvedApps) {
            String packageName = resolveInfo.activityInfo.packageName;
            if (!packageName.equals(ownPackageName)) {
                filteredApps.add(resolveInfo);
            }
        }

        Collections.sort(filteredApps, new Comparator<ResolveInfo>() {
            @Override
            public int compare(ResolveInfo a, ResolveInfo b) {
                String nameA = a.loadLabel(packageManager).toString();
                String nameB = b.loadLabel(packageManager).toString();
                return nameA.compareToIgnoreCase(nameB);
            }
        });

        LayoutInflater inflater = LayoutInflater.from(this);

        for (ResolveInfo resolveInfo : filteredApps) {
            String appName = resolveInfo.loadLabel(packageManager).toString();
            String packageName = resolveInfo.activityInfo.packageName;
            Drawable icon = resolveInfo.loadIcon(packageManager);

            View row = inflater.inflate(R.layout.item_app_checkbox, containerAppChecklist, false);
            ImageView ivIcon = row.findViewById(R.id.iv_app_icon);
            TextView tvName = row.findViewById(R.id.tv_app_name);
            CheckBox cbSelected = row.findViewById(R.id.cb_app_selected);

            ivIcon.setImageDrawable(icon);
            tvName.setText(appName);

            boolean isPreselected = alreadyBlockedPackages.contains(packageName) || isPopularTimeConsumingApp(packageName);
            cbSelected.setChecked(isPreselected);

            checkboxRefs.add(cbSelected);
            packageNameRefs.add(packageName);
            appNameRefs.add(appName);

            containerAppChecklist.addView(row);
        }
    }

    private boolean isPopularTimeConsumingApp(String packageName) {
        if (packageName == null) return false;
        String pkg = packageName.toLowerCase(Locale.ROOT);
        return pkg.contains("facebook") || pkg.contains("instagram") || pkg.contains("tiktok")
                || pkg.contains("youtube") || pkg.contains("twitter") || pkg.contains("x.android")
                || pkg.contains("snapchat") || pkg.contains("reddit") || pkg.contains("pinterest")
                || pkg.contains("netflix") || pkg.contains("roblox") || pkg.contains("mobile.legends")
                || pkg.contains("genshin") || pkg.contains("twitch") || pkg.contains("discord")
                || pkg.contains("telegram") || pkg.contains("bilibili") || pkg.contains("wattpad")
                || pkg.contains("threads") || pkg.contains("shopee") || pkg.contains("lazada")
                || pkg.contains("hbo") || pkg.contains("disney") || pkg.contains("primevideo")
                || pkg.contains("hulu") || pkg.contains("webtoon") || pkg.contains("iqiyi");
    }

    private void saveSelectedAppsAndProceed() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete(DatabaseContract.BlockedAppEntry.TABLE_NAME, null, null);

        for (int i = 0; i < checkboxRefs.size(); i++) {
            if (checkboxRefs.get(i).isChecked()) {
                ContentValues values = new ContentValues();
                values.put(DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME, packageNameRefs.get(i));
                values.put(DatabaseContract.BlockedAppEntry.COLUMN_APP_NAME, appNameRefs.get(i));
                db.insert(DatabaseContract.BlockedAppEntry.TABLE_NAME, null, values);
            }
        }

        boolean fromSettings = getIntent().getBooleanExtra("FROM_SETTINGS", false);
        if (fromSettings) {
            finish();
        } else {
            getSharedPreferences("DaGoalPrefs", MODE_PRIVATE).edit()
                    .putBoolean("isOnboardingComplete", true)
                    .putBoolean("isFirstRun", false)
                    .apply();

            Intent intent = new Intent(AppSelectionActivity.this, DailyRevealActivity.class);
            startActivity(intent);
            finish();
        }
    }
}
