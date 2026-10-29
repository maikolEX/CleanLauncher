package com.simple.carlauncher;

import android.content.Context;

import java.util.Calendar;

/**
 * Restricción vehicular de San José (Costa Rica): inside the Circunvalación, Monday to Friday
 * 6:00-19:00, by the last digit of the plate. Mon 1-2, Tue 3-4, Wed 5-6, Thu 7-8, Fri 9-0.
 * Holidays and MOPT suspensions are not known to the app.
 */
final class Restriccion {

    static final int START_HOUR = 6, END_HOUR = 19;
    private static final String[] DAY_NAMES = {"", "domingo", "lunes", "martes", "miércoles", "jueves", "viernes", "sábado"};

    private Restriccion() { }

    /** Last digit of the plate (Maikol's Rich 6 ends in 8). */
    static int plateDigit(Context c) {
        return Profile.prefs(c).getInt("plate_digit", 8);
    }

    static void setPlateDigit(Context c, int d) {
        Profile.prefs(c).edit().putInt("plate_digit", d).apply();
    }

    /** Calendar.DAY_OF_WEEK on which this plate cannot drive. */
    static int restrictedDay(int digit) {
        switch (digit) {
            case 1: case 2: return Calendar.MONDAY;
            case 3: case 4: return Calendar.TUESDAY;
            case 5: case 6: return Calendar.WEDNESDAY;
            case 7: case 8: return Calendar.THURSDAY;
            default: return Calendar.FRIDAY; // 9 and 0
        }
    }

    /** Tomorrow is a restricted day for this plate. */
    static boolean restrictedTomorrow(Context c) {
        Calendar t = Calendar.getInstance();
        t.add(Calendar.DAY_OF_MONTH, 1);
        return t.get(Calendar.DAY_OF_WEEK) == restrictedDay(plateDigit(c));
    }

    static boolean restrictedToday(Context c) {
        return Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == restrictedDay(plateDigit(c));
    }

    /** Restricted day and inside the 6:00-19:00 window. */
    static boolean activeNow(Context c) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return restrictedToday(c) && h >= START_HOUR && h < END_HOUR;
    }

    static String dayName(Context c) {
        return DAY_NAMES[restrictedDay(plateDigit(c))];
    }

    /** One line for the home screen. */
    static String status(Context c) {
        if (!restrictedToday(c)) return "Hoy circulas · restricción los " + dayName(c);
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h < START_HOUR) return "Hoy no circulas en San José · desde las 6:00";
        if (h < END_HOUR) return "Hoy no circulas en San José · hasta las 19:00";
        return "Restricción terminada · ya puedes circular";
    }
}
