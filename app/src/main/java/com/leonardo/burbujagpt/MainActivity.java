package com.leonardo.burbujagpt;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
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
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** Configuración de las burbujas con pantalla virtual de ChatGPT oficial. */
public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1500;
    private static final int SHIZUKU_PERMISSION_REQUEST = 1502;
    private static final int BACKGROUND = 0xFF050505;
    private static final int CARD = 0xFF171717;
    private static final int BORDER = 0xFF343434;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3A3A3;
    private static final int PRIMARY = 0xFF10A37F;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            this::onShizukuPermissionResult;

    private TextView statusView;
    private TextView diagnosticView;
    private Button autoButton;
    private Runnable pendingAfterNotificationPermission;
    private Runnable pendingAfterShizukuPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        NativeBubblePublisher.cancelLegacy(this);
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener);
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_gpt_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(logo, logoParams);

        TextView title = text("Globo GPT V15", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text("ChatGPT oficial dentro de una pantalla virtual", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "No usa WebView ni la ventana múltiple de Samsung. Shizuku crea una pantalla virtual y ejecuta allí la aplicación oficial de ChatGPT; el globo muestra esa pantalla y reenvía los toques.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView note = text(
                "Después de reiniciar el teléfono debes volver a iniciar Shizuku. El primer arranque puede tardar unos segundos.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(note, noteParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        root.addView(button("Preparar Shizuku", true, view -> prepareShizuku(null)), buttonParams());
        root.addView(button("Crear un globo de ChatGPT", false, view -> createManualBubble()), buttonParams());

        autoButton = button("Activar globos automáticos", false, view -> toggleAutomaticBubbles());
        root.addView(autoButton, buttonParams());

        root.addView(button(
                "Permitir acceso a notificaciones",
                false,
                view -> openNotificationListenerSettings()
        ), buttonParams());
        root.addView(button(
                "Permitir burbujas en Android",
                false,
                view -> openBubbleSettings()
        ), buttonParams());
        root.addView(button(
                "Abrir Shizuku",
                false,
                view -> openShizuku()
        ), buttonParams());
        root.addView(button(
                "Eliminar todos los globos",
                false,
                view -> removeAllBubbles()
        ), buttonParams());

        diagnosticView = text("", 12, 0xFFFCA5A5, false);
        diagnosticView.setVisibility(View.GONE);
        root.addView(diagnosticView, cardParams());

        TextView help = text(
                "Para escribir, toca el cuadro de texto dentro de ChatGPT. Si el teclado no aparece en la pantalla virtual, usa el botón ⌨ de la barra del globo.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        root.addView(help, matchWrap());
        return scroll;
    }

    private void createManualBubble() {
        prepareShizuku(() -> runWithNotificationPermission(() -> {
            if (!isChatGptInstalled()) {
                Toast.makeText(this, "Instala primero ChatGPT oficial", Toast.LENGTH_LONG).show();
                openChatGptStorePage();
                return;
            }
            try {
                AppPreferences.clearLastError(this);
                BubbleRecord record = NativeBubblePublisher.publishManual(this, true);
                Toast.makeText(this, record.title + " creado", Toast.LENGTH_SHORT).show();
                getWindow().getDecorView().postDelayed(this::updateStatus, 350);
                getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 750);
            } catch (RuntimeException | LinkageError error) {
                AppPreferences.recordError(this, "No se pudo crear el globo", error);
                Toast.makeText(this, "Android rechazó el globo", Toast.LENGTH_LONG).show();
                updateStatus();
            }
        }));
    }

    private void toggleAutomaticBubbles() {
        if (AppPreferences.isAutoBubblesEnabled(this)) {
            AppPreferences.setAutoBubblesEnabled(this, false);
            Toast.makeText(this, "Globos automáticos desactivados", Toast.LENGTH_SHORT).show();
            updateStatus();
            return;
        }

        prepareShizuku(() -> runWithNotificationPermission(() -> {
            AppPreferences.setAutoBubblesEnabled(this, true);
            if (!isNotificationListenerEnabled()) {
                Toast.makeText(
                        this,
                        "Activa el acceso de Globo GPT a las notificaciones",
                        Toast.LENGTH_LONG
                ).show();
                openNotificationListenerSettings();
            } else {
                Toast.makeText(this, "Globos automáticos activados", Toast.LENGTH_SHORT).show();
            }
            updateStatus();
        }));
    }

    private void prepareShizuku(Runnable afterReady) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Se requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }
        if (!ShizukuDisplayBridge.isInstalled(this)) {
            Toast.makeText(this, "Instala Shizuku", Toast.LENGTH_LONG).show();
            openShizuku();
            return;
        }
        if (!ShizukuDisplayBridge.isRunning()) {
            Toast.makeText(this, "Abre Shizuku y pulsa Iniciar", Toast.LENGTH_LONG).show();
            openShizuku();
            return;
        }
        if (!ShizukuDisplayBridge.hasPermission()) {
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Toast.makeText(
                            this,
                            "Autoriza Globo GPT desde la lista de aplicaciones de Shizuku",
                            Toast.LENGTH_LONG
                    ).show();
                    openShizuku();
                } else {
                    pendingAfterShizukuPermission = afterReady;
                    ShizukuDisplayBridge.requestPermission(SHIZUKU_PERMISSION_REQUEST);
                }
            } catch (RuntimeException error) {
                AppPreferences.recordError(this, "No se pudo solicitar Shizuku", error);
                openShizuku();
            }
            return;
        }

        ShizukuDisplayBridge.connect(this, new ShizukuDisplayBridge.ConnectionCallback() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Shizuku listo", Toast.LENGTH_SHORT).show();
                    updateStatus();
                    if (afterReady != null) afterReady.run();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    AppPreferences.recordMessage(MainActivity.this, message);
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    updateStatus();
                });
            }
        });
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode != SHIZUKU_PERMISSION_REQUEST) return;
        Runnable action = pendingAfterShizukuPermission;
        pendingAfterShizukuPermission = null;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            prepareShizuku(action);
        } else {
            Toast.makeText(this, "Permiso de Shizuku rechazado", Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void runWithNotificationPermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAfterNotificationPermission = action;
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }
        action.run();
    }

    private void removeAllBubbles() {
        NativeBubblePublisher.cancelAll(this);
        VirtualDisplaySessions.releaseAll(this);
        Toast.makeText(this, "Todos los globos fueron eliminados", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        if (statusView == null || autoButton == null) return;

        boolean chatGpt = isChatGptInstalled();
        boolean shizukuInstalled = ShizukuDisplayBridge.isInstalled(this);
        boolean shizukuRunning = ShizukuDisplayBridge.isRunning();
        boolean shizukuPermission = ShizukuDisplayBridge.hasPermission();
        boolean autoEnabled = AppPreferences.isAutoBubblesEnabled(this);
        boolean listenerEnabled = isNotificationListenerEnabled();
        int bubbleCount = NativeBubblePublisher.activeBubbleCount(this);

        if (!chatGpt) {
            statusView.setText("Estado: falta instalar ChatGPT oficial");
            statusView.setTextColor(0xFFFCA5A5);
        } else if (!shizukuInstalled) {
            statusView.setText("Estado: falta instalar Shizuku");
            statusView.setTextColor(0xFFFCA5A5);
        } else if (!shizukuRunning) {
            statusView.setText("Estado: Shizuku está detenido");
            statusView.setTextColor(0xFFFBBF24);
        } else if (!shizukuPermission) {
            statusView.setText("Estado: falta autorizar Globo GPT en Shizuku");
            statusView.setTextColor(0xFFFBBF24);
        } else {
            String autoState = !autoEnabled
                    ? "automático desactivado"
                    : (listenerEnabled ? "automático activo" : "automático sin permiso de lectura");
            statusView.setText(
                    "Estado: listo · " + bubbleCount
                            + (bubbleCount == 1 ? " globo" : " globos")
                            + " · " + autoState
            );
            statusView.setTextColor(0xFF34D399);
        }

        autoButton.setText(autoEnabled
                ? "Desactivar globos automáticos"
                : "Activar globos automáticos");

        String diagnostic = AppPreferences.getLastError(this);
        if (diagnostic.isEmpty()) {
            diagnosticView.setVisibility(View.GONE);
        } else {
            diagnosticView.setText("Diagnóstico:\n" + diagnostic);
            diagnosticView.setVisibility(View.VISIBLE);
        }
    }

    private boolean isChatGptInstalled() {
        return getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.CHATGPT_PACKAGE
        ) != null;
    }

    private boolean isNotificationListenerEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(),
                "enabled_notification_listeners"
        );
        ComponentName component = new ComponentName(
                this,
                ChatGptNotificationListenerService.class
        );
        return enabled != null && enabled.contains(component.flattenToString());
    }

    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, "Android no expuso ese ajuste", Toast.LENGTH_LONG).show();
        }
    }

    private void openBubbleSettings() {
        try {
            Intent bubbles = new Intent("android.settings.APP_NOTIFICATION_BUBBLE_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
            startActivity(bubbles);
        } catch (ActivityNotFoundException error) {
            try {
                Intent notifications = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                startActivity(notifications);
            } catch (ActivityNotFoundException ignored) {
                startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())
                ));
            }
        }
    }

    private void openShizuku() {
        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                ShizukuDisplayBridge.SHIZUKU_PACKAGE
        );
        if (launcher != null) {
            try {
                startActivity(launcher);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + ShizukuDisplayBridge.SHIZUKU_PACKAGE)
            ));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://shizuku.rikka.app/download/")
            ));
        }
    }

    private void openChatGptStorePage() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + NativeBubblePublisher.CHATGPT_PACKAGE)
            ));
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="
                            + NativeBubblePublisher.CHATGPT_PACKAGE)
            ));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;

        Runnable action = pendingAfterNotificationPermission;
        pendingAfterNotificationPermission = null;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (action != null) action.run();
        } else {
            Toast.makeText(
                    this,
                    "Sin notificaciones Android no puede crear los globos",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
        }
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(15), dp(16), dp(15));
        card.setBackground(rounded(CARD, 17, BORDER));
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.14f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(listener);
        button.setBackground(rounded(primary ? PRIMARY : 0xFF262626, 14, primary ? PRIMARY : BORDER));
        return button;
    }

    private GradientDrawable rounded(int color, int radiusDp, int borderColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), borderColor);
        return background;
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

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
