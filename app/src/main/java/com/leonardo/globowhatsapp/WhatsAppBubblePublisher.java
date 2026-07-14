package com.leonardo.globowhatsapp;

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
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Publica conversaciones Android que abren la aplicación oficial desde la tarea del globo. */
final class WhatsAppBubblePublisher {
    static final String WHATSAPP_PACKAGE = "com.whatsapp";
    static final String EXTRA_CONVERSATION_TITLE =
            "com.leonardo.globowhatsapp.extra.CONVERSATION_TITLE";
    static final String EXTRA_CONVERSATION_TOKEN =
            "com.leonardo.globowhatsapp.extra.CONVERSATION_TOKEN";

    private static final String CHANNEL_ID = "whatsapp_native_bubbles_v1";
    private static final String SHORTCUT_PREFIX = "whatsapp_conversation_v1_";
    private static final String CATEGORY = "com.leonardo.globowhatsapp.category.CONVERSATION";
    private static final String MANUAL_TOKEN = "manual";
    private static final int NOTIFICATION_BASE = 2300;
    private static final int REQUEST_BASE = 23000;

    private static final String PREFS = "whatsapp_bubble_state_v1";
    private static final String KEY_ACTIVE_TOKENS = "active_tokens";

    private WhatsAppBubblePublisher() {
    }

    static void publishManual(Context context, boolean autoExpand) {
        publish(
                context,
                MANUAL_TOKEN,
                "WhatsApp",
                "Toca el globo para abrir la aplicación oficial",
                autoExpand
        );
    }

    static void publishConversation(
            Context context,
            String sourceKey,
            String title,
            String message,
            boolean autoExpand
    ) {
        publish(
                context,
                tokenFor(sourceKey),
                sanitize(title, 80, "WhatsApp"),
                sanitize(message, 220, "Nuevo mensaje de WhatsApp"),
                autoExpand
        );
    }

    private static void publish(
            Context context,
            String token,
            String title,
            String message,
            boolean autoExpand
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
        rememberToken(context, token);

        Icon officialIcon = loadOfficialWhatsAppIcon(context);
        Person contact = new Person.Builder()
                .setName(title)
                .setIcon(officialIcon)
                .setImportant(true)
                .build();
        publishShortcut(context, shortcuts, contact, officialIcon, token, title);

        Intent bubbleIntent = createBubbleIntent(context, token, title);
        int mutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_MUTABLE
                : 0;
        PendingIntent bubbleIntentToken = PendingIntent.getActivity(
                context,
                requestCode(token),
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | mutableFlag
        );

        Intent settingsIntent = new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                requestCode(token) + 5000,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Person user = new Person.Builder().setName("Tú").build();
        Notification.MessagingStyle style = new Notification.MessagingStyle(user)
                .setConversationTitle(title)
                .addMessage(message, System.currentTimeMillis(), contact);

        Notification.BubbleMetadata bubble = new Notification.BubbleMetadata.Builder(
                bubbleIntentToken,
                officialIcon
        )
                .setDesiredHeight(720)
                .setAutoExpandBubble(autoExpand)
                .setSuppressNotification(false)
                .build();

        String shortcutId = shortcutId(token);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentTitle(title)
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setStyle(style)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(shortcutId)
                .setLocusId(new LocusId(shortcutId))
                .addPerson(contact)
                .setBubbleMetadata(bubble)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis())
                .setAutoCancel(false)
                .build();

