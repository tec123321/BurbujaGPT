from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SERVICE = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/StopwatchService.java"
MAIN = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/MainActivity.java"
PREFS = ROOT / "stopwatch/src/main/java/com/leonardo/edgestopwatch/AppPrefs.java"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old in text:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f"No se encontró el bloque para {label}")


def replace_between(text: str, start_marker: str, end_marker: str, replacement: str) -> str:
    start = text.find(start_marker)
    end = text.find(end_marker, start + len(start_marker))
    if start < 0 or end < 0:
        raise RuntimeError(f"No se encontró el bloque entre {start_marker!r} y {end_marker!r}")
    return text[:start] + replacement + text[end:]


def patch_service() -> None:
    text = SERVICE.read_text(encoding="utf-8")

    # La escala ahora reduce también el ancho real de toda la ventana flotante.
    text = text.replace("dp(AppPrefs.getPanelWidth(this))", "panelWidthPx()")
    panel_width_method = '''    private int panelWidthPx() {
        return Math.max(dp(120), scaledDp(AppPrefs.getPanelWidth(this)));
    }

'''
    if "    private int panelWidthPx() {" not in text:
        marker = "    private int edgeHitWidthPx() {"
        if marker not in text:
            raise RuntimeError("No se encontró edgeHitWidthPx")
        text = text.replace(marker, panel_width_method + marker, 1)

    # Separadores y filas más compactos, sin espacios negros fijos.
    text = replace_once(
        text,
        '''            dividerParams.leftMargin = scaledDp(10);
            dividerParams.rightMargin = scaledDp(10);
            dividerParams.topMargin = scaledDp(5);
            dividerParams.bottomMargin = scaledDp(5);
''',
        '''            dividerParams.leftMargin = scaledDp(6);
            dividerParams.rightMargin = scaledDp(6);
            dividerParams.topMargin = scaledDp(2);
            dividerParams.bottomMargin = scaledDp(2);
''',
        "márgenes de separadores",
    )
    text = replace_once(
        text,
        '''            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            timer.labelView = label;
''',
        '''            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setVisibility(timer.config.label.isEmpty() ? View.GONE : View.VISIBLE);
            timer.labelView = label;
''',
        "visibilidad del nombre del temporizador",
    )
    text = replace_once(
        text,
        '''            reset.setContentDescription("Reiniciar " + timer.config.label);
''',
        '''            reset.setContentDescription(timer.config.label.isEmpty()
                    ? "Reiniciar temporizador"
                    : "Reiniciar " + timer.config.label);
''',
        "descripción del reinicio",
    )

    compact_scale_method = '''    private void applyPanelScale() {
        if (expandedPanel == null || stopwatchRow == null || timeView == null || resetView == null) return;

        expandedPanel.setPadding(scaledDp(5), scaledDp(3), scaledDp(3), scaledDp(3));
        stopwatchRow.setPadding(scaledDp(1), 0, 0, 0);

        LinearLayout.LayoutParams timeParams = (LinearLayout.LayoutParams) timeView.getLayoutParams();
        timeParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        timeView.setLayoutParams(timeParams);
        timeView.setMinimumHeight(0);
        timeView.setPadding(scaledDp(2), scaledDp(1), scaledDp(2), scaledDp(1));
        timeView.setTextSize(AppPrefs.getTextSize(this) * uiScale());

        LinearLayout.LayoutParams resetParams = (LinearLayout.LayoutParams) resetView.getLayoutParams();
        resetParams.width = scaledDp(34);
        resetParams.height = scaledDp(38);
        resetView.setLayoutParams(resetParams);
        resetView.setTextSize(18f * uiScale());

        for (TimerRuntime timer : timers.values()) {
            if (timer.rowView == null) continue;
            timer.rowView.setPadding(scaledDp(3), scaledDp(1), 0, scaledDp(1));
            timer.rowView.setMinimumHeight(0);

            boolean showLabel = !timer.config.label.isEmpty();
            timer.labelView.setVisibility(showLabel ? View.VISIBLE : View.GONE);
            timer.labelView.setTextSize(10f * uiScale());
            timer.labelView.setPadding(scaledDp(2), showLabel ? scaledDp(1) : 0, scaledDp(2), 0);

            timer.valueView.setTextSize(Math.max(14f, AppPrefs.getTextSize(this) - 4f) * uiScale());
            LinearLayout.LayoutParams valueParams = (LinearLayout.LayoutParams) timer.valueView.getLayoutParams();
            valueParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            timer.valueView.setLayoutParams(valueParams);
            timer.valueView.setMinimumHeight(0);
            timer.valueView.setPadding(scaledDp(1), 0, scaledDp(1), 0);

            LinearLayout.LayoutParams timerResetParams = (LinearLayout.LayoutParams) timer.resetView.getLayoutParams();
            timerResetParams.width = scaledDp(34);
            timerResetParams.height = scaledDp(40);
            timer.resetView.setLayoutParams(timerResetParams);
            timer.resetView.setTextSize(17f * uiScale());
        }
    }

'''
    text = replace_between(
        text,
        "    private void applyPanelScale() {",
        "    private void applyTimerAppearance() {",
        compact_scale_method,
    )

    text = replace_once(
        text,
        '''                timer.labelView.setText(timer.running
                        ? timer.config.label + "  •  en marcha"
                        : timer.config.label);
''',
        '''                if (timer.config.label.isEmpty()) {
                    timer.labelView.setText("");
                    timer.labelView.setVisibility(View.GONE);
                } else {
                    timer.labelView.setVisibility(View.VISIBLE);
                    timer.labelView.setText(timer.running
                            ? timer.config.label + "  •  en marcha"
                            : timer.config.label);
                }
''',
        "actualización del nombre del temporizador",
    )

    text = text.replace(
        "int estimatedExpandedHeight = scaledDp(58 + visibleTimerCount() * 57);",
        "int estimatedExpandedHeight = scaledDp(42 + visibleTimerCount() * 40);",
    )

    # El borde se dibuja dentro del lienzo para que no se corte arriba ni abajo.
    text = replace_once(
        text,
        '''            float lineWidth = scaledDp(BASE_EDGE_LINE_WIDTH_DP);
            float left = drawLeft ? 0f : getWidth() - lineWidth;
            float right = left + lineWidth;
            RectF line = new RectF(left, 0f, right, getHeight());
            float radius = lineWidth / 2f;
''',
        '''            float lineWidth = scaledDp(BASE_EDGE_LINE_WIDTH_DP);
            float borderWidth = Math.max(2f, scaledDp(2));
            float inset = borderWidth / 2f;
            float left = drawLeft ? inset : getWidth() - lineWidth + inset;
            float right = drawLeft ? lineWidth - inset : getWidth() - inset;
            RectF line = new RectF(left, inset, right, Math.max(inset, getHeight() - inset));
            float radius = Math.max(0f, line.width() / 2f);
''',
        "geometría del borde contraído",
    )
    text = replace_once(
        text,
        "            strokePaint.setStrokeWidth(Math.max(2f, scaledDp(2)));\n",
        "            strokePaint.setStrokeWidth(borderWidth);\n",
        "grosor del borde contraído",
    )

    SERVICE.write_text(text, encoding="utf-8")


