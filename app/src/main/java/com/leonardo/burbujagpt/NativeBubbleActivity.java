package com.leonardo.burbujagpt;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * Contenido real del globo: la app oficial se ejecuta en una pantalla virtual
 * creada por Shizuku y esa pantalla se dibuja en este SurfaceView.
 */
public class NativeBubbleActivity extends Activity implements SurfaceHolder.Callback {
    private static final int REQUEST_SHIZUKU_PERMISSION = 1501;

    private final Shizuku.OnRequestPermissionResultListener permissionResultListener =
            this::onShizukuPermissionResult;

    private FrameLayout root;
    private SurfaceView surfaceView;
    private LinearLayout overlay;
    private TextView status;
    private ProgressBar progress;
    private LinearLayout inputPanel;
    private EditText composer;

    private String bubbleId;
    private String sourceNotificationKey;
    private String bubbleTitle;
    private Surface surface;
    private boolean surfaceReady;
    private boolean bridgeReady;
    private boolean attaching;
    private int displayId = -1;
    private int displayWidth;
    private int displayHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        readConversation(getIntent());
        NativeBubblePublisher.markRead(this, bubbleId);
        setContentView(buildUi());

        Shizuku.addRequestPermissionResultListener(permissionResultListener);
        surfaceView.getHolder().addCallback(this);
        ensureShizuku();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readConversation(intent);
        NativeBubblePublisher.markRead(this, bubbleId);
        if (displayId >= 0) launchChatGpt(false);
    }

    private void readConversation(Intent intent) {
        bubbleId = intent == null ? null : intent.getStringExtra(NativeBubblePublisher.EXTRA_BUBBLE_ID);
        sourceNotificationKey = intent == null
                ? null
                : intent.getStringExtra(NativeBubblePublisher.EXTRA_SOURCE_KEY);
        bubbleTitle = intent == null ? null : intent.getStringExtra(NativeBubblePublisher.EXTRA_TITLE);

        if ((bubbleId == null || bubbleId.isEmpty()) && intent != null && intent.getData() != null) {
            bubbleId = intent.getData().getLastPathSegment();
        }
        if (bubbleId == null || bubbleId.isEmpty()) bubbleId = "manual_default";
        if (bubbleTitle == null || bubbleTitle.isEmpty()) bubbleTitle = "ChatGPT";
    }

    private View buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111111);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(0xFF111111);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(4), 0, dp(4), 0);
        toolbar.setBackgroundColor(0xFF090909);

        toolbar.addView(toolButton("‹", "Atrás", view -> {
            if (displayId >= 0) ShizukuDisplayBridge.back(displayId);
        }), squareParams());

        TextView title = text(bubbleTitle, 13, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        toolbar.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1));

        toolbar.addView(toolButton("⌨", "Escribir", view -> toggleInputPanel()), squareParams());
        toolbar.addView(toolButton("↻", "Recargar", view -> launchChatGpt(false)), squareParams());
        toolbar.addView(toolButton("—", "Minimizar", view -> moveTaskToBack(true)), squareParams());
        content.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(42)
        ));

        surfaceView = new SurfaceView(this);
        surfaceView.setBackgroundColor(0xFF212121);
        surfaceView.setFocusable(true);
        surfaceView.setFocusableInTouchMode(true);
        surfaceView.getHolder().setFormat(PixelFormat.RGBA_8888);
        surfaceView.setOnTouchListener(this::forwardTouch);
        content.addView(surfaceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        inputPanel = new LinearLayout(this);
        inputPanel.setOrientation(LinearLayout.HORIZONTAL);
        inputPanel.setGravity(Gravity.CENTER_VERTICAL);
        inputPanel.setPadding(dp(8), dp(6), dp(8), dp(6));
        inputPanel.setBackgroundColor(0xFF171717);
        inputPanel.setVisibility(View.GONE);

        composer = new EditText(this);
        composer.setHint("Toca primero el cuadro de ChatGPT y escribe aquí");
        composer.setSingleLine(false);
        composer.setMaxLines(4);
        composer.setTextColor(Color.WHITE);
        composer.setHintTextColor(0xFF8A8A8A);
        composer.setTextSize(14);
        composer.setImeOptions(EditorInfo.IME_ACTION_SEND);
        composer.setBackground(rounded(0xFF262626, 14, 0xFF3F3F46));
        composer.setPadding(dp(12), dp(8), dp(12), dp(8));
        composer.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComposerText();
                return true;
            }
            return false;
        });
        inputPanel.addView(composer, new LinearLayout.LayoutParams(0, dp(52), 1));

        Button send = toolButton("➤", "Enviar", view -> sendComposerText());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(52), dp(52));
        sendParams.setMargins(dp(6), 0, 0, 0);
        inputPanel.addView(send, sendParams);
        content.addView(inputPanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setGravity(Gravity.CENTER);
        overlay.setPadding(dp(24), dp(22), dp(24), dp(22));
        overlay.setBackground(rounded(0xF21B1B1B, 20, 0xFF3F3F46));

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        overlay.addView(progress, progressParams);

        status = text("Preparando pantalla virtual…", 14, 0xFFE5E5E5, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.setMargins(0, dp(14), 0, 0);
        overlay.addView(status, statusParams);

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                Math.min(dp(330), getResources().getDisplayMetrics().widthPixels - dp(40)),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(overlay, overlayParams);
        return root;
    }

    private void ensureShizuku() {
        showLoading("Conectando con Shizuku…");
        if (!ShizukuDisplayBridge.isInstalled(this)) {
            showError("Instala Shizuku para crear la pantalla virtual.", "Instalar Shizuku", this::openShizuku);
            return;
        }
        if (!ShizukuDisplayBridge.isRunning()) {
            showError("Shizuku no está iniciado. Ábrelo y pulsa Iniciar.", "Abrir Shizuku", this::openShizuku);
            return;
        }
        if (!ShizukuDisplayBridge.hasPermission()) {
            try {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    showError(
                            "Autoriza Globo GPT desde la lista de aplicaciones de Shizuku.",
                            "Abrir Shizuku",
                            this::openShizuku
                    );
                } else {
                    ShizukuDisplayBridge.requestPermission(REQUEST_SHIZUKU_PERMISSION);
                }
            } catch (RuntimeException error) {
                showError("No se pudo solicitar el permiso de Shizuku.", "Abrir Shizuku", this::openShizuku);
            }
            return;
        }
        connectBridge();
    }

    private void onShizukuPermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_SHIZUKU_PERMISSION) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            connectBridge();
        } else {
            showError("Permiso de Shizuku rechazado.", "Reintentar", this::ensureShizuku);
        }
    }

    private void connectBridge() {
        ShizukuDisplayBridge.connect(this, new ShizukuDisplayBridge.ConnectionCallback() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    bridgeReady = true;
                    maybeAttachDisplay();
                });
            }

            @Override
            public void onError(String message) {
                runOnUiThread(() -> showError(message, "Reintentar", NativeBubbleActivity.this::ensureShizuku));
            }
        });
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        surface = holder.getSurface();
        surfaceReady = surface != null && surface.isValid();
        displayWidth = Math.max(1, holder.getSurfaceFrame().width());
        displayHeight = Math.max(1, holder.getSurfaceFrame().height());
        maybeAttachDisplay();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surface = holder.getSurface();
        surfaceReady = surface != null && surface.isValid();
        displayWidth = Math.max(1, width);
        displayHeight = Math.max(1, height);
        if (bridgeReady) maybeAttachDisplay();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        surfaceReady = false;
        surface = null;
        if (displayId >= 0) VirtualDisplaySessions.detach(this, bubbleId);
    }

    private void maybeAttachDisplay() {
        if (!bridgeReady || !surfaceReady || surface == null || attaching) return;
        attaching = true;
        showLoading(displayId >= 0 ? "Recuperando ChatGPT…" : "Creando pantalla virtual…");

        int width = Math.max(320, displayWidth > 1 ? displayWidth : surfaceView.getWidth());
        int height = Math.max(480, displayHeight > 1 ? displayHeight : surfaceView.getHeight());
        int density = Math.max(200, getResources().getDisplayMetrics().densityDpi);

        VirtualDisplaySessions.attach(
                this,
                bubbleId,
                width,
                height,
                density,
                surface,
                new VirtualDisplaySessions.Callback() {
                    @Override
                    public void onReady(int id, boolean newlyCreated) {
                        runOnUiThread(() -> {
                            attaching = false;
                            displayId = id;
                            displayWidth = width;
                            displayHeight = height;
                            launchChatGpt(newlyCreated);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            attaching = false;
                            showError(message, "Reintentar", NativeBubbleActivity.this::maybeAttachDisplay);
                        });
                    }
                }
        );
    }

    private void launchChatGpt(boolean multipleTask) {
        if (displayId < 0) {
            maybeAttachDisplay();
            return;
        }
        showLoading("Abriendo ChatGPT en el globo…");

        if (sourceNotificationKey != null
                && ChatGptNotificationListenerService.openSourceNotificationOnDisplay(
                        sourceNotificationKey,
                        displayId
                )) {
            getWindow().getDecorView().postDelayed(this::showContent, 700);
            return;
        }

        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                NativeBubblePublisher.CHATGPT_PACKAGE
        );
        ComponentName component = launcher == null ? null : launcher.getComponent();
        if (component == null) {
            showError("ChatGPT oficial no está instalado.", "Abrir Play Store", this::openChatGptStore);
            return;
        }

        int userId = Math.max(0, Process.myUid() / 100000);
        ShizukuDisplayBridge.launch(
                component.flattenToShortString(),
                userId,
                displayId,
                multipleTask,
                result -> runOnUiThread(() -> {
                    if (result == 0) {
                        AppPreferences.clearLastError(this);
                        getWindow().getDecorView().postDelayed(this::showContent, 650);
                    } else {
                        AppPreferences.recordMessage(
                                this,
                                "One UI rechazó iniciar ChatGPT en el display virtual. Código: " + result
                        );
                        showError(
                                "One UI rechazó iniciar ChatGPT en la pantalla virtual.",
                                "Reintentar",
                                () -> launchChatGpt(false)
                        );
                    }
                })
        );
    }

    private boolean forwardTouch(View view, MotionEvent event) {
        if (displayId < 0 || displayWidth <= 0 || displayHeight <= 0) return true;
        float scaleX = displayWidth / (float) Math.max(1, view.getWidth());
        float scaleY = displayHeight / (float) Math.max(1, view.getHeight());

        if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            for (int index = 0; index < event.getHistorySize(); index++) {
                ShizukuDisplayBridge.injectTouch(
                        displayId,
                        MotionEvent.ACTION_MOVE,
                        event.getHistoricalX(0, index) * scaleX,
                        event.getHistoricalY(0, index) * scaleY,
                        event.getDownTime(),
                        event.getHistoricalEventTime(index)
                );
            }
        }

        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_POINTER_UP) {
            return true;
        }
        ShizukuDisplayBridge.injectTouch(
                displayId,
                action,
                event.getX(0) * scaleX,
                event.getY(0) * scaleY,
                event.getDownTime(),
                event.getEventTime()
        );
        return true;
    }

    private void toggleInputPanel() {
        boolean show = inputPanel.getVisibility() != View.VISIBLE;
        inputPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            composer.requestFocus();
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.showSoftInput(composer, InputMethodManager.SHOW_IMPLICIT);
        } else {
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(composer.getWindowToken(), 0);
            surfaceView.requestFocus();
        }
    }

    private void sendComposerText() {
        String value = composer.getText().toString();
        if (displayId < 0 || value.trim().isEmpty()) return;
        ShizukuDisplayBridge.inputText(displayId, value, true);
        composer.setText("");
        inputPanel.setVisibility(View.GONE);
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(composer.getWindowToken(), 0);
        surfaceView.requestFocus();
    }

    private void showLoading(String message) {
        removeOverlayActions();
        overlay.setVisibility(View.VISIBLE);
        progress.setVisibility(View.VISIBLE);
        status.setText(message);
        status.setTextColor(0xFFE5E5E5);
    }

    private void showContent() {
        overlay.setVisibility(View.GONE);
        surfaceView.requestFocus();
    }

    private void showError(String message, String actionText, Runnable action) {
        removeOverlayActions();
        overlay.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
        status.setText(message);
        status.setTextColor(0xFFFCA5A5);

        Button actionButton = actionButton(actionText, view -> action.run());
        actionButton.setTag("overlay_action");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        actionParams.setMargins(0, dp(16), 0, 0);
        overlay.addView(actionButton, actionParams);

        Button settings = actionButton("Ajustes de Globo GPT", view -> {
            startActivity(new Intent(this, MainActivity.class));
            moveTaskToBack(true);
        });
        settings.setTag("overlay_action");
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        settingsParams.setMargins(0, dp(8), 0, 0);
        overlay.addView(settings, settingsParams);
    }

    private void removeOverlayActions() {
        for (int index = overlay.getChildCount() - 1; index >= 0; index--) {
            if ("overlay_action".equals(overlay.getChildAt(index).getTag())) {
                overlay.removeViewAt(index);
            }
        }
    }

    private void openShizuku() {
        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                ShizukuDisplayBridge.SHIZUKU_PACKAGE
        );
        if (launcher != null) {
            try {
                startActivity(launcher);
                return;
            } catch (RuntimeException ignored) {
            }
        }
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + ShizukuDisplayBridge.SHIZUKU_PACKAGE)
            ));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://shizuku.rikka.app/download/")
            ));
        }
    }

    private void openChatGptStore() {
        try {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + NativeBubblePublisher.CHATGPT_PACKAGE)
            ));
        } catch (ActivityNotFoundException error) {
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id="
                            + NativeBubblePublisher.CHATGPT_PACKAGE)
            ));
        }
    }

    @Override
    public void onBackPressed() {
        if (inputPanel.getVisibility() == View.VISIBLE) {
            toggleInputPanel();
        } else if (displayId >= 0) {
            ShizukuDisplayBridge.back(displayId);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
        if (isFinishing()) {
            VirtualDisplaySessions.release(this, bubbleId);
        } else if (displayId >= 0) {
            VirtualDisplaySessions.detach(this, bubbleId);
        }
        super.onDestroy();
    }

    private Button toolButton(String value, String description, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setContentDescription(description);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackgroundColor(Color.TRANSPARENT);
        button.setOnClickListener(listener);
        return button;
    }

    private Button actionButton(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackground(rounded(0xFF2A2A2A, 13, 0xFF454545));
        button.setOnClickListener(listener);
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.12f);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private GradientDrawable rounded(int color, int radiusDp, int borderColor) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), borderColor);
        return background;
    }

    private LinearLayout.LayoutParams squareParams() {
        return new LinearLayout.LayoutParams(dp(42), dp(42));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
