package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** GHOST GOLD emblem: watch-bezel ring with 12 indices and the "GG" monogram in gold metal. */
public class EmblemView extends View {

    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private float progress = 1f; // how much of the ring is drawn (boot animation)

    public EmblemView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeCap(Paint.Cap.ROUND);
        tick.setStrokeCap(Paint.Cap.ROUND);
        tick.setColor(Gg.GOLD);
        text.setTypeface(Gg.display(c));
        text.setTextAlign(Paint.Align.CENTER);
    }

    void setProgress(float p) {
        progress = p;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        LinearGradient metal = new LinearGradient(0, 0, w, h,
                new int[]{0xFFFFF1BF, Gg.GOLD, 0xFF7A5A12}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);
        ring.setShader(metal);
        text.setShader(metal);
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f, cy = getHeight() / 2f, u = s / 104f;
        float r = 48 * u;
        ring.setStrokeWidth(Math.max(1f, 2 * u));
        oval.set(cx - r, cy - r, cx + r, cy + r);
        c.drawArc(oval, -90, 360 * progress, false, ring);

        if (progress >= 1f) {
            for (int i = 0; i < 12; i++) {
                boolean major = i % 3 == 0;
                tick.setStrokeWidth(Math.max(1f, 1.4f * u));
                tick.setAlpha(major ? 255 : 140);
                double a = Math.toRadians(i * 30);
                float r1 = 44 * u, r2 = (major ? 36 : 40) * u;
                c.drawLine(cx + (float) Math.sin(a) * r1, cy - (float) Math.cos(a) * r1,
                        cx + (float) Math.sin(a) * r2, cy - (float) Math.cos(a) * r2, tick);
            }
        }
        text.setTextSize(20 * u);
        text.setLetterSpacing(0.05f);
        c.drawText("GG", cx, cy + 7 * u, text);
    }
}
