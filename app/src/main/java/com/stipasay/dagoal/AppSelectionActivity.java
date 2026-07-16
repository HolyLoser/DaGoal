package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
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
import java.util.List;

public class AppSelectionActivity extends AppCompatActivity {

    private LinearLayout containerAppChecklist;
    private Button btnContinue;
    private DatabaseHelper dbHelper;
    private final List<CheckBox> checkboxRefs = new ArrayList<>();
    private final List<String> packageNameRefs = new ArrayList<>();
    private final List<String> appNameRefs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_selection);

        dbHelper = new DatabaseHelper(this);
        containerAppChecklist = findViewById(R.id.container_app_checklist);
        btnContinue = findViewById(R.id.btn_app_selection_continue);

        loadInstalledApps();

        btnContinue.setOnClickListener(v -> saveSelectedAppsAndProceed());
    }

    private void loadInstalledApps() {
        PackageManager packageManager = getPackageManager();
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

            checkboxRefs.add(cbSelected);
            packageNameRefs.add(packageName);
            appNameRefs.add(appName);

            containerAppChecklist.addView(row);
        }
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

        Intent intent = new Intent(AppSelectionActivity.this, DailyRevealActivity.class);
        startActivity(intent);
        finish();
    }
}