package com.leonardo.burbujagpt;

import android.app.Application;
import android.app.UiModeManager;
import android.os.Build;
import android.os.Process;

/** Aplica modo oscuro a toda la aplicación y al esquema de color del WebView. */
public class BurbujaGptApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        installCrashRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
            }
        }
    }

    private void installCrashRecorder() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                AppPreferences.recordUncaughtCrash(this, error);
            } catch (RuntimeException ignored) {
            }
            if (previous != null) {
                previous.uncaughtException(thread, error);
            } else {
                Process.killProcess(Process.myPid());
            }
        });
    }
}
