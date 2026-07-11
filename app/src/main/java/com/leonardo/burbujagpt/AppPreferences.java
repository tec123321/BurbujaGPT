package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class AppPreferences {
    private static final String PREFS = "globo_gpt_native_state";
    private static final String KEY_LAST_ERROR = "last_error";
    private static final String KEY_AUTO_BUBBLES = "auto_bubbles";
    private static final String KEY_MANUAL_SEQUENCE = "manual_sequence";
    private static final String KEY_RECORD_IDS = "bubble_record_ids";
    private static final String RECORD_PREFIX = "bubble_record_";

    private AppPreferences() {
    }

    static void recordError(Context context, String phase, Throwable error) {
        String message = phase
                + "\nDispositivo: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " · Android " + android.os.Build.VERSION.RELEASE
                + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\n" + Log.getStackTraceString(error);
        recordMessage(context, message);
    }

    static void recordMessage(Context context, String message) {
        String safe = message == null ? "Error desconocido" : message;
        if (safe.length() > 3800) safe = safe.substring(0, 3800);
        prefs(context).edit().putString(KEY_LAST_ERROR, safe).apply();
    }

    static String getLastError(Context context) {
        return prefs(context).getString(KEY_LAST_ERROR, "");
    }

    static void clearLastError(Context context) {
        prefs(context).edit().remove(KEY_LAST_ERROR).apply();
    }

    static boolean isAutoBubblesEnabled(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_BUBBLES, false);
    }

    static void setAutoBubblesEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_BUBBLES, enabled).apply();
    }

    static int nextManualSequence(Context context) {
        SharedPreferences preferences = prefs(context);
        int next = preferences.getInt(KEY_MANUAL_SEQUENCE, 0) + 1;
        preferences.edit().putInt(KEY_MANUAL_SEQUENCE, next).apply();
        return next;
    }

    static BubbleRecord getRecord(Context context, String id) {
        if (id == null || id.isEmpty()) return null;
        String encoded = prefs(context).getString(RECORD_PREFIX + id, null);
        if (encoded == null) return null;
        try {
            return BubbleRecord.fromJson(new JSONObject(encoded));
        } catch (JSONException error) {
            recordError(context, "Registro de burbuja dañado", error);
            return null;
        }
    }

    static void saveRecord(Context context, BubbleRecord record) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_RECORD_IDS, Collections.emptySet()));
        ids.add(record.id);
        preferences.edit()
                .putStringSet(KEY_RECORD_IDS, ids)
                .putString(RECORD_PREFIX + record.id, record.toJson().toString())
                .apply();
    }

    static List<BubbleRecord> getRecords(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = preferences.getStringSet(KEY_RECORD_IDS, Collections.emptySet());
        List<BubbleRecord> records = new ArrayList<>();
        for (String id : ids) {
            BubbleRecord record = getRecord(context, id);
            if (record != null) records.add(record);
        }
        records.sort((left, right) -> Long.compare(right.updatedAt, left.updatedAt));
        return records;
    }

    static void removeRecord(Context context, String id) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_RECORD_IDS, Collections.emptySet()));
        ids.remove(id);
        preferences.edit()
                .putStringSet(KEY_RECORD_IDS, ids)
                .remove(RECORD_PREFIX + id)
                .apply();
    }

    static void clearRecords(Context context) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit();
        for (String id : preferences.getStringSet(KEY_RECORD_IDS, Collections.emptySet())) {
            editor.remove(RECORD_PREFIX + id);
        }
        editor.remove(KEY_RECORD_IDS).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
