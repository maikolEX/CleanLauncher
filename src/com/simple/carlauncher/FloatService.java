package com.simple.carlauncher;

import android.app.AppOpsManager;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.List;

/**
 * GHOST GOLD floating panel over Android Auto (ZLink). Android does not let an app embed
 * Android Auto, so this small window floats on top with what Android Auto does not know about
 * the truck: speed, restriction, maintenance and alerts. It can be dragged (snaps to the left or
 * right edge) and minimised to a "GG" bubble; a new urgent alert opens it again.
 *
 * Shown when GHOST GOLD opens Android Auto, or by itself whenever ZLink comes to the front if
 * the user granted usage access. Hidden when the home screen is back.
 */
public class FloatService extends Service {

    static final String ZLINK = "com.zjinnova.zlink";
    static final String ACTION_SHOW = "show", ACTION_HIDE = "hide";

    private final Handler handler = new Handler();
    private WindowManager wm;
    private View panel, bubble;
    private WindowManager.LayoutParams panelLp, bubbleLp;
    private boolean shown, minimised;
    private int lastAlertLevel;
    private TextView speed, restrict, maint, odo, note;

    static void show(Context c) {
        if (Settings.canDrawOverlays(c)) c.startService(new Intent(c, FloatService.class).setAction(ACTION_SHOW));
    }

    static void hide(Context c) {
        c.startService(new Intent(c, FloatService.class).setAction(ACTION_HIDE));
    }

