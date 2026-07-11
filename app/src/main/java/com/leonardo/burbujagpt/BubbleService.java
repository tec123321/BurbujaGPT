package com.leonardo.burbujagpt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Person;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;

/** Burbuja arrastrable que abre el panel web o la app oficial. */
public class BubbleService extends Service {
    public static final String ACTION_STOP = "com.leonardo.burbujagpt.STOP";
    public static final String ACTION_REFRESH = "com.leonardo.burbujagpt.REFRESH";
    public static final String ACTION_SHOW_NATIVE = "com.leonardo.burbujagpt.SHOW_NATIVE";

    private static final String CHANNEL_ID = "bubble_service";
    static final String NATIVE_CHANNEL_ID = "native_chat_bubble_v2";
    static final String NATIVE_SHORTCUT_ID = "native_chatgpt_conversation_v2";
    private static final String OLD_NATIVE_SHORTCUT_ID = "native_chatgpt_conversation";
    private static final String NATIVE_CATEGORY = "com.leonardo.burbujagpt.category.CHAT";
    private static final int SERVICE_NOTIFICATION_ID = 1001;
    private static final int NATIVE_BUBBLE_NOTIFICATION_ID = 1002;
    private static final String POSITION_PREFS = "bubble_position";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";

    static volatile boolean isRunning;

    private WindowManager windowManager;
    private TextView bubbleView;
    private TextView closeTarget;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams closeParams;
    private ValueAnimator snapAnimator;
    private int bubbleSizeDp;
    private boolean nearCloseTarget;
    private boolean nativeSystemBubble;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;
        nativeSystemBubble = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                && AppPreferences.MODE_NATIVE.equals(AppPreferences.getMode(this));
        startForegroundNotification();

        String selectedMode = AppPreferences.getMode(this);
        if (nativeSystemBubble || AppPreferences.MODE_WEB.equals(selectedMode)) {
            // El servicio ya esta en primer plano: inicializar Chromium ahora reduce el
            // tiempo del primer toque sin empezar a cargar datos en segundo plano.
            mainHandler.post(() -> PersistentWebViewStore.warmUp(getApplicationContext()));
        }

        if (nativeSystemBubble && !AppPreferences.isNativeFallbackRequired(this)) {
            if (tryPostNativeBubble()) {
                scheduleNativeBubbleVerification();
                return;
            }
        }

        activateOverlayFallback(nativeSystemBubble
                ? "Samsung rechazo el inicio de la burbuja nativa"
                : null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_SHOW_NATIVE.equals(action) && nativeSystemBubble) {
            if (tryPostNativeBubble()) scheduleNativeBubbleVerification();
        }
        if (ACTION_REFRESH.equals(action)) refreshBubbleAppearance();
        return START_STICKY;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (bubbleParams != null && bubbleView != null) {
            clampPosition();
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (snapAnimator != null) snapAnimator.cancel();
        removeCloseTarget();
        removeBubble();
        mainHandler.removeCallbacksAndMessages(null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NATIVE_BUBBLE_NOTIFICATION_ID);
        PersistentWebViewStore.destroyIfDetached();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void startForegroundNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager != null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "BurbujaGPT activa",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantiene visible la burbuja flotante");
            channel.setShowBadge(false);
            manager.createNotificationChannel(channel);
        }

