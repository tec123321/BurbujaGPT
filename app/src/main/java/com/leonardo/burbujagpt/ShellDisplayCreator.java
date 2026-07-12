package com.leonardo.burbujagpt;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.IBinder;
import android.view.Display;
import android.view.Surface;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * Crea el display privilegiado usando identidad coherente de shell.
 *
 * En V16 el binder se ejecutaba como UID shell pero el Context declaraba el
 * paquete de Globo GPT. Android 16 detectaba la discrepancia y devolvía:
 * "packageName must match the owner uid". Este creador usa com.android.shell
 * y AttributionSource(SHELL_UID), igual que el propietario efectivo.
 */
final class ShellDisplayCreator {
    private static final int FLAG_PRESENTATION = 1 << 1;
    private static final int FLAG_OWN_CONTENT_ONLY = 1 << 3;
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_OWN_FOCUS = 1 << 14;
    private static final int FLAG_DEVICE_DISPLAY_GROUP = 1 << 15;

    private static volatile DisplayManager manager;

    private ShellDisplayCreator() {
    }

    static synchronized VirtualDisplay create(
            Context context,
            String name,
            int width,
            int height,
            int densityDpi,
            Surface surface
    ) throws Exception {
        if (surface == null || !surface.isValid()) {
            throw new IllegalArgumentException("Surface inválida");
        }
        if (manager == null) manager = buildManager(context);

        int flags = FLAG_PRESENTATION
                | FLAG_OWN_CONTENT_ONLY
                | FLAG_SUPPORTS_TOUCH
                | FLAG_DESTROY_CONTENT_ON_REMOVAL
                | FLAG_TRUSTED
                | FLAG_OWN_DISPLAY_GROUP
                | FLAG_ALWAYS_UNLOCKED
                | FLAG_OWN_FOCUS
                | FLAG_DEVICE_DISPLAY_GROUP;

        VirtualDisplayConfig.Builder builder = new VirtualDisplayConfig.Builder(
                name,
                Math.max(320, width),
                Math.max(480, height),
                Math.max(160, densityDpi)
        )
                .setFlags(flags)
                .setSurface(surface);

        Display physical = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (physical != null && physical.getRefreshRate() > 0f) {
            builder.setRequestedRefreshRate(physical.getRefreshRate());
        }

        try {
            VirtualDisplay result = manager.createVirtualDisplay(builder.build());
            if (result == null || result.getDisplay() == null) {
                throw new IllegalStateException("IDisplayManager devolvió un display nulo");
            }
            return result;
        } catch (Throwable first) {
            // El binder puede quedar obsoleto si Shizuku se reinicia.
            manager = buildManager(context);
            VirtualDisplay result = manager.createVirtualDisplay(builder.build());
            if (result == null || result.getDisplay() == null) throw asException(first);
            return result;
        }
    }

    private static DisplayManager buildManager(Context context) throws Exception {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/");

        IBinder raw = SystemServiceHelper.getSystemService("display");
        if (raw == null) throw new IllegalStateException("Servicio display ausente");
        IBinder wrapped = new ShizukuBinderWrapper(raw);

        Class<?> interfaceClass = Class.forName("android.hardware.display.IDisplayManager");
        Class<?> stubClass = Class.forName("android.hardware.display.IDisplayManager$Stub");
        Method asInterface = stubClass.getMethod("asInterface", IBinder.class);
        Object iDisplayManager = asInterface.invoke(null, wrapped);
        if (iDisplayManager == null) throw new IllegalStateException("IDisplayManager nulo");

        Class<?> globalClass = Class.forName("android.hardware.display.DisplayManagerGlobal");
        Constructor<?> globalConstructor = globalClass.getDeclaredConstructor(interfaceClass);
        globalConstructor.setAccessible(true);
        Object global = globalConstructor.newInstance(iDisplayManager);

        ShellContext shellContext = new ShellContext(context);
        Constructor<DisplayManager> constructor = DisplayManager.class
                .getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        DisplayManager result = constructor.newInstance(shellContext);

        Field globalField = DisplayManager.class.getDeclaredField("mGlobal");
        globalField.setAccessible(true);
        globalField.set(result, global);
        return result;
    }

    private static Exception asException(Throwable throwable) {
        if (throwable instanceof Exception) return (Exception) throwable;
        return new Exception(throwable);
    }
}