    static boolean hasUsageAccess(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops != null && ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), c.getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler.post(tick);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_SHOW.equals(a)) showPanel();
        else if (ACTION_HIDE.equals(a)) hidePanel();
        return START_STICKY;
    }

    // ---------- Windows ----------

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
    }

    private WindowManager.LayoutParams params() {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        SharedPreferences p = prefs();
        lp.x = p.getInt("x", dp(40));
        lp.y = p.getInt("y", dp(230));
        return lp;
    }

    private void showPanel() {
        if (!Settings.canDrawOverlays(this) || shown) return;
        if (panel == null) buildPanel();
        try {
            if (minimised) wm.addView(bubble, bubbleLp);
            else wm.addView(panel, panelLp);
            shown = true;
        } catch (Exception e) {
            android.util.Log.e("FloatService", "addView failed", e);
        }
    }

    private void hidePanel() {
        if (!shown) return;
        try {
            wm.removeView(minimised ? bubble : panel);
        } catch (Exception ignored) { }
        shown = false;
    }

    private void setMinimised(boolean m) {
        if (m == minimised) return;
        boolean was = shown;
        hidePanel();
        minimised = m;
        if (was) showPanel();
    }

    private void buildPanel() {
        panelLp = params();
        bubbleLp = params();

        // ----- Panel -----
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(18));
        bg.setColor(0xDB050505);
        bg.setStroke(dp(1), 0x80D4AF37);
        p.setBackground(bg);

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(14), 0, dp(4), 0);
        TextView gg = Gg.label(this, "GG");
        gg.setTextColor(Gg.GOLD);
        gg.setTextSize(12);
        bar.addView(gg);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(10);
        clock.setTypeface(Gg.display(this));
        clock.setLetterSpacing(0.2f);
        clock.setTextColor(Gg.LABEL);
        clock.setGravity(Gravity.CENTER);
        bar.addView(clock, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView min = new ImageView(this);
        min.setImageResource(R.drawable.ic_prev);
        min.setRotation(-90);
        min.setColorFilter(Gg.LABEL);
        min.setPadding(dp(12), dp(12), dp(12), dp(12));
        min.setContentDescription("Minimizar");
        min.setOnClickListener(v -> setMinimised(true));
        bar.addView(min, new LinearLayout.LayoutParams(dp(44), dp(44)));
        p.addView(bar, new LinearLayout.LayoutParams(-1, dp(46)));
        View hair = new View(this);
        hair.setBackgroundColor(0x33D4AF37);
        p.addView(hair, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(10), dp(16), dp(14));
        LinearLayout sp = new LinearLayout(this);
        sp.setGravity(Gravity.BOTTOM);
        speed = Gg.value(this, "0", 44, Gg.IGNITION);
        sp.addView(speed);
        TextView u = Gg.label(this, "km/h");
        u.setPadding(dp(6), 0, 0, dp(8));
        sp.addView(u);
        body.addView(sp);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(0, dp(8), 0, 0);
        restrict = cell(grid, "Restricción");
        maint = cell(grid, "Mantenimiento");
        odo = cell(grid, "Odómetro");
        body.addView(grid);
        note = Gg.body(this, "", 13, Gg.AMBER);
        note.setSingleLine(false);
        note.setMaxLines(2);
        note.setPadding(0, dp(8), 0, 0);
        body.addView(note, new LinearLayout.LayoutParams(dp(220), -2));
        p.addView(body);
        panel = p;
        drag(bar, panelLp, () -> panel);

        // ----- Bubble -----
        FrameLayout b = new FrameLayout(this);
        GradientDrawable bb = new GradientDrawable();
        bb.setShape(GradientDrawable.OVAL);
        bb.setColor(0xDB050505);
        bb.setStroke(dp(1), Gg.GOLD);
        b.setBackground(bb);
        EmblemView emb = new EmblemView(this);
        b.addView(emb, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));
        b.setLayoutParams(new FrameLayout.LayoutParams(dp(56), dp(56)));
        FrameLayout holder = new FrameLayout(this);
        holder.addView(b, new FrameLayout.LayoutParams(dp(56), dp(56)));
        bubble = holder;
        drag(holder, bubbleLp, () -> bubble);
        holder.setOnClickListener(v -> setMinimised(false));
    }

    private TextView cell(LinearLayout parent, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(4), 0, dp(4));
        TextView l = Gg.label(this, label);
        l.setTextSize(8);
        r.addView(l, new LinearLayout.LayoutParams(dp(104), -2));
        TextView v = Gg.body(this, "", 15, Gg.TEXT2);
        r.addView(v, new LinearLayout.LayoutParams(dp(116), -2));
        parent.addView(r);
        return v;
    }

    private interface ViewRef { View get(); }

    /** Drag by the given handle; on release snap to the nearest side so the map stays free. */
    private void drag(View handle, WindowManager.LayoutParams lp, ViewRef target) {
        final float[] start = new float[4];
        final boolean[] moved = new boolean[1];
        handle.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    start[0] = e.getRawX(); start[1] = e.getRawY(); start[2] = lp.x; start[3] = lp.y;
                    moved[0] = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                    if (Math.abs(dx) + Math.abs(dy) > dp(8)) moved[0] = true;
                    lp.x = Math.round(start[2] + dx);
                    lp.y = Math.max(0, Math.round(start[3] + dy));
                    update(target.get(), lp);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!moved[0]) { v.performClick(); return true; }
                    int screenW = getResources().getDisplayMetrics().widthPixels;
                    int w = target.get().getWidth();
                    lp.x = lp.x + w / 2 < screenW / 2 ? dp(16) : screenW - w - dp(16);
                    update(target.get(), lp);
                    // Keep both windows in the same place.
                    WindowManager.LayoutParams other = lp == panelLp ? bubbleLp : panelLp;
                    other.y = lp.y;
                    other.x = lp.x < screenW / 2 ? dp(16) : screenW - (lp == panelLp ? dp(56) : panel.getWidth()) - dp(16);
                    prefs().edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                    return true;
            }
            return false;
        });
    }

    private void update(View v, WindowManager.LayoutParams lp) {
        try {
            wm.updateViewLayout(v, lp);
        } catch (Exception ignored) { }
    }

    // ---------- Content + auto show ----------

    private final Runnable tick = new Runnable() {
        public void run() {
            autoShow();
            if (shown && !minimised) refresh();
            if (shown) checkAlerts();
            handler.postDelayed(this, 1000);
        }
    };

    private String lastForeground = "";
    private long lastQuery;

    /** With usage access: show over ZLink, hide when anything else comes to the front. */
    private void autoShow() {
        if (!hasUsageAccess(this) || !Settings.canDrawOverlays(this)) return;
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) return;
        long now = System.currentTimeMillis();
        UsageEvents ev = usm.queryEvents(lastQuery == 0 ? now - 10_000 : lastQuery, now);
        lastQuery = now;
        UsageEvents.Event e = new UsageEvents.Event();
        String fg = null;
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) fg = e.getPackageName();
        }
        if (fg == null || fg.equals(lastForeground)) return;
        lastForeground = fg;
        if (ZLINK.equals(fg)) showPanel();
        // Any other app (or our own screens) in front: the panel belongs to Android Auto only.
        else if (!"android".equals(fg) && !"com.android.systemui".equals(fg)) hidePanel();
    }

    private void refresh() {
        int s = Odometer.speedNow();
        speed.setText(String.valueOf(Math.max(0, s)));
        boolean blocked = Restriccion.activeNow(this);
        restrict.setText(blocked ? "Hoy no circulas" : Restriccion.restrictedToday(this) ? "Hoy · desde 19:00" : "Circulas");
        restrict.setTextColor(blocked ? Gg.AMBER : Gg.TEXT2);
        List<Mantenimiento.Item> due = Mantenimiento.attention(this);
        maint.setText(due.isEmpty() ? "Al día" : due.get(0).name);
        maint.setTextColor(due.isEmpty() ? Gg.TEXT2 : Gg.AMBER);
        odo.setText(Odometer.isConfigured(this) ? OilActivity.fmt(Odometer.currentKm(this)) + " km" : "—");
        List<Alertas.Alerta> alerts = Alertas.current(this);
        note.setText(alerts.isEmpty() ? "" : alerts.get(0).detail);
        note.setVisibility(alerts.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** A new urgent alert re-opens the panel from the bubble. */
    private void checkAlerts() {
        int lvl = 0;
        for (Alertas.Alerta a : Alertas.current(this)) lvl = Math.max(lvl, a.level);
        if (lvl == 2 && lastAlertLevel < 2 && minimised) setMinimised(false);
        lastAlertLevel = lvl;
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("float", MODE_PRIVATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        hidePanel();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
