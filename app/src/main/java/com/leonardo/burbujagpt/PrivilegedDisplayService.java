package com.leonardo.burbujagpt;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Build;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import androidx.annotation.Keep;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de usuario Shizuku. Se ejecuta con identidad shell, crea una pantalla
 * virtual confiable y controla la aplicación oficial dentro de ese display.
 */
public final class PrivilegedDisplayService extends IDisplayBridge.Stub {
    private static final int FLAG_PRESENTATION = 1 << 1;
    private static final int FLAG_OWN_CONTENT_ONLY = 1 << 3;
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_OWN_FOCUS = 1 << 14;
    private static final int FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private final Map<Integer, VirtualDisplay> displays = new ConcurrentHashMap<>();
    private final Map<Integer, GestureState> fallbackGestures = new ConcurrentHashMap<>();
    private volatile DisplayManager displayManager;
    private volatile Object inputManager;
    private volatile Method injectInputEvent;
    private volatile Method setDisplayId;

    /** Constructor de respaldo para versiones antiguas de Shizuku. */
    public PrivilegedDisplayService() {
        prepareInputInjection();
    }

    /** Shizuku API 13 entrega un Context del paquete dentro del proceso shell. */
    @Keep
    public PrivilegedDisplayService(Context context) {
        displayManager = context.getSystemService(DisplayManager.class);
        prepareInputInjection();
    }

    @Override
    public void destroy() {
        for (VirtualDisplay display : displays.values()) {
            try {
                display.release();
            } catch (RuntimeException ignored) {
            }
        }
        displays.clear();
        System.exit(0);
    }

    @Override
    public int createDisplay(
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface
    ) {
        DisplayManager manager = displayManager;
        if (manager == null || surface == null || !surface.isValid()) return -1;

        int safeWidth = Math.max(320, width);
        int safeHeight = Math.max(480, height);
        int safeDensity = Math.max(160, densityDpi);
        int[] flagAttempts = new int[]{
                FLAG_PRESENTATION
                        | FLAG_OWN_CONTENT_ONLY
                        | FLAG_SUPPORTS_TOUCH
                        | FLAG_DESTROY_CONTENT_ON_REMOVAL
                        | FLAG_TRUSTED
                        | FLAG_OWN_DISPLAY_GROUP
                        | FLAG_ALWAYS_UNLOCKED
                        | FLAG_OWN_FOCUS
                        | FLAG_DEVICE_DISPLAY_GROUP,
                FLAG_PRESENTATION
                        | FLAG_OWN_CONTENT_ONLY
                        | FLAG_SUPPORTS_TOUCH
                        | FLAG_DESTROY_CONTENT_ON_REMOVAL
                        | FLAG_TRUSTED
                        | FLAG_OWN_DISPLAY_GROUP
                        | FLAG_ALWAYS_UNLOCKED
                        | FLAG_OWN_FOCUS,
                FLAG_PRESENTATION
                        | FLAG_OWN_CONTENT_ONLY
                        | FLAG_SUPPORTS_TOUCH
                        | FLAG_DESTROY_CONTENT_ON_REMOVAL
                        | FLAG_TRUSTED,
                FLAG_PRESENTATION
                        | FLAG_OWN_CONTENT_ONLY
                        | FLAG_SUPPORTS_TOUCH
        };

        for (int flags : flagAttempts) {
            VirtualDisplay display = createVirtualDisplay(
                    manager,
                    name == null ? "Globo GPT" : name,
                    safeWidth,
                    safeHeight,
                    safeDensity,
                    surface,
                    flags
            );
            if (display == null || display.getDisplay() == null) continue;
            int displayId = display.getDisplay().getDisplayId();
            displays.put(displayId, display);
            return displayId;
        }
        return -1;
    }