def patch_prefs() -> None:
    text = PREFS.read_text(encoding="utf-8")
    text = text.replace(
        "                if (label.isEmpty()) label = durationLabel(durationMs);\n",
        "",
    )
    PREFS.write_text(text, encoding="utf-8")


def patch_main() -> None:
    text = MAIN.read_text(encoding="utf-8")

    text = replace_once(
        text,
        '''            TextView name = text(config.label, 15, TEXT_PRIMARY);
''',
        '''            TextView name = text(config.label.isEmpty() ? "Sin nombre" : config.label, 15,
                    config.label.isEmpty() ? TEXT_MUTED : TEXT_PRIMARY);
''',
        "nombre en la lista de temporizadores",
    )

    editor_header_old = '''        LinearLayout body = dialogBody();
        EditText label = textField("Nombre (opcional)", existing == null ? "" : existing.label);
        body.addView(label, fullWidth());

        TextView durationTitle = text("Duración", 14, TEXT_SECONDARY);
'''
    editor_header_new = '''        LinearLayout body = dialogBody();
        boolean existingAutoName = existing != null
                && isAutomaticTimerLabel(existing.label, existing.durationMs);

        Switch showName = switchView("Mostrar nombre en el panel");
        showName.setChecked(existing == null || !existing.label.isEmpty());
        body.addView(showName, fullWidth());

        EditText label = textField(
                "Nombre personalizado (opcional)",
                existing == null ? "" : existing.label);
        label.setEnabled(showName.isChecked());
        label.setAlpha(showName.isChecked() ? 1f : 0.45f);
        showName.setOnCheckedChangeListener((buttonView, isChecked) -> {
            label.setEnabled(isChecked);
            label.setAlpha(isChecked ? 1f : 0.45f);
        });
        body.addView(label, fullWidthMargin(8));

        TextView durationTitle = text("Duración", 14, TEXT_SECONDARY);
'''
    text = replace_once(text, editor_header_old, editor_header_new, "selector de nombre")

    save_name_old = '''                String name = label.getText().toString().trim();
                if (name.isEmpty()) name = AppPrefs.durationLabel(totalMs);
                AppPrefs.TimerConfig updated = new AppPrefs.TimerConfig(
'''
    save_name_new = '''                String typedName = label.getText().toString().trim();
                String name;
                if (!showName.isChecked()) {
                    name = "";
                } else if (typedName.isEmpty()
                        || (existingAutoName
                        && existing != null
                        && typedName.equals(existing.label))) {
                    name = AppPrefs.durationLabel(totalMs);
                } else {
                    name = typedName;
                }
                AppPrefs.TimerConfig updated = new AppPrefs.TimerConfig(
'''
    text = replace_once(text, save_name_old, save_name_new, "guardado automático del nombre")

    helper = '''    private boolean isAutomaticTimerLabel(String label, long durationMs) {
        if (label == null || label.trim().isEmpty()) return false;
        String value = label.trim();
        if (value.equals(AppPrefs.durationLabel(durationMs))) return true;

        long totalSeconds = Math.max(1L, durationMs / 1_000L);
        if (totalSeconds % 60L == 0L && totalSeconds < 3_600L) {
            long minutes = totalSeconds / 60L;
            return value.equals(minutes + " minuto") || value.equals(minutes + " minutos");
        }
        return false;
    }

'''
    marker = "    private void showTimerEditor(AppPrefs.TimerConfig existing) {"
    if "    private boolean isAutomaticTimerLabel(" not in text:
        if marker not in text:
            raise RuntimeError("No se encontró showTimerEditor")
        text = text.replace(marker, helper + marker, 1)

    text = text.replace(
        '                .setMessage("Se eliminará “" + config.label + "” y su progreso guardado.")',
        '                .setMessage(config.label.isEmpty()\n                        ? "Se eliminará este temporizador y su progreso guardado."\n                        : "Se eliminará “" + config.label + "” y su progreso guardado.")',
    )

    MAIN.write_text(text, encoding="utf-8")


if __name__ == "__main__":
    patch_service()
    patch_prefs()
    patch_main()
