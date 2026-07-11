package com.leonardo.burbujagpt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.LocusId;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Publica una conversación Android distinta por cada chat o notificación. */
final class NativeBubblePublisher {
    static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    static final String EXTRA_BUBBLE_ID = "bubble_id";
    static final String EXTRA_SOURCE_KEY = "source_key";
    static final String EXTRA_TITLE = "bubble_title";

    private static final String CHANNEL_ID = "chatgpt_native_bubbles_v14";
    private static final String CATEGORY = "com.leonardo.burbujagpt.category.NATIVE_CHAT";
    private static final String NOTIFICATION_GROUP = "globo_gpt_conversations";
    private static final int LEGACY_NOTIFICATION_ID = 1301;

    private NativeBubblePublisher() {
    }

    static BubbleRecord publishManual(Context context, boolean autoExpand) {
        int sequence = AppPreferences.nextManualSequence(context);
        long now = System.currentTimeMillis();
        BubbleRecord record = new BubbleRecord(
                "manual_" + sequence + "_" + now,
                "ChatGPT · Chat " + sequence,
                null,
                0,
                0L,
                now,
                true
        );
        AppPreferences.saveRecord(context, record);
        publish(context, record, autoExpand, "Chat creado manualmente");
        return record;
    }

    static BubbleRecord publishFromNotification(
            Context context,
            StatusBarNotification source,
            boolean autoExpand
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IllegalStateException("Las burbujas nativas requieren Android 11 o posterior");
        }

        Notification notification = source.getNotification();
        String title = extractTitle(notification);
        String identity = extractConversationIdentity(source, notification, title);
        String id = "auto_" + Integer.toUnsignedString(identity.hashCode(), 36);

        BubbleRecord record = AppPreferences.getRecord(context, id);
        long now = System.currentTimeMillis();
        if (record == null) {
            record = new BubbleRecord(id, title, source.getKey(), 0, 0L, now, false);
        }
        record.title = title;
        record.sourceNotificationKey = source.getKey();
        if (source.getPostTime() > record.lastSourcePostTime) {
            record.unreadCount = Math.min(999, record.unreadCount + 1);
            record.lastSourcePostTime = source.getPostTime();
        }
        record.updatedAt = now;
        AppPreferences.saveRecord(context, record);

