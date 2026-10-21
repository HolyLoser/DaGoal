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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(android.graphics.Color.parseColor("#546B41"));
            getWindow().setNavigationBarColor(android.graphics.Color.parseColor("#546B41"));
        }

        View rootLayout = findViewById(R.id.root_app_selection_layout);
        if (rootLayout != null) {
            AppearanceHelper.setupEdgeToEdgeInsets(rootLayout);
        }

        dbHelper = new DatabaseHelper(this);
        containerAppChecklist = findViewById(R.id.container_app_checklist);
        btnContinue = findViewById(R.id.btn_app_selection_continue);
        boolean fromSettings = getIntent().getBooleanExtra("FROM_SETTINGS", false);

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fromSettings) {
                    finish();
                }
            }
        });

        android.widget.FrameLayout avatarContainer = findViewById(R.id.avatar_host_container);
        if (avatarContainer != null) {
            AvatarHelper.renderUserAvatar(this, avatarContainer);
        }

        loadInstalledApps();

        DebouncedOnClickListener.setSafeOnClickListener(btnContinue, v -> saveSelectedAppsAndProceed());
    }

    public static class AppItemInfo {
        public String appName;
        public String packageName;
        public Drawable icon;
        public boolean isPreselected;

        public AppItemInfo(String appName, String packageName, Drawable icon, boolean isPreselected) {
            this.appName = appName;
            this.packageName = packageName;
            this.icon = icon;
            this.isPreselected = isPreselected;
        }
    }

    private static List<AppItemInfo> cachedAppList = null;

    public static synchronized List<AppItemInfo> preloadInstalledApps(android.content.Context context) {
        if (cachedAppList != null && !cachedAppList.isEmpty()) {
            return cachedAppList;
        }

        PackageManager packageManager = context.getPackageManager();
        Set<String> alreadyBlockedPackages = new HashSet<>();
        try (DatabaseHelper dbHelper = new DatabaseHelper(context)) {
            SQLiteDatabase readDb = dbHelper.getReadableDatabase();
            Cursor blockedCursor = readDb.query(DatabaseContract.BlockedAppEntry.TABLE_NAME,
                    new String[]{ DatabaseContract.BlockedAppEntry.COLUMN_PACKAGE_NAME }, null, null, null, null, null);
            if (blockedCursor != null) {
                while (blockedCursor.moveToNext()) {
                    alreadyBlockedPackages.add(blockedCursor.getString(0));
                }
                blockedCursor.close();
            }
        } catch (Exception ignored) {}

        boolean hasExistingPreferences = !alreadyBlockedPackages.isEmpty();

        Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolvedApps = packageManager.queryIntentActivities(launcherIntent, 0);
        String ownPackageName = context.getPackageName();

        List<AppItemInfo> appList = new ArrayList<>();
        Set<String> processedPackages = new HashSet<>();

        if (resolvedApps != null) {
            for (ResolveInfo resolveInfo : resolvedApps) {
                if (resolveInfo.activityInfo == null || resolveInfo.activityInfo.applicationInfo == null) continue;
                String packageName = resolveInfo.activityInfo.packageName;
                if (processedPackages.contains(packageName)) continue;

                CharSequence labelSeq = resolveInfo.loadLabel(packageManager);
                String appName = labelSeq != null ? labelSeq.toString() : packageName;

                if (!isUserSelectableAppStatic(resolveInfo.activityInfo.applicationInfo, appName, packageName, ownPackageName)) {
                    continue;
                }

                processedPackages.add(packageName);
                Drawable icon = resolveInfo.loadIcon(packageManager);
                boolean isPreselected = hasExistingPreferences ? alreadyBlockedPackages.contains(packageName) : isPopularTimeConsumingAppStatic(packageName);

                appList.add(new AppItemInfo(appName, packageName, icon, isPreselected));
            }
        }

        Collections.sort(appList, (a, b) -> {
            if (a.isPreselected != b.isPreselected) {
                return a.isPreselected ? -1 : 1;
            }
            return a.appName.compareToIgnoreCase(b.appName);
        });

        cachedAppList = appList;
        return cachedAppList;
    }

    private static boolean isUserSelectableAppStatic(android.content.pm.ApplicationInfo appInfo, String appName, String pkgName, String ownPackageName) {
        if (appInfo == null || pkgName == null) return false;
        if ("com.stipasay.dagoal".equalsIgnoreCase(pkgName) || "DaGoal".equalsIgnoreCase(appName) || ownPackageName.equalsIgnoreCase(pkgName)) {
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

        if (isBrowser) return true;

        boolean isSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean isUpdatedSystemApp = (appInfo.flags & android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;

        return !isSystemApp && !isUpdatedSystemApp;
    }

    private static boolean isPopularTimeConsumingAppStatic(String packageName) {
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

    private void moveRowToSortedPosition(View rowView) {
        if (containerAppChecklist == null || rowView == null) return;

        int currentIndex = containerAppChecklist.indexOfChild(rowView);
        if (currentIndex < 0) return;

        int lastCheckedIndex = -1;
        int childCount = containerAppChecklist.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = containerAppChecklist.getChildAt(i);
            if (child == rowView) continue;
            CheckBox cb = child.findViewById(R.id.cb_app_selected);
            if (cb != null && cb.isChecked()) {
                lastCheckedIndex = i;
            }
        }

        int targetIndex = lastCheckedIndex + 1;
        if (currentIndex == targetIndex) return;

        containerAppChecklist.removeView(rowView);
        int newChildCount = containerAppChecklist.getChildCount();
        int safeInsertIndex = Math.min(Math.max(0, targetIndex), newChildCount);
        containerAppChecklist.addView(rowView, safeInsertIndex);
    }

    private void loadInstalledApps() {
        List<AppItemInfo> apps = preloadInstalledApps(this);
        LayoutInflater inflater = LayoutInflater.from(this);
        containerAppChecklist.removeAllViews();
        checkboxRefs.clear();
        packageNameRefs.clear();
        appNameRefs.clear();

        for (AppItemInfo app : apps) {
            View row = inflater.inflate(R.layout.item_app_checkbox, containerAppChecklist, false);
            ImageView ivIcon = row.findViewById(R.id.iv_app_icon);
            TextView tvName = row.findViewById(R.id.tv_app_name);
            CheckBox cbSelected = row.findViewById(R.id.cb_app_selected);

            if (ivIcon != null) ivIcon.setImageDrawable(app.icon);
            if (tvName != null) tvName.setText(app.appName);

            if (cbSelected != null) {
                cbSelected.setChecked(app.isPreselected);
                cbSelected.setButtonTintList(android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.dagoal_primary_dark)));
                cbSelected.setOnCheckedChangeListener((buttonView, isChecked) -> moveRowToSortedPosition(row));
            }

            DebouncedOnClickListener.setSafeOnClickListener(row, 150L, v -> {
                if (cbSelected != null) {
                    cbSelected.setChecked(!cbSelected.isChecked());
                }
            });

            checkboxRefs.add(cbSelected);
            packageNameRefs.add(app.packageName);
            appNameRefs.add(app.appName);

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
