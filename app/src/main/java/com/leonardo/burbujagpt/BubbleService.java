package com.leonardo.burbujagpt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class BubbleService extends Service {
    private WindowManager windowManager;
    private View bubbleView;
    private View panelView;
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams panelParams;
    private EditText input;

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
                .setContentText("Toca la burbuja para abrir el panel.")
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

        TextView title = new TextView(this);
        title.setText("BurbujaGPT");
        title.setTextColor(0xFFFFFFFF);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(17);
        root.addView(title);

        input = new EditText(this);
        input.setHint("Escribe o pega aquí...");
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFFBBBBBB);
        input.setSingleLine(false);
        root.addView(input, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        root.addView(makeButton("Copiar texto", v -> copyText()));
        root.addView(makeButton("Abrir ChatGPT", v -> openPackageOrUrl("com.openai.chatgpt", "https://chatgpt.com/")));
        root.addView(makeButton("Abrir Gemini", v -> openPackageOrUrl("com.google.android.apps.bard", "https://gemini.google.com/app")));
        root.addView(makeButton("Abrir WhatsApp", v -> openPackageOrUrl("com.whatsapp", "https://wa.me/")));
        root.addView(makeButton("Cerrar panel", v -> hidePanel()));
        root.addView(makeButton("Apagar burbuja", v -> stopSelf()));

        panelParams = new WindowManager.LayoutParams(
                dp(330), WindowManager.LayoutParams.WRAP_CONTENT, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        panelParams.gravity = Gravity.TOP | Gravity.START;
        panelParams.x = Math.max(dp(6), bubbleParams.x);
        panelParams.y = Math.max(dp(40), bubbleParams.y + dp(70));

        panelView = root;
        windowManager.addView(panelView, panelParams);
        input.requestFocus();
        input.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 200);
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

    private void copyText() {
        String text = input == null ? "" : input.getText().toString();
        if (text.trim().isEmpty()) {
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
        copyText();
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
            input = null;
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
