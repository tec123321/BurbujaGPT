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
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.Collections;

/** Publica una burbuja cuyo contenido es el PendingIntent real de un chat de WhatsApp. */
final class NativeBubblePublisher {
    static final String WHATSAPP_PACKAGE = "com.whatsapp";

    private static final String CHANNEL_ID = "whatsapp_captured_bubble_v32";
    private static final String SHORTCUT_ID = "whatsapp_captured_conversation_v32";
    private static final String CATEGORY = "com.leonardo.globowhatsapp.category.CAPTURED_CHAT";
    private static final int NOTIFICATION_ID = 3201;

    private NativeBubblePublisher() {
    }

    static void publish(Context context, boolean autoExpand) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IllegalStateException("Las burbujas nativas requieren Android 11 o posterior");
        }

        PendingIntent whatsappChat = WhatsAppNotificationCaptureService.getLatestContentIntent();
        if (whatsappChat == null) {
            throw new IllegalStateException(
                    "No hay una notificación activa de WhatsApp con un chat capturado"
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !whatsappChat.isActivity()) {
            throw new IllegalStateException("La notificación de WhatsApp no contiene una actividad");
        }

        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        if (notifications == null || shortcuts == null) {
            throw new IllegalStateException("Android no expuso el servicio de burbujas");
        }

        ensureChannel(notifications);
        String conversationName = WhatsAppNotificationCaptureService.getLatestConversationTitle();
        Person contact = new Person.Builder()
                .setName(conversationName)
                .setImportant(true)
                .build();
        publishShortcut(context, shortcuts, contact);

        Intent settingsIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                3202,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Person user = new Person.Builder().setName("Tú").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .setConversationTitle(conversationName)
                .addMessage(
                        "Toca la burbuja para abrir este chat con el PendingIntent original de WhatsApp",
                        System.currentTimeMillis(),
                        contact
                );

        Notification.BubbleMetadata bubble = new Notification.BubbleMetadata.Builder(
                whatsappChat,
                Icon.createWithResource(context, R.drawable.ic_bubble)
        )
                .setDesiredHeight(640)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle(conversationName)
                .setContentText("Chat de WhatsApp capturado como burbuja")
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_ID)
                .setLocusId(new LocusId(SHORTCUT_ID))
                .addPerson(contact)
                .setBubbleMetadata(bubble)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .build();

        notifications.notify(NOTIFICATION_ID, notification);
    }

    private static void ensureChannel(NotificationManager notifications) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Chats de WhatsApp capturados",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Burbujas creadas desde el PendingIntent original de WhatsApp");
        channel.setShowBadge(true);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setAllowBubbles(true);
        notifications.createNotificationChannel(channel);
    }

    private static void publishShortcut(
            Context context,
            ShortcutManager shortcuts,
            Person contact
    ) {
        Intent settingsIntent = new Intent(context, MainActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel(contact.getName())
                .setLongLabel(contact.getName() + " en burbuja")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_bubble))
                .setCategories(Collections.singleton(CATEGORY))
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(settingsIntent)
                .setPerson(contact)
                .setLongLived(true)
                .build();

        shortcuts.removeDynamicShortcuts(Arrays.asList(
                "whatsapp_native_conversation",
                "whatsapp_native_conversation_v2",
                "whatsapp_native_conversation_v3",
                "whatsapp_native_conversation_v30"
        ));
        shortcuts.pushDynamicShortcut(shortcut);
    }

    static void cancel(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(NOTIFICATION_ID);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
            if (shortcuts != null) {
                shortcuts.removeDynamicShortcuts(Collections.singletonList(SHORTCUT_ID));
            }
        }
    }

    static boolean isPosted(Context context) {
        return findActiveNotification(context) != null;
    }

    static boolean isExpandedAsBubble(Context context) {
        StatusBarNotification active = findActiveNotification(context);
        return active != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && (active.getNotification().flags & Notification.FLAG_BUBBLE) != 0;
    }

    private static StatusBarNotification findActiveNotification(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;
        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                if (item.getId() == NOTIFICATION_ID
                        && context.getPackageName().equals(item.getPackageName())) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
