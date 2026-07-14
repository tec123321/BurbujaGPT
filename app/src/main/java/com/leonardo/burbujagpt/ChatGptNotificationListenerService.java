package com.leonardo.burbujagpt;

import android.app.ActivityOptions;
import android.app.Notification;
import android.app.PendingIntent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public final class ChatGptNotificationListenerService extends NotificationListenerService {
    private static volatile ChatGptNotificationListenerService activeInstance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        activeInstance = this;
        if (!AppPreferences.isAutoBubblesEnabled(this)) return;

        long recentCutoff = System.currentTimeMillis() - 2 * 60 * 1000L;
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification notification : active) {
            if (notification.getPostTime() >= recentCutoff) publishIfEligible(notification, false);
        }
    }

    @Override
    public void onListenerDisconnected() {
        activeInstance = null;
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        publishIfEligible(sbn, true);
    }

    private void publishIfEligible(StatusBarNotification sbn, boolean autoExpand) {
        if (sbn == null
                || !NativeBubblePublisher.CHATGPT_PACKAGE.equals(sbn.getPackageName())
                || !AppPreferences.isAutoBubblesEnabled(this)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null || (notification.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;

        try {
            NativeBubblePublisher.publishFromNotification(this, sbn, autoExpand);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(
                    this,
                    "No se pudo convertir la notificación de ChatGPT en burbuja",
                    error
            );
        }
    }

    static boolean openSourceNotificationOnDisplay(String key, int displayId) {
        ChatGptNotificationListenerService service = activeInstance;
        if (service == null || key == null || key.isEmpty() || displayId < 0) return false;

        StatusBarNotification[] active = service.getActiveNotifications();
        if (active == null) return false;
        for (StatusBarNotification item : active) {
            if (!key.equals(item.getKey())) continue;
            PendingIntent contentIntent = item.getNotification().contentIntent;
            if (contentIntent == null) return false;
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchDisplayId(displayId);
                contentIntent.send(
                        service,
                        0,
                        null,
                        null,
                        null,
                        null,
                        options.toBundle()
                );
                return true;
            } catch (PendingIntent.CanceledException | RuntimeException error) {
                AppPreferences.recordError(
                        service,
                        "No se pudo abrir la conversación de la notificación en el globo",
                        error
                );
                return false;
            }
        }
        return false;
    }
}
