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
import android.net.Uri;
import android.os.Build;
import android.service.notification.StatusBarNotification;

import java.util.Arrays;
import java.util.Collections;

/** Publica una burbuja que inicia una pila atómica: anfitrión y WhatsApp encima. */
final class NativeBubblePublisher {
    static final String WHATSAPP_PACKAGE = "com.whatsapp";

    private static final String CHANNEL_ID = "whatsapp_atomic_stack_v33";
    private static final String SHORTCUT_ID = "whatsapp_atomic_stack_v33";
    private static final String CATEGORY = "com.leonardo.globowhatsapp.category.ATOMIC_STACK";
    private static final int NOTIFICATION_ID = 3301;

    private NativeBubblePublisher() {
    }

    static void publish(Context context, boolean autoExpand) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw new IllegalStateException("Las burbujas nativas requieren Android 11 o posterior");
        }

        Intent installedLauncher = context.getPackageManager()
                .getLaunchIntentForPackage(WHATSAPP_PACKAGE);
        if (installedLauncher == null || installedLauncher.getComponent() == null) {
            throw new IllegalStateException("No se encontró la actividad principal de WhatsApp");
        }

        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        if (notifications == null || shortcuts == null) {
            throw new IllegalStateException("Android no expuso los servicios de burbujas");
        }

        clearOldVersions(notifications, shortcuts);
        ensureChannel(notifications);

        Person whatsapp = new Person.Builder()
                .setName("WhatsApp")
                .setImportant(true)
                .build();
        publishShortcut(context, shortcuts, whatsapp);

        Intent host = createHostIntent(context);
        Intent target = createWhatsAppIntent(installedLauncher.getComponent());
        int mutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE
                : 0;

        PendingIntent bubbleStack = PendingIntent.getActivities(
                context,
                3303,
                new Intent[]{host, target},
                PendingIntent.FLAG_UPDATE_CURRENT | mutableFlag
        );

        Intent settingsIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                3302,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Person user = new Person.Builder().setName("Tú").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .setConversationTitle("WhatsApp")
                .addMessage(
                        "Toca el globo para abrir WhatsApp",
                        System.currentTimeMillis(),
                        whatsapp
                );

        Notification.BubbleMetadata bubble = new Notification.BubbleMetadata.Builder(
                bubbleStack,
                Icon.createWithResource(context, R.drawable.ic_bubble)
        )
                .setDesiredHeight(640)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle("WhatsApp")
                .setContentText("Aplicación oficial en una pila de burbuja")
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(SHORTCUT_ID)
                .setLocusId(new LocusId(SHORTCUT_ID))
                .addPerson(whatsapp)
                .setBubbleMetadata(bubble)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setAutoCancel(false)
                .build();

        notifications.notify(NOTIFICATION_ID, notification);
    }

    private static Intent createHostIntent(Context context) {
        return new Intent(context, NativeBubbleActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("globowhatsapp://atomic-stack/v33"))
                .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
                                | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                );
    }

    private static Intent createWhatsAppIntent(ComponentName component) {
        Intent target = new Intent(Intent.ACTION_MAIN);
        target.addCategory(Intent.CATEGORY_LAUNCHER);
        target.setComponent(component);
        target.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
        );
        return target;
    }

    private static void ensureChannel(NotificationManager notifications) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "WhatsApp en globo",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Abre WhatsApp mediante una pila atómica iniciada por SystemUI");
        channel.setShowBadge(true);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setAllowBubbles(true);
        notifications.createNotificationChannel(channel);
    }

    private static void publishShortcut(
            Context context,
            ShortcutManager shortcuts,
            Person whatsapp
    ) {
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, SHORTCUT_ID)
                .setShortLabel("WhatsApp")
                .setLongLabel("WhatsApp en globo")
                .setIcon(Icon.createWithResource(context, R.drawable.ic_bubble))
                .setCategories(Collections.singleton(CATEGORY))
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(createHostIntent(context))
                .setPerson(whatsapp)
                .setLongLived(true)
                .build();
        shortcuts.pushDynamicShortcut(shortcut);
    }

    private static void clearOldVersions(
            NotificationManager notifications,
            ShortcutManager shortcuts
    ) {
        notifications.cancel(3001);
        notifications.cancel(3201);
        notifications.cancel(NOTIFICATION_ID);
        shortcuts.removeDynamicShortcuts(Arrays.asList(
                "whatsapp_native_conversation",
                "whatsapp_native_conversation_v2",
                "whatsapp_native_conversation_v3",
                "whatsapp_native_conversation_v30",
                "whatsapp_captured_conversation_v32",
                SHORTCUT_ID
        ));
    }

    static void cancel(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications != null) {
            notifications.cancel(3001);
            notifications.cancel(3201);
            notifications.cancel(NOTIFICATION_ID);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
            if (shortcuts != null) {
                shortcuts.removeDynamicShortcuts(Arrays.asList(
                        "whatsapp_native_conversation_v30",
                        "whatsapp_captured_conversation_v32",
                        SHORTCUT_ID
                ));
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
