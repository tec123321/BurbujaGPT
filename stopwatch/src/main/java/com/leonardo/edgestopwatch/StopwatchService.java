package com.leonardo.edgestopwatch;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

public class StopwatchService extends Service {
    public static final String ACTION_SHOW = "com.leonardo.edgestopwatch.SHOW";
    public static final String ACTION_TOGGLE = "com.leonardo.edgestopwatch.TOGGLE";
    public static final String ACTION_RESET = "com.leonardo.edgestopwatch.RESET";
    public static final String ACTION_COLLAPSE = "com.leonardo.edgestopwatch.COLLAPSE";
    public static final String ACTION_EXPAND = "com.leonardo.edgestopwatch.EXPAND";
    public static final String ACTION_REFRESH = "com.leonardo.edgestopwatch.REFRESH";
    public static final String ACTION_STOP = "com.leonardo.edgestopwatch.STOP";

    private static final String CHANNEL_ID = "edge_stopwatch_service";
    private static final int NOTIFICATION_ID = 7310;
    private static final String STATE_PREFS = "stopwatch_state";
    private static final String WINDOW_PREFS = "stopwatch_window";

    private static volatile boolean serviceRunning;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override public void run() {
            updateTimeText();
            if (running && !collapsed && overlayRoot != null) {
                handler.postDelayed(this, 50L);
            }
        }
    };

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout overlayRoot;
    private LinearLayout expandedPanel;
    private TextView timeView;
    private TextView toggleView;
    private TextView resetView;
    private TextView collapseView;
    private TextView closeView;
    private TextView edgeHandle;
    private ValueAnimator snapAnimator;

    private boolean running;
    private boolean collapsed;
    private boolean sideLeft;
    private long accumulatedMs;
    private long startedAtRealtime;

    public static boolean isRunning() {
        return serviceRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceRunning = true;
        createNotificationChannel();
        loadStopwatchState();
        startForeground(NOTIFICATION_ID, buildNotification());

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa el permiso ‘Aparecer encima’", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (overlayRoot == null && Settings.canDrawOverlays(this)) {
            if (windowManager == null) {
                windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            }
            createOverlay();
        }

        if (ACTION_TOGGLE.equals(action)) {
            toggleRunning();
        } else if (ACTION_RESET.equals(action)) {
            resetStopwatch();
        } else if (ACTION_COLLAPSE.equals(action)) {
            collapseToEdge(true);
        } else if (ACTION_EXPAND.equals(action) || ACTION_SHOW.equals(action)) {
            expandFromEdge(true);
        } else if (ACTION_REFRESH.equals(action)) {
            applyAppearance();
            updateTimeText();
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        serviceRunning = false;
        handler.removeCallbacksAndMessages(null);
        if (snapAnimator != null) snapAnimator.cancel();
        saveStopwatchState();
        if (windowManager != null && overlayRoot != null) {
            try {
                windowManager.removeView(overlayRoot);
            } catch (RuntimeException ignored) {
            }
        }
        overlayRoot = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        super.onDestroy();
    }

    private void createOverlay() {
        if (overlayRoot != null || windowManager == null) return;

        loadWindowState();

        overlayRoot = new FrameLayout(this);
        expandedPanel = buildExpandedPanel();
        edgeHandle = buildEdgeHandle();
        overlayRoot.addView(expandedPanel);
        overlayRoot.addView(edgeHandle);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        windowParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;

        if (collapsed) {
            expandedPanel.setVisibility(View.GONE);
            edgeHandle.setVisibility(View.VISIBLE);
            windowParams.width = dp(38);
            windowParams.height = dp(64);
            windowParams.x = collapsedX();
        } else {
            expandedPanel.setVisibility(View.VISIBLE);
            edgeHandle.setVisibility(View.GONE);
            windowParams.x = sideLeft ? dp(6) : Math.max(dp(6), screenWidth() - dp(260));
        }
        windowParams.y = clampY(loadSavedY());

        applyAppearance();
        try {
            windowManager.addView(overlayRoot, windowParams);
        } catch (RuntimeException error) {
            overlayRoot = null;
            Toast.makeText(this, "No se pudo mostrar el cronómetro flotante", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }

        overlayRoot.post(() -> {
            if (collapsed) {
                collapseToEdge(false);
            } else {
                snapExpanded(false);
            }
            scheduleTick();
        });
    }

    private LinearLayout buildExpandedPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(10), dp(4), dp(4), dp(4));
        panel.setElevation(dp(8));

        timeView = new TextView(this);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setGravity(Gravity.CENTER);
        timeView.setMinWidth(dp(112));
        timeView.setPadding(dp(4), 0, dp(6), 0);
        timeView.setOnTouchListener(new DragTouchListener(this::toggleRunning));
        panel.addView(timeView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(48)));

        toggleView = control("Ⅱ", this::toggleRunning);
        resetView = control("↺", this::resetStopwatch);
        collapseView = control("‹", () -> collapseToEdge(true));
        closeView = control("×", this::stopSelf);

        panel.addView(toggleView);
        panel.addView(resetView);
        panel.addView(collapseView);
        panel.addView(closeView);
        return panel;
    }

    private TextView buildEdgeHandle() {
        TextView handle = new TextView(this);
        handle.setTextSize(24);
        handle.setTypeface(Typeface.DEFAULT_BOLD);
        handle.setElevation(dp(8));
        handle.setOnTouchListener(new DragTouchListener(() -> expandFromEdge(true)));
        return handle;
    }

    private TextView control(String text, Runnable action) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(20);
        view.setGravity(Gravity.CENTER);
        view.setMinWidth(dp(38));
        view.setMinHeight(dp(44));
        view.setPadding(dp(2), 0, dp(2), 0);
        view.setBackgroundColor(Color.TRANSPARENT);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void toggleRunning() {
        if (running) {
            accumulatedMs = elapsedNow();
            running = false;
        } else {
            startedAtRealtime = SystemClock.elapsedRealtime();
            running = true;
        }
        saveStopwatchState();
        updateTimeText();
        updateNotification();
        scheduleTick();
    }

    private void resetStopwatch() {
        accumulatedMs = 0L;
        if (running) startedAtRealtime = SystemClock.elapsedRealtime();
        saveStopwatchState();
        updateTimeText();
        updateNotification();
    }

    private long elapsedNow() {
        if (!running) return Math.max(0L, accumulatedMs);
        return Math.max(0L, accumulatedMs + SystemClock.elapsedRealtime() - startedAtRealtime);
    }

    private void updateTimeText() {
        if (timeView == null) return;
        long elapsed = elapsedNow();
        long totalSeconds = elapsed / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds / 60L) % 60L;
        long seconds = totalSeconds % 60L;
        boolean showTenths = AppPrefs.showTenths(this);
        String value;
        if (hours > 0L) {
            value = showTenths
                    ? String.format(Locale.US, "%02d:%02d:%02d.%d", hours, minutes, seconds, (elapsed / 100L) % 10L)
                    : String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            value = showTenths
                    ? String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, (elapsed / 100L) % 10L)
                    : String.format(Locale.US, "%02d:%02d", minutes, seconds);
        }
        timeView.setText(value);
        if (toggleView != null) toggleView.setText(running ? "Ⅱ" : "▶");
    }

    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        updateTimeText();
        if (running && !collapsed && overlayRoot != null) {
            handler.postDelayed(tickRunnable, 50L);
        }
    }

    private void collapseToEdge(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;
        collapsed = true;
        saveWindowState();
        handler.removeCallbacks(tickRunnable);

        expandedPanel.setVisibility(View.GONE);
        edgeHandle.setVisibility(View.VISIBLE);
        windowParams.width = dp(38);
        windowParams.height = dp(64);
        windowParams.y = clampY(windowParams.y);
        configureHandleDirection();
        safeUpdateLayout();

        int target = collapsedX();
        if (animate) animateX(target); else setX(target);
    }

    private void expandFromEdge(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;
        if (!collapsed && ACTION_SHOW != null) {
            snapExpanded(animate);
            scheduleTick();
            return;
        }
        collapsed = false;
        saveWindowState();

        edgeHandle.setVisibility(View.GONE);
        expandedPanel.setVisibility(View.VISIBLE);
        windowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        safeUpdateLayout();
        overlayRoot.post(() -> {
            snapExpanded(animate);
            scheduleTick();
        });
    }

    private void snapExpanded(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;
        int width = overlayRoot.getWidth();
        if (width <= 0) width = dp(250);
        int target = sideLeft
                ? dp(6)
                : Math.max(dp(6), screenWidth() - width - dp(6));
        windowParams.y = clampY(windowParams.y);
        if (collapseView != null) collapseView.setText(sideLeft ? "‹" : "›");
        safeUpdateLayout();
        if (animate) animateX(target); else setX(target);
    }

    private int collapsedX() {
        int width = dp(38);
        int exposed = dp(15);
        return sideLeft ? -(width - exposed) : screenWidth() - exposed;
    }

    private void configureHandleDirection() {
        if (edgeHandle == null) return;
        if (sideLeft) {
            edgeHandle.setText("›");
            edgeHandle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            edgeHandle.setPadding(0, 0, dp(2), 0);
        } else {
            edgeHandle.setText("‹");
            edgeHandle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            edgeHandle.setPadding(dp(2), 0, 0, 0);
        }
    }

    private void animateX(int targetX) {
        if (windowParams == null) return;
        if (snapAnimator != null) snapAnimator.cancel();
        snapAnimator = ValueAnimator.ofInt(windowParams.x, targetX);
        snapAnimator.setDuration(170L);
        snapAnimator.addUpdateListener(animation -> setX((Integer) animation.getAnimatedValue()));
        snapAnimator.start();
    }

    private void setX(int value) {
        if (windowParams == null) return;
        windowParams.x = value;
        safeUpdateLayout();
        saveWindowState();
    }

    private void safeUpdateLayout() {
        if (windowManager == null || overlayRoot == null || windowParams == null) return;
        try {
            windowManager.updateViewLayout(overlayRoot, windowParams);
        } catch (RuntimeException ignored) {
        }
    }

    private void applyAppearance() {
        if (expandedPanel == null || edgeHandle == null) return;
        int theme = AppPrefs.getTheme(this);
        int background;
        int foreground;
        switch (theme) {
            case AppPrefs.THEME_DARK:
                background = Color.rgb(48, 52, 57);
                foreground = Color.WHITE;
                break;
            case AppPrefs.THEME_LIGHT:
                background = Color.rgb(245, 245, 245);
                foreground = Color.rgb(20, 20, 20);
                break;
            case AppPrefs.THEME_BLUE:
                background = Color.rgb(25, 78, 112);
                foreground = Color.WHITE;
                break;
            case AppPrefs.THEME_BLACK:
            default:
                background = Color.BLACK;
                foreground = Color.WHITE;
                break;
        }

        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(background);
        panelBackground.setCornerRadius(dp(16));
        expandedPanel.setBackground(panelBackground);

        GradientDrawable handleBackground = new GradientDrawable();
        handleBackground.setColor(background);
        handleBackground.setCornerRadius(dp(14));
        edgeHandle.setBackground(handleBackground);

        float alpha = AppPrefs.getOpacity(this) / 100f;
        expandedPanel.setAlpha(alpha);
        edgeHandle.setAlpha(alpha);

        timeView.setTextSize(AppPrefs.getTextSize(this));
        timeView.setTextColor(foreground);
        toggleView.setTextColor(foreground);
        resetView.setTextColor(foreground);
        collapseView.setTextColor(foreground);
        closeView.setTextColor(foreground);
        edgeHandle.setTextColor(foreground);
        configureHandleDirection();
    }

    private void loadStopwatchState() {
        SharedPreferences prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        boolean initialized = prefs.getBoolean("initialized", false);
        if (!initialized) {
            accumulatedMs = 0L;
            startedAtRealtime = SystemClock.elapsedRealtime();
            running = true;
            saveStopwatchState();
            return;
        }
        accumulatedMs = Math.max(0L, prefs.getLong("accumulated_ms", 0L));
        startedAtRealtime = prefs.getLong("started_at", SystemClock.elapsedRealtime());
        running = prefs.getBoolean("running", false);
        if (running && startedAtRealtime > SystemClock.elapsedRealtime()) {
            running = false;
            accumulatedMs = 0L;
        }
    }

    private void saveStopwatchState() {
        getSharedPreferences(STATE_PREFS, MODE_PRIVATE).edit()
                .putBoolean("initialized", true)
                .putLong("accumulated_ms", accumulatedMs)
                .putLong("started_at", startedAtRealtime)
                .putBoolean("running", running)
                .apply();
    }

    private void loadWindowState() {
        SharedPreferences prefs = getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE);
        sideLeft = prefs.getBoolean("side_left", false);
        collapsed = prefs.getBoolean("collapsed", false);
    }

    private int loadSavedY() {
        return getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE).getInt("y", dp(120));
    }

    private void saveWindowState() {
        if (windowParams == null) return;
        getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE).edit()
                .putBoolean("side_left", sideLeft)
                .putBoolean("collapsed", collapsed)
                .putInt("y", windowParams.y)
                .apply();
    }

    private int clampY(int value) {
        int viewHeight = overlayRoot == null || overlayRoot.getHeight() <= 0
                ? (collapsed ? dp(64) : dp(56))
                : overlayRoot.getHeight();
        int max = Math.max(dp(8), screenHeight() - viewHeight - dp(24));
        return Math.max(dp(8), Math.min(max, value));
    }

    private int screenWidth() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.widthPixels;
    }

    private int screenHeight() {
        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        return metrics.heightPixels;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Cronómetro flotante",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Mantiene activo el cronómetro lateral");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                pendingFlags());

        PendingIntent togglePending = servicePending(ACTION_TOGGLE, 2);
        PendingIntent resetPending = servicePending(ACTION_RESET, 3);
        PendingIntent stopPending = servicePending(ACTION_STOP, 4);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder
                .setSmallIcon(com.leonardo.edgestopwatch.R.drawable.ic_stopwatch)
                .setContentTitle("Cronómetro lateral")
                .setContentText(running ? "En marcha" : "En pausa")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(com.leonardo.edgestopwatch.R.drawable.ic_stopwatch, running ? "Pausar" : "Continuar", togglePending)
                .addAction(com.leonardo.edgestopwatch.R.drawable.ic_stopwatch, "Reiniciar", resetPending)
                .addAction(com.leonardo.edgestopwatch.R.drawable.ic_stopwatch, "Cerrar", stopPending)
                .build();
    }

    private PendingIntent servicePending(String action, int requestCode) {
        Intent intent = new Intent(this, StopwatchService.class);
        intent.setAction(action);
        return PendingIntent.getService(this, requestCode, intent, pendingFlags());
    }

    private int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private final class DragTouchListener implements View.OnTouchListener {
        private final Runnable tapAction;
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private boolean moved;

        DragTouchListener(Runnable tapAction) {
            this.tapAction = tapAction;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (windowParams == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (snapAnimator != null) snapAnimator.cancel();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startX = windowParams.x;
                    startY = windowParams.y;
                    moved = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downRawX;
                    float dy = event.getRawY() - downRawY;
                    if (!moved && Math.hypot(dx, dy) > dp(5)) moved = true;
                    if (moved) {
                        windowParams.x = startX + Math.round(dx);
                        windowParams.y = clampY(startY + Math.round(dy));
                        safeUpdateLayout();
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        tapAction.run();
                    } else {
                        int center = windowParams.x + Math.max(dp(20), view.getWidth() / 2);
                        sideLeft = center < screenWidth() / 2;
                        if (collapsed) collapseToEdge(true); else snapExpanded(true);
                        saveWindowState();
                    }
                    return true;

                default:
                    return false;
            }
        }
    }
}
