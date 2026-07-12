package com.leonardo.burbujagpt;

interface IDisplayBridge {
    void destroy() = 16777114;
    int launch(String component, int userId, int displayId, boolean multipleTask) = 1;
    boolean injectTouch(int displayId, int action, float x, float y, long downTime, long eventTime) = 2;
    boolean injectKey(int displayId, int keyCode, int action, long downTime, long eventTime) = 3;
    int inputText(int displayId, String text) = 4;
    int back(int displayId) = 5;
}
