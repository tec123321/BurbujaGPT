package com.leonardo.burbujagpt;

import android.view.Surface;

interface IDisplayBridge {
    void destroy() = 16777114;
    int createDisplay(String name, int width, int height, int densityDpi, in Surface surface) = 1;
    boolean updateDisplay(int displayId, int width, int height, int densityDpi, in Surface surface) = 2;
    void detachDisplay(int displayId) = 3;
    void releaseDisplay(int displayId) = 4;
    int launch(String component, int userId, int displayId, boolean multipleTask) = 5;
    boolean injectTouch(int displayId, int action, float x, float y, long downTime, long eventTime) = 6;
    boolean injectKey(int displayId, int keyCode, int action, long downTime, long eventTime) = 7;
    int inputText(int displayId, String text) = 8;
    int back(int displayId) = 9;
}
