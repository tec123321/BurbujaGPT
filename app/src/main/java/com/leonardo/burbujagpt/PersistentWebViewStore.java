package com.leonardo.burbujagpt;

import android.content.Context;
import android.content.MutableContextWrapper;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

/** Mantiene una sola pagina web viva mientras el servicio de la burbuja esta activo. */
final class PersistentWebViewStore {
    private static final String CHATGPT_URL = "https://chatgpt.com/";
    private static final String WEB_PREFS = "chat_web_state";
    private static final String KEY_LAST_URL = "last_chat_url";

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

    /**
     * Crea el motor y empieza a cargar ChatGPT en cuanto se activa el servicio.
     * Así, el primer toque solo adjunta una página que ya se está preparando.
     */
    static void prewarm(Context context) {
        Context applicationContext = context.getApplicationContext();
        if (Looper.myLooper() != Looper.getMainLooper()) {
            new Handler(Looper.getMainLooper()).post(() -> prewarm(applicationContext));
            return;
        }

        try {
            if (retainedWebView != null && retainedWebView.getParent() != null) return;
            WebView webView = acquire(applicationContext);
            applyBaseSettings(webView);
            if (webView.getUrl() == null) {
                String lastUrl = applicationContext
                        .getSharedPreferences(WEB_PREFS, Context.MODE_PRIVATE)
                        .getString(KEY_LAST_URL, CHATGPT_URL);
                webView.loadUrl(lastUrl == null || !lastUrl.startsWith("https://")
                        ? CHATGPT_URL
                        : lastUrl);
            }
            release(applicationContext);
        } catch (RuntimeException ignored) {
            // ChatActivity volverá a crear el motor y mostrará el diagnóstico normal.
        }
    }

    static void applyBaseSettings(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkLoads(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setSaveFormData(false);
        settings.setTextZoom(100);
        settings.setDefaultTextEncodingName("UTF-8");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }
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
