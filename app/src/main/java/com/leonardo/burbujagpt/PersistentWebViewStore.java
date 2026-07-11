package com.leonardo.burbujagpt;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.MutableContextWrapper;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/** Mantiene una sola pagina web viva mientras el servicio de la burbuja esta activo. */
final class PersistentWebViewStore {
    private static WebView retainedWebView;
    private static MutableContextWrapper contextWrapper;
    private static boolean loadInProgress;
    private static final WebViewClient DETACHED_CLIENT = createDetachedClient();

    private PersistentWebViewStore() {
    }

    static boolean hasRetainedPage() {
        if (retainedWebView == null) return false;
        try {
            String url = retainedWebView.getUrl();
            // Un WebView recien precalentado puede representar about:blank; eso no es
            // una pagina que debamos reutilizar en lugar de cargar ChatGPT.
            return url != null && url.startsWith("https://");
        } catch (RuntimeException error) {
            discardAfterRendererGone(retainedWebView);
            return false;
        }
    }

    /**
     * Inicializa Chromium y empieza a cargar la ultima pagina segura al activar la burbuja.
     * La red y JavaScript trabajan antes del primer toque, sin rasterizar una vista oculta.
     */
    static void warmUp(Context applicationContext) {
        Context safeContext = applicationContext.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> warmUp(safeContext));
            return;
        }
        try {
            if (retainedWebView == null) {
                contextWrapper = new MutableContextWrapper(safeContext);
                retainedWebView = new WebView(contextWrapper);
            }
            ChatActivity.applyBaseWebViewSettings(retainedWebView);
            retainedWebView.setWebViewClient(DETACHED_CLIENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                retainedWebView.setRendererPriorityPolicy(
                        WebView.RENDERER_PRIORITY_IMPORTANT,
                        false
                );
            }

            String currentUrl = retainedWebView.getUrl();
            if (currentUrl == null || !currentUrl.startsWith("https://")) {
                loadInProgress = true;
                retainedWebView.loadUrl(ChatActivity.getLastSafeChatUrl(safeContext));
            }
        } catch (RuntimeException error) {
            WebView failedWebView = retainedWebView;
            retainedWebView = null;
            contextWrapper = null;
            loadInProgress = false;
            if (failedWebView != null) {
                try {
                    detachFromParent(failedWebView);
                    failedWebView.destroy();
                } catch (RuntimeException ignored) {
                }
            }
        }
    }

    static boolean isLoadInProgress(WebView webView) {
        return webView != null && webView == retainedWebView && loadInProgress;
    }

    static void markLoadStarted(WebView webView) {
        if (webView == retainedWebView) loadInProgress = true;
    }

    static void markLoadFinished(WebView webView) {
        if (webView == retainedWebView) loadInProgress = false;
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
            loadInProgress = false;
        }
    }

    static void destroyIfDetached() {
        if (retainedWebView == null || retainedWebView.getParent() != null) return;
        retainedWebView.stopLoading();
        retainedWebView.destroy();
        retainedWebView = null;
        contextWrapper = null;
        loadInProgress = false;
    }

    static void destroy(WebView webView) {
        if (webView == null) return;
        detachFromParent(webView);
        webView.stopLoading();
        webView.destroy();
        if (webView == retainedWebView) {
            retainedWebView = null;
            contextWrapper = null;
            loadInProgress = false;
        }
    }

    private static void detachFromParent(WebView webView) {
        ViewParent parent = webView.getParent();
        if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
    }

    private static WebViewClient createDetachedClient() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return new DetachedWebViewClient();
        return new DetachedWebViewClientBase();
    }

    private static class DetachedWebViewClientBase extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            markLoadStarted(view);
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            markLoadFinished(view);
            super.onPageCommitVisible(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            markLoadFinished(view);
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error
        ) {
            if (request.isForMainFrame()) markLoadFinished(view);
            super.onReceivedError(view, request, error);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private static final class DetachedWebViewClient extends DetachedWebViewClientBase {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            discardAfterRendererGone(view);
            return true;
        }
    }
}
