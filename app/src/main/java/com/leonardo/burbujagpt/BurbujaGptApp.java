package com.leonardo.burbujagpt;

import android.app.Application;
import android.app.UiModeManager;
import android.os.Build;

/** Aplica modo oscuro a toda la aplicación y al esquema de color del WebView. */
public class BurbujaGptApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
            }
        }
    }
}
