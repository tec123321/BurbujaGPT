package com.leonardo.burbujagpt;

import android.app.Activity;
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
import android.widget.TextView;

/**
 * Raíz segura de la pila de la burbuja. No lanza, mueve ni reintenta actividades.
 * WhatsApp debe llegar encima en la misma operación PendingIntent.getActivities().
 */
public class NativeBubbleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF071A10);
        getWindow().setNavigationBarColor(0xFF071A10);
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
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

        TextView status = text(
                "WhatsApp no quedó encima del contenedor. Cierra este globo antes de volver a probar.",
                14,
                0xFFFCA5A5,
                false
        );
        status.setGravity(Gravity.CENTER);
        root.addView(status, matchWrap());

        Button settings = button("Volver a Globo WhatsApp", view -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
        );
        settingsParams.setMargins(0, dp(20), 0, 0);
        root.addView(settings, settingsParams);

        return root;
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
