package com.simple.carlauncher;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * GHOST GOLD home screen. The truck is the centre; time, weather, speed, radio, music,
 * the San José restriction and maintenance sit around it. Left rail for the main sections.
 */
public class MainActivity extends Activity implements NowPlaying.Listener {

    static final String ACTION_BOOT = "com.simple.carlauncher.GG_BOOT";
    private static final String SETTINGS = "com.android.car.setting/.home_horizontalActivity";
    private static final String WAZE = "com.waze", GMAPS = "com.google.android.apps.maps";
    private static final long WEATHER_EVERY_MS = 30 * 60 * 1000;

    /** The boot animation plays once per process start (head unit boot) and on ACC on. */
    private static boolean bootShown;
    private static long lastBootAt;
    private static final String TPMS = "com.nwd.can.setting/.ui.MainActivity";
    private static final long AUTO_SHOWROOM_MS = 2 * 60 * 1000;

    private final Handler handler = new Handler();
    private final RadioClient radio = new RadioClient();
    private NowPlaying nowPlaying;
    private long lastWeatherFetch;
    private boolean createdDark;
    private String createdProfile;

    private FrameLayout stage;
    private TruckView truck;
    private final List<View> chrome = new ArrayList<>(); // everything that hides in showroom
    private boolean showroom;
    private View rail, railHair;
    private TextView showroomTitle, showroomLabel;
    private long lastTouch = SystemClock.elapsedRealtime();

    private TextView greeting, weatherTemp, weatherDesc, weatherCity;
    private ImageView weatherIcon;
    private TextView speed, odometer;
    private TextView restrictText;
    private GradientDrawable restrictBg;
    private View restrictDot;
    private TextView fmBand, fmFreq;
    private TextView songSource, songTitle, songArtist;
    private ImageView playBtn;
    private TextView status;
    private FrameLayout bootLayer;

