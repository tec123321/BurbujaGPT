package com.leonardo.globowhatsapp;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Historial efímero de los minichats. Vive solo en memoria y nunca escribe
 * nombres o mensajes en disco.
 */
final class ConversationStore {
    private static final int MAX_MESSAGES_PER_CHAT = 60;
    private static final Object LOCK = new Object();
    private static final Map<String, State> STATES = new HashMap<>();
    private static final Set<Listener> LISTENERS = new CopyOnWriteArraySet<>();

    interface Listener {
        void onConversationChanged(String token);
    }

    static final class MessageItem {
        final String text;
        final long timestamp;
        final boolean outgoing;

        MessageItem(String text, long timestamp, boolean outgoing) {
            this.text = text;
            this.timestamp = timestamp;
            this.outgoing = outgoing;
        }
    }

    static final class Snapshot {
        final String title;
        final List<MessageItem> messages;
        final boolean canReply;
        final boolean canOpenWhatsApp;

        Snapshot(
                String title,
                List<MessageItem> messages,
                boolean canReply,
                boolean canOpenWhatsApp
        ) {
            this.title = title;
            this.messages = messages;
            this.canReply = canReply;
            this.canOpenWhatsApp = canOpenWhatsApp;
        }
    }

    static final class SendResult {
        final boolean sent;
        final String message;

        SendResult(boolean sent, String message) {
            this.sent = sent;
            this.message = message;
        }
    }

    private static final class ReplyTarget {
        final PendingIntent actionIntent;
        final RemoteInput[] remoteInputs;

        ReplyTarget(PendingIntent actionIntent, RemoteInput[] remoteInputs) {
            this.actionIntent = actionIntent;
            this.remoteInputs = remoteInputs;
        }
    }

    private static final class State {
        String title;
        final ArrayDeque<MessageItem> messages = new ArrayDeque<>();
        PendingIntent openTarget;
        ReplyTarget replyTarget;

        State(String title) {
            this.title = title;
        }
    }

    private ConversationStore() {
    }

    static void ensure(String token, String title) {
        synchronized (LOCK) {
            State state = STATES.get(token);
            if (state == null) STATES.put(token, new State(safeTitle(title)));
            else if (!TextUtils.isEmpty(title)) state.title = safeTitle(title);
        }
    }

    static void updateFromNotification(
            String token,
            String title,
            String message,
            long timestamp,
            PendingIntent openTarget,
            Notification.Action[] actions
    ) {
        if (TextUtils.isEmpty(token) || TextUtils.isEmpty(message)) return;

        synchronized (LOCK) {
            State state = STATES.get(token);
            if (state == null) {
                state = new State(safeTitle(title));
                STATES.put(token, state);
            } else if (!TextUtils.isEmpty(title)) {
                state.title = safeTitle(title);
            }

            if (isWhatsAppPendingIntent(openTarget)) state.openTarget = openTarget;
            ReplyTarget reply = findReplyTarget(actions);
            if (reply != null) state.replyTarget = reply;

            long safeTime = timestamp > 0 ? timestamp : System.currentTimeMillis();
            MessageItem last = state.messages.peekLast();
            boolean duplicate = last != null
                    && !last.outgoing
                    && last.text.equals(message)
                    && Math.abs(last.timestamp - safeTime) < 5000L;
            if (!duplicate) {
                state.messages.addLast(new MessageItem(message, safeTime, false));
                trim(state);
            }
        }
        notifyChanged(token);
    }

    static Snapshot snapshot(String token, String fallbackTitle) {
        synchronized (LOCK) {
            State state = STATES.get(token);
            if (state == null) {
                state = new State(safeTitle(fallbackTitle));
                STATES.put(token, state);
            }
            return new Snapshot(
                    state.title,
                    Collections.unmodifiableList(new ArrayList<>(state.messages)),
                    isReplyTargetUsable(state.replyTarget),
                    isWhatsAppPendingIntent(state.openTarget)
            );
        }
    }

