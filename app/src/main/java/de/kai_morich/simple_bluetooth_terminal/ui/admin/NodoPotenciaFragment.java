package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.data.Series5M;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DarkChartView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;

/**
 * A4 · Nodo · Potencia día: la curva del día en 5 min / 15 min / 1 h contra el día anterior, máximo / promedio / mínimo y el
 * detalle por hora con las caídas marcadas. Si el nodo no tiene serie de 5 minutos se usa la hora (get SmartSunLitePowerDay).
 */
public class NodoPotenciaFragment extends Fragment {

    private static final Locale ES = new Locale("es", "MX");
    private static final int[] PER = {1, 3, 12}; // slots de 5 min por punto: 5 min, 15 min, 1 h

    private ApiService api;
    private int idNode, idCustomer, idCircuit;
    private String name;

    private int offset, interval = 2;    // 0 = hoy; intervalo: 0 = 5 min, 1 = 15 min, 2 = 1 h
    private boolean hourlyOnly;           // el nodo no entrega 5 minutales
    private double[] raw, rawPrev;        // 288 valores (o 24 si hourlyOnly), kW
    private int loaded;                   // respuestas recibidas (hoy + ayer)
    private int gen;
    private boolean shownOnce;

    private TextView tvDate, btnNext, tvNote, tvMax, tvMaxSub, tvAvg, tvAvgSub, tvMin, tvMinSub;
    private final TextView[] seg = new TextView[3];
    private DarkChartView chart;
    private LinearLayout hoursList;
    private View content;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        Bundle a = requireArguments();
        idNode = a.getInt("idMeasure");
        idCustomer = a.getInt("idCustomer");
        idCircuit = a.getInt("idCircuit");
        name = a.getString("nodeName");
        api = ApiClient.getApiService();

