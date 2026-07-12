package com.leonardo.burbujagpt;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.Surface;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** Conexión compartida con el servicio privilegiado que controla el display virtual. */
final class ShizukuDisplayBridge {
    static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    interface ConnectionCallback {
        void onReady();
        void onError(String message);
    }

    interface ResultCallback {
        void onResult(int result);
    }

    interface DisplayCallback {
        void onDisplayCreated(int displayId);
    }

    private static final Object LOCK = new Object();
    private static final List<ConnectionCallback> WAITING = new CopyOnWriteArrayList<>();
    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "globo-gpt-display-bridge");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile IDisplayBridge service;
    private static volatile boolean binding;
    private static volatile Context applicationContext;
    private static volatile WeakReference<Context> ownerContext = new WeakReference<>(null);
    private static Shizuku.UserServiceArgs serviceArgs;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IDisplayBridge connected = IDisplayBridge.Stub.asInterface(binder);
            if (connected == null || !binder.pingBinder()) {
                service = null;
                binding = false;
                notifyError("Shizuku no entregó un servicio válido");
                return;
            }
            service = connected;
            binding = false;
            for (ConnectionCallback callback : WAITING) callback.onReady();
            WAITING.clear();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            binding = false;
        }
    };

    private ShizukuDisplayBridge() {
    }

    static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(SHIZUKU_PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    static boolean isRunning() {
        try {
            return Shizuku.pingBinder();
        } catch (RuntimeException error) {
            return false;
        }
    }

    static boolean hasPermission() {
        try {
            return Shizuku.pingBinder()
                    && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException error) {
            return false;
        }
    }

    static void requestPermission(int requestCode) {
        Shizuku.requestPermission(requestCode);
    }

    static void connect(Context context, ConnectionCallback callback) {
        applicationContext = context.getApplicationContext();
        ownerContext = new WeakReference<>(context);

        IDisplayBridge current = service;
        if (current != null && current.asBinder().pingBinder()) {
            callback.onReady();
            return;
        }
        if (!isInstalled(context)) {
            callback.onError("Shizuku no está instalado");
            return;
        }
        if (!isRunning()) {
            callback.onError("Abre Shizuku y pulsa Iniciar");
            return;
        }
        if (!hasPermission()) {
            callback.onError("Falta autorizar Globo GPT en Shizuku");
            return;
        }

        WAITING.add(callback);
        synchronized (LOCK) {
            if (binding) return;
            binding = true;
            if (serviceArgs == null) {
                serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(
                        BuildConfig.APPLICATION_ID,
                        PrivilegedDisplayService.class.getName()
                ))
                        .daemon(false)
                        .processNameSuffix("virtual_display")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE);
            }
            try {
                Shizuku.bindUserService(serviceArgs, CONNECTION);
            } catch (RuntimeException error) {
                binding = false;
                notifyError("No se pudo iniciar el servicio de pantalla virtual");
            }
        }
    }

    static void createDisplay(
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            DisplayCallback callback
    ) {
        COMMANDS.execute(() -> {
            int displayId = -1;
            IDisplayBridge current = service;
            if (current != null) {
                try {
                    displayId = current.createDisplay(
                            name,
                            width,
                            height,
                            densityDpi,
                            surface
                    );
                } catch (RemoteException | RuntimeException ignored) {
                    service = null;
                }
            }
            if (callback != null) callback.onDisplayCreated(displayId);
        });
    }

    static void updateDisplay(
            int displayId,
            int width,
            int height,
            int densityDpi,
            Surface surface,
            ResultCallback callback
    ) {
        COMMANDS.execute(() -> {
            int result = 1;
            IDisplayBridge current = service;
            if (current != null) {
                try {
                    result = current.updateDisplay(
                            displayId,
                            width,
                            height,
                            densityDpi,
                            surface
                    ) ? 0 : 1;
                } catch (RemoteException | RuntimeException ignored) {
                    service = null;
                }
            }
            if (callback != null) callback.onResult(result);
        });
    }

    static void detachDisplay(int displayId) {
        COMMANDS.execute(() -> {
            IDisplayBridge current = service;
            if (current == null) return;
            try {
                current.detachDisplay(displayId);
            } catch (RemoteException | RuntimeException ignored) {
                service = null;
            }
        });
    }

    static void releaseDisplay(int displayId) {
        COMMANDS.execute(() -> {
            IDisplayBridge current = service;
            if (current == null) return;
            try {
                current.releaseDisplay(displayId);
            } catch (RemoteException | RuntimeException ignored) {
                service = null;
            }
        });
    }

    static void launch(
            String component,
            int userId,
            int displayId,
            boolean multipleTask,
            ResultCallback callback
    ) {
        COMMANDS.execute(() -> {
            int directResult = launchAsDisplayOwner(component, displayId, multipleTask);
            if (directResult == 0) {
                if (callback != null) callback.onResult(0);
                return;
            }

            int shellResult = 1;
            IDisplayBridge current = service;
            if (current != null) {
                try {
                    shellResult = current.launch(component, userId, displayId, multipleTask);
                } catch (RemoteException | RuntimeException ignored) {
                    service = null;
                }
            }

            Context context = applicationContext;
            if (shellResult != 0 && context != null) {
                AppPreferences.recordMessage(
                        context,
                        "Inicio en display rechazado. App=" + directResult + ", Shizuku=" + shellResult
                );
            }
            if (callback != null) callback.onResult(shellResult);
        });
    }

    private static int launchAsDisplayOwner(
            String flattenedComponent,
            int displayId,
            boolean multipleTask
    ) {
        Context context = ownerContext.get();
        if (context == null) context = applicationContext;
        if (context == null || displayId < 0) return 90;

        ComponentName component = ComponentName.unflattenFromString(flattenedComponent);
        if (component == null) return 91;

        try {
            Intent packageIntent = context.getPackageManager()
                    .getLaunchIntentForPackage(component.getPackageName());
            Intent target = packageIntent == null
                    ? new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    : new Intent(packageIntent);
            target.setComponent(component);
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            if (multipleTask) {
                target.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
            } else {
                target.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            }

            ActivityOptions options = ActivityOptions.makeBasic();
            options.setLaunchDisplayId(displayId);
            context.startActivity(target, options.toBundle());
            return 0;
        } catch (SecurityException error) {
            recordDirectLaunchError("SecurityException", error);
            return 92;
        } catch (RuntimeException error) {
            recordDirectLaunchError(error.getClass().getSimpleName(), error);
            return 93;
        }
    }

    private static void recordDirectLaunchError(String type, Throwable error) {
        Context context = applicationContext;
        if (context == null) return;
        String message = error.getMessage();
        if (message == null) message = "sin detalle";
        if (message.length() > 240) message = message.substring(0, 240);
        AppPreferences.recordMessage(
                context,
                "Inicio directo en display falló: " + type + " · " + message
        );
    }

    static void injectTouch(
            int displayId,
            int action,
            float x,
            float y,
            long downTime,
            long eventTime
    ) {
        COMMANDS.execute(() -> {
            IDisplayBridge current = service;
            if (current == null) return;
            try {
                current.injectTouch(displayId, action, x, y, downTime, eventTime);
            } catch (RemoteException | RuntimeException ignored) {
                service = null;
            }
        });
    }

    static void back(int displayId) {
        COMMANDS.execute(() -> {
            IDisplayBridge current = service;
            if (current == null) return;
            try {
                current.back(displayId);
            } catch (RemoteException | RuntimeException ignored) {
                service = null;
            }
        });
    }

    static void inputText(int displayId, String text, boolean pressEnter) {
        COMMANDS.execute(() -> {
            IDisplayBridge current = service;
            if (current == null) return;
            try {
                current.inputText(displayId, text);
                if (pressEnter) {
                    long now = android.os.SystemClock.uptimeMillis();
                    current.injectKey(
                            displayId,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.ACTION_DOWN,
                            now,
                            now
                    );
                    current.injectKey(
                            displayId,
                            android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.ACTION_UP,
                            now,
                            now + 20
                    );
                }
            } catch (RemoteException | RuntimeException ignored) {
                service = null;
            }
        });
    }

    private static void notifyError(String message) {
        for (ConnectionCallback callback : WAITING) callback.onError(message);
        WAITING.clear();
    }
}
