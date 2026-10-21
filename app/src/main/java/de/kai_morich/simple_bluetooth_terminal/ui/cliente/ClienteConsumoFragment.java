package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.StackedBarsView;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;

/** K2 · Consumo por período (Día / Semana / Mes): solar y CFE apilados, comparación con el período anterior y desglose. */
public class ClienteConsumoFragment extends Fragment {

    private static final int DIA = 0, SEMANA = 1, MES = 2;
    private static final Locale ES = new Locale("es", "MX");

    private ClienteViewModel vm;
    private TextView[] seg;
    private TextView tvRange, tvTotalLabel, tvTotal, chipDelta, tvTip, tvBrSolar, tvBrCfe, tvBrMaxLabel, tvBrMax, btnNext, tvLiveNote;
    private StackedBarsView bars;

    private int period = SEMANA;
    private int offset = 0; // períodos hacia atrás; 0 = el actual
    private int gen;

    // Datos mostrados (para el tooltip)
    private String[] tipLabels = new String[0];
    private double[] solar = new double[0], cfe = new double[0];

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inflater.inflate(R.layout.fragment_cliente_consumo, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        seg = new TextView[]{v.findViewById(R.id.segDia), v.findViewById(R.id.segSemana), v.findViewById(R.id.segMes)};
        tvRange = v.findViewById(R.id.tvRange);
        tvTotalLabel = v.findViewById(R.id.tvTotalLabel);
        tvTotal = v.findViewById(R.id.tvTotal);
        chipDelta = v.findViewById(R.id.chipDelta);
        tvTip = v.findViewById(R.id.tvTip);
        tvBrSolar = v.findViewById(R.id.tvBrSolar);
        tvBrCfe = v.findViewById(R.id.tvBrCfe);
        tvBrMaxLabel = v.findViewById(R.id.tvBrMaxLabel);
        tvBrMax = v.findViewById(R.id.tvBrMax);
        tvLiveNote = v.findViewById(R.id.tvLiveNote);
        btnNext = v.findViewById(R.id.btnNext);
        bars = v.findViewById(R.id.bars);

        for (int i = 0; i < 3; i++) {
            final int p = i;
            seg[i].setOnClickListener(x -> { period = p; offset = 0; load(); });
        }
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> { offset++; load(); });
        btnNext.setOnClickListener(x -> { if (offset > 0) { offset--; load(); } }); // no se avanza más allá del período actual
        bars.setOnBar(i -> {
            if (i < tipLabels.length && i < solar.length) {
                tvTip.setText(tipLabels[i] + " · Sol " + Fmt.n0(solar[i]) + " · CFE " + Fmt.n0(cfe[i])
                        + " · Total " + Fmt.n0(solar[i] + cfe[i]) + " kWh");
                tvTip.setVisibility(View.VISIBLE);
            }
        });

