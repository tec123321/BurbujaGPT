package com.leonardo.edgestopwatch;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 4102;

    private static final int BACKGROUND = Color.rgb(4, 6, 8);
    private static final int SURFACE = Color.rgb(12, 16, 21);
    private static final int SURFACE_RAISED = Color.rgb(18, 24, 31);
    private static final int BORDER = Color.rgb(37, 48, 60);
    private static final int ACCENT = Color.rgb(45, 212, 191);
    private static final int ACCENT_DARK = Color.rgb(13, 94, 86);
    private static final int TEXT_PRIMARY = Color.rgb(248, 250, 252);
    private static final int TEXT_SECONDARY = Color.rgb(174, 184, 196);
    private static final int TEXT_MUTED = Color.rgb(111, 124, 138);
    private static final int DANGER = Color.rgb(248, 113, 113);

    private TextView permissionStatus;
    private TextView sizeValue;
    private TextView widthValue;
    private TextView opacityValue;
    private TextView scaleValue;
    private TextView intervalValue;
    private LinearLayout timersList;
    private final List<Button> intervalButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(Color.BLACK);
        setTitle("Cronómetro lateral");
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        LinearLayout root = vertical();
        root.setPadding(dp(16), dp(18), dp(16), dp(32));
        root.setBackgroundColor(BACKGROUND);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        root.addView(buildHeader(), cardParams(0));

        LinearLayout accessCard = card();
        permissionStatus = text("", 14, TEXT_SECONDARY);
        permissionStatus.setTypeface(Typeface.DEFAULT_BOLD);
        accessCard.addView(permissionStatus);
        accessCard.addView(spacer(12));

        Button permissionButton = button("Conceder permiso para mostrarse encima", false);
        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        accessCard.addView(permissionButton, fullWidth());

        Button showButton = button("Abrir panel flotante", true);
        showButton.setOnClickListener(v -> startStopwatch());
        accessCard.addView(showButton, fullWidthMargin(10));

        Button stopButton = button("Cerrar panel flotante", false);
        stopButton.setOnClickListener(v -> sendServiceAction(StopwatchService.ACTION_STOP));
        accessCard.addView(stopButton, fullWidthMargin(8));
        root.addView(accessCard, cardParams(12));

        addSectionTitle(root, "Apariencia");
        root.addView(buildAppearanceCard(), cardParams(0));

        addSectionTitle(root, "Marcas por intervalo");
        root.addView(buildIntervalCard(), cardParams(0));

        addSectionTitle(root, "Temporizadores");
        root.addView(buildTimersCard(), cardParams(0));

        TextView usage = text(
                "Toca un tiempo para iniciar o pausar. Desliza horizontalmente para contraer el panel y da dos toques sobre la línea para expandirlo. Arrástralo hasta la papelera para cerrarlo.",
                14,
                TEXT_SECONDARY);
        usage.setLineSpacing(0, 1.15f);
        root.addView(usage, topMargin(20));

        TextView privacy = text(
                "Sin anuncios, sin Internet y sin acceso a archivos, cámara, micrófono, contactos o ubicación.",
                13,
                TEXT_MUTED);
        privacy.setPadding(0, dp(10), 0, 0);
        root.addView(privacy);

        return scroll;
    }

    private View buildHeader() {
        LinearLayout header = card();
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(18), dp(16), dp(18));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher);
        logo.setContentDescription("Logo de Cronómetro lateral");
        header.addView(logo, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout copy = vertical();
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = dp(14);
        header.addView(copy, copyParams);

        TextView title = text("Cronómetro lateral", 25, TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        copy.addView(title);

        TextView subtitle = text(
                "Cronómetro, intervalos y varios temporizadores en un panel discreto.",
                14,
                TEXT_SECONDARY);
        subtitle.setPadding(0, dp(4), 0, 0);
        copy.addView(subtitle);
        return header;
    }

    private View buildAppearanceCard() {
        LinearLayout content = card();

        sizeValue = settingLabel("Tamaño del tiempo", AppPrefs.getTextSize(this) + " sp");
        content.addView(sizeValue);
        SeekBar sizeBar = seekBar(24, AppPrefs.getTextSize(this) - 16);
        sizeBar.setOnSeekBarChangeListener(listener(progress -> {
            int value = 16 + progress;
            AppPrefs.setTextSize(this, value);
            sizeValue.setText(settingText("Tamaño del tiempo", value + " sp"));
            refreshOverlay();
        }));
        content.addView(sizeBar, fullWidth());

        widthValue = settingLabel("Ancho del panel", AppPrefs.getPanelWidth(this) + " dp");
        content.addView(widthValue, topMargin(8));
        SeekBar widthBar = seekBar(
                AppPrefs.MAX_PANEL_WIDTH_DP - AppPrefs.MIN_PANEL_WIDTH_DP,
                AppPrefs.getPanelWidth(this) - AppPrefs.MIN_PANEL_WIDTH_DP);
        widthBar.setOnSeekBarChangeListener(listener(progress -> {
            int value = AppPrefs.MIN_PANEL_WIDTH_DP + progress;
            AppPrefs.setPanelWidth(this, value);
            widthValue.setText(settingText("Ancho del panel", value + " dp"));
            refreshOverlay();
        }));
        content.addView(widthBar, fullWidth());

        scaleValue = settingLabel("Escala de todo el flotante", AppPrefs.getUiScale(this) + "%");
        content.addView(scaleValue, topMargin(8));
        SeekBar scaleBar = seekBar(
                AppPrefs.MAX_UI_SCALE_PERCENT - AppPrefs.MIN_UI_SCALE_PERCENT,
                AppPrefs.getUiScale(this) - AppPrefs.MIN_UI_SCALE_PERCENT);
        scaleBar.setOnSeekBarChangeListener(listener(progress -> {
            int value = AppPrefs.MIN_UI_SCALE_PERCENT + progress;
            AppPrefs.setUiScale(this, value);
            scaleValue.setText(settingText("Escala de todo el flotante", value + "%"));
            refreshOverlay();
        }));
        content.addView(scaleBar, fullWidth());

        opacityValue = settingLabel("Opacidad", AppPrefs.getOpacity(this) + "%");
        content.addView(opacityValue, topMargin(8));
        SeekBar opacityBar = seekBar(70, AppPrefs.getOpacity(this) - 30);
        opacityBar.setOnSeekBarChangeListener(listener(progress -> {
            int value = 30 + progress;
            AppPrefs.setOpacity(this, value);
            opacityValue.setText(settingText("Opacidad", value + "%"));
            refreshOverlay();
        }));
        content.addView(opacityBar, fullWidth());

        TextView themeLabel = text("Color del panel", 15, TEXT_SECONDARY);
        themeLabel.setPadding(0, dp(10), 0, dp(4));
        content.addView(themeLabel);

        RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.setOrientation(RadioGroup.VERTICAL);
        int currentTheme = AppPrefs.getTheme(this);
        int[] themeValues = {
                AppPrefs.THEME_BLACK,
                AppPrefs.THEME_DARK,
                AppPrefs.THEME_LIGHT,
                AppPrefs.THEME_BLUE
        };
        String[] themeNames = {"Negro puro", "Grafito", "Claro", "Azul profundo"};
        for (int i = 0; i < themeValues.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(themeValues[i]);
            radio.setText(themeNames[i]);
            radio.setTextSize(15);
            radio.setTextColor(TEXT_PRIMARY);
            radio.setButtonTintList(checkTint());
            radio.setChecked(themeValues[i] == currentTheme);
            radio.setPadding(0, dp(2), 0, dp(2));
            themeGroup.addView(radio);
        }
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof Integer) {
                AppPrefs.setTheme(this, (Integer) checked.getTag());
                refreshOverlay();
            }
        });
        content.addView(themeGroup, fullWidth());

        Switch tenthsSwitch = switchView("Mostrar décimas en el cronómetro");
        tenthsSwitch.setChecked(AppPrefs.showTenths(this));
        tenthsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setShowTenths(this, isChecked);
            refreshOverlay();
        });
        content.addView(tenthsSwitch, topMargin(8));
        return content;
    }

    private View buildIntervalCard() {
        LinearLayout content = card();

        Switch enabledSwitch = switchView("Crear marcas negras en la línea blanca");
        enabledSwitch.setChecked(AppPrefs.intervalMarksEnabled(this));
        enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setIntervalMarksEnabled(this, isChecked);
            refreshOverlay();
        });
        content.addView(enabledSwitch);

        TextView help = text(
                "Cada intervalo completado añade una barra horizontal. Reiniciar el cronómetro también reinicia las marcas.",
                13,
                TEXT_MUTED);
        help.setPadding(0, dp(8), 0, dp(14));
        content.addView(help);

        intervalValue = settingLabel("Intervalo actual", AppPrefs.getIntervalMinutes(this) + " min");
        content.addView(intervalValue);

        LinearLayout choices = new LinearLayout(this);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        choices.setGravity(Gravity.CENTER_VERTICAL);
        int[] minutes = {5, 10, 20};
        for (int minute : minutes) {
            Button option = compactButton(minute + " min");
            option.setTag(minute);
            option.setOnClickListener(v -> setIntervalMinutes((Integer) v.getTag()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (choices.getChildCount() > 0) params.leftMargin = dp(8);
            choices.addView(option, params);
            intervalButtons.add(option);
        }
        content.addView(choices, fullWidthMargin(10));

        Button custom = button("Elegir otro número de minutos", false);
        custom.setOnClickListener(v -> showIntervalDialog());
        content.addView(custom, fullWidthMargin(8));
        updateIntervalButtons();
        return content;
    }

    private View buildTimersCard() {
        LinearLayout content = card();

        Switch master = switchView("Mostrar temporizadores en el panel");
        master.setChecked(AppPrefs.timersEnabled(this));
        master.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setTimersEnabled(this, isChecked);
            refreshOverlay();
        });
        content.addView(master);

        TextView help = text(
                "Activa uno o varios. En el panel flotante, toca cada cuenta para iniciarla o pausarla y usa ↺ para reiniciarla.",
                13,
                TEXT_MUTED);
        help.setPadding(0, dp(8), 0, dp(12));
        content.addView(help);

        timersList = vertical();
        content.addView(timersList, fullWidth());
        renderTimerRows();

        Button addTimer = button("+ Añadir temporizador personalizado", false);
        addTimer.setOnClickListener(v -> {
            if (AppPrefs.getTimerConfigs(this).size() >= AppPrefs.MAX_TIMER_COUNT) {
                Toast.makeText(this, "Puedes configurar hasta 6 temporizadores", Toast.LENGTH_SHORT).show();
                return;
            }
            showTimerEditor(null);
        });
        content.addView(addTimer, fullWidthMargin(12));
        return content;
    }

    private void renderTimerRows() {
        if (timersList == null) return;
        timersList.removeAllViews();
        List<AppPrefs.TimerConfig> configs = AppPrefs.getTimerConfigs(this);
        if (configs.isEmpty()) {
            TextView empty = text("No hay temporizadores configurados.", 14, TEXT_MUTED);
            empty.setPadding(0, dp(6), 0, dp(6));
            timersList.addView(empty);
            return;
        }

        for (AppPrefs.TimerConfig config : configs) {
            LinearLayout row = vertical();
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            row.setBackground(rounded(SURFACE_RAISED, 12, BORDER, 1));

            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(top, fullWidth());

            LinearLayout labels = vertical();
            top.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView name = text(config.label, 15, TEXT_PRIMARY);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            labels.addView(name);
            TextView duration = text(AppPrefs.durationLabel(config.durationMs), 13, TEXT_MUTED);
            duration.setPadding(0, dp(2), 0, 0);
            labels.addView(duration);

            Switch visible = switchView("Mostrar");
            visible.setTextSize(13);
            visible.setChecked(config.visible);
            visible.setOnCheckedChangeListener((buttonView, isChecked) -> {
                updateTimerVisibility(config.id, isChecked);
                refreshOverlay();
            });
            top.addView(visible, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.setGravity(Gravity.END);
            row.addView(actions, fullWidthMargin(8));

            Button edit = textButton("Editar", ACCENT);
            edit.setOnClickListener(v -> showTimerEditor(config));
            actions.addView(edit, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(38)));

            Button delete = textButton("Eliminar", DANGER);
            delete.setOnClickListener(v -> confirmDeleteTimer(config));
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    dp(38));
            deleteParams.leftMargin = dp(6);
            actions.addView(delete, deleteParams);

            timersList.addView(row, fullWidthMargin(timersList.getChildCount() == 0 ? 0 : 8));
        }
    }

    private void setIntervalMinutes(int minutes) {
        AppPrefs.setIntervalMinutes(this, minutes);
        intervalValue.setText(settingText("Intervalo actual", minutes + " min"));
        updateIntervalButtons();
        refreshOverlay();
    }

    private void updateIntervalButtons() {
        int selected = AppPrefs.getIntervalMinutes(this);
        for (Button button : intervalButtons) {
            boolean active = button.getTag() instanceof Integer && (Integer) button.getTag() == selected;
            button.setTextColor(active ? Color.rgb(2, 20, 18) : TEXT_PRIMARY);
            button.setBackground(rounded(active ? ACCENT : SURFACE_RAISED, 10, active ? ACCENT : BORDER, 1));
        }
    }

    private void showIntervalDialog() {
        EditText input = numericField("Minutos", String.valueOf(AppPrefs.getIntervalMinutes(this)));
        input.setSelectAllOnFocus(true);
        LinearLayout body = dialogBody();
        body.addView(input, fullWidth());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Intervalo personalizado")
                .setMessage("Elige un valor entre 1 y 720 minutos.")
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int value = parseNumber(input);
                if (value < AppPrefs.MIN_INTERVAL_MINUTES || value > AppPrefs.MAX_INTERVAL_MINUTES) {
                    Toast.makeText(this, "Escribe un número entre 1 y 720", Toast.LENGTH_SHORT).show();
                    return;
                }
                setIntervalMinutes(value);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void showTimerEditor(AppPrefs.TimerConfig existing) {
        long durationMs = existing == null ? 5L * 60L * 1_000L : existing.durationMs;
        long totalSeconds = durationMs / 1_000L;
        long hours = totalSeconds / 3_600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;

        LinearLayout body = dialogBody();
        EditText label = textField("Nombre (opcional)", existing == null ? "" : existing.label);
        body.addView(label, fullWidth());

        TextView durationTitle = text("Duración", 14, TEXT_SECONDARY);
        durationTitle.setPadding(0, dp(14), 0, dp(6));
        body.addView(durationTitle);

        LinearLayout fields = new LinearLayout(this);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        EditText hoursInput = numericField("Horas", String.valueOf(hours));
        EditText minutesInput = numericField("Min", String.valueOf(minutes));
        EditText secondsInput = numericField("Seg", String.valueOf(seconds));
        fields.addView(hoursInput, weightedField(0));
        fields.addView(minutesInput, weightedField(8));
        fields.addView(secondsInput, weightedField(8));
        body.addView(fields, fullWidth());

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Nuevo temporizador" : "Editar temporizador")
                .setView(body)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            tintDialogButtons(dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int hoursValue = parseNumber(hoursInput);
                int minutesValue = parseNumber(minutesInput);
                int secondsValue = parseNumber(secondsInput);
                if (hoursValue < 0 || hoursValue > 99
                        || minutesValue < 0 || minutesValue > 59
                        || secondsValue < 0 || secondsValue > 59) {
                    Toast.makeText(this, "Usa 0–99 horas y 0–59 minutos/segundos", Toast.LENGTH_SHORT).show();
                    return;
                }

                long totalMs = (hoursValue * 3_600L + minutesValue * 60L + secondsValue) * 1_000L;
                if (totalMs < AppPrefs.MIN_TIMER_DURATION_MS
                        || totalMs > AppPrefs.MAX_TIMER_DURATION_MS) {
                    Toast.makeText(this, "La duración debe ser mayor que cero", Toast.LENGTH_SHORT).show();
                    return;
                }

                String name = label.getText().toString().trim();
                if (name.isEmpty()) name = AppPrefs.durationLabel(totalMs);
                AppPrefs.TimerConfig updated = new AppPrefs.TimerConfig(
                        existing == null ? AppPrefs.newTimerId() : existing.id,
                        name,
                        totalMs,
                        existing == null || existing.visible);
                upsertTimer(updated);
                renderTimerRows();
                refreshOverlay();
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void confirmDeleteTimer(AppPrefs.TimerConfig config) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Eliminar temporizador")
                .setMessage("Se eliminará “" + config.label + "” y su progreso guardado.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Eliminar", (ignored, which) -> {
                    List<AppPrefs.TimerConfig> configs = new ArrayList<>(AppPrefs.getTimerConfigs(this));
                    for (int i = configs.size() - 1; i >= 0; i--) {
                        if (configs.get(i).id.equals(config.id)) configs.remove(i);
                    }
                    AppPrefs.setTimerConfigs(this, configs);
                    renderTimerRows();
                    refreshOverlay();
                })
                .create();
        dialog.setOnShowListener(ignored -> tintDialogButtons(dialog));
        dialog.show();
    }

    private void updateTimerVisibility(String id, boolean visible) {
        List<AppPrefs.TimerConfig> configs = new ArrayList<>(AppPrefs.getTimerConfigs(this));
        for (int i = 0; i < configs.size(); i++) {
            AppPrefs.TimerConfig config = configs.get(i);
            if (config.id.equals(id)) {
                configs.set(i, config.withVisible(visible));
                break;
            }
        }
        AppPrefs.setTimerConfigs(this, configs);
    }

    private void upsertTimer(AppPrefs.TimerConfig updated) {
        List<AppPrefs.TimerConfig> configs = new ArrayList<>(AppPrefs.getTimerConfigs(this));
        boolean replaced = false;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).id.equals(updated.id)) {
                configs.set(i, updated);
                replaced = true;
                break;
            }
        }
        if (!replaced) configs.add(updated);
        AppPrefs.setTimerConfigs(this, configs);
    }

    private void addSectionTitle(LinearLayout root, String value) {
        TextView section = text(value, 18, TEXT_PRIMARY);
        section.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(section, topMargin(22));
        section.setPadding(dp(2), 0, 0, dp(9));
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(rounded(SURFACE, 18, BORDER, 1));
        card.setElevation(dp(2));
        return card;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private TextView settingLabel(String label, String value) {
        TextView view = text(settingText(label, value), 15, TEXT_SECONDARY);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private String settingText(String label, String value) {
        return label + "  ·  " + value;
    }

    private Button button(String value, boolean primary) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.rgb(2, 20, 18) : TEXT_PRIMARY);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setMinHeight(dp(48));
        button.setBackground(rounded(primary ? ACCENT : SURFACE_RAISED, 12, primary ? ACCENT : BORDER, 1));
        return button;
    }

    private Button compactButton(String value) {
        Button button = button(value, false);
        button.setTextSize(13);
        button.setMinHeight(0);
        button.setPadding(dp(4), 0, dp(4), 0);
        return button;
    }

    private Button textButton(String value, int color) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(color);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(rounded(Color.TRANSPARENT, 10, BORDER, 1));
        return button;
    }

    private Switch switchView(String value) {
        Switch view = new Switch(this);
        view.setText(value);
        view.setTextSize(15);
        view.setTextColor(TEXT_PRIMARY);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setShowText(false);
        view.setThumbTintList(checkTint());
        view.setTrackTintList(new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT_DARK, Color.rgb(48, 57, 67)}));
        return view;
    }

    private SeekBar seekBar(int max, int progress) {
        SeekBar view = new SeekBar(this);
        view.setMax(max);
        view.setProgress(progress);
        view.setProgressTintList(ColorStateList.valueOf(ACCENT));
        view.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(45, 55, 66)));
        view.setThumbTintList(ColorStateList.valueOf(ACCENT));
        return view;
    }

    private SeekBar.OnSeekBarChangeListener listener(ProgressAction action) {
        return new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                action.onProgress(progress);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };
    }

    private ColorStateList checkTint() {
        return new ColorStateList(
                new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{ACCENT, Color.rgb(104, 116, 129)});
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private View spacer(int heightDp) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(heightDp)));
        return view;
    }

    private EditText textField(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(TEXT_MUTED);
        input.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        input.setPadding(dp(4), dp(4), dp(4), dp(4));
        return input;
    }

    private EditText numericField(String hint, String value) {
        EditText input = textField(hint, value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setGravity(Gravity.CENTER);
        return input;
    }

    private LinearLayout dialogBody() {
        LinearLayout body = vertical();
        body.setPadding(dp(24), dp(8), dp(24), dp(2));
        return body;
    }

    private LinearLayout.LayoutParams weightedField(int leftMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = dp(leftMarginDp);
        return params;
    }

    private int parseNumber(EditText input) {
        try {
            String raw = input.getText().toString().trim();
            return raw.isEmpty() ? 0 : Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private void tintDialogButtons(AlertDialog dialog) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(ACCENT);
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(TEXT_SECONDARY);
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams fullWidthMargin(int topDp) {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams cardParams(int topDp) {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = dp(topDp);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        return params;
    }

    private void requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            updatePermissionStatus();
            return;
        }
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void startStopwatch() {
        if (!Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }
        requestNotificationPermissionIfNeeded();
        Intent intent = new Intent(this, StopwatchService.class);
        intent.setAction(StopwatchService.ACTION_SHOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void sendServiceAction(String action) {
        if (!StopwatchService.isRunning() && !StopwatchService.ACTION_SHOW.equals(action)) return;
        Intent intent = new Intent(this, StopwatchService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !StopwatchService.isRunning()) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void refreshOverlay() {
        if (StopwatchService.isRunning()) {
            sendServiceAction(StopwatchService.ACTION_REFRESH);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private void updatePermissionStatus() {
        if (permissionStatus == null) return;
        if (Settings.canDrawOverlays(this)) {
            permissionStatus.setText("●  Permiso concedido");
            permissionStatus.setTextColor(ACCENT);
        } else {
            permissionStatus.setText("●  Falta activar ‘Aparecer encima’");
            permissionStatus.setTextColor(DANGER);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private interface ProgressAction {
        void onProgress(int progress);
    }
}