    static SendResult sendReply(Context context, String token, String rawText) {
        String text = rawText == null ? "" : rawText.trim();
        if (text.isEmpty()) return new SendResult(false, "Escribe un mensaje");

        ReplyTarget target;
        synchronized (LOCK) {
            State state = STATES.get(token);
            target = state == null ? null : state.replyTarget;
        }
        if (!isReplyTargetUsable(target)) {
            return new SendResult(false, "WhatsApp no ofreció una acción de respuesta activa");
        }

        Intent fillInIntent = new Intent();
        Bundle results = new Bundle();
        boolean hasFreeFormInput = false;
        for (RemoteInput input : target.remoteInputs) {
            if (input != null && input.getAllowFreeFormInput()) {
                results.putCharSequence(input.getResultKey(), text);
                hasFreeFormInput = true;
            }
        }
        if (!hasFreeFormInput) {
            return new SendResult(false, "La respuesta rápida de WhatsApp no admite texto");
        }

        try {
            RemoteInput.addResultsToIntent(target.remoteInputs, fillInIntent, results);
            RemoteInput.setResultsSource(fillInIntent, RemoteInput.SOURCE_FREE_FORM_INPUT);
            target.actionIntent.send(context, 0, fillInIntent);
        } catch (PendingIntent.CanceledException | SecurityException | IllegalArgumentException error) {
            synchronized (LOCK) {
                State state = STATES.get(token);
                if (state != null) state.replyTarget = null;
            }
            return new SendResult(false, "La respuesta caducó; espera el siguiente mensaje");
        }

        synchronized (LOCK) {
            State state = STATES.get(token);
            if (state == null) {
                state = new State("WhatsApp");
                STATES.put(token, state);
            }
            state.messages.addLast(new MessageItem(text, System.currentTimeMillis(), true));
            trim(state);
        }
        notifyChanged(token);
        return new SendResult(true, "Enviado");
    }

    static PendingIntent getOpenTarget(String token) {
        synchronized (LOCK) {
            State state = STATES.get(token);
            if (state == null || !isWhatsAppPendingIntent(state.openTarget)) return null;
            return state.openTarget;
        }
    }

    static void addListener(Listener listener) {
        if (listener != null) LISTENERS.add(listener);
    }

    static void removeListener(Listener listener) {
        if (listener != null) LISTENERS.remove(listener);
    }

    static void clear() {
        synchronized (LOCK) {
            STATES.clear();
        }
    }

    private static ReplyTarget findReplyTarget(Notification.Action[] actions) {
        if (actions == null) return null;

        ReplyTarget fallback = null;
        for (Notification.Action action : actions) {
            if (action == null
                    || !isWhatsAppPendingIntent(action.actionIntent)
                    || action.getRemoteInputs() == null
                    || action.getRemoteInputs().length == 0) {
                continue;
            }

            ReplyTarget candidate = new ReplyTarget(action.actionIntent, action.getRemoteInputs());
            if (action.getSemanticAction() == Notification.Action.SEMANTIC_ACTION_REPLY) {
                return candidate;
            }

            String label = action.title == null ? "" : action.title.toString().toLowerCase();
            if (label.contains("responder") || label.contains("reply")) return candidate;
            if (fallback == null) fallback = candidate;
        }
        return fallback;
    }

    private static boolean isReplyTargetUsable(ReplyTarget target) {
        return target != null
                && isWhatsAppPendingIntent(target.actionIntent)
                && target.remoteInputs != null
                && target.remoteInputs.length > 0;
    }

    private static boolean isWhatsAppPendingIntent(PendingIntent pendingIntent) {
        return pendingIntent != null
                && WhatsAppBubblePublisher.WHATSAPP_PACKAGE.equals(
                pendingIntent.getCreatorPackage()
        );
    }

    private static void trim(State state) {
        while (state.messages.size() > MAX_MESSAGES_PER_CHAT) state.messages.removeFirst();
    }

    private static void notifyChanged(String token) {
        for (Listener listener : LISTENERS) listener.onConversationChanged(token);
    }

    private static String safeTitle(String title) {
        if (title == null || title.trim().isEmpty()) return "WhatsApp";
        String clean = title.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.length() > 80 ? clean.substring(0, 79) + "…" : clean;
    }
}
