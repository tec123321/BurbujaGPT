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

    if "    private static final long TIMER_FINISHED_BLINK_MS = 450L;" not in text:
        text = replace_once(
            text,
            "    private static final long DOUBLE_TAP_TIMEOUT_MS = 380L;\n\n"
            "    private static final int TIMER_RUNNING_COLOR",
            "    private static final long DOUBLE_TAP_TIMEOUT_MS = 380L;\n"
            "    private static final long TIMER_FINISHED_BLINK_MS = 450L;\n\n"
            "    private static final int TIMER_RUNNING_COLOR",
            "intervalo de parpadeo",
        )

    text = replace_once(
        text,
        """    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateAllDisplays();
            if (hasRunningClock() && overlayRoot != null) {
                handler.postDelayed(this, 100L);
            }
        }
    };
""",
        """    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateAllDisplays();
            long nextDelayMs = nextDisplayDelayMs();
            if (nextDelayMs > 0L && overlayRoot != null) {
                handler.postDelayed(this, nextDelayMs);
            }
        }
    };
""",
        "bucle de actualización del parpadeo",
    )

    text = replace_once(
        text,
        """    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        updateAllDisplays();
        if (hasRunningClock() && overlayRoot != null) {
            handler.postDelayed(tickRunnable, 100L);
        }
    }

    private boolean hasRunningClock() {
        if (running) return true;
        for (TimerRuntime timer : timers.values()) {
            if (timer.running) return true;
        }
        return false;
    }

""",
        """    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        updateAllDisplays();
        long nextDelayMs = nextDisplayDelayMs();
        if (nextDelayMs > 0L && overlayRoot != null) {
            handler.postDelayed(tickRunnable, nextDelayMs);
        }
    }

    private long nextDisplayDelayMs() {
        if (hasRunningClock()) return 100L;
        if (hasFinishedTimer()) return TIMER_FINISHED_BLINK_MS;
        return -1L;
    }

    private boolean hasRunningClock() {
        if (running) return true;
        for (TimerRuntime timer : timers.values()) {
            if (timer.running) return true;
        }
        return false;
    }

    private boolean hasFinishedTimer() {
        if (!timersEnabled) return false;
        for (TimerRuntime timer : timers.values()) {
            if (timer.config.visible
                    && !timer.running
                    && timer.completionReported
                    && timer.remainingNow() <= 0L) {
                return true;
            }
        }
        return false;
    }

""",
        "programación eficiente y detección de temporizador finalizado",
    )

    text = replace_once(
        text,
        """        edgeTouchView.setIndicatorState(
                running,
                activeTimerCount() > 0,
                AppPrefs.intervalMarksEnabled(this),
""",
        """        boolean timerFinished = hasFinishedTimer();
        boolean finishBlinkOn = timerFinished
                && ((SystemClock.uptimeMillis() / TIMER_FINISHED_BLINK_MS) & 1L) == 0L;

        edgeTouchView.setIndicatorState(
                running,
                activeTimerCount() > 0,
                timerFinished,
                finishBlinkOn,
                AppPrefs.intervalMarksEnabled(this),
""",
        "estado de parpadeo enviado al borde",
    )

    text = replace_once(
        text,
        """        private boolean stopwatchRunning;
        private boolean timerRunning;
        private boolean marksEnabled;
""",
        """        private boolean stopwatchRunning;
        private boolean timerRunning;
        private boolean timerFinished;
        private boolean finishBlinkOn;
        private boolean marksEnabled;
""",
        "estado visual de finalización",
    )

    text = replace_once(
        text,
        """        void setIndicatorState(
                boolean isStopwatchRunning,
                boolean isTimerRunning,
                boolean enabled,
""",
        """        void setIndicatorState(
                boolean isStopwatchRunning,
                boolean isTimerRunning,
                boolean isTimerFinished,
                boolean isFinishBlinkOn,
                boolean enabled,
""",
        "parámetros del parpadeo",
    )

    text = replace_once(
        text,
        """            if (stopwatchRunning == isStopwatchRunning
                    && timerRunning == isTimerRunning
                    && marksEnabled == enabled
""",
        """            if (stopwatchRunning == isStopwatchRunning
                    && timerRunning == isTimerRunning
                    && timerFinished == isTimerFinished
                    && finishBlinkOn == isFinishBlinkOn
                    && marksEnabled == enabled
""",
        "comparación del estado de parpadeo",
    )

    text = replace_once(
        text,
        """            stopwatchRunning = isStopwatchRunning;
            timerRunning = isTimerRunning;
            marksEnabled = enabled;
""",
        """            stopwatchRunning = isStopwatchRunning;
            timerRunning = isTimerRunning;
            timerFinished = isTimerFinished;
            finishBlinkOn = isFinishBlinkOn;
            marksEnabled = enabled;
""",
        "actualización del estado de parpadeo",
    )

    text = replace_once(
        text,
        """            int currentBorderColor = timerRunning ? timerActiveColor : idleColor;
            boolean drawStateBorder = timerRunning || !stopwatchRunning;
            if (drawStateBorder) {
                strokePaint.setColor(currentBorderColor);
                strokePaint.setStrokeWidth(borderWidth);
                canvas.drawRoundRect(line, radius, radius, strokePaint);
            }
""",
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
        "borde parpadeante al finalizar el temporizador",
    )

    SERVICE.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
