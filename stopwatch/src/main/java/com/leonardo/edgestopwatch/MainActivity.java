package com.leonardo.edgestopwatch;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int REQUEST_NOTIFICATIONS = 4102;

    private static final int BACKGROUND = Color.rgb(18, 18, 18);
    private static final int TEXT_PRIMARY = Color.rgb(245, 245, 245);
    private static final int TEXT_SECONDARY = Color.rgb(190, 190, 190);
    private static final int TEXT_MUTED = Color.rgb(145, 145, 145);

    private TextView permissionStatus;
    private TextView sizeValue;
    private TextView widthValue;
    private TextView opacityValue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        root.setBackgroundColor(BACKGROUND);
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Cronómetro flotante lateral", 26, TEXT_PRIMARY);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView description = text(
                "Expandido muestra el tiempo. Contraído solo muestra una barra vertical: blanca cuando corre y negra cuando está pausado.",
                16,
                TEXT_SECONDARY);
        description.setPadding(0, dp(8), 0, dp(16));
        root.addView(description);

        permissionStatus = text("", 15, TEXT_SECONDARY);
        permissionStatus.setPadding(0, 0, 0, dp(10));
        root.addView(permissionStatus);

        Button permissionButton = new Button(this);
        permissionButton.setText("Conceder permiso para mostrarse encima");
        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permissionButton, fullWidth());

        Button showButton = new Button(this);
        showButton.setText("Mostrar cronómetro");
        showButton.setOnClickListener(v -> startStopwatch());
        LinearLayout.LayoutParams showParams = fullWidth();
        showParams.topMargin = dp(10);
        root.addView(showButton, showParams);

        Button stopButton = new Button(this);
        stopButton.setText("Cerrar cronómetro");
        stopButton.setOnClickListener(v -> sendServiceAction(StopwatchService.ACTION_STOP));
        LinearLayout.LayoutParams stopParams = fullWidth();
        stopParams.topMargin = dp(6);
        root.addView(stopButton, stopParams);

        addSectionTitle(root, "Personalización");

        sizeValue = text(
                "Tamaño del tiempo: " + AppPrefs.getTextSize(this) + " sp",
                16,
                TEXT_SECONDARY);
        root.addView(sizeValue);
        SeekBar sizeBar = new SeekBar(this);
        sizeBar.setMax(20);
        sizeBar.setProgress(AppPrefs.getTextSize(this) - 16);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 16 + progress;
                AppPrefs.setTextSize(MainActivity.this, value);
                sizeValue.setText("Tamaño del tiempo: " + value + " sp");
                refreshOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(sizeBar, fullWidth());

        widthValue = text(
                "Ancho: " + AppPrefs.getPanelWidth(this) + " dp",
                16,
                TEXT_SECONDARY);
        widthValue.setPadding(0, dp(8), 0, 0);
        root.addView(widthValue);
        SeekBar widthBar = new SeekBar(this);
        widthBar.setMax(AppPrefs.MAX_PANEL_WIDTH_DP - AppPrefs.MIN_PANEL_WIDTH_DP);
        widthBar.setProgress(AppPrefs.getPanelWidth(this) - AppPrefs.MIN_PANEL_WIDTH_DP);
        widthBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = AppPrefs.MIN_PANEL_WIDTH_DP + progress;
                AppPrefs.setPanelWidth(MainActivity.this, value);
                widthValue.setText("Ancho: " + value + " dp");
                refreshOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(widthBar, fullWidth());

        opacityValue = text(
                "Opacidad: " + AppPrefs.getOpacity(this) + "%",
                16,
                TEXT_SECONDARY);
        opacityValue.setPadding(0, dp(8), 0, 0);
        root.addView(opacityValue);
        SeekBar opacityBar = new SeekBar(this);
        opacityBar.setMax(70);
        opacityBar.setProgress(AppPrefs.getOpacity(this) - 30);
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 30 + progress;
                AppPrefs.setOpacity(MainActivity.this, value);
                opacityValue.setText("Opacidad: " + value + "%");
                refreshOverlay();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        root.addView(opacityBar, fullWidth());

        TextView themeLabel = text("Color del cronómetro", 16, TEXT_SECONDARY);
        themeLabel.setPadding(0, dp(8), 0, dp(4));
        root.addView(themeLabel);

        RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.setOrientation(RadioGroup.VERTICAL);
        int currentTheme = AppPrefs.getTheme(this);
        int[] themeValues = {
                AppPrefs.THEME_BLACK,
                AppPrefs.THEME_DARK,
                AppPrefs.THEME_LIGHT,
                AppPrefs.THEME_BLUE
        };
        String[] themeNames = {"Negro", "Gris oscuro", "Claro", "Azul"};
        for (int i = 0; i < themeValues.length; i++) {
            RadioButton radio = new RadioButton(this);
            radio.setId(View.generateViewId());
            radio.setTag(themeValues[i]);
            radio.setText(themeNames[i]);
            radio.setTextColor(TEXT_PRIMARY);
            radio.setChecked(themeValues[i] == currentTheme);
            themeGroup.addView(radio);
        }
        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            View checked = group.findViewById(checkedId);
            if (checked != null && checked.getTag() instanceof Integer) {
                AppPrefs.setTheme(MainActivity.this, (Integer) checked.getTag());
                refreshOverlay();
            }
        });
        root.addView(themeGroup, fullWidth());

        Switch tenthsSwitch = new Switch(this);
        tenthsSwitch.setText("Mostrar décimas");
        tenthsSwitch.setTextColor(TEXT_PRIMARY);
        tenthsSwitch.setChecked(AppPrefs.showTenths(this));
        tenthsSwitch.setPadding(0, dp(8), 0, dp(8));
        tenthsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setShowTenths(MainActivity.this, isChecked);
            refreshOverlay();
        });
        root.addView(tenthsSwitch, fullWidth());

        TextView usage = text(
                "Gestos: toca los números para pausar o continuar; desliza horizontalmente para contraerlo en un borde; toca la barra para expandirlo; arrástralo hasta abajo para cerrarlo.",
                15,
                TEXT_SECONDARY);
        usage.setPadding(0, dp(12), 0, dp(10));
        root.addView(usage);

        TextView privacy = text(
                "Sin anuncios, sin Internet y sin acceso a archivos, cámara, micrófono, contactos o ubicación.",
                14,
                TEXT_MUTED);
        root.addView(privacy);

        return scroll;
    }

    private void addSectionTitle(LinearLayout root, String value) {
        TextView section = text(value, 20, TEXT_PRIMARY);
        section.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        section.setPadding(0, dp(24), 0, dp(10));
        root.addView(section);
    }

    private TextView text(String value, int sizeSp, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
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
        Intent intent = new Intent(this, StopwatchService.class);
        intent.setAction(action);
        startService(intent);
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
            permissionStatus.setText("Permiso: concedido");
            permissionStatus.setTextColor(Color.rgb(129, 199, 132));
        } else {
            permissionStatus.setText("Permiso: falta activar ‘Aparecer encima’");
            permissionStatus.setTextColor(Color.rgb(239, 154, 154));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
