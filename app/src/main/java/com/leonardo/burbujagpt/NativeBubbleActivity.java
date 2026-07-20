package com.leonardo.burbujagpt;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * Actividad anfitriona que Android coloca en la burbuja. Reproduce el mismo
 * patrón de Globo GPT V13 y, desde esa tarea, inicia WhatsApp oficial sin
 * FLAG_ACTIVITY_NEW_TASK, WebView, Shizuku ni superposición.
 */
public class NativeBubbleActivity extends Activity {
    private static final String STATE_ATTEMPTED = "attempted";

    private LinearLayout root;
    private TextView status;
    private ProgressBar progress;
    private boolean attempted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF071A10);
        getWindow().setNavigationBarColor(0xFF071A10);
        setContentView(buildUi());

        attempted = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ATTEMPTED);
        if (!attempted) root.postDelayed(this::launchOfficialInsideBubble, 120);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        attempted = false;
        showLoading("Abriendo WhatsApp…");
        root.postDelayed(this::launchOfficialInsideBubble, 80);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STATE_ATTEMPTED, attempted);
        super.onSaveInstanceState(state);
    }

    private void launchOfficialInsideBubble() {
        if (attempted || isFinishing()) return;
        attempted = true;

        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.WHATSAPP_PACKAGE
        );
        if (launcher == null || launcher.getComponent() == null) {
            showError("WhatsApp oficial no está instalado o Android no permite localizarlo.");
            return;
        }

        Intent target = new Intent(launcher);
        // setFlags reemplaza el NEW_TASK que suele traer el intent launcher.
        // Son exactamente las banderas usadas por Globo GPT V13.
        target.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            startActivity(target);
            overridePendingTransition(0, 0);
            AppPreferences.clearLastError(this);
            status.setText("WhatsApp se inició desde la tarea de la burbuja.");
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException error) {
            AppPreferences.recordError(this, "Android rechazó abrir WhatsApp en la burbuja", error);
            showError("Android rechazó insertar la actividad oficial en esta burbuja.");
        }
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(0xFF0B1510);

        TextView icon = text("☎", 32, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable();
        iconBackground.setColor(0xFF25D366);
        iconBackground.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBackground);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        TextView title = text("WhatsApp", 22, 0xFFF5F5F5, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(title, titleParams);

        status = text("Abriendo la aplicación oficial…", 14, 0xFFB4B4B8, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(18), 0, 0);
        root.addView(progress, progressParams);

        return root;
    }

    private void showLoading(String message) {
        status.setText(message);
        status.setTextColor(0xFFB4B4B8);
        progress.setVisibility(View.VISIBLE);
        removeActionButtons();
    }

    private void showError(String message) {
        status.setText(message);
        status.setTextColor(0xFFFCA5A5);
        progress.setVisibility(View.GONE);
        removeActionButtons();

        Button retry = button("Reintentar", view -> {
            attempted = false;
            showLoading("Reintentando…");
            root.postDelayed(this::launchOfficialInsideBubble, 100);
        });
        retry.setTag("bubble_action");
        LinearLayout.LayoutParams retryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        retryParams.setMargins(0, dp(20), 0, 0);
        root.addView(retry, retryParams);

        Button settings = button("Volver a Globo WhatsApp", view -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        settings.setTag("bubble_action");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        settingsParams.setMargins(0, dp(10), 0, 0);
        root.addView(settings, settingsParams);
    }

    private void removeActionButtons() {
        for (int index = root.getChildCount() - 1; index >= 0; index--) {
            if ("bubble_action".equals(root.getChildAt(index).getTag())) {
                root.removeViewAt(index);
            }
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button button(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setOnClickListener(listener);
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xFF1F3A2A);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFF315B43);
        button.setBackground(background);
        return button;
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
