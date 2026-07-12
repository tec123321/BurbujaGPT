from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f"No se encontró el bloque para {label}")


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")

    text = replace_once(
        text,
        "    private static final long TIMER_FINISHED_BLINK_MS = 450L;\n",
        "    private static final long TIMER_FINISHED_BLINK_MS = 320L;\n"
        "    private static final int TIMER_FINISHED_ALERT_BRIGHT = Color.rgb(255, 24, 24);\n"
        "    private static final int TIMER_FINISHED_ALERT_DARK = Color.rgb(165, 0, 0);\n",
        "ritmo y colores de la alerta final",
    )

    text = replace_once(
        text,
        """        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
""",
        """        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint alertGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
""",
        "pincel del resplandor de alerta",
    )

    text = replace_once(
        text,
        """            linePaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStyle(Paint.Style.FILL);
""",
        """            linePaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            alertGlowPaint.setStyle(Paint.Style.STROKE);
            progressPaint.setStyle(Paint.Style.FILL);
""",
        "estilo del resplandor de alerta",
    )

    text = replace_once(
        text,
        """            int normalBorderColor = timerRunning ? timerActiveColor : idleColor;
            int currentBorderColor = timerFinished && finishBlinkOn
                    ? TIMER_FINISHED_COLOR
                    : normalBorderColor;
            boolean drawStateBorder = timerFinished || timerRunning || !stopwatchRunning;
            if (drawStateBorder) {
                strokePaint.setColor(currentBorderColor);
                strokePaint.setStrokeWidth(borderWidth);
                canvas.drawRoundRect(line, radius, radius, strokePaint);
            }
""",
        """            int normalBorderColor = timerRunning ? timerActiveColor : idleColor;
            int currentBorderColor = normalBorderColor;
            float currentBorderWidth = borderWidth;

            if (timerFinished) {
                currentBorderColor = finishBlinkOn
                        ? TIMER_FINISHED_ALERT_BRIGHT
                        : TIMER_FINISHED_ALERT_DARK;
                currentBorderWidth = Math.max(
                        borderWidth,
                        scaledDp(finishBlinkOn ? 4 : 3));

                if (finishBlinkOn) {
                    alertGlowPaint.setColor(Color.argb(145, 255, 24, 24));
                    alertGlowPaint.setStrokeWidth(Math.max(
                            currentBorderWidth + scaledDp(3),
                            scaledDp(7)));
                    canvas.drawRoundRect(line, radius, radius, alertGlowPaint);
                }
            }

            boolean drawStateBorder = timerFinished || timerRunning || !stopwatchRunning;
            if (drawStateBorder) {
                strokePaint.setColor(currentBorderColor);
                strokePaint.setStrokeWidth(currentBorderWidth);
                canvas.drawRoundRect(line, radius, radius, strokePaint);
            }
""",
        "borde rojo grueso y resplandor pulsante",
    )

    SERVICE.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()

