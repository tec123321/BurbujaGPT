from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"
MAIN = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/MainActivity.java"

EDGE_UPDATE_METHOD = r'''    private void updateEdgeIndicator() {
        if (edgeTouchView == null) return;

        long elapsedMs = elapsedNow();
        int intervalMinutes = AppPrefs.getIntervalMinutes(this);
        long intervalMs = Math.max(1_000L, intervalMinutes * 60_000L);
        long intervalSeconds = Math.max(1L, intervalMs / 1_000L);
        long elapsedSeconds = Math.max(0L, elapsedMs / 1_000L);
        int completedSquares = (int) Math.min(
                Integer.MAX_VALUE,
                elapsedSeconds / intervalSeconds);
        float currentSquareProgress =
                (elapsedSeconds % intervalSeconds) / (float) intervalSeconds;

        edgeTouchView.setIndicatorState(
                running,
                AppPrefs.intervalMarksEnabled(this),
                completedSquares,
                currentSquareProgress,
                AppPrefs.getIntervalMarkWidth(this),
                AppPrefs.getIntervalMarkHeight(this));
    }

'''

EDGE_CLASS = r'''    private final class EdgeProgressView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint counterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint counterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean drawLeft;
        private boolean stopwatchRunning;
        private boolean marksEnabled;
        private int markCount;
        private int markWidthDp;
        private int markHeightDp;
        private float currentSquareProgress;

        EdgeProgressView(Context context) {
            super(context);
            linePaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            markPaint.setStyle(Paint.Style.FILL);
            counterPaint.setStyle(Paint.Style.FILL);
            counterTextPaint.setStyle(Paint.Style.FILL);
            counterTextPaint.setTextAlign(Paint.Align.CENTER);
            counterTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
        }

        void setSideLeft(boolean value) {
            if (drawLeft == value) return;
            drawLeft = value;
            invalidate();
        }

        void setIndicatorState(
                boolean isRunning,
                boolean enabled,
                int completedMarks,
                float inProgressReveal,
                int configuredMarkWidthDp,
                int configuredMarkHeightDp) {
            int safeCount = Math.max(0, completedMarks);
            float safeProgress = Math.max(0f, Math.min(1f, inProgressReveal));
            int safeWidth = AppPrefs.clamp(
                    configuredMarkWidthDp,
                    AppPrefs.MIN_INTERVAL_MARK_WIDTH_DP,
                    AppPrefs.MAX_INTERVAL_MARK_WIDTH_DP);
            int safeHeight = AppPrefs.clamp(
                    configuredMarkHeightDp,
                    AppPrefs.MIN_INTERVAL_MARK_HEIGHT_DP,
                    AppPrefs.MAX_INTERVAL_MARK_HEIGHT_DP);
            if (stopwatchRunning == isRunning
                    && marksEnabled == enabled
                    && markCount == safeCount
                    && Math.abs(currentSquareProgress - safeProgress) < 0.0001f
                    && markWidthDp == safeWidth
                    && markHeightDp == safeHeight) {
                return;
            }

            stopwatchRunning = isRunning;
            marksEnabled = enabled;
            markCount = safeCount;
            currentSquareProgress = safeProgress;
            markWidthDp = safeWidth;
            markHeightDp = safeHeight;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float lineWidth = scaledDp(BASE_EDGE_LINE_WIDTH_DP);
            float left = drawLeft ? 0f : getWidth() - lineWidth;
            float right = left + lineWidth;
            RectF line = new RectF(left, 0f, right, getHeight());
            float radius = lineWidth / 2f;

            if (stopwatchRunning) {
                linePaint.setColor(Color.rgb(20, 64, 49));
                strokePaint.setColor(Color.rgb(29, 104, 78));
            } else {
                linePaint.setColor(Color.rgb(18, 22, 27));
                strokePaint.setColor(Color.rgb(108, 118, 129));
            }
            canvas.drawRoundRect(line, radius, radius, linePaint);
            strokePaint.setStrokeWidth(Math.max(1f, scaledDp(1)));
            canvas.drawRoundRect(line, radius, radius, strokePaint);

            if (!marksEnabled || (markCount <= 0 && currentSquareProgress <= 0f)) return;

            float squareSide = Math.max(
                    1f,
                    Math.min(
                            line.width(),
                            Math.min(scaledDp(markWidthDp), scaledDp(markHeightDp))));
            float gap = Math.max(1f, scaledDp(2));
            float verticalPadding = Math.max(scaledDp(2), radius - squareSide / 2f);
            float usableHeight = Math.max(squareSide, line.height() - verticalPadding * 2f);
            int capacity = Math.max(
                    1,
                    (int) Math.floor((usableHeight + gap) / (squareSide + gap)));

            int completedBars = markCount / capacity;
            int visibleCompletedSquares = markCount % capacity;

            float centerX = line.centerX();
            float squareLeft = Math.max(line.left, centerX - squareSide / 2f);
            float squareRight = Math.min(line.right, centerX + squareSide / 2f);
            float stackBottom = line.bottom - verticalPadding;

            markPaint.setColor(Color.rgb(1, 5, 4));
            Path lineClip = new Path();
            lineClip.addRoundRect(line, radius, radius, Path.Direction.CW);
            int restoreTo = canvas.save();
            canvas.clipPath(lineClip);

            for (int i = 0; i < visibleCompletedSquares; i++) {
                float bottom = stackBottom - i * (squareSide + gap);
                float top = bottom - squareSide;
                canvas.drawRect(squareLeft, top, squareRight, bottom, markPaint);
            }

            if (currentSquareProgress > 0f && visibleCompletedSquares < capacity) {
                float bottom =
                        stackBottom - visibleCompletedSquares * (squareSide + gap);
                float top = bottom - squareSide * currentSquareProgress;
                if (top < bottom) {
                    canvas.drawRect(squareLeft, top, squareRight, bottom, markPaint);
                }
            }
            canvas.restoreToCount(restoreTo);

            if (completedBars > 0) {
                drawCompletedBarsCounter(canvas, line, completedBars);
            }
        }

        private void drawCompletedBarsCounter(Canvas canvas, RectF line, int completedBars) {
            float outsideSpace = Math.max(0f, getWidth() - line.width() - scaledDp(5));
            float badgeSize = Math.min(scaledDp(20), outsideSpace);
            if (badgeSize < scaledDp(14)) return;

            float badgeGap = scaledDp(3);
            float badgeLeft = drawLeft
                    ? line.right + badgeGap
                    : line.left - badgeGap - badgeSize;
            float badgeTop = line.bottom - badgeSize;
            RectF badge = new RectF(
                    badgeLeft,
                    badgeTop,
                    badgeLeft + badgeSize,
                    line.bottom);
            float badgeRadius = scaledDp(4);

            counterPaint.setColor(Color.rgb(14, 18, 23));
            canvas.drawRoundRect(badge, badgeRadius, badgeRadius, counterPaint);
            strokePaint.setColor(stopwatchRunning
                    ? Color.rgb(29, 104, 78)
                    : Color.rgb(108, 118, 129));
            strokePaint.setStrokeWidth(Math.max(1f, scaledDp(1)));
            canvas.drawRoundRect(badge, badgeRadius, badgeRadius, strokePaint);

            String counter = completedBars > 999 ? "999+" : String.valueOf(completedBars);
            float textSize = counter.length() <= 2 ? scaledDp(10) : scaledDp(7);
            counterTextPaint.setTextSize(textSize);
            counterTextPaint.setColor(Color.WHITE);
            Paint.FontMetrics metrics = counterTextPaint.getFontMetrics();
            float baseline = badge.centerY() - (metrics.ascent + metrics.descent) / 2f;
            canvas.drawText(counter, badge.centerX(), baseline, counterTextPaint);
        }
    }

'''


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = text.find(start_marker)
    end = text.find(end_marker, start + len(start_marker))
    if start < 0 or end < 0:
        raise RuntimeError(f"No se encontró el bloque entre {start_marker!r} y {end_marker!r}")
    return text[:start] + replacement + text[end:]


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")
    text = replace_between(
        text,
        "    private void updateEdgeIndicator() {",
        "    private void showTrashTarget(boolean active) {",
        EDGE_UPDATE_METHOD,
    )
    text = replace_between(
        text,
        "    private final class EdgeProgressView extends View {",
        "    private static final class TimerRuntime {",
        EDGE_CLASS,
    )
    SERVICE.write_text(text, encoding="utf-8")


