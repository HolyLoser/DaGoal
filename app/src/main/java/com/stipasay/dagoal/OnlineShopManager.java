package com.stipasay.dagoal;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OnlineShopManager {

    private static final String TAG = "OnlineShopManager";

    public static boolean isNetworkAvailable(Context context) {
        if (context == null) return false;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network activeNetwork = cm.getActiveNetwork();
                if (activeNetwork == null) return false;
                android.net.NetworkCapabilities capabilities = cm.getNetworkCapabilities(activeNetwork);
                if (capabilities == null) return false;
                return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        && capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            } else {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                return activeNetwork != null && activeNetwork.isConnected();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking network connectivity", e);
        }
        return false;
    }

    public static List<ShopItem> getDynamicShopItems(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        String lastShopDate = prefs.getString("pref_last_shop_rotation_date", "");

        if (!todayDateStr.equals(lastShopDate)) {
            prefs.edit()
                    .putString("pref_last_shop_rotation_date", todayDateStr)
                    .putInt("pref_daily_shop_refresh_count", 0)
                    .putInt("pref_shop_rotation_seed", (int) System.currentTimeMillis())
                    .apply();

            if (isNetworkAvailable(context)) {
                syncOnlineShopRotation(context, todayDateStr);
            }
        }

        int seed = prefs.getInt("pref_shop_rotation_seed", 0);
        return generateRotatedCatalog(context, seed);
    }

    public static void forceShopRotationRefresh(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
        String todayDateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        int newSeed = (int) System.currentTimeMillis();

        prefs.edit()
                .putString("pref_last_shop_rotation_date", todayDateStr)
                .putInt("pref_shop_rotation_seed", newSeed)
                .apply();

        if (isNetworkAvailable(context)) {
            syncOnlineShopRotation(context, todayDateStr);
        }
    }

    private static void syncOnlineShopRotation(Context context, String dateStr) {
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("daily_shop_rotation").document(dateStr)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            Log.i(TAG, "Online shop rotation fetched from Firestore for date: " + dateStr);
                        } else {
                            SharedPreferences prefs = context.getSharedPreferences("DaGoalPrefs", Context.MODE_PRIVATE);
                            int seed = prefs.getInt("pref_shop_rotation_seed", 0);

                            Map<String, Object> docData = new HashMap<>();
                            docData.put("date", dateStr);
                            docData.put("seed", seed);
                            docData.put("created_at", new Date());

                            db.collection("daily_shop_rotation").document(dateStr)
                                    .set(docData)
                                    .addOnSuccessListener(aVoid -> Log.i(TAG, "Today's shop rotation published to Firestore!"))
                                    .addOnFailureListener(e -> Log.e(TAG, "Failed to publish rotation to Firestore", e));
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to connect to Firestore", e));
        } catch (Exception e) {
            Log.e(TAG, "Firestore initialization exception", e);
        }
    }

    public static void logChestClaimOnline(Context context, int tier, int bonusGold, int bonusXp, String consumableType) {
        if (!isNetworkAvailable(context)) return;
        try {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> logData = new HashMap<>();
            logData.put("tier", tier);
            logData.put("gold_awarded", bonusGold);
            logData.put("xp_awarded", bonusXp);
            logData.put("consumable_awarded", consumableType != null ? consumableType : "NONE");
            logData.put("claimed_at", new Date());

            db.collection("chest_claim_logs")
                    .add(logData)
                    .addOnSuccessListener(ref -> Log.i(TAG, "Chest claim logged to Firestore: " + ref.getId()))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to log chest claim to Firestore", e));
        } catch (Exception e) {
            Log.e(TAG, "Firestore chest claim log error", e);
        }
    }

    private static List<ShopItem> generateRotatedCatalog(Context context, int seed) {
        TaskManager tm = new TaskManager(context);
        List<ShopItem> allItems = tm.getShopItems();

        List<ShopItem> commons = new ArrayList<>();
        List<ShopItem> uncommons = new ArrayList<>();
        List<ShopItem> rares = new ArrayList<>();
        List<ShopItem> epics = new ArrayList<>();

        for (ShopItem item : allItems) {
            if ("COMMON".equalsIgnoreCase(item.getRarityTier())) commons.add(item);
            else if ("UNCOMMON".equalsIgnoreCase(item.getRarityTier())) uncommons.add(item);
            else if ("RARE".equalsIgnoreCase(item.getRarityTier())) rares.add(item);
            else if ("EPIC".equalsIgnoreCase(item.getRarityTier())) epics.add(item);
            else commons.add(item);
        }

        java.util.Random rnd = new java.util.Random(seed);
        Collections.shuffle(commons, rnd);
        Collections.shuffle(uncommons, rnd);
        Collections.shuffle(rares, rnd);
        Collections.shuffle(epics, rnd);

        List<ShopItem> catalog = new ArrayList<>();
        catalog.addAll(commons);
        catalog.addAll(uncommons);
        catalog.addAll(rares);
        catalog.addAll(epics);

        return catalog;
    }
}
