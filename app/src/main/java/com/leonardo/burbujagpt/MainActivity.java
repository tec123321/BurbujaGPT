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

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1400;
    private static final int BACKGROUND = 0xFF050505;
    private static final int CARD = 0xFF171717;
    private static final int BORDER = 0xFF343434;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3A3A3;
    private static final int PRIMARY = 0xFF10A37F;

    private TextView statusView;
    private TextView diagnosticView;
    private Button autoButton;
    private Runnable pendingAfterPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        NativeBubblePublisher.cancelLegacy(this);
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

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_gpt_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(78), dp(78));
        logoParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(logo, logoParams);

        TextView title = text("Globo GPT", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = text("Varios chats y activación por notificaciones", 15, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(6), 0, dp(18));
        root.addView(subtitle, subtitleParams);

        LinearLayout explanation = card();
        explanation.addView(text(
                "Crea globos manualmente o automáticamente cuando ChatGPT oficial publique una notificación. Cada conversación usa su propio globo y muestra el número de avisos pendientes.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView licenseNote = text(
                "La aplicación rechaza copias modificadas de ChatGPT para evitar el error de licencia que aparece después de varios minutos.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams noteParams = matchWrap();
        noteParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(licenseNote, noteParams);
        root.addView(explanation, cardParams());

        statusView = text("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        root.addView(button("Crear un globo de chat", true, view -> createManualBubble()), buttonParams());

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
                "Reinstalar ChatGPT oficial",
                false,
                view -> openChatGptStorePage()
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
                "La activación automática requiere habilitar una sola vez el acceso a notificaciones. ChatGPT oficial también debe tener sus notificaciones activadas.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        root.addView(help, matchWrap());
        return scroll;
    }

    private void createManualBubble() {
        if (!canUseBubbles()) return;
        runWithNotificationPermission(() -> {
            try {
                AppPreferences.clearLastError(this);
                BubbleRecord record = NativeBubblePublisher.publishManual(this, true);
                Toast.makeText(this, record.title + " creado", Toast.LENGTH_SHORT).show();
                getWindow().getDecorView().postDelayed(this::updateStatus, 350);
                getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 750);
            } catch (RuntimeException | LinkageError error) {
                AppPreferences.recordError(this, "No se pudo crear el globo manual", error);
                Toast.makeText(this, "Android rechazó el globo", Toast.LENGTH_LONG).show();
                updateStatus();
            }
        });
    }

    private void toggleAutomaticBubbles() {
        if (AppPreferences.isAutoBubblesEnabled(this)) {
            AppPreferences.setAutoBubblesEnabled(this, false);
            Toast.makeText(this, "Globos automáticos desactivados", Toast.LENGTH_SHORT).show();
            updateStatus();
            return;
        }
        if (!canUseBubbles()) return;

        runWithNotificationPermission(() -> {
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
        });
    }

    private boolean canUseBubbles() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Toast.makeText(this, "Se requiere Android 11 o posterior", Toast.LENGTH_LONG).show();
            return false;
        }

        OfficialChatGptVerifier.Result verification = OfficialChatGptVerifier.verify(this);
        if (!verification.valid) {
            AppPreferences.recordMessage(this, verification.message);
            Toast.makeText(this, verification.message, Toast.LENGTH_LONG).show();
            openChatGptStorePage();
            updateStatus();
            return false;
        }
        return true;
    }

    private void runWithNotificationPermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAfterPermission = action;
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
        Toast.makeText(this, "Todos los globos fueron eliminados", Toast.LENGTH_SHORT).show();
        updateStatus();
    }

    private void updateStatus() {
        if (statusView == null || autoButton == null) return;

        OfficialChatGptVerifier.Result verification = OfficialChatGptVerifier.verify(this);
        boolean listenerEnabled = isNotificationListenerEnabled();
        boolean autoEnabled = AppPreferences.isAutoBubblesEnabled(this);
        int bubbleCount = NativeBubblePublisher.activeBubbleCount(this);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            statusView.setText("Estado: Android no admite burbujas nativas");
            statusView.setTextColor(0xFFFCA5A5);
        } else if (!verification.valid) {
            statusView.setText("Estado: " + verification.message);
            statusView.setTextColor(0xFFFCA5A5);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            statusView.setText("Estado: falta permitir notificaciones de Globo GPT");
            statusView.setTextColor(0xFFFBBF24);
        } else {
            String autoState;
            if (!autoEnabled) {
                autoState = "automático desactivado";
            } else if (!listenerEnabled) {
                autoState = "automático pendiente de permiso";
            } else {
                autoState = "automático activo";
            }
            statusView.setText(
                    "Estado: ChatGPT oficial verificado · "
                            + bubbleCount + (bubbleCount == 1 ? " globo" : " globos")
                            + " · " + autoState
            );
            statusView.setTextColor(
                    autoEnabled && !listenerEnabled ? 0xFFFBBF24 : 0xFF34D399
            );
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
            Toast.makeText(this, "Android no expuso el ajuste de notificaciones", Toast.LENGTH_LONG).show();
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

        Runnable action = pendingAfterPermission;
        pendingAfterPermission = null;
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
