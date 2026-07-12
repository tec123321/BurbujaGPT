from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = text.find(start_marker)
    end = text.find(end_marker, start + len(start_marker))
    if start < 0 or end < 0:
        raise RuntimeError(f"No se encontró el bloque entre {start_marker!r} y {end_marker!r}")
    return text[:start] + replacement + text[end:]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f"No se encontró el bloque para {label}")


COMPACT_SCALE_METHOD = '''    private void applyPanelScale() {
        if (expandedPanel == null || stopwatchRow == null || timeView == null || resetView == null) return;

        expandedPanel.setPadding(dp(5), dp(3), dp(3), dp(3));
        stopwatchRow.setPadding(dp(1), 0, 0, 0);
        stopwatchRow.setMinimumHeight(0);

        LinearLayout.LayoutParams timeParams = (LinearLayout.LayoutParams) timeView.getLayoutParams();
        timeParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        timeView.setLayoutParams(timeParams);
        timeView.setMinHeight(0);
        timeView.setMinimumHeight(0);
        timeView.setPadding(dp(2), dp(1), dp(2), dp(1));
        timeView.setTextSize(AppPrefs.getTextSize(this) * uiScale());

        LinearLayout.LayoutParams resetParams = (LinearLayout.LayoutParams) resetView.getLayoutParams();
        resetParams.width = scaledDp(30);
        resetParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        resetView.setLayoutParams(resetParams);
        resetView.setMinHeight(0);
        resetView.setMinimumHeight(0);
        resetView.setPadding(0, dp(2), 0, dp(2));
        resetView.setTextSize(18f * uiScale());

        for (TimerRuntime timer : timers.values()) {
            if (timer.rowView == null) continue;

            timer.rowView.setPadding(dp(3), dp(1), 0, dp(1));
            timer.rowView.setMinimumHeight(0);

            boolean showLabel = !timer.config.label.isEmpty();
            timer.labelView.setVisibility(showLabel ? View.VISIBLE : View.GONE);
            timer.labelView.setTextSize(10f * uiScale());
            timer.labelView.setMinHeight(0);
            timer.labelView.setMinimumHeight(0);
            timer.labelView.setPadding(dp(2), showLabel ? dp(1) : 0, dp(2), 0);

            timer.valueView.setTextSize(Math.max(14f, AppPrefs.getTextSize(this) - 4f) * uiScale());
            LinearLayout.LayoutParams valueParams = (LinearLayout.LayoutParams) timer.valueView.getLayoutParams();
            valueParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            timer.valueView.setLayoutParams(valueParams);
            timer.valueView.setMinHeight(0);
            timer.valueView.setMinimumHeight(0);
            timer.valueView.setPadding(dp(1), 0, dp(1), 0);

            LinearLayout.LayoutParams timerResetParams = (LinearLayout.LayoutParams) timer.resetView.getLayoutParams();
            timerResetParams.width = scaledDp(30);
            timerResetParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            timer.resetView.setLayoutParams(timerResetParams);
            timer.resetView.setMinHeight(0);
            timer.resetView.setMinimumHeight(0);
            timer.resetView.setPadding(0, dp(2), 0, dp(2));
            timer.resetView.setTextSize(17f * uiScale());
        }
    }

'''


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")

    text = replace_between(
        text,
        "    private void applyPanelScale() {",
        "    private void applyTimerAppearance() {",
        COMPACT_SCALE_METHOD,
    )

    text = text.replace(
        "            dividerParams.leftMargin = scaledDp(6);\n"
        "            dividerParams.rightMargin = scaledDp(6);\n"
        "            dividerParams.topMargin = scaledDp(2);\n"
        "            dividerParams.bottomMargin = scaledDp(2);\n",
        "            dividerParams.leftMargin = dp(6);\n"
        "            dividerParams.rightMargin = dp(6);\n"
        "            dividerParams.topMargin = dp(2);\n"
        "            dividerParams.bottomMargin = dp(2);\n",
    )

    # El borde verde depende EXCLUSIVAMENTE de los temporizadores de cuenta regresiva.
    # El cronómetro principal no cambia el borde: permanece blanco.
    old_update = '''        edgeTouchView.setIndicatorState(
                running,
                AppPrefs.intervalMarksEnabled(this),
                completedSquares,
                currentSquareProgress,
                AppPrefs.getIntervalMarkWidth(this),
                AppPrefs.getIntervalMarkHeight(this));
'''
    new_update = '''        edgeTouchView.setIndicatorState(
                activeTimerCount() > 0,
                AppPrefs.intervalMarksEnabled(this),
                completedSquares,
                currentSquareProgress,
                AppPrefs.getIntervalMarkWidth(this),
                AppPrefs.getIntervalMarkHeight(this));
        edgeTouchView.invalidate();
'''
    text = replace_once(text, old_update, new_update, "estado exclusivo de temporizadores")

    old_colors = '''            if (stopwatchRunning) {
                linePaint.setColor(Color.rgb(20, 64, 49));
                strokePaint.setColor(Color.rgb(29, 104, 78));
            } else {
                linePaint.setColor(Color.rgb(18, 22, 27));
                strokePaint.setColor(Color.WHITE);
            }
'''
    new_colors = '''            if (stopwatchRunning) {
                linePaint.setColor(Color.rgb(12, 72, 52));
                strokePaint.setColor(Color.rgb(20, 132, 91));
            } else {
                linePaint.setColor(Color.rgb(18, 22, 27));
                strokePaint.setColor(Color.WHITE);
            }
'''
    text = replace_once(text, old_colors, new_colors, "color verde del temporizador activo")

    SERVICE.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
