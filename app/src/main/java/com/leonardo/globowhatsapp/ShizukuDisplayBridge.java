package com.leonardo.globowhatsapp;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/**
 * Une el motor de servicios del sistema con el servicio de usuario Shizuku.
 * El motor V2 crea y controla el display; el servicio de usuario queda como
 * respaldo para escritura por comando en firmwares Samsung.
 */
final class ShizukuDisplayBridge {
    static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    interface ConnectionCallback {
        void onReady();
        void onError(String message);
    }

    interface ResultCallback {
        void onResult(int result);
    }

    private static final Object LOCK = new Object();
    private static final List<ConnectionCallback> WAITING = new CopyOnWriteArrayList<>();
    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "globo-whatsapp-display-bridge");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile IDisplayBridge service;
    private static volatile boolean binding;
    private static volatile Context applicationContext;
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
        if (!isInstalled(context)) {
            callback.onError("Shizuku no está instalado");
            return;
        }
        if (!isRunning()) {
            callback.onError("Abre Shizuku y pulsa Iniciar");
            return;
        }
        if (!hasPermission()) {
            callback.onError("Falta autorizar Globo WhatsApp en Shizuku");
            return;
        }
        if (!SystemDisplayEngine.initialize(context)) {
            callback.onError(
                    "No se pudo conectar al motor de pantalla: "
                            + SystemDisplayEngine.getInitializationError()
            );
            return;
        }

        IDisplayBridge current = service;
        if (current != null && current.asBinder().pingBinder()) {
            callback.onReady();
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
                        .processNameSuffix("input_bridge")
                        .debuggable(BuildConfig.DEBUG)
                        .version(BuildConfig.VERSION_CODE);
            }
            try {
                Shizuku.bindUserService(serviceArgs, CONNECTION);
            } catch (RuntimeException error) {
                binding = false;
                // El motor principal ya está listo; no bloquear por el respaldo.
                for (ConnectionCallback item : WAITING) item.onReady();
                WAITING.clear();
            }
        }
    }

    static void launch(
            String flattenedComponent,
            int userId,
            int displayId,
            boolean multipleTask,
            ResultCallback callback
    ) {
        Context context = applicationContext;
        ComponentName component = ComponentName.unflattenFromString(flattenedComponent);
        if (context == null || component == null) {
            if (callback != null) callback.onResult(31);
            return;
        }

        SystemDisplayEngine.launchOrMove(
                context,
                component.getPackageName(),
                flattenedComponent,
                userId,
                displayId,
                (code, diagnostic) -> {
                    if (code != 0) {
                        AppPreferences.recordMessage(
                                context,
                                "Motor V2 no pudo mover WhatsApp: " + diagnostic
                        );
                    } else {
                        AppPreferences.clearLastError(context);
                    }
                    if (callback != null) callback.onResult(code);
                }
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
            if (SystemDisplayEngine.injectTouch(
                    displayId,
                    action,
                    x,
                    y,
                    downTime,
                    eventTime
            )) {
                return;
            }

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
            if (SystemDisplayEngine.injectBack(displayId)) return;
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
