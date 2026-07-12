package com.leonardo.burbujagpt;

import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Servicio de usuario Shizuku. Se ejecuta con identidad shell para iniciar
 * ChatGPT en la pantalla virtual e inyectar eventos en ese display.
 */
public final class PrivilegedDisplayService extends IDisplayBridge.Stub {
    private final Map<Integer, GestureState> fallbackGestures = new ConcurrentHashMap<>();
    private volatile Object inputManager;
    private volatile Method injectInputEvent;
    private volatile Method setDisplayId;

    public PrivilegedDisplayService() {
        prepareInputInjection();
    }

    @Override
    public void destroy() {
        System.exit(0);
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
        String display = String.valueOf(displayId);
        String[] taskFlags = multipleTask
                ? new String[]{"--activity-new-task", "--activity-new-document", "--activity-multiple-task"}
                : new String[]{"--activity-new-task", "--activity-reorder-to-front"};

        String[][] prefixes = new String[][]{
                {"/system/bin/am", "start", "--user", user, "--display", display},
                {"/system/bin/cmd", "activity", "start-activity", "--user", user, "--display", display}
        };

        int last = 1;
        for (String[] prefix : prefixes) {
            String[] command = concat(prefix, taskFlags, new String[]{"-n", component});
            last = run(command, 10);
            if (last == 0) return 0;
        }

        // Algunos firmwares Samsung rechazan MULTIPLE_TASK pero aceptan un arranque simple.
        String[][] simpleAttempts = new String[][]{
                {"/system/bin/am", "start", "--user", user, "--display", display,
                        "--activity-new-task", "-n", component},
                {"/system/bin/cmd", "activity", "start-activity", "--user", user,
                        "--display", display, "--activity-new-task", "-n", component}
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
