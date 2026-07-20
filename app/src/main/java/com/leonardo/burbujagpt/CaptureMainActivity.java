package com.leonardo.burbujagpt;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
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

/** Configuración segura basada en el PendingIntent de una notificación real de WhatsApp. */
public class CaptureMainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 3200;
    private static final int BACKGROUND = 0xFF050806;
    private static final int CARD = 0xFF111A15;
    private static final int BORDER = 0xFF2D4435;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3ADA7;
    private static final int PRIMARY = 0xFF128C4A;

    private TextView statusView;
    private Button activateButton;

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
        getWindow().getDecorView().postDelayed(this::updateStatus, 350);
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
        GradientDrawable orbBackground = new GradientDrawable();
        orbBackground.setColor(0xFF25D366);
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = text("Globo WhatsApp V3.2", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text("Burbuja desde una notificación real", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "Esta versión no fuerza tareas ni lanza varias actividades. Captura el acceso exacto de una notificación de WhatsApp y lo entrega directamente a la burbuja de Android.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView limitation = text(
                "Necesita que exista una notificación visible de un chat. No lee ni guarda el mensaje; conserva temporalmente el PendingIntent que abre ese chat.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams limitationParams = matchWrap();
        limitationParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(limitation, limitationParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        root.addView(button(
                "1. Permitir acceso a notificaciones",
                false,
                view -> openNotificationAccess()
        ), buttonParams());

        activateButton = button(
                "2. Crear burbuja del último chat",
                true,
                view -> beginActivation()
        );
        root.addView(activateButton, buttonParams());

        root.addView(button(
                "Permitir burbujas en Android",
                false,
                view -> openBubbleSettings()
        ), buttonParams());

        root.addView(button(
                "Cerrar y limpiar la burbuja",
                false,
                view -> {
                    NativeBubblePublisher.cancel(this);
                    Toast.makeText(this, "Burbuja eliminada", Toast.LENGTH_SHORT).show();
                    updateStatus();
                }
        ), buttonParams());

        TextView steps = text(
                "Uso: activa el acceso, recibe un mensaje de WhatsApp y deja su notificación visible. Regresa aquí y crea la burbuja. Prueba primero con un solo chat.",
                12,
                MUTED,
                false
        );
        steps.setGravity(Gravity.CENTER);
        root.addView(steps, matchWrap());
        return scroll;
    }

    private void beginActivation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Se requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isWhatsAppInstalled()) {
            Toast.makeText(this, "WhatsApp oficial no está instalado", Toast.LENGTH_LONG).show();
            openWhatsAppStorePage();
            return;
        }
        if (!WhatsAppNotificationCaptureService.isAccessEnabled(this)) {
            Toast.makeText(this, "Primero permite el acceso a notificaciones", Toast.LENGTH_LONG).show();
            openNotificationAccess();
            return;
        }
        if (!WhatsAppNotificationCaptureService.hasCapturedNotification()) {
            Toast.makeText(
                    this,
                    "No hay un chat capturado. Recibe un mensaje y deja visible su notificación.",
                    Toast.LENGTH_LONG
            ).show();
            updateStatus();
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
            Toast.makeText(this, "Burbuja del chat publicada", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::updateStatus, 400);
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 850);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(this, "No se pudo publicar el chat capturado", error);
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void updateStatus() {
        if (statusView == null || activateButton == null) return;

        boolean access = WhatsAppNotificationCaptureService.isAccessEnabled(this);
        boolean captured = WhatsAppNotificationCaptureService.hasCapturedNotification();

        if (!access) {
            statusView.setText("Estado: falta acceso a notificaciones");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setEnabled(false);
        } else if (!captured) {
            statusView.setText("Estado: acceso activo · falta una notificación de WhatsApp");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setEnabled(false);
        } else if (NativeBubblePublisher.isExpandedAsBubble(this)) {
            statusView.setText("Estado: burbuja activa para "
                    + WhatsAppNotificationCaptureService.getLatestConversationTitle());
            statusView.setTextColor(0xFF34D399);
            activateButton.setEnabled(true);
        } else {
            statusView.setText("Estado: chat capturado: "
                    + WhatsAppNotificationCaptureService.getLatestConversationTitle());
            statusView.setTextColor(0xFF34D399);
            activateButton.setEnabled(true);
        }
    }

    private void openNotificationAccess() {
        try {
            startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private void openBubbleSettings() {
        try {
            Intent bubbles = new Intent("android.settings.APP_NOTIFICATION_BUBBLE_SETTINGS")
                    .putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
            startActivity(bubbles);
        } catch (ActivityNotFoundException error) {
            Intent notifications = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
            startActivity(notifications);
        }
    }

    private boolean isWhatsAppInstalled() {
        return getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.WHATSAPP_PACKAGE
        ) != null;
    }

    private void openWhatsAppStorePage() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + NativeBubblePublisher.WHATSAPP_PACKAGE)
            ));
        } catch (ActivityNotFoundException ignored) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="
                            + NativeBubblePublisher.WHATSAPP_PACKAGE)
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
            Toast.makeText(this, "Sin notificaciones Android no puede crear la burbuja", Toast.LENGTH_LONG).show();
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
        background.setColor(primary ? PRIMARY : 0xFF1D2921);
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
