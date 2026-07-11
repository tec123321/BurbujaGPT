package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

final class AppPreferences {
    static final String MODE_WEB = "web";
    static final String MODE_OFFICIAL = "official";
    static final String MODE_BROWSER = "browser";
    static final String MODE_NATIVE = "native";

    static final int PANEL_COMPACT = 0;
    static final int PANEL_LARGE = 1;
    static final int PANEL_FULL = 2;

    private static final String PREFS = "burbujagpt_settings";
    private static final String KEY_MODE = "tap_mode";
    private static final String KEY_BUBBLE_SIZE = "bubble_size_dp";
    private static final String KEY_BUBBLE_OPACITY = "bubble_opacity";
    private static final String KEY_PANEL_SIZE = "panel_size";
    private static final String KEY_NATIVE_ERROR = "native_last_error";
    private static final String KEY_NATIVE_FALLBACK = "native_fallback_required";

    private AppPreferences() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String getMode(Context context) {
        return prefs(context).getString(KEY_MODE, MODE_OFFICIAL);
    }

    static void setMode(Context context, String mode) {
        String safeMode;
        if (MODE_NATIVE.equals(mode)) safeMode = MODE_NATIVE;
        else if (MODE_OFFICIAL.equals(mode)) safeMode = MODE_OFFICIAL;
        else if (MODE_BROWSER.equals(mode)) safeMode = MODE_BROWSER;
        else safeMode = MODE_WEB;
        prefs(context).edit().putString(KEY_MODE, safeMode).apply();
    }

    static int getBubbleSize(Context context) {
        return clamp(prefs(context).getInt(KEY_BUBBLE_SIZE, 64), 48, 84);
    }

    static void setBubbleSize(Context context, int sizeDp) {
        prefs(context).edit().putInt(KEY_BUBBLE_SIZE, clamp(sizeDp, 48, 84)).apply();
    }

    static int getBubbleOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_BUBBLE_OPACITY, 92), 45, 100);
    }

    static void setBubbleOpacity(Context context, int opacity) {
        prefs(context).edit().putInt(KEY_BUBBLE_OPACITY, clamp(opacity, 45, 100)).apply();
    }

    static int getPanelSize(Context context) {
        return clamp(prefs(context).getInt(KEY_PANEL_SIZE, PANEL_LARGE), PANEL_COMPACT, PANEL_FULL);
    }

    static void setPanelSize(Context context, int panelSize) {
        prefs(context).edit().putInt(
                KEY_PANEL_SIZE,
                clamp(panelSize, PANEL_COMPACT, PANEL_FULL)
        ).apply();
    }

    static void recordNativeError(Context context, String phase, Throwable error) {
        String message = "Fase: " + phase
                + "\nDispositivo: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL
                + " · Android " + android.os.Build.VERSION.RELEASE + " (API " + android.os.Build.VERSION.SDK_INT + ")"
                + "\n" + Log.getStackTraceString(error);
        recordNativeMessage(context, message);
    }

    static void recordNativeMessage(Context context, String message) {
        String safeMessage = message == null ? "Error desconocido" : message;
        if (safeMessage.length() > 3800) safeMessage = safeMessage.substring(0, 3800);
        prefs(context).edit().putString(KEY_NATIVE_ERROR, safeMessage).commit();
    }

    static String getLastNativeError(Context context) {
        return prefs(context).getString(KEY_NATIVE_ERROR, "");
    }

    static void clearNativeError(Context context) {
        prefs(context).edit()
                .remove(KEY_NATIVE_ERROR)
                .putBoolean(KEY_NATIVE_FALLBACK, false)
                .apply();
    }

    static boolean isNativeFallbackRequired(Context context) {
        return prefs(context).getBoolean(KEY_NATIVE_FALLBACK, false);
    }

    static void setNativeFallbackRequired(Context context, boolean required) {
        prefs(context).edit().putBoolean(KEY_NATIVE_FALLBACK, required).commit();
    }

    static void recordUncaughtCrash(Context context, Throwable error) {
        String trace = Log.getStackTraceString(error);
        if (MODE_NATIVE.equals(getMode(context))
                && (trace.contains("NativeBubbleActivity")
                || trace.contains("ChatActivity")
                || trace.contains("BubbleService"))) {
            recordNativeMessage(context, "Fallo no controlado\n" + trace);
            setNativeFallbackRequired(context, true);
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }
}
