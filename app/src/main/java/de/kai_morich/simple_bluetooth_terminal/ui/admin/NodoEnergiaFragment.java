package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.EnergyBarsView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;

/**
 * A5 · Nodo · Energía día: energía por hora (Día), por día de la semana o del mes, comparada con el periodo anterior,
 * con la hora o el día de mayor consumo, el promedio y la proyección al cierre, más los últimos 7 días.
 */
public class NodoEnergiaFragment extends Fragment {

    private static final Locale ES = new Locale("es", "MX");
    private static final int DIA = 0, SEMANA = 1, MES = 2;

    private ApiService api;
    private int idNode, idCustomer;
    private boolean solar, shownOnce;
    private int mode = DIA, offset, gen;

    private TextView tvDate, btnNext, tvLabel, tvTotal, tvVs, tvLegGhost, tvLegNow, tvLegMain;
    private TextView tvR1L, tvR1V, tvR2L, tvR2V, tvR3L, tvR3V;
    private View row3, line3, content;
    private final TextView[] seg = new TextView[3];
    private EnergyBarsView bars, bars7;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        Bundle a = requireArguments();
        idNode = a.getInt("idMeasure");
        idCustomer = a.getInt("idCustomer");
        solar = NodeKind.of(a.getInt("nodeType", -1), a.getString("nodeTypeName")) == NodeKind.SOLAR;
        api = ApiClient.getApiService();

