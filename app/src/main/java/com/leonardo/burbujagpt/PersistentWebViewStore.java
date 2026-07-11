package com.leonardo.burbujagpt;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Mantiene una sola pagina web viva mientras el servicio de la burbuja esta activo. */
final class PersistentWebViewStore {
    private static WebView retainedWebView;
    private static MutableContextWrapper contextWrapper;
    private static final WebViewClient DETACHED_CLIENT = createDetachedClient();

    private PersistentWebViewStore() {
    }

    static boolean hasRetainedPage() {
        return retainedWebView != null && retainedWebView.getUrl() != null;
    }

    /**
     * Inicializa Chromium al activar la burbuja. No carga la pagina ni consume la sesion:
     * solo evita pagar todo el coste de arranque al primer toque.
     */
    static void warmUp(Context applicationContext) {
        Context safeContext = applicationContext.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> warmUp(safeContext));
            return;
        }
        if (retainedWebView != null) return;

        try {
            contextWrapper = new MutableContextWrapper(safeContext);
            retainedWebView = new WebView(contextWrapper);
        } catch (RuntimeException error) {
            retainedWebView = null;
            contextWrapper = null;
        }
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
        // No conservamos un WebViewClient interno de Activity porque filtraria la ventana.
        // Este cliente liviano mantiene la deteccion de un renderizador muerto mientras
        // la pagina esta separada de la interfaz.
        retainedWebView.setWebChromeClient(null);
        retainedWebView.setDownloadListener(null);
        retainedWebView.setWebViewClient(DETACHED_CLIENT);
        if (contextWrapper != null) contextWrapper.setBaseContext(applicationContext);
    }

    /** El WebView afectado ya no puede reutilizarse despues de perder su renderizador. */
    static void discardAfterRendererGone(WebView webView) {
        if (webView == null) return;
        detachFromParent(webView);
        try {
            webView.destroy();
        } catch (RuntimeException ignored) {
        }
        if (webView == retainedWebView) {
            retainedWebView = null;
            contextWrapper = null;
        }
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

    private static WebViewClient createDetachedClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new DetachedWebViewClient();
        return new WebViewClient();
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static final class DetachedWebViewClient extends WebViewClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            discardAfterRendererGone(view);
            return true;
        }
    }
}