        int immutableFlag = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE
                : 0;

        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                10,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag
        );

        Intent stopIntent = new Intent(this, BubbleService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                11,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("BurbujaGPT activa")
                .setContentText("Mantiene el chat disponible en segundo plano")
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentIntent(openPendingIntent)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        R.drawable.ic_bubble,
                        "Apagar",
                        stopPendingIntent
                ).build())
                .build();

        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    static void ensureNativeChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
                NATIVE_CHANNEL_ID,
                "ChatGPT en burbuja",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        channel.setDescription("Burbuja nativa de Android para la conversacion de ChatGPT");
        channel.setShowBadge(true);
        channel.setSound(null, null);
        channel.enableVibration(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) channel.setAllowBubbles(true);
        manager.createNotificationChannel(channel);
    }

    private boolean tryPostNativeBubble() {
        try {
            postNativeBubbleNotification();
            return true;
        } catch (RuntimeException | LinkageError error) {
            AppPreferences.recordNativeError(this, "registro", error);
            AppPreferences.setNativeFallbackRequired(this, true);
            activateOverlayFallback("Fallo al registrar la conversacion nativa");
            return false;
        }
    }

    private void postNativeBubbleNotification() {
        ensureNativeChannel(this);
        publishConversationShortcut();

        Person chatPartner = new Person.Builder()
                .setName("ChatGPT")
                .setImportant(true)
                .build();
        Person user = new Person.Builder()
                .setName("Tu")
                .build();

        Intent bubbleIntent = new Intent(this, NativeBubbleActivity.class);
        bubbleIntent.setAction(Intent.ACTION_VIEW);
        bubbleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        PendingIntent bubblePendingIntent = PendingIntent.getActivity(
                this,
                20,
                bubbleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.BubbleMetadata bubbleData = new Notification.BubbleMetadata.Builder(
                NATIVE_SHORTCUT_ID
        )
                .setDesiredHeight(dp(640))
                .setAutoExpandBubble(true)
                .setSuppressNotification(false)
                .build();

        Notification.MessagingStyle messagingStyle = new Notification.MessagingStyle(user)
                .setConversationTitle("ChatGPT")
                .addMessage("Burbuja lista", System.currentTimeMillis(), chatPartner);

        Notification notification = new Notification.Builder(this, NATIVE_CHANNEL_ID)
                .setContentTitle("ChatGPT")
                .setContentText("Burbuja nativa activa")
                .setSmallIcon(R.drawable.ic_bubble)
                .setContentIntent(bubblePendingIntent)
                .setStyle(messagingStyle)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setShortcutId(NATIVE_SHORTCUT_ID)
                .addPerson(chatPartner)
                .setBubbleMetadata(bubbleData)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) throw new IllegalStateException("NotificationManager no disponible");
        manager.notify(NATIVE_BUBBLE_NOTIFICATION_ID, notification);
    }

    private void publishConversationShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null) return;

        Icon icon = Icon.createWithResource(this, R.drawable.ic_bubble);
        Person person = new Person.Builder()
                .setName("ChatGPT")
                .setImportant(true)
                .build();
        Intent shortcutIntent = new Intent(this, NativeBubbleActivity.class);
        shortcutIntent.setAction(Intent.ACTION_VIEW);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        ShortcutInfo shortcut = new ShortcutInfo.Builder(this, NATIVE_SHORTCUT_ID)
                .setShortLabel("ChatGPT")
                .setLongLabel("ChatGPT en burbuja")
                .setIcon(icon)
                .setCategories(Collections.singleton(NATIVE_CATEGORY))
                .setActivity(new ComponentName(this, MainActivity.class))
                .setIntent(shortcutIntent)
                .setPerson(person)
                .setLongLived(true)
                .build();
        manager.removeDynamicShortcuts(Collections.singletonList(OLD_NATIVE_SHORTCUT_ID));
        manager.pushDynamicShortcut(shortcut);
    }

    private void scheduleNativeBubbleVerification() {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(() -> {
            if (!isRunning || !nativeSystemBubble || isNativeNotificationBubbled()) return;
            AppPreferences.recordNativeMessage(
                    this,
                    "Android publico la conversacion, pero Samsung no la convirtio en burbuja"
            );
            activateOverlayFallback("Samsung no mostro la burbuja del sistema");
        }, 3500);
    }

    private boolean isNativeNotificationBubbled() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return false;
        try {
            for (StatusBarNotification item : manager.getActiveNotifications()) {
                if (item.getId() == NATIVE_BUBBLE_NOTIFICATION_ID) {
                    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                            && (item.getNotification().flags & Notification.FLAG_BUBBLE) != 0;
                }
            }
        } catch (RuntimeException ignored) {
        }
        return false;
    }

    private void activateOverlayFallback(String reason) {
        nativeSystemBubble = false;
        NotificationManager notifications = getSystemService(NotificationManager.class);
        if (notifications != null) notifications.cancel(NATIVE_BUBBLE_NOTIFICATION_ID);

        if (!canDrawOverlays()) {
            if (reason != null) AppPreferences.recordNativeMessage(this, reason);
            Toast.makeText(
                    this,
                    "No se pudo crear la burbuja nativa. Permite Aparecer encima para usar el respaldo.",
                    Toast.LENGTH_LONG
            ).show();
            return;
        }

        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        if (bubbleView == null && windowManager != null) showBubble();
        if (reason != null) {
            AppPreferences.recordNativeMessage(this, reason);
            Toast.makeText(
                    this,
                    "Samsung no abrio la burbuja nativa; active el globo compatible.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    private void showBubble() {
        bubbleSizeDp = AppPreferences.getBubbleSize(this);

        TextView bubble = new TextView(this);
        bubble.setText("✦");
        bubble.setTextColor(Color.WHITE);
        bubble.setTextSize(Math.max(20, bubbleSizeDp * 0.42f));
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);
        bubble.setContentDescription("Abrir ChatGPT flotante");
        bubble.setAlpha(AppPreferences.getBubbleOpacity(this) / 100f);
        bubble.setBackground(makeOrbBackground());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) bubble.setElevation(dp(14));

        bubbleParams = new WindowManager.LayoutParams(
                dp(bubbleSizeDp),
                dp(bubbleSizeDp),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;

        SharedPreferences prefs = getSharedPreferences(POSITION_PREFS, MODE_PRIVATE);
        bubbleParams.x = prefs.getInt(KEY_X, dp(12));
        bubbleParams.y = prefs.getInt(KEY_Y, dp(180));
        clampPosition();

        bubbleView = bubble;
        setupBubbleTouch();
        windowManager.addView(bubbleView, bubbleParams);
    }

    private GradientDrawable makeOrbBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7C3AED, 0xFF0EA5E9, 0xFF10B981}
        );
        background.setShape(GradientDrawable.OVAL);
        background.setStroke(dp(2), 0xF2FFFFFF);
        return background;
    }

    private void setupBubbleTouch() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long downTime;
            private boolean moved;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        if (snapAnimator != null) snapAnimator.cancel();
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        moved = false;
                        nearCloseTarget = false;
                        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) {
                            if (!moved) showCloseTarget();
                            moved = true;
                        }
                        bubbleParams.x = initialX + dx;
                        bubbleParams.y = initialY + dy;
                        clampPosition();
                        updateCloseTargetState();
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        long elapsed = System.currentTimeMillis() - downTime;

                        if (moved && nearCloseTarget) {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                            removeCloseTarget();
                            stopSelf();
                            return true;
                        }

                        removeCloseTarget();
                        if (!moved && elapsed < 500) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                            openSelectedMode();
                        } else if (!moved) {
                            openSettings();
                        } else {
                            animateSnapToEdge();
                        }
                        return true;

                    default:
                        return false;
                }
            }
        });
    }

    private void openSelectedMode() {
        if (ChatActivity.isVisible) {
            Intent minimize = new Intent(this, ChatActivity.class);
            minimize.putExtra(ChatActivity.EXTRA_MINIMIZE, true);
            minimize.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(minimize);
            return;
        }

        MainActivity.sendVisibleTaskToBack();
        String mode = AppPreferences.getMode(this);
        if (AppPreferences.MODE_OFFICIAL.equals(mode)
                && OfficialChatLauncher.openOfficialApp(this, true)) {
            return;
        }
        if (AppPreferences.MODE_BROWSER.equals(mode)
                && OfficialChatLauncher.openBrowser(this, "https://chatgpt.com/", true)) {
            return;
        }

        Intent intent = new Intent(this, ChatActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void openSettings() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    private void showCloseTarget() {
        if (closeTarget != null) return;

        TextView target = new TextView(this);
        target.setText("×");
        target.setTextSize(36);
        target.setTextColor(Color.WHITE);
        target.setGravity(Gravity.CENTER);
        target.setAlpha(0f);

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xDDDC2626);
        background.setStroke(dp(2), 0xEEFFFFFF);
        target.setBackground(background);

        closeParams = new WindowManager.LayoutParams(
                dp(78),
                dp(78),
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
        );
        closeParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        closeParams.y = dp(30);

        closeTarget = target;
        try {
            windowManager.addView(closeTarget, closeParams);
            closeTarget.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(120).start();
        } catch (Exception e) {
            closeTarget = null;
            closeParams = null;
        }
    }

    private void updateCloseTargetState() {
        if (closeTarget == null) return;

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        float bubbleCenterX = bubbleParams.x + dp(bubbleSizeDp) / 2f;
        float bubbleCenterY = bubbleParams.y + dp(bubbleSizeDp) / 2f;
        float targetCenterX = screenWidth / 2f;
        float targetCenterY = screenHeight - dp(30 + 39);
        float dx = bubbleCenterX - targetCenterX;
        float dy = bubbleCenterY - targetCenterY;
        boolean isNear = Math.sqrt(dx * dx + dy * dy) < dp(105);

        if (isNear != nearCloseTarget) {
            nearCloseTarget = isNear;
            float scale = isNear ? 1.22f : 1f;
            closeTarget.animate().scaleX(scale).scaleY(scale).setDuration(100).start();
            bubbleView.animate().alpha(isNear ? 0.55f : AppPreferences.getBubbleOpacity(this) / 100f)
                    .setDuration(100)
                    .start();
        }
    }

    private void removeCloseTarget() {
        nearCloseTarget = false;
        if (bubbleView != null) {
            bubbleView.animate()
                    .alpha(AppPreferences.getBubbleOpacity(this) / 100f)
                    .setDuration(100)
                    .start();
        }
        if (closeTarget == null || windowManager == null) return;
        try {
            windowManager.removeView(closeTarget);
        } catch (Exception ignored) {
        }
        closeTarget = null;
        closeParams = null;
    }

    private void animateSnapToEdge() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int bubbleSize = dp(bubbleSizeDp);
        int margin = dp(8);
        int targetX = bubbleParams.x + bubbleSize / 2 < screenWidth / 2
                ? margin
                : Math.max(margin, screenWidth - bubbleSize - margin);
        int startX = bubbleParams.x;

        snapAnimator = ValueAnimator.ofInt(startX, targetX);
        snapAnimator.setDuration(190);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(animation -> {
            if (bubbleView == null) return;
            bubbleParams.x = (int) animation.getAnimatedValue();
            windowManager.updateViewLayout(bubbleView, bubbleParams);
        });
        snapAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                clampPosition();
                savePosition();
            }
        });
        snapAnimator.start();
    }

    private void refreshBubbleAppearance() {
        if (bubbleView == null || bubbleParams == null || windowManager == null) return;
        bubbleSizeDp = AppPreferences.getBubbleSize(this);
        bubbleParams.width = dp(bubbleSizeDp);
        bubbleParams.height = dp(bubbleSizeDp);
        bubbleView.setTextSize(Math.max(20, bubbleSizeDp * 0.42f));
        bubbleView.setAlpha(AppPreferences.getBubbleOpacity(this) / 100f);
        clampPosition();
        windowManager.updateViewLayout(bubbleView, bubbleParams);
        savePosition();
    }

    private void clampPosition() {
        if (bubbleParams == null) return;
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int bubbleSize = dp(bubbleSizeDp <= 0 ? 64 : bubbleSizeDp);
        bubbleParams.x = Math.max(0, Math.min(bubbleParams.x, width - bubbleSize));
        bubbleParams.y = Math.max(dp(24), Math.min(bubbleParams.y, height - bubbleSize - dp(32)));
    }

    private void savePosition() {
        if (bubbleParams == null) return;
        getSharedPreferences(POSITION_PREFS, MODE_PRIVATE)
                .edit()
                .putInt(KEY_X, bubbleParams.x)
                .putInt(KEY_Y, bubbleParams.y)
                .apply();
    }

    private void removeBubble() {
        if (bubbleView == null || windowManager == null) return;
        try {
            windowManager.removeView(bubbleView);
        } catch (Exception ignored) {
        }
        bubbleView = null;
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
