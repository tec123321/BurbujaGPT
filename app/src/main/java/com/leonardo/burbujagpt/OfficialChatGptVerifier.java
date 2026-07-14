package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.InstallSourceInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

final class OfficialChatGptVerifier {
    static final String PLAY_STORE_PACKAGE = "com.android.vending";

    private OfficialChatGptVerifier() {
    }

    static Result verify(Context context) {
        PackageManager packages = context.getPackageManager();
        try {
            PackageInfo info = packages.getPackageInfo(NativeBubblePublisher.CHATGPT_PACKAGE, 0);
            ApplicationInfo app = info.applicationInfo;
            if (app != null && (app.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                return new Result(false, "La instalación de ChatGPT es depurable o está modificada.");
            }

            String installer;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                InstallSourceInfo source = packages.getInstallSourceInfo(NativeBubblePublisher.CHATGPT_PACKAGE);
                installer = source.getInstallingPackageName();
                if (installer == null) installer = source.getInitiatingPackageName();
            } else {
                installer = packages.getInstallerPackageName(NativeBubblePublisher.CHATGPT_PACKAGE);
            }

            if (!PLAY_STORE_PACKAGE.equals(installer)) {
                return new Result(false,
                        "ChatGPT no fue instalado por Google Play. Una APK modificada puede funcionar unos minutos y luego fallar la licencia.");
            }
            return new Result(true, "ChatGPT oficial verificado");
        } catch (PackageManager.NameNotFoundException error) {
            return new Result(false, "ChatGPT oficial no está instalado.");
        }
    }

    static final class Result {
        final boolean valid;
        final String message;

        Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
    }
}
