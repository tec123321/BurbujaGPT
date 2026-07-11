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
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
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
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final String TIMER_STATE_PREFS = "timer_states";
    private static final String KEY_TIMER_STATES = "states";

    private static final int BASE_EDGE_HIT_WIDTH_DP = 38;
    private static final int BASE_EDGE_LINE_WIDTH_DP = 8;
    private static final int BASE_EDGE_HEIGHT_DP = 62;
    private static final int DELETE_ZONE_HEIGHT_DP = 130;
    private static final long DOUBLE_TAP_TIMEOUT_MS = 380L;

    private static final int TIMER_RUNNING_COLOR = Color.rgb(45, 212, 191);
    private static final int TIMER_FINISHED_COLOR = Color.rgb(248, 113, 113);

    private static volatile boolean serviceRunning;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinkedHashMap<String, TimerRuntime> timers = new LinkedHashMap<>();
    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            updateAllDisplays();
            if (hasRunningClock() && overlayRoot != null) {
                handler.postDelayed(this, 100L);
            }
        }
    };

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private FrameLayout overlayRoot;
    private LinearLayout expandedPanel;
    private LinearLayout stopwatchRow;
    private LinearLayout timersContainer;
    private TextView timeView;
    private TextView resetView;
    private EdgeProgressView edgeTouchView;
    private ValueAnimator snapAnimator;

    private TextView trashTargetView;
    private WindowManager.LayoutParams trashTargetParams;
    private boolean trashTargetAttached;

    private boolean running;
    private boolean collapsed;
    private boolean sideLeft;
    private boolean timersEnabled;
    private boolean firstStartCommand = true;
    private long accumulatedMs;
    private long startedAtRealtime;
    private int panelForeground = Color.WHITE;
    private int panelSecondary = Color.rgb(190, 190, 190);
    private int panelBackground = Color.BLACK;

    public static boolean isRunning() {
        return serviceRunning;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        serviceRunning = true;
        createNotificationChannel();
        loadStopwatchState();
        reloadTimerConfiguration(true);
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
        String action = intent == null || intent.getAction() == null
                ? ACTION_SHOW
                : intent.getAction();

        if (ACTION_STOP.equals(action)) {
            pauseAllTimers();
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
            reloadTimerConfiguration(true);
            rebuildTimerRows();
            applyAppearance();
            applyConfiguredSize();
            updateAllDisplays();
            updateNotification();
            scheduleTick();
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
        pauseAllTimers();
        saveStopwatchState();
        saveTimerStates();

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
                collapsed ? edgeHitWidthPx() : dp(AppPrefs.getPanelWidth(this)),
                collapsed ? edgeHeightPx() : WindowManager.LayoutParams.WRAP_CONTENT,
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
        updateAllDisplays();

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
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setElevation(dp(9));

        stopwatchRow = new LinearLayout(this);
        stopwatchRow.setOrientation(LinearLayout.HORIZONTAL);
        stopwatchRow.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(stopwatchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        timeView = new TextView(this);
        timeView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        timeView.setGravity(Gravity.CENTER);
        timeView.setOnTouchListener(new StopwatchDragListener(this::toggleRunning, true));
        stopwatchRow.addView(
                timeView,
                new LinearLayout.LayoutParams(0, scaledDp(50), 1f));

        resetView = new TextView(this);
        resetView.setText("↺");
        resetView.setGravity(Gravity.CENTER);
        resetView.setBackgroundColor(Color.TRANSPARENT);
        resetView.setContentDescription("Reiniciar cronómetro");
        resetView.setOnClickListener(v -> resetStopwatch());
        stopwatchRow.addView(resetView, new LinearLayout.LayoutParams(scaledDp(42), scaledDp(46)));

        timersContainer = new LinearLayout(this);
        timersContainer.setOrientation(LinearLayout.VERTICAL);
        panel.addView(timersContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        rebuildTimerRows();
        return panel;
    }

    private EdgeProgressView buildEdgeTouchView() {
        EdgeProgressView touchArea = new EdgeProgressView(this);
        touchArea.setBackgroundColor(Color.TRANSPARENT);
        touchArea.setOnTouchListener(new EdgeBarTouchListener());
        return touchArea;
    }

    private void rebuildTimerRows() {
        if (timersContainer == null) return;
        timersContainer.removeAllViews();
        for (TimerRuntime timer : timers.values()) {
            timer.rowView = null;
            timer.labelView = null;
            timer.valueView = null;
            timer.resetView = null;

            if (!timersEnabled || !timer.config.visible) continue;

            View divider = new View(this);
            divider.setBackgroundColor(Color.argb(85, 128, 145, 160));
            LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Math.max(1, scaledDp(1)));
            dividerParams.leftMargin = scaledDp(10);
            dividerParams.rightMargin = scaledDp(10);
            timersContainer.addView(divider, dividerParams);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            timer.rowView = row;
            timersContainer.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout copy = new LinearLayout(this);
            copy.setOrientation(LinearLayout.VERTICAL);
            copy.setGravity(Gravity.CENTER);
            row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView label = new TextView(this);
            label.setText(timer.config.label);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            timer.labelView = label;
            copy.addView(label, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            TextView value = new TextView(this);
            value.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            value.setGravity(Gravity.CENTER);
            value.setOnTouchListener(new StopwatchDragListener(() -> toggleTimer(timer.config.id), true));
            timer.valueView = value;
            copy.addView(value, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    scaledDp(35)));

            TextView reset = new TextView(this);
            reset.setText("↺");
            reset.setGravity(Gravity.CENTER);
            reset.setBackgroundColor(Color.TRANSPARENT);
            reset.setContentDescription("Reiniciar " + timer.config.label);
            reset.setOnClickListener(v -> resetTimer(timer.config.id));
            timer.resetView = reset;
            row.addView(reset, new LinearLayout.LayoutParams(scaledDp(42), scaledDp(52)));
        }

        timersContainer.setVisibility(timersContainer.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        applyPanelScale();
        applyTimerAppearance();
        updateTimerViews();
        if (overlayRoot != null) {
            overlayRoot.post(() -> {
                if (!collapsed) snapExpanded(false);
            });
        }
    }

    private void configureEdgeLinePosition() {
        if (edgeTouchView != null) edgeTouchView.setSideLeft(sideLeft);
    }

    private void pauseWithoutReset() {
        if (running) {
            accumulatedMs = elapsedNow();
            running = false;
            saveStopwatchState();
        }
        updateAllDisplays();
        updateNotification();
        scheduleTick();
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
        updateAllDisplays();
        updateNotification();
        scheduleTick();
    }

    private void resetStopwatch() {
        accumulatedMs = 0L;
        if (running) startedAtRealtime = SystemClock.elapsedRealtime();
        saveStopwatchState();
        updateAllDisplays();
        updateNotification();
    }

    private long elapsedNow() {
        if (!running) return Math.max(0L, accumulatedMs);
        return Math.max(0L, accumulatedMs + SystemClock.elapsedRealtime() - startedAtRealtime);
    }

    private void updateTimeText() {
        if (timeView == null) return;
        timeView.setText(TimeMath.formatStopwatch(elapsedNow(), AppPrefs.showTenths(this)));
    }

    private void updateAllDisplays() {
        updateTimeText();
        updateTimerViews();
        updateEdgeIndicator();
    }

    private void scheduleTick() {
        handler.removeCallbacks(tickRunnable);
        updateAllDisplays();
        if (hasRunningClock() && overlayRoot != null) {
            handler.postDelayed(tickRunnable, 100L);
        }
    }

    private boolean hasRunningClock() {
        if (running) return true;
        for (TimerRuntime timer : timers.values()) {
            if (timer.running) return true;
        }
        return false;
    }

    private void collapseToEdge(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;

        collapsed = true;
        expandedPanel.setVisibility(View.GONE);
        edgeTouchView.setVisibility(View.VISIBLE);
        windowParams.width = edgeHitWidthPx();
        windowParams.height = edgeHeightPx();
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
            updateAllDisplays();
            scheduleTick();
        });
        saveWindowState();
    }

    private void applyConfiguredSize() {
        if (windowParams == null) return;
        applyPanelScale();
        if (collapsed) {
            windowParams.width = edgeHitWidthPx();
            windowParams.height = edgeHeightPx();
            windowParams.x = collapsedX();
            windowParams.y = clampY(windowParams.y);
            configureEdgeLinePosition();
            safeUpdateLayout();
            return;
        }

        windowParams.width = dp(AppPrefs.getPanelWidth(this));
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        safeUpdateLayout();
        if (overlayRoot != null) {
            overlayRoot.post(() -> snapExpanded(false));
        }
    }

    private void snapExpanded(boolean animate) {
        if (overlayRoot == null || windowParams == null) return;

        int width = dp(AppPrefs.getPanelWidth(this));
        windowParams.width = width;
        windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        int target = sideLeft
                ? dp(6)
                : Math.max(dp(6), screenWidth() - width - dp(6));

        windowParams.y = clampY(windowParams.y);
        safeUpdateLayout();
        if (animate) animateX(target); else setX(target);
    }

    private int collapsedX() {
        return sideLeft ? 0 : Math.max(0, screenWidth() - edgeHitWidthPx());
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
        switch (theme) {
            case AppPrefs.THEME_DARK:
                panelBackground = Color.rgb(30, 35, 41);
                panelForeground = Color.WHITE;
                panelSecondary = Color.rgb(188, 198, 208);
                break;
            case AppPrefs.THEME_LIGHT:
                panelBackground = Color.rgb(245, 247, 249);
                panelForeground = Color.rgb(16, 23, 30);
                panelSecondary = Color.rgb(74, 86, 98);
                break;
            case AppPrefs.THEME_BLUE:
                panelBackground = Color.rgb(9, 37, 55);
                panelForeground = Color.WHITE;
                panelSecondary = Color.rgb(188, 224, 235);
                break;
            case AppPrefs.THEME_BLACK:
            default:
                panelBackground = Color.rgb(1, 3, 5);
                panelForeground = Color.WHITE;
                panelSecondary = Color.rgb(180, 190, 200);
                break;
        }

        GradientDrawable background = new GradientDrawable();
        background.setColor(panelBackground);
        background.setCornerRadius(scaledDp(17));
        background.setStroke(Math.max(1, scaledDp(1)), Color.argb(105, 120, 145, 160));
        expandedPanel.setBackground(background);
        expandedPanel.setAlpha(AppPrefs.getOpacity(this) / 100f);

        timeView.setTextColor(panelForeground);
        resetView.setTextColor(panelForeground);
        applyPanelScale();
        applyTimerAppearance();
        updateAllDisplays();
    }

    private void applyPanelScale() {
        if (expandedPanel == null || stopwatchRow == null || timeView == null || resetView == null) return;
        expandedPanel.setPadding(scaledDp(7), scaledDp(4), scaledDp(4), scaledDp(4));
        stopwatchRow.setPadding(scaledDp(1), 0, 0, 0);

        LinearLayout.LayoutParams timeParams = (LinearLayout.LayoutParams) timeView.getLayoutParams();
        timeParams.height = scaledDp(50);
        timeView.setLayoutParams(timeParams);
        timeView.setPadding(scaledDp(4), 0, scaledDp(4), 0);
        timeView.setTextSize(AppPrefs.getTextSize(this) * uiScale());

        LinearLayout.LayoutParams resetParams = (LinearLayout.LayoutParams) resetView.getLayoutParams();
        resetParams.width = scaledDp(42);
        resetParams.height = scaledDp(46);
        resetView.setLayoutParams(resetParams);
        resetView.setTextSize(20f * uiScale());

        for (TimerRuntime timer : timers.values()) {
            if (timer.rowView == null) continue;
            timer.rowView.setPadding(scaledDp(5), scaledDp(2), 0, scaledDp(2));
            timer.rowView.setMinimumHeight(scaledDp(56));
            timer.labelView.setTextSize(11f * uiScale());
            timer.labelView.setPadding(scaledDp(3), scaledDp(2), scaledDp(3), 0);
            timer.valueView.setTextSize(Math.max(16f, AppPrefs.getTextSize(this) - 3f) * uiScale());
            LinearLayout.LayoutParams valueParams = (LinearLayout.LayoutParams) timer.valueView.getLayoutParams();
            valueParams.height = scaledDp(35);
            timer.valueView.setLayoutParams(valueParams);
            LinearLayout.LayoutParams timerResetParams = (LinearLayout.LayoutParams) timer.resetView.getLayoutParams();
            timerResetParams.width = scaledDp(42);
            timerResetParams.height = scaledDp(52);
            timer.resetView.setLayoutParams(timerResetParams);
            timer.resetView.setTextSize(19f * uiScale());
        }
    }

    private void applyTimerAppearance() {
        for (TimerRuntime timer : timers.values()) {
            if (timer.rowView == null) continue;
            timer.labelView.setTextColor(panelSecondary);
            timer.resetView.setTextColor(panelForeground);
            GradientDrawable rowBackground = new GradientDrawable();
            rowBackground.setColor(Color.TRANSPARENT);
            rowBackground.setCornerRadius(scaledDp(10));
            timer.rowView.setBackground(rowBackground);
        }
        updateTimerViews();
    }

    private void updateEdgeIndicator() {
        if (edgeTouchView == null) return;
        edgeTouchView.setIndicatorState(
                running,
                AppPrefs.intervalMarksEnabled(this),
                TimeMath.completedIntervals(elapsedNow(), AppPrefs.getIntervalMinutes(this)));
    }

    private void showTrashTarget(boolean active) {
        if (windowManager == null) return;

        int size = scaledDp(76);
        if (trashTargetView == null) {
            trashTargetView = new TextView(this);
            trashTargetView.setText("🗑");
            trashTargetView.setGravity(Gravity.CENTER);
            trashTargetView.setElevation(dp(12));

            int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    : WindowManager.LayoutParams.TYPE_PHONE;
            trashTargetParams = new WindowManager.LayoutParams(
                    size,
                    size,
                    overlayType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            trashTargetParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            trashTargetParams.y = dp(26);
        }
        trashTargetView.setTextSize(30f * uiScale());
        trashTargetParams.width = size;
        trashTargetParams.height = size;

        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(active ? Color.rgb(190, 45, 45) : Color.rgb(35, 40, 46));
        background.setStroke(dp(2), active ? Color.WHITE : Color.rgb(150, 165, 178));
        trashTargetView.setBackground(background);
        trashTargetView.setScaleX(active ? 1.12f : 1f);
        trashTargetView.setScaleY(active ? 1.12f : 1f);

        if (!trashTargetAttached) {
            try {
                windowManager.addView(trashTargetView, trashTargetParams);
                trashTargetAttached = true;
            } catch (RuntimeException ignored) {
            }
        } else {
            try {
                windowManager.updateViewLayout(trashTargetView, trashTargetParams);
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

    private void reloadTimerConfiguration(boolean pauseDisabled) {
        JSONObject savedStates = readTimerStates();
        List<AppPrefs.TimerConfig> configs = AppPrefs.getTimerConfigs(this);
        boolean newMasterEnabled = AppPrefs.timersEnabled(this);
        LinkedHashMap<String, TimerRuntime> next = new LinkedHashMap<>();

        for (AppPrefs.TimerConfig config : configs) {
            TimerRuntime timer = timers.get(config.id);
            if (timer == null || timer.config.durationMs != config.durationMs) {
                timer = timerFromSavedState(config, savedStates.optJSONObject(config.id));
            } else {
                timer.config = config;
            }

            if (pauseDisabled && (!newMasterEnabled || !config.visible)) {
                pauseTimer(timer);
            }
            next.put(config.id, timer);
        }

        for (Map.Entry<String, TimerRuntime> entry : timers.entrySet()) {
            if (!next.containsKey(entry.getKey())) pauseTimer(entry.getValue());
        }

        timers.clear();
        timers.putAll(next);
        timersEnabled = newMasterEnabled;
        saveTimerStates();
    }

    private TimerRuntime timerFromSavedState(AppPrefs.TimerConfig config, JSONObject state) {
        TimerRuntime timer = new TimerRuntime(config);
        if (state == null || state.optLong("duration_ms", -1L) != config.durationMs) return timer;

        timer.remainingMs = AppPrefs.clamp(
                state.optLong("remaining_ms", config.durationMs),
                0L,
                config.durationMs);
        timer.startedAtRealtime = state.optLong("started_at", SystemClock.elapsedRealtime());
        timer.running = state.optBoolean("running", false);
        timer.completionReported = state.optBoolean("completion_reported", false);

        long now = SystemClock.elapsedRealtime();
        if (timer.running && timer.startedAtRealtime > now) {
            timer.running = false;
        } else if (timer.running && timer.remainingNow() <= 0L) {
            timer.remainingMs = 0L;
            timer.running = false;
            timer.completionReported = true;
        }
        return timer;
    }

    private JSONObject readTimerStates() {
        String raw = getSharedPreferences(TIMER_STATE_PREFS, MODE_PRIVATE)
                .getString(KEY_TIMER_STATES, "{}");
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (JSONException ignored) {
            return new JSONObject();
        }
    }

    private void saveTimerStates() {
        JSONObject root = new JSONObject();
        for (TimerRuntime timer : timers.values()) {
            JSONObject state = new JSONObject();
            try {
                state.put("duration_ms", timer.config.durationMs);
                state.put("remaining_ms", timer.remainingMs);
                state.put("started_at", timer.startedAtRealtime);
                state.put("running", timer.running);
                state.put("completion_reported", timer.completionReported);
                root.put(timer.config.id, state);
            } catch (JSONException ignored) {
            }
        }
        getSharedPreferences(TIMER_STATE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(KEY_TIMER_STATES, root.toString())
                .apply();
    }

    private void toggleTimer(String id) {
        TimerRuntime timer = timers.get(id);
        if (timer == null || !timersEnabled || !timer.config.visible) return;

        if (timer.running) {
            pauseTimer(timer);
        } else {
            if (timer.remainingMs <= 0L) timer.remainingMs = timer.config.durationMs;
            timer.startedAtRealtime = SystemClock.elapsedRealtime();
            timer.running = true;
            timer.completionReported = false;
        }
        saveTimerStates();
        updateTimerViews();
        updateNotification();
        scheduleTick();
    }

    private void resetTimer(String id) {
        TimerRuntime timer = timers.get(id);
        if (timer == null) return;
        timer.remainingMs = timer.config.durationMs;
        timer.startedAtRealtime = SystemClock.elapsedRealtime();
        timer.running = false;
        timer.completionReported = false;
        saveTimerStates();
        updateTimerViews();
        updateNotification();
        scheduleTick();
    }

    private void pauseTimer(TimerRuntime timer) {
        if (timer == null || !timer.running) return;
        timer.remainingMs = timer.remainingNow();
        timer.running = false;
    }

    private void pauseAllTimers() {
        for (TimerRuntime timer : timers.values()) pauseTimer(timer);
        saveTimerStates();
    }

    private void updateTimerViews() {
        boolean stateChanged = false;
        for (TimerRuntime timer : timers.values()) {
            long remaining = timer.remainingNow();
            if (timer.running && remaining <= 0L) {
                timer.remainingMs = 0L;
                timer.running = false;
                stateChanged = true;
                if (!timer.completionReported) {
                    timer.completionReported = true;
                    Toast.makeText(this, timer.config.label + " finalizado", Toast.LENGTH_LONG).show();
                }
            }

            if (timer.valueView != null) {
                timer.valueView.setText(TimeMath.formatCountdown(remaining));
                int color;
                if (remaining <= 0L) color = TIMER_FINISHED_COLOR;
                else if (timer.running) color = TIMER_RUNNING_COLOR;
                else color = panelForeground;
                timer.valueView.setTextColor(color);
                timer.labelView.setText(timer.running
                        ? timer.config.label + "  •  en marcha"
                        : timer.config.label);
            }
        }
        if (stateChanged) {
            saveTimerStates();
            updateNotification();
        }
    }

    private int activeTimerCount() {
        int count = 0;
        for (TimerRuntime timer : timers.values()) {
            if (timer.running) count++;
        }
        return count;
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
        int measuredHeight = overlayRoot == null ? 0 : overlayRoot.getHeight();
        int estimatedExpandedHeight = scaledDp(58 + visibleTimerCount() * 57);
        int viewHeight = collapsed
                ? edgeHeightPx()
                : Math.max(measuredHeight, estimatedExpandedHeight);
        int max = Math.max(dp(8), screenHeight() - viewHeight - dp(24));
        return Math.max(dp(8), Math.min(max, value));
    }

    private int visibleTimerCount() {
        if (!timersEnabled) return 0;
        int count = 0;
        for (TimerRuntime timer : timers.values()) {
            if (timer.config.visible) count++;
        }
        return count;
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

    private float uiScale() {
        return AppPrefs.getUiScale(this) / 100f;
    }

    private int scaledDp(int value) {
        return Math.max(1, Math.round(dp(value) * uiScale()));
    }

    private int edgeHitWidthPx() {
        return scaledDp(BASE_EDGE_HIT_WIDTH_DP);
    }

    private int edgeHeightPx() {
        return scaledDp(BASE_EDGE_HEIGHT_DP);
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
        channel.setDescription("Mantiene activos el cronómetro y los temporizadores laterales");
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

        int activeTimers = activeTimerCount();
        String status = running ? "Cronómetro en marcha" : "Cronómetro en pausa";
        if (activeTimers > 0) {
            status += " · " + activeTimers + (activeTimers == 1 ? " temporizador" : " temporizadores");
        }

        return builder
                .setSmallIcon(R.drawable.ic_stopwatch)
                .setContentTitle("Cronómetro lateral")
                .setContentText(status)
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
                    if (!moved && Math.hypot(dx, dy)
                            > ViewConfiguration.get(StopwatchService.this).getScaledTouchSlop()) {
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
                        pauseAllTimers();
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
                boolean doubleTap = now - lastTapAt <= DOUBLE_TAP_TIMEOUT_MS && distance <= scaledDp(36);
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

    private final class EdgeProgressView extends View {
        private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean drawLeft;
        private boolean stopwatchRunning;
        private boolean marksEnabled;
        private int markCount;

        EdgeProgressView(Context context) {
            super(context);
            linePaint.setStyle(Paint.Style.FILL);
            strokePaint.setStyle(Paint.Style.STROKE);
            markPaint.setStyle(Paint.Style.STROKE);
            markPaint.setStrokeCap(Paint.Cap.SQUARE);
        }

        void setSideLeft(boolean value) {
            if (drawLeft == value) return;
            drawLeft = value;
            invalidate();
        }

        void setIndicatorState(boolean isRunning, boolean enabled, int completedMarks) {
            int safeCount = Math.max(0, Math.min(120, completedMarks));
            if (stopwatchRunning == isRunning && marksEnabled == enabled && markCount == safeCount) return;
            stopwatchRunning = isRunning;
            marksEnabled = enabled;
            markCount = safeCount;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float lineWidth = scaledDp(BASE_EDGE_LINE_WIDTH_DP);
            float left = drawLeft ? 0f : getWidth() - lineWidth;
            float right = left + lineWidth;
            RectF line = new RectF(left, 0f, right, getHeight());
            float radius = lineWidth / 2f;

            linePaint.setColor(stopwatchRunning ? Color.WHITE : Color.BLACK);
            canvas.drawRoundRect(line, radius, radius, linePaint);
            if (!stopwatchRunning) {
                strokePaint.setColor(Color.rgb(85, 92, 100));
                strokePaint.setStrokeWidth(Math.max(1f, scaledDp(1)));
                canvas.drawRoundRect(line, radius, radius, strokePaint);
            }

            if (!stopwatchRunning || !marksEnabled || markCount <= 0) return;
            float padding = scaledDp(4);
            float usable = Math.max(1f, getHeight() - padding * 2f);
            float preferredStep = scaledDp(5);
            float step = markCount * preferredStep <= usable
                    ? preferredStep
                    : usable / (markCount + 1f);
            float thickness = Math.max(1f, Math.min(scaledDp(2), step * 0.48f));
            float markLength = Math.min(getWidth(), scaledDp(18));
            float startX = drawLeft ? 0f : getWidth() - markLength;
            float endX = drawLeft ? markLength : getWidth();

            markPaint.setColor(Color.BLACK);
            markPaint.setStrokeWidth(thickness);
            for (int i = 1; i <= markCount; i++) {
                float y = getHeight() - padding - i * step;
                if (y < padding) break;
                canvas.drawLine(startX, y, endX, y, markPaint);
            }
        }
    }

    private static final class TimerRuntime {
        AppPrefs.TimerConfig config;
        long remainingMs;
        long startedAtRealtime;
        boolean running;
        boolean completionReported;
        LinearLayout rowView;
        TextView labelView;
        TextView valueView;
        TextView resetView;

        TimerRuntime(AppPrefs.TimerConfig config) {
            this.config = config;
            this.remainingMs = config.durationMs;
            this.startedAtRealtime = SystemClock.elapsedRealtime();
        }

        long remainingNow() {
            if (!running) return Math.max(0L, remainingMs);
            return Math.max(0L, remainingMs - (SystemClock.elapsedRealtime() - startedAtRealtime));
        }
    }
}
