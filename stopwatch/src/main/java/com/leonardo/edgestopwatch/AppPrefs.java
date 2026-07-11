package com.leonardo.edgestopwatch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class AppPrefs {
    static final int THEME_BLACK = 0;
    static final int THEME_DARK = 1;
    static final int THEME_LIGHT = 2;
    static final int THEME_BLUE = 3;

    static final int MIN_PANEL_WIDTH_DP = 190;
    static final int MAX_PANEL_WIDTH_DP = 420;
    static final int MIN_UI_SCALE_PERCENT = 75;
    static final int MAX_UI_SCALE_PERCENT = 160;
    static final int MIN_INTERVAL_MINUTES = 1;
    static final int MAX_INTERVAL_MINUTES = 720;
    static final int MAX_TIMER_COUNT = 6;
    static final long MIN_TIMER_DURATION_MS = 1_000L;
    static final long MAX_TIMER_DURATION_MS = 99L * 60L * 60L * 1_000L
            + 59L * 60L * 1_000L
            + 59L * 1_000L;

    private static final String FILE = "edge_stopwatch_prefs";
    private static final String KEY_TEXT_SIZE = "text_size_sp";
    private static final String KEY_PANEL_WIDTH = "panel_width_dp";
    private static final String KEY_OPACITY = "opacity_percent";
    private static final String KEY_THEME = "theme";
    private static final String KEY_TENTHS = "show_tenths";
    private static final String KEY_UI_SCALE = "ui_scale_percent";
    private static final String KEY_INTERVAL_MARKS = "interval_marks_enabled";
    private static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    private static final String KEY_TIMERS_ENABLED = "timers_enabled";
    private static final String KEY_TIMER_CONFIGS = "timer_configs";

    private AppPrefs() {}

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int getTextSize(Context context) {
        return clamp(prefs(context).getInt(KEY_TEXT_SIZE, 24), 16, 40);
    }

    static void setTextSize(Context context, int value) {
        prefs(context).edit().putInt(KEY_TEXT_SIZE, clamp(value, 16, 40)).apply();
    }

    static int getPanelWidth(Context context) {
        return clamp(
                prefs(context).getInt(KEY_PANEL_WIDTH, 260),
                MIN_PANEL_WIDTH_DP,
                MAX_PANEL_WIDTH_DP);
    }

    static void setPanelWidth(Context context, int value) {
        prefs(context).edit().putInt(
                KEY_PANEL_WIDTH,
                clamp(value, MIN_PANEL_WIDTH_DP, MAX_PANEL_WIDTH_DP)).apply();
    }

    static int getOpacity(Context context) {
        return clamp(prefs(context).getInt(KEY_OPACITY, 94), 30, 100);
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

    static int getUiScale(Context context) {
        return clamp(
                prefs(context).getInt(KEY_UI_SCALE, 100),
                MIN_UI_SCALE_PERCENT,
                MAX_UI_SCALE_PERCENT);
    }

    static void setUiScale(Context context, int value) {
        prefs(context).edit().putInt(
                KEY_UI_SCALE,
                clamp(value, MIN_UI_SCALE_PERCENT, MAX_UI_SCALE_PERCENT)).apply();
    }

    static boolean intervalMarksEnabled(Context context) {
        return prefs(context).getBoolean(KEY_INTERVAL_MARKS, true);
    }

    static void setIntervalMarksEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_INTERVAL_MARKS, value).apply();
    }

    static int getIntervalMinutes(Context context) {
        return clamp(
                prefs(context).getInt(KEY_INTERVAL_MINUTES, 5),
                MIN_INTERVAL_MINUTES,
                MAX_INTERVAL_MINUTES);
    }

    static void setIntervalMinutes(Context context, int value) {
        prefs(context).edit().putInt(
                KEY_INTERVAL_MINUTES,
                clamp(value, MIN_INTERVAL_MINUTES, MAX_INTERVAL_MINUTES)).apply();
    }

    static boolean timersEnabled(Context context) {
        return prefs(context).getBoolean(KEY_TIMERS_ENABLED, false);
    }

    static void setTimersEnabled(Context context, boolean value) {
        prefs(context).edit().putBoolean(KEY_TIMERS_ENABLED, value).apply();
    }

    static List<TimerConfig> getTimerConfigs(Context context) {
        String raw = prefs(context).getString(KEY_TIMER_CONFIGS, null);
        if (raw == null || raw.trim().isEmpty()) {
            return defaultTimerConfigs();
        }

        List<TimerConfig> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;

                String id = item.optString("id", "").trim();
                String label = item.optString("label", "").trim();
                long durationMs = clamp(
                        item.optLong("duration_ms", 5L * 60L * 1_000L),
                        MIN_TIMER_DURATION_MS,
                        MAX_TIMER_DURATION_MS);
                boolean visible = item.optBoolean("visible", false);
                if (id.isEmpty()) continue;
                if (label.isEmpty()) label = durationLabel(durationMs);
                result.add(new TimerConfig(id, label, durationMs, visible));
            }
        } catch (JSONException ignored) {
            return defaultTimerConfigs();
        }
        return result;
    }

    static void setTimerConfigs(Context context, List<TimerConfig> configs) {
        JSONArray array = new JSONArray();
        for (TimerConfig config : configs) {
            if (config == null) continue;
            JSONObject item = new JSONObject();
            try {
                item.put("id", config.id);
                item.put("label", config.label);
                item.put("duration_ms", config.durationMs);
                item.put("visible", config.visible);
                array.put(item);
            } catch (JSONException ignored) {
            }
        }
        prefs(context).edit().putString(KEY_TIMER_CONFIGS, array.toString()).apply();
    }

    static String newTimerId() {
        return "custom-" + System.currentTimeMillis();
    }

    static String durationLabel(long durationMs) {
        long totalSeconds = Math.max(1L, durationMs / 1_000L);
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            if (minutes == 0L && seconds == 0L) return hours + (hours == 1L ? " hora" : " horas");
            return String.format(java.util.Locale.US, "%dh %02dm %02ds", hours, minutes, seconds);
        }
        if (seconds == 0L) return minutes + " min";
        if (minutes == 0L) return seconds + " s";
        return String.format(java.util.Locale.US, "%dm %02ds", minutes, seconds);
    }

    private static List<TimerConfig> defaultTimerConfigs() {
        List<TimerConfig> configs = new ArrayList<>();
        configs.add(new TimerConfig("preset-5", "5 minutos", 5L * 60L * 1_000L, true));
        configs.add(new TimerConfig("preset-10", "10 minutos", 10L * 60L * 1_000L, false));
        configs.add(new TimerConfig("preset-20", "20 minutos", 20L * 60L * 1_000L, false));
        return Collections.unmodifiableList(configs);
    }

    static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    static final class TimerConfig {
        final String id;
        final String label;
        final long durationMs;
        final boolean visible;

        TimerConfig(String id, String label, long durationMs, boolean visible) {
            this.id = id;
            this.label = label;
            this.durationMs = clamp(durationMs, MIN_TIMER_DURATION_MS, MAX_TIMER_DURATION_MS);
            this.visible = visible;
        }

        TimerConfig withVisible(boolean value) {
            return new TimerConfig(id, label, durationMs, value);
        }
    }
}
