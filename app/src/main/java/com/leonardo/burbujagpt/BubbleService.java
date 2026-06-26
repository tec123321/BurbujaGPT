package com.leonardo.burbujagpt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class BubbleService extends Service {
    private static final String PREFS = "burbujagpt_prefs";
    private static final String KEY_TOKEN = "openai_token";

    private WindowManager windowManager;
    private View bubbleView;
    private View panelView;
    private WindowManager.LayoutParams bubbleParams;
    private EditText promptInput;
    private EditText tokenInput;
    private TextView responseView;
    private Button sendButton;

    @Override
    public void onCreate() {
        super.onCreate();
        startNotification();
        if (!canDrawOverlays()) {
            Toast.makeText(this, "Falta permiso de aparecer encima", Toast.LENGTH_LONG).show();
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeViews();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void startNotification() {
        String channelId = "bubble_service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "BurbujaGPT", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);

        Notification notification = builder
                .setContentTitle("BurbujaGPT activa")
                .setContentText("GPT dentro de una burbuja flotante.")
                .setSmallIcon(R.drawable.ic_bubble)
                .setOngoing(true)
                .build();

        startForeground(1001, notification);
    }

    private void showBubble() {
        TextView bubble = new TextView(this);
        bubble.setText("GPT");
        bubble.setTextColor(0xFFFFFFFF);
        bubble.setTextSize(14);
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        bubble.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF111111);
        bg.setStroke(dp(2), 0xFFFFFFFF);
        bubble.setBackground(bg);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        bubbleParams = new WindowManager.LayoutParams(
                dp(62), dp(62), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = dp(12);
        bubbleParams.y = dp(180);

        bubbleView = bubble;
        setupBubbleTouch();
        windowManager.addView(bubbleView, bubbleParams);
    }

    private void setupBubbleTouch() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long downTime;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downTime = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - initialTouchX);
                        int dy = (int) (event.getRawY() - initialTouchY);
                        if (Math.abs(dx) > dp(4) || Math.abs(dy) > dp(4)) moved = true;
                        bubbleParams.x = initialX + dx;
                        bubbleParams.y = initialY + dy;
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;
                    case MotionEvent.ACTION_UP:
                        long elapsed = System.currentTimeMillis() - downTime;
                        if (!moved && elapsed < 250) togglePanel();
                        return true;
                }
                return false;
            }
        });
    }

    private void togglePanel() {
        if (panelView == null) showPanel();
        else hidePanel();
    }

    private void showPanel() {
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF202124);
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), 0x66FFFFFF);
        root.setBackground(bg);

        TextView title = makeLabel("BurbujaGPT V2", 18, true);
        root.addView(title);

        root.addView(makeLabel("Token API de OpenAI", 13, false));
        tokenInput = new EditText(this);
        tokenInput.setHint("Pega tu token aquí");
        tokenInput.setSingleLine(true);
        tokenInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        tokenInput.setTextColor(0xFFFFFFFF);
        tokenInput.setHintTextColor(0xFFBBBBBB);
        tokenInput.setText(loadToken());
        root.addView(tokenInput, matchWrap());
        root.addView(makeButton("Guardar token", v -> saveToken()));

        root.addView(makeLabel("Pregunta", 13, false));
        promptInput = new EditText(this);
        promptInput.setHint("Escribe tu pregunta...");
        promptInput.setMinLines(3);
        promptInput.setMaxLines(6);
        promptInput.setTextColor(0xFFFFFFFF);
        promptInput.setHintTextColor(0xFFBBBBBB);
        promptInput.setSingleLine(false);
        root.addView(promptInput, matchWrap());

        sendButton = makeButton("Enviar a GPT", v -> sendToGpt());
        root.addView(sendButton);
        root.addView(makeButton("Copiar pregunta", v -> copyText(promptInput == null ? "" : promptInput.getText().toString())));
        root.addView(makeButton("Abrir ChatGPT oficial", v -> openPackageOrUrl("com.openai.chatgpt", "https://chatgpt.com/")));

        responseView = new TextView(this);
        responseView.setText("Respuesta GPT aparecerá aquí.");
        responseView.setTextColor(0xFFFFFFFF);
        responseView.setTextSize(15);
        responseView.setPadding(dp(8), dp(8), dp(8), dp(8));

        ScrollView scroll = new ScrollView(this);
        scroll.addView(responseView);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(210)
        );
        scrollParams.setMargins(0, dp(6), 0, dp(6));
        root.addView(scroll, scrollParams);

        root.addView(makeButton("Copiar respuesta", v -> copyText(responseView == null ? "" : responseView.getText().toString())));
        root.addView(makeButton("Cerrar panel", v -> hidePanel()));
        root.addView(makeButton("Apagar burbuja", v -> stopSelf()));

        WindowManager.LayoutParams panelParams = new WindowManager.LayoutParams(
                dp(350), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = Math.max(dp(6), bubbleParams.x);
        panelParams.y = Math.max(dp(20), bubbleParams.y + dp(70));

        panelView = root;
        windowManager.addView(panelView, panelParams);
        promptInput.requestFocus();
        promptInput.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(promptInput, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
    }

    private TextView makeLabel(String text, int size, boolean bold) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextColor(0xFFFFFFFF);
        label.setTextSize(size);
        label.setPadding(0, dp(6), 0, 0);
        if (bold) label.setTypeface(Typeface.DEFAULT_BOLD);
        return label;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private Button makeButton(String text, View.OnClickListener listener) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(4), 0, dp(4));
        b.setLayoutParams(params);
        return b;
    }

    private String loadToken() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        return prefs.getString(KEY_TOKEN, "");
    }

    private void saveToken() {
        String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        if (token.isEmpty()) {
            Toast.makeText(this, "Token vacío", Toast.LENGTH_SHORT).show();
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply();
        Toast.makeText(this, "Token guardado en este celular", Toast.LENGTH_SHORT).show();
    }

    private void sendToGpt() {
        String token = tokenInput == null ? "" : tokenInput.getText().toString().trim();
        String prompt = promptInput == null ? "" : promptInput.getText().toString().trim();

        if (token.isEmpty()) {
            setResponse("Falta pegar y guardar tu token API de OpenAI.");
            return;
        }
        if (prompt.isEmpty()) {
            setResponse("Escribe una pregunta primero.");
            return;
        }

        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_TOKEN, token).apply();
        sendButton.setEnabled(false);
        setResponse("Consultando GPT...");

        new Thread(() -> {
            try {
                String result = OpenAiClient.ask(token, prompt);
                postUi(() -> {
                    setResponse(result);
                    sendButton.setEnabled(true);
                });
            } catch (Exception e) {
                postUi(() -> {
                    setResponse("Error: " + e.getMessage());
                    sendButton.setEnabled(true);
                });
            }
        }).start();
    }

    private void setResponse(String text) {
        if (responseView != null) responseView.setText(text);
    }

    private void postUi(Runnable r) {
        new Handler(getMainLooper()).post(r);
    }

    private void copyText(String text) {
        if (text == null || text.trim().isEmpty()) {
            Toast.makeText(this, "No hay texto para copiar", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("BurbujaGPT", text));
            Toast.makeText(this, "Texto copiado", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPackageOrUrl(String packageName, String fallbackUrl) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launch);
        } else {
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl));
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(browser);
        }
    }

    private void hidePanel() {
        if (panelView != null) {
            try { windowManager.removeView(panelView); } catch (Exception ignored) {}
            panelView = null;
            promptInput = null;
            tokenInput = null;
            responseView = null;
            sendButton = null;
        }
    }

    private void removeViews() {
        hidePanel();
        if (bubbleView != null) {
            try { windowManager.removeView(bubbleView); } catch (Exception ignored) {}
            bubbleView = null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
