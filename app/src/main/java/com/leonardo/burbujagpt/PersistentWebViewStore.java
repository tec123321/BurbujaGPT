package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebView;

/** Mantiene una sola pagina web viva mientras el servicio de la burbuja esta activo. */
final class PersistentWebViewStore {
    private static WebView retainedWebView;
    private static MutableContextWrapper contextWrapper;

    private PersistentWebViewStore() {
    }

    static boolean hasRetainedPage() {
        return retainedWebView != null && retainedWebView.getUrl() != null;
    }

    static WebView acquire(Context activityContext) {
        if (retainedWebView == null) {
            contextWrapper = new MutableContextWrapper(activityContext);
            retainedWebView = new WebView(contextWrapper);
        } else {
            detachFromParent(retainedWebView);
            contextWrapper.setBaseContext(activityContext);
        }
        return retainedWebView;
    }

    static void release(Context applicationContext) {
        if (retainedWebView == null) return;
        detachFromParent(retainedWebView);
        if (contextWrapper != null) contextWrapper.setBaseContext(applicationContext);
    }

    static void destroyIfDetached() {
        if (retainedWebView == null || retainedWebView.getParent() != null) return;
        retainedWebView.stopLoading();
        retainedWebView.destroy();
        retainedWebView = null;
        contextWrapper = null;
    }

    static void destroy(WebView webView) {
        if (webView == null) return;
        detachFromParent(webView);
        webView.stopLoading();
        webView.destroy();
        if (webView == retainedWebView) {
            retainedWebView = null;
            contextWrapper = null;
        }
    }

    private static void detachFromParent(WebView webView) {
        ViewParent parent = webView.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
    }
}
