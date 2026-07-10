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
import android.view.Gravity;
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
    private static final int REQUEST_OVERLAY = 4101;
    private static final int REQUEST_NOTIFICATIONS = 4102;

    private TextView permissionStatus;
    private TextView sizeValue;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView title = text("Cronómetro flotante lateral", 26, Color.rgb(25, 25, 25));
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(title);

        TextView description = text(
                "Cuenta hacia arriba sobre cualquier aplicación. Puedes arrastrarlo, pausarlo y plegarlo en el borde dejando solo una pestaña pequeña.",
                16,
                Color.rgb(70, 70, 70));
        description.setPadding(0, dp(8), 0, dp(16));
        root.addView(description);

        permissionStatus = text("", 15, Color.rgb(80, 80, 80));
        permissionStatus.setPadding(0, 0, 0, dp(10));
        root.addView(permissionStatus);

        Button permissionButton = new Button(this);
        permissionButton.setText("Conceder permiso para mostrarse encima");
        permissionButton.setOnClickListener(v -> requestOverlayPermission());
        root.addView(permissionButton, fullWidth());

        Button showButton = new Button(this);
        showButton.setText("Mostrar e iniciar cronómetro");
        showButton.setOnClickListener(v -> startStopwatch());
        LinearLayout.LayoutParams showParams = fullWidth();
        showParams.topMargin = dp(10);
        root.addView(showButton, showParams);

        Button stopButton = new Button(this);
        stopButton.setText("Cerrar cronómetro flotante");
        stopButton.setOnClickListener(v -> sendServiceAction(StopwatchService.ACTION_STOP));
        LinearLayout.LayoutParams stopParams = fullWidth();
        stopParams.topMargin = dp(6);
        root.addView(stopButton, stopParams);

        addSectionTitle(root, "Personalización");

        sizeValue = text("Tamaño del tiempo: " + AppPrefs.getTextSize(this) + " sp", 16, Color.DKGRAY);
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

        opacityValue = text("Opacidad: " + AppPrefs.getOpacity(this) + "%", 16, Color.DKGRAY);
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

        TextView themeLabel = text("Color", 16, Color.DKGRAY);
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
        tenthsSwitch.setText("Mostrar décimas de segundo");
        tenthsSwitch.setChecked(AppPrefs.showTenths(this));
        tenthsSwitch.setPadding(0, dp(8), 0, dp(8));
        tenthsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            AppPrefs.setShowTenths(MainActivity.this, isChecked);
            refreshOverlay();
        });
        root.addView(tenthsSwitch, fullWidth());

        TextView usage = text(
                "Uso: toca el tiempo para pausar o continuar. Arrástralo para moverlo. Pulsa la flecha para plegarlo en el borde y toca la pestaña para recuperarlo.",
                15,
                Color.rgb(70, 70, 70));
        usage.setPadding(0, dp(12), 0, dp(10));
        root.addView(usage);

        TextView privacy = text(
                "No contiene anuncios, no usa Internet y no solicita acceso a archivos, cámara, micrófono, contactos ni ubicación.",
                14,
                Color.rgb(90, 90, 90));
        root.addView(privacy);

        return scroll;
    }

    private void addSectionTitle(LinearLayout root, String value) {
        TextView section = text(value, 20, Color.rgb(35, 35, 35));
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
        startActivityForResult(intent, REQUEST_OVERLAY);
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
            permissionStatus.setTextColor(Color.rgb(25, 115, 55));
        } else {
            permissionStatus.setText("Permiso: falta activar ‘Aparecer encima’");
            permissionStatus.setTextColor(Color.rgb(175, 40, 35));
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
