package com.simple.carlauncher;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * The truck as the centre of the home screen: gold halo behind it, a soft gold floor glow,
 * a faded mirror reflection and a cross-fade to the "lights on" photo.
 */
public class TruckView extends View {

    private Bitmap car, lights;
    private Bitmap rear, carMirror;   // extra views for the showroom turntable
    private float spin = -1f;         // turntable angle 0..360 (-1 = off)
    private ValueAnimator spinAnim;
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float lightsAlpha;
    private float haloStrength = 1f;   // 1 = day, lower at night
    private float pulse;               // showroom breathing 0..1
    private ValueAnimator pulseAnim, sweepAnim;
    private float sweep = -1f;         // showroom light sweep position 0..1 (-1 = off)
    private final Paint sweepPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint floor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private final RectF tmp = new RectF();
    private final Matrix m = new Matrix();

    public TruckView(Context c) {
        super(c);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 1));
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        sweepPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
    }

    void setCar(Bitmap car, Bitmap lights) {
        this.car = car;
        this.lights = lights;
        requestLayout();
        invalidate();
    }

    void setCarResource(int res, int lightsRes) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        setCar(BitmapFactory.decodeResource(getResources(), res, o),
                lightsRes == 0 ? null : BitmapFactory.decodeResource(getResources(), lightsRes, o));
    }

    /** Rear photo: with it the showroom turntable shows front, rear and the mirrored front. */
    void setRearResource(int res) {
        if (res == 0) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        rear = BitmapFactory.decodeResource(getResources(), res, o);
    }

    private Bitmap mirror() {
        if (carMirror == null && car != null) {
            Matrix f = new Matrix();
            f.preScale(-1f, 1f);
            carMirror = Bitmap.createBitmap(car, 0, 0, car.getWidth(), car.getHeight(), f, true);
        }
        return carMirror;
    }

    boolean hasLights() {
        return lights != null;
    }

    void setLights(boolean on, boolean animate) {
        float target = on && lights != null ? 1f : 0f;
        if (!animate) {
            lightsAlpha = target;
            invalidate();
            return;
        }
        ValueAnimator a = ValueAnimator.ofFloat(lightsAlpha, target).setDuration(600);
        a.addUpdateListener(v -> { lightsAlpha = (float) v.getAnimatedValue(); invalidate(); });
        a.start();
    }

    void setNight(boolean night) {
        haloStrength = night ? 0.6f : 1f;
        invalidate();
    }

    /** Showroom: the halo breathes slowly and a gold light sweeps across the paint. */
    void setShowroom(boolean on) {
        if (pulseAnim != null) pulseAnim.cancel();
        if (sweepAnim != null) sweepAnim.cancel();
        if (spinAnim != null) spinAnim.cancel();
        if (!on) {
            pulse = 0;
            sweep = -1f;
            spin = -1f;
            invalidate();
            return;
        }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f, 0f).setDuration(5000);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(v -> { pulse = (float) v.getAnimatedValue(); invalidate(); });
        pulseAnim.start();
        // 0..1 is the sweep crossing the truck; 1..1.8 is a pause before the next pass.
        sweepAnim = ValueAnimator.ofFloat(-0.2f, 1.8f).setDuration(4200);
        sweepAnim.setRepeatCount(ValueAnimator.INFINITE);
        sweepAnim.setInterpolator(new LinearInterpolator());
        sweepAnim.addUpdateListener(v -> { sweep = (float) v.getAnimatedValue(); invalidate(); });
        sweepAnim.start();
        if (rear != null) {
            // Turntable: a full turn every 16 s.
            spinAnim = ValueAnimator.ofFloat(0f, 360f).setDuration(16000);
            spinAnim.setRepeatCount(ValueAnimator.INFINITE);
            spinAnim.setInterpolator(new LinearInterpolator());
            spinAnim.addUpdateListener(v -> { spin = (float) v.getAnimatedValue(); invalidate(); });
            spinAnim.start();
        }
    }

    /** Where the truck is drawn, for placing the data around it. */
    RectF truckRect() {
        return dst;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutTruck(w, h);
    }

    private void layoutTruck(int w, int h) {
        if (car == null || w == 0) return;
        float ar = car.getWidth() / (float) car.getHeight();
        float bw = w * 0.56f, bh = h * 0.64f;
        if (bw / ar > bh) bw = bh * ar;
        float bhh = bw / ar;
        float left = (w - bw) / 2f, top = h * 0.50f - bhh / 2f;
        dst.set(left, top, left + bw, top + bhh);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (car == null) return;
        if (dst.isEmpty()) layoutTruck(getWidth(), getHeight());
        float cx = dst.centerX(), cy = dst.centerY();
        float k = haloStrength * (1f + 0.35f * pulse);

        // Gold halo behind the truck.
        float r = dst.width() * 0.62f;
        halo.setShader(new RadialGradient(cx, cy, r,
                new int[]{alpha(0x3D, k), alpha(0x18, k), 0x00D4AF37}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, r, halo);

        // Floor glow and two thin gold orbit lines under the wheels.
        float fy = dst.bottom - dst.height() * 0.06f;
        float fw = dst.width() * 0.62f, fh = dst.height() * 0.11f;
        c.save();
        c.scale(1f, fh / fw, cx, fy);
        floor.setShader(new RadialGradient(cx, fy, fw,
                new int[]{alpha(0x55, k), alpha(0x14, k), 0x00D4AF37}, new float[]{0f, 0.6f, 1f},
                Shader.TileMode.CLAMP));
        c.drawCircle(cx, fy, fw, floor);
        c.restore();
        ring.setColor(alpha(0x66, k));
        tmp.set(cx - fw * 0.92f, fy - fh * 0.55f, cx + fw * 0.92f, fy + fh * 0.55f);
        c.drawOval(tmp, ring);
        ring.setColor(alpha(0x2A, k));
        tmp.inset(-fw * 0.12f, -fh * 0.12f);
        c.drawOval(tmp, ring);
        if (spin >= 0) {
            // Three gold markers travelling round the turntable rim.
            float rx = fw * 0.92f, ry = fh * 0.55f;
            for (int i = 0; i < 3; i++) {
                double a = Math.toRadians(spin + i * 120);
                float front = (float) Math.sin(a); // >0 = near side of the platform
                dot.setColor(alpha(front > 0 ? 0xE6 : 0x55, k));
                c.drawCircle(cx + (float) Math.cos(a) * rx, fy + front * ry, Ui.dp(getContext(), front > 0 ? 3 : 2), dot);
            }
        }

        // Mirror reflection fading into the floor.
        float reflH = dst.height() * 0.32f;
        int save = c.saveLayer(dst.left, dst.bottom, dst.right, dst.bottom + reflH, null);
        c.save();
        c.scale(1f, -1f, cx, dst.bottom);
        bmp.setAlpha(70);
        drawCar(c);
        c.restore();
        fade.setShader(new LinearGradient(0, dst.bottom, 0, dst.bottom + reflH,
                0xFF000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(dst.left, dst.bottom, dst.right, dst.bottom + reflH, fade);
        c.restoreToCount(save);

        // The truck itself (with the showroom light sweep clipped to the paint).
        bmp.setAlpha(255);
        if (sweep > -0.2f && sweep < 1.2f) {
            int l2 = c.saveLayer(dst.left, dst.top, dst.right, dst.bottom, null);
            drawCar(c);
            float x = dst.left + (dst.width() * 1.4f) * sweep - dst.width() * 0.2f;
            float band = dst.width() * 0.18f;
            sweepPaint.setShader(new LinearGradient(x - band, dst.top, x + band, dst.bottom,
                    new int[]{0x00FFF1BF, 0x66FFF1BF, 0x00FFF1BF}, null, Shader.TileMode.CLAMP));
            c.drawRect(dst, sweepPaint);
            c.restoreToCount(l2);
        } else {
            drawCar(c);
        }
    }

    private final RectF fit = new RectF();

    private void drawCar(Canvas c) {
        if (spin >= 0) {
            drawTurntable(c);
            return;
        }
        int base = bmp.getAlpha();
        if (lights != null && lightsAlpha > 0f) {
            bmp.setAlpha(Math.round(base * (1f - lightsAlpha)));
            draw(c, car);
            bmp.setAlpha(Math.round(base * lightsAlpha));
            draw(c, lights);
            bmp.setAlpha(base);
        } else {
            draw(c, car);
        }
    }

    private void draw(Canvas c, Bitmap b) {
        m.setRectToRect(new RectF(0, 0, b.getWidth(), b.getHeight()), dst, Matrix.ScaleToFit.FILL);
        c.drawBitmap(b, m, bmp);
    }

    /**
     * Turntable with four views (front, rear, mirrored front, rear). Each view holds while it
     * narrows a little, then flips edge-on into the next one, like the truck turning.
     */
    private void drawTurntable(Canvas c) {
        Bitmap[] views = {car, rear, mirror(), rear};
        int seg = (int) (spin / 90f) % 4;
        float t = (spin % 90f) / 90f;
        Bitmap b;
        float sx;
        if (t < 0.78f) {
            b = views[seg];
            sx = 1f - 0.05f * (t / 0.78f);
        } else if (t < 0.89f) {
            b = views[seg];
            float u = (t - 0.78f) / 0.11f;
            sx = 0.95f * (float) Math.cos(u * Math.PI / 2);
        } else {
            b = views[(seg + 1) % 4];
            float u = (t - 0.89f) / 0.11f;
            sx = (float) Math.sin(u * Math.PI / 2);
        }
        sx = Math.max(0.02f, sx);
        // Fit each view in the truck box, standing on the same floor line.
        float ar = b.getWidth() / (float) b.getHeight();
        float h = b == rear ? dst.height() * 0.88f : dst.height();
        float w = h * ar;
        if (w > dst.width()) { w = dst.width(); h = w / ar; }
        fit.set(dst.centerX() - w / 2f, dst.bottom - h, dst.centerX() + w / 2f, dst.bottom);
        m.setRectToRect(new RectF(0, 0, b.getWidth(), b.getHeight()), fit, Matrix.ScaleToFit.FILL);
        m.postScale(sx, 1f, dst.centerX(), 0);
        int base = bmp.getAlpha();
        bmp.setAlpha(Math.round(base * (0.55f + 0.45f * sx))); // edge-on = darker
        c.drawBitmap(b, m, bmp);
        bmp.setAlpha(base);
    }

    private static int alpha(int a, float k) {
        int v = Math.max(0, Math.min(255, Math.round(a * k)));
        return (v << 24) | 0xD4AF37;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnim != null) pulseAnim.cancel();
        if (sweepAnim != null) sweepAnim.cancel();
        if (spinAnim != null) spinAnim.cancel();
    }
}
