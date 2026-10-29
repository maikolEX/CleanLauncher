package com.simple.carlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Maintenance log: services by kilometres (brakes, tyre rotation, filters) and the yearly
 * Costa Rica paperwork (marchamo by 31 December, RTV in the month of the plate's last digit).
 * The oil change keeps living in {@link Odometer} / {@link OilActivity}.
 */
final class Mantenimiento {

    static final int WARN_KM = 1000, WARN_DAYS = 30;
    static final String OIL = "oil", MARCHAMO = "marchamo", RTV = "rtv";

    static final class Item {
        final String id, name;
        final boolean byKm;
        final int defaultInterval; // km, or 12 months for the yearly items
        Item(String id, String name, boolean byKm, int interval) {
            this.id = id;
            this.name = name;
            this.byKm = byKm;
            this.defaultInterval = interval;
        }
    }

    static final Item[] ITEMS = {
            new Item(OIL, "Cambio de aceite", true, 5000),
            new Item("brakes", "Revisión de frenos", true, 20000),
            new Item("rotation", "Rotación de llantas", true, 10000),
            new Item("air", "Filtro de aire", true, 20000),
            new Item("cabin", "Filtro de cabina (A/C)", true, 15000),
            new Item("fuel", "Filtro de combustible", true, 40000),
            new Item(MARCHAMO, "Marchamo", false, 12),
            new Item(RTV, "Revisión técnica (RTV)", false, 12),
    };

    private Mantenimiento() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("mantenimiento", Context.MODE_PRIVATE);
    }

    static Item item(String id) {
        for (Item i : ITEMS) if (i.id.equals(id)) return i;
        return null;
    }

    // ---------- Kilometre items ----------

    static int intervalKm(Context c, Item i) {
        if (OIL.equals(i.id)) return Odometer.intervalKm(c);
        return prefs(c).getInt(i.id + "_interval", i.defaultInterval);
    }

    static void setIntervalKm(Context c, Item i, int km) {
        if (OIL.equals(i.id)) Odometer.setIntervalKm(c, km);
        else prefs(c).edit().putInt(i.id + "_interval", km).apply();
    }

    /** Dashboard km at the last service; defaults to the km when the log was first opened. */
    static int lastKm(Context c, Item i) {
        if (OIL.equals(i.id)) return Odometer.lastChangeKm(c);
        SharedPreferences p = prefs(c);
        if (!p.contains(i.id + "_last")) p.edit().putInt(i.id + "_last", Odometer.currentKm(c)).apply();
        return p.getInt(i.id + "_last", 0);
    }

    static void setLastKm(Context c, Item i, int km) {
        if (OIL.equals(i.id)) Odometer.setLastChangeKm(c, km);
        else prefs(c).edit().putInt(i.id + "_last", km).apply();
    }

    static int remainingKm(Context c, Item i) {
        return lastKm(c, i) + intervalKm(c, i) - Odometer.currentKm(c);
    }

    // ---------- Yearly items ----------

    /** Marchamo: year the paid marchamo covers. RTV: year it was passed. 0 = never recorded. */
    static int doneYear(Context c, Item i) {
        return prefs(c).getInt(i.id + "_year", 0);
    }

    static void setDoneYear(Context c, Item i, int year) {
        prefs(c).edit().putInt(i.id + "_year", year).apply();
    }

    /**
     * RTV month by the last digit of the plate: 1 = January ... 9 = September, 0 = October.
     * Check the exact calendar with Dekra; the app only reminds.
     */
    static int rtvMonth(Context c) {
        int d = Restriccion.plateDigit(c);
        return d == 0 ? Calendar.OCTOBER : Calendar.JANUARY + d - 1;
    }

    /** Due date of the next marchamo / RTV not yet recorded as done. */
    static Calendar dueDate(Context c, Item i) {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        Calendar due = Calendar.getInstance();
        due.clear();
        if (MARCHAMO.equals(i.id)) {
            // doneYear = the year the paid marchamo covers; the next one is due 31 December of it.
            // Not recorded yet: assume this year's is paid.
            int covered = doneYear(c, i) == 0 ? year : doneYear(c, i);
            due.set(covered, Calendar.DECEMBER, 31);
        } else {
            int y = doneYear(c, i) >= year ? doneYear(c, i) + 1 : year;
            due.set(y, rtvMonth(c), 1);
            due.add(Calendar.MONTH, 1);
            due.add(Calendar.DAY_OF_MONTH, -1); // last day of the RTV month
        }
        return due;
    }

    /** "Done today": marchamo paid (covers next year from November on), RTV passed this year. */
    static void markDone(Context c, Item i) {
        if (i.byKm) {
            setLastKm(c, i, Odometer.currentKm(c));
            return;
        }
        Calendar now = Calendar.getInstance();
        int y = now.get(Calendar.YEAR);
        if (MARCHAMO.equals(i.id)) setDoneYear(c, i, now.get(Calendar.MONTH) >= Calendar.NOVEMBER ? y + 1 : y);
        else setDoneYear(c, i, y);
    }

    static int daysLeft(Context c, Item i) {
        Calendar now = Calendar.getInstance();
        long ms = dueDate(c, i).getTimeInMillis() - now.getTimeInMillis();
        return (int) Math.floor(ms / 86_400_000.0) + 1;
    }

    // ---------- Status ----------

    static boolean configured(Context c) {
        return Odometer.isConfigured(c);
    }

    /** 0 = fine, 1 = soon, 2 = overdue. */
    static int level(Context c, Item i) {
        if (i.byKm) {
            if (!configured(c)) return 0;
            int r = remainingKm(c, i);
            return r < 0 ? 2 : r <= (OIL.equals(i.id) ? Odometer.WARN_KM : WARN_KM) ? 1 : 0;
        }
        if (RTV.equals(i.id) && doneYear(c, i) == 0) return 0; // unknown until recorded once
        if (MARCHAMO.equals(i.id)) {
            Calendar now = Calendar.getInstance();
            int covered = doneYear(c, i) == 0 ? now.get(Calendar.YEAR) : doneYear(c, i);
            // Collection of next year's marchamo opens in November.
            if (now.get(Calendar.MONTH) >= Calendar.NOVEMBER && covered <= now.get(Calendar.YEAR) && daysLeft(c, i) >= 0) return 1;
        }
        int d = daysLeft(c, i);
        return d < 0 ? 2 : d <= WARN_DAYS ? 1 : 0;
    }

    static String statusLine(Context c, Item i) {
        if (i.byKm) {
            if (!configured(c)) return "Configura el kilometraje en Aceite";
            int r = remainingKm(c, i);
            return r < 0 ? "Vencido hace " + OilActivity.fmt(-r) + " km"
                    : "Faltan " + OilActivity.fmt(r) + " km · cada " + OilActivity.fmt(intervalKm(c, i)) + " km";
        }
        if (RTV.equals(i.id) && doneYear(c, i) == 0) {
            return "Tu mes: " + MONTHS[rtvMonth(c)] + " (placa termina en " + Restriccion.plateDigit(c)
                    + ") · marca cuándo la pasaste · confirma con Dekra";
        }
        int d = daysLeft(c, i);
        Calendar due = dueDate(c, i);
        String date = due.get(Calendar.DAY_OF_MONTH) + " de " + MONTHS[due.get(Calendar.MONTH)] + " " + due.get(Calendar.YEAR);
        String when = d < 0 ? "Vencido desde el " + date : d == 0 ? "Vence hoy" : d == 1 ? "Vence mañana" : "Faltan " + d + " días · " + date;
        if (RTV.equals(i.id)) when += " · confirma tu mes con Dekra";
        return when;
    }

    static final String[] MONTHS = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre"};

    /** Items that need attention, most urgent first: for the home-screen alerts. */
    static List<Item> attention(Context c) {
        List<Item> out = new ArrayList<>();
        for (int lvl = 2; lvl >= 1; lvl--) {
            for (Item i : ITEMS) if (level(c, i) == lvl) out.add(i);
        }
        return out;
    }
}