def patch_main_activity() -> None:
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace(
        'Switch enabledSwitch = switchView("Crear marcas negras en la línea blanca");',
        'Switch enabledSwitch = switchView("Crear cuadrados negros progresivos");',
    )
    text = text.replace(
        'Switch enabledSwitch = switchView("Crear cuadrados negros en la barra blanca");',
        'Switch enabledSwitch = switchView("Crear cuadrados negros progresivos");',
    )
    text = text.replace(
        '"Cada intervalo completado añade una barra horizontal. Reiniciar el cronómetro también reinicia las marcas.",',
        '"Cada cuadrado se forma de abajo hacia arriba durante todo el intervalo configurado. Su altura avanza cada segundo hasta quedar completo.",',
    )
    text = text.replace(
        '"Cada intervalo completado añade un cuadrado con una animación de abajo hacia arriba. Al llenarse la barra, el contador inferior indica cuántas veces se completó.",',
        '"Cada cuadrado se forma de abajo hacia arriba durante todo el intervalo configurado. Su altura avanza cada segundo hasta quedar completo.",',
    )

    settings_start = "        intervalMarkWidthValue = settingLabel("
    settings_end = '        intervalValue = settingLabel("Intervalo actual", AppPrefs.getIntervalMinutes(this) + " min");'
    settings_block = r'''        int currentSquareSize = Math.min(
                AppPrefs.getIntervalMarkWidth(this),
                AppPrefs.getIntervalMarkHeight(this));
        intervalMarkWidthValue = settingLabel(
                "Tamaño de cada cuadrado",
                currentSquareSize + " dp");
        content.addView(intervalMarkWidthValue);
        SeekBar squareSizeBar = seekBar(
                AppPrefs.MAX_INTERVAL_MARK_WIDTH_DP - AppPrefs.MIN_INTERVAL_MARK_WIDTH_DP,
                currentSquareSize - AppPrefs.MIN_INTERVAL_MARK_WIDTH_DP);
        squareSizeBar.setOnSeekBarChangeListener(listener(progress -> {
            int value = AppPrefs.MIN_INTERVAL_MARK_WIDTH_DP + progress;
            AppPrefs.setIntervalMarkWidth(this, value);
            AppPrefs.setIntervalMarkHeight(this, value);
            intervalMarkWidthValue.setText(settingText("Tamaño de cada cuadrado", value + " dp"));
            refreshOverlay();
        }));
        content.addView(squareSizeBar, fullWidth());

        TextView dimensionsHelp = text(
                "Los cuadrados permanecen dentro de la barra lateral. Al completarse una barra, el número inferior cuenta las vueltas acumuladas.",
                13,
                TEXT_MUTED);
        dimensionsHelp.setPadding(0, dp(4), 0, dp(14));
        content.addView(dimensionsHelp);

'''
    if "        int currentSquareSize = Math.min(" not in text:
        text = replace_between(text, settings_start, settings_end, settings_block)
    else:
        text = text.replace(
            '"Los cuadrados permanecen dentro de la barra blanca. Cuando se completa, el número inferior cuenta las vueltas acumuladas.",',
            '"Los cuadrados permanecen dentro de la barra lateral. Al completarse una barra, el número inferior cuenta las vueltas acumuladas.",',
        )
    MAIN.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
    patch_main_activity()
