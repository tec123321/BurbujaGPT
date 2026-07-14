package com.leonardo.globowhatsapp;

import android.app.Application;
import android.app.UiModeManager;
import android.os.Build;
import android.os.Process;

/** Mantiene el tema oscuro y conserva un diagnóstico local si falla una burbuja. */
public final class GloboWhatsAppApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        installCrashRecorder();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager manager = getSystemService(UiModeManager.class);
            if (manager != null) manager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
        }
    }

    private void installCrashRecorder() {
        Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                AppPreferences.recordError(this, "Fallo no controlado", error);
            } catch (RuntimeException ignored) {
            }
            if (previous != null) previous.uncaughtException(thread, error);
            else Process.killProcess(Process.myPid());
        });
    }
}
