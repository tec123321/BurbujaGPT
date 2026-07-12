package com.leonardo.burbujagpt;

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

    private static final Object LOCK = new Object();
    private static final List<ConnectionCallback> WAITING = new CopyOnWriteArrayList<>();
    private static final ExecutorService COMMANDS = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "globo-gpt-display-bridge");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile IDisplayBridge service;
    private static volatile boolean binding;
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

    static void launch(
            String component,
            int userId,
            int displayId,
            boolean multipleTask,
            ResultCallback callback
    ) {
        COMMANDS.execute(() -> {
            int result = 1;
            IDisplayBridge current = service;
            if (current != null) {
                try {
                    result = current.launch(component, userId, displayId, multipleTask);
                } catch (RemoteException | RuntimeException ignored) {
                    service = null;
                }
            }
            if (callback != null) callback.onResult(result);
        });
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
                    current.injectKey(displayId, android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.ACTION_DOWN, now, now);
                    current.injectKey(displayId, android.view.KeyEvent.KEYCODE_ENTER,
                            android.view.KeyEvent.ACTION_UP, now, now + 20);
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
