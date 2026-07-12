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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Configura una conversación Android que aloja la actividad oficial de ChatGPT. */
public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1300;
    private static final int BACKGROUND = 0xFF050505;
    private static final int CARD = 0xFF171717;
    private static final int BORDER = 0xFF343434;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3A3A3;
    private static final int PRIMARY = 0xFF2563EB;

    private TextView statusView;
    private TextView diagnosticView;
    private Button activateButton;
    private Button messageAccessButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
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

        TextView orb = text("◎", 36, Color.WHITE, true);
        orb.setGravity(Gravity.CENTER);
        GradientDrawable orbBackground = new GradientDrawable();
        orbBackground.setColor(0xFF202020);
        orbBackground.setShape(GradientDrawable.OVAL);
        orbBackground.setStroke(dp(1), 0xFF505050);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = text("Globo GPT V13 Adaptada", 27, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text("ChatGPT oficial en una burbuja nativa", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "Usa la aplicación oficial instalada. Cuando ChatGPT publica una respuesta, Globo GPT vuelve a mostrar la misma burbuja.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView session = text(
                "La sesión, Plus, historial, voz y actualizaciones siguen perteneciendo a ChatGPT oficial. El icono grande de la burbuja se obtiene de esa aplicación.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams sessionParams = matchWrap();
        sessionParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(session, sessionParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        activateButton = button("Activar burbuja nativa", true, view -> beginActivation());
        root.addView(activateButton, buttonParams());

        messageAccessButton = button(
                "Permitir aparición al recibir mensajes",
                false,
                view -> openNotificationListenerSettings()
        );
        root.addView(messageAccessButton, buttonParams());

        root.addView(button(
                "Permitir burbujas en Android",
                false,
                view -> openBubbleSettings()
        ), buttonParams());
        root.addView(button(
                "Desactivar burbuja",
                false,
                view -> deactivateBubble()
        ), buttonParams());

        diagnosticView = text("", 12, 0xFFFCA5A5, false);
        diagnosticView.setVisibility(View.GONE);
        root.addView(diagnosticView, cardParams());

        TextView help = text(
                "Activa una vez el acceso a notificaciones. Después, una respuesta nueva de ChatGPT vuelve a publicar y expandir la misma burbuja.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        root.addView(help, matchWrap());
        return scroll;
    }

    private void beginActivation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Se requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isOfficialAppInstalled()) {
            Toast.makeText(this, "ChatGPT oficial no está instalado", Toast.LENGTH_LONG).show();
            openChatGptStorePage();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }
        publishBubble();
    }

    private void publishBubble() {
        try {
            AppPreferences.clearLastError(this);
            NativeBubblePublisher.publish(this, true);
            Toast.makeText(this, "Burbuja de ChatGPT publicada", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::updateStatus, 450);
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 900);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(this, "No se pudo publicar la burbuja", error);
            Toast.makeText(this, "Android rechazó la burbuja", Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void deactivateBubble() {
        NativeBubblePublisher.cancel(this);
        Toast.makeText(this, "Burbuja desactivada", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::updateStatus, 180);
    }

    private void updateStatus() {
        if (statusView == null || activateButton == null || messageAccessButton == null) return;

        boolean listenerEnabled = isNotificationListenerEnabled();
        messageAccessButton.setText(listenerEnabled
                ? "Aparición al recibir mensajes: activada"
                : "Permitir aparición al recibir mensajes");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusView.setText("Estado: Android no admite esta burbuja nativa");
            statusView.setTextColor(0xFFFCA5A5);
            activateButton.setEnabled(false);
        } else if (!isOfficialAppInstalled()) {
            statusView.setText("Estado: falta instalar ChatGPT oficial");
            statusView.setTextColor(0xFFFCA5A5);
            activateButton.setText("Instalar ChatGPT oficial");
            activateButton.setEnabled(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Estado: falta permitir notificaciones");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setText("Permitir y activar");
            activateButton.setEnabled(true);
        } else if (NativeBubblePublisher.isExpandedAsBubble(this)) {
            statusView.setText(listenerEnabled
                    ? "Estado: burbuja activa · aparición por mensajes activa"
                    : "Estado: burbuja activa · falta acceso a mensajes");
            statusView.setTextColor(listenerEnabled ? 0xFF34D399 : 0xFFFBBF24);
            activateButton.setText("Volver a publicar burbuja");
            activateButton.setEnabled(true);
        } else if (NativeBubblePublisher.isPosted(this)) {
            statusView.setText(listenerEnabled
                    ? "Estado: conversación publicada · aparición por mensajes activa"
                    : "Estado: conversación publicada · falta acceso a mensajes");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setText("Volver a publicar burbuja");
            activateButton.setEnabled(true);
        } else {
            statusView.setText(listenerEnabled
                    ? "Estado: listo · aparición por mensajes activa"
                    : "Estado: listo · falta permitir aparición por mensajes");
            statusView.setTextColor(listenerEnabled ? 0xFF34D399 : TEXT);
            activateButton.setText("Activar burbuja nativa");
            activateButton.setEnabled(true);
        }

        String diagnostic = AppPreferences.getLastError(this);
        if (diagnostic.isEmpty()) {
            diagnosticView.setVisibility(View.GONE);
        } else {
            diagnosticView.setText("Diagnóstico:\n" + diagnostic);
            diagnosticView.setVisibility(View.VISIBLE);
        }
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
            startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            ));
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

    private boolean isOfficialAppInstalled() {
        return getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.CHATGPT_PACKAGE
        ) != null;
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
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            publishBubble();
        } else {
            Toast.makeText(
                    this,
                    "Sin notificaciones Android no puede crear la burbuja",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
        }
    }

    private LinearLayout card() {
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