        View v = inflater.inflate(R.layout.fragment_nodo_potencia, container, false);
        content = v.findViewById(R.id.content);
        tvDate = v.findViewById(R.id.tvDate);
        btnNext = v.findViewById(R.id.btnNext);
        tvNote = v.findViewById(R.id.tvNote);
        tvMax = v.findViewById(R.id.tvMax);
        tvMaxSub = v.findViewById(R.id.tvMaxSub);
        tvAvg = v.findViewById(R.id.tvAvg);
        tvAvgSub = v.findViewById(R.id.tvAvgSub);
        tvMin = v.findViewById(R.id.tvMin);
        tvMinSub = v.findViewById(R.id.tvMinSub);
        chart = v.findViewById(R.id.chart);
        hoursList = v.findViewById(R.id.hoursList);
        chart.setLight(true);
        chart.setYLabels(true);
        chart.setNames("Hoy", "", "Ayer");
        String[] names = {"5 min", "15 min", "1 h"};
        int[] ids = {R.id.seg0, R.id.seg1, R.id.seg2};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            seg[i] = v.findViewById(ids[i]);
            seg[i].setText(names[i]);
            seg[i].setOnClickListener(x -> { if (!hourlyOnly) { interval = k; render(); } });
        }
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> { offset++; reload(); });
        btnNext.setOnClickListener(x -> { if (offset > 0) { offset--; reload(); } });
        reload();
        return v;
    }

    // ───────────────────────── datos ─────────────────────────

    private String date(int daysBack) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -daysBack);
        return EnergyLoader.ymd(c);
    }

    private void reload() {
        if (idCustomer == 0 || idNode == 0) return;
        gen++;
        loaded = 0;
        raw = rawPrev = null;
        hourlyOnly = false;
        paintDate();
        final int g = gen;
        fetch(g, date(offset), true);
        fetch(g, date(offset + 1), false);
    }

    /** Pide un día; si el nodo no tiene serie de 5 minutos cae a la potencia por hora. */
    private void fetch(final int g, final String day, final boolean main) {
        Series5M.powerKw(api, idCustomer, idNode, day, (kw, err) -> {
            if (!isAdded() || g != gen) return;
            if (kw != null) { store(g, main, kw); return; }
            hourlyOnly = true;
            NodeTrafficLight n = new NodeTrafficLight();
            n.setFields(idNode, name, null, idCircuit, 0, null, null, "Green", -1);
            List<NodeTrafficLight> one = new ArrayList<>();
            one.add(n);
            EnergyLoader.powerDay(api, idCustomer, one, day, hourly -> {
                if (!isAdded() || g != gen) return;
                store(g, main, hourly != null ? hourly : nan(24));
            });
        });
    }

    private void store(int g, boolean main, double[] kw) {
        if (main) raw = kw; else rawPrev = kw;
        if (++loaded == 2) render();
    }

    private static double[] nan(int n) {
        double[] a = new double[n];
        Arrays.fill(a, Double.NaN);
        return a;
    }

    // ───────────────────────── pintar ─────────────────────────

    private void paintDate() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -offset);
        String d = new SimpleDateFormat("d MMM", ES).format(c.getTime()).replace(".", "");
        tvDate.setText(offset == 0 ? "Hoy · " + d : new SimpleDateFormat("EEE", ES).format(c.getTime()).replace(".", "") + " · " + d);
        btnNext.setAlpha(offset > 0 ? 1f : 0.35f);
    }

    /** Serie por hora (24) a partir de lo que haya: 288 valores de 5 min o directamente 24. */
    private static double[] hourly(double[] kw) {
        return kw.length == 24 ? kw : Series5M.bucket(kw, 12);
    }

    private void render() {
        if (getView() == null || raw == null) return;
        if (hourlyOnly) interval = 2;
        for (int i = 0; i < 3; i++) {
            boolean on = i == interval;
            seg[i].setBackgroundResource(on ? R.drawable.am_seg_on : 0);
            seg[i].setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.am_ink : R.color.am_t666));
            seg[i].setAlpha(hourlyOnly && i < 2 ? 0.4f : 1f);
        }
        tvNote.setVisibility(hourlyOnly ? View.VISIBLE : View.GONE);
        tvNote.setText("Este nodo no entrega datos cada 5 min: se muestra la potencia por hora.");

        Calendar now = Calendar.getInstance();
        boolean today = offset == 0;
        int nowHour = now.get(Calendar.HOUR_OF_DAY);

        // Curva con la resolución elegida
        int per = hourlyOnly ? 1 : PER[interval];
        int slots = hourlyOnly ? 24 : Series5M.SLOTS / per;
        double[] main = hourlyOnly ? hourly(raw) : Series5M.bucket(raw, per);
        double[] prev = rawPrev == null ? null : hourlyOnly ? hourly(rawPrev) : Series5M.bucket(rawPrev, per);
        int nowIdx = today ? Math.min(slots - 1, (now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)) * slots / 1440) : -1;
        int len = nowIdx >= 0 ? nowIdx + 1 : slots;
        double[] shown = Arrays.copyOf(main, len);

        double[] h = hourly(raw), hp = rawPrev != null ? hourly(rawPrev) : null;
        int lastH = today ? nowHour : 23;
        boolean[] drops = drops(h, lastH);
        boolean[] marks = new boolean[len];
        for (int hr = 0; hr <= lastH; hr++) {
            int idx = hr * slots / 24 + slots / 48;
            if (drops[hr] && idx < len) marks[idx] = true;
        }
        double peak = 0;
        for (double v : shown) if (!Double.isNaN(v)) peak = Math.max(peak, v);
        chart.setSlots(slots);
        chart.set(shown, null, prev, nowIdx, peak > 0 ? Fmt.n1(peak) : null);
        chart.setMarks(marks);

        // Máx / Prom / Mín por hora
        double max = -1, min = Double.MAX_VALUE, sum = 0, sumPrev = 0;
        int maxAt = 0, minAt = 0, n = 0;
        for (int hr = 0; hr <= lastH; hr++) {
            double v = h[hr];
            if (Double.isNaN(v)) continue;
            sum += v;
            n++;
            if (hp != null && !Double.isNaN(hp[hr])) sumPrev += hp[hr];
            if (v > max) { max = v; maxAt = hr; }
            if (v > 0 && v < min) { min = v; minAt = hr; }
        }
        tvMax.setText(max >= 0 ? Fmt.n1(max) : "--");
        tvMaxSub.setText(max >= 0 ? String.format(Locale.US, "kW · %02d:00", maxAt) : "kW");
        tvAvg.setText(n > 0 ? Fmt.n1(sum / n) : "--");
        tvAvgSub.setText(n > 0 && hp != null ? "kW · ayer " + Fmt.n1(sumPrev / n) : "kW");
        tvMin.setText(min != Double.MAX_VALUE ? Fmt.n1(min) : "--");
        tvMinSub.setText(min != Double.MAX_VALUE ? String.format(Locale.US, "kW · %02d:00", minAt) : "kW");

        // Detalle por hora
        hoursList.removeAllViews();
        for (int hr = 0; hr <= lastH; hr++) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_hour_row, hoursList, false);
            double v = h[hr];
            boolean isNow = today && hr == nowHour;
            boolean isMax = hr == maxAt && max > 0;
            ((TextView) row.findViewById(R.id.tvHour)).setText(String.format(Locale.US, "%02d:00", hr));
            TextView tv = row.findViewById(R.id.tvVal);
            String txt = Double.isNaN(v) ? "—" : String.format(Locale.US, "%.2f", v);
            if (isMax) txt += " ▲"; else if (drops[hr]) txt += " ▼"; else if (isNow) txt += " …";
            tv.setText(txt);
            int color = drops[hr] ? 0xFFD32F2F : isMax ? 0xFF161616 : isNow ? 0xFF8FB8FF : 0xFF1E7BFF;
            tv.setTextColor(drops[hr] ? 0xFFD32F2F : 0xFF161616);
            double share = !Double.isNaN(v) && max > 0 ? Math.max(0, v) / max : 0;
            setWeight(row.findViewById(R.id.fill), (float) share);
            setWeight(row.findViewById(R.id.rest), (float) (1 - share));
            row.findViewById(R.id.fill).setBackgroundTintList(ColorStateList.valueOf(color));
            hoursList.addView(row);
        }
        if (!shownOnce) {
            shownOnce = true;
            Anim.staggerIn((ViewGroup) content);
        }
    }

    /**
     * Caída: una hora con valor por debajo del 60 % del promedio de sus vecinas (la anterior y la siguiente; en la última
     * hora sólo la anterior). Es una regla propuesta; el diseño marca las caídas sin definir el umbral.
     */
    private static boolean[] drops(double[] h, int last) {
        boolean[] out = new boolean[24];
        for (int i = 1; i <= last; i++) {
            double v = h[i], p = h[i - 1];
            if (Double.isNaN(v) || Double.isNaN(p) || v <= 0) continue;
            double nxt = i < last ? h[i + 1] : Double.NaN;
            double ref = Double.isNaN(nxt) ? p : (p + nxt) / 2;
            out[i] = ref > 0 && v < 0.6 * ref;
        }
        return out;
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }
}
