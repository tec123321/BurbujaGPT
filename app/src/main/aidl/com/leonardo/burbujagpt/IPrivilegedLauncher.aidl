package com.leonardo.burbujagpt;

interface IPrivilegedLauncher {
    void destroy() = 16777114;
    int launch(String component, int userId, int left, int top, int right, int bottom) = 1;
}
