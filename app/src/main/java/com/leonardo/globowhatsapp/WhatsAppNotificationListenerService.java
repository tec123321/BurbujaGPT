package com.leonardo.globowhatsapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte únicamente notificaciones de mensajes de com.whatsapp en burbujas locales.
 * Los títulos y mensajes se mantienen en memoria el tiempo mínimo necesario y nunca se guardan.
 */
public final class WhatsAppNotificationListenerService extends NotificationListenerService {
    private static final long DUPLICATE_WINDOW_MS = 1800L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, LastPublication> lastPublications = new HashMap<>();

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        mainHandler.postDelayed(this::publishUnreadConversations, 450L);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        process(posted);
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        lastPublications.clear();
        super.onDestroy();
    }

    private void publishUnreadConversations() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) return;
            for (StatusBarNotification posted : active) process(posted);
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(
                    getApplicationContext(),
                    "No se pudieron recuperar los mensajes pendientes",
                    error
            );
        }
    }

    private void process(StatusBarNotification posted) {
        if (posted == null
                || !WhatsAppBubblePublisher.WHATSAPP_PACKAGE.equals(posted.getPackageName())) {
            return;
        }

        Notification notification = posted.getNotification();
        if (!isMessageNotification(notification)) return;

        String title = extractConversationTitle(notification);
        String message = extractMessage(notification);
        if (TextUtils.isEmpty(message)) return;

        String sourceKey = extractConversationKey(posted, notification, title);
        String token = WhatsAppBubblePublisher.tokenFor(sourceKey);
        String fingerprint = title + '\u0000' + message;
        long now = System.currentTimeMillis();

        LastPublication previous = lastPublications.get(token);
        if (previous != null
                && fingerprint.equals(previous.fingerprint)
                && now - previous.time < DUPLICATE_WINDOW_MS) {
            return;
        }
        lastPublications.put(token, new LastPublication(fingerprint, now));

        PendingIntent target = notification.contentIntent;
        if (target != null
                && !WhatsAppBubblePublisher.WHATSAPP_PACKAGE.equals(target.getCreatorPackage())) {
            target = null;
        }

        try {
            WhatsAppBubblePublisher.publishConversation(
                    getApplicationContext(),
                    sourceKey,
                    title,
                    message,
                    target,
                    false
            );
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordError(
                    getApplicationContext(),
                    "No se pudo crear el globo de WhatsApp",
                    error
            );
        }
    }

    private boolean isMessageNotification(Notification notification) {
        if (notification == null) return false;

        int flags = notification.flags;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            return false;
        }

        String category = notification.category;
        if (Notification.CATEGORY_CALL.equals(category)
                || Notification.CATEGORY_TRANSPORT.equals(category)
                || Notification.CATEGORY_PROGRESS.equals(category)
                || Notification.CATEGORY_SERVICE.equals(category)) {
            return false;
        }

        Bundle extras = notification.extras;
        if (extras == null) return false;

        if (!TextUtils.isEmpty(extras.getCharSequence(Notification.EXTRA_TEXT))
                || !TextUtils.isEmpty(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))) {
            return true;
        }

        Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        return messages != null && messages.length > 0;
    }

    private String extractConversationTitle(Notification notification) {
        Bundle extras = notification.extras;
        CharSequence value = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_TITLE);

        if (TextUtils.isEmpty(value)) {
            List<Notification.MessagingStyle.Message> messages = extractMessagingStyle(notification);
            if (!messages.isEmpty()) {
                Notification.MessagingStyle.Message latest = messages.get(messages.size() - 1);
                if (latest.getSenderPerson() != null) {
                    value = latest.getSenderPerson().getName();
                }
            }
        }

        return sanitize(value, 80, "WhatsApp");
    }

    private String extractMessage(Notification notification) {
        List<Notification.MessagingStyle.Message> messages = extractMessagingStyle(notification);
        if (!messages.isEmpty()) {
            CharSequence text = messages.get(messages.size() - 1).getText();
            if (!TextUtils.isEmpty(text)) return sanitize(text, 220, "");
        }

        Bundle extras = notification.extras;
        CharSequence value = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        return sanitize(value, 220, "");
    }

    private List<Notification.MessagingStyle.Message> extractMessagingStyle(
            Notification notification
    ) {
        Parcelable[] bundles = notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (bundles == null || bundles.length == 0) return java.util.Collections.emptyList();
        try {
            return Notification.MessagingStyle.Message.getMessagesFromBundleArray(bundles);
        } catch (RuntimeException | LinkageError ignored) {
            return java.util.Collections.emptyList();
        }
    }

    private String extractConversationKey(
            StatusBarNotification posted,
            Notification notification,
            String title
    ) {
        String shortcutId = notification.getShortcutId();
        if (!TextUtils.isEmpty(shortcutId)) return "shortcut:" + shortcutId;

        String key = posted.getKey();
        if (!TextUtils.isEmpty(key)) return "notification:" + key;

        String tag = posted.getTag();
        if (!TextUtils.isEmpty(tag)) return "tag:" + tag + ':' + posted.getId();
        return "fallback:" + posted.getId() + ':' + title;
    }

    private String sanitize(CharSequence value, int maxLength, String fallback) {
        if (value == null) return fallback;
        String clean = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        while (clean.contains("  ")) clean = clean.replace("  ", " ");
        if (clean.length() > maxLength) clean = clean.substring(0, maxLength - 1) + "…";
        return clean.isEmpty() ? fallback : clean;
    }

    private static final class LastPublication {
        final String fingerprint;
        final long time;

        LastPublication(String fingerprint, long time) {
            this.fingerprint = fingerprint;
            this.time = time;
        }
    }
}