        String message = extractMessage(notification);
        publish(context, record, autoExpand, message);
        return record;
    }

    static void markRead(Context context, String bubbleId) {
        BubbleRecord record = AppPreferences.getRecord(context, bubbleId);
        if (record == null || record.unreadCount == 0) return;
        record.unreadCount = 0;
        record.updatedAt = System.currentTimeMillis();
        AppPreferences.saveRecord(context, record);
        publish(context, record, false, "Conversación abierta");
    }

    private static void publish(
            Context context,
            BubbleRecord record,
            boolean autoExpand,
            String message
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IllegalStateException("Las burbujas nativas requieren Android 11 o posterior");
        }

        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        if (notifications == null || shortcuts == null) {
            throw new IllegalStateException("Android no expuso el servicio de burbujas");
        }

        ensureChannel(notifications);
        Person assistant = new Person.Builder()
                .setName(record.title)
                .setImportant(true)
                .build();

        Intent bubbleIntent = createBubbleIntent(context, record);
        Bitmap iconBitmap = BubbleIconFactory.create(context, record.unreadCount);
        Icon bubbleIcon = Icon.createWithBitmap(iconBitmap);
        publishShortcut(context, shortcuts, assistant, record, bubbleIntent, bubbleIcon);

        int mutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE
                : 0;
        PendingIntent bubbleIntentToken = PendingIntent.getActivity(
                context,
                record.notificationId(),
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | mutableFlag
        );

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                record.notificationId() + 1,
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Person user = new Person.Builder().setName("Tú").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .setConversationTitle(record.title)
                .addMessage(
                        TextUtils.isEmpty(message) ? "Nueva respuesta de ChatGPT" : message,
                        System.currentTimeMillis(),
                        assistant
                );

        Notification.BubbleMetadata bubble = new Notification.BubbleMetadata.Builder(
                bubbleIntentToken,
                bubbleIcon
        )
                .setDesiredHeight(720)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();

        Notification.Builder builder = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_gpt_small)
                .setContentTitle(record.title)
                .setContentText(record.unreadCount > 0
                        ? record.unreadCount + (record.unreadCount == 1 ? " notificación" : " notificaciones")
                        : "Toca para abrir ChatGPT")
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(record.shortcutId())
                .setLocusId(new LocusId(record.shortcutId()))
                .addPerson(assistant)
                .setBubbleMetadata(bubble)
                .setOnlyAlertOnce(record.unreadCount == 0)
                .setShowWhen(true)
                .setWhen(record.updatedAt)
                .setAutoCancel(false)
                .setGroup(NOTIFICATION_GROUP);
        if (record.unreadCount > 0) builder.setNumber(record.unreadCount);

        notifications.notify(record.notificationId(), builder.build());
    }

    private static void ensureChannel(NotificationManager notifications) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Chats de ChatGPT en burbujas",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Crea una burbuja por chat y por respuesta de ChatGPT");
        channel.setShowBadge(true);
        channel.setAllowBubbles(true);
        notifications.createNotificationChannel(channel);
    }

    private static void publishShortcut(
            Context context,
            ShortcutManager shortcuts,
            Person assistant,
            BubbleRecord record,
            Intent bubbleIntent,
            Icon icon
    ) {
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, record.shortcutId())
                .setShortLabel(trimLabel(record.title, 28))
                .setLongLabel(trimLabel(record.title + " en burbuja", 48))
                .setIcon(icon)
                .setCategories(Collections.singleton(CATEGORY))
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(bubbleIntent)
                .setPerson(assistant)
                .setLongLived(true)
                .setRank(0)
                .build();
        shortcuts.pushDynamicShortcut(shortcut);
    }

    private static Intent createBubbleIntent(Context context, BubbleRecord record) {
        return new Intent(context, NativeBubbleActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("burbujagpt://conversation/" + Uri.encode(record.id)))
                .putExtra(EXTRA_BUBBLE_ID, record.id)
                .putExtra(EXTRA_SOURCE_KEY, record.sourceNotificationKey)
                .putExtra(EXTRA_TITLE, record.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    static void cancelAll(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? context.getSystemService(ShortcutManager.class)
                : null;

        List<String> shortcutIds = new ArrayList<>();
        for (BubbleRecord record : AppPreferences.getRecords(context)) {
            if (notifications != null) notifications.cancel(record.notificationId());
            shortcutIds.add(record.shortcutId());
        }
        if (shortcuts != null && !shortcutIds.isEmpty()) {
            shortcuts.removeDynamicShortcuts(shortcutIds);
        }
        AppPreferences.clearRecords(context);
        cancelLegacy(context);
    }

    static void cancelLegacy(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(LEGACY_NOTIFICATION_ID);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
            if (shortcuts != null) {
                shortcuts.removeDynamicShortcuts(java.util.Arrays.asList(
                        "chatgpt_native_conversation_v13",
                        "native_chatgpt_conversation",
                        "native_chatgpt_conversation_v2"
                ));
            }
        }
    }

    static int activeBubbleCount(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return 0;
        int count = 0;
        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                Notification notification = item.getNotification();
                if (context.getPackageName().equals(item.getPackageName())
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && CHANNEL_ID.equals(notification.getChannelId())) {
                    count++;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return count;
    }

    static boolean hasExpandedBubble(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                Notification notification = item.getNotification();
                if (context.getPackageName().equals(item.getPackageName())
                        && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                        && CHANNEL_ID.equals(notification.getChannelId())
                        && (notification.flags & Notification.FLAG_BUBBLE) != 0) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static String extractConversationIdentity(
            StatusBarNotification source,
            Notification notification,
            String title
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String shortcutId = notification.getShortcutId();
            if (!TextUtils.isEmpty(shortcutId)) return "shortcut:" + shortcutId;
        }
        if (!TextUtils.isEmpty(source.getTag())) return "tag:" + source.getTag();
        if (!TextUtils.isEmpty(title) && !"ChatGPT".equalsIgnoreCase(title)) return "title:" + title;
        return "key:" + source.getKey();
    }

    private static String extractTitle(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "ChatGPT";
        CharSequence value = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (TextUtils.isEmpty(value)) return "ChatGPT";
        String title = value.toString().trim();
        return title.isEmpty() ? "ChatGPT" : trimLabel(title, 48);
    }

    private static String extractMessage(Notification notification) {
        Bundle extras = notification.extras;
        if (extras == null) return "Nueva respuesta de ChatGPT";
        CharSequence value = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(value)) return "Nueva respuesta de ChatGPT";
        return trimLabel(value.toString().replace('\n', ' ').trim(), 120);
    }

    private static String trimLabel(String value, int max) {
        if (value == null) return "ChatGPT";
        String safe = value.trim();
        if (safe.length() <= max) return safe;
        return safe.substring(0, Math.max(1, max - 1)) + "…";
    }
}
