package com.leonardo.edgestopwatch;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {
    static final int THEME_BLACK = 0;
    static final int THEME_DARK = 1;
    static final int THEME_LIGHT = 2;
    static final int THEME_BLUE = 3;

    private static final String FILE = "edge_stopwatch_prefs";
    private static final String KEY_TEXT_SIZE = "text_size_sp";
    private static final String KEY_OPACITY = "opacity_percent";
    private static final String KEY_THEME = "theme";
    private static final String KEY_TENTHS = "show_tenths";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int getTextSize(Context context) {
        return clamp(prefs(context).getInt(KEY_TEXT_SIZE, 24), 16, 36);
    }

    static void setTextSize(Context context, int value) {
        prefs(context).edit().putInt(KEY_TEXT_SIZE, clamp(value, 16, 36)).apply();
    }

    static int getOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_OPACITY, 90), 30, 100);
    }

    static void setOpacity(Context context, int value) {
        prefs(context).edit().putInt(KEY_OPACITY, clamp(value, 30, 100)).apply();
    }

    static int getTheme(Context context) {
        return clamp(prefs(context).getInt(KEY_THEME, THEME_BLACK), THEME_BLACK, THEME_BLUE);
    }

    static void setTheme(Context context, int value) {
        prefs(context).edit().putInt(KEY_THEME, clamp(value, THEME_BLACK, THEME_BLUE)).apply();
    }

    static boolean showTenths(Context context) {
        return prefs(context).getBoolean(KEY_TENTHS, true);
    }

    static void setShowTenths(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_TENTHS, value).apply();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
