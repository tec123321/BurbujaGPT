package com.leonardo.burbujagpt;

import android.app.Notification;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

/**
 * Vuelve a publicar la burbuja únicamente cuando ChatGPT genera una notificación
 * de respuesta real. No fuerza la expansión si ya existe un globo activo, porque
 * hacerlo durante la transición de apertura provoca que One UI lo minimice.
 */
public final class ChatGptNotificationListenerService extends NotificationListenerService {
    private static final long MIN_PUBLISH_INTERVAL_MS = 2500L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastPublishAt;
    private String lastNotificationKey = "";
    private String lastMessage = "";

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        if (posted == null
                || !NativeBubblePublisher.CHATGPT_PACKAGE.equals(posted.getPackageName())) {
            return;
        }

        Notification notification = posted.getNotification();
        if (!isActualChatMessage(notification)) return;

        String message = NativeBubblePublisher.extractMessage(notification);
        if (TextUtils.isEmpty(message) || "Nueva respuesta de ChatGPT".equals(message)) return;

        long now = System.currentTimeMillis();
        String key = posted.getKey() == null ? "" : posted.getKey();
        boolean duplicate = key.equals(lastNotificationKey) && message.equals(lastMessage);
        if (duplicate || now - lastPublishAt < MIN_PUBLISH_INTERVAL_MS) return;

        lastPublishAt = now;
        lastNotificationKey = key;
        lastMessage = message;

        mainHandler.postDelayed(() -> {
            try {
                android.content.Context context = getApplicationContext();

                // Si el globo ya existe, solo actualiza su notificación. Forzar
                // autoExpand=true mientras se está abriendo hace que One UI lo
                // cierre inmediatamente y obligue a tocarlo varias veces.
                boolean shouldAutoExpand = !NativeBubblePublisher.isPosted(context);
                NativeBubblePublisher.publish(context, shouldAutoExpand, message);
            } catch (RuntimeException | LinkageError error) {
                AppPreferences.recordError(
                        getApplicationContext(),
                        "No se pudo mostrar la burbuja al recibir un mensaje",
                        error
                );
            }
        }, 250L);
    }

    private boolean isActualChatMessage(Notification notification) {
        if (notification == null) return false;

        int flags = notification.flags;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return false;
        }

        Bundle extras = notification.extras;
        if (extras == null) return false;

        CharSequence bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence text = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        android.os.Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);

        boolean hasReadableText = !TextUtils.isEmpty(bigText)
                || !TextUtils.isEmpty(text)
                || (lines != null && lines.length > 0)
                || (messages != null && messages.length > 0);
        if (!hasReadableText) return false;

        String category = notification.category;
        return category == null
                || Notification.CATEGORY_MESSAGE.equals(category)
                || Notification.CATEGORY_SOCIAL.equals(category)
                || Notification.CATEGORY_RECOMMENDATION.equals(category);
    }
}
