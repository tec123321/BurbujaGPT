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
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Publica varias conversaciones Android que abren ChatGPT oficial. */
final class NativeBubblePublisher {
    static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    static final String EXTRA_CHAT_SEQUENCE = "com.leonardo.burbujagpt.extra.CHAT_SEQUENCE";

    private static final String CHANNEL_ID = "chatgpt_native_bubble_v13";
    private static final String SHORTCUT_PREFIX = "chatgpt_native_conversation_v13_";
    private static final String CATEGORY = "com.leonardo.burbujagpt.category.NATIVE_CHAT";
    private static final int NOTIFICATION_BASE = 1300;
    private static final int REQUEST_BASE = 13000;

    private static final String PREFS = "v13_multiple_chats";
    private static final String KEY_SEQUENCE = "sequence";
    private static final String KEY_LAST = "last";
    private static final String KEY_ACTIVE = "active";

    private NativeBubblePublisher() {
    }

    /** Reabre el último chat; si todavía no existe, crea Chat 1. */
    static void publish(Context context, boolean autoExpand) {
        publish(context, autoExpand, "Toca la burbuja para abrir la aplicación oficial");
    }

    /** Reabre el último chat al llegar una respuesta de ChatGPT. */
    static void publish(Context context, boolean autoExpand, String message) {
        int sequence = getLastSequence(context);
        if (sequence <= 0) sequence = createSequence(context);
        publishChat(context, sequence, autoExpand, message);
    }

    /** Crea una burbuja adicional con identificadores independientes. */
    static int publishNewChat(Context context, boolean autoExpand) {
        int sequence = createSequence(context);
        publishChat(
                context,
                sequence,
                autoExpand,
                "Chat nuevo listo. Abre una conversación distinta en ChatGPT."
        );
        return sequence;
    }

    private static void publishChat(
            Context context,
            int sequence,
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
        rememberSequence(context, sequence);

        String shortcutId = shortcutId(sequence);
        String title = title(sequence);
        Icon officialIcon = loadOfficialChatGptIcon(context);
        Person assistant = new Person.Builder()
                .setName(title)
                .setIcon(officialIcon)
                .setImportant(true)
                .build();
        publishShortcut(context, shortcuts, assistant, officialIcon, sequence);

        Intent bubbleIntent = createBubbleIntent(context, sequence);
        int mutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE
                : 0;
        PendingIntent bubbleIntentToken = PendingIntent.getActivity(
                context,
                REQUEST_BASE + sequence,
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | mutableFlag
        );

        Intent settingsIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                REQUEST_BASE + 5000 + sequence,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String safeMessage = TextUtils.isEmpty(message)
                ? "Nueva respuesta de ChatGPT"
                : message;
        Person user = new Person.Builder().setName("Tú").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .setConversationTitle(title)
                .addMessage(safeMessage, System.currentTimeMillis(), assistant);

        Notification.BubbleMetadata bubble = new Notification.BubbleMetadata.Builder(
                bubbleIntentToken,
                officialIcon
        )
                .setDesiredHeight(640)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();

        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle(title)
                .setContentText(safeMessage)
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(shortcutId)
                .setLocusId(new LocusId(shortcutId))
                .addPerson(assistant)
                .setBubbleMetadata(bubble)
                .setOnlyAlertOnce(false)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .build();

        notifications.notify(notificationId(sequence), notification);
    }

