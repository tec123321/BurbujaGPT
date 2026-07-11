package com.leonardo.burbujagpt;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
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

/** Configuración de Globo GPT. La APK oficial nunca se modifica ni se vuelve a firmar. */
public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1101;
    private static final int BACKGROUND = 0xFF050505;
    private static final int CARD = 0xFF171717;
    private static final int BORDER = 0xFF343434;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3A3A3;
    private static final int PRIMARY = 0xFF2563EB;

    private static final int MODE_ID_NATIVE = 201;
    private static final int MODE_ID_OFFICIAL = 202;
    private static final int MODE_ID_WEB = 203;
    private static final int MODE_ID_BROWSER = 204;

    private static volatile MainActivity visibleInstance;

    private TextView statusView;
    private Button permissionButton;
    private boolean awaitingOverlayPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppPreferences.migrateToV13(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visibleInstance = this;
        updateStatus();

        if (awaitingOverlayPermission && canDrawOverlays()) {
            awaitingOverlayPermission = false;
            beginActivation();
        }
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
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView orb = makeText("✦", 34, Color.WHITE, true);
        orb.setGravity(Gravity.CENTER);
        GradientDrawable orbBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7C3AED, 0xFF0EA5E9, 0xFF10B981}
        );
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = makeText("Globo GPT V13", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = makeText(
                "Burbuja propia sin alterar ChatGPT oficial",
                15,
                MUTED,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = makeCard();
        explanation.addView(makeText(
                "El modo principal abre chatgpt.com dentro de una burbuja nativa y conserva cookies, almacenamiento local, la página y el chat mientras el servicio está activo.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView limit = makeText(
                "Google bloquea su inicio de sesión dentro de WebView. Si tu cuenta entra únicamente con Google, usa el modo de aplicación oficial: conserva la firma, Play Integrity, Plus, historial y voz.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams limitParams = matchWrap();
        limitParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(limit, limitParams);
        root.addView(explanation, cardParams());

        LinearLayout modeCard = makeCard();
        modeCard.addView(makeText("Contenido del globo", 17, TEXT, true), matchWrap());

        RadioGroup modeGroup = new RadioGroup(this);
        modeGroup.setOrientation(RadioGroup.VERTICAL);

        RadioButton nativeMode = makeRadio(
                "Burbuja nativa · panel web persistente",
                AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this))
        );
        nativeMode.setId(MODE_ID_NATIVE);
        nativeMode.setEnabled(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R);
        modeGroup.addView(nativeMode);

        RadioButton officialMode = makeRadio(
                OfficialChatLauncher.isOfficialAppInstalled(this)
                        ? "App oficial · ventana emergente con Shizuku"
                        : "App oficial · falta instalar ChatGPT",
                AppPreferences.MODE_OFFICIAL.equals(AppPreferences.getMode(this))
        );
        officialMode.setId(MODE_ID_OFFICIAL);
        modeGroup.addView(officialMode);

        RadioButton webMode = makeRadio(
                "Panel web compatible · globo superpuesto",
                AppPreferences.MODE_WEB.equals(AppPreferences.getMode(this))
        );
        webMode.setId(MODE_ID_WEB);
        modeGroup.addView(webMode);

        RadioButton browserMode = makeRadio(
                "Navegador · usa la sesión de Chrome o Brave",
                AppPreferences.MODE_BROWSER.equals(AppPreferences.getMode(this))
        );
        browserMode.setId(MODE_ID_BROWSER);
        modeGroup.addView(browserMode);

        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String mode;
            if (checkedId == MODE_ID_NATIVE) mode = AppPreferences.MODE_NATIVE;
            else if (checkedId == MODE_ID_OFFICIAL) mode = AppPreferences.MODE_OFFICIAL;
            else if (checkedId == MODE_ID_BROWSER) mode = AppPreferences.MODE_BROWSER;
            else mode = AppPreferences.MODE_WEB;

            AppPreferences.setMode(MainActivity.this, mode);
            updateStatus();
            restartBubbleAfterModeChange();
        });
        modeCard.addView(modeGroup, matchWrap());

        modeCard.addView(makeButton(
                "Configurar burbujas de Android",
                false,
                v -> openNativeBubbleSettings()
        ), compactButtonParams());
        modeCard.addView(makeButton(
                "Permitir globo compatible de respaldo",
                false,
                v -> requestOverlayPermission()
        ), compactButtonParams());
        modeCard.addView(makeButton(
                "Probar el modo seleccionado",
                false,
                v -> testSelectedMode()
        ), compactButtonParams());
        root.addView(modeCard, cardParams());

        LinearLayout appearance = makeCard();
        appearance.addView(makeText("Aspecto del globo", 17, TEXT, true), matchWrap());

        TextView sizeLabel = makeText(
                "Tamaño: " + AppPreferences.getBubbleSize(this) + " dp",
                13,
                MUTED,
                false
        );
        appearance.addView(sizeLabel, labelParams());

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
                    refreshBubble();
                }
            }
        });
        appearance.addView(sizeSeek, matchWrap());

        TextView opacityLabel = makeText(
                "Opacidad: " + AppPreferences.getBubbleOpacity(this) + "%",
                13,
                MUTED,
                false
        );
        appearance.addView(opacityLabel, labelParams());

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
                    refreshBubble();
                }
            }
        });
        appearance.addView(opacitySeek, matchWrap());

        appearance.addView(makeText("Tamaño inicial del panel", 13, MUTED, false), labelParams());
        RadioGroup panelGroup = new RadioGroup(this);
        panelGroup.setOrientation(RadioGroup.HORIZONTAL);
        int panelSize = AppPreferences.getPanelSize(this);
        RadioButton compact = makeRadio("Compacto", panelSize == AppPreferences.PANEL_COMPACT);
        compact.setId(301);
        RadioButton large = makeRadio("Grande", panelSize == AppPreferences.PANEL_LARGE);
        large.setId(302);
        RadioButton full = makeRadio("Completo", panelSize == AppPreferences.PANEL_FULL);
        full.setId(303);
        panelGroup.addView(compact, weightedParams());
        panelGroup.addView(large, weightedParams());
        panelGroup.addView(full, weightedParams());
        panelGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int value = checkedId == 301
                    ? AppPreferences.PANEL_COMPACT
                    : checkedId == 303
                    ? AppPreferences.PANEL_FULL
                    : AppPreferences.PANEL_LARGE;
            AppPreferences.setPanelSize(MainActivity.this, value);
        });
        appearance.addView(panelGroup, matchWrap());
        root.addView(appearance, cardParams());

        statusView = makeText("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        permissionButton = makeButton("Configurar permisos", true, v -> requestRequiredPermission());
        root.addView(permissionButton, buttonParams());
        root.addView(makeButton("Activar globo", true, v -> beginActivation()), buttonParams());
        root.addView(makeButton("Desactivar globo", false, v -> stopBubble()), buttonParams());
        root.addView(makeButton(
                "Cerrar sesión del panel web",
                false,
                v -> confirmClearWebSession()
        ), buttonParams());
        root.addView(makeButton(
                "Ver diagnóstico de la burbuja",
                false,
                v -> showNativeDiagnostic()
        ), buttonParams());

        TextView help = makeText(
                "Toca el globo para abrir o minimizar. Mantén pulsado para volver a estos ajustes. Arrástralo hacia la × para apagarlo.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpParams = matchWrap();
        helpParams.setMargins(0, dp(10), 0, 0);
        root.addView(help, helpParams);

        return scroll;
    }

    private void beginActivation() {
        String mode = AppPreferences.getMode(this);
        boolean nativeMode = AppPreferences.MODE_NATIVE.equals(mode)
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R;

        if (AppPreferences.MODE_OFFICIAL.equals(mode)
                && !OfficialChatLauncher.isOfficialAppInstalled(this)) {
            Toast.makeText(this, "Instala primero ChatGPT oficial", Toast.LENGTH_LONG).show();
            openChatGptStorePage();
            return;
        }

        if (!nativeMode && !canDrawOverlays()) {
            awaitingOverlayPermission = true;
            requestOverlayPermission();
            return;
        }

        if (!notificationsGranted()) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }

        if (nativeMode) {
            BubbleService.ensureNativeChannel(this);
            if (!areNativeBubblesAllowed()) {
                Toast.makeText(
                        this,
                        "Activa las burbujas para Globo GPT y vuelve a pulsar Activar globo",
                        Toast.LENGTH_LONG
                ).show();
                openNativeBubbleSettings();
                return;
            }
        }

        activateBubble();
    }

    private void activateBubble() {
        if (AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this))) {
            AppPreferences.clearNativeError(this);
        }

        stopService(new Intent(this, BubbleService.class));
        getWindow().getDecorView().postDelayed(() -> {
            Intent service = new Intent(this, BubbleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            Toast.makeText(this, "Globo GPT activado", Toast.LENGTH_SHORT).show();
            updateStatus();
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 500);
        }, 220);
    }

    private void restartBubbleAfterModeChange() {
        if (!BubbleService.isRunning || statusView == null) return;
        stopService(new Intent(this, BubbleService.class));
        statusView.postDelayed(() -> {
            updateStatus();
            beginActivation();
        }, 260);
    }

    private void stopBubble() {
        stopService(new Intent(this, BubbleService.class));
        Toast.makeText(this, "Globo GPT desactivado", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::updateStatus, 220);
    }

    private void refreshBubble() {
        if (!BubbleService.isRunning) return;
        Intent refresh = new Intent(this, BubbleService.class);
        refresh.setAction(BubbleService.ACTION_REFRESH);
        startService(refresh);
    }

    private void requestRequiredPermission() {
        if (AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this))) {
            openNativeBubbleSettings();
        } else {
            requestOverlayPermission();
        }
    }

    private void requestOverlayPermission() {
        if (canDrawOverlays()) {
            Toast.makeText(this, "El permiso de globo compatible ya está concedido", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            ));
        } catch (RuntimeException error) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private void openNativeBubbleSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "La burbuja nativa requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }

        BubbleService.ensureNativeChannel(this);
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (RuntimeException error) {
            Intent fallback = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            fallback.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(fallback);
        }
    }

    private void testSelectedMode() {
        String mode = AppPreferences.getMode(this);
        moveTaskToBack(true);

        if (AppPreferences.MODE_OFFICIAL.equals(mode)) {
            if (!OfficialChatLauncher.openOfficialApp(this, true)) {
                Toast.makeText(this, "ChatGPT oficial no está instalada", Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (AppPreferences.MODE_BROWSER.equals(mode)) {
            if (!OfficialChatLauncher.openBrowser(this, "https://chatgpt.com/", true)) {
                Toast.makeText(this, "No hay navegador disponible", Toast.LENGTH_LONG).show();
            }
            return;
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void updateStatus() {
        if (statusView == null || permissionButton == null) return;

        String selectedMode = AppPreferences.getMode(this);
        boolean nativeMode = AppPreferences.MODE_NATIVE.equals(selectedMode);
        String modeName = nativeMode
                ? "burbuja nativa"
                : AppPreferences.MODE_OFFICIAL.equals(selectedMode)
                ? "aplicación oficial"
                : AppPreferences.MODE_BROWSER.equals(selectedMode)
                ? "navegador"
                : "panel compatible";

        if (nativeMode && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusView.setText("Estado: la burbuja nativa requiere Android 11 o posterior");
            statusView.setTextColor(0xFFFBBF24);
        } else if (!notificationsGranted()) {
            statusView.setText("Estado: falta permitir notificaciones");
            statusView.setTextColor(0xFFFBBF24);
        } else if (nativeMode && !areNativeBubblesAllowed()) {
            statusView.setText("Estado: falta habilitar burbujas para Globo GPT");
            statusView.setTextColor(0xFFFBBF24);
        } else if (!nativeMode && !canDrawOverlays()) {
            statusView.setText("Estado: falta permitir Aparecer encima");
            statusView.setTextColor(0xFFFBBF24);
        } else if (nativeMode && AppPreferences.isNativeFallbackRequired(this)) {
            statusView.setText("Estado: Samsung usó el globo compatible de respaldo");
            statusView.setTextColor(0xFFFBBF24);
        } else {
            statusView.setText(
                    "Estado: " + (BubbleService.isRunning ? "activo" : "listo")
                            + " · modo " + modeName
            );
            statusView.setTextColor(BubbleService.isRunning ? 0xFF34D399 : TEXT);
        }

        permissionButton.setText(nativeMode
                ? "Configurar burbujas del sistema"
                : canDrawOverlays() ? "Permiso concedido" : "Permitir Aparecer encima");
    }

    private boolean areNativeBubblesAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || !manager.areBubblesAllowed()) return false;

        NotificationChannel channel = manager.getNotificationChannel(BubbleService.NATIVE_CHANNEL_ID);
        return channel == null || channel.canBubble();
    }

    private boolean notificationsGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void showNativeDiagnostic() {
        String error = AppPreferences.getLastNativeError(this);
        String message = error == null || error.trim().isEmpty()
                ? "No hay fallos registrados. Si la burbuja no aparece, revisa que Android permita burbujas y notificaciones para Globo GPT."
                : error;
        new AlertDialog.Builder(this)
                .setTitle("Diagnóstico")
                .setMessage(message)
                .setNegativeButton("Cerrar", null)
                .setPositiveButton("Reintentar", (dialog, which) -> {
                    AppPreferences.clearNativeError(MainActivity.this);
                    beginActivation();
                })
                .show();
    }

    private void confirmClearWebSession() {
        new AlertDialog.Builder(this)
                .setTitle("¿Cerrar la sesión del panel?")
                .setMessage("Borrará cookies y almacenamiento local del panel web. No elimina tus chats de ChatGPT.")
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
        PersistentWebViewStore.destroyIfDetached();
    }

    private void openChatGptStorePage() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.openai.chatgpt")
            ));
        } catch (RuntimeException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=com.openai.chatgpt")
            ));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            beginActivation();
        } else {
            Toast.makeText(
                    this,
                    "Android necesita notificaciones para mantener y mostrar el globo",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
        }
    }

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD);
        background.setCornerRadius(dp(17));
        background.setStroke(dp(1), BORDER);
        card.setBackground(background);
        return card;
    }

    private TextView makeText(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.14f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private RadioButton makeRadio(String text, boolean checked) {
        RadioButton radio = new RadioButton(this);
        radio.setText(text);
        radio.setTextSize(14);
        radio.setTextColor(TEXT);
        radio.setChecked(checked);
        radio.setPadding(0, dp(4), 0, dp(4));
        return radio;
    }

    private Button makeButton(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? PRIMARY : 0xFF262626);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), primary ? PRIMARY : BORDER);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, 0, 0, dp(13));
        return params;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
        );
        params.setMargins(0, 0, 0, dp(10));
        return params;
    }

    private LinearLayout.LayoutParams compactButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.setMargins(0, dp(8), 0, 0);
        return params;
    }

    private LinearLayout.LayoutParams labelParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(0, dp(12), 0, 0);
        return params;
    }

    private RadioGroup.LayoutParams weightedParams() {
        return new RadioGroup.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
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
        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    }
}