        notifications.notify(notificationId(token), notification);
    }

    private static void ensureChannel(NotificationManager notifications) {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Conversaciones de WhatsApp",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Globos nativos que abren la aplicación oficial de WhatsApp");
        channel.setShowBadge(true);
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setAllowBubbles(true);
        notifications.createNotificationChannel(channel);
    }

    private static void publishShortcut(
            Context context,
            ShortcutManager shortcuts,
            Person contact,
            Icon officialIcon,
            String token,
            String title
    ) {
        String shortcutId = shortcutId(token);
        ShortcutInfo shortcut = new ShortcutInfo.Builder(context, shortcutId)
                .setShortLabel(title)
                .setLongLabel(title + " en globo")
                .setIcon(officialIcon)
                .setCategories(Collections.singleton(CATEGORY))
                .setActivity(new ComponentName(context, MainActivity.class))
                .setIntent(createBubbleIntent(context, token, title))
                .setPerson(contact)
                .setLongLived(true)
                .build();

        try {
            shortcuts.pushDynamicShortcut(shortcut);
        } catch (IllegalArgumentException | IllegalStateException error) {
            if (!containsShortcut(shortcuts, shortcutId)) throw error;
        }
    }

    private static boolean containsShortcut(ShortcutManager shortcuts, String shortcutId) {
        try {
            for (ShortcutInfo shortcut : shortcuts.getDynamicShortcuts()) {
                if (shortcutId.equals(shortcut.getId())) return true;
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private static Intent createBubbleIntent(
            Context context,
            String token,
            String title
    ) {
        return new Intent(context, NativeBubbleActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("globowhatsapp://conversation/" + Uri.encode(token)))
                .putExtra(EXTRA_CONVERSATION_TITLE, title)
                .putExtra(EXTRA_CONVERSATION_TOKEN, token)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
    }

    static String getConversationToken(Intent intent) {
        if (intent == null) return MANUAL_TOKEN;
        String token = intent.getStringExtra(EXTRA_CONVERSATION_TOKEN);
        if (!TextUtils.isEmpty(token)) return token;
        Uri data = intent.getData();
        if (data != null && !TextUtils.isEmpty(data.getLastPathSegment())) {
            return data.getLastPathSegment();
        }
        return MANUAL_TOKEN;
    }

    static String getConversationTitle(Intent intent) {
        if (intent == null) return "WhatsApp";
        return sanitize(intent.getStringExtra(EXTRA_CONVERSATION_TITLE), 80, "WhatsApp");
    }

    static String tokenFor(String sourceKey) {
        String safe = TextUtils.isEmpty(sourceKey) ? "unknown" : sourceKey;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(24);
            for (int index = 0; index < 12; index++) {
                result.append(String.format(java.util.Locale.ROOT, "%02x", digest[index]));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException impossible) {
            return Integer.toUnsignedString(safe.hashCode(), 16);
        }
    }

    private static Icon loadOfficialWhatsAppIcon(Context context) {
        try {
            Drawable drawable = context.getPackageManager().getApplicationIcon(WHATSAPP_PACKAGE);
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

    static void cancelAll(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        ShortcutManager shortcuts = context.getSystemService(ShortcutManager.class);

        if (notifications != null) {
            try {
                for (StatusBarNotification item : notifications.getActiveNotifications()) {
                    Notification notification = item.getNotification();
                    if (context.getPackageName().equals(item.getPackageName())
                            && CHANNEL_ID.equals(notification.getChannelId())) {
                        notifications.cancel(item.getId());
                    }
                }
            } catch (RuntimeException ignored) {
                for (String token : getActiveTokens(context)) {
                    notifications.cancel(notificationId(token));
                }
            }
        }

        if (shortcuts != null) {
            Set<String> ids = new HashSet<>();
            for (String token : getActiveTokens(context)) ids.add(shortcutId(token));
            try {
                for (ShortcutInfo shortcut : shortcuts.getDynamicShortcuts()) {
                    if (shortcut.getId().startsWith(SHORTCUT_PREFIX)) ids.add(shortcut.getId());
                }
            } catch (RuntimeException ignored) {
            }

            List<String> shortcutIds = new ArrayList<>(ids);
            if (!shortcutIds.isEmpty()) {
                shortcuts.removeDynamicShortcuts(shortcutIds);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    shortcuts.removeLongLivedShortcuts(shortcutIds);
                }
            }
        }

        prefs(context).edit().remove(KEY_ACTIVE_TOKENS).apply();
    }

    static int getActiveBubbleCount(Context context) {
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        if (notifications == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return getActiveTokens(context).size();
        }

        int count = 0;
        try {
            for (StatusBarNotification item : notifications.getActiveNotifications()) {
                Notification notification = item.getNotification();
                if (context.getPackageName().equals(item.getPackageName())
                        && CHANNEL_ID.equals(notification.getChannelId())) {
                    count++;
                }
            }
            return count;
        } catch (RuntimeException ignored) {
            return getActiveTokens(context).size();
        }
    }

    private static void rememberToken(Context context, String token) {
        Set<String> tokens = new HashSet<>(getActiveTokens(context));
        tokens.add(token);
        prefs(context).edit().putStringSet(KEY_ACTIVE_TOKENS, tokens).apply();
    }

    private static Set<String> getActiveTokens(Context context) {
        Set<String> stored = prefs(context).getStringSet(
                KEY_ACTIVE_TOKENS,
                Collections.emptySet()
        );
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static int notificationId(String token) {
        return NOTIFICATION_BASE + (token.hashCode() & 0x0FFFFFFF);
    }

    private static int requestCode(String token) {
        return REQUEST_BASE + (token.hashCode() & 0x00FFFFFF);
    }

    private static String shortcutId(String token) {
        return SHORTCUT_PREFIX + token;
    }

    private static String sanitize(CharSequence value, int maxLength, String fallback) {
        if (value == null) return fallback;
        String clean = value.toString().replace('\n', ' ').replace('\r', ' ').trim();
        while (clean.contains("  ")) clean = clean.replace("  ", " ");
        if (clean.length() > maxLength) clean = clean.substring(0, maxLength - 1) + "…";
        return clean.isEmpty() ? fallback : clean;
    }
}
