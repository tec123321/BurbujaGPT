package com.leonardo.traductorleo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TranslatorService extends Service {
    public static final String ACTION_START = "com.leonardo.traductorleo.START";
    public static final String ACTION_STOP = "com.leonardo.traductorleo.STOP";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";

    private static final String CHANNEL_ID = "traductor_leo_active";
    private static final int NOTIFICATION_ID = 2071;
    private static final long CAPTURE_INTERVAL_MS = 900L;
    private static final long OVERLAY_HIDE_DELAY_MS = 100L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean captureRequested = new AtomicBoolean(false);
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final AtomicInteger generation = new AtomicInteger(0);
    private final GoogleTranslateClient translateClient = new GoogleTranslateClient();

    private HandlerThread captureThread;
    private Handler captureHandler;
    private ExecutorService ocrExecutor;
    private ExecutorService networkPool;
    private TextRecognizer recognizer;

    private WindowManager windowManager;
    private TranslationOverlayView overlayView;
    private TextView bubbleView;
    private WindowManager.LayoutParams overlayParams;
    private WindowManager.LayoutParams bubbleParams;

    private MediaProjection mediaProjection;
    private MediaProjection.Callback projectionCallback;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    private int screenWidth;
    private int screenHeight;
    private int densityDpi;
    private volatile long lastObservedHash = Long.MIN_VALUE;
    private volatile long lastProcessedHash = Long.MIN_VALUE;
    private volatile int stableHits = 0;
    private volatile boolean stopping = false;

    private final Runnable captureLoop = new Runnable() {
        @Override
        public void run() {
            if (mediaProjection == null || stopping) {
                return;
            }
            if (!processing.get() && !captureRequested.get()) {
                requestCleanFrame();
            }
            mainHandler.postDelayed(this, CAPTURE_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        captureThread = new HandlerThread("TraductorLeoCapture");
        captureThread.start();
        captureHandler = new Handler(captureThread.getLooper());
        ocrExecutor = Executors.newSingleThreadExecutor();
        networkPool = Executors.newFixedThreadPool(4);
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTranslator();
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action) || intent == null) {
            return START_NOT_STICKY;
        }

        startAsForeground();
        if (!Settings.canDrawOverlays(this)) {
            stopTranslator();
            return START_NOT_STICKY;
        }
        if (mediaProjection != null) {
            forceRefresh();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        Intent resultData;
        if (Build.VERSION.SDK_INT >= 33) {
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            //noinspection deprecation
            resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (resultCode == 0 || resultData == null) {
            stopTranslator();
            return START_NOT_STICKY;
        }

        try {
            showOverlayWindows();
            startProjection(resultCode, resultData);
            getSharedPreferences("translator", MODE_PRIVATE).edit().putBoolean("running", true).apply();
        } catch (Exception ignored) {
            stopTranslator();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Traductor Leo activo",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Mantiene activa la captura y traducción de pantalla.");
        channel.setShowBadge(false);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                1,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, TranslatorService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                2,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Traductor Leo activo")
                .setContentText("Desliza normalmente; las traducciones se actualizarán al detenerte.")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Detener", stopPending);
        if (Build.VERSION.SDK_INT >= 31) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return builder.build();
    }

    private void startAsForeground() {
        Notification notification = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            );
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void showOverlayWindows() {
        updateScreenMetrics();

        overlayView = new TranslationOverlayView(this);
        overlayView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        overlayParams.gravity = Gravity.TOP | Gravity.START;
        overlayParams.alpha = 0.79f;
        windowManager.addView(overlayView, overlayParams);

        bubbleView = new TextView(this);
        bubbleView.setText("↻");
        bubbleView.setTextColor(Color.WHITE);
        bubbleView.setTextSize(24);
        bubbleView.setGravity(Gravity.CENTER);
        bubbleView.setContentDescription("Actualizar traducciones");
        bubbleView.setElevation(dp(8));
        GradientDrawable bubbleBackground = new GradientDrawable();
        bubbleBackground.setShape(GradientDrawable.OVAL);
        bubbleBackground.setColor(Color.rgb(26, 115, 232));
        bubbleBackground.setStroke(dp(2), Color.WHITE);
        bubbleView.setBackground(bubbleBackground);

        int bubbleSize = dp(54);
        bubbleParams = new WindowManager.LayoutParams(
                bubbleSize,
                bubbleSize,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.END;
        bubbleParams.x = dp(8);
        bubbleParams.y = Math.max(dp(80), screenHeight / 2 - bubbleSize / 2);
        bubbleView.setOnTouchListener(new BubbleTouchListener());
        bubbleView.setOnLongClickListener(v -> {
            stopTranslator();
            return true;
        });
        windowManager.addView(bubbleView, bubbleParams);
    }

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void startProjection(int resultCode, Intent resultData) {
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mediaProjection = manager.getMediaProjection(resultCode, resultData);
        if (mediaProjection == null) {
            throw new IllegalStateException("MediaProjection unavailable");
        }

        projectionCallback = new MediaProjection.Callback() {
            @Override
            public void onStop() {
                mainHandler.post(() -> {
                    mediaProjection = null;
                    stopTranslator();
                });
            }
        };
        mediaProjection.registerCallback(projectionCallback, mainHandler);

        updateScreenMetrics();
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3);
        imageReader.setOnImageAvailableListener(this::onImageAvailable, captureHandler);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "TraductorLeoCapture",
                screenWidth,
                screenHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                captureHandler
        );
        mainHandler.postDelayed(captureLoop, 450L);
    }

    private void updateScreenMetrics() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        densityDpi = metrics.densityDpi;
        if (Build.VERSION.SDK_INT >= 30) {
            Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
            screenWidth = Math.max(1, bounds.width());
            screenHeight = Math.max(1, bounds.height());
        } else {
            DisplayMetrics real = new DisplayMetrics();
            //noinspection deprecation
            windowManager.getDefaultDisplay().getRealMetrics(real);
            screenWidth = Math.max(1, real.widthPixels);
            screenHeight = Math.max(1, real.heightPixels);
            densityDpi = real.densityDpi;
        }
    }

    private void requestCleanFrame() {
        setOverlayCaptureVisibility(false);
        mainHandler.postDelayed(() -> {
            if (mediaProjection == null || stopping) {
                setOverlayCaptureVisibility(true);
                return;
            }
            captureRequested.set(true);
            mainHandler.postDelayed(() -> setOverlayCaptureVisibility(true), 550L);
        }, OVERLAY_HIDE_DELAY_MS);
    }

    private void onImageAvailable(ImageReader reader) {
        Image image = null;
        try {
            image = reader.acquireLatestImage();
            if (image == null) {
                return;
            }
            if (!captureRequested.compareAndSet(true, false)) {
                return;
            }
            Bitmap bitmap = imageToBitmap(image);
            mainHandler.post(() -> setOverlayCaptureVisibility(true));
            if (bitmap != null) {
                handleCapturedBitmap(bitmap);
            }
        } catch (Exception ignored) {
            mainHandler.post(() -> setOverlayCaptureVisibility(true));
        } finally {
            if (image != null) {
                image.close();
            }
        }
    }

    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        if (planes.length == 0) {
            return null;
        }
        Image.Plane plane = planes[0];
        ByteBuffer buffer = plane.getBuffer();
        int pixelStride = plane.getPixelStride();
        int rowStride = plane.getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        int paddedWidth = screenWidth + Math.max(0, rowPadding / Math.max(1, pixelStride));

        Bitmap padded = Bitmap.createBitmap(paddedWidth, screenHeight, Bitmap.Config.ARGB_8888);
        buffer.rewind();
        padded.copyPixelsFromBuffer(buffer);
        if (paddedWidth == screenWidth) {
            return padded;
        }
        Bitmap cropped = Bitmap.createBitmap(padded, 0, 0, screenWidth, screenHeight);
        padded.recycle();
        return cropped;
    }

    private void handleCapturedBitmap(Bitmap bitmap) {
        if (processing.get()) {
            bitmap.recycle();
            return;
        }

        long hash = perceptualHash(bitmap);
        if (lastObservedHash == Long.MIN_VALUE || Long.bitCount(hash ^ lastObservedHash) > 9) {
            lastObservedHash = hash;
            stableHits = 0;
            bitmap.recycle();
            return;
        }

        stableHits++;
        if (lastProcessedHash != Long.MIN_VALUE && Long.bitCount(hash ^ lastProcessedHash) <= 5) {
            bitmap.recycle();
            return;
        }
        if (!processing.compareAndSet(false, true)) {
            bitmap.recycle();
            return;
        }

        int generationId = generation.get();
        InputImage inputImage = InputImage.fromBitmap(bitmap, 0);
        recognizer.process(inputImage)
                .addOnSuccessListener(ocrExecutor, result -> processRecognizedText(result, hash, generationId))
                .addOnFailureListener(ocrExecutor, error -> processing.set(false))
                .addOnCompleteListener(ocrExecutor, task -> bitmap.recycle());
    }

    private void processRecognizedText(Text result, long frameHash, int generationId) {
        if (generationId != generation.get() || stopping) {
            processing.set(false);
            return;
        }
        List<SourceRegion> sourceRegions = extractRegions(result);
        if (sourceRegions.isEmpty()) {
            mainHandler.post(() -> {
                if (overlayView != null) {
                    overlayView.setRegions(Collections.emptyList());
                }
            });
            lastProcessedHash = frameHash;
            processing.set(false);
            return;
        }

        List<TranslationOverlayView.Region> translatedRegions =
                Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(sourceRegions.size());
        for (SourceRegion sourceRegion : sourceRegions) {
            networkPool.execute(() -> {
                try {
                    String translated = translateClient.translateToSpanish(sourceRegion.text);
                    if (!translated.isEmpty() && !equivalent(sourceRegion.text, translated)) {
                        translatedRegions.add(
                                new TranslationOverlayView.Region(sourceRegion.bounds, translated)
                        );
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await(20, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        if (generationId != generation.get() || stopping) {
            processing.set(false);
            return;
        }
        translatedRegions.sort(Comparator.comparingInt(region -> region.bounds.top));
        List<TranslationOverlayView.Region> finalRegions = new ArrayList<>(translatedRegions);
        mainHandler.post(() -> {
            if (overlayView != null) {
                overlayView.setRegions(finalRegions);
            }
        });
        lastProcessedHash = frameHash;
        processing.set(false);
    }

    private List<SourceRegion> extractRegions(Text result) {
        List<SourceRegion> regions = new ArrayList<>();
        int topGuard = dp(58);
        int bottomGuard = dp(54);

        outer:
        for (Text.TextBlock block : result.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect bounds = line.getBoundingBox();
                String text = line.getText() == null ? "" : line.getText().trim();
                if (bounds == null || !isUsefulText(text)) {
                    continue;
                }
                if (bounds.top < topGuard || bounds.bottom > screenHeight - bottomGuard) {
                    continue;
                }
                if (bounds.width() < dp(24) || bounds.height() < dp(8)) {
                    continue;
                }
                regions.add(new SourceRegion(bounds, text));
                if (regions.size() >= 14) {
                    break outer;
                }
            }
        }
        regions.sort(Comparator.comparingInt(region -> region.bounds.top));
        return regions;
    }

    private boolean isUsefulText(String text) {
        if (text.length() < 2 || text.length() > 220) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("http://") || lower.contains("https://") ||
                lower.contains("www.") || lower.contains("discord.gg")) {
            return false;
        }
        int letters = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isLetter(text.charAt(i))) {
                letters++;
            }
        }
        return letters >= 2;
    }

    private boolean equivalent(String source, String translated) {
        String a = source.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        String b = translated.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim();
        return a.equals(b);
    }

    private long perceptualHash(Bitmap bitmap) {
        int top = Math.max(0, bitmap.getHeight() / 12);
        int usableHeight = Math.max(1, bitmap.getHeight() * 10 / 12);
        long hash = 0L;
        int bit = 0;
        for (int row = 0; row < 8; row++) {
            int y = top + row * Math.max(1, usableHeight - 1) / 7;
            int previous = grayscale(bitmap.getPixel(0, Math.min(bitmap.getHeight() - 1, y)));
            for (int column = 1; column <= 8; column++) {
                int x = column * Math.max(1, bitmap.getWidth() - 1) / 8;
                int current = grayscale(bitmap.getPixel(
                        Math.min(bitmap.getWidth() - 1, x),
                        Math.min(bitmap.getHeight() - 1, y)
                ));
                if (previous > current) {
                    hash |= (1L << bit);
                }
                previous = current;
                bit++;
            }
        }
        return hash;
    }

    private int grayscale(int color) {
        return (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
    }

    private void setOverlayCaptureVisibility(boolean visible) {
        float alpha = visible ? 1f : 0f;
        if (overlayView != null) {
            overlayView.setAlpha(alpha);
        }
        if (bubbleView != null) {
            bubbleView.setAlpha(alpha);
        }
    }

    private void forceRefresh() {
        generation.incrementAndGet();
        lastObservedHash = Long.MIN_VALUE;
        lastProcessedHash = Long.MIN_VALUE;
        stableHits = 0;
        captureRequested.set(false);
        mainHandler.removeCallbacks(captureLoop);
        mainHandler.post(captureLoop);
    }

    private void stopTranslator() {
        if (stopping) {
            return;
        }
        stopping = true;
        generation.incrementAndGet();
        getSharedPreferences("translator", MODE_PRIVATE).edit().putBoolean("running", false).apply();
        mainHandler.removeCallbacksAndMessages(null);
        captureRequested.set(false);

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        if (mediaProjection != null) {
            try {
                if (projectionCallback != null) {
                    mediaProjection.unregisterCallback(projectionCallback);
                }
                mediaProjection.stop();
            } catch (Exception ignored) {
            }
            mediaProjection = null;
        }

        removeOverlay(overlayView);
        removeOverlay(bubbleView);
        overlayView = null;
        bubbleView = null;

        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        stopSelf();
    }

    private void removeOverlay(View view) {
        if (view == null) {
            return;
        }
        try {
            windowManager.removeViewImmediate(view);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onDestroy() {
        getSharedPreferences("translator", MODE_PRIVATE).edit().putBoolean("running", false).apply();
        if (!stopping) {
            stopTranslator();
        }
        if (recognizer != null) {
            recognizer.close();
        }
        if (ocrExecutor != null) {
            ocrExecutor.shutdownNow();
        }
        if (networkPool != null) {
            networkPool.shutdownNow();
        }
        if (captureThread != null) {
            captureThread.quitSafely();
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private final class BubbleTouchListener implements View.OnTouchListener {
        private float downRawY;
        private int initialY;
        private long downTime;
        private boolean moved;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (bubbleParams == null) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downRawY = event.getRawY();
                    initialY = bubbleParams.y;
                    downTime = System.currentTimeMillis();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int delta = Math.round(event.getRawY() - downRawY);
                    if (Math.abs(delta) > dp(5)) {
                        moved = true;
                    }
                    int maxY = Math.max(0, screenHeight - bubbleParams.height - dp(20));
                    bubbleParams.y = Math.max(0, Math.min(maxY, initialY + delta));
                    try {
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                    } catch (Exception ignored) {
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved && System.currentTimeMillis() - downTime < 600L) {
                        forceRefresh();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    private static final class SourceRegion {
        final Rect bounds;
        final String text;

        SourceRegion(Rect bounds, String text) {
            this.bounds = new Rect(bounds);
            this.text = text;
        }
    }
}
