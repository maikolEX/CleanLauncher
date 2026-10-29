package com.simple.carlauncher;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Everything that needs the driver's attention, most urgent first (home screen and floating panel). */
final class Alertas {

    static final class Alerta {
        final String title, detail;
        final int level;          // 1 = soon, 2 = now / overdue
        final boolean maintenance;
        Alerta(String title, String detail, int level, boolean maintenance) {
            this.title = title;
            this.detail = detail;
            this.level = level;
            this.maintenance = maintenance;
        }
    }

    private Alertas() { }

    static List<Alerta> current(Context c) {
        List<Alerta> out = new ArrayList<>();
        if (Restriccion.activeNow(c)) {
            out.add(new Alerta("Restricción vehicular", "Hoy no circulas en San José hasta las 19:00", 2, false));
        }
        for (Mantenimiento.Item i : Mantenimiento.attention(c)) {
            out.add(new Alerta(i.name, Mantenimiento.statusLine(c, i), Mantenimiento.level(c, i), true));
        }
        return out;
    }
}
