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
    private float lightsAlpha;
    private float haloStrength = 1f;   // 1 = day, lower at night
    private float pulse;               // showroom breathing 0..1
    private ValueAnimator pulseAnim;

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

    /** Showroom: the halo breathes slowly. */
    void setShowroom(boolean on) {
        if (pulseAnim != null) pulseAnim.cancel();
        if (!on) {
            pulse = 0;
            invalidate();
            return;
        }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f, 0f).setDuration(5000);
        pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
        pulseAnim.setInterpolator(new LinearInterpolator());
        pulseAnim.addUpdateListener(v -> { pulse = (float) v.getAnimatedValue(); invalidate(); });
        pulseAnim.start();
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

        // The truck itself.
        bmp.setAlpha(255);
        drawCar(c);
    }

    private void drawCar(Canvas c) {
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

    private static int alpha(int a, float k) {
        int v = Math.max(0, Math.min(255, Math.round(a * k)));
        return (v << 24) | 0xD4AF37;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pulseAnim != null) pulseAnim.cancel();
    }
}
