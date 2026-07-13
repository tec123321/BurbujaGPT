package com.leonardo.burbujagpt;

import org.json.JSONException;
import org.json.JSONObject;

final class BubbleRecord {
    final String id;
    String title;
    String sourceNotificationKey;
    int unreadCount;
    long lastSourcePostTime;
    long updatedAt;
    final boolean manual;

    BubbleRecord(String id, String title, String sourceNotificationKey, int unreadCount,
                 long lastSourcePostTime, long updatedAt, boolean manual) {
        this.id = id;
        this.title = title;
        this.sourceNotificationKey = sourceNotificationKey;
        this.unreadCount = Math.max(0, unreadCount);
        this.lastSourcePostTime = lastSourcePostTime;
        this.updatedAt = updatedAt;
        this.manual = manual;
    }

    int notificationId() {
        return 2000 + Math.floorMod(id.hashCode(), 900000);
    }

    String shortcutId() {
        return "gpt_bubble_" + id;
    }

    JSONObject toJson() {
        JSONObject value = new JSONObject();
        try {
            value.put("id", id);
            value.put("title", title);
            value.put("sourceNotificationKey", sourceNotificationKey == null ? JSONObject.NULL : sourceNotificationKey);
            value.put("unreadCount", unreadCount);
            value.put("lastSourcePostTime", lastSourcePostTime);
            value.put("updatedAt", updatedAt);
            value.put("manual", manual);
        } catch (JSONException ignored) {
        }
        return value;
    }

    static BubbleRecord fromJson(JSONObject value) throws JSONException {
        return new BubbleRecord(
                value.getString("id"),
                value.optString("title", "ChatGPT"),
                value.isNull("sourceNotificationKey") ? null : value.optString("sourceNotificationKey", null),
                value.optInt("unreadCount", 0),
                value.optLong("lastSourcePostTime", 0L),
                value.optLong("updatedAt", 0L),
                value.optBoolean("manual", false)
        );
    }
}
