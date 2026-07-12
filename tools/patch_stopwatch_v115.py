from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"
MAIN = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/MainActivity.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f"No se encontró el bloque para {label}")


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")

    old = '''            linePaint.setColor(Color.rgb(18, 22, 27));
            int currentBorderColor = timerRunning
                    ? timerActiveColor
                    : (stopwatchRunning ? stopwatchActiveColor : idleColor);
            strokePaint.setColor(currentBorderColor);
            strokePaint.setStrokeWidth(borderWidth);
            canvas.drawRoundRect(line, radius, radius, linePaint);
            canvas.drawRoundRect(line, radius, radius, strokePaint);
'''

    new = '''            // El cronómetro principal se representa mediante relleno, no mediante borde.
            // El borde queda reservado para reposo y para indicar temporizadores activos.
            linePaint.setColor(stopwatchRunning
                    ? stopwatchActiveColor
                    : Color.rgb(18, 22, 27));
            canvas.drawRoundRect(line, radius, radius, linePaint);

            int currentBorderColor = timerRunning ? timerActiveColor : idleColor;
            boolean drawStateBorder = timerRunning || !stopwatchRunning;
            if (drawStateBorder) {
                strokePaint.setColor(currentBorderColor);
                strokePaint.setStrokeWidth(borderWidth);
                canvas.drawRoundRect(line, radius, radius, strokePaint);
            }
'''

    text = replace_once(text, old, new, "relleno activo sin borde del cronómetro")
    SERVICE.write_text(text, encoding="utf-8")


def patch_main() -> None:
    text = MAIN.read_text(encoding="utf-8")
    text = text.replace(
        '"Borde con cronómetro activo",\n                AppPrefs.getEdgeStopwatchActiveColor(this),',
        '"Relleno con cronómetro activo",\n                AppPrefs.getEdgeStopwatchActiveColor(this),',
    )
    MAIN.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
    patch_main()
