package com.leonardo.globowhatsapp;

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

/** Configura el globo manual y los globos derivados de mensajes de WhatsApp. */
public final class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 2300;
    private static final int BACKGROUND = 0xFF050806;
    private static final int CARD = 0xFF132017;
    private static final int BORDER = 0xFF294332;
    private static final int TEXT = 0xFFF3F7F4;
    private static final int MUTED = 0xFFA8B5AC;
    private static final int PRIMARY = 0xFF16884C;

    private TextView statusView;
    private TextView diagnosticView;
    private Button manualButton;
    private Button messageAccessButton;
    private boolean pendingManualBubble;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07110C);
        getWindow().setNavigationBarColor(0xFF07110C);
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

        TextView orb = text("☎", 34, Color.WHITE, true);
        orb.setGravity(Gravity.CENTER);
        GradientDrawable orbBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF128C7E, 0xFF25D366}
        );
        orbBackground.setShape(GradientDrawable.OVAL);
        orbBackground.setStroke(dp(1), 0xFF70E79B);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = text("Globo WhatsApp V1.2", 27, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text(
                "Conversaciones de WhatsApp en burbujas nativas",
                15,
                MUTED,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "Cada mensaje crea o actualiza un globo. Al abrirlo, se inicia la aplicación oficial de WhatsApp dentro de la misma tarea de la burbuja.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView compatibility = text(
                "V1.2 usa el mismo mecanismo de Globo GPT: elimina NEW_TASK y reutiliza la tarea creada por Android para la burbuja. No usa WebView ni una ventana múltiple de Samsung.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams compatibilityParams = matchWrap();
        compatibilityParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(compatibility, compatibilityParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        messageAccessButton = button(
                "Activar globos de mensajes",
                true,
                view -> beginMessageAccessActivation()
        );
        root.addView(messageAccessButton, buttonParams());

        manualButton = button(
                "Crear globo de WhatsApp",
                false,
                view -> beginManualActivation()
        );
        root.addView(manualButton, buttonParams());

        root.addView(button(
                "Permitir burbujas en Android",
                false,
                view -> openBubbleSettings()
        ), buttonParams());

        root.addView(button(
                "Eliminar todos los globos",
                false,
                view -> deactivateAllBubbles()
        ), buttonParams());

        LinearLayout privacy = card();
        privacy.addView(text("Privacidad", 14, TEXT, true), matchWrap());
        TextView privacyBody = text(
                "Android mostrará un aviso amplio de acceso a notificaciones. El permiso técnicamente permite ver todas, pero esta APK solo procesa com.whatsapp. No guarda nombres ni mensajes y no tiene permiso de Internet.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams privacyParams = matchWrap();
        privacyParams.setMargins(0, dp(8), 0, 0);
        privacy.addView(privacyBody, privacyParams);
        root.addView(privacy, cardParams());

        diagnosticView = text("", 12, 0xFFFCA5A5, false);
        diagnosticView.setVisibility(View.GONE);
        root.addView(diagnosticView, cardParams());

        TextView help = text(
                "Después de activar el acceso, permite “Todas las conversaciones” en los ajustes de burbujas de Globo WhatsApp.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        root.addView(help, matchWrap());
        return scroll;
    }

    private void beginManualActivation() {
        pendingManualBubble = true;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Se requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, "WhatsApp oficial no está instalado", Toast.LENGTH_LONG).show();
            openWhatsAppStorePage();
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

        publishManualBubble();
    }

    private void beginMessageAccessActivation() {
        pendingManualBubble = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }
        openNotificationListenerSettings();
    }

    private void publishManualBubble() {
        try {
            AppPreferences.clearLastError(this);
            WhatsAppBubblePublisher.publishManual(this, true);
            Toast.makeText(this, "Globo de WhatsApp creado", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::updateStatus, 350L);
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 800L);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(this, "No se pudo publicar la burbuja", error);
            Toast.makeText(this, "Android rechazó la burbuja", Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void deactivateAllBubbles() {
        WhatsAppBubblePublisher.cancelAll(this);
        Toast.makeText(this, "Todos los globos fueron eliminados", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::updateStatus, 180L);
    }

    private void updateStatus() {
        if (statusView == null || manualButton == null || messageAccessButton == null) return;

        boolean listenerEnabled = isNotificationListenerEnabled();
        int activeBubbles = WhatsAppBubblePublisher.getActiveBubbleCount(this);

        messageAccessButton.setText(listenerEnabled
                ? "Globos de mensajes: activados"
                : "Activar globos de mensajes");

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusView.setText("Estado: Android no admite estas burbujas nativas");
            statusView.setTextColor(0xFFFCA5A5);
            manualButton.setEnabled(false);
            messageAccessButton.setEnabled(false);
        } else if (!isWhatsAppInstalled()) {
            statusView.setText("Estado: falta instalar WhatsApp oficial");
            statusView.setTextColor(0xFFFCA5A5);
            manualButton.setText("Instalar WhatsApp oficial");
            manualButton.setEnabled(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Estado: falta permitir notificaciones");
            statusView.setTextColor(0xFFFBBF24);
            manualButton.setText("Permitir y crear globo de WhatsApp");
            manualButton.setEnabled(true);
        } else {
            String bubbleLabel = activeBubbles == 1
                    ? "1 globo publicado"
                    : activeBubbles + " globos publicados";
            String listenerLabel = listenerEnabled
                    ? "mensajes automáticos activos"
                    : "falta acceso a mensajes";
            statusView.setText("Estado: " + bubbleLabel + " · " + listenerLabel);
            statusView.setTextColor(listenerEnabled ? 0xFF34D399 : 0xFFFBBF24);
            manualButton.setText("Crear globo de WhatsApp");
            manualButton.setEnabled(true);
            messageAccessButton.setEnabled(true);
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
                WhatsAppNotificationListenerService.class
        );
        return enabled != null && enabled.contains(component.flattenToString());
    }

    private void openNotificationListenerSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException error) {
            openApplicationDetails();
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
                openApplicationDetails();
            }
        }
    }

    private void openApplicationDetails() {
        startActivity(new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        ));
    }

    private boolean isWhatsAppInstalled() {
        return getPackageManager().getLaunchIntentForPackage(
                WhatsAppBubblePublisher.WHATSAPP_PACKAGE
        ) != null;
    }

    private void openWhatsAppStorePage() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + WhatsAppBubblePublisher.WHATSAPP_PACKAGE)
            ));
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="
                            + WhatsAppBubblePublisher.WHATSAPP_PACKAGE)
            ));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (pendingManualBubble) publishManualBubble();
            else openNotificationListenerSettings();
        } else {
            Toast.makeText(
                    this,
                    "Sin notificaciones Android no puede crear las burbujas",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
        }
        pendingManualBubble = false;
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
        background.setColor(primary ? PRIMARY : 0xFF1B2A20);
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