    private static void ensureChannel(NotificationManager notifications) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "ChatGPT en burbujas",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Conversaciones nativas que abren la aplicación oficial de ChatGPT");
        channel.setShowBadge(true);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setAllowBubbles(true);
        notifications.createNotificationChannel(channel);
    }

    private static void publishShortcut(
            Context context,
            ShortcutManager shortcuts,
            Person assistant,
            Icon officialIcon,
            int sequence
    ) {
        Intent bubbleIntent = createBubbleIntent(context, sequence);
        String shortcutId = shortcutId(sequence);
        String title = title(sequence);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(title)
                .setLongLabel(title + " en burbuja")
                .setIcon(officialIcon)
                .setCategories(Collections.singleton(CATEGORY))
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(bubbleIntent)
                .setPerson(assistant)
                .setLongLived(true)
                .build();

        shortcuts.pushDynamicShortcut(shortcut);
    }

    private static Intent createBubbleIntent(Context context, int sequence) {
        return new Intent(context, NativeBubbleActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("burbujagpt://conversation/chatgpt/" + sequence))
                .putExtra(EXTRA_CHAT_SEQUENCE, sequence)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    static int getChatSequence(Intent intent) {
        if (intent == null) return 1;
        int sequence = intent.getIntExtra(EXTRA_CHAT_SEQUENCE, 0);
        if (sequence > 0) return sequence;
        Uri data = intent.getData();
        if (data != null) {
            try {
                String segment = data.getLastPathSegment();
                if (segment != null) return Math.max(1, Integer.parseInt(segment));
            } catch (NumberFormatException ignored) {
            }
        }
        return 1;
    }

    static String extractMessage(Notification notification) {
        if (notification == null) return "Nueva respuesta de ChatGPT";
        Bundle extras = notification.extras;
        if (extras == null) return "Nueva respuesta de ChatGPT";

        CharSequence value = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(value)) value = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        if (TextUtils.isEmpty(value)) return "Nueva respuesta de ChatGPT";

        String result = value.toString().replace('\n', ' ').trim();
        if (result.length() > 160) result = result.substring(0, 159) + "…";
        return result.isEmpty() ? "Nueva respuesta de ChatGPT" : result;
    }

    private static Icon loadOfficialChatGptIcon(Context context) {
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(CHATGPT_PACKAGE);
            int size = Math.max(
                    192,
                    Math.round(96 * context.getResources().getDisplayMetrics().density)
            );
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            return Icon.createWithBitmap(bitmap);
        } catch (Exception error) {
            return Icon.createWithResource(context, R.drawable.ic_bubble);
        }
    }

    static void cancel(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);
        Set<String> active = getActiveSet(context);
        List<String> shortcutIds = new ArrayList<>();

        for (String value : active) {
            try {
                int sequence = Integer.parseInt(value);
                if (notifications != null) notifications.cancel(notificationId(sequence));
                shortcutIds.add(shortcutId(sequence));
            } catch (NumberFormatException ignored) {
            }
        }

        if (shortcuts != null && !shortcutIds.isEmpty()) {
            shortcuts.removeDynamicShortcuts(shortcutIds);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                shortcuts.removeLongLivedShortcuts(shortcutIds);
            }
        }

        prefs(context).edit()
                .remove(KEY_ACTIVE)
                .remove(KEY_LAST)
                .apply();
    }

    static int getActiveChatCount(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return getActiveSet(context).size();
        }

        Set<Integer> expected = new HashSet<>();
        for (String value : getActiveSet(context)) {
            try {
                expected.add(notificationId(Integer.parseInt(value)));
            } catch (NumberFormatException ignored) {
            }
        }

        int count = 0;
        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                if (context.getPackageName().equals(item.getPackageName())
                        && expected.contains(item.getId())) {
                    count++;
                }
            }
        } catch (RuntimeException ignored) {
            return getActiveSet(context).size();
        }
        return count;
    }

    static boolean isPosted(Context context) {
        return findActiveNotification(context, false) != null;
    }

    static boolean isExpandedAsBubble(Context context) {
        return findActiveNotification(context, true) != null;
    }

    private static StatusBarNotification findActiveNotification(
            Context context,
            boolean requireBubble
    ) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null;

        Set<Integer> expected = new HashSet<>();
        for (String value : getActiveSet(context)) {
            try {
                expected.add(notificationId(Integer.parseInt(value)));
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                if (!context.getPackageName().equals(item.getPackageName())
                        || !expected.contains(item.getId())) {
                    continue;
                }
                if (!requireBubble
                        || (item.getNotification().flags & Notification.FLAG_BUBBLE) != 0) {
                    return item;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }

    private static synchronized int createSequence(Context context) {
        SharedPreferences preferences = prefs(context);
        int next = preferences.getInt(KEY_SEQUENCE, 0) + 1;
        preferences.edit()
                .putInt(KEY_SEQUENCE, next)
                .putInt(KEY_LAST, next)
                .apply();
        rememberSequence(context, next);
        return next;
    }

    private static void rememberSequence(Context context, int sequence) {
        Set<String> values = new HashSet<>(getActiveSet(context));
        values.add(String.valueOf(sequence));
        prefs(context).edit()
                .putStringSet(KEY_ACTIVE, values)
                .putInt(KEY_LAST, sequence)
                .apply();
    }

    private static int getLastSequence(Context context) {
        return prefs(context).getInt(KEY_LAST, 0);
    }

    private static Set<String> getActiveSet(Context context) {
        Set<String> stored = prefs(context).getStringSet(KEY_ACTIVE, Collections.emptySet());
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int notificationId(int sequence) {
        return NOTIFICATION_BASE + sequence;
    }

    private static String shortcutId(int sequence) {
        return SHORTCUT_PREFIX + sequence;
    }

    private static String title(int sequence) {
        return "ChatGPT · Chat " + sequence;
    }
}
