package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;

/**
 * Driving mode: the truck seen from behind on a gold-lined road. Lane markers rush towards
 * the viewer and the tyres roll, both at the real GPS speed; the tail lights glow when braking.
 */
public class DriveView extends View {

    private Bitmap rear, tread;
    private float speed;          // km/h
    private float lanePhase, rollPx;
    private long lastFrame;
    private long brakeUntil;
    private boolean running;

    private final Paint road = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tyre = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tyreShade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF(), box = new RectF();
    private final Matrix m = new Matrix();

    // Positions inside the rear photo, as fractions of its width/height.
    private static final float[][] TYRES = {{0.040f, 0.848f, 0.120f, 0.136f}, {0.845f, 0.848f, 0.120f, 0.136f}};
    private static final float[][] TAILS = {{0.000f, 0.321f, 0.080f, 0.283f}, {0.915f, 0.321f, 0.080f, 0.283f},
            {0.048f, 0.698f, 0.130f, 0.050f}, {0.810f, 0.698f, 0.130f, 0.050f}};

    public DriveView(Context c) {
        super(c);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        int rr = getResources().getIdentifier("car_rear", "drawable", c.getPackageName());
        int tr = getResources().getIdentifier("tread", "drawable", c.getPackageName());
        if (rr != 0) rear = BitmapFactory.decodeResource(getResources(), rr, o);
        if (tr != 0) tread = BitmapFactory.decodeResource(getResources(), tr, o);
        edge.setStyle(Paint.Style.STROKE);
        edge.setColor(0xB3D4AF37);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeCap(Paint.Cap.ROUND);
        mark.setColor(Gg.IGNITION);
    }

    boolean available() {
        return rear != null;
    }

    void setSpeed(float kmh) {
        // Braking: a clear drop in speed lights the tail lamps for a moment.
        if (kmh < speed - 6) brakeUntil = SystemClock.uptimeMillis() + 1200;
        speed = kmh;
    }

    void setRunning(boolean on) {
        running = on;
        lastFrame = 0;
        if (on) postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        long now = SystemClock.uptimeMillis();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        float v = Math.max(speed, 5); // keep a little motion even when the fix lags
        lanePhase = (lanePhase + dt * v / 55f) % 1f;  // ~1.4 s per marker at 40 km/h
        rollPx = (rollPx + dt * v * 3.2f * getResources().getDisplayMetrics().density) % 100000f;

        float vx = w / 2f, vy = h * 0.36f;

        // Horizon glow and line.
        glow.setShader(new RadialGradient(vx, vy, w * 0.36f, 0x73D4AF37, 0x00D4AF37, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.2f, vx, vy);
        c.drawCircle(vx, vy, w * 0.36f, glow);
        c.restore();
        edge.setStrokeWidth(Ui.dp(getContext(), 1));
        edge.setAlpha(90);
        c.drawLine(w * 0.15f, vy, w * 0.85f, vy, edge);

        // Road surface.
        path.reset();
        path.moveTo(vx - 4, vy);
        path.lineTo(vx + 4, vy);
        path.lineTo(w * 1.06f, h);
        path.lineTo(-w * 0.06f, h);
        path.close();
        road.setShader(new LinearGradient(0, vy, 0, h, new int[]{0x000A0A0A, 0xFF0B0B0B, 0xFF121212},
                new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
        c.drawPath(path, road);
        edge.setAlpha(180);
        edge.setStrokeWidth(Ui.dp(getContext(), 2));
        c.drawLine(vx - 2, vy, -w * 0.06f, h, edge);
        c.drawLine(vx + 2, vy, w * 1.06f, h, edge);

        // Lane markers: four per side, each growing from the vanishing point towards the viewer.
        float[] lx = {0.30f, 0.70f};
        for (float base : lx) {
            float bx1 = w * (base + (base < 0.5f ? 0.0f : 0.0f)), by1 = h * 0.86f;
            float bx2 = w * (base + (base < 0.5f ? -0.03f : 0.03f)), by2 = h * 0.94f;
            for (int i = 0; i < 4; i++) {
                float p = (lanePhase + i * 0.25f) % 1f;
                float s = 0.03f + 1.27f * p * p;
                mark.setAlpha(Math.round(255 * Math.min(1f, p / 0.12f)));
                mark.setStrokeWidth(Ui.dp(getContext(), 5) * s);
                c.drawLine(vx + (bx1 - vx) * s, vy + (by1 - vy) * s, vx + (bx2 - vx) * s, vy + (by2 - vy) * s, mark);
            }
        }

        if (rear == null) return;
        // The truck from behind.
        float bw = w * 0.36f, bh = bw * rear.getHeight() / rear.getWidth();
        if (bh > h * 0.62f) { bh = h * 0.62f; bw = bh * rear.getWidth() / rear.getHeight(); }
        box.set(vx - bw / 2f, h * 0.95f - bh, vx + bw / 2f, h * 0.95f);

        glow.setShader(new RadialGradient(vx, box.bottom, bw * 0.62f, 0x52FFD700, 0x00FFD700, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.14f, vx, box.bottom);
        c.drawCircle(vx, box.bottom, bw * 0.62f, glow);
        c.restore();
        glow.setShader(new RadialGradient(vx, box.bottom - bh * 0.01f, bw * 0.56f,
                new int[]{0xE6000000, 0xE6000000, 0x00000000}, new float[]{0f, 0.4f, 1f}, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.12f, vx, box.bottom);
        c.drawCircle(vx, box.bottom, bw * 0.56f, glow);
        c.restore();

        m.setRectToRect(new RectF(0, 0, rear.getWidth(), rear.getHeight()), box, Matrix.ScaleToFit.FILL);
        c.drawBitmap(rear, m, bmp);

        // Rolling tread over each tyre.
        if (tread != null) {
            for (float[] t : TYRES) {
                sub(t);
                float tileH = r.height() * 0.66f;
                BitmapShader sh = new BitmapShader(tread, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
                Matrix tm = new Matrix();
                float sc = tileH / tread.getHeight();
                tm.setScale(r.width() / tread.getWidth(), sc);
                tm.postTranslate(r.left, r.top + (rollPx % tileH));
                sh.setLocalMatrix(tm);
                tyre.setShader(sh);
                float rad = r.width() * 0.18f;
                c.drawRoundRect(r, rad, rad, tyre);
                tyreShade.setShader(new LinearGradient(0, r.top, 0, r.bottom,
                        new int[]{0xBF000000, 0x00000000, 0x00000000, 0xCC000000}, new float[]{0f, 0.3f, 0.7f, 1f},
                        Shader.TileMode.CLAMP));
                c.drawRoundRect(r, rad, rad, tyreShade);
            }
        }

        // Brake lights.
        if (now < brakeUntil) {
            for (float[] t : TAILS) {
                sub(t);
                tail.setShader(new RadialGradient(r.centerX(), r.centerY(), Math.max(r.width(), r.height()) * 0.7f,
                        0xE6FF4628, 0x00C8553D, Shader.TileMode.CLAMP));
                c.drawOval(r, tail);
            }
        }

        if (running) postInvalidateOnAnimation();
    }

    private void sub(float[] t) {
        r.set(box.left + box.width() * t[0], box.top + box.height() * t[1],
                box.left + box.width() * (t[0] + t[2]), box.top + box.height() * (t[1] + t[3]));
    }
}
