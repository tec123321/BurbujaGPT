package com.leonardo.globowhatsapp;

import android.content.Context;
import android.hardware.display.VirtualDisplay;
import android.view.Surface;

import java.util.Map;
import java.util.HashMap;

/** Conserva una única pantalla virtual privilegiada aunque el globo se minimice. */
final class VirtualDisplaySessions {
    private static final Map<String, Session> DISPLAYS = new HashMap<>();

    interface Callback {
        void onReady(int displayId, boolean newlyCreated);
        void onError(String message);
    }

    private VirtualDisplaySessions() {
    }

    static synchronized void attach(
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

        Session existing = DISPLAYS.get(bubbleId);
        if (existing != null
                && existing.display != null
                && existing.display.getDisplay() != null) {
            try {
                existing.display.resize(
                        Math.max(320, width),
                        Math.max(480, height),
                        Math.max(160, densityDpi)
                );
                existing.display.setSurface(surface);
                existing.surface = surface;
                callback.onReady(existing.display.getDisplay().getDisplayId(), false);
                return;
            } catch (RuntimeException error) {
                release(context, bubbleId);
            }
        }

        try {
            VirtualDisplay display = ShellDisplayCreator.create(
                    context,
                    "Globo WhatsApp " + bubbleId,
                    width,
                    height,
                    densityDpi,
                    surface
            );
            DISPLAYS.put(bubbleId, new Session(display, surface));
            callback.onReady(display.getDisplay().getDisplayId(), true);
        } catch (Throwable error) {
            String detail = error.getMessage();
            if (detail == null || detail.trim().isEmpty()) {
                detail = error.getClass().getSimpleName();
            }
            AppPreferences.recordMessage(
                    context,
                    "Motor V2 no pudo crear el display confiable: " + detail
            );
            callback.onError("No se pudo crear la pantalla virtual privilegiada");
        }
    }

    static synchronized void detach(
            Context context,
            String bubbleId,
            Surface expectedSurface
    ) {
        Session session = DISPLAYS.get(bubbleId);
        if (session == null || session.surface != expectedSurface) return;
        try {
            session.display.setSurface(null);
            session.surface = null;
        } catch (RuntimeException ignored) {
        }
    }

    static synchronized void release(Context context, String bubbleId) {
        Session session = DISPLAYS.remove(bubbleId);
        if (session == null) return;
        try {
            session.display.release();
        } catch (RuntimeException ignored) {
        }
    }

    static synchronized void releaseAll(Context context) {
        for (Session session : DISPLAYS.values()) {
            try {
                session.display.release();
            } catch (RuntimeException ignored) {
            }
        }
        DISPLAYS.clear();
    }

    private static final class Session {
        final VirtualDisplay display;
        Surface surface;

        Session(VirtualDisplay display, Surface surface) {
            this.display = display;
            this.surface = surface;
        }
    }
}
