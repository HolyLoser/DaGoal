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
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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
    private boolean isSaving = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppearanceHelper.applyPreferredNightMode(this);
        setContentView(R.layout.activity_app_selection);

        View rootLayout = findViewById(R.id.root_app_selection_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);
        containerAppChecklist = findViewById(R.id.container_app_checklist);
        btnContinue = findViewById(R.id.btn_app_selection_continue);
        btnBack = findViewById(R.id.btn_app_selection_back);
        if (btnBack != null) {
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> navigateBackToPreviousScreen());
        }

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBackToPreviousScreen();
            }
        });

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

        boolean hasExistingPreferences = !alreadyBlockedPackages.isEmpty();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(launcherIntent, 0);
        String ownPackageName = getPackageName();

        List<ResolveInfo> filteredApps = new ArrayList<>();
        Set<String> processedPackages = new HashSet<>();

        if (resolvedApps != null) {
            for (ResolveInfo resolveInfo : resolvedApps) {
                if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.applicationInfo == null) continue;
                String packageName = resolveInfo.activityInfo.packageName;
                if (processedPackages.contains(packageName)) continue;

                CharSequence labelSeq = resolveInfo.loadLabel(packageManager);
                String appName = labelSeq != null ? labelSeq.toString() : packageName;

                if (!isUserSelectableApp(resolveInfo.activityInfo.applicationInfo, appName, packageName)) {
                    continue;
                }

                processedPackages.add(packageName);
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
        containerAppChecklist.removeAllViews();

        for (ResolveInfo resolveInfo : filteredApps) {
            String appName = resolveInfo.loadLabel(packageManager).toString();
            String packageName = resolveInfo.activityInfo.packageName;
            Drawable icon = resolveInfo.loadIcon(packageManager);

            View row = inflater.inflate(R.layout.item_app_checkbox, containerAppChecklist, false);
            ImageView ivIcon = row.findViewById(R.id.iv_app_icon);
            TextView tvName = row.findViewById(R.id.tv_app_name);
            CheckBox cbSelected = row.findViewById(R.id.cb_app_selected);

            if (ivIcon != null) ivIcon.setImageDrawable(icon);
            if (tvName != null) tvName.setText(appName);

            boolean isPreselected = hasExistingPreferences ? alreadyBlockedPackages.contains(packageName) : isPopularTimeConsumingApp(packageName);
            if (cbSelected != null) {
                cbSelected.setChecked(isPreselected);
                cbSelected.setButtonTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.dagoal_primary_dark)));
            }

            row.setOnClickListener(v -> {
                if (cbSelected != null) {
                    cbSelected.setChecked(!cbSelected.isChecked());
                }
            });

            checkboxRefs.add(cbSelected);
            packageNameRefs.add(packageName);
            appNameRefs.add(appName);

            containerAppChecklist.addView(row);
        }
    }

    private boolean isUserSelectableApp(android.content.pm.ApplicationInfo appInfo, String appName, String pkgName) {
        if (appInfo == null || pkgName == null) return false;

        if ("com.stipasay.dagoal".equalsIgnoreCase(pkgName) || "DaGoal".equalsIgnoreCase(appName) || getPackageName().equalsIgnoreCase(pkgName)) {
            return false;
        }

        String lowerPkg = pkgName.toLowerCase(Locale.ROOT);
        String lowerName = appName != null ? appName.toLowerCase(Locale.ROOT) : "";

        if (lowerName.contains("calculator") || lowerName.contains("settings") || lowerName.contains("calendar")
                || lowerName.contains("theme") || lowerName.contains("assistant") || lowerName.contains("app vault")
                || lowerName.contains("clock") || lowerName.contains("compass") || lowerName.contains("gallery")
                || lowerName.contains("camera") || lowerName.contains("phone") || lowerName.contains("contacts")
                || lowerName.contains("sim toolkit") || lowerName.contains("system ui") || lowerName.contains("downloads")
                || lowerName.contains("file manager") || lowerName.contains("fm radio") || lowerName.contains("wallpaper")
                || lowerPkg.contains("android.settings") || lowerPkg.contains("android.calculator") || lowerPkg.contains("android.calendar")
                || lowerPkg.contains("miui.securitycenter") || lowerPkg.contains("miui.calculator") || lowerPkg.contains("miui.cleanmaster")
                || lowerPkg.contains("google.android.gms") || lowerPkg.contains("google.android.googlequicksearchbox")) {
            return false;
        }

        boolean isBrowser = lowerPkg.contains("chrome") || lowerPkg.contains("browser") || lowerPkg.contains("firefox")
                || lowerPkg.contains("opera") || lowerPkg.contains("brave") || lowerPkg.contains("edge")
                || lowerName.contains("chrome") || lowerName.contains("browser") || lowerName.contains("firefox");

        if (isBrowser) {
            return true;
        }

        boolean isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        return !isSystemApp && !isUpdatedSystemApp;
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

    private void navigateBackToPreviousScreen() {
        boolean fromSettings = getIntent().getBooleanExtra("FROM_SETTINGS", false);
        if (fromSettings) {
            finish();
        } else if (isTaskRoot()) {
            Intent intent = new Intent(AppSelectionActivity.this, ProfilingActivity.class);
            intent.putExtra("EXTRA_START_LAST_QUESTION", true);
            startActivity(intent);
            finish();
        } else {
            finish();
        }
    }

    private void saveSelectedAppsAndProceed() {
        if (isSaving) return;
        isSaving = true;
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
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }
}
