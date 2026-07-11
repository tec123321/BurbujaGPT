package com.leonardo.burbujagpt;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/** Puente transparente que solicita Shizuku y abre ChatGPT en modo freeform. */
public class ShizukuLaunchActivity extends Activity {
    private static final int REQUEST_SHIZUKU_PERMISSION = 1201;
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    private static final String SHIZUKU_PACKAGE = "moe.shizuku.privileged.api";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean binding;
    private boolean completed;
    private int binderChecks;

    private final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(
                    BuildConfig.APPLICATION_ID,
                    PrivilegedLauncherService.class.getName()
            ))
                    .daemon(false)
                    .processNameSuffix("popup")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = this::continueLaunch;
    private final Shizuku.OnBinderDeadListener binderDeadListener = () -> {
        if (!completed) {
            Toast.makeText(this, "Shizuku se detuvo", Toast.LENGTH_SHORT).show();
            finishBridge();
        }
    };
    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::onShizukuPermissionResult;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            IPrivilegedLauncher launcher = IPrivilegedLauncher.Stub.asInterface(binder);
            if (launcher == null || !binder.pingBinder()) {
                fallbackOpen("Shizuku no entregó un servicio válido");
                return;
            }

            Intent target = getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
            ComponentName component = target == null ? null : target.getComponent();
            if (component == null) {
                Toast.makeText(
                        ShizukuLaunchActivity.this,
                        "No se encontró la actividad principal de ChatGPT",
                        Toast.LENGTH_LONG
                ).show();
                finishBridge();
                return;
            }

            Rect bounds = calculatePopupBounds();
            int userId = Math.max(0, Process.myUid() / 100000);
            String flattened = component.flattenToShortString();

            new Thread(() -> {
                int result;
                try {
                    result = launcher.launch(
                            flattened,
                            userId,
                            bounds.left,
                            bounds.top,
                            bounds.right,
                            bounds.bottom
                    );
                } catch (RemoteException | RuntimeException error) {
                    result = 1;
                }

                int finalResult = result;
                runOnUiThread(() -> {
                    if (finalResult == 0) {
                        finishBridge();
                    } else {
                        fallbackOpen("One UI rechazó el modo ventana solicitado por Shizuku");
                    }
                });
            }, "shizuku-popup-launch").start();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            binding = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setDimAmount(0f);

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        handler.postDelayed(this::continueLaunch, 250);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!completed) handler.postDelayed(this::continueLaunch, 180);
    }

    private void continueLaunch() {
        if (completed || binding || isFinishing()) return;

        if (getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE) == null) {
            Toast.makeText(this, "Instala primero ChatGPT oficial", Toast.LENGTH_LONG).show();
            finishBridge();
            return;
        }

        if (!isPackageInstalled(SHIZUKU_PACKAGE)) {
            Toast.makeText(this, "Instala Shizuku para usar la ventana emergente", Toast.LENGTH_LONG).show();
            openShizukuStore();
            finishBridge();
            return;
        }

        if (!Shizuku.pingBinder()) {
            if (binderChecks++ < 3) {
                handler.postDelayed(this::continueLaunch, 300);
                return;
            }
            Toast.makeText(this, "Abre Shizuku y pulsa Iniciar", Toast.LENGTH_LONG).show();
            openShizukuApp();
            finishBridge();
            return;
        }

        try {
            if (Shizuku.isPreV11()) {
                Toast.makeText(this, "Actualiza Shizuku", Toast.LENGTH_LONG).show();
                openShizukuApp();
                finishBridge();
                return;
            }

            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindLauncherService();
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                Toast.makeText(
                        this,
                        "Autoriza Globo GPT desde la pantalla de aplicaciones de Shizuku",
                        Toast.LENGTH_LONG
                ).show();
                openShizukuApp();
                finishBridge();
            } else {
                Shizuku.requestPermission(REQUEST_SHIZUKU_PERMISSION);
            }
        } catch (RuntimeException error) {
            Toast.makeText(this, "No se pudo conectar con Shizuku", Toast.LENGTH_LONG).show();
            openShizukuApp();
            finishBridge();
        }
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_SHIZUKU_PERMISSION || completed) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bindLauncherService();
        } else {
            Toast.makeText(this, "Permiso de Shizuku rechazado", Toast.LENGTH_LONG).show();
            finishBridge();
        }
    }

    private void bindLauncherService() {
        if (binding || completed) return;
        binding = true;
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        } catch (RuntimeException error) {
            binding = false;
            fallbackOpen("No se pudo iniciar el servicio privilegiado");
        }
    }

    private void fallbackOpen(String message) {
        if (completed) return;
        Toast.makeText(this, message + "; se intentará el modo compatible", Toast.LENGTH_LONG).show();

        Intent target = getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
        if (target != null) {
            target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(calculatePopupBounds());
                startActivity(target, options.toBundle());
            } catch (RuntimeException error) {
                try {
                    startActivity(target);
                } catch (RuntimeException ignored) {
                }
            }
        }
        finishBridge();
    }

    private Rect calculatePopupBounds() {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int margin = dp(16);
        int top = dp(64);
        int bottomMargin = dp(88);
        int width = Math.min(screenWidth - margin * 2, dp(430));
        int height = Math.min(screenHeight - top - bottomMargin, dp(720));
        int left = Math.max(margin, (screenWidth - width) / 2);
        return new Rect(left, top, left + width, top + height);
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException error) {
            return false;
        }
    }

    private void openShizukuApp() {
        Intent intent = getPackageManager().getLaunchIntentForPackage(SHIZUKU_PACKAGE);
        if (intent != null) {
            try {
                startActivity(intent);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        openShizukuStore();
    }

    private void openShizukuStore() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + SHIZUKU_PACKAGE)
            ));
        } catch (ActivityNotFoundException error) {
            try {
                startActivity(new Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + SHIZUKU_PACKAGE)
                ));
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void finishBridge() {
        if (completed) return;
        completed = true;
        handler.removeCallbacksAndMessages(null);
        if (binding) {
            try {
                Shizuku.unbindUserService(userServiceArgs, serviceConnection, true);
            } catch (RuntimeException ignored) {
            }
            binding = false;
        }
        finishAndRemoveTask();
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        super.onDestroy();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
