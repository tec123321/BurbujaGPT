package com.leonardo.globowhatsapp;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Minichat nativo que lee y responde mediante las acciones oficiales de notificación. */
public final class NativeBubbleActivity extends Activity
        implements ConversationStore.Listener {

    private LinearLayout root;
    private TextView titleView;
    private TextView subtitleView;
    private ScrollView messagesScroll;
    private LinearLayout messagesContainer;
    private EditText replyInput;
    private Button sendButton;
    private String token = "manual";
    private String fallbackTitle = "WhatsApp";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF07110C);
        getWindow().setNavigationBarColor(0xFF07110C);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        setContentView(buildUi());
        bindIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        bindIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ConversationStore.addListener(this);
        renderConversation();
    }

    @Override
    protected void onStop() {
        ConversationStore.removeListener(this);
        super.onStop();
    }

    @Override
    public void onConversationChanged(String changedToken) {
        if (token.equals(changedToken)) runOnUiThread(this::renderConversation);
    }

    private void bindIntent(Intent intent) {
        token = WhatsAppBubblePublisher.getConversationToken(intent);
        fallbackTitle = WhatsAppBubblePublisher.getConversationTitle(intent);
        ConversationStore.ensure(token, fallbackTitle);
        renderConversation();
    }

    private View buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1410);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(12), dp(12));
        header.setBackgroundColor(0xFF15251B);

        TextView icon = text("☎", 22, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable iconBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF128C7E, 0xFF25D366}
        );
        iconBackground.setShape(GradientDrawable.OVAL);
        icon.setBackground(iconBackground);
        header.addView(icon, new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        heading.setPadding(dp(12), 0, dp(8), 0);
        titleView = text("WhatsApp", 18, 0xFFF4F7F5, true);
        titleView.setSingleLine(true);
        heading.addView(titleView, matchWrap());
        subtitleView = text("Esperando un mensaje…", 12, 0xFFA9B7AE, false);
        subtitleView.setSingleLine(true);
        heading.addView(subtitleView, matchWrap());
        header.addView(heading, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));

        Button openButton = compactButton("↗", view -> openWhatsApp());
        openButton.setContentDescription("Abrir esta conversación en WhatsApp");
        header.addView(openButton, new LinearLayout.LayoutParams(dp(46), dp(46)));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        messagesContainer = new LinearLayout(this);
        messagesContainer.setOrientation(LinearLayout.VERTICAL);
        messagesContainer.setPadding(dp(12), dp(14), dp(12), dp(14));

        messagesScroll = new ScrollView(this);
        messagesScroll.setFillViewport(true);
        messagesScroll.addView(messagesContainer, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(messagesScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        LinearLayout composer = new LinearLayout(this);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.CENTER_VERTICAL);
        composer.setPadding(dp(10), dp(8), dp(10), dp(10));
        composer.setBackgroundColor(0xFF101C15);

        replyInput = new EditText(this);
        replyInput.setSingleLine(true);
        replyInput.setTextColor(0xFFF4F7F5);
        replyInput.setHintTextColor(0xFF7F9185);
        replyInput.setTextSize(15);
        replyInput.setPadding(dp(15), 0, dp(14), 0);
        replyInput.setImeOptions(EditorInfo.IME_ACTION_SEND);
        replyInput.setBackground(roundedBackground(0xFF1C2C22, 0xFF34503D, 22));
        replyInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendCurrentReply();
                return true;
            }
            return false;
        });
        composer.addView(replyInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        sendButton = compactButton("➤", view -> sendCurrentReply());
        sendButton.setContentDescription("Enviar respuesta");
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(50), dp(48));
        sendParams.setMargins(dp(8), 0, 0, 0);
        composer.addView(sendButton, sendParams);
        root.addView(composer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return root;
    }

    private void renderConversation() {
        if (messagesContainer == null) return;
        ConversationStore.Snapshot snapshot = ConversationStore.snapshot(token, fallbackTitle);
        titleView.setText(snapshot.title);

        messagesContainer.removeAllViews();
        if (snapshot.messages.isEmpty()) {
            TextView empty = text(
                    "Cuando llegue un mensaje de WhatsApp, aparecerá aquí y podrás responder sin salir del globo.",
                    14,
                    0xFF9DAAA2,
                    false
            );
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            emptyParams.setMargins(dp(24), dp(90), dp(24), 0);
            messagesContainer.addView(empty, emptyParams);
        } else {
            for (ConversationStore.MessageItem message : snapshot.messages) {
                messagesContainer.addView(messageRow(message), messageRowParams());
            }
        }

        replyInput.setEnabled(snapshot.canReply);
        sendButton.setEnabled(snapshot.canReply);
        sendButton.setAlpha(snapshot.canReply ? 1f : 0.42f);
        if (snapshot.canReply) {
            replyInput.setHint("Escribe una respuesta");
            subtitleView.setText("Respuesta rápida disponible");
        } else if (snapshot.messages.isEmpty()) {
            replyInput.setHint("Espera un mensaje");
            subtitleView.setText("Sin conversación activa");
        } else {
            replyInput.setHint("Respuesta no disponible");
            subtitleView.setText("Abre WhatsApp para responder");
        }

        messagesScroll.post(() -> messagesScroll.fullScroll(View.FOCUS_DOWN));
    }

    private View messageRow(ConversationStore.MessageItem message) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(message.outgoing ? Gravity.END : Gravity.START);

        TextView bubble = text(message.text, 15, 0xFFF4F7F5, false);
        bubble.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.76f));
        bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
        bubble.setBackground(roundedBackground(
                message.outgoing ? 0xFF075E54 : 0xFF1E3025,
                message.outgoing ? 0xFF128C7E : 0xFF35513E,
                16
        ));
        row.addView(bubble, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return row;
    }

    private LinearLayout.LayoutParams messageRowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        return params;
    }

    private void sendCurrentReply() {
        ConversationStore.SendResult result = ConversationStore.sendReply(
                this,
                token,
                replyInput.getText().toString()
        );
        if (result.sent) replyInput.setText("");
        else Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
    }

    private void openWhatsApp() {
        PendingIntent conversation = ConversationStore.getOpenTarget(token);
        if (conversation != null) {
            try {
                startIntentSender(conversation.getIntentSender(), null, 0, 0, 0);
                return;
            } catch (IntentSender.SendIntentException
                     | SecurityException
                     | IllegalArgumentException ignored) {
            }
        }

        Intent launcher = getPackageManager().getLaunchIntentForPackage(
                WhatsAppBubblePublisher.WHATSAPP_PACKAGE
        );
        if (launcher == null) {
            Toast.makeText(this, "WhatsApp oficial no está instalado", Toast.LENGTH_LONG).show();
            return;
        }
        launcher.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            startActivity(launcher);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "Android no pudo abrir WhatsApp", Toast.LENGTH_LONG).show();
        }
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

    private Button compactButton(String value, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(20);
        button.setTextColor(Color.WHITE);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setPadding(0, 0, 0, 0);
        button.setOnClickListener(listener);
        button.setBackground(roundedBackground(0xFF16884C, 0xFF2CCB70, 23));
        return button;
    }

    private GradientDrawable roundedBackground(int color, int stroke, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
