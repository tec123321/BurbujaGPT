package com.leonardo.traductorleo;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private static final int REQUEST_CAPTURE = 501;
    private static final int REQUEST_NOTIFICATIONS = 502;

    private TextView statusView;
    private MediaProjectionManager projectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        setContentView(buildUi());
    }

    private LinearLayout buildUi() {
        int padding = dp(22);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Traductor Leo");
        title.setTextSize(30);
        title.setTextColor(Color.rgb(24, 24, 24));
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap(dp(12)));

        TextView description = new TextView(this);
        description.setText(
                "Traduce el texto visible y lo coloca sobre la pantalla sin impedir el desplazamiento. " +
                "El OCR se ejecuta en el teléfono; el texto reconocido se envía a Google para traducirlo al español."
        );
        description.setTextSize(16);
        description.setTextColor(Color.rgb(65, 65, 65));
        description.setLineSpacing(0f, 1.18f);
        root.addView(description, matchWrap(dp(20)));

        statusView = new TextView(this);
        statusView.setTextSize(16);
        statusView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        statusView.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(statusView, matchWrap(dp(18)));

        Button overlayButton = makeButton("1. Permitir mostrar sobre otras aplicaciones");
        overlayButton.setOnClickListener(v -> openOverlaySettings());
        root.addView(overlayButton, matchWrap(dp(10)));

        Button startButton = makeButton("2. Iniciar traducción de pantalla");
        startButton.setOnClickListener(v -> startTranslation());
        root.addView(startButton, matchWrap(dp(10)));

        Button stopButton = makeButton("Detener Traductor Leo");
        stopButton.setOnClickListener(v -> stopTranslation());
        root.addView(stopButton, matchWrap(dp(18)));

        TextView help = new TextView(this);
        help.setText(
                "Uso: abre Discord después de iniciar. Los cuadros son atravesables: puedes deslizar directamente sobre ellos. " +
                "El botón flotante ↻ fuerza una actualización y puede arrastrarse verticalmente."
        );
        help.setTextSize(14);
        help.setTextColor(Color.DKGRAY);
        root.addView(help, matchWrap(0));

        refreshStatus();
        return root;
    }

    private Button makeButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setMinHeight(dp(52));
        return button;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = bottomMargin;
        return params;
    }

    private void openOverlaySettings() {
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void startTranslation() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero concede el permiso para mostrar sobre otras aplicaciones.", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }

        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CAPTURE);
    }

    private void stopTranslation() {
        Intent intent = new Intent(this, TranslatorService.class);
        intent.setAction(TranslatorService.ACTION_STOP);
        startService(intent);
        getSharedPreferences("translator", MODE_PRIVATE).edit().putBoolean("running", false).apply();
        refreshStatus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE) {
            return;
        }
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "No se concedió la captura de pantalla.", Toast.LENGTH_LONG).show();
            return;
        }

        Intent service = new Intent(this, TranslatorService.class);
        service.setAction(TranslatorService.ACTION_START);
        service.putExtra(TranslatorService.EXTRA_RESULT_CODE, resultCode);
        service.putExtra(TranslatorService.EXTRA_RESULT_DATA, data);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(service);
        } else {
            startService(service);
        }
        getSharedPreferences("translator", MODE_PRIVATE).edit().putBoolean("running", true).apply();
        Toast.makeText(this, "Traductor Leo iniciado. Abre Discord.", Toast.LENGTH_LONG).show();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (statusView != null) {
            refreshStatus();
        }
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean running = getSharedPreferences("translator", MODE_PRIVATE).getBoolean("running", false);
        String status = "Superposición: " + (overlay ? "permitida" : "pendiente") +
                "\nServicio: " + (running ? "activo" : "detenido");
        statusView.setText(status);
        statusView.setTextColor(running ? Color.rgb(20, 120, 60) : Color.rgb(130, 55, 40));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
