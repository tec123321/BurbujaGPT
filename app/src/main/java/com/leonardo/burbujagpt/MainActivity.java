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

/** Configura el lanzamiento manual de WhatsApp desde una burbuja nativa. */
public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 3300;
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
        GradientDrawable orbBackground = new GradientDrawable();
        orbBackground.setColor(0xFF25D366);
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(76), dp(76));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = text("Globo WhatsApp V3.3", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text("Apertura manual · sin Shizuku", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "SystemUI recibe una sola pila ya construida: el contenedor de la burbuja y WhatsApp encima. No captura mensajes, no mueve tareas y no ejecuta reintentos.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView warning = text(
                "Cierra primero los globos de V3.1/V3.2. Esta versión elimina sus notificaciones antiguas al activarse.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams warningParams = matchWrap();
        warningParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(warning, warningParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        activateButton = button("Activar globo de WhatsApp", true, view -> beginActivation());
        root.addView(activateButton, buttonParams());
        root.addView(button(
                "Permitir burbujas en Android",
                false,
                view -> openBubbleSettings()
        ), buttonParams());
        root.addView(button(
                "Eliminar todos los globos de esta app",
                false,
                view -> {
                    NativeBubblePublisher.cancel(this);
                    Toast.makeText(this, "Globos eliminados", Toast.LENGTH_SHORT).show();
                    updateStatus();
                }
        ), buttonParams());

        TextView help = text(
                "Después de activarlo, toca el globo cuando quieras. No necesitas recibir mensajes.",
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
        publishBubble();
    }

    private void publishBubble() {
        try {
            AppPreferences.clearLastError(this);
            NativeBubblePublisher.publish(this, true);
            Toast.makeText(this, "Globo publicado", Toast.LENGTH_SHORT).show();
            getWindow().getDecorView().postDelayed(this::updateStatus, 400);
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 850);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(this, "No se pudo publicar la pila de la burbuja", error);
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
            updateStatus();
        }
    }

    private void updateStatus() {
        if (statusView == null || activateButton == null) return;
        if (!isWhatsAppInstalled()) {
            statusView.setText("Estado: falta instalar WhatsApp oficial");
            statusView.setTextColor(0xFFFCA5A5);
            activateButton.setEnabled(true);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Estado: falta permitir notificaciones");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setEnabled(true);
        } else if (NativeBubblePublisher.isExpandedAsBubble(this)) {
            statusView.setText("Estado: globo activo");
            statusView.setTextColor(0xFF34D399);
            activateButton.setEnabled(true);
        } else if (NativeBubblePublisher.isPosted(this)) {
            statusView.setText("Estado: conversación publicada · habilita la burbuja");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setEnabled(true);
        } else {
            statusView.setText("Estado: listo");
            statusView.setTextColor(TEXT);
            activateButton.setEnabled(true);
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
            Toast.makeText(this, "Sin notificaciones Android no puede crear el globo", Toast.LENGTH_LONG).show();
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
