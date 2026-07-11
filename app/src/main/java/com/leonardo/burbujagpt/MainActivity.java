package com.leonardo.burbujagpt;

import android.Manifest;
import android.app.Activity;
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
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Lanzador ligero: mantiene la app oficial de ChatGPT instalada y usa una burbuja
 * propia para abrirla o traerla al frente solicitando vista libre/emergente.
 */
public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1101;
    private static final int BACKGROUND = 0xFF050505;
    private static final int CARD = 0xFF171717;
    private static final int BORDER = 0xFF343434;
    private static final int TEXT = 0xFFF5F5F5;
    private static final int MUTED = 0xFFA3A3A3;
    private static final int PRIMARY = 0xFF2563EB;

    private static volatile MainActivity visibleInstance;

    private TextView statusView;
    private Button activateButton;
    private boolean awaitingOverlayPermission;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);
        AppPreferences.setMode(this, AppPreferences.MODE_OFFICIAL);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        visibleInstance = this;
        updateStatus();

        if (awaitingOverlayPermission && canDrawOverlays()) {
            awaitingOverlayPermission = false;
            requestNotificationOrActivate();
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
        root.setPadding(dp(20), dp(28), dp(20), dp(32));
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

        TextView title = makeText("Globo GPT V11", 28, TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = makeText(
                "Burbuja para la aplicación oficial de ChatGPT",
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
                "Usa la aplicación oficial, por lo que conserva tu inicio con Google, Plus, historial, voz y funciones nativas. Al tocar el globo, intenta abrir o recuperar ChatGPT en la vista emergente de Samsung.",
                14,
                TEXT,
                false
        ), matchWrap());
        TextView limit = makeText(
                "Samsung controla el modo de ventana. Si One UI rechaza la vista emergente, ChatGPT se abrirá normalmente; no se modifica ni se clona su APK.",
                12,
                MUTED,
                false
        );
        LinearLayout.LayoutParams limitParams = matchWrap();
        limitParams.setMargins(0, dp(10), 0, 0);
        explanation.addView(limit, limitParams);
        root.addView(explanation, cardParams());

        statusView = makeText("", 14, TEXT, true);
        root.addView(statusView, cardParams());

        activateButton = makeButton("Activar globo", true, v -> beginActivation());
        root.addView(activateButton, buttonParams());
        root.addView(makeButton(
                "Probar ventana emergente ahora",
                false,
                v -> testOfficialPopup()
        ), buttonParams());
        root.addView(makeButton(
                "Desactivar globo",
                false,
                v -> stopBubble()
        ), buttonParams());

        LinearLayout appearance = makeCard();
        appearance.addView(makeText("Aspecto del globo", 17, TEXT, true), matchWrap());

        TextView sizeLabel = makeText(
                "Tamaño: " + AppPreferences.getBubbleSize(this) + " dp",
                13,
                MUTED,
                false
        );
        LinearLayout.LayoutParams labelParams = matchWrap();
        labelParams.setMargins(0, dp(12), 0, 0);
        appearance.addView(sizeLabel, labelParams);

        SeekBar sizeSeek = new SeekBar(this);
        sizeSeek.setMax(36);
        sizeSeek.setProgress(AppPreferences.getBubbleSize(this) - 48);
        sizeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 48 + progress;
                sizeLabel.setText("Tamaño: " + value + " dp");
                if (fromUser) {
                    AppPreferences.setBubbleSize(MainActivity.this, value);
                    refreshBubble();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        appearance.addView(sizeSeek, matchWrap());

        TextView opacityLabel = makeText(
                "Opacidad: " + AppPreferences.getBubbleOpacity(this) + "%",
                13,
                MUTED,
                false
        );
        appearance.addView(opacityLabel, labelParams);

        SeekBar opacitySeek = new SeekBar(this);
        opacitySeek.setMax(55);
        opacitySeek.setProgress(AppPreferences.getBubbleOpacity(this) - 45);
        opacitySeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = 45 + progress;
                opacityLabel.setText("Opacidad: " + value + "%");
                if (fromUser) {
                    AppPreferences.setBubbleOpacity(MainActivity.this, value);
                    refreshBubble();
                }
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        appearance.addView(opacitySeek, matchWrap());
        root.addView(appearance, cardParams());

        TextView help = makeText(
                "Toca el globo para abrir ChatGPT. Mantén pulsado para volver a esta pantalla. Arrástralo hacia la × para apagarlo.",
                12,
                MUTED,
                false
        );
        help.setGravity(Gravity.CENTER);
        root.addView(help, matchWrap());

        return scroll;
    }

    private void beginActivation() {
        AppPreferences.setMode(this, AppPreferences.MODE_OFFICIAL);

        if (!OfficialChatLauncher.isOfficialAppInstalled(this)) {
            Toast.makeText(this, "La aplicación oficial de ChatGPT no está instalada", Toast.LENGTH_LONG).show();
            openChatGptStorePage();
            return;
        }

        if (!canDrawOverlays()) {
            awaitingOverlayPermission = true;
            Intent permission = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(permission);
            return;
        }

        requestNotificationOrActivate();
    }

    private void requestNotificationOrActivate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
            return;
        }
        activateBubble();
    }

    private void activateBubble() {
        AppPreferences.setMode(this, AppPreferences.MODE_OFFICIAL);
        stopService(new Intent(this, BubbleService.class));
        getWindow().getDecorView().postDelayed(() -> {
            Intent service = new Intent(this, BubbleService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
            else startService(service);
            Toast.makeText(this, "Globo GPT activado", Toast.LENGTH_SHORT).show();
            updateStatus();
            getWindow().getDecorView().postDelayed(() -> moveTaskToBack(true), 450);
        }, 180);
    }

    private void stopBubble() {
        stopService(new Intent(this, BubbleService.class));
        Toast.makeText(this, "Globo GPT desactivado", Toast.LENGTH_SHORT).show();
        getWindow().getDecorView().postDelayed(this::updateStatus, 180);
    }

    private void testOfficialPopup() {
        if (!OfficialChatLauncher.isOfficialAppInstalled(this)) {
            Toast.makeText(this, "La aplicación oficial de ChatGPT no está instalada", Toast.LENGTH_LONG).show();
            openChatGptStorePage();
            return;
        }
        moveTaskToBack(true);
        if (!OfficialChatLauncher.openOfficialApp(this, true)) {
            Toast.makeText(this, "Android impidió abrir ChatGPT", Toast.LENGTH_LONG).show();
        }
    }

    private void refreshBubble() {
        if (!BubbleService.isRunning) return;
        Intent refresh = new Intent(this, BubbleService.class);
        refresh.setAction(BubbleService.ACTION_REFRESH);
        startService(refresh);
    }

    private void updateStatus() {
        if (statusView == null || activateButton == null) return;

        boolean installed = OfficialChatLauncher.isOfficialAppInstalled(this);
        boolean overlay = canDrawOverlays();
        boolean active = BubbleService.isRunning;

        if (!installed) {
            statusView.setText("Estado: falta instalar ChatGPT oficial");
            statusView.setTextColor(0xFFFCA5A5);
            activateButton.setText("Instalar ChatGPT oficial");
        } else if (!overlay) {
            statusView.setText("Estado: falta permitir Aparecer encima");
            statusView.setTextColor(0xFFFBBF24);
            activateButton.setText("Permitir globo flotante");
        } else {
            statusView.setText(active
                    ? "Estado: globo activo · modo app oficial"
                    : "Estado: listo · globo apagado");
            statusView.setTextColor(active ? 0xFF34D399 : TEXT);
            activateButton.setText(active ? "Reiniciar globo" : "Activar globo");
        }
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void openChatGptStorePage() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=com.openai.chatgpt")
            ));
        } catch (Exception ignored) {
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
            activateBubble();
        } else {
            Toast.makeText(
                    this,
                    "Sin notificaciones Android puede ocultar el servicio del globo",
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
