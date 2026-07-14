package com.leonardo.globowhatsapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.IBinder;
import android.os.IInterface;
import android.os.SystemClock;
import android.view.Display;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Motor de display inspirado en SuperWindow (GPL-3.0).
 *
 * A diferencia de un VirtualDisplay creado con el DisplayManager normal de la
 * aplicación, este objeto conecta DisplayManagerGlobal a IDisplayManager a
 * través de ShizukuBinderWrapper. De ese modo el sistema ve la creación del
 * display con la identidad privilegiada concedida por Shizuku.
 */
final class SystemDisplayEngine {
    interface LaunchCallback {
        void onResult(int code, String diagnostic);
    }

    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "globo-whatsapp-system-display");
        thread.setDaemon(true);
        return thread;
    });

    private static final int FLAG_PRESENTATION = 1 << 1;
    private static final int FLAG_OWN_CONTENT_ONLY = 1 << 3;
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_OWN_FOCUS = 1 << 14;
    private static final int FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private static volatile DisplayManager privilegedDisplayManager;
    private static volatile Object activityTaskManager;
    private static volatile Object inputManager;
    private static volatile Method inputInjectMethod;
    private static volatile Method inputSetDisplayMethod;
    private static volatile String initializationError = "";

    private SystemDisplayEngine() {
    }

    static synchronized boolean initialize(Context context) {
        if (privilegedDisplayManager != null && activityTaskManager != null) return true;
        if (!Shizuku.pingBinder()) {
            initializationError = "Shizuku no está en ejecución";
            return false;
        }

        try {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/");

            Object iDisplayManager = getSystemInterface(
                    "display",
                    "android.hardware.display.IDisplayManager"
            );
            Object displayManagerGlobal = createDisplayManagerGlobal(iDisplayManager);
            privilegedDisplayManager = createDisplayManager(context.getApplicationContext(), displayManagerGlobal);

            activityTaskManager = getSystemInterface(
                    "activity_task",
                    "android.app.IActivityTaskManager"
            );
            inputManager = getSystemInterface(
                    "input",
                    "android.hardware.input.IInputManager"
            );
            prepareInputMethods();
            initializationError = "";
            return true;
        } catch (Throwable error) {
            privilegedDisplayManager = null;
            activityTaskManager = null;
            inputManager = null;
            initializationError = compact(error);
            AppPreferences.recordMessage(
                    context,
                    "Motor V2 no pudo conectarse a los servicios del sistema: " + initializationError
            );
            return false;
        }
    }

    static String getInitializationError() {
        return initializationError;
    }

    static VirtualDisplay createVirtualDisplay(
            Context context,
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface
    ) throws Exception {
        if (!initialize(context)) {
            throw new IllegalStateException(initializationError);
        }
        if (surface == null || !surface.isValid()) {
            throw new IllegalArgumentException("Surface inválida");
        }

        int flags = FLAG_PRESENTATION
                | FLAG_OWN_CONTENT_ONLY
                | FLAG_SUPPORTS_TOUCH
                | FLAG_DESTROY_CONTENT_ON_REMOVAL
                | FLAG_TRUSTED
                | FLAG_OWN_DISPLAY_GROUP
                | FLAG_ALWAYS_UNLOCKED
                | FLAG_OWN_FOCUS
                | FLAG_DEVICE_DISPLAY_GROUP;

        int safeWidth = Math.max(320, width);
        int safeHeight = Math.max(480, height);
        int safeDensity = Math.max(160, densityDpi);
        DisplayManager manager = privilegedDisplayManager;

        VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                name,
                safeWidth,
                safeHeight,
                safeDensity
        )
                .setFlags(flags)
                .setSurface(surface);

        Display physical = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (physical != null && physical.getRefreshRate() > 0f) {
            builder.setRequestedRefreshRate(physical.getRefreshRate());
        }

        VirtualDisplay display = manager.createVirtualDisplay(builder.build());
        if (display == null || display.getDisplay() == null) {
            throw new IllegalStateException("IDisplayManager devolvió un display nulo");
        }
        return display;
    }

    static void launchOrMove(
            Context context,
            String packageName,
            String flattenedComponent,
            int userId,
            int displayId,
            LaunchCallback callback
    ) {
        COMMANDS.execute(() -> {
            if (!initialize(context)) {
                callback.onResult(20, "Inicialización: " + initializationError);
                return;
            }

            TaskSnapshot existing = findTask(packageName);
            if (existing != null) {
                if (existing.displayId == displayId) {
                    callback.onResult(0, "La tarea ya estaba en el display " + displayId);
                    return;
                }

                if (moveTaskToDisplay(
                        existing.taskId,
                        displayId,
                        packageName,
                        1800
                )) {
                    callback.onResult(0, "Tarea " + existing.taskId + " movida al display " + displayId);
                    return;
                }
            }

            CommandResult started = runShell(
                    "am start --user " + Math.max(0, userId)
                            + " --display " + displayId
                            + " --activity-new-task -n " + flattenedComponent,
                    12
            );
            if (started.exitCode == 0 && waitForTaskDisplay(packageName, displayId, 2400)) {
                callback.onResult(0, "WhatsApp iniciado directamente en el display " + displayId);
                return;
            }

            // Último intento: abrir la app normalmente, localizar su tarea y moverla.
            CommandResult normal = runShell(
                    "am start --user " + Math.max(0, userId)
                            + " --activity-new-task -n " + flattenedComponent,
                    10
            );
            TaskSnapshot launched = waitForTask(packageName, 2200);
            if (normal.exitCode == 0 && launched != null) {
                if (moveTaskToDisplay(
                        launched.taskId,
                        displayId,
                        packageName,
                        2200
                )) {
                    callback.onResult(0, "WhatsApp abierto y tarea movida al display " + displayId);
                    return;
                }
            }

            String diagnostic = "start=" + started.exitCode
                    + " [" + trim(started.output, 160) + "]"
                    + "; normal=" + normal.exitCode
                    + " [" + trim(normal.output, 160) + "]";
            callback.onResult(21, diagnostic);
        });
    }

    static boolean injectTouch(
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
            return injectEvent(event, displayId);
        } finally {
            event.recycle();
        }
    }

    static boolean injectBack(int displayId) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(
                now,
                now,
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK,
                0,
                0,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        KeyEvent up = new KeyEvent(
                now,
                now + 20,
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_BACK,
                0,
                0,
                -1,
                0,
                0,
                InputDevice.SOURCE_KEYBOARD
        );
        return injectEvent(down, displayId) && injectEvent(up, displayId);
    }

    private static boolean injectEvent(InputEvent event, int displayId) {
        Object manager = inputManager;
        Method inject = inputInjectMethod;
        Method setDisplay = inputSetDisplayMethod;
        if (manager == null || inject == null || setDisplay == null) return false;
        try {
            setDisplay.invoke(event, displayId);
            Object result = inject.invoke(manager, event, 0);
            return !(result instanceof Boolean) || (Boolean) result;
        } catch (Throwable error) {
            return false;
        }
    }

    private static void prepareInputMethods() throws Exception {
        Class<?> inputInterface = Class.forName("android.hardware.input.IInputManager");
        inputInjectMethod = inputInterface.getMethod(
                "injectInputEvent",
                InputEvent.class,
                int.class
        );
        inputSetDisplayMethod = InputEvent.class.getDeclaredMethod("setDisplayId", int.class);
        inputSetDisplayMethod.setAccessible(true);
    }

    private static Object getSystemInterface(String serviceName, String interfaceName) throws Exception {
        IBinder raw = SystemServiceHelper.getSystemService(serviceName);
        if (raw == null) throw new IllegalStateException("Servicio ausente: " + serviceName);
        IBinder wrapped = new ShizukuBinderWrapper(raw);
        Class<?> stubClass = Class.forName(interfaceName + "$Stub");
        Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
        Object result = asInterface.invoke(null, wrapped);
        if (result == null) throw new IllegalStateException("Interfaz nula: " + interfaceName);
        return result;
    }

    private static Object createDisplayManagerGlobal(Object iDisplayManager) throws Exception {
        Class<?> globalClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Class<?> interfaceClass = Class.forName("android.hardware.display.IDisplayManager");
        try {
            Constructor<?> constructor = globalClass.getDeclaredConstructor(interfaceClass);
            constructor.setAccessible(true);
            return constructor.newInstance(iDisplayManager);
        } catch (NoSuchMethodException ignored) {
            for (Constructor<?> constructor : globalClass.getDeclaredConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1 && parameters[0].isAssignableFrom(iDisplayManager.getClass())) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(iDisplayManager);
                }
            }
            throw new NoSuchMethodException("Constructor de DisplayManagerGlobal no encontrado");
        }
    }

    private static DisplayManager createDisplayManager(Context context, Object global) throws Exception {
        DisplayManager manager;
        try {
            Constructor<DisplayManager> constructor = DisplayManager.class
                    .getDeclaredConstructor(Context.class);
            constructor.setAccessible(true);
            manager = constructor.newInstance(context);
        } catch (Throwable ignored) {
            manager = context.getSystemService(DisplayManager.class);
        }
        if (manager == null) throw new IllegalStateException("DisplayManager no disponible");

        Field globalField = DisplayManager.class.getDeclaredField("mGlobal");
        globalField.setAccessible(true);
        globalField.set(manager, global);
        return manager;
    }

    private static TaskSnapshot findTask(String packageName) {
        Object manager = activityTaskManager;
        if (manager == null) return null;
        try {
            Class<?> interfaceClass = Class.forName("android.app.IActivityTaskManager");
            Method allRootTasks = interfaceClass.getMethod("getAllRootTaskInfos");
            Object value = allRootTasks.invoke(manager);
            if (!(value instanceof List<?>)) return null;

            TaskSnapshot best = null;
            for (Object task : (List<?>) value) {
                if (task == null) continue;
                ComponentName component = readComponent(task, "baseActivity");
                if (component == null) {
                    Intent baseIntent = readField(task, "baseIntent", Intent.class);
                    if (baseIntent != null) component = baseIntent.getComponent();
                }
                if (component == null || !packageName.equals(component.getPackageName())) continue;

                Integer taskId = readInt(task, "taskId");
                Integer displayId = readInt(task, "displayId");
                if (taskId == null) continue;
                TaskSnapshot candidate = new TaskSnapshot(
                        taskId,
                        displayId == null ? Display.DEFAULT_DISPLAY : displayId
                );
                if (best == null || candidate.displayId != Display.DEFAULT_DISPLAY) best = candidate;
            }
            return best;
        } catch (Throwable error) {
            return null;
        }
    }

    private static boolean waitForTaskDisplay(String packageName, int displayId, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        do {
            TaskSnapshot task = findTask(packageName);
            if (task != null && task.displayId == displayId) return true;
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < end);
        return false;
    }

    private static boolean moveTaskToDisplay(
            int rootTaskId,
            int displayId,
            String packageName,
            long timeoutMs
    ) {
        Object manager = activityTaskManager;
        if (manager != null) {
            try {
                Class<?> interfaceClass = Class.forName("android.app.IActivityTaskManager");
                Method move = interfaceClass.getMethod(
                        "moveRootTaskToDisplay",
                        int.class,
                        int.class
                );
                move.invoke(manager, rootTaskId, displayId);
                if (waitForTaskDisplay(packageName, displayId, timeoutMs)) return true;
            } catch (Throwable ignored) {
                // One UI puede ocultar o cambiar este método; se usa el comando shell.
            }
        }

        CommandResult result = runShell(
                "am display move-stack " + rootTaskId + " " + displayId,
                8
        );
        return result.exitCode == 0
                && waitForTaskDisplay(packageName, displayId, timeoutMs);
    }

    private static TaskSnapshot waitForTask(String packageName, long timeoutMs) {
        long end = SystemClock.uptimeMillis() + timeoutMs;
        do {
            TaskSnapshot task = findTask(packageName);
            if (task != null) return task;
            SystemClock.sleep(120);
        } while (SystemClock.uptimeMillis() < end);
        return null;
    }

    private static ComponentName readComponent(Object target, String fieldName) {
        return readField(target, fieldName, ComponentName.class);
    }

    private static Integer readInt(Object target, String fieldName) {
        try {
            Field field = target.getClass().getField(fieldName);
            return field.getInt(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        try {
            Field field = target.getClass().getField(fieldName);
            Object value = field.get(target);
            return type.isInstance(value) ? type.cast(value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static CommandResult runShell(String command, int timeoutSeconds) {
        Process process = null;
        try {
            Method newProcess = Shizuku.class.getDeclaredMethod(
                    "newProcess",
                    String[].class,
                    String[].class,
                    String.class
            );
            newProcess.setAccessible(true);
            process = (Process) newProcess.invoke(
                    null,
                    new Object[]{new String[]{"sh", "-c", command + " 2>&1"}, null, null}
            );
            if (process == null) return new CommandResult(125, "Shizuku devolvió proceso nulo");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > 65536) break;
                }
            }

            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(124, "Tiempo agotado");
            }
            return new CommandResult(
                    process.exitValue(),
                    output.toString(java.nio.charset.StandardCharsets.UTF_8.name()).trim()
            );
        } catch (Throwable error) {
            if (process != null) process.destroy();
            return new CommandResult(126, compact(error));
        }
    }

    private static String compact(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        if (message == null || message.trim().isEmpty()) message = current.getClass().getSimpleName();
        return current.getClass().getSimpleName() + ": " + trim(message, 260);
    }

    private static String trim(String value, int max) {
        if (value == null) return "";
        String clean = value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() <= max ? clean : clean.substring(0, max);
    }

    private static final class TaskSnapshot {
        final int taskId;
        final int displayId;

        TaskSnapshot(int taskId, int displayId) {
            this.taskId = taskId;
            this.displayId = displayId;
        }
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;

        CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
        }
    }
}
