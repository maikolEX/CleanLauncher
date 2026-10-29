package com.simple.carlauncher;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/**
 * Shown when the car is switched off after a drive: distance, time, speeds, an estimate of the
 * fuel used and what to keep in mind for tomorrow. Fades to black by itself; a touch closes it.
 */
public class ByeActivity extends Activity {

    /** Average consumption reported by the CAN box (L/100 km) until it can be read live. */
    static final float AVG_L_PER_100 = 8.4f;
    private static final long SHOW_MS = 14_000;

    private final Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        SharedPreferences t = Odometer.tripPrefs(this);
        float km = t.getFloat("meters", 0) / 1000f;
        float movingS = t.getFloat("moving_s", 0);
        long totalMs = Math.max(0, t.getLong("last_move", 0) - t.getLong("start", 0));
        float avg = movingS > 0 ? km / (movingS / 3600f) : 0;
        int max = Math.round(t.getFloat("max", 0));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Gg.VOID);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);

        EmblemView emb = new EmblemView(this);
        c.addView(emb, new LinearLayout.LayoutParams(dp(64), dp(64)));
        String n = Profile.name(this);
        TextView bye = Gg.label(this, n.isEmpty() ? "Hasta pronto" : "Hasta pronto, " + n);
        bye.setTextSize(13);
        bye.setTextColor(Gg.GOLD);
        bye.setLetterSpacing(0.35f);
        bye.setGravity(Gravity.CENTER);
        bye.setPadding(0, dp(18), 0, 0);
        c.addView(bye);
        TextView sub = Gg.label(this, "Resumen del viaje");
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(8), 0, 0);
        c.addView(sub);

        LinearLayout big = new LinearLayout(this);
        big.setGravity(Gravity.BOTTOM);
        TextView dist = Gg.value(this, String.format(Locale.US, "%.1f", km), 84, Gg.IGNITION);
        dist.setShadowLayer(dp(18), 0, 0, 0x59FFD700);
        big.addView(dist);
        TextView unit = Gg.label(this, "km");
        unit.setPadding(dp(10), 0, 0, dp(16));
        big.addView(unit);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = dp(18);
        c.addView(big, blp);

        LinearLayout grid = new LinearLayout(this);
        grid.addView(stat("Duración", duration(totalMs)));
        grid.addView(stat("Promedio", Math.round(avg) + " km/h"));
        grid.addView(stat("Máxima", max + " km/h"));
        grid.addView(stat("Combustible aprox.", String.format(Locale.US, "%.1f L", km * AVG_L_PER_100 / 100f)));
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-2, -2);
        glp.topMargin = dp(28);
        c.addView(grid, glp);

        View line = new View(this);
        line.setBackgroundColor(Gg.HAIR);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(520), dp(1));
        llp.setMargins(0, dp(28), 0, dp(18));
        c.addView(line, llp);

        TextView tomorrow = Gg.body(this, Restriccion.restrictedTomorrow(this)
                ? "Mañana no circulas en San José (6:00 a 19:00)"
                : "Mañana circulas sin restricción", 17, Restriccion.restrictedTomorrow(this) ? Gg.AMBER : Gg.TEXT2);
        tomorrow.setGravity(Gravity.CENTER);
        c.addView(tomorrow);
        List<Mantenimiento.Item> due = Mantenimiento.attention(this);
        if (!due.isEmpty()) {
            Mantenimiento.Item i = due.get(0);
            TextView m = Gg.body(this, i.name + " · " + Mantenimiento.statusLine(this, i), 15, Gg.CHAMPAGNE);
            m.setGravity(Gravity.CENTER);
            m.setPadding(0, dp(6), 0, 0);
            c.addView(m);
        }

        root.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        root.setOnClickListener(v -> finish());
        setContentView(root);

        c.setAlpha(0f);
        c.setTranslationY(dp(16));
        c.animate().alpha(1f).translationY(0).setDuration(900).start();
        handler.postDelayed(() -> c.animate().alpha(0f).setDuration(1500).withEndAction(this::finish).start(), SHOW_MS);
    }

    private View stat(String label, String value) {
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setPadding(dp(26), 0, dp(26), 0);
        TextView l = Gg.label(this, label);
        l.setGravity(Gravity.CENTER);
        s.addView(l);
        TextView v = Gg.value(this, value, 28, Gg.TEXT);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(6), 0, 0);
        s.addView(v);
        return s;
    }

    private static String duration(long ms) {
        long min = ms / 60_000;
        return min < 60 ? min + " min" : (min / 60) + " h " + (min % 60) + " min";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
