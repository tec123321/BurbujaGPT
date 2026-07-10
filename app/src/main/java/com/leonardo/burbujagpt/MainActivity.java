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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;

    private TextView statusView;
    private Button permissionButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        scroll.setBackgroundColor(0xFFF4F4F5);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(30));
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
                new int[]{0xFF5B5CE2, 0xFF0EA5E9, 0xFF10B981}
        );
        orbBackground.setShape(GradientDrawable.OVAL);
        orb.setBackground(orbBackground);
        LinearLayout.LayoutParams orbParams = new LinearLayout.LayoutParams(dp(72), dp(72));
        orbParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(orb, orbParams);

        TextView title = makeText("BurbujaGPT V3", 28, 0xFF111827, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(14), 0, 0);
        root.addView(title, titleParams);

        TextView subtitle = makeText(
                "Tus chats reales de ChatGPT dentro de una ventana flotante",
                17,
                0xFF4B5563,
                false
        );
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.setMargins(0, dp(8), 0, dp(22));
        root.addView(subtitle, subtitleParams);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(Color.WHITE);
        cardBackground.setCornerRadius(dp(16));
        cardBackground.setStroke(dp(1), 0x1A111827);
        card.setBackground(cardBackground);

        card.addView(makeText(
                "No usa una clave API. El panel carga chatgpt.com directamente: al iniciar sesión aparecen el historial, la búsqueda, tus GPTs y los chats nuevos.",
                15,
                0xFF1F2937,
                false
        ), matchWrap());

        TextView loginNote = makeText(
                "La sesión queda guardada solo en el WebView del teléfono. La app no inyecta código ni lee tu contraseña. Si Google rechaza el inicio dentro del panel, usa correo y contraseña de OpenAI.",
                13,
                0xFF6B7280,
                false
        );
        LinearLayout.LayoutParams loginParams = matchWrap();
        loginParams.setMargins(0, dp(10), 0, 0);
        card.addView(loginNote, loginParams);
        root.addView(card, matchWrap());

        statusView = makeText("", 14, 0xFF374151, true);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.setMargins(0, dp(18), 0, dp(8));
        root.addView(statusView, statusParams);

        permissionButton = makeButton("1. Permitir globo flotante", true, v -> requestOverlayPermission());
        root.addView(permissionButton, buttonParams());

        root.addView(makeButton("2. Activar globo", true, v -> startBubble()), buttonParams());
        root.addView(makeButton("Abrir ventana ahora", false, v -> openFloatingChat()), buttonParams());
        root.addView(makeButton("Abrir la app oficial", false, v -> openOfficialChatGpt()), buttonParams());
        root.addView(makeButton("Desactivar globo", false, v -> {
            stopService(new Intent(this, BubbleService.class));
            Toast.makeText(this, "Globo desactivado", Toast.LENGTH_SHORT).show();
        }), buttonParams());

        TextView footer = makeText(
                "Esta es una envoltura independiente y no una función oficial de OpenAI. Si cambia el inicio de sesión o el sitio web, el panel puede necesitar una actualización.",
                12,
                0xFF6B7280,
                false
        );
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = matchWrap();
        footerParams.setMargins(0, dp(18), 0, 0);
        root.addView(footer, footerParams);

        return scroll;
    }

    private void updateStatus() {
        boolean overlayGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || Settings.canDrawOverlays(this);
        if (statusView != null) {
            statusView.setText(overlayGranted
                    ? "Estado: permiso de globo concedido"
                    : "Estado: falta permitir Aparecer encima");
            statusView.setTextColor(overlayGranted ? 0xFF047857 : 0xFFB45309);
        }
        if (permissionButton != null) {
            permissionButton.setText(overlayGranted
                    ? "1. Permiso concedido"
                    : "1. Permitir globo flotante");
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            requestOverlayPermission();
            return;
        }

        requestNotificationPermissionIfNeeded();

        Intent service = new Intent(this, BubbleService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service);
        else startService(service);
        Toast.makeText(this, "Globo activado", Toast.LENGTH_SHORT).show();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void openFloatingChat() {
        startActivity(new Intent(this, ChatActivity.class));
    }

    private void openOfficialChatGpt() {
        Intent launch = getPackageManager().getLaunchIntentForPackage("com.openai.chatgpt");
        if (launch != null) {
            startActivity(launch);
            return;
        }
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com/")));
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

    private Button makeButton(String text, boolean primary, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : 0xFF111827);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setOnClickListener(listener);

        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? 0xFF2563EB : Color.WHITE);
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), primary ? 0xFF2563EB : 0x22111827);
        button.setBackground(background);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        );
        params.setMargins(0, dp(6), 0, dp(6));
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
