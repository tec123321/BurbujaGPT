package com.leonardo.burbujagpt;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

/** Ventana flotante que muestra el sitio oficial de ChatGPT. */
public class ChatActivity extends Activity {
    static final String EXTRA_URL = "chat_url";
    static final String EXTRA_MINIMIZE = "minimize_chat";
    static final String EXTRA_SAFE_WEBVIEW = "safe_webview";
    static volatile boolean isVisible;

    private static final String CHATGPT_URL = "https://chatgpt.com/";
    private static final String WEB_PREFS = "chat_web_state";
    private static final String KEY_LAST_URL = "last_chat_url";
    private static final String WINDOW_PREFS = "chat_window_geometry";
    private static final String KEY_CUSTOM_SIZE = "custom_size";
    private static final String KEY_WINDOW_WIDTH = "width";
    private static final String KEY_WINDOW_HEIGHT = "height";
    private static final String KEY_WINDOW_X = "x";
    private static final String KEY_WINDOW_Y = "y";
    private static final int FILE_CHOOSER_REQUEST = 4001;
    private static final int MICROPHONE_REQUEST = 4002;
    private static final int COLOR_PANEL = 0xFF18181B;
    private static final int COLOR_HEADER = 0xFF09090B;
    private static final int COLOR_WEB_BACKGROUND = 0xFF212121;
    private static final long LOAD_STALL_TIMEOUT_MS = 30_000L;

    private WebView webView;
    private FrameLayout webContainer;
    private ProgressBar progressBar;
    private LinearLayout errorBar;
    private TextView errorText;
    private TextView titleView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingPermissionRequest;
    private int panelSize;
    private boolean loginDialogShowing;
    private boolean customSize;
    private boolean nativeBubbleMode;
    private boolean reusedRetainedPage;
    private boolean safeWebViewMode;
    private boolean persistentWebViewManaged;
    private boolean recoveringWebView;
    private boolean pageCommitted;
    private int loadGeneration;
    private int automaticRecoveryCount;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable loadWatchdog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        nativeBubbleMode = this instanceof NativeBubbleActivity;
        safeWebViewMode = getIntent().getBooleanExtra(EXTRA_SAFE_WEBVIEW, false);

        Window window = getWindow();
        window.setBackgroundDrawable(nativeBubbleMode
                ? new ColorDrawable(COLOR_PANEL)
                : new ColorDrawable(Color.TRANSPARENT));
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if (!nativeBubbleMode) {
            window.setGravity(Gravity.CENTER);
            window.addFlags(
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            );
            setFinishOnTouchOutside(false);
        }

