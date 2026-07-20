package com.leonardo.burbujagpt;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

/** Captura exclusivamente el PendingIntent de la notificación más reciente de WhatsApp. */
public class WhatsAppNotificationCaptureService extends NotificationListenerService {
    private static volatile PendingIntent latestContentIntent;
    private static volatile String latestConversationTitle = "WhatsApp";
    private static volatile long latestPostTime;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        StatusBarNotification[] active = getActiveNotifications();
        if (active == null) return;
        for (StatusBarNotification item : active) capture(item);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        capture(sbn);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        if (sbn == null || !NativeBubblePublisher.WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;
        if (sbn.getPostTime() == latestPostTime) {
            latestContentIntent = null;
            latestConversationTitle = "WhatsApp";
            latestPostTime = 0L;
        }
    }

    private static void capture(StatusBarNotification sbn) {
        if (sbn == null || !NativeBubblePublisher.WHATSAPP_PACKAGE.equals(sbn.getPackageName())) return;
        Notification notification = sbn.getNotification();
        if (notification == null || notification.contentIntent == null) return;
        if (sbn.getPostTime() < latestPostTime) return;

        CharSequence title = notification.extras == null
                ? null
                : notification.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (TextUtils.isEmpty(title) && notification.extras != null) {
            title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        }

        latestContentIntent = notification.contentIntent;
        latestConversationTitle = TextUtils.isEmpty(title) ? "WhatsApp" : title.toString();
        latestPostTime = sbn.getPostTime();
    }

    static PendingIntent getLatestContentIntent() {
        return latestContentIntent;
    }

    static String getLatestConversationTitle() {
        return latestConversationTitle;
    }

    static boolean hasCapturedNotification() {
        PendingIntent value = latestContentIntent;
        return value != null && !value.isCanceled();
    }

    static boolean isAccessEnabled(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(),
                "enabled_notification_listeners"
        );
        if (enabled == null) return false;
        ComponentName own = new ComponentName(context, WhatsAppNotificationCaptureService.class);
        for (String item : enabled.split(":")) {
            ComponentName parsed = ComponentName.unflattenFromString(item);
            if (own.equals(parsed)) return true;
        }
        return false;
    }
}
