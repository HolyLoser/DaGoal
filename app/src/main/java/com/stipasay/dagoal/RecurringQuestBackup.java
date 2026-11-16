package com.stipasay.dagoal;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;

/** Account-owned schedule snapshots; absent legacy cloud fields never erase saved schedules. */
final class RecurringQuestBackup {
    static final String CLOUD_FIELD = "recurring_quest_schedules";
    private RecurringQuestBackup() {}
    private static SharedPreferences storage(Context context) {
        return context.getSharedPreferences("DaGoalScheduleBackups", Context.MODE_PRIVATE);
    }
    private static String today() { return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()); }

    static List<Map<String,Object>> exportSchedules(Context context) {
        List<Map<String,Object>> result = new ArrayList<>();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                DatabaseHelper.archiveRecurringQuestSchedules(db);
                try (Cursor cursor = db.rawQuery("SELECT * FROM recurring_quest_schedules ORDER BY _id", null)) {
                    while (cursor.moveToNext()) {
                        Map<String,Object> row = new HashMap<>();
                        for (int i=0;i<cursor.getColumnCount();i++) {
                            if (cursor.isNull(i)) row.put(cursor.getColumnName(i), null);
                            else if (cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER) row.put(cursor.getColumnName(i), cursor.getLong(i));
                            else row.put(cursor.getColumnName(i), cursor.getString(i));
                        }
                        try (Cursor receipt = db.rawQuery("SELECT r.awarded_gold,r.awarded_xp FROM quest_reward_receipts r JOIN daily_tasks t ON t._id=r.task_id WHERE t.recurring_schedule_id=? AND t.task_date=?",
                                new String[]{String.valueOf(row.get("_id")), today()})) {
                            if (receipt.moveToFirst()) {
                                row.put("_awarded_gold", receipt.getLong(0));
                                row.put("_awarded_xp", receipt.getLong(1));
                            }
                        }
                        result.add(row);
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        return result;
    }

    static synchronized void saveLocal(Context context, String uid, List<Map<String,Object>> rows, String revision) {
        if (uid==null || uid.isEmpty()) return;
        JSONObject state = new JSONObject();
        try (DatabaseHelper helper = new DatabaseHelper(context)) {
            SQLiteDatabase db=helper.getReadableDatabase();
            try (Cursor user=db.rawQuery("SELECT * FROM user WHERE _id=1",null)) {
                if(user.moveToFirst())state.put("user",new JSONObject(cursorRow(user)));
            }
            for(String table:new String[]{"inventory","inventory_consumables"}) {
                List<Map<String,Object>> saved=new ArrayList<>();
                try(Cursor cursor=db.rawQuery("SELECT * FROM "+table,null)){while(cursor.moveToNext())saved.add(cursorRow(cursor));}
                state.put(table,new JSONArray(saved));
            }
            JSONObject equipped=new JSONObject();SharedPreferences active=context.getSharedPreferences("DaGoalPrefs",Context.MODE_PRIVATE);
            for(String key:new String[]{"pref_equipped_clothes_id","pref_equipped_glasses_id","pref_equipped_hat_id","pref_equipped_item_id"})equipped.put(key,active.getInt(key,-1));
            state.put("equipped",equipped);state.put("clothes",active.getString("pref_avatar_clothes","tank_top"));
        } catch(org.json.JSONException error) { throw new IllegalStateException("Local backup could not be encoded",error); }
        if(!storage(context).edit().putString("rows:"+uid, new JSONArray(rows).toString())
                .putString("state:"+uid,state.toString()).putString("revision:"+uid, revision).putBoolean("dirty:"+uid, true).commit()) {
            throw new IllegalStateException("Local backup could not be saved");
        }
    }
    static synchronized void acknowledge(Context context, String uid, String revision) {
        SharedPreferences prefs = storage(context);
        if (revision.equals(prefs.getString("revision:"+uid,""))) prefs.edit().putBoolean("dirty:"+uid,false).apply();
    }
    static void preserveBeforeReset(Context context) {
        SharedPreferences prefs=context.getSharedPreferences("DaGoalPrefs",Context.MODE_PRIVATE);
        String uid=prefs.getString("user_uid","");
        if (!prefs.getBoolean("isGuestUser",false) && !uid.isEmpty()) {
            List<Map<String,Object>> rows=exportSchedules(context);
            boolean pending=prefs.contains(TaskManager.PENDING_PROFILE_SYNC) || hasPending(context,uid);
            String revision=prefs.getString(TaskManager.PENDING_PROFILE_SYNC,UUID.randomUUID().toString());
            saveLocal(context,uid,rows,revision);
            if(!pending) acknowledge(context,uid,revision);
        }
    }
    static void forget(Context context,String uid) {
        if(uid!=null) storage(context).edit().remove("rows:"+uid).remove("state:"+uid).remove("revision:"+uid).remove("dirty:"+uid).commit();
    }
    static boolean hasPending(Context context,String uid) { return storage(context).getBoolean("dirty:"+uid,false); }

    static boolean restoreAfterLogin(Object cloudRows, Context context, String uid) {
        try { return restoreAfterLoginInternal(cloudRows,context,uid); }
        catch(RuntimeException error) { android.util.Log.e("RecurringQuestBackup","Schedule restore could not be saved",error);return false; }
    }
    private static boolean restoreAfterLoginInternal(Object cloudRows, Context context, String uid) {
        if(uid==null || uid.isEmpty()) return false;
        SharedPreferences prefs=storage(context);
        Object rows=cloudRows;
        boolean local=prefs.getBoolean("dirty:"+uid,false) || !(cloudRows instanceof List);
        if(local && prefs.contains("rows:"+uid)) {
            try {
                JSONArray array=new JSONArray(prefs.getString("rows:"+uid,"[]"));
                List<Map<String,Object>> saved=new ArrayList<>();
                for(int i=0;i<array.length();i++) {
                    JSONObject obj=array.getJSONObject(i);Map<String,Object> row=new HashMap<>();
                    Iterator<String> keys=obj.keys();while(keys.hasNext()){String key=keys.next();Object value=obj.get(key);row.put(key,value==JSONObject.NULL?null:value);}saved.add(row);
                }
                rows=saved;
            } catch (org.json.JSONException error) { android.util.Log.w("RecurringQuestBackup","Saved schedule backup could not be read"); }
        }
        if(!(rows instanceof List)) return cloudRows==null;
        if(!restoreSchedules((List<?>)rows,context)) return false;
        if(local && prefs.getBoolean("dirty:"+uid,false) && prefs.contains("state:"+uid)) restoreLocalState(context,uid);
        return true;
    }

    private static Map<String,Object> cursorRow(Cursor cursor) {
        Map<String,Object> row=new HashMap<>();
        for(int i=0;i<cursor.getColumnCount();i++) {
            if(cursor.isNull(i))row.put(cursor.getColumnName(i),null);
            else if(cursor.getType(i)==Cursor.FIELD_TYPE_INTEGER)row.put(cursor.getColumnName(i),cursor.getLong(i));
            else row.put(cursor.getColumnName(i),cursor.getString(i));
        }
        return row;
    }
    private static ContentValues localValues(JSONObject json) throws org.json.JSONException {
        ContentValues values=new ContentValues();Iterator<String> keys=json.keys();
        while(keys.hasNext()){String key=keys.next();Object value=json.get(key);if(value==JSONObject.NULL)values.putNull(key);else if(value instanceof Number)values.put(key,((Number)value).longValue());else values.put(key,(String)value);}
        return values;
    }
    private static void restoreLocalState(Context context,String uid) {
        try(DatabaseHelper helper=new DatabaseHelper(context)) {
            JSONObject state=new JSONObject(storage(context).getString("state:"+uid,"{}"));
            SQLiteDatabase db=helper.getWritableDatabase();db.beginTransaction();
            try {
                if(state.has("user")){ContentValues values=localValues(state.getJSONObject("user"));values.remove("_id");db.update("user",values,"_id=1",null);}
                for(String table:new String[]{"inventory","inventory_consumables"}) {
                    if(!state.has(table))continue;JSONArray rows=state.getJSONArray(table);db.delete(table,null,null);
                    for(int i=0;i<rows.length();i++){ContentValues values=localValues(rows.getJSONObject(i));values.remove("_id");db.insertOrThrow(table,null,values);}
                }
                try(Cursor current=db.rawQuery("SELECT COUNT(*) FROM daily_tasks WHERE task_date=?",new String[]{today()})) {
                    if(current.moveToFirst() && current.getInt(0)>0)new TaskManager(context).fillRestoredDailySlots(db,today());
                }
                db.setTransactionSuccessful();
            } finally{db.endTransaction();}
            if(state.has("equipped")) {
                JSONObject equipped=state.getJSONObject("equipped");SharedPreferences.Editor editor=context.getSharedPreferences("DaGoalPrefs",Context.MODE_PRIVATE).edit();
                Iterator<String> keys=equipped.keys();while(keys.hasNext()){String key=keys.next();editor.putInt(key,equipped.getInt(key));}
                editor.putString("pref_avatar_clothes",state.optString("clothes","tank_top")).apply();
            }
        } catch(org.json.JSONException error){throw new IllegalStateException("Local backup could not be restored",error);}
    }

    static boolean restoreSchedules(List<?> rows, Context context) {
        try(DatabaseHelper helper=new DatabaseHelper(context)) {
            SQLiteDatabase db=helper.getWritableDatabase();
            Map<String,Boolean> columns=new HashMap<>();
            try(Cursor schema=db.rawQuery("PRAGMA table_info(recurring_quest_schedules)",null)) {
                while(schema.moveToNext())columns.put(schema.getString(1),schema.getString(2).contains("INT"));
            }
            List<ContentValues> validated=new ArrayList<>();Set<Long> ids=new HashSet<>();
            for(Object item:rows) {
                if(!(item instanceof Map))return false;
                Map<?,?> raw=(Map<?,?>)item;ContentValues values=new ContentValues();
                if(!(raw.get("_id") instanceof Number) || !(raw.get("repeat_interval") instanceof Number)
                        || ((Number)raw.get("_id")).longValue()<=0 || ((Number)raw.get("repeat_interval")).intValue()<=0
                        || !(raw.get("title") instanceof String) || !(raw.get("repeat_start_date") instanceof String))return false;
                long id=((Number)raw.get("_id")).longValue();if(!ids.add(id))return false;
                for(Map.Entry<String,Boolean> column:columns.entrySet()) {
                    Object value=raw.get(column.getKey());if(value==null)continue;
                    if(column.getValue()){if(!(value instanceof Number))return false;values.put(column.getKey(),((Number)value).longValue());}
                    else {if(!(value instanceof String))return false;values.put(column.getKey(),(String)value);}
                }
                values.put("is_custom",1);values.put("recurring_schedule_id",id);validated.add(values);
            }
            db.beginTransaction();
            try {
                db.execSQL("DELETE FROM quest_reward_receipts WHERE task_id IN (SELECT _id FROM daily_tasks WHERE recurring_schedule_id>0)");
                db.delete("daily_tasks","recurring_schedule_id>0",null);
                db.delete(DatabaseHelper.RECURRING_SCHEDULES_TABLE,null,null);
                int restoredToday=0;
                for(int i=0;i<validated.size();i++) {
                    ContentValues values=validated.get(i);
                    db.insertOrThrow(DatabaseHelper.RECURRING_SCHEDULES_TABLE,null,values);
                    if(today().equals(values.getAsString("task_date"))) {
                        ContentValues occurrence=new ContentValues(values);occurrence.remove("_id");
                        long taskId=db.insertOrThrow("daily_tasks",null,occurrence);restoredToday++;
                        Map<?,?> raw=(Map<?,?>)rows.get(i);
                        if(raw.get("_awarded_gold") instanceof Number && raw.get("_awarded_xp") instanceof Number) {
                            ContentValues receipt=new ContentValues();receipt.put("task_id",taskId);
                            receipt.put("awarded_gold",((Number)raw.get("_awarded_gold")).intValue());receipt.put("awarded_xp",((Number)raw.get("_awarded_xp")).intValue());
                            receipt.put("chest_point_awarded",0);receipt.put("chest_cycle",-1);
                            db.insertOrThrow(DatabaseHelper.REWARD_RECEIPTS_TABLE,null,receipt);
                        }
                    }
                }
                if(restoredToday>0)new TaskManager(context).fillRestoredDailySlots(db,today());
                db.setTransactionSuccessful();
            } finally {db.endTransaction();}
        }
        return true;
    }
}
