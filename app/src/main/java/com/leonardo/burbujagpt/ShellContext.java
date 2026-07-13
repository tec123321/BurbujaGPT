package com.leonardo.burbujagpt;

import android.annotation.TargetApi;
import android.content.AttributionSource;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Build;
import android.os.Process;

/**
 * Contexto de identidad shell para llamadas a servicios del sistema envueltas
 * por Shizuku. El nombre del paquete y el UID deben coincidir; de lo contrario
 * DisplayManagerService rechaza la creación con
 * "packageName must match the owner uid".
 */
final class ShellContext extends MutableContextWrapper {
    static final String PACKAGE_NAME = "com.android.shell";

    ShellContext(Context base) {
        super(base.getApplicationContext());
    }

    @Override
    public String getPackageName() {
        return PACKAGE_NAME;
    }

    @Override
    public String getOpPackageName() {
        return PACKAGE_NAME;
    }

    @TargetApi(Build.VERSION_CODES.S)
    @Override
    public AttributionSource getAttributionSource() {
        return new AttributionSource.Builder(Process.SHELL_UID)
                .setPackageName(PACKAGE_NAME)
                .build();
    }

    @Override
    public Context getApplicationContext() {
        return this;
    }

    /** Android 14+ consulta este método internamente en algunos servicios. */
    @SuppressWarnings("unused")
    public int getDeviceId() {
        return 0;
    }
}
