package com.leonardo.burbujagpt;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/** Ejecuta el arranque de ChatGPT con identidad shell mediante Shizuku. */
public class PrivilegedLauncherService extends IPrivilegedLauncher.Stub {

    public PrivilegedLauncherService() {
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    @Override
    public int launch(
            String component,
            int userId,
            int left,
            int top,
            int right,
            int bottom
    ) {
        if (component == null || component.trim().isEmpty()) return 64;

        String user = String.valueOf(Math.max(0, userId));
        String bounds = left + "," + top + "," + right + "," + bottom;

        String[][] attempts = new String[][]{
                {
                        "/system/bin/cmd", "activity", "start-activity",
                        "--user", user,
                        "--windowingMode", "5",
                        "--bounds", bounds,
                        "--activity-reorder-to-front",
                        "-n", component
                },
                {
                        "/system/bin/am", "start",
                        "--user", user,
                        "--windowingMode", "5",
                        "--bounds", bounds,
                        "--activity-reorder-to-front",
                        "-n", component
                },
                {
                        "/system/bin/cmd", "activity", "start-activity",
                        "--user", user,
                        "--windowingMode", "5",
                        "--activity-reorder-to-front",
                        "-n", component
                },
                {
                        "/system/bin/am", "start",
                        "--user", user,
                        "--windowingMode", "5",
                        "--activity-reorder-to-front",
                        "-n", component
                },
                {
                        "/system/bin/am", "start",
                        "--user", user,
                        "--windowingMode", "5",
                        "-n", component
                }
        };

        int lastResult = 1;
        for (String[] command : attempts) {
            lastResult = run(command);
            if (lastResult == 0) return 0;
        }
        return lastResult;
    }

    private int run(String[] command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();

            ByteArrayOutputStream sink = new ByteArrayOutputStream();
            try (InputStream stream = process.getInputStream()) {
                byte[] buffer = new byte[2048];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    sink.write(buffer, 0, read);
                    if (sink.size() > 32768) break;
                }
            }

            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return 124;
            }
            return process.exitValue();
        } catch (Throwable error) {
            if (process != null) process.destroy();
            return 1;
        }
    }
}
