package com.leonardo.globowhatsapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
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
 * Actividad anfitriona que Android coloca en la burbuja. Intenta iniciar el
 * PendingIntent original de WhatsApp desde esa misma tarea y usa la app general como respaldo.
 */
public final class NativeBubbleActivity extends Activity {
    private static final String STATE_ATTEMPTED = "attempted";

    private LinearLayout root;
    private TextView titleView;
    private TextView status;
    private ProgressBar progress;
    private boolean attempted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07110C);
        getWindow().setNavigationBarColor(0xFF07110C);
        setContentView(buildUi());

        attempted = savedInstanceState != null && savedInstanceState.getBoolean(STATE_ATTEMPTED);
        updateConversationTitle();
        if (!attempted) root.postDelayed(this::launchWhatsAppInsideBubble, 100L);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        attempted = false;
        updateConversationTitle();
        showLoading("Abriendo la conversación…");
        root.postDelayed(this::launchWhatsAppInsideBubble, 70L);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putBoolean(STATE_ATTEMPTED, attempted);
        super.onSaveInstanceState(state);
    }

    private void launchWhatsAppInsideBubble() {
        if (attempted || isFinishing()) return;
        attempted = true;

        PendingIntent conversation = WhatsAppBubblePublisher.getTargetPendingIntent(getIntent());
        if (conversation != null) {
            try {
                startIntentSender(conversation.getIntentSender(), null, 0, 0, 0);
                overridePendingTransition(0, 0);
                AppPreferences.clearLastError(this);
                status.setText("Conversación abierta con WhatsApp oficial.");
                return;
            } catch (IntentSender.SendIntentException
                     | SecurityException
                     | IllegalArgumentException error) {
                AppPreferences.recordError(
                        this,
                        "El acceso a la conversación dejó de ser válido",
                        error
                );
            }
        }

        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                WhatsAppBubblePublisher.WHATSAPP_PACKAGE
        );
        if (launcher == null || launcher.getComponent() == null) {
            showError("WhatsApp oficial no está instalado o Android no permite localizarlo.");
            return;
        }

        Intent target = new Intent(launcher);
        target.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_NO_ANIMATION);

        try {
            startActivity(target);
            overridePendingTransition(0, 0);
            AppPreferences.clearLastError(this);
            status.setText("WhatsApp se abrió desde la tarea de la burbuja.");
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException error) {
            AppPreferences.recordError(this, "Android rechazó abrir WhatsApp", error);
            showError("Android rechazó abrir WhatsApp desde esta burbuja.");
        }
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(24), dp(28), dp(24), dp(28));
        root.setBackgroundColor(0xFF101A14);

        TextView icon = text("☎", 33, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF128C7E, 0xFF25D366}
        );
        iconBackground.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBackground);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(68), dp(68));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(icon, iconParams);

        titleView = text("WhatsApp", 22, 0xFFF5F5F5, true);
        titleView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.setMargins(0, dp(16), 0, dp(8));
        root.addView(titleView, titleParams);

        status = text("Abriendo la aplicación oficial…", 14, 0xFFB9C6BE, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.setMargins(0, dp(18), 0, 0);
        root.addView(progress, progressParams);
        return root;
    }

    private void updateConversationTitle() {
        if (titleView != null) {
            titleView.setText(WhatsAppBubblePublisher.getConversationTitle(getIntent()));
        }
    }

    private void showLoading(String message) {
        status.setText(message);
        status.setTextColor(0xFFB9C6BE);
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
            root.postDelayed(this::launchWhatsAppInsideBubble, 90L);
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
        background.setColor(0xFF1E3928);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFF31553D);
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
