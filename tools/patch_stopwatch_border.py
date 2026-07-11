from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"

OLD = '''            if (stopwatchRunning) {
                linePaint.setColor(Color.rgb(20, 64, 49));
                strokePaint.setColor(Color.rgb(29, 104, 78));
            } else {
                linePaint.setColor(Color.rgb(18, 22, 27));
                strokePaint.setColor(Color.rgb(108, 118, 129));
            }
            canvas.drawRoundRect(line, radius, radius, linePaint);
            strokePaint.setStrokeWidth(Math.max(1f, scaledDp(1)));
            canvas.drawRoundRect(line, radius, radius, strokePaint);
'''

NEW = '''            if (stopwatchRunning) {
                linePaint.setColor(Color.rgb(20, 64, 49));
                strokePaint.setColor(Color.rgb(29, 104, 78));
            } else {
                linePaint.setColor(Color.rgb(18, 22, 27));
                strokePaint.setColor(Color.WHITE);
            }
            canvas.drawRoundRect(line, radius, radius, linePaint);
            strokePaint.setStrokeWidth(Math.max(2f, scaledDp(2)));
            canvas.drawRoundRect(line, radius, radius, strokePaint);
'''

text = SERVICE.read_text(encoding="utf-8")
if OLD not in text:
    raise RuntimeError("No se encontró el bloque del borde contraído")
SERVICE.write_text(text.replace(OLD, NEW, 1), encoding="utf-8")
