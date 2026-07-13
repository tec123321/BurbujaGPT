package com.leonardo.traductorleo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class TranslationOverlayView extends View {
    static final class Region {
        final Rect bounds;
        final String text;

        Region(Rect bounds, String text) {
            this.bounds = new Rect(bounds);
            this.text = text;
        }
    }

    private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private volatile List<Region> regions = Collections.emptyList();

    TranslationOverlayView(Context context) {
        super(context);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        backgroundPaint.setColor(Color.argb(244, 255, 255, 255));
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));
        borderPaint.setColor(Color.argb(180, 45, 45, 45));
        textPaint.setColor(Color.BLACK);
        textPaint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD));
    }

    void setRegions(List<Region> newRegions) {
        regions = newRegions == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(newRegions));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        List<Region> snapshot = regions;
        for (Region region : snapshot) {
            drawRegion(canvas, region);
        }
    }

    private void drawRegion(Canvas canvas, Region region) {
        Rect source = region.bounds;
        if (source.width() < dp(20) || source.height() < dp(10) || TextUtils.isEmpty(region.text)) {
            return;
        }

        int horizontalPad = (int) dp(5);
        int verticalPad = (int) dp(3);
        RectF box = new RectF(
                Math.max(0, source.left - horizontalPad),
                Math.max(0, source.top - verticalPad),
                Math.min(getWidth(), source.right + horizontalPad),
                Math.min(getHeight(), source.bottom + verticalPad)
        );
        if (box.width() <= dp(12) || box.height() <= dp(8)) {
            return;
        }

        float radius = dp(5);
        canvas.drawRoundRect(box, radius, radius, backgroundPaint);
        canvas.drawRoundRect(box, radius, radius, borderPaint);

        int textWidth = Math.max(1, Math.round(box.width() - horizontalPad * 2f));
        float size = Math.min(sp(19), Math.max(sp(10), box.height() * 0.46f));
        StaticLayout layout = buildLayout(region.text, textWidth, size);
        while (layout.getHeight() > box.height() - verticalPad * 2f && size > sp(8)) {
            size -= sp(0.8f);
            layout = buildLayout(region.text, textWidth, size);
        }

        canvas.save();
        canvas.clipRect(box);
        float top = box.top + Math.max(verticalPad, (box.height() - layout.getHeight()) / 2f);
        canvas.translate(box.left + horizontalPad, top);
        layout.draw(canvas);
        canvas.restore();
    }

    private StaticLayout buildLayout(String text, int width, float size) {
        textPaint.setTextSize(size);
        return StaticLayout.Builder.obtain(text, 0, text.length(), textPaint, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(3)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private float sp(float value) {
        return value * getResources().getDisplayMetrics().scaledDensity;
    }
}
