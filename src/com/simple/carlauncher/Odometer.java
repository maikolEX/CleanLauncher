package com.simple.carlauncher;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * GPS odometer for the oil-change reminder (the car's CAN box does not report mileage).
 *
 * The user sets the dashboard reading once; from then on distance between good GPS fixes
 * is added. Fixes while parked or with poor accuracy are ignored so drift does not count.
 */
public class Odometer extends Service implements LocationListener {

    private static final String TAG = "Odometer";
    static final int DEFAULT_INTERVAL_KM = 5000;
    static final int WARN_KM = 500;

    private LocationManager lm;
    private Location last;
    private float pendingMeters;

    @Override
    public void onCreate() {
        super.onCreate();
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000, 10, this);
            } catch (Exception e) {
                Log.w(TAG, "no GPS", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /** Latest GPS speed for the home screen (km/h) and when it was measured. */
    static volatile float speedKmh;
    static volatile long speedAt;

    /** Current speed in km/h, or -1 when there is no recent GPS fix. */
    static int speedNow() {
        return android.os.SystemClock.elapsedRealtime() - speedAt > 6000 ? -1 : Math.round(speedKmh);
    }

    @Override
    public void onLocationChanged(Location l) {
        // This head unit's GPS reports nonsense speeds while parked (e.g. 383 km/h), so the speed
        // is taken from the distance between two good fixes and the reported one only when it agrees.
        if (!l.hasAccuracy() || l.getAccuracy() > 25) {
            return;
        }
        float kmh = -1;
        if (last != null) {
            float d = last.distanceTo(l);
            float secs = (l.getTime() - last.getTime()) / 1000f;
            if (secs >= 0.5f && secs <= 10f) kmh = d / secs * 3.6f;
            boolean moving = kmh > 4 && kmh < 200;
            if (moving) {
                pendingMeters += d; // count only real driving; drop parked drift and jumps
                tripMove(this, d, kmh, secs);
            }
        }
        if (kmh >= 0 && kmh < 200) {
            float reported = l.hasSpeed() ? l.getSpeed() * 3.6f : -1;
            float v = reported >= 0 && reported < 200 && Math.abs(reported - kmh) < 25 ? reported : kmh;
            speedKmh = v < 4 ? 0 : v; // parked GPS jitter reads as 0
            speedAt = android.os.SystemClock.elapsedRealtime();
        }
        last = l;
        if (pendingMeters >= 100) { // persist every 100 m
            SharedPreferences p = prefs(this);
            p.edit().putFloat("gps_km", p.getFloat("gps_km", 0) + pendingMeters / 1000f).apply();
            pendingMeters = 0;
        }
    }

    public void onStatusChanged(String p, int s, Bundle b) { }
    public void onProviderEnabled(String p) { }
    public void onProviderDisabled(String p) { }

    @Override
    public void onDestroy() {
        super.onDestroy();
        lm.removeUpdates(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ---------- Trip (for the summary when the car is switched off) ----------

    /** A new trip starts when the car moves again after this long standing still. */
    private static final long NEW_TRIP_AFTER_MS = 20 * 60 * 1000;

    static SharedPreferences tripPrefs(Context c) {
        return c.getSharedPreferences("trip", Context.MODE_PRIVATE);
    }

    static void tripMove(Context c, float meters, float kmh, float secs) {
        SharedPreferences p = tripPrefs(c);
        long now = System.currentTimeMillis();
        SharedPreferences.Editor e = p.edit();
        if (now - p.getLong("last_move", 0) > NEW_TRIP_AFTER_MS || p.getBoolean("ended", false)) {
            e.putLong("start", now).putFloat("meters", 0).putFloat("max", 0).putFloat("moving_s", 0)
                    .putBoolean("ended", false).putBoolean("shown", false);
            e.apply();
            e = p.edit();
        }
        e.putFloat("meters", p.getFloat("meters", 0) + meters)
                .putFloat("max", Math.max(p.getFloat("max", 0), kmh))
                .putFloat("moving_s", p.getFloat("moving_s", 0) + secs)
                .putLong("last_move", now)
                .apply();
    }

    /** The car was switched off: the current trip is over. */
    static void endTrip(Context c) {
        tripPrefs(c).edit().putBoolean("ended", true).apply();
    }

    // ---------- Shared state ----------

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("odometer", Context.MODE_PRIVATE);
    }

    static boolean isConfigured(Context c) {
        return prefs(c).contains("base_km");
    }

    /** Estimated dashboard reading: the value the user entered plus GPS distance since. */
    static int currentKm(Context c) {
        SharedPreferences p = prefs(c);
        return Math.round(p.getInt("base_km", 0) + p.getFloat("gps_km", 0));
    }

    static void setCurrentKm(Context c, int km) {
        prefs(c).edit().putInt("base_km", km).putFloat("gps_km", 0).apply();
    }

    static int lastChangeKm(Context c) {
        return prefs(c).getInt("last_change_km", currentKm(c));
    }

    static void setLastChangeKm(Context c, int km) {
        prefs(c).edit().putInt("last_change_km", km).apply();
    }

    static int intervalKm(Context c) {
        return prefs(c).getInt("interval_km", DEFAULT_INTERVAL_KM);
    }

    static void setIntervalKm(Context c, int km) {
        prefs(c).edit().putInt("interval_km", km).apply();
    }

    /** Km left until the next oil change (negative when overdue). */
    static int remainingKm(Context c) {
        return lastChangeKm(c) + intervalKm(c) - currentKm(c);
    }
}
