package com.leonardo.edgestopwatch;

import java.util.Locale;

final class TimeMath {
    private TimeMath() {}

    static int completedIntervals(long elapsedMs, int intervalMinutes) {
        if (elapsedMs <= 0L || intervalMinutes <= 0) return 0;
        long intervalMs = intervalMinutes * 60_000L;
        return (int) Math.min(Integer.MAX_VALUE, elapsedMs / intervalMs);
    }

    static String formatStopwatch(long elapsedMs, boolean showTenths) {
        long safeElapsed = Math.max(0L, elapsedMs);
        long totalSeconds = safeElapsed / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        long tenths = (safeElapsed / 100L) % 10L;

        if (hours > 0L) {
            return showTenths
                    ? String.format(Locale.US, "%02d:%02d:%02d.%d", hours, minutes, seconds, tenths)
                    : String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return showTenths
                ? String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
                : String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    static String formatCountdown(long remainingMs) {
        long safeRemaining = Math.max(0L, remainingMs);
        long totalSeconds = (safeRemaining + 999L) / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) {
            return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }
}
