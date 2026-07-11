package com.leonardo.burbujagpt;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import java.lang.reflect.Method;

/** Abre la aplicación oficial conservando su paquete, firma y sesión. */
final class OfficialChatLauncher {
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    private static final String CHATGPT_URL = "https://chatgpt.com/";
    private static final int WINDOWING_MODE_FREEFORM = 5;

    private OfficialChatLauncher() {
    }

    static boolean isOfficialAppInstalled(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE) != null;
    }

    static boolean openOfficialApp(Context context, boolean requestFloatingWindow) {
        Intent launcher = context.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
        if (launcher == null) return false;

        if (requestFloatingWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                Intent bridge = new Intent(context, ShizukuLaunchActivity.class);
                bridge.addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                                | Intent.FLAG_ACTIVITY_NO_ANIMATION
                );
                context.startActivity(bridge);
                return true;
            } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException ignored) {
                // Se mantiene el método compatible como último recurso.
            }
        }

        Intent intent = new Intent(launcher);
        intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        );
        intent.removeFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return startWithOptionalBounds(context, intent, requestFloatingWindow);
    }

    static boolean openBrowser(Context context, String url, boolean requestFloatingWindow) {
        Uri uri = Uri.parse(url == null || url.trim().isEmpty() ? CHATGPT_URL : url);
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            uri = Uri.parse(CHATGPT_URL);
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return startWithOptionalBounds(context, intent, requestFloatingWindow);
    }

    private static boolean startWithOptionalBounds(
            Context context,
            Intent intent,
            boolean requestFloatingWindow
    ) {
        if (requestFloatingWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(calculatePopupBounds(context));
                requestSamsungFreeformWindow(options);
                Bundle bundle = options.toBundle();
                context.startActivity(intent, bundle);
                return true;
            } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException ignored) {
                // One UI puede rechazar la petición; se reintenta normalmente.
            }
        }

        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    private static Rect calculatePopupBounds(Context context) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int screenHeight = context.getResources().getDisplayMetrics().heightPixels;
        int margin = dp(context, 18);
        int top = dp(context, 72);
        int bottom = dp(context, 96);

        int desiredWidth = Math.max(dp(context, 320), screenWidth - margin * 2);
        int desiredHeight = Math.max(dp(context, 480), screenHeight - top - bottom);
        int left = Math.max(margin, (screenWidth - desiredWidth) / 2);
        int right = Math.min(screenWidth - margin, left + desiredWidth);
        int lower = Math.min(screenHeight - bottom, top + desiredHeight);

        return new Rect(left, top, right, lower);
    }

    private static void requestSamsungFreeformWindow(ActivityOptions options) {
        try {
            Method method = ActivityOptions.class.getDeclaredMethod(
                    "setLaunchWindowingMode",
                    int.class
            );
            method.setAccessible(true);
            method.invoke(options, WINDOWING_MODE_FREEFORM);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
