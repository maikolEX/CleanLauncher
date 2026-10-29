package com.simple.carlauncher;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.TextView;

/**
 * GHOST GOLD design tokens: black / gold palette and the two typefaces
 * (Michroma for labels and numbers, Barlow for text). The fonts are downloaded into
 * assets/fonts by the build; without them the system sans-serif is used.
 */
final class Gg {

    private Gg() { }

    // ---------- Palette (never purple, neon blue, neon green or dominant red) ----------
    static final int VOID = 0xFF050505;      // background
    static final int ONYX = 0xFF0A0A0A;      // surfaces
    static final int GRAPHITE = 0xFF111111;  // raised surfaces
    static final int GOLD = 0xFFD4AF37;      // primary accent
    static final int IGNITION = 0xFFFFD700;  // active / highlight
    static final int CHAMPAGNE = 0xFFC8A951; // secondary accent
    static final int TEXT = 0xFFFFFFFF;
    static final int TEXT2 = 0xFFEAEAEA;
    static final int LABEL = 0xFFB5B5B5;
    static final int OFF = 0xFF2A2A2A;
    static final int HAIR = 0x59D4AF37;      // gold hairline (35 %)
    static final int SURFACE = 0x1FD4AF37;   // gold tint (12 %)
    static final int AMBER = 0xFFE8A33D;     // warnings (never red as a theme colour)

    // ---------- Typefaces ----------
    private static Typeface display, light, regular;

    /** Michroma: wide, technical. Labels, clock, big numbers. */
    static Typeface display(Context c) {
        if (display == null) display = load(c, "fonts/Michroma-Regular.ttf",
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return display;
    }

    /** Barlow Light: large readable values. */
    static Typeface light(Context c) {
        if (light == null) light = load(c, "fonts/Barlow-Light.ttf",
                Typeface.create("sans-serif-light", Typeface.NORMAL));
        return light;
    }

    /** Barlow Regular: body text. */
    static Typeface regular(Context c) {
        if (regular == null) regular = load(c, "fonts/Barlow-Regular.ttf", Typeface.DEFAULT);
        return regular;
    }

    private static Typeface load(Context c, String path, Typeface fallback) {
        try {
            return Typeface.createFromAsset(c.getAssets(), path);
        } catch (Exception e) {
            return fallback;
        }
    }

    // ---------- Text styles ----------

    /** Small uppercase label with wide tracking, e.g. "VELOCIDAD". */
    static TextView label(Context c, CharSequence s) {
        TextView t = Ui.text(c, s.toString().toUpperCase(), 10, LABEL);
        t.setTypeface(display(c));
        t.setLetterSpacing(0.22f);
        t.setGravity(Gravity.START);
        return t;
    }

    /** Large light value, e.g. "21°" or "87.5". */
    static TextView value(Context c, CharSequence s, float sp, int color) {
        TextView t = Ui.text(c, s, sp, color);
        t.setTypeface(light(c));
        t.setIncludeFontPadding(false);
        t.setGravity(Gravity.START);
        return t;
    }

    /** Body text. */
    static TextView body(Context c, CharSequence s, float sp, int color) {
        TextView t = Ui.text(c, s, sp, color);
        t.setTypeface(regular(c));
        t.setGravity(Gravity.START);
        return t;
    }

    /** Rounded pill with a gold hairline; filled=true gives a gold tint. */
    static GradientDrawable pill(Context c, boolean filled) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(Ui.dp(c, 22));
        d.setColor(filled ? SURFACE : 0x00000000);
        d.setStroke(Ui.dp(c, 1), HAIR);
        return d;
    }
}
