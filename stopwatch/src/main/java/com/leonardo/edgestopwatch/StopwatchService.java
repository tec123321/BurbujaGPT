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
import android.view.ViewConfiguration;
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
    public static final String ACTION_EXPAND = "com.leonardo.edgestopwatch.EXPAND";
    public static final String ACTION_REFRESH = "com.leonardo.edgestopwatch.REFRESH";
    public static final String ACTION_STOP = "com.leonardo.edgestopwatch.STOP";

    private static final String CHANNEL_ID = "edge_stopwatch_service";
    private static final int NOTIFICATION_ID = 7310;
    private static final String STATE_PREFS = "stopwatch_state";
    private static final String WINDOW_PREFS = "stopwatch_window";

    private static final int EDGE_HIT_WIDTH_DP = 38;
    private static final int EDGE_LINE_WIDTH_DP = 8;
    private static final int EDGE_HEIGHT_DP = 62;
    private static final int DELETE_ZONE_HEIGHT_DP = 130;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 380L;

    private static volatile boolean serviceRunning;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateTimeText();
            if (running && overlayRoot != null) {
                handler.postDelayed(this, 100L);
            }
        }
    };

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout overlayRoot;
    private LinearLayout expandedPanel;
    private TextView timeView;
    private TextView resetView;
    private FrameLayout edgeTouchView;
    private View edgeLineView;
    private ValueAnimator snapAnimator;

    private TextView trashTargetView;
    private WindowManager.LayoutParams trashTargetParams;
    private boolean trashTargetAttached;

    private boolean running;
    private boolean collapsed;
    private boolean sideLeft;
    private boolean firstStartCommand = true;
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

        if (firstStartCommand && ACTION_SHOW.equals(action)) {
            pauseWithoutReset();
        }
        firstStartCommand = false;

        if (ACTION_TOGGLE.equals(action)) {
            toggleRunning();
        } else if (ACTION_RESET.equals(action)) {
            resetStopwatch();
        } else if (ACTION_EXPAND.equals(action) || ACTION_SHOW.equals(action)) {
            expandFromEdge(true);
        } else if (ACTION_REFRESH.equals(action)) {
            applyAppearance();
            applyConfiguredWidth();
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
        hideTrashTarget();
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
        edgeTouchView = buildEdgeTouchView();

        overlayRoot.addView(
                expandedPanel,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT));
        overlayRoot.addView(
                edgeTouchView,
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        windowParams = new WindowManager.LayoutParams(
                collapsed ? dp(EDGE_HIT_WIDTH_DP) : dp(AppPrefs.getPanelWidth(this)),
                collapsed ? dp(EDGE_HEIGHT_DP) : WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.y = clampY(loadSavedY());

        if (collapsed) {
            expandedPanel.setVisibility(View.GONE);
            edgeTouchView.setVisibility(View.VISIBLE);
            windowParams.x = collapsedX();
        } else {
            expandedPanel.setVisibility(View.VISIBLE);
            edgeTouchView.setVisibility(View.GONE);
            int width = dp(AppPrefs.getPanelWidth(this));
            windowParams.x = sideLeft ? dp(6) : Math.max(dp(6), screenWidth() - width - dp(6));
        }

        configureEdgeLinePosition();
        applyAppearance();
        updateTimeText();

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
        panel.setPadding(dp(8), dp(4), dp(4), dp(4));
        panel.setElevation(dp(8));

        timeView = new TextView(this);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setGravity(Gravity.CENTER);
        timeView.setPadding(dp(4), 0, dp(4), 0);
        timeView.setOnTouchListener(new StopwatchDragListener(this::toggleRunning, true));
        panel.addView(
                timeView,
                new LinearLayout.LayoutParams(0, dp(48), 1f));

        resetView = new TextView(this);
        resetView.setText("↺");
        resetView.setTextSize(20);
        resetView.setGravity(Gravity.CENTER);
        resetView.setBackgroundColor(Color.TRANSPARENT);
        resetView.setOnClickListener(v -> resetStopwatch());
        panel.addView(resetView, new LinearLayout.LayoutParams(dp(40), dp(44)));

        return panel;
    }

    private FrameLayout buildEdgeTouchView() {
        FrameLayout touchArea = new FrameLayout(this);
        touchArea.setBackgroundColor(Color.TRANSPARENT);
        touchArea.setOnTouchListener(new EdgeBarTouchListener());

        edgeLineView = new View(this);
        FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(
                dp(EDGE_LINE_WIDTH_DP),
                FrameLayout.LayoutParams.MATCH_PARENT);
        touchArea.addView(edgeLineView, lineParams);
        return touchArea;
    }

    private void configureEdgeLinePosition() {
        if (edgeLineView == null) return;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) edgeLineView.getLayoutParams();
        params.gravity = sideLeft ? Gravity.START : Gravity.END;
        edgeLineView.setLayoutParams(params);
    }

    private void pauseWithoutReset() {
        if (running) {
            accumulatedMs = elapsedNow();
            running = false;
            saveStopwatchState();
        }
        updateTimeText();
        updateEdgeIndicator();
        updateNotification();
        handler.removeCallbacks(tickRunnable);
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
        updateEdgeIndicator();
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
    }

    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        updateTimeText();
        if (running && overlayRoot != null) {
            handler.postDelayed(tickRunnable, 100L);
        }
    }

    private void collapseToEdge(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;

        collapsed = true;
        expandedPanel.setVisibility(View.GONE);
        edgeTouchView.setVisibility(View.VISIBLE);
        windowParams.width = dp(EDGE_HIT_WIDTH_DP);
        windowParams.height = dp(EDGE_HEIGHT_DP);
        windowParams.y = clampY(windowParams.y);
        windowParams.x = collapsedX();
        configureEdgeLinePosition();
        updateEdgeIndicator();
        safeUpdateLayout();

        if (animate) animateX(collapsedX());
        saveWindowState();
    }

    private void expandFromEdge(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;

        collapsed = false;
        edgeTouchView.setVisibility(View.GONE);
        expandedPanel.setVisibility(View.VISIBLE);
        windowParams.width = dp(AppPrefs.getPanelWidth(this));
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        applyAppearance();
        safeUpdateLayout();

        overlayRoot.post(() -> {
            snapExpanded(animate);
            updateTimeText();
            scheduleTick();
        });
        saveWindowState();
    }

    private void applyConfiguredWidth() {
        if (windowParams == null || collapsed) return;
        windowParams.width = dp(AppPrefs.getPanelWidth(this));
        safeUpdateLayout();
        if (overlayRoot != null) {
            overlayRoot.post(() -> snapExpanded(false));
        }
    }

    private void snapExpanded(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;

        int width = dp(AppPrefs.getPanelWidth(this));
        windowParams.width = width;
        int target = sideLeft
                ? dp(6)
                : Math.max(dp(6), screenWidth() - width - dp(6));

        windowParams.y = clampY(windowParams.y);
        safeUpdateLayout();
        if (animate) animateX(target); else setX(target);
    }

    private int collapsedX() {
        return sideLeft ? 0 : Math.max(0, screenWidth() - dp(EDGE_HIT_WIDTH_DP));
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
        if (expandedPanel == null || timeView == null || resetView == null) return;

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
        expandedPanel.setAlpha(AppPrefs.getOpacity(this) / 100f);

        timeView.setTextSize(AppPrefs.getTextSize(this));
        timeView.setTextColor(foreground);
        resetView.setTextColor(foreground);
        updateEdgeIndicator();
    }

    private void updateEdgeIndicator() {
        if (edgeLineView == null) return;
        GradientDrawable line = new GradientDrawable();
        line.setColor(running ? Color.WHITE : Color.BLACK);
        line.setCornerRadius(dp(4));
        if (!running) line.setStroke(dp(1), Color.rgb(85, 85, 85));
        edgeLineView.setBackground(line);
        edgeLineView.setAlpha(1f);
    }

    private void showTrashTarget(boolean active) {
        if (windowManager == null) return;

        if (trashTargetView == null) {
            trashTargetView = new TextView(this);
            trashTargetView.setText("🗑");
            trashTargetView.setTextSize(30);
            trashTargetView.setGravity(Gravity.CENTER);
            trashTargetView.setElevation(dp(12));

            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            trashTargetParams = new WindowManager.LayoutParams(
                    dp(76),
                    dp(76),
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            trashTargetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            trashTargetParams.y = dp(26);
        }

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(active ? Color.rgb(190, 45, 45) : Color.rgb(55, 55, 55));
        background.setStroke(dp(2), active ? Color.WHITE : Color.rgb(170, 170, 170));
        trashTargetView.setBackground(background);
        trashTargetView.setScaleX(active ? 1.12f : 1f);
        trashTargetView.setScaleY(active ? 1.12f : 1f);

        if (!trashTargetAttached) {
            try {
                windowManager.addView(trashTargetView, trashTargetParams);
                trashTargetAttached = true;
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void hideTrashTarget() {
        if (!trashTargetAttached || windowManager == null || trashTargetView == null) return;
        try {
            windowManager.removeView(trashTargetView);
        } catch (RuntimeException ignored) {
        }
        trashTargetAttached = false;
    }

    private boolean isInsideDeleteZone(float rawY) {
        return rawY >= screenHeight() - dp(DELETE_ZONE_HEIGHT_DP);
    }

    private void loadStopwatchState() {
        SharedPreferences prefs = getSharedPreferences(STATE_PREFS, MODE_PRIVATE);
        boolean initialized = prefs.getBoolean("initialized", false);

        if (!initialized) {
            accumulatedMs = 0L;
            startedAtRealtime = SystemClock.elapsedRealtime();
            running = false;
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
        int viewHeight = collapsed ? dp(EDGE_HEIGHT_DP) : dp(56);
        int max = Math.max(dp(8), screenHeight() - viewHeight - dp(24));
        return Math.max(dp(8), Math.min(max, value));
    }

    private int clampDragY(int value) {
        return Math.max(0, Math.min(screenHeight() - dp(12), value));
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
                .setSmallIcon(R.drawable.ic_stopwatch)
                .setContentTitle("Cronómetro lateral")
                .setContentText(running ? "En marcha" : "En pausa")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(R.drawable.ic_stopwatch, running ? "Pausar" : "Continuar", togglePending)
                .addAction(R.drawable.ic_stopwatch, "Reiniciar", resetPending)
                .addAction(R.drawable.ic_stopwatch, "Cerrar", stopPending)
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
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private class StopwatchDragListener implements View.OnTouchListener {
        private final Runnable tapAction;
        private final boolean collapseOnSwipe;
        private float downRawX;
        private float downRawY;
        private int startX;
        private int startY;
        private boolean moved;

        StopwatchDragListener(Runnable tapAction, boolean collapseOnSwipe) {
            this.tapAction = tapAction;
            this.collapseOnSwipe = collapseOnSwipe;
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
                    if (!moved && Math.hypot(dx, dy) > ViewConfiguration.get(StopwatchService.this).getScaledTouchSlop()) {
                        moved = true;
                    }
                    if (moved) {
                        windowParams.x = startX + Math.round(dx);
                        windowParams.y = clampDragY(startY + Math.round(dy));
                        safeUpdateLayout();
                        showTrashTarget(isInsideDeleteZone(event.getRawY()));
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float totalDx = event.getRawX() - downRawX;
                    float totalDy = event.getRawY() - downRawY;
                    boolean delete = moved && isInsideDeleteZone(event.getRawY());
                    hideTrashTarget();

                    if (delete) {
                        stopSelf();
                        return true;
                    }

                    if (!moved && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        tapAction.run();
                        return true;
                    }

                    int center = windowParams.x + Math.max(dp(6), view.getWidth() / 2);
                    boolean nearLeftEdge = windowParams.x <= dp(36);
                    boolean nearRightEdge = windowParams.x + Math.max(view.getWidth(), dp(40))
                            >= screenWidth() - dp(36);

                    if (collapsed) {
                        sideLeft = center < screenWidth() / 2;
                        collapseToEdge(true);
                    } else if (collapseOnSwipe
                            && ((Math.abs(totalDx) >= dp(56)
                            && Math.abs(totalDx) > Math.abs(totalDy) * 1.1f)
                            || nearLeftEdge
                            || nearRightEdge)) {
                        if (nearLeftEdge) sideLeft = true;
                        else if (nearRightEdge) sideLeft = false;
                        else sideLeft = totalDx < 0f;
                        collapseToEdge(true);
                    } else {
                        sideLeft = center < screenWidth() / 2;
                        snapExpanded(true);
                        saveWindowState();
                    }
                    return true;

                default:
                    return false;
            }
        }
    }

    private final class EdgeBarTouchListener extends StopwatchDragListener {
        private long lastTapAt;
        private float lastTapX;
        private float lastTapY;

        EdgeBarTouchListener() {
            super(() -> { }, false);
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                long now = SystemClock.uptimeMillis();
                float distance = (float) Math.hypot(
                        event.getRawX() - lastTapX,
                        event.getRawY() - lastTapY);
                boolean doubleTap = now - lastTapAt <= DOUBLE_TAP_TIMEOUT_MS && distance <= dp(36);
                lastTapAt = now;
                lastTapX = event.getRawX();
                lastTapY = event.getRawY();

                if (doubleTap) {
                    hideTrashTarget();
                    expandFromEdge(true);
                    lastTapAt = 0L;
                    return true;
                }
            }
            return super.onTouch(view, event);
        }
    }
}