        View v = inflater.inflate(R.layout.fragment_nodo_energia, container, false);
        content = v.findViewById(R.id.content);
        tvDate = v.findViewById(R.id.tvDate);
        btnNext = v.findViewById(R.id.btnNext);
        tvLabel = v.findViewById(R.id.tvLabel);
        tvTotal = v.findViewById(R.id.tvTotal);
        tvVs = v.findViewById(R.id.tvVs);
        tvLegMain = v.findViewById(R.id.tvLegMain);
        tvLegGhost = v.findViewById(R.id.tvLegGhost);
        tvLegNow = v.findViewById(R.id.tvLegNow);
        tvR1L = v.findViewById(R.id.tvR1L);
        tvR1V = v.findViewById(R.id.tvR1V);
        tvR2L = v.findViewById(R.id.tvR2L);
        tvR2V = v.findViewById(R.id.tvR2V);
        tvR3L = v.findViewById(R.id.tvR3L);
        tvR3V = v.findViewById(R.id.tvR3V);
        row3 = v.findViewById(R.id.row3);
        line3 = v.findViewById(R.id.line3);
        bars = v.findViewById(R.id.bars);
        bars7 = v.findViewById(R.id.bars7);
        bars.setUnit("kWh");
        bars7.setColors(0xFFD6E4FF, 0xFF1E7BFF, 0xFFE0E0E0);
        String[] names = {"Día", "Semana", "Mes"};
        int[] ids = {R.id.seg0, R.id.seg1, R.id.seg2};
        for (int i = 0; i < 3; i++) {
            final int k = i;
            seg[i] = v.findViewById(ids[i]);
            seg[i].setText(names[i]);
            seg[i].setOnClickListener(x -> { mode = k; offset = 0; reload(); });
        }
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> { offset++; reload(); });
        btnNext.setOnClickListener(x -> { if (offset > 0) { offset--; reload(); } });
        reload();
        loadLast7();
        return v;
    }

    private void paintSeg() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == mode;
            seg[i].setBackgroundResource(on ? R.drawable.am_seg_on : 0);
            seg[i].setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.am_ink : R.color.am_t666));
        }
        btnNext.setAlpha(offset > 0 ? 1f : 0.35f);
    }

    private double[] pick(double[] gen, double[] cons) {
        double g = 0, c = 0;
        for (double v : gen) g += v;
        for (double v : cons) c += v;
        return solar ? (g > 0 || c == 0 ? gen : cons) : (c > 0 || g == 0 ? cons : gen);
    }

    private static double sum(double[] a, int to) {
        double s = 0;
        for (int i = 0; i <= to && i < a.length; i++) if (!Double.isNaN(a[i])) s += a[i];
        return s;
    }

    private void reload() {
        if (idCustomer == 0 || idNode == 0) return;
        gen++;
        paintSeg();
        if (mode == DIA) loadDay(gen); else loadRange(gen);
    }

    // ───────────────────────── Día ─────────────────────────

    private void loadDay(final int g) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -offset);
        Calendar p = (Calendar) c.clone();
        p.add(Calendar.DAY_OF_MONTH, -1);
        final double[][] box = new double[2][];
        final int[] pending = {2};
        Runnable done = () -> {
            if (--pending[0] > 0 || !isAdded() || g != gen) return;
            showDay(c, box[0], box[1]);
        };
        EnergyLoader.nodeEnergyHours(api, idCustomer, idNode, EnergyLoader.ymd(c), solar, kwh -> { box[0] = kwh; done.run(); });
        EnergyLoader.nodeEnergyHours(api, idCustomer, idNode, EnergyLoader.ymd(p), solar, kwh -> { box[1] = kwh; done.run(); });
    }

    private void showDay(Calendar day, double[] cur, double[] prev) {
        if (getView() == null) return;
        Calendar now = Calendar.getInstance();
        boolean today = offset == 0;
        int nowH = now.get(Calendar.HOUR_OF_DAY);
        int upTo = today ? nowH : 23;
        if (cur == null) cur = new double[24];
        tvDate.setText(today ? "Hoy · " + fmt("d MMM", day) : fmt("EEE · d MMM", day));

        String[] labels = new String[24], tips = new String[24];
        for (int h = 0; h < 24; h++) {
            labels[h] = String.format(Locale.US, "%02d", h);
            tips[h] = String.format(Locale.US, "%02d:00", h);
        }
        bars.setTips(tips);
        bars.setNames("Hoy", "Ayer");
        bars.set(labels, cur, prev, today ? nowH : -1, true, 6);
        tvLegMain.setText(today ? "■ Hoy" : "■ Día");
        tvLegGhost.setVisibility(prev != null && today ? View.VISIBLE : View.GONE);
        tvLegNow.setVisibility(today ? View.VISIBLE : View.GONE);

        double total = sum(cur, upTo), ySo = prev != null ? sum(prev, upTo) : 0;
        tvLabel.setText(today ? String.format(Locale.US, "Energía hasta las %02d:%02d", nowH, now.get(Calendar.MINUTE)) : "Energía del día");
        Anim.countUp(tvTotal, total, v -> Fmt.withUnit(requireContext(), Fmt.n1(v), " kWh", 14f / 32f));
        paintVs(ySo > 0 ? Math.round(100 * (total - ySo) / ySo) : null, "ayer");

        int maxAt = 0;
        for (int h = 0; h <= upTo; h++) if (cur[h] > cur[maxAt]) maxAt = h;
        tvR1L.setText("Hora de mayor consumo");
        tvR1V.setText(cur[maxAt] > 0 ? String.format(Locale.US, "%02d:00 · %s kWh", maxAt, Fmt.n0(cur[maxAt])) : "—");
        tvR2L.setText("Promedio por hora");
        tvR2V.setText(Fmt.n0(total / (upTo + 1)) + " kWh");
        if (today && prev != null && ySo > 0) {
            // Lo que falta del día se estima como ayer, ajustado a cuánto va hoy respecto a ayer a la misma hora
            double ratio = Math.max(0.5, Math.min(2.0, total / ySo));
            tvR3L.setText("Proyección al cierre");
            tvR3V.setText("≈ " + Fmt.n0(total + (sum(prev, 23) - ySo) * ratio) + " kWh");
            row3.setVisibility(View.VISIBLE);
            line3.setVisibility(View.VISIBLE);
        } else {
            row3.setVisibility(View.GONE);
            line3.setVisibility(View.GONE);
        }
        firstShow();
    }

    // ───────────────────────── Semana / Mes ─────────────────────────

    private void loadRange(final int g) {
        final Calendar start = Calendar.getInstance();
        final int count;
        final boolean live;
        if (mode == SEMANA) {
            start.add(Calendar.DAY_OF_MONTH, -7 * offset - 6);
            count = 7;
            live = offset == 0;
        } else {
            start.add(Calendar.MONTH, -offset);
            live = offset == 0;
            count = live ? start.get(Calendar.DAY_OF_MONTH) : start.getActualMaximum(Calendar.DAY_OF_MONTH);
            start.set(Calendar.DAY_OF_MONTH, 1);
        }
        start.set(Calendar.HOUR_OF_DAY, 12);
        final Calendar prevStart = (Calendar) start.clone();
        if (mode == SEMANA) prevStart.add(Calendar.DAY_OF_MONTH, -7); else prevStart.add(Calendar.MONTH, -1);
        final int prevCount = mode == SEMANA ? 7 : Math.min(count, prevStart.getActualMaximum(Calendar.DAY_OF_MONTH));

        final double[][] box = new double[2][];
        final int[] pending = {2};
        Runnable done = () -> {
            if (--pending[0] > 0 || !isAdded() || g != gen) return;
            showRange(start, count, live, box[0], box[1]);
        };
        EnergyLoader.nodeDays(api, idCustomer, idNode, start, count, (gn, cs, sm) -> { box[0] = gn == null ? new double[count] : pick(gn, cs); done.run(); });
        EnergyLoader.nodeDays(api, idCustomer, idNode, prevStart, prevCount, (gn, cs, sm) -> { box[1] = gn == null ? null : pick(gn, cs); done.run(); });
    }

    private void showRange(Calendar start, int count, boolean live, double[] cur, double[] prev) {
        if (getView() == null) return;
        String[] labels = new String[count], tips = new String[count];
        Calendar d = (Calendar) start.clone();
        String[] wd = {"D", "L", "M", "M", "J", "V", "S"};
        for (int i = 0; i < count; i++, d.add(Calendar.DAY_OF_MONTH, 1)) {
            labels[i] = mode == SEMANA ? wd[d.get(Calendar.DAY_OF_WEEK) - 1] : String.valueOf(d.get(Calendar.DAY_OF_MONTH));
            tips[i] = cap(fmt("EEE d MMM", d));
        }
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, count - 1);
        tvDate.setText(mode == SEMANA ? fmt("d", start) + " – " + fmt("d MMM", end) : cap(fmt("MMMM yyyy", start)));
        bars.setTips(tips);
        bars.setNames("Energía", "");
        bars.set(labels, cur, null, live && mode == SEMANA ? count - 1 : -1, true, mode == SEMANA ? 1 : 5);
        tvLegMain.setText("■ kWh por día");
        tvLegGhost.setVisibility(View.GONE);
        tvLegNow.setVisibility(live && mode == SEMANA ? View.VISIBLE : View.GONE);
        tvLegNow.setText("■ hoy");

        double total = sum(cur, count - 1), pt = prev != null ? sum(prev, prev.length - 1) : 0;
        tvLabel.setText(mode == SEMANA ? "Energía de la semana" : live ? "Energía del mes, hasta hoy" : "Energía del mes");
        Anim.countUp(tvTotal, total, v -> Fmt.withUnit(requireContext(), Fmt.n0(v), " kWh", 14f / 32f));
        paintVs(pt > 0 ? Math.round(100 * (total - pt) / pt) : null, mode == SEMANA ? "semana anterior" : "mes anterior");

        int maxAt = 0;
        for (int i = 0; i < count; i++) if (cur[i] > cur[maxAt]) maxAt = i;
        Calendar md = (Calendar) start.clone();
        md.add(Calendar.DAY_OF_MONTH, maxAt);
        tvR1L.setText("Día de mayor consumo");
        tvR1V.setText(cur[maxAt] > 0 ? cap(fmt("EEE d", md)) + " · " + Fmt.n0(cur[maxAt]) + " kWh" : "—");
        tvR2L.setText("Promedio por día");
        tvR2V.setText(Fmt.n0(total / Math.max(1, count)) + " kWh");
        if (mode == MES && live) {
            tvR3L.setText("Proyección al cierre");
            tvR3V.setText("≈ " + Fmt.n0(total / count * start.getActualMaximum(Calendar.DAY_OF_MONTH)) + " kWh");
            row3.setVisibility(View.VISIBLE);
            line3.setVisibility(View.VISIBLE);
        } else {
            row3.setVisibility(View.GONE);
            line3.setVisibility(View.GONE);
        }
        firstShow();
    }

    /** Insignia "↓ 2% vs ayer": verde si baja, roja si sube. */
    private void paintVs(Long pct, String against) {
        if (pct == null) { tvVs.setVisibility(View.GONE); return; }
        tvVs.setVisibility(View.VISIBLE);
        boolean down = pct <= 0;
        tvVs.setText((pct == 0 ? "= " : down ? "↓ " : "↑ ") + Math.abs(pct) + "% vs " + against);
        tvVs.setBackgroundResource(down ? R.drawable.am_badge_ok : R.drawable.am_badge_err);
        tvVs.setTextColor(ContextCompat.getColor(requireContext(), down ? R.color.am_ok_text : R.color.am_err_text));
    }

    // ───────────────────────── últimos 7 días ─────────────────────────

    private void loadLast7() {
        final Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -6);
        start.set(Calendar.HOUR_OF_DAY, 12);
        final int g = gen;
        EnergyLoader.nodeDays(api, idCustomer, idNode, start, 7, (gn, cs, sm) -> {
            if (!isAdded() || gn == null) return;
            double[] v = pick(gn, cs);
            String[] labels = new String[7], tips = new String[7];
            String[] wd = {"D", "L", "M", "M", "J", "V", "S"};
            Calendar d = (Calendar) start.clone();
            for (int i = 0; i < 7; i++, d.add(Calendar.DAY_OF_MONTH, 1)) {
                labels[i] = i == 6 ? "Hoy" : wd[d.get(Calendar.DAY_OF_WEEK) - 1];
                tips[i] = cap(fmt("EEE d MMM", d));
            }
            bars7.setTips(tips);
            bars7.setNames("Energía", "");
            bars7.set(labels, v, null, 6, false, 1);
        });
    }

    // ───────────────────────── utilidades ─────────────────────────

    private void firstShow() {
        if (shownOnce) return;
        shownOnce = true;
        Anim.staggerIn((ViewGroup) content);
    }

    private static String fmt(String pattern, Calendar c) {
        return new SimpleDateFormat(pattern, ES).format(c.getTime()).replace(".", "");
    }

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1); }
}
