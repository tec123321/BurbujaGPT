package com.leonardo.burbujagpt;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

final class BubbleIconFactory {
    private BubbleIconFactory() {
    }

    static Bitmap create(Context context, int unreadCount) {
        int size = Math.max(96, Math.round(96 * context.getResources().getDisplayMetrics().density));
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint background = new Paint(Paint.ANTI_ALIAS_FLAG);
        background.setColor(0xFF101010);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, background);

        Drawable logo = context.getDrawable(R.drawable.ic_gpt_logo_foreground);
        if (logo != null) {
            int padding = Math.round(size * 0.16f);
            logo.setBounds(padding, padding, size - padding, size - padding);
            logo.draw(canvas);
        }

        if (unreadCount > 0) {
            String label = unreadCount > 99 ? "99+" : String.valueOf(unreadCount);
            float badgeRadius = size * (label.length() > 2 ? 0.22f : 0.19f);
            float cx = size - badgeRadius;
            float cy = badgeRadius;

            Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
            badge.setColor(Color.WHITE);
            canvas.drawCircle(cx, cy, badgeRadius, badge);

            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.BLACK);
            text.setTypeface(Typeface.DEFAULT_BOLD);
            text.setTextAlign(Paint.Align.CENTER);
            text.setTextSize(size * (label.length() > 2 ? 0.20f : 0.24f));
            Rect bounds = new Rect();
            text.getTextBounds(label, 0, label.length(), bounds);
            canvas.drawText(label, cx, cy - bounds.exactCenterY(), text);
        }
        return bitmap;
    }
}
