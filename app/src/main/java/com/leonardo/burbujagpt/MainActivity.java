package com.leonardo.burbujagpt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;
    private static final int COLOR_BACKGROUND = 0xFF09090B;
    private static final int COLOR_CARD = 0xFF18181B;
    private static final int COLOR_TEXT = 0xFFF4F4F5;
    private static final int COLOR_MUTED = 0xFFA1A1AA;
    private static final int COLOR_SUBTLE = 0xFFD4D4D8;
    private static final int COLOR_BORDER = 0xFF3F3F46;
    private static final int COLOR_BUTTON = 0xFF27272A;
    private static final int COLOR_PRIMARY = 0xFF2563EB;

    private TextView statusView;
    private Button permissionButton;
    private static volatile MainActivity visibleInstance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visibleInstance = this;
        updateStatus();
    }

    @Override
    protected void onPause() {
        if (visibleInstance == this) visibleInstance = null;
        super.onPause();
    }

    static void sendVisibleTaskToBack() {
        MainActivity activity = visibleInstance;
        if (activity != null && !activity.isFinishing()) {
            activity.runOnUiThread(() -> activity.moveTaskToBack(true));
        }
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(COLOR_BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView orb = new TextView(this);
        orb.setText("✦");
        orb.setTextSize(32);
        orb.setTextColor(Color.WHITE);
        orb.setGravity(Gravity.CENTER);
        GradientDrawable orbBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7C3AED, 0xFF0EA5E9, 0xFF10B981}
        );
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = makeText("BurbujaGPT V7", 28, COLOR_TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(13), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = makeText(
                "Globo configurable con tus chats reales de ChatGPT",
                16,
                COLOR_MUTED,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(7), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout infoCard = makeCard();
        infoCard.addView(makeText(
                "Ahora incluye una burbuja reconocida por Android. El panel conserva la misma pagina mientras el servicio esta activo. Para cuentas iniciadas con Google, el acceso incrustado sigue bloqueado por Google: usa correo y contrasena, la app oficial o el navegador.",
                14,
                COLOR_TEXT,
                false
        ), matchWrap());
        TextView gestureHelp = makeText(
                "Toca: abrir · Mantén pulsado: ajustes · Arrastra: mover · Arrastra hacia ×: apagar",
                12,
                COLOR_MUTED,
                false
        );
        LinearLayout.LayoutParams gestureParams = matchWrap();
        gestureParams.setMargins(0, dp(8), 0, 0);
        infoCard.addView(gestureHelp, gestureParams);
        root.addView(infoCard, cardParams());

        LinearLayout modeCard = makeCard();
        modeCard.addView(makeText("Al tocar el globo", 17, COLOR_TEXT, true), matchWrap());

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);

        boolean nativeSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;
        RadioButton nativeMode = makeRadio(
                nativeSupported
                        ? "Burbuja nativa de Android — mantiene el panel y la pagina"
                        : "Burbuja nativa — requiere Android 11 o posterior",
                AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this))
        );
        nativeMode.setId(103);
        nativeMode.setEnabled(nativeSupported);
        modeGroup.addView(nativeMode);

        String officialLabel = OfficialChatLauncher.isOfficialAppInstalled(this)
                ? "App oficial emergente — recomendada y con todos tus chats"
                : "App oficial emergente — ChatGPT no está instalada";
        RadioButton officialMode = makeRadio(
                officialLabel,
                AppPreferences.MODE_OFFICIAL.equals(AppPreferences.getMode(this))
        );
        officialMode.setId(101);
        modeGroup.addView(officialMode);

        RadioButton browserMode = makeRadio(
                "Navegador emergente — usa tu sesión de Brave o Chrome",
                AppPreferences.MODE_BROWSER.equals(AppPreferences.getMode(this))
        );
        browserMode.setId(102);
        modeGroup.addView(browserMode);

        RadioButton webMode = makeRadio(
                "Panel web interno — solo correo/contraseña, Google lo bloquea",
                AppPreferences.MODE_WEB.equals(AppPreferences.getMode(this))
        );
        webMode.setId(100);
        modeGroup.addView(webMode);

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode = checkedId == 103
                    ? AppPreferences.MODE_NATIVE
                    : checkedId == 101
                    ? AppPreferences.MODE_OFFICIAL
                    : checkedId == 102 ? AppPreferences.MODE_BROWSER : AppPreferences.MODE_WEB;
            AppPreferences.setMode(this, mode);
            updateStatus();
            restartRunningBubbleForModeChange();
        });
        modeCard.addView(modeGroup, matchWrap());

        modeCard.addView(makeButton(
                "Configurar burbujas de Android / Samsung",
                false,
                v -> openNativeBubbleSettings()
        ), compactButtonParams());

        modeCard.addView(makeButton(
                "Probar app oficial emergente",
                false,
                v -> testOfficialFloatingMode()
        ), compactButtonParams());
        modeCard.addView(makeButton(
                "Probar navegador emergente",
                false,
                v -> testBrowserFloatingMode()
        ), compactButtonParams());
        modeCard.addView(makeButton(
                "Probar panel web interno",
                false,
                v -> openFloatingChat()
        ), compactButtonParams());
        root.addView(modeCard, cardParams());

        LinearLayout appearanceCard = makeCard();
        appearanceCard.addView(makeText("Aspecto del globo", 17, COLOR_TEXT, true), matchWrap());

        TextView sizeLabel = makeText(
                "Tamaño: " + AppPreferences.getBubbleSize(this) + " dp",
                13,
                COLOR_SUBTLE,
                false
        );
        appearanceCard.addView(sizeLabel, labelParams());
        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(36);
        sizeSeek.setProgress(AppPreferences.getBubbleSize(this) - 48);
        sizeSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 48 + progress;
                sizeLabel.setText("Tamaño: " + value + " dp");
                if (fromUser) {
                    AppPreferences.setBubbleSize(MainActivity.this, value);
                    refreshRunningBubble();
                }
            }
        });
        appearanceCard.addView(sizeSeek, matchWrap());

        TextView opacityLabel = makeText(
                "Opacidad: " + AppPreferences.getBubbleOpacity(this) + "%",
                13,
                COLOR_SUBTLE,
                false
        );
        appearanceCard.addView(opacityLabel, labelParams());
        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(55);
        opacitySeek.setProgress(AppPreferences.getBubbleOpacity(this) - 45);
        opacitySeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 45 + progress;
                opacityLabel.setText("Opacidad: " + value + "%");
                if (fromUser) {
                    AppPreferences.setBubbleOpacity(MainActivity.this, value);
                    refreshRunningBubble();
                }
            }
        });
        appearanceCard.addView(opacitySeek, matchWrap());

        appearanceCard.addView(makeText("Tamaño inicial del panel", 13, COLOR_SUBTLE, false), labelParams());
        RadioGroup panelGroup = new RadioGroup(this);
        panelGroup.setOrientation(RadioGroup.HORIZONTAL);
        int currentPanelSize = AppPreferences.getPanelSize(this);
        RadioButton compact = makeRadio("Compacto", currentPanelSize == AppPreferences.PANEL_COMPACT);
        compact.setId(200);
        RadioButton large = makeRadio("Grande", currentPanelSize == AppPreferences.PANEL_LARGE);
        large.setId(201);
        RadioButton full = makeRadio("Completo", currentPanelSize == AppPreferences.PANEL_FULL);
        full.setId(202);
        panelGroup.addView(compact, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panelGroup.addView(large, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panelGroup.addView(full, new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        panelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int size = checkedId == 200
                    ? AppPreferences.PANEL_COMPACT
                    : checkedId == 202 ? AppPreferences.PANEL_FULL : AppPreferences.PANEL_LARGE;
            AppPreferences.setPanelSize(this, size);
        });
        appearanceCard.addView(panelGroup, matchWrap());
        root.addView(appearanceCard, cardParams());

        statusView = makeText("", 14, COLOR_SUBTLE, true);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(statusView, statusParams);

        permissionButton = makeButton("1. Permitir globo flotante", true, v -> requestRequiredPermission());
        root.addView(permissionButton, buttonParams());
        root.addView(makeButton("2. Activar globo", true, v -> startBubble()), buttonParams());
        root.addView(makeButton("Desactivar globo", false, v -> stopBubble()), buttonParams());
        root.addView(makeButton("Cerrar sesión del panel web", false, v -> confirmClearWebSession()), buttonParams());

        TextView footer = makeText(
                "En Samsung activa Ajustes > Notificaciones > Ajustes avanzados > Notificaciones flotantes > Burbujas. Android puede cerrar cualquier proceso bajo presion extrema de memoria, pero el servicio mantiene el WebView vivo en condiciones normales.",
                12,
                COLOR_MUTED,
                false
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.setMargins(0, dp(16), 0, 0);
        root.addView(footer, footerParams);

        return scroll;
    }

    private void updateStatus() {
        boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        String selectedMode = AppPreferences.getMode(this);
        boolean nativeMode = AppPreferences.MODE_NATIVE.equals(selectedMode);
        boolean notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        boolean bubblesAllowed = true;
        if (nativeMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            bubblesAllowed = manager != null && manager.areBubblesAllowed();
        }
        String mode = nativeMode
                ? "burbuja nativa"
                : AppPreferences.MODE_OFFICIAL.equals(selectedMode)
                ? "app oficial"
                : AppPreferences.MODE_BROWSER.equals(selectedMode) ? "navegador" : "panel web";

        if (statusView != null) {
            if (nativeMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                statusView.setText("Estado: la burbuja nativa requiere Android 11 o posterior");
                statusView.setTextColor(0xFFFBBF24);
            } else if (nativeMode && !notificationsGranted) {
                statusView.setText("Estado: falta permitir notificaciones para crear la burbuja");
                statusView.setTextColor(0xFFFBBF24);
            } else if (nativeMode && !bubblesAllowed) {
                statusView.setText("Estado: activa Burbujas en los ajustes de notificaciones");
                statusView.setTextColor(0xFFFBBF24);
            } else if (!nativeMode && !overlayGranted) {
                statusView.setText("Estado: falta permitir Aparecer encima");
                statusView.setTextColor(0xFFFBBF24);
            } else {
                statusView.setText(
                        "Estado: listo · Globo "
                                + (BubbleService.isRunning ? "activo" : "apagado")
                                + " · Modo " + mode
                );
                statusView.setTextColor(BubbleService.isRunning ? 0xFF34D399 : COLOR_SUBTLE);
            }
        }
        if (permissionButton != null) {
            if (nativeMode) {
                permissionButton.setText("1. Configurar burbujas del sistema");
            } else {
                permissionButton.setText(overlayGranted
                        ? "1. Permiso concedido"
                        : "1. Permitir globo flotante");
            }
        }
    }

    private void requestRequiredPermission() {
        if (AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this))) {
            openNativeBubbleSettings();
        } else {
            requestOverlayPermission();
        }
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "El permiso ya está concedido", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startBubble() {
        boolean nativeMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this));
        if (!nativeMode
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        if (!requestNotificationPermissionIfNeeded()) return;
        launchBubbleService();
        Toast.makeText(
                this,
                nativeMode ? "Burbuja nativa activada" : "Globo activado",
                Toast.LENGTH_SHORT
        ).show();
        statusView.postDelayed(this::updateStatus, 250);
        statusView.postDelayed(() -> moveTaskToBack(true), 450);
    }

    private void launchBubbleService() {
        Intent service = new Intent(this, BubbleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
    }

    private void stopBubble() {
        stopService(new Intent(this, BubbleService.class));
        Toast.makeText(this, "Globo desactivado", Toast.LENGTH_SHORT).show();
        statusView.postDelayed(this::updateStatus, 200);
    }

    private void refreshRunningBubble() {
        if (!BubbleService.isRunning) return;
        Intent refresh = new Intent(this, BubbleService.class);
        refresh.setAction(BubbleService.ACTION_REFRESH);
        startService(refresh);
    }

    private void restartRunningBubbleForModeChange() {
        if (!BubbleService.isRunning || statusView == null) return;
        stopService(new Intent(this, BubbleService.class));
        statusView.postDelayed(() -> {
            boolean nativeMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this));
            boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                    || Settings.canDrawOverlays(this);
            if ((nativeMode || overlayGranted) && requestNotificationPermissionIfNeeded()) {
                launchBubbleService();
            }
            statusView.postDelayed(this::updateStatus, 250);
        }, 220);
    }

    private boolean requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startBubble();
        } else {
            Toast.makeText(
                    this,
                    "Android necesita notificaciones para mostrar una burbuja nativa",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
        }
    }

    private void openNativeBubbleSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "La burbuja nativa requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }

        BubbleService.ensureNativeChannel(this);
        Intent bubbleSettings = new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS);
        bubbleSettings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(bubbleSettings);
        } catch (Exception ignored) {
            Intent notificationSettings = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            notificationSettings.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(notificationSettings);
        }
    }

    private void openFloatingChat() {
        moveTaskToBack(true);
        Intent intent = new Intent(this, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        getApplicationContext().startActivity(intent);
    }

    private void testOfficialFloatingMode() {
        moveTaskToBack(true);
        if (!OfficialChatLauncher.openOfficialApp(this, true)) {
            Toast.makeText(this, "La app oficial de ChatGPT no está instalada", Toast.LENGTH_LONG).show();
        }
    }

    private void testBrowserFloatingMode() {
        moveTaskToBack(true);
        if (!OfficialChatLauncher.openBrowser(this, "https://chatgpt.com/", true)) {
            Toast.makeText(this, "No hay un navegador disponible", Toast.LENGTH_LONG).show();
        }
    }

    private void confirmClearWebSession() {
        new AlertDialog.Builder(this)
                .setTitle("¿Cerrar la sesión del panel?")
                .setMessage("Borrará las cookies y datos locales del panel web. No elimina tus chats de ChatGPT.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Cerrar sesión", (dialog, which) -> clearWebSession())
                .show();
    }

    private void clearWebSession() {
        CookieManager.getInstance().removeAllCookies(value -> {
            CookieManager.getInstance().flush();
            Toast.makeText(this, "Sesión web eliminada", Toast.LENGTH_SHORT).show();
        });
        WebStorage.getInstance().deleteAllData();
        getSharedPreferences("chat_web_state", MODE_PRIVATE).edit().clear().apply();
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_CARD);
        background.setCornerRadius(dp(16));
        background.setStroke(dp(1), COLOR_BORDER);
        card.setBackground(background);
        return card;
    }

    private TextView makeText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private RadioButton makeRadio(String text, boolean checked) {
        RadioButton radio = new RadioButton(this);
        radio.setText(text);
        radio.setTextSize(13);
        radio.setTextColor(COLOR_TEXT);
        radio.setChecked(checked);
        radio.setPadding(0, dp(3), 0, dp(3));
        return radio;
    }

    private Button makeButton(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.WHITE : COLOR_TEXT);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setOnClickListener(listener);

        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? COLOR_PRIMARY : COLOR_BUTTON);
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), primary ? COLOR_PRIMARY : COLOR_BORDER);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams labelParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(11), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams compactButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        params.setMargins(0, dp(5), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
