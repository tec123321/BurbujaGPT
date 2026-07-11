package com.leonardo.burbujagpt;

import android.app.Activity;
import android.app.Application;
import android.app.UiModeManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Aplica modo oscuro, registra fallos y actualiza la interfaz de Globo GPT V12. */
public class BurbujaGptApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        installCrashRecorder();
        installV12Branding();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES);
            }
        }
    }

    private void installV12Branding() {
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity instanceof MainActivity) {
                    View root = activity.getWindow().getDecorView();
                    root.post(() -> updateTexts(root));
                }
            }

            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
    }

    private void updateTexts(View view) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            String text = String.valueOf(textView.getText());
            if ("Globo GPT V11".equals(text)) {
                textView.setText("Globo GPT V12 · Shizuku");
            } else if ("Burbuja para la aplicación oficial de ChatGPT".equals(text)) {
                textView.setText("Burbuja para ChatGPT oficial mediante Shizuku");
            } else if (text.startsWith("Usa la aplicación oficial, por lo que conserva")) {
                textView.setText("Usa la aplicación oficial y conserva Google Login, Plus, historial, voz y funciones nativas. Shizuku solicita a Android abrir ChatGPT en una ventana emergente real.");
            } else if (text.startsWith("Samsung controla el modo de ventana")) {
                textView.setText("Shizuku debe estar iniciado y Globo GPT debe tener autorización. One UI aún puede restringir el modo emergente en algunas configuraciones.");
            } else if (text.contains("modo app oficial")) {
                textView.setText(text.replace("modo app oficial", "modo Shizuku"));
            }
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                updateTexts(group.getChildAt(i));
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
