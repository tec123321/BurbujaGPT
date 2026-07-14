package com.leonardo.burbujagpt;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.view.Display;
import android.view.Surface;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mantiene las pantallas virtuales en el proceso normal de Globo GPT. Esta vía
 * evita que One UI rechace la creación cuando se intenta desde el proceso shell
 * de Shizuku. Shizuku se usa solamente para abrir ChatGPT e inyectar eventos.
 */
final class VirtualDisplaySessions {
    private static final Map<String, VirtualDisplay> DISPLAYS = new ConcurrentHashMap<>();

    interface Callback {
        void onReady(int displayId, boolean newlyCreated);
        void onError(String message);
    }

    private VirtualDisplaySessions() {
    }

    static void attach(
            Context context,
            String bubbleId,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            Callback callback
    ) {
        if (surface == null || !surface.isValid()) {
            callback.onError("La superficie del globo todavía no está lista");
            return;
        }

        VirtualDisplay existing = DISPLAYS.get(bubbleId);
        if (existing != null && existing.getDisplay() != null) {
            try {
                existing.resize(
                        Math.max(320, width),
                        Math.max(480, height),
                        Math.max(160, densityDpi)
                );
                existing.setSurface(surface);
                callback.onReady(existing.getDisplay().getDisplayId(), false);
                return;
            } catch (RuntimeException error) {
                release(context, bubbleId);
            }
        }

        DisplayManager manager = context.getSystemService(DisplayManager.class);
        if (manager == null) {
            callback.onError("Android no entregó el administrador de pantallas");
            return;
        }

        int safeWidth = Math.max(320, width);
        int safeHeight = Math.max(480, height);
        int safeDensity = Math.max(160, densityDpi);

        /*
         * PUBLIC permite que ChatGPT, que tiene otro UID, abra ventanas en el
         * display. OWN_CONTENT_ONLY evita la duplicación de la pantalla principal
         * y, a diferencia de los flags TRUSTED/SUPPORTS_TOUCH, no requiere un
         * permiso reservado del sistema.
         */
        int[] attempts = new int[]{
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY,
                0
        };

        Throwable lastError = null;
        for (int flags : attempts) {
            try {
                VirtualDisplay display = createDisplay(
                        manager,
                        "Globo GPT " + bubbleId,
                        safeWidth,
                        safeHeight,
                        safeDensity,
                        surface,
                        flags
                );
                if (display == null || display.getDisplay() == null) continue;

                DISPLAYS.put(bubbleId, display);
                callback.onReady(display.getDisplay().getDisplayId(), true);
                return;
            } catch (Throwable error) {
                lastError = error;
            }
        }

        String detail = lastError == null
                ? "Android devolvió una pantalla nula"
                : lastError.getClass().getSimpleName();
        AppPreferences.recordMessage(
                context,
                "No se pudo crear el display público: " + detail
        );
        callback.onError("One UI rechazó todos los modos de pantalla virtual");
    }

    static void detach(Context context, String bubbleId) {
        VirtualDisplay display = DISPLAYS.get(bubbleId);
        if (display == null) return;
        try {
            display.setSurface(null);
        } catch (RuntimeException ignored) {
        }
    }

    static void release(Context context, String bubbleId) {
        VirtualDisplay display = DISPLAYS.remove(bubbleId);
        if (display == null) return;
        try {
            display.release();
        } catch (RuntimeException ignored) {
        }
    }

    static void releaseAll(Context context) {
        for (VirtualDisplay display : DISPLAYS.values()) {
            try {
                display.release();
            } catch (RuntimeException ignored) {
            }
        }
        DISPLAYS.clear();
    }

    private static VirtualDisplay createDisplay(
            DisplayManager manager,
            String name,
            int width,
            int height,
            int density,
            Surface surface,
            int flags
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                    name,
                    width,
                    height,
                    density
            )
                    .setFlags(flags)
                    .setSurface(surface);

            Display physical = manager.getDisplay(Display.DEFAULT_DISPLAY);
            if (physical != null && physical.getRefreshRate() > 0f) {
                builder.setRequestedRefreshRate(physical.getRefreshRate());
            }
            return manager.createVirtualDisplay(builder.build());
        }
        return manager.createVirtualDisplay(name, width, height, density, surface, flags);
    }
}