        vm.location.observe(getViewLifecycleOwner(), l -> { offset = 0; load(); });
        return v;
    }

    // ───────────────────────── carga ─────────────────────────

    private void load() {
        if (getView() == null) return;
        paintSegment();
        tvTip.setVisibility(View.GONE);
        if (vm.customerId == null || vm.location.getValue() == null) { clear(); return; }
        final int g = ++gen;
        final Calendar today = Calendar.getInstance();
        // Rango actual y el anterior de la misma longitud
        final Calendar start = periodStart(today, offset);
        final int count = bucketCount(start);
        final Calendar prevStart = periodStart(today, offset + 1);
        final int prevCount = bucketCount(prevStart);
        final boolean includesToday = offset == 0;

        // Totales de la localidad: Día = getTotalPowerDay (kW por hora), Semana/Mes = getTotalEnergyMonth (kWh por día)
        final int loc = vm.locationId();
        final double[][] cur = new double[2][], prev = new double[2][];
        final int[] pending = {2};
        Runnable done = () -> {
            if (--pending[0] > 0 || !isAdded() || g != gen) return;
            if (cur[0] == null) { clear(); return; }
            show(start, cur[0], cur[1], prev[0], prev[1], includesToday, today);
        };
        EnergyLoader.Totals sc = (s, gr, co) -> { cur[0] = s; cur[1] = gr; done.run(); };
        EnergyLoader.Totals sp = (s, gr, co) -> { prev[0] = s; prev[1] = gr; done.run(); };
        if (period == DIA) {
            EnergyLoader.day(vm.api(), vm.customerId, loc, EnergyLoader.ymd(start), sc);
            EnergyLoader.day(vm.api(), vm.customerId, loc, EnergyLoader.ymd(prevStart), sp);
        } else {
            EnergyLoader.days(vm.api(), vm.customerId, loc, start, count, sc);
            EnergyLoader.days(vm.api(), vm.customerId, loc, prevStart, prevCount, sp);
        }
    }

    /** Inicio del período: Día = ese día · Semana = 7 días terminando hoy−7·offset · Mes = primer día del mes. */
    private Calendar periodStart(Calendar today, int off) {
        Calendar c = (Calendar) today.clone();
        c.set(Calendar.HOUR_OF_DAY, 12);
        switch (period) {
            case DIA:
                c.add(Calendar.DAY_OF_MONTH, -off);
                break;
            case SEMANA:
                c.add(Calendar.DAY_OF_MONTH, -6 - 7 * off);
                break;
            default:
                c.set(Calendar.DAY_OF_MONTH, 1);
                c.add(Calendar.MONTH, -off);
        }
        return c;
    }

    private int bucketCount(Calendar start) {
        switch (period) {
            case DIA: return 24;
            case SEMANA: return 7;
            default: return start.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
    }

    // ───────────────────────── pintar ─────────────────────────

    private void paintSegment() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == period;
            seg[i].setBackgroundResource(on ? R.drawable.am_seg_on : 0);
            seg[i].setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.am_ink : R.color.am_t666));
        }
        btnNext.setTextColor(offset > 0 ? 0xFF999999 : 0xFFCCCCCC);
        tvTotalLabel.setText(period == DIA ? "Total día" : period == SEMANA ? "Total semana" : "Total mes");
        tvBrMaxLabel.setText(period == DIA ? "Hora con más consumo" : "Día con más consumo");
    }

    private void clear() {
        if (getView() == null) return;
        tipLabels = new String[0];
        solar = new double[0];
        cfe = new double[0];
        bars.set(new String[0], solar, cfe, false, 1);
        tvTotal.setText("--");
        chipDelta.setVisibility(View.GONE);
        tvBrSolar.setText("--");
        tvBrCfe.setText("--");
        tvBrMax.setText("--");
        tvRange.setText(rangeLabel(periodStart(Calendar.getInstance(), offset)));
    }

    private void show(Calendar start, double[] s, double[] c, double[] ps, double[] pc, boolean includesToday, Calendar today) {
        solar = s;
        cfe = c;
        int n = s.length;
        String[] labels = new String[n];
        tipLabels = new String[n];
        Calendar d = (Calendar) start.clone();
        for (int i = 0; i < n; i++) {
            if (period == DIA) {
                labels[i] = String.format(Locale.US, "%02d", i);
                tipLabels[i] = String.format(Locale.US, "%02d:00", i);
            } else {
                labels[i] = period == SEMANA
                        ? (includesToday && i == n - 1 ? "Hoy" : new SimpleDateFormat("EEE", ES).format(d.getTime()).substring(0, 1).toUpperCase(ES))
                        : String.valueOf(d.get(Calendar.DAY_OF_MONTH));
                tipLabels[i] = cap(new SimpleDateFormat("EEE d", ES).format(d.getTime()).replace(".", ""));
                d.add(Calendar.DAY_OF_MONTH, 1);
            }
        }
        // La barra "en curso": última hora (día) o último día (semana); en mes, el día de hoy
        int liveIdx = -1;
        if (includesToday) {
            liveIdx = period == DIA ? today.get(Calendar.HOUR_OF_DAY) : period == SEMANA ? n - 1 : today.get(Calendar.DAY_OF_MONTH) - 1;
        }
        bars.set(labels, s, c, includesToday && period != MES && liveIdx == n - 1, period == DIA ? 6 : period == MES ? 5 : 1);
        bars.setTips(tipLabels);
        tvLiveNote.setVisibility(includesToday ? View.VISIBLE : View.GONE);
        tvRange.setText(rangeLabel(start));

        // Totales y desglose
        double sol = 0, grid = 0, maxV = -1;
        int maxI = 0;
        for (int i = 0; i < n; i++) {
            sol += s[i];
            grid += c[i];
            if (s[i] + c[i] > maxV) { maxV = s[i] + c[i]; maxI = i; }
        }
        double total = sol + grid;
        tvTotal.setText(Fmt.withUnit(requireContext(), Fmt.n0(total), " kWh", 12f / 26f));
        tvBrSolar.setText(Fmt.n0(sol) + " kWh · " + pct(sol, total));
        tvBrCfe.setText(Fmt.n0(grid) + " kWh · " + pct(grid, total));
        tvBrMax.setText(total > 0 ? (period == DIA ? tipLabels[maxI] : fullDay(start, maxI)) : "--");

        // Comparación: si el período está en curso se compara contra el mismo tramo del anterior
        double prevTotal = 0;
        int upTo = ps != null ? ps.length : 0;
        if (includesToday && period == DIA) upTo = Math.min(upTo, today.get(Calendar.HOUR_OF_DAY) + 1);
        if (includesToday && period == MES) upTo = Math.min(upTo, today.get(Calendar.DAY_OF_MONTH));
        for (int i = 0; i < upTo; i++) prevTotal += ps[i] + pc[i];
        double curCmp = total;
        if (includesToday && period == MES) { // sólo los días transcurridos del mes actual
            curCmp = 0;
            for (int i = 0; i < Math.min(n, today.get(Calendar.DAY_OF_MONTH)); i++) curCmp += s[i] + c[i];
        }
        if (prevTotal > 0) {
            long r = Math.round(100 * (curCmp - prevTotal) / prevTotal);
            boolean down = r <= 0;
            chipDelta.setText((r == 0 ? "= " : down ? "↓ " : "↑ ") + Math.abs(r) + "% vs anterior");
            chipDelta.setBackgroundResource(down ? R.drawable.am_badge_ok : R.drawable.am_badge_err);
            chipDelta.setTextColor(ContextCompat.getColor(requireContext(), down ? R.color.am_ok_text : R.color.am_err_text));
            chipDelta.setVisibility(View.VISIBLE);
        } else {
            chipDelta.setVisibility(View.GONE);
        }
    }

    private String fullDay(Calendar start, int idx) {
        Calendar d = (Calendar) start.clone();
        d.add(Calendar.DAY_OF_MONTH, idx);
        return cap(new SimpleDateFormat("EEEE d", ES).format(d.getTime()));
    }

    private String rangeLabel(Calendar start) {
        SimpleDateFormat full = new SimpleDateFormat("d MMM yyyy", ES);
        if (period == DIA) {
            return offset == 0 ? "Hoy" : full.format(start.getTime()).replace(".", "");
        }
        if (period == MES) {
            return cap(new SimpleDateFormat("MMMM yyyy", ES).format(start.getTime()));
        }
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.DAY_OF_MONTH, 6);
        boolean sameMonth = start.get(Calendar.MONTH) == end.get(Calendar.MONTH);
        String a = new SimpleDateFormat(sameMonth ? "d" : "d MMM", ES).format(start.getTime());
        return (a + " – " + full.format(end.getTime())).replace(".", "");
    }

    private static String pct(double part, double total) {
        return total > 0 ? Math.round(100 * part / total) + "%" : "--";
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1);
    }
}