    private String profileStamp() {
        return Profile.name(this) + "|" + Profile.carPhotoFile(this).lastModified();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        createdDark = Ui.isDark(this);
        createdProfile = profileStamp();

        FrameLayout root = new FrameLayout(this);
        root.setBackground(Ui.background(this));

        LinearLayout row = new LinearLayout(this);
        rail = buildRail();
        row.addView(rail, new LinearLayout.LayoutParams(dp(92), -1));
        View hair = new View(this);
        railHair = hair;
        hair.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00D4AF37, 0x80D4AF37, 0x80D4AF37, 0x00D4AF37}));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(1), -1);
        hlp.setMargins(0, dp(40), 0, dp(40));
        row.addView(hair, hlp);
        stage = buildStage();
        row.addView(stage, new LinearLayout.LayoutParams(0, -1, 1f));
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        bootLayer = buildBoot();
        root.addView(bootLayer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        radio.bind(this);
        nowPlaying = NowPlaying.get(this);
        if (!Profile.isSetupDone(this) && Profile.bundledCar(this, false) == 0) {
            startActivity(new Intent(this, SetupActivity.class));
        }
        if (Profile.isNwd(this)) startService(new Intent(this, VolumeOverlay.class));
        startService(new Intent(this, GreeterService.class));
        startService(new Intent(this, Odometer.class));

        if (!bootShown) {
            bootShown = true;
            playBoot();
        } else {
            bootLayer.setVisibility(View.GONE);
        }
    }

    // ======================= Rail =======================

    private View buildRail() {
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER_HORIZONTAL);
        rail.setPadding(0, dp(18), 0, dp(14));

        EmblemView emb = new EmblemView(this);
        rail.addView(emb, new LinearLayout.LayoutParams(dp(46), dp(46)));
        Ui.pressable(emb, 0.9f);
        emb.setOnClickListener(v -> playBoot());

        LinearLayout items = new LinearLayout(this);
        items.setOrientation(LinearLayout.VERTICAL);
        items.setGravity(Gravity.CENTER);
        items.addView(railItem(R.drawable.ic_compass, "Mapa", this::openMaps, this::chooseMaps));
        items.addView(railItem(R.drawable.ic_music, "Música", () -> nowPlaying.open(this), null));
        items.addView(railItem(R.drawable.ic_phone, "Teléfono", this::openBluetooth, null));
        items.addView(railItem(R.drawable.ic_radio, "Radio", this::openRadio, null));
        items.addView(railItem(R.drawable.ic_apps, "Apps",
                () -> startActivity(new Intent(this, AppsActivity.class)), null));
        items.addView(railItem(R.drawable.ic_eq, "Sonido", this::openEqualizer, null));
        items.addView(railItem(R.drawable.ic_car, "Showroom", this::toggleShowroom, null));
        items.addView(railItem(R.drawable.ic_settings, "Ajustes",
                () -> startActivity(new Intent(this, SetupActivity.class)), this::openSystemSettings));
        rail.addView(items, new LinearLayout.LayoutParams(-1, 0, 1f));

        rail.addView(railItem(R.drawable.ic_w_moon, "Noche",
                () -> startActivity(new Intent(this, ScreenOffActivity.class)), null));
        return rail;
    }

    private View railItem(int icon, String name, Runnable tap, Runnable longTap) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(Gg.LABEL);
        iv.setPadding(dp(15), dp(15), dp(15), dp(15));
        iv.setContentDescription(name);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(56), dp(56));
        lp.setMargins(0, dp(3), 0, dp(3));
        iv.setLayoutParams(lp);
        Ui.pressable(iv, 0.86f);
        iv.setOnClickListener(v -> {
            flash(iv);
            tap.run();
        });
        if (longTap != null) iv.setOnLongClickListener(v -> { longTap.run(); return true; });
        return iv;
    }

    /** Brief gold glow on the touched rail icon. */
    private void flash(ImageView iv) {
        GradientDrawable g = Ui.circle(Gg.SURFACE);
        g.setStroke(dp(1), Gg.HAIR);
        iv.setBackground(g);
        iv.setColorFilter(Gg.IGNITION);
        handler.postDelayed(() -> { iv.setBackground(null); iv.setColorFilter(Gg.LABEL); }, 900);
    }

    // ======================= Stage (truck + data) =======================

    private FrameLayout buildStage() {
        FrameLayout s = new FrameLayout(this);

        truck = new TruckView(this);
        android.graphics.Bitmap own = Profile.carPhoto(this);
        int bundled = Profile.bundledCar(this, false), bundledLights = Profile.bundledCar(this, true);
        if (own != null) truck.setCar(own, null);
        else if (bundled != 0) truck.setCarResource(bundled, bundledLights);
        truck.setNight(createdDark);
        s.addView(truck, new FrameLayout.LayoutParams(-1, -1));
        truck.setOnClickListener(v -> toggleShowroom());
        truck.setOnLongClickListener(v -> { startActivity(new Intent(this, SetupActivity.class)); return true; });

        s.addView(chrome(buildClock()), at(Gravity.TOP | Gravity.START, 36, 22, 0, 0));
        s.addView(chrome(buildRestriction()), at(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 26, 0, 0));
        s.addView(chrome(buildWeather()), at(Gravity.TOP | Gravity.END, 0, 22, 36, 0));
        s.addView(chrome(buildSpeed()), at(Gravity.START | Gravity.CENTER_VERTICAL, 36, 40, 0, 0));
        s.addView(chrome(buildRadio()), at(Gravity.END | Gravity.CENTER_VERTICAL, 0, 0, 36, 90));
        s.addView(chrome(buildMusic()), at(Gravity.END | Gravity.BOTTOM, 0, 0, 36, 58));
        s.addView(chrome(buildStatus()), at(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, 0, 0, 20));

        // Showroom caption (only visible in showroom).
        LinearLayout cap = column();
        cap.setGravity(Gravity.CENTER_HORIZONTAL);
        showroomLabel = Gg.label(this, "Showroom");
        showroomLabel.setTextColor(Gg.GOLD);
        showroomLabel.setLetterSpacing(0.5f);
        cap.addView(showroomLabel);
        showroomTitle = Gg.label(this, "Dongfeng Rich 6");
        showroomTitle.setTextSize(22);
        showroomTitle.setLetterSpacing(0.35f);
        showroomTitle.setTextColor(Gg.TEXT);
        showroomTitle.setPadding(0, dp(10), 0, 0);
        cap.addView(showroomTitle);
        cap.setAlpha(0f);
        cap.setTag("caption");
        s.addView(cap, at(Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 40, 0, 0));
        return s;
    }

    private View chrome(View v) {
        chrome.add(v);
        return v;
    }

    private FrameLayout.LayoutParams at(int gravity, int l, int t, int r, int b) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, gravity);
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    // ---------- Clock + greeting ----------

    private View buildClock() {
        LinearLayout c = column();
        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(54);
        time.setTextColor(Gg.TEXT);
        time.setTypeface(Gg.light(this));
        time.setIncludeFontPadding(false);
        c.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE · d MMM");
        date.setFormat12Hour("EEEE · d MMM");
        date.setAllCaps(true);
        date.setTextSize(10);
        date.setLetterSpacing(0.22f);
        date.setTextColor(Gg.LABEL);
        date.setTypeface(Gg.display(this));
        date.setPadding(0, dp(6), 0, 0);
        c.addView(date);

        greeting = Gg.body(this, "", 16, Gg.TEXT2);
        greeting.setPadding(0, dp(12), 0, 0);
        c.addView(greeting);
        return c;
    }

    private void updateGreeting() {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String n = Profile.name(this);
        greeting.setText((h < 12 ? "Buenos días" : h < 19 ? "Buenas tardes" : "Buenas noches")
                + (n.isEmpty() ? "" : ", " + n));
    }

    // ---------- Restricción vehicular ----------

    private View buildRestriction() {
        LinearLayout pill = new LinearLayout(this);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(16), dp(10), dp(18), dp(10));
        restrictBg = Gg.pill(this, true);
        pill.setBackground(restrictBg);

        restrictDot = new View(this);
        pill.addView(restrictDot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        LinearLayout t = column();
        t.setPadding(dp(12), 0, 0, 0);
        TextView label = Gg.label(this, "Restricción · San José");
        label.setTextSize(8);
        label.setTextColor(Gg.GOLD);
        t.addView(label);
        restrictText = Gg.body(this, "", 15, Gg.TEXT);
        restrictText.setPadding(0, dp(2), 0, 0);
        t.addView(restrictText);
        pill.addView(t);

        Ui.pressable(pill, 0.96f);
        pill.setOnClickListener(v -> choosePlate());
        return pill;
    }

    private void updateRestriction() {
        boolean blocked = Restriccion.activeNow(this);
        boolean today = Restriccion.restrictedToday(this);
        restrictText.setText(Restriccion.status(this));
        int dot = blocked ? Gg.AMBER : today ? Gg.CHAMPAGNE : Gg.GOLD;
        restrictDot.setBackground(Ui.circle(dot));
        restrictBg.setStroke(dp(1), blocked ? 0xB3E8A33D : Gg.HAIR);
        restrictBg.setColor(blocked ? 0x26E8A33D : Gg.SURFACE);
    }

    private void choosePlate() {
        String[] digits = new String[10];
        for (int i = 0; i < 10; i++) digits[i] = "Placa terminada en " + i;
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("Último número de la placa")
                .setSingleChoiceItems(digits, Restriccion.plateDigit(this), (d, which) -> {
                    Restriccion.setPlateDigit(this, which);
                    updateRestriction();
                    d.dismiss();
                })
                .show();
    }

    // ---------- Weather ----------

    private View buildWeather() {
        LinearLayout c = column();
        c.setGravity(Gravity.END);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageResource(R.drawable.ic_w_cloud);
        weatherIcon.setColorFilter(Gg.GOLD);
        top.addView(weatherIcon, new LinearLayout.LayoutParams(dp(40), dp(40)));
        weatherTemp = Gg.value(this, "--°", 54, Gg.TEXT);
        weatherTemp.setPadding(dp(14), 0, 0, 0);
        top.addView(weatherTemp);
        c.addView(top);
        weatherDesc = Gg.body(this, "Cargando el clima…", 16, Gg.TEXT2);
        weatherDesc.setGravity(Gravity.END);
        weatherDesc.setPadding(0, dp(6), 0, 0);
        c.addView(weatherDesc, new LinearLayout.LayoutParams(dp(260), -2));
        weatherCity = Gg.label(this, "");
        weatherCity.setGravity(Gravity.END);
        weatherCity.setPadding(0, dp(4), 0, 0);
        c.addView(weatherCity, new LinearLayout.LayoutParams(dp(260), -2));
        Ui.pressable(c, 0.97f);
        c.setOnClickListener(v -> fetchWeather(true));
        return c;
    }

    private void showWeather(Weather w) {
        if (w == null) return;
        weatherIcon.setImageResource(w.icon());
        weatherIcon.setColorFilter(w.icon() == R.drawable.ic_w_sun ? Gg.IGNITION : Gg.GOLD);
        weatherTemp.setText(w.temp + "°");
        weatherDesc.setText(w.description());
        weatherCity.setText((w.city.isEmpty() ? "" : w.city + " · ") + "Máx " + w.max + " · Mín " + w.min);
    }

    private void fetchWeather(boolean force) {
        long now = SystemClock.elapsedRealtime();
        if (!force && lastWeatherFetch != 0 && now - lastWeatherFetch < WEATHER_EVERY_MS) return;
        lastWeatherFetch = now;
        Weather.fetch(this, this::showWeather);
    }

    // ---------- Speed + odometer ----------

    private View buildSpeed() {
        LinearLayout c = column();
        c.addView(Gg.label(this, "Velocidad"));
        LinearLayout v = new LinearLayout(this);
        v.setGravity(Gravity.BOTTOM);
        speed = Gg.value(this, "--", 60, Gg.IGNITION);
        speed.setShadowLayer(dp(14), 0, 0, 0x59FFD700);
        v.addView(speed);
        TextView unit = Gg.label(this, "km/h");
        unit.setPadding(dp(8), 0, 0, dp(10));
        v.addView(unit);
        c.addView(v);
        View line = new View(this);
        line.setBackgroundColor(Gg.HAIR);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(150), dp(1));
        llp.setMargins(0, dp(12), 0, dp(14));
        c.addView(line, llp);
        c.addView(Gg.label(this, "Odómetro"));
        odometer = Gg.body(this, "", 20, Gg.TEXT);
        odometer.setPadding(0, dp(4), 0, 0);
        c.addView(odometer);
        c.addView(buildTires(), new LinearLayout.LayoutParams(-2, -2));
        Ui.pressable(c, 0.97f);
        c.setOnClickListener(v2 -> startActivity(new Intent(this, OilActivity.class)));
        return c;
    }

    /** Tyre pressure: the radio's CAN box knows it; until we can read it, open its TPMS screen. */
    private View buildTires() {
        LinearLayout c = column();
        c.setPadding(0, dp(18), 0, 0);
        c.addView(Gg.label(this, "Llantas · TPMS"));
        LinearLayout grid = new LinearLayout(this);
        grid.setGravity(Gravity.CENTER_VERTICAL);
        grid.setPadding(0, dp(8), 0, 0);
        // Four tyres seen from above: front pair, then rear pair.
        LinearLayout car = new LinearLayout(this);
        car.setOrientation(LinearLayout.VERTICAL);
        for (int axle = 0; axle < 2; axle++) {
            LinearLayout pair = new LinearLayout(this);
            for (int side = 0; side < 2; side++) {
                View t = new View(this);
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(3));
                g.setStroke(dp(1), Gg.GOLD);
                g.setColor(Gg.SURFACE);
                t.setBackground(g);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(10), dp(18));
                lp.setMargins(side == 0 ? 0 : dp(16), axle == 0 ? 0 : dp(8), 0, 0);
                pair.addView(t, lp);
            }
            car.addView(pair);
        }
        grid.addView(car);
        TextView t = Gg.body(this, "Ver presión", 16, Gg.TEXT);
        t.setPadding(dp(14), 0, 0, 0);
        grid.addView(t);
        c.addView(grid);
        Ui.pressable(c, 0.96f);
        c.setOnClickListener(v -> {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setComponent(android.content.ComponentName.unflattenFromString(TPMS));
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception e) {
                toast("No se encontró la pantalla de TPMS del radio");
            }
        });
        return c;
    }

    private void updateSpeed() {
        int s = Odometer.speedNow();
        // With the 10 m GPS filter no fixes arrive while parked: no recent fix = stopped.
        speed.setText(String.valueOf(Math.max(0, s)));
        odometer.setText(Odometer.isConfigured(this) ? OilActivity.fmt(Odometer.currentKm(this)) + " km" : "Configurar");
    }

    // ---------- Radio ----------

    private View buildRadio() {
        LinearLayout c = column();
        c.setGravity(Gravity.END);
        fmBand = Gg.label(this, "Radio FM");
        fmBand.setGravity(Gravity.END);
        c.addView(fmBand, new LinearLayout.LayoutParams(dp(220), -2));
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(Ui.iconButton(this, R.drawable.ic_prev, 40, false, () -> stationStep(false)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        fmFreq = Gg.value(this, "--.-", 38, Gg.TEXT);
        fmFreq.setPadding(dp(14), 0, dp(14), 0);
        r.addView(fmFreq);
        r.addView(Ui.iconButton(this, R.drawable.ic_next, 40, false, () -> stationStep(true)),
                new LinearLayout.LayoutParams(dp(40), dp(40)));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = dp(6);
        c.addView(r, rlp);
        Ui.pressable(c, 0.97f);
        c.setOnClickListener(v -> openRadio());
        return c;
    }

    private void stationStep(boolean next) {
        if (!Profile.isNwd(this)) { openRadio(); return; }
        if (!Ui.radioIsSource(this)) {
            Ui.selectRadio(this, radio);
            handler.postDelayed(this::updateRadio, 2500);
            return;
        }
        radio.preset(next);
        handler.postDelayed(this::updateRadio, 300);
    }

    private void updateRadio() {
        RadioClient.Station st = radio.current();
        if (st == null) return;
        fmBand.setText((st.psName.isEmpty() ? "Radio " + st.bandLabel() : st.bandLabel() + " · " + st.psName).toUpperCase());
        fmFreq.setText(st.frequencyLabel() + (st.isAm() ? " kHz" : ""));
    }

    private final Runnable radioPoll = new Runnable() {
        public void run() {
            updateRadio();
            handler.postDelayed(this, 1000);
        }
    };

    // ---------- Music ----------

    private View buildMusic() {
        LinearLayout c = column();
        c.setGravity(Gravity.END);
        songSource = Gg.label(this, "Música");
        songSource.setGravity(Gravity.END);
        c.addView(songSource, new LinearLayout.LayoutParams(dp(280), -2));
        songTitle = Gg.body(this, "", 20, Gg.TEXT);
        songTitle.setGravity(Gravity.END);
        songTitle.setPadding(0, dp(4), 0, 0);
        c.addView(songTitle, new LinearLayout.LayoutParams(dp(280), -2));
        songArtist = Gg.body(this, "", 15, Gg.LABEL);
        songArtist.setGravity(Gravity.END);
        c.addView(songArtist, new LinearLayout.LayoutParams(dp(280), -2));

        LinearLayout ctl = new LinearLayout(this);
        ctl.setGravity(Gravity.CENTER_VERTICAL);
        ctl.addView(Ui.iconButton(this, R.drawable.ic_prev, 44, false, () -> nowPlaying.skip(false)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        playBtn = Ui.iconButton(this, R.drawable.ic_play, 58, true, () -> nowPlaying.playPause());
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(dp(58), dp(58));
        plp.setMargins(dp(14), 0, dp(14), 0);
        ctl.addView(playBtn, plp);
        ctl.addView(Ui.iconButton(this, R.drawable.ic_next, 44, false, () -> nowPlaying.skip(true)),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, -2);
        clp.topMargin = dp(10);
        c.addView(ctl, clp);
        Ui.pressable(c, 0.98f);
        c.setOnClickListener(v -> nowPlaying.open(this));
        return c;
    }

    @Override
    public void onNowPlayingChanged() {
        NowPlaying.Source s = nowPlaying.current();
        NowPlaying.Track t = s == null ? null : nowPlaying.track(s);
        boolean empty = t == null || t.title.isEmpty();
        songSource.setText(s == null ? "MÚSICA" : nowPlaying.label(s).toUpperCase());
        songTitle.setText(empty ? "Nada sonando" : t.title);
        songArtist.setText(empty ? "Toca para abrir la música" : t.artist);
        playBtn.setImageResource(t != null && t.playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    // ---------- Status line (maintenance) ----------

    private View buildStatus() {
        status = Gg.label(this, "");
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(16), dp(10), dp(16), dp(10));
        Ui.pressable(status, 0.96f);
        status.setOnClickListener(v -> startActivity(new Intent(this, OilActivity.class)));
        return status;
    }

    private void updateStatus() {
        String oil;
        if (!Odometer.isConfigured(this)) oil = "Aceite · configurar";
        else {
            int left = Odometer.remainingKm(this);
            oil = left < 0 ? "Aceite · cambio vencido" : "Aceite en " + OilActivity.fmt(left) + " km";
        }
        boolean warn = Odometer.isConfigured(this) && Odometer.remainingKm(this) <= Odometer.WARN_KM;
        status.setText(("Mantenimiento · " + oil + "     ·     Placa termina en " + Restriccion.plateDigit(this)).toUpperCase());
        status.setTextColor(warn ? Gg.AMBER : Gg.LABEL);
    }

    // ======================= Showroom =======================

    private void toggleShowroom() {
        showroom = !showroom;
        lastTouch = SystemClock.elapsedRealtime();
        truck.setShowroom(showroom);
        DecelerateInterpolator ease = new DecelerateInterpolator(2f);
        for (View v : chrome) v.animate().alpha(showroom ? 0f : 1f).setStartDelay(0).setDuration(700).start();
        rail.animate().alpha(showroom ? 0f : 1f).setDuration(700).start();
        railHair.animate().alpha(showroom ? 0f : 1f).setDuration(700).start();
        truck.animate().scaleX(showroom ? 1.16f : 1f).scaleY(showroom ? 1.16f : 1f)
                .translationY(showroom ? dp(26) : 0).setStartDelay(0).setDuration(1600).setInterpolator(ease).start();
        View cap = stage.findViewWithTag("caption");
        cap.animate().alpha(showroom ? 1f : 0f).setStartDelay(showroom ? 900 : 0).setDuration(900).start();
    }

    /** In showroom any touch brings the dashboard back; any touch also resets the idle timer. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        lastTouch = SystemClock.elapsedRealtime();
        if (showroom && e.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            toggleShowroom();
            return true;
        }
        if (showroom) return true;
        return super.dispatchTouchEvent(e);
    }

    // ======================= Boot animation =======================

    private FrameLayout buildBoot() {
        FrameLayout b = new FrameLayout(this);
        b.setBackgroundColor(Gg.VOID);
        b.setClickable(true); // swallow touches while it plays

        LinearLayout c = column();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        EmblemView emb = new EmblemView(this);
        emb.setTag("emb");
        c.addView(emb, new LinearLayout.LayoutParams(dp(110), dp(110)));

        TextView w1 = Gg.label(this, "GHOST GOLD");
        w1.setTag("w1");
        w1.setTextSize(30);
        w1.setLetterSpacing(0.42f);
        w1.setTextColor(Gg.GOLD);
        w1.setShadowLayer(dp(16), 0, 0, 0x73D4AF37);
        w1.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams w1p = new LinearLayout.LayoutParams(-2, -2);
        w1p.topMargin = dp(30);
        c.addView(w1, w1p);

        View line = new View(this);
        line.setTag("line");
        line.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x00D4AF37, Gg.GOLD, Gg.IGNITION, Gg.GOLD, 0x00D4AF37}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(520), dp(1));
        lp.setMargins(0, dp(22), 0, dp(22));
        c.addView(line, lp);

        TextView w2 = Gg.label(this, "System online");
        w2.setTag("w2");
        w2.setTextSize(11);
        w2.setTextColor(Gg.TEXT2);
        c.addView(w2);
        TextView w3 = Gg.label(this, "Vehicle ready");
        w3.setTag("w3");
        w3.setTextSize(11);
        w3.setTextColor(Gg.IGNITION);
        w3.setPadding(0, dp(8), 0, 0);
        c.addView(w3);

        b.addView(c, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return b;
    }

    private void playBoot() {
        if (showroom) toggleShowroom();
        View b = bootLayer;
        EmblemView emb = b.findViewWithTag("emb");
        View w1 = b.findViewWithTag("w1"), line = b.findViewWithTag("line");
        View w2 = b.findViewWithTag("w2"), w3 = b.findViewWithTag("w3");
        DecelerateInterpolator ease = new DecelerateInterpolator(2f);

        lastBootAt = SystemClock.elapsedRealtime();
        handler.removeCallbacksAndMessages("boot");
        b.animate().cancel();
        b.setAlpha(1f);
        b.setVisibility(View.VISIBLE);
        emb.setAlpha(0f);
        emb.setProgress(0f);
        w1.setAlpha(0f);
        w1.setScaleX(1.08f);
        w1.setScaleY(1.08f);
        line.setScaleX(0f);
        w2.setAlpha(0f);
        w2.setTranslationY(dp(8));
        w3.setAlpha(0f);
        w3.setTranslationY(dp(8));
        stage.setAlpha(0f);

        emb.animate().alpha(1f).setStartDelay(200).setDuration(900).setInterpolator(ease).start();
        ValueAnimator ring = ValueAnimator.ofFloat(0f, 1f).setDuration(1200);
        ring.setStartDelay(200);
        ring.setInterpolator(ease);
        ring.addUpdateListener(a -> emb.setProgress((float) a.getAnimatedValue()));
        ring.start();
        w1.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(1100).setDuration(1100).setInterpolator(ease).start();
        line.animate().scaleX(1f).setStartDelay(1500).setDuration(1000).setInterpolator(ease).start();
        w2.animate().alpha(1f).translationY(0).setStartDelay(2400).setDuration(500).start();
        w3.animate().alpha(1f).translationY(0).setStartDelay(3000).setDuration(500).start();

        handler.postAtTime(() -> {
            b.animate().alpha(0f).setStartDelay(0).setDuration(900).withEndAction(() -> b.setVisibility(View.GONE)).start();
            // The truck emerges from the dark, then the data fades in around it.
            stage.setAlpha(1f);
            truck.setAlpha(0f);
            truck.setTranslationY(dp(24));
            truck.setScaleX(0.96f);
            truck.setScaleY(0.96f);
            truck.animate().alpha(1f).translationY(0).scaleX(1f).scaleY(1f).setStartDelay(300)
                    .setDuration(2000).setInterpolator(ease).start();
            for (View v : chrome) {
                v.setAlpha(0f);
                v.animate().alpha(showroom ? 0f : 1f).setStartDelay(1800).setDuration(1000).start();
            }
        }, "boot", SystemClock.uptimeMillis() + 4300);
    }

    private final BroadcastReceiver bootReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (SystemClock.elapsedRealtime() - lastBootAt > 20_000) playBoot();
        }
    };

    // ======================= Car lights (ILL wire) =======================

    private final BroadcastReceiver illReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            truck.setLights(i.getByteExtra("extra_ill_state", (byte) 0) != 0, true);
            handler.postDelayed(() -> { if (Ui.isDark(MainActivity.this) != createdDark) recreate(); }, 1500);
        }
    };

    private boolean carLightsOn() {
        int state = android.provider.Settings.System.getInt(getContentResolver(), "mcu_no_source_device_state", 0);
        return (state & 2) != 0;
    }

    // ======================= Periodic refresh =======================

    private final Runnable periodic = new Runnable() {
        public void run() {
            if (Ui.isDark(MainActivity.this) != createdDark) { recreate(); return; }
            updateGreeting();
            updateRestriction();
            updateStatus();
            fetchWeather(false);
            handler.postDelayed(this, 60 * 1000);
        }
    };

    private final Runnable fast = new Runnable() {
        public void run() {
            updateSpeed();
            long now = SystemClock.elapsedRealtime();
            boolean parked = Odometer.speedNow() <= 0;
            if (!showroom && parked && now - lastTouch > AUTO_SHOWROOM_MS && bootLayer.getVisibility() != View.VISIBLE) {
                toggleShowroom(); // parked and untouched for a while: show the truck off
            } else if (showroom && !parked) {
                toggleShowroom(); // started driving: back to the data
            }
            handler.postDelayed(this, 1000);
        }
    };

    // ======================= Shortcuts =======================

    /** Opens the chosen map app (Waze or Google Maps); long-press the map icon to change it. */
    private void openMaps() {
        String pkg = Profile.prefs(this).getString("maps_pkg", "");
        if (pkg.isEmpty() || !isInstalled(pkg)) pkg = isInstalled(WAZE) ? WAZE : GMAPS;
        Intent maps = getPackageManager().getLaunchIntentForPackage(pkg);
        if (maps == null || !safeStart(maps)) {
            safeStart(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=")));
        }
    }

    private void chooseMaps() {
        final List<String> pkgs = new ArrayList<>();
        final List<String> names = new ArrayList<>();
        if (isInstalled(WAZE)) { pkgs.add(WAZE); names.add("Waze"); }
        if (isInstalled(GMAPS)) { pkgs.add(GMAPS); names.add("Google Maps"); }
        if (pkgs.isEmpty()) { toast("No hay Waze ni Google Maps instalados"); return; }
        String cur = Profile.prefs(this).getString("maps_pkg", "");
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle("App de mapas")
                .setSingleChoiceItems(names.toArray(new String[0]), pkgs.indexOf(cur), (d, which) -> {
                    Profile.prefs(this).edit().putString("maps_pkg", pkgs.get(which)).apply();
                    d.dismiss();
                    openMaps();
                })
                .show();
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void openRadio() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, RadioActivity.class));
        else toast("Radio no disponible");
    }

    private void openBluetooth() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, BtActivity.class));
        else safeStart(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
    }

    private void openEqualizer() {
        if (Profile.isNwd(this)) startActivity(new Intent(this, EqActivity.class));
        else safeStart(new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL));
    }

    private void openSystemSettings() {
        if (Profile.isNwd(this)) Ui.launch(this, SETTINGS);
        else safeStart(new Intent(android.provider.Settings.ACTION_SETTINGS));
    }

    private boolean safeStart(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void toast(String s) {
        android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_SHORT).show();
    }

    // ======================= Lifecycle =======================

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.isDark(this) != createdDark || !profileStamp().equals(createdProfile)) {
            recreate();
            return;
        }
        updateGreeting();
        updateRestriction();
        updateStatus();
        updateSpeed();
        showWeather(Weather.cached(this));
        registerReceiver(illReceiver, new IntentFilter("com.nwd.ACTION_ILL_STATE_CHANGE"));
        registerReceiver(bootReceiver, new IntentFilter(ACTION_BOOT));
        truck.setLights(truck.hasLights() && carLightsOn(), false);
        // The car or screen came on since the last intro: play it (the broadcast may arrive before we are visible).
        if (GreeterService.wokeAt > lastBootAt && SystemClock.elapsedRealtime() - GreeterService.wokeAt < 60_000) playBoot();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1);
        }
        handler.post(periodic);
        handler.post(fast);
        if (Profile.isNwd(this)) handler.post(radioPoll);
        nowPlaying.addListener(this);
        onNowPlayingChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(illReceiver);
        unregisterReceiver(bootReceiver);
        handler.removeCallbacks(periodic);
        handler.removeCallbacks(fast);
        handler.removeCallbacks(radioPoll);
        nowPlaying.removeListener(this);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            fetchWeather(true);
            stopService(new Intent(this, Odometer.class)); // restart so it starts listening to GPS
            startService(new Intent(this, Odometer.class));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        radio.unbind(this);
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }

    static void pressable(View v) {
        Ui.pressable(v, 0.94f);
    }

    @Override
    public void onBackPressed() {
        if (showroom) toggleShowroom();
    }
}
