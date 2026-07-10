package com.leonardo.burbujagpt;

import android.app.ActivityOptions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

final class OfficialChatLauncher {
    private static final String CHATGPT_PACKAGE = "com.openai.chatgpt";
    private static final String CHATGPT_URL = "https://chatgpt.com/";

    private OfficialChatLauncher() {
    }

    static boolean isOfficialAppInstalled(Context context) {
        return context.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE) != null;
    }

    static boolean openOfficialApp(Context context, boolean requestFloatingWindow) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(CHATGPT_PACKAGE);
        if (intent == null) return false;

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
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
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return startWithOptionalBounds(context, intent, requestFloatingWindow);
    }

    private static boolean startWithOptionalBounds(
            Context context,
            Intent intent,
            boolean requestFloatingWindow
    ) {
        try {
            if (requestFloatingWindow && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                int width = context.getResources().getDisplayMetrics().widthPixels;
                int height = context.getResources().getDisplayMetrics().heightPixels;
                int horizontalMargin = dp(context, 14);
                int topMargin = dp(context, 72);
                int bottomMargin = dp(context, 88);

                Rect bounds = new Rect(
                        horizontalMargin,
                        topMargin,
                        Math.max(horizontalMargin + dp(context, 280), width - horizontalMargin),
                        Math.max(topMargin + dp(context, 420), height - bottomMargin)
                );
                ActivityOptions options = ActivityOptions.makeBasic();
                options.setLaunchBounds(bounds);
                Bundle bundle = options.toBundle();
                context.startActivity(intent, bundle);
            } else {
                context.startActivity(intent);
            }
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}
