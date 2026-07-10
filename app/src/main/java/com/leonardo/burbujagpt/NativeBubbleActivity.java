package com.leonardo.burbujagpt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

/** Actividad redimensionable que Android muestra dentro de su burbuja nativa. */
public class NativeBubbleActivity extends ChatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
        } catch (RuntimeException error) {
            AppPreferences.recordNativeError(this, "ventana", error);
            AppPreferences.setNativeFallbackRequired(this, true);
            try {
                finish();
                Intent fallback = new Intent(this, ChatActivity.class);
                fallback.putExtra(ChatActivity.EXTRA_SAFE_WEBVIEW, true);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(fallback);
                Toast.makeText(
                        this,
                        "La ventana nativa fallo; abri el panel compatible.",
                        Toast.LENGTH_LONG
                ).show();
            } catch (RuntimeException ignored) {
            }
        }
    }
}
