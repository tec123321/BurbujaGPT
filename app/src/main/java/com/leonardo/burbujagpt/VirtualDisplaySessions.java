package com.leonardo.burbujagpt;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Conserva una pantalla virtual privilegiada por cada globo. */
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

        try {
            VirtualDisplay display = ShellDisplayCreator.create(
                    context,
                    "Globo GPT " + bubbleId,
                    width,
                    height,
                    densityDpi,
                    surface
            );
            DISPLAYS.put(bubbleId, display);
            callback.onReady(display.getDisplay().getDisplayId(), true);
        } catch (Throwable error) {
            String detail = error.getMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = error.getClass().getSimpleName();
            }
            AppPreferences.recordMessage(
                    context,
                    "Motor V16.1 no pudo crear el display confiable: " + detail
            );
            callback.onError("No se pudo crear la pantalla virtual privilegiada");
        }
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
}