        panelSize = nativeBubbleMode
                ? AppPreferences.PANEL_FULL
                : AppPreferences.getPanelSize(this);
        customSize = getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE)
                .getBoolean(KEY_CUSTOM_SIZE, false);
        setContentView(buildUi());
        applyWindowSize();
        configureWebView();

        boolean restoredState = !reusedRetainedPage
                && savedInstanceState != null
                && webView.restoreState(savedInstanceState) != null;
        if (!reusedRetainedPage && !restoredState) {
            String requestedUrl = getIntent().getStringExtra(EXTRA_URL);
            webView.loadUrl(isSafeChatGptUrl(requestedUrl)
                    ? requestedUrl
                    : getResumeUrl(false));
        } else {
            verifyRetainedPage();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra(EXTRA_MINIMIZE, false)) {
            getWindow().getDecorView().post(this::minimizeChat);
            return;
        }
        String requestedUrl = intent.getStringExtra(EXTRA_URL);
        if (webView != null && isSafeChatGptUrl(requestedUrl)) webView.loadUrl(requestedUrl);
    }

    private View buildUi() {
        FrameLayout shell = new FrameLayout(this);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setClipToOutline(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(COLOR_PANEL);
        background.setCornerRadius(nativeBubbleMode ? 0 : dp(22));
        background.setStroke(nativeBubbleMode ? 0 : dp(1), 0xFF3F3F46);
        root.setBackground(background);
        if (!nativeBubbleMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            root.setElevation(dp(18));
        }

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(1), dp(4), dp(1));
        header.setBackgroundColor(COLOR_HEADER);

        header.addView(makeHeaderButton("‹", "Atrás", v -> goBack()), squareParams());
        header.addView(makeHeaderButton("+", "Chat nuevo", v -> webView.loadUrl(CHATGPT_URL)), squareParams());

        titleView = new TextView(this);
        titleView.setText("ChatGPT");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(13);
        titleView.setGravity(Gravity.CENTER);
        titleView.setSingleLine(true);
        titleView.setContentDescription("Arrastra para mover la ventana");
        titleView.setOnTouchListener(new WindowDragListener());
        header.addView(titleView, new LinearLayout.LayoutParams(0, dp(38), 1));

        header.addView(makeHeaderButton("□", "Cambiar tamaño", v -> cycleWindowSize()), squareParams());
        header.addView(makeHeaderButton("—", "Minimizar", v -> minimizeChat()), squareParams());

        Button menuButton = makeHeaderButton("⋮", "Más opciones", null);
        menuButton.setOnClickListener(v -> showMoreMenu(menuButton));
        header.addView(menuButton, squareParams());
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setProgress(0);
        root.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        ));

        errorBar = new LinearLayout(this);
        errorBar.setOrientation(LinearLayout.VERTICAL);
        errorBar.setPadding(dp(12), dp(9), dp(12), dp(9));
        errorBar.setBackgroundColor(0xFF3F1118);
        errorBar.setVisibility(View.GONE);

        errorText = new TextView(this);
        errorText.setTextColor(0xFFFDA4AF);
        errorText.setTextSize(13);
        errorBar.addView(errorText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout errorActions = new LinearLayout(this);
        errorActions.setOrientation(LinearLayout.HORIZONTAL);
        errorActions.setGravity(Gravity.END);
        errorActions.addView(makeSmallButton("Reintentar", v -> retryWebView()));
        errorActions.addView(makeSmallButton("Navegador", v -> openInBrowser()));
        errorActions.addView(makeSmallButton("App oficial", v -> openOfficialApp()));
        errorBar.addView(errorActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(errorBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(COLOR_WEB_BACKGROUND);
        if (safeWebViewMode) {
            reusedRetainedPage = false;
            persistentWebViewManaged = false;
            webView = new WebView(this);
        } else {
            reusedRetainedPage = PersistentWebViewStore.hasRetainedPage();
            persistentWebViewManaged = true;
            webView = PersistentWebViewStore.acquire(this);
        }
        attachWebViewToContainer();
        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        shell.addView(root, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (!nativeBubbleMode) {
            TextView resizeHandle = new TextView(this);
            resizeHandle.setText("◢");
            resizeHandle.setTextColor(0xFFB4B4BE);
            resizeHandle.setTextSize(17);
            resizeHandle.setGravity(Gravity.CENTER);
            resizeHandle.setContentDescription("Arrastra para cambiar el tamaño");
            resizeHandle.setOnTouchListener(new WindowResizeListener());
            FrameLayout.LayoutParams resizeParams = new FrameLayout.LayoutParams(
                    dp(30),
                    dp(30),
                    Gravity.BOTTOM | Gravity.END
            );
            resizeParams.setMargins(0, 0, dp(2), dp(2));
            shell.addView(resizeHandle, resizeParams);
        }

        return shell;
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setSaveFormData(false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // ChatGPT es una SPA pesada. Prerasterizarla oculta duplica memoria y puede
            // hacer que Android mate su renderizador en telefonos con poca RAM.
            settings.setOffscreenPreRaster(false);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.setSafeBrowsingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookies.setAcceptThirdPartyCookies(webView, true);
        }

        webView.setWebViewClient(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new ChatWebViewClientO()
                : new ChatWebViewClient());
        webView.setWebChromeClient(new ChatWebChromeClient());
        webView.setDownloadListener(new ChatDownloadListener());
    }

    private void attachWebViewToContainer() {
        if (webView == null || webContainer == null) return;
        ViewGroup parent = (ViewGroup) webView.getParent();
        if (parent != null) parent.removeView(webView);
        webContainer.removeAllViews();

        webView.setBackgroundColor(COLOR_WEB_BACKGROUND);
        // La ventana ya tiene aceleracion grafica. Una capa permanente aparte solo reserva
        // otra textura GPU; LAYER_TYPE_NONE deja que WebView componga cada cuadro normalmente.
        webView.setLayerType(View.LAYER_TYPE_NONE, null);
        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false);
        }
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
    }

    private String getResumeUrl(boolean allowCurrentWebView) {
        if (allowCurrentWebView && webView != null) {
            try {
                String currentUrl = webView.getUrl();
                if (isSafeChatGptUrl(currentUrl)) return currentUrl;
            } catch (RuntimeException ignored) {
            }
        }

        String lastUrl = getSharedPreferences(WEB_PREFS, MODE_PRIVATE)
                .getString(KEY_LAST_URL, CHATGPT_URL);
        return isSafeChatGptUrl(lastUrl) ? lastUrl : CHATGPT_URL;
    }

    private void retryWebView() {
        recreateWebView("Panel web reiniciado", true);
    }

    private void recreateWebView(String notice, boolean clearHttpCache) {
        replaceWebView(webView, false, getResumeUrl(true), notice, clearHttpCache);
    }

    private void recoverAfterRendererGone(WebView failedWebView, boolean didCrash) {
        String notice = didCrash
                ? "El motor web fallo y se recupero automaticamente"
                : "Android libero el motor web; la pagina se restauro";
        replaceWebView(failedWebView, true, getResumeUrl(false), notice, false);
    }

    private void replaceWebView(
            WebView previousWebView,
            boolean rendererGone,
            String resumeUrl,
            String notice,
            boolean clearHttpCache
    ) {
        if (recoveringWebView || isFinishing()) return;
        recoveringWebView = true;
        cancelLoadWatchdog();

        if (webContainer != null && previousWebView != null) {
            webContainer.removeView(previousWebView);
        }
        if (previousWebView != null) {
            if (clearHttpCache && !rendererGone) {
                try {
                    // Repara recursos web obsoletos sin borrar cookies, cuenta ni historial.
                    previousWebView.clearCache(true);
                } catch (RuntimeException ignored) {
                }
            }
            if (rendererGone) {
                if (persistentWebViewManaged) {
                    PersistentWebViewStore.discardAfterRendererGone(previousWebView);
                } else {
                    try {
                        previousWebView.destroy();
                    } catch (RuntimeException ignored) {
                    }
                }
            } else if (persistentWebViewManaged) {
                PersistentWebViewStore.destroy(previousWebView);
            } else {
                try {
                    previousWebView.stopLoading();
                    previousWebView.destroy();
                } catch (RuntimeException ignored) {
                }
            }
        }

        webView = null;
        pageCommitted = false;
        try {
            webView = persistentWebViewManaged
                    ? PersistentWebViewStore.acquire(this)
                    : new WebView(this);
            attachWebViewToContainer();
            configureWebView();
            webView.loadUrl(isSafeChatGptUrl(resumeUrl) ? resumeUrl : CHATGPT_URL);
            if (notice != null && !notice.isEmpty()) {
                Toast.makeText(this, notice, Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException error) {
            showWebError(
                    "No se pudo iniciar Android System WebView. Actualiza Chrome/WebView y reintenta."
            );
        } finally {
            recoveringWebView = false;
        }
    }

    private void scheduleLoadWatchdog() {
        cancelLoadWatchdog();
        pageCommitted = false;
        final int generation = ++loadGeneration;
        loadWatchdog = () -> {
            if (generation != loadGeneration || webView == null || pageCommitted || isFinishing()) {
                return;
            }
            if (automaticRecoveryCount == 0) {
                automaticRecoveryCount = 1;
                recreateWebView("La carga se bloqueo; el panel se reinicio automaticamente", false);
            } else {
                showWebError(
                        "ChatGPT no respondio en 30 segundos. Comprueba Internet y pulsa Reintentar."
                );
            }
        };
        mainHandler.postDelayed(loadWatchdog, LOAD_STALL_TIMEOUT_MS);
    }

    private void verifyRetainedPage() {
        if (webView == null) return;
        final WebView candidate = webView;
        final boolean[] answered = {false};
        Runnable timeout = () -> {
            if (!answered[0] && candidate == webView && !isFinishing()) {
                automaticRecoveryCount = 1;
                recreateWebView("La pagina guardada no respondia y fue restaurada", false);
            }
        };
        mainHandler.postDelayed(timeout, 4_000L);
        try {
            candidate.evaluateJavascript("document.readyState", value -> {
                answered[0] = true;
                mainHandler.removeCallbacks(timeout);
                if (candidate != webView || isFinishing()) return;
                if (value == null || "null".equals(value)) {
                    recreateWebView("La pagina guardada estaba vacia y fue restaurada", false);
                } else {
                    markPageResponsive();
                }
            });
        } catch (RuntimeException error) {
            answered[0] = true;
            mainHandler.removeCallbacks(timeout);
            recreateWebView("La pagina guardada estaba bloqueada y fue restaurada", false);
        }
    }

    private void markPageResponsive() {
        pageCommitted = true;
        automaticRecoveryCount = 0;
        cancelLoadWatchdog();
    }

    private void cancelLoadWatchdog() {
        loadGeneration++;
        if (loadWatchdog != null) {
            mainHandler.removeCallbacks(loadWatchdog);
            loadWatchdog = null;
        }
    }

    private Button makeHeaderButton(String text, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(18);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setContentDescription(description);
        button.setBackgroundColor(Color.TRANSPARENT);
        if (listener != null) button.setOnClickListener(listener);
        return button;
    }

    private Button makeSmallButton(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(11);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayout.LayoutParams squareParams() {
        return new LinearLayout.LayoutParams(dp(36), dp(38));
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Recargar");
        menu.getMenu().add(0, 2, 1, "Abrir app oficial");
        menu.getMenu().add(0, 3, 2, "Abrir en navegador");
        menu.getMenu().add(0, 4, 3, "Copiar enlace del chat");
        menu.getMenu().add(0, 5, 4, "Volver al inicio");
        menu.getMenu().add(0, 6, 5, "Reiniciar panel web");
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    webView.reload();
                    return true;
                case 2:
                    openOfficialApp();
                    return true;
                case 3:
                    openInBrowser();
                    return true;
                case 4:
                    copyCurrentUrl();
                    return true;
                case 5:
                    webView.loadUrl(CHATGPT_URL);
                    return true;
                case 6:
                    recreateWebView("Panel web reiniciado", true);
                    return true;
                default:
                    return false;
            }
        });
        menu.show();
    }

    private void goBack() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else minimizeChat();
    }

    private void minimizeChat() {
        if (nativeBubbleMode) {
            if (!moveTaskToBack(true)) finish();
            return;
        }

        View decor = getWindow().getDecorView();
        decor.animate().cancel();
        decor.animate()
                .alpha(0f)
                .scaleX(0.84f)
                .scaleY(0.84f)
                .setDuration(140)
                .withEndAction(() -> {
                    if (!moveTaskToBack(true)) finish();
                    decor.setAlpha(1f);
                    decor.setScaleX(1f);
                    decor.setScaleY(1f);
                })
                .start();
    }

    private void cycleWindowSize() {
        panelSize = (panelSize + 1) % 3;
        AppPreferences.setPanelSize(this, panelSize);
        customSize = false;
        getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CUSTOM_SIZE, false)
                .apply();
        applyWindowSize();
    }

    private void applyWindowSize() {
        if (nativeBubbleMode) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int width;
        int height;

        if (customSize && panelSize != AppPreferences.PANEL_FULL) {
            width = clamp(
                    getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE)
                            .getInt(KEY_WINDOW_WIDTH, dp(360)),
                    dp(280),
                    screenWidth - dp(8)
            );
            height = clamp(
                    getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE)
                            .getInt(KEY_WINDOW_HEIGHT, dp(560)),
                    dp(390),
                    screenHeight - dp(56)
            );
        } else if (panelSize == AppPreferences.PANEL_COMPACT) {
            width = Math.min(screenWidth - dp(24), dp(420));
            height = (int) (screenHeight * 0.70f);
        } else if (panelSize == AppPreferences.PANEL_FULL) {
            width = ViewGroup.LayoutParams.MATCH_PARENT;
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        } else {
            width = screenWidth - dp(12);
            height = Math.min(screenHeight - dp(84), (int) (screenHeight * 0.86f));
        }

        getWindow().setLayout(width, height);
        getWindow().setGravity(Gravity.CENTER);
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (panelSize == AppPreferences.PANEL_FULL) {
            attributes.x = 0;
            attributes.y = 0;
        } else {
            int maxX = Math.max(0, (screenWidth - width) / 2);
            int maxY = Math.max(0, (screenHeight - height) / 2);
            attributes.x = clamp(
                    getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE).getInt(KEY_WINDOW_X, 0),
                    -maxX,
                    maxX
            );
            attributes.y = clamp(
                    getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE).getInt(KEY_WINDOW_Y, 0),
                    -maxY,
                    maxY
            );
        }
        getWindow().setAttributes(attributes);
    }

    private void saveWindowGeometry() {
        View decor = getWindow().getDecorView();
        WindowManager.LayoutParams attributes = getWindow().getAttributes();
        if (decor.getWidth() <= 0 || decor.getHeight() <= 0) return;
        getSharedPreferences(WINDOW_PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_CUSTOM_SIZE, customSize)
                .putInt(KEY_WINDOW_WIDTH, decor.getWidth())
                .putInt(KEY_WINDOW_HEIGHT, decor.getHeight())
                .putInt(KEY_WINDOW_X, attributes.x)
                .putInt(KEY_WINDOW_Y, attributes.y)
                .apply();
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void openOfficialApp() {
        if (OfficialChatLauncher.openOfficialApp(this, true)) {
            finish();
            return;
        }
        Toast.makeText(this, "La app oficial no está instalada", Toast.LENGTH_SHORT).show();
        OfficialChatLauncher.openBrowser(this, CHATGPT_URL, true);
    }

    private void openInBrowser() {
        String currentUrl = webView == null ? CHATGPT_URL : webView.getUrl();
        if (!OfficialChatLauncher.openBrowser(this, currentUrl, true)) {
            Toast.makeText(this, "No hay navegador disponible", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyCurrentUrl() {
        String url = webView == null ? null : webView.getUrl();
        if (url == null || url.trim().isEmpty()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("ChatGPT", url));
            Toast.makeText(this, "Enlace copiado", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isSafeChatGptUrl(String url) {
        if (url == null) return false;
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        return "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && (host.equals("chatgpt.com") || host.endsWith(".chatgpt.com"));
    }

    private boolean isTrustedWebDestination(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);

        return host.equals("chatgpt.com")
                || host.endsWith(".chatgpt.com")
                || host.equals("openai.com")
                || host.endsWith(".openai.com")
                || host.equals("auth0.com")
                || host.endsWith(".auth0.com");
    }

    private boolean isExternalIdentityProvider(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.US);
        return host.equals("accounts.google.com")
                || host.equals("appleid.apple.com")
                || host.equals("login.microsoftonline.com")
                || host.equals("login.live.com");
    }

    private void showEmbeddedLoginBlocked() {
        if (loginDialogShowing || isFinishing()) return;
        loginDialogShowing = true;
        if (webView != null) webView.stopLoading();

        new AlertDialog.Builder(this)
                .setTitle("Google no permite este acceso")
                .setMessage(
                        "Google bloquea el inicio de sesión dentro de WebView. No es un error de tu cuenta. "
                                + "Usa la app oficial o abre ChatGPT en Brave/Chrome. Si quieres continuar "
                                + "dentro del panel, debes entrar con correo y contraseña de OpenAI."
                )
                .setNegativeButton("Usar correo", (dialog, which) -> {
                    if (webView != null && webView.canGoBack()) webView.goBack();
                    else if (webView != null) webView.loadUrl(CHATGPT_URL);
                })
                .setNeutralButton("Brave/Chrome", (dialog, which) ->
                        OfficialChatLauncher.openBrowser(this, CHATGPT_URL, true))
                .setPositiveButton("App oficial", (dialog, which) -> openOfficialApp())
                .setOnDismissListener(dialog -> loginDialogShowing = false)
                .show();
    }

    private boolean handleExternalNavigation(String url) {
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                OfficialChatLauncher.openBrowser(this, url, false);
                return true;
            }
            if ("intent".equalsIgnoreCase(scheme)) {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
                startActivity(intent);
                return true;
            }
            if ("mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)) {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(intent);
                return true;
            }
        } catch (Exception ignored) {
        }
        Toast.makeText(this, "No se pudo abrir ese enlace", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void showWebError(String message) {
        if (errorBar == null || errorText == null) return;
        errorText.setText(message);
        errorBar.setVisibility(View.VISIBLE);
    }

    private void hideWebError() {
        if (errorBar != null) errorBar.setVisibility(View.GONE);
    }

    private void handleWebPermission(PermissionRequest request) {
        boolean asksForAudio = false;
        for (String resource : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) {
                asksForAudio = true;
                break;
            }
        }

        if (!asksForAudio) {
            request.deny();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            return;
        }

        pendingPermissionRequest = request;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != MICROPHONE_REQUEST || pendingPermissionRequest == null) return;

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pendingPermissionRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            pendingPermissionRequest.deny();
        }
        pendingPermissionRequest = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileCallback == null) return;

        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileCallback.onReceiveValue(result);
        fileCallback = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (webView != null) webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        isVisible = false;
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isVisible = true;
        View decor = getWindow().getDecorView();
        decor.setAlpha(1f);
        decor.setScaleX(1f);
        decor.setScaleY(1f);
        if (webView != null) webView.onResume();
    }

    @Override
    protected void onDestroy() {
        isVisible = false;
        cancelLoadWatchdog();
        mainHandler.removeCallbacksAndMessages(null);
        if (fileCallback != null) {
            fileCallback.onReceiveValue(null);
            fileCallback = null;
        }
        if (pendingPermissionRequest != null) {
            pendingPermissionRequest.deny();
            pendingPermissionRequest = null;
        }
        if (webView != null) {
            CookieManager.getInstance().flush();
            if (persistentWebViewManaged && BubbleService.isRunning) {
                PersistentWebViewStore.release(getApplicationContext());
            } else if (persistentWebViewManaged) {
                PersistentWebViewStore.destroy(webView);
            } else {
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.setDownloadListener(null);
                webView.stopLoading();
                webView.destroy();
            }
            webView = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        goBack();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (!nativeBubbleMode && event.getActionMasked() == MotionEvent.ACTION_OUTSIDE) {
            minimizeChat();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private class WindowDragListener implements View.OnTouchListener {
        private int initialX;
        private int initialY;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (panelSize == AppPreferences.PANEL_FULL) return false;
            WindowManager.LayoutParams attributes = getWindow().getAttributes();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialX = attributes.x;
                    initialY = attributes.y;
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    int maxX = Math.max(0, (screenWidth - getWindow().getDecorView().getWidth()) / 2);
                    int maxY = Math.max(0, (screenHeight - getWindow().getDecorView().getHeight()) / 2);
                    attributes.x = clamp(
                            initialX + (int) (event.getRawX() - initialTouchX),
                            -maxX,
                            maxX
                    );
                    attributes.y = clamp(
                            initialY + (int) (event.getRawY() - initialTouchY),
                            -maxY,
                            maxY
                    );
                    getWindow().setAttributes(attributes);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveWindowGeometry();
                    return true;
                default:
                    return false;
            }
        }
    }

    private class WindowResizeListener implements View.OnTouchListener {
        private int initialWidth;
        private int initialHeight;
        private float initialTouchX;
        private float initialTouchY;

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (panelSize == AppPreferences.PANEL_FULL) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    initialWidth = getWindow().getDecorView().getWidth();
                    initialHeight = getWindow().getDecorView().getHeight();
                    initialTouchX = event.getRawX();
                    initialTouchY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int width = clamp(
                            initialWidth + (int) (event.getRawX() - initialTouchX),
                            dp(280),
                            getResources().getDisplayMetrics().widthPixels - dp(8)
                    );
                    int height = clamp(
                            initialHeight + (int) (event.getRawY() - initialTouchY),
                            dp(390),
                            getResources().getDisplayMetrics().heightPixels - dp(56)
                    );
                    customSize = true;
                    getWindow().setLayout(width, height);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    saveWindowGeometry();
                    return true;
                default:
                    return false;
            }
        }
    }

    private class ChatWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            if (isExternalIdentityProvider(uri)) {
                showEmbeddedLoginBlocked();
                return true;
            }
            if (isTrustedWebDestination(uri)) return false;
            return handleExternalNavigation(uri.toString());
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            Uri uri = Uri.parse(url);
            if (isExternalIdentityProvider(uri)) {
                showEmbeddedLoginBlocked();
                return true;
            }
            if (isTrustedWebDestination(uri)) return false;
            return handleExternalNavigation(url);
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            hideWebError();
            scheduleLoadWatchdog();
            super.onPageStarted(view, url, favicon);
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            markPageResponsive();
            super.onPageCommitVisible(view, url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            markPageResponsive();
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if (host != null && (host.equals("chatgpt.com") || host.endsWith(".chatgpt.com"))) {
                getSharedPreferences(WEB_PREFS, MODE_PRIVATE)
                        .edit()
                        .putString(KEY_LAST_URL, url)
                        .apply();
            }
            titleView.setText("ChatGPT");
            super.onPageFinished(view, url);
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request.isForMainFrame()) {
                cancelLoadWatchdog();
                showWebError("No se pudo cargar ChatGPT: " + error.getDescription());
            }
            super.onReceivedError(view, request, error);
        }

        @Override
        public void onReceivedHttpError(
                WebView view,
                WebResourceRequest request,
                WebResourceResponse errorResponse
        ) {
            if (request.isForMainFrame() && errorResponse.getStatusCode() >= 400) {
                cancelLoadWatchdog();
                showWebError(
                        "ChatGPT devolvió el error " + errorResponse.getStatusCode()
                                + ". Prueba el navegador o la app oficial."
                );
            }
            super.onReceivedHttpError(view, request, errorResponse);
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private final class ChatWebViewClientO extends ChatWebViewClient {
        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            if (view != webView || isFinishing()) {
                if (persistentWebViewManaged) {
                    PersistentWebViewStore.discardAfterRendererGone(view);
                } else {
                    try {
                        view.destroy();
                    } catch (RuntimeException ignored) {
                    }
                }
                if (view == webView) webView = null;
                return true;
            }
            recoverAfterRendererGone(view, detail.didCrash());
            return true;
        }
    }

    private class ChatWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            if (progressBar != null) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            super.onProgressChanged(view, newProgress);
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
        ) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = filePathCallback;
            try {
                startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                return true;
            } catch (ActivityNotFoundException e) {
                fileCallback = null;
                Toast.makeText(ChatActivity.this, "No hay selector de archivos", Toast.LENGTH_SHORT).show();
                return false;
            }
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            runOnUiThread(() -> handleWebPermission(request));
        }

        @Override
        public void onPermissionRequestCanceled(PermissionRequest request) {
            if (pendingPermissionRequest == request) pendingPermissionRequest = null;
        }
    }

    private class ChatDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                OfficialChatLauncher.openBrowser(ChatActivity.this, url, false);
                return;
            }

            try {
                String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(fileName);
                request.setMimeType(mimeType);
                request.setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                );
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null) request.addRequestHeader("Cookie", cookie);
                if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);

                DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                if (manager == null) throw new IllegalStateException("DownloadManager no disponible");
                manager.enqueue(request);
                Toast.makeText(ChatActivity.this, "Descarga iniciada", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                OfficialChatLauncher.openBrowser(ChatActivity.this, url, false);
            }
        }
    }
}
