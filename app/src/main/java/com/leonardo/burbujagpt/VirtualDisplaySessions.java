package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.Surface;

import java.util.HashSet;
import java.util.Set;

/** Conserva el identificador de pantalla virtual de cada globo. */
final class VirtualDisplaySessions {
    private static final String PREFS = "globo_gpt_virtual_displays";
    private static final String KEY_IDS = "session_ids";
    private static final String PREFIX = "display_";

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
        int existing = getDisplayId(context, bubbleId);
        if (existing >= 0) {
            ShizukuDisplayBridge.updateDisplay(
                    existing,
                    width,
                    height,
                    densityDpi,
                    surface,
                    result -> {
                        if (result == 0) {
                            callback.onReady(existing, false);
                        } else {
                            forget(context, bubbleId);
                            create(context, bubbleId, width, height, densityDpi, surface, callback);
                        }
                    }
            );
            return;
        }
        create(context, bubbleId, width, height, densityDpi, surface, callback);
    }

    static void detach(Context context, String bubbleId) {
        int displayId = getDisplayId(context, bubbleId);
        if (displayId >= 0) ShizukuDisplayBridge.detachDisplay(displayId);
    }

    static void release(Context context, String bubbleId) {
        int displayId = getDisplayId(context, bubbleId);
        if (displayId >= 0) ShizukuDisplayBridge.releaseDisplay(displayId);
        forget(context, bubbleId);
    }

    static void releaseAll(Context context) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_IDS, java.util.Collections.emptySet()));
        for (String bubbleId : ids) {
            int displayId = preferences.getInt(PREFIX + bubbleId, -1);
            if (displayId >= 0) ShizukuDisplayBridge.releaseDisplay(displayId);
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (String bubbleId : ids) editor.remove(PREFIX + bubbleId);
        editor.remove(KEY_IDS).apply();
    }

    private static void create(
            Context context,
            String bubbleId,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            Callback callback
    ) {
        ShizukuDisplayBridge.createDisplay(
                "Globo GPT " + bubbleId,
                width,
                height,
                densityDpi,
                surface,
                displayId -> {
                    if (displayId < 0) {
                        callback.onError("One UI rechazó la pantalla virtual");
                        return;
                    }
                    remember(context, bubbleId, displayId);
                    callback.onReady(displayId, true);
                }
        );
    }

    private static int getDisplayId(Context context, String bubbleId) {
        return prefs(context).getInt(PREFIX + bubbleId, -1);
    }

    private static void remember(Context context, String bubbleId, int displayId) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_IDS, java.util.Collections.emptySet()));
        ids.add(bubbleId);
        preferences.edit()
                .putStringSet(KEY_IDS, ids)
                .putInt(PREFIX + bubbleId, displayId)
                .apply();
    }

    private static void forget(Context context, String bubbleId) {
        SharedPreferences preferences = prefs(context);
        Set<String> ids = new HashSet<>(preferences.getStringSet(KEY_IDS, java.util.Collections.emptySet()));
        ids.remove(bubbleId);
        preferences.edit()
                .putStringSet(KEY_IDS, ids)
                .remove(PREFIX + bubbleId)
                .apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