    @Override
    public boolean updateDisplay(
            int displayId,
            int width,
            int height,
            int densityDpi,
            Surface surface
    ) {
        VirtualDisplay display = displays.get(displayId);
        if (display == null || surface == null || !surface.isValid()) return false;
        try {
            display.resize(Math.max(320, width), Math.max(480, height), Math.max(160, densityDpi));
            display.setSurface(surface);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    @Override
    public void detachDisplay(int displayId) {
        VirtualDisplay display = displays.get(displayId);
        if (display == null) return;
        try {
            display.setSurface(null);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void releaseDisplay(int displayId) {
        VirtualDisplay display = displays.remove(displayId);
        if (display == null) return;
        try {
            display.release();
        } catch (RuntimeException ignored) {
        }
        fallbackGestures.remove(displayId);
    }

    @Override
    public int launch(
            String component,
            int userId,
            int displayId,
            boolean multipleTask
    ) {
        if (component == null || component.trim().isEmpty() || displayId < 0) return 64;

        String user = String.valueOf(Math.max(0, userId));
        String targetDisplay = String.valueOf(displayId);
        String[] taskFlags = multipleTask
                ? new String[]{"--activity-new-task", "--activity-new-document", "--activity-multiple-task"}
                : new String[]{"--activity-new-task", "--activity-reorder-to-front"};

        String[][] prefixes = new String[][]{
                {"/system/bin/am", "start", "--user", user, "--display", targetDisplay},
                {"/system/bin/cmd", "activity", "start-activity", "--user", user,
                        "--display", targetDisplay}
        };

        int last = 1;
        for (String[] prefix : prefixes) {
            String[] command = concat(prefix, taskFlags, new String[]{"-n", component});
            last = run(command, 10);
            if (last == 0) return 0;
        }

        String[][] simpleAttempts = new String[][]{
                {"/system/bin/am", "start", "--user", user, "--display", targetDisplay,
                        "--activity-new-task", "-n", component},
                {"/system/bin/cmd", "activity", "start-activity", "--user", user,
                        "--display", targetDisplay, "--activity-new-task", "-n", component}
        };
        for (String[] command : simpleAttempts) {
            last = run(command, 10);
            if (last == 0) return 0;
        }
        return last;
    }

    @Override
    public boolean injectTouch(
            int displayId,
            int action,
            float x,
            float y,
            long downTime,
            long eventTime
    ) {
        long safeDown = downTime > 0 ? downTime : SystemClock.uptimeMillis();
        long safeEvent = eventTime > 0 ? eventTime : SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(
                safeDown,
                safeEvent,
                action,
                Math.max(0f, x),
                Math.max(0f, y),
                0
        );
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            if (inject(event, displayId)) return true;
        } finally {
            event.recycle();
        }
        return fallbackTouch(displayId, action, x, y, safeDown, safeEvent);
    }

    @Override
    public boolean injectKey(
            int displayId,
            int keyCode,
            int action,
            long downTime,
            long eventTime
    ) {
        long safeDown = downTime > 0 ? downTime : SystemClock.uptimeMillis();
        long safeEvent = eventTime > 0 ? eventTime : SystemClock.uptimeMillis();
        KeyEvent event = new KeyEvent(
                safeDown,
                safeEvent,
                action,
                keyCode,
                0,
                0,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        if (inject(event, displayId)) return true;

        if (action == KeyEvent.ACTION_UP) {
            return run(new String[]{
                    "/system/bin/input", "-d", String.valueOf(displayId),
                    "keyevent", String.valueOf(keyCode)
            }, 4) == 0;
        }
        return true;
    }

    @Override
    public int inputText(int displayId, String text) {
        if (text == null || text.isEmpty()) return 0;
        String encoded = text
                .replace("%", "%%")
                .replace(" ", "%s")
                .replace("\n", "%s");
        return run(new String[]{
                "/system/bin/input", "-d", String.valueOf(displayId),
                "text", encoded
        }, 8);
    }

    @Override
    public int back(int displayId) {
        return run(new String[]{
                "/system/bin/input", "-d", String.valueOf(displayId),
                "keyevent", String.valueOf(KeyEvent.KEYCODE_BACK)
        }, 4);
    }

    private VirtualDisplay createVirtualDisplay(
            DisplayManager manager,
            String name,
            int width,
            int height,
            int density,
            Surface surface,
            int flags
    ) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                        name,
                        width,
                        height,
                        density
                )
                        .setFlags(flags)
                        .setSurface(surface);
                Display physical = manager.getDisplay(Display.DEFAULT_DISPLAY);
                if (physical != null && physical.getRefreshRate() > 0f) {
                    builder.setRequestedRefreshRate(physical.getRefreshRate());
                }
                return manager.createVirtualDisplay(builder.build());
            }
            return manager.createVirtualDisplay(name, width, height, density, surface, flags);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void prepareInputInjection() {
        try {
            Class<?> managerClass = Class.forName("android.hardware.input.InputManager");
            Method getInstance = managerClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            inputManager = getInstance.invoke(null);

            injectInputEvent = managerClass.getDeclaredMethod(
                    "injectInputEvent",
                    InputEvent.class,
                    int.class
            );
            injectInputEvent.setAccessible(true);

            setDisplayId = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
            setDisplayId.setAccessible(true);
        } catch (Throwable ignored) {
            inputManager = null;
            injectInputEvent = null;
            setDisplayId = null;
        }
    }

    private boolean inject(InputEvent event, int displayId) {
        Object manager = inputManager;
        Method injectMethod = injectInputEvent;
        Method displayMethod = setDisplayId;
        if (manager == null || injectMethod == null || displayMethod == null) {
            prepareInputInjection();
            manager = inputManager;
            injectMethod = injectInputEvent;
            displayMethod = setDisplayId;
        }
        if (manager == null || injectMethod == null || displayMethod == null) return false;

        try {
            displayMethod.invoke(event, displayId);
            Object result = injectMethod.invoke(manager, event, 0);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable ignored) {
            inputManager = null;
            injectInputEvent = null;
            setDisplayId = null;
            return false;
        }
    }

    private boolean fallbackTouch(
            int displayId,
            int action,
            float x,
            float y,
            long downTime,
            long eventTime
    ) {
        if (action == MotionEvent.ACTION_DOWN) {
            fallbackGestures.put(displayId, new GestureState(x, y, downTime));
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            fallbackGestures.remove(displayId);
            return true;
        }
        if (action != MotionEvent.ACTION_UP) return true;

        GestureState start = fallbackGestures.remove(displayId);
        if (start == null) start = new GestureState(x, y, downTime);
        float dx = x - start.x;
        float dy = y - start.y;
        long duration = Math.max(40L, Math.min(1200L, eventTime - start.downTime));

        String[] command;
        if (dx * dx + dy * dy < 100f) {
            command = new String[]{
                    "/system/bin/input", "-d", String.valueOf(displayId),
                    "tap", String.valueOf(Math.round(x)), String.valueOf(Math.round(y))
            };
        } else {
            command = new String[]{
                    "/system/bin/input", "-d", String.valueOf(displayId),
                    "swipe",
                    String.valueOf(Math.round(start.x)),
                    String.valueOf(Math.round(start.y)),
                    String.valueOf(Math.round(x)),
                    String.valueOf(Math.round(y)),
                    String.valueOf(duration)
            };
        }
        return run(command, 5) == 0;
    }

    private int run(String[] command, int timeoutSeconds) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                    if (sink.size() > 65536) break;
                }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return 124;
            }
            return process.exitValue();
        } catch (Throwable error) {
            if (process != null) process.destroy();
            return 1;
        }
    }

    private static String[] concat(String[] first, String[] second, String[] third) {
        String[] result = new String[first.length + second.length + third.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        System.arraycopy(third, 0, result, first.length + second.length, third.length);
        return result;
    }

    private static final class GestureState {
        final float x;
        final float y;
        final long downTime;

        GestureState(float x, float y, long downTime) {
            this.x = x;
            this.y = y;
            this.downTime = downTime;
        }
    }
}
