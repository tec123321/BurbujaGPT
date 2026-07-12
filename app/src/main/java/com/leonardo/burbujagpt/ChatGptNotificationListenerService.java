package com.leonardo.burbujagpt;

import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Vuelve a publicar la misma burbuja cuando ChatGPT genera una notificación. */
public final class ChatGptNotificationListenerService extends NotificationListenerService {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private long lastPublishAt;

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        if (notification == null
                || !NativeBubblePublisher.CHATGPT_PACKAGE.equals(notification.getPackageName())) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastPublishAt < 1200L) return;
        lastPublishAt = now;

        String message = NativeBubblePublisher.extractMessage(notification.getNotification());
        mainHandler.post(() -> {
            try {
                NativeBubblePublisher.publish(
                        getApplicationContext(),
                        true,
                        message
                );
            } catch (RuntimeException | LinkageError error) {
                AppPreferences.recordError(
                        getApplicationContext(),
                        "No se pudo mostrar la burbuja al recibir un mensaje",
                        error
                );
            }
        });
    }
}
