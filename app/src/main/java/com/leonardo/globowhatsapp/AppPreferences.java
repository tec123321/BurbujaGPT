package com.leonardo.globowhatsapp;

import android.content.Context;
import android.util.Log;

/** Conserva únicamente diagnósticos técnicos locales; nunca guarda mensajes de WhatsApp. */
final class AppPreferences {
    private static final String PREFS = "globo_whatsapp_native_state";
    private static final String KEY_LAST_ERROR = "last_error";

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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LAST_ERROR, safe)
                .apply();
    }

    static String getLastError(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST_ERROR, "");
    }

    static void clearLastError(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_LAST_ERROR)
                .apply();
    }
}
