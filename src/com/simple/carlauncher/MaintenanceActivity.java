package com.simple.carlauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** GHOST GOLD maintenance log: every service with what is left and a "done today" button. */
public class MaintenanceActivity extends Activity {

    private LinearLayout list;
    private TextView kmLine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(28), dp(18), dp(28), dp(12));
        root.setBackground(Ui.background(this));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(Ui.iconButton(this, R.drawable.ic_back, 48, false, this::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(20), 0, 0, 0);
        TextView l = Gg.label(this, "Ghost Gold · Vehículo");
        l.setTextColor(Gg.GOLD);
        titles.addView(l);
        TextView t = Gg.value(this, "Mantenimiento", 32, Gg.TEXT);
        t.setPadding(0, dp(4), 0, 0);
        titles.addView(t);
        head.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));
        kmLine = Gg.body(this, "", 16, Gg.TEXT2);
        kmLine.setPadding(dp(16), dp(10), dp(16), dp(10));
        kmLine.setBackground(Gg.pill(this, true));
        Ui.pressable(kmLine, 0.95f);
        kmLine.setOnClickListener(v -> startActivity(new android.content.Intent(this, OilActivity.class)));
        head.addView(kmLine);
        root.addView(head);

        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, 0, 1f);
        slp.topMargin = dp(16);
        root.addView(sv, slp);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        kmLine.setText(Odometer.isConfigured(this)
                ? "Tablero ≈ " + OilActivity.fmt(Odometer.currentKm(this)) + " km"
                : "Configurar kilometraje");
        list.removeAllViews();
        for (Mantenimiento.Item i : Mantenimiento.ITEMS) list.addView(row(i));
    }

    private View row(Mantenimiento.Item i) {
        int level = Mantenimiento.level(this, i);
        int color = level == 2 ? Gg.AMBER : level == 1 ? Gg.CHAMPAGNE : Gg.GOLD;

        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(4), dp(12), dp(4), dp(12));
        r.setMinimumHeight(dp(76));

        View dot = new View(this);
        dot.setBackground(Ui.circle(color));
        r.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(18), 0, dp(12), 0);
        txt.addView(Gg.body(this, i.name, 19, Gg.TEXT2));
        TextView st = Gg.body(this, Mantenimiento.statusLine(this, i), 14, level > 0 ? Gg.AMBER : Gg.LABEL);
        st.setSingleLine(false);
        st.setMaxLines(2);
        st.setPadding(0, dp(3), 0, 0);
        txt.addView(st);
        r.addView(txt, new LinearLayout.LayoutParams(0, -2, 1f));

        String doneText = Mantenimiento.MARCHAMO.equals(i.id) ? "Pagado" : "Hecho hoy";
        TextView done = Gg.label(this, doneText);
        done.setTextColor(Gg.GOLD);
        done.setGravity(Gravity.CENTER);
        done.setPadding(dp(18), dp(12), dp(18), dp(12));
        done.setBackground(Gg.pill(this, false));
        Ui.pressable(done, 0.92f);
        done.setOnClickListener(v -> {
            Mantenimiento.markDone(this, i);
            Toast.makeText(this, "Registrado: " + i.name, Toast.LENGTH_SHORT).show();
            refresh();
        });
        r.addView(done);

        Ui.pressable(r, 0.98f);
        r.setOnClickListener(v -> edit(i));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(r);
        View line = new View(this);
        line.setBackgroundColor(0xFF161616);
        wrap.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
        return wrap;
    }

    private void edit(Mantenimiento.Item i) {
        if (Mantenimiento.OIL.equals(i.id)) {
            startActivity(new android.content.Intent(this, OilActivity.class));
            return;
        }
        if (!i.byKm) {
            int y = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
            String[] years = Mantenimiento.MARCHAMO.equals(i.id)
                    ? new String[]{"Pagado el de " + y, "Pagado el de " + (y + 1)}
                    : new String[]{"La pasé en " + (y - 1), "La pasé en " + y};
            int[] values = Mantenimiento.MARCHAMO.equals(i.id) ? new int[]{y, y + 1} : new int[]{y - 1, y};
            new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                    .setTitle(i.name)
                    .setItems(years, (d, w) -> { Mantenimiento.setDoneYear(this, i, values[w]); refresh(); })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(i.name)
                .setItems(new String[]{"Último servicio fue a los… km", "Hacerlo cada… km"}, (d, w) -> {
                    if (w == 0) askKm("Kilometraje del último servicio", Mantenimiento.lastKm(this, i),
                            km -> Mantenimiento.setLastKm(this, i, km));
                    else askKm("Cada cuántos km", Mantenimiento.intervalKm(this, i),
                            km -> Mantenimiento.setIntervalKm(this, i, Math.max(500, km)));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private interface KmSink { void set(int km); }

    private void askKm(String title, int value, KmSink sink) {
        EditText in = new EditText(this);
        in.setInputType(InputType.TYPE_CLASS_NUMBER);
        in.setText(String.valueOf(value));
        in.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this, AlertDialog.THEME_DEVICE_DEFAULT_DARK)
                .setTitle(title)
                .setView(in)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        sink.set(Integer.parseInt(in.getText().toString().trim()));
                        refresh();
                    } catch (NumberFormatException ignored) { }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
