package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.animation.ValueAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.data.DeviceLookup;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.LocationEnergyPower;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsEnergyPowerResponse;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DarkChartView;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.StackedBarsView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import de.kai_morich.simple_bluetooth_terminal.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * K4 · Detalle de un medidor, en palabras simples: tarjeta oscura "Ahora" con Hoy / Semana / Mes,
 * tres datos del día y la calidad de energía. Los datos técnicos quedan detrás de un enlace.
 */
public class ClienteMedidorFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L;

    static ClienteMedidorFragment newInstance(NodeTrafficLight n) {
        Bundle b = new Bundle();
        b.putInt("idNode", n.getIdNode() != null ? n.getIdNode() : 0);
        b.putString("name", n.getName());
        b.putInt("type", n.getType() != null ? n.getType() : -1);
        b.putString("typeName", n.getTypeName());
        b.putInt("idCircuit", n.getIdCircuit() != null ? n.getIdCircuit() : 0);
        b.putString("serie", n.getSerie());
        b.putString("lastDate", n.getLastDate());
        b.putString("lastHour", n.getLastHour());
        b.putBoolean("online", n.isGreen());
        ClienteMedidorFragment f = new ClienteMedidorFragment();
        f.setArguments(b);
        return f;
    }

    private ClienteViewModel vm;
    private int idNode, idCircuit, kind;
    private String name, serie, typeName, lastDate, lastHour;
    private boolean online;
    private DeviceCurrent data;
    private double[] todayKw, yesterdayKw;          // kW ya calibrados
    private double[] rawToday, rawYesterday;         // crudos del API / 1000
    private double nodeEnergyKwh = -1;               // getEnergyDay: respaldo si no hay lectura en vivo
    private double plantKw = -1;
    private int tab; // 0 hoy · 1 semana · 2 mes
    private double[][] weekKwh, monthKwh; // {generación, consumo} por día, en caché
    private boolean chartDrawn;
    private final float[] shownWeight = new float[3];

    private TextView tvBadge, tvPower, tvShare, tvToday, tvDelta, tvPeak, tvPeakAt, tvAvg, tvQuality, tvLegA, tvLegB;
    private TextView tvVolt, tvVoltNote, tvImb, tvImbNote, tvPf, tvPfNote;
    private TextView[] tabs;
    private final TextView[] tvA = new TextView[3];
    private final View[] fill = new View[3], rest = new View[3];
    private DarkChartView chart;
    private StackedBarsView bars;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            load();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        Bundle a = requireArguments();
        idNode = a.getInt("idNode");
        idCircuit = a.getInt("idCircuit");
        name = a.getString("name");
        serie = a.getString("serie");
        typeName = a.getString("typeName");
        lastDate = a.getString("lastDate");
        lastHour = a.getString("lastHour");
        online = a.getBoolean("online");
        kind = NodeKind.of(a.getInt("type") >= 0 ? a.getInt("type") : null, typeName);

        View v = inflater.inflate(R.layout.fragment_cliente_medidor, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        ((TextView) v.findViewById(R.id.tvName)).setText(name);
        tvBadge = v.findViewById(R.id.tvBadge);
        tvPower = v.findViewById(R.id.tvPower);
        tvShare = v.findViewById(R.id.tvShare);
        tvToday = v.findViewById(R.id.tvToday);
        tvDelta = v.findViewById(R.id.tvDelta);
        tvPeak = v.findViewById(R.id.tvPeak);
        tvPeakAt = v.findViewById(R.id.tvPeakAt);
        tvAvg = v.findViewById(R.id.tvAvg);
        tvQuality = v.findViewById(R.id.tvQuality);
        tvLegA = v.findViewById(R.id.tvLegA);
        tvLegB = v.findViewById(R.id.tvLegB);
        tvVolt = v.findViewById(R.id.tvVolt);
        tvVoltNote = v.findViewById(R.id.tvVoltNote);
        tvImb = v.findViewById(R.id.tvImb);
        tvImbNote = v.findViewById(R.id.tvImbNote);
        tvPf = v.findViewById(R.id.tvPf);
        tvPfNote = v.findViewById(R.id.tvPfNote);
        chart = v.findViewById(R.id.chart);
        bars = v.findViewById(R.id.bars);
        chart.setYLabels(true);
        chart.setNames("Hoy", "Solar", "Ayer");
        bars.setDark(true);
        int[] aIds = {R.id.tvA1, R.id.tvA2, R.id.tvA3};
        int[] fIds = {R.id.fill1, R.id.fill2, R.id.fill3};
        int[] rIds = {R.id.rest1, R.id.rest2, R.id.rest3};
        for (int i = 0; i < 3; i++) {
            tvA[i] = v.findViewById(aIds[i]);
            fill[i] = v.findViewById(fIds[i]);
            rest[i] = v.findViewById(rIds[i]);
        }
        tabs = new TextView[]{v.findViewById(R.id.tabDay), v.findViewById(R.id.tabWeek), v.findViewById(R.id.tabMonth)};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            tabs[i].setOnClickListener(x -> selectTab(t));
        }
        if (kind == NodeKind.SOLAR) chart.setLineColors(0xFFF2B01E, 0xFF4A3A10);
        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());
        View tech = v.findViewById(R.id.rowTech);
        Anim.pressable(tech);
        tech.setOnClickListener(x -> openTecnicos());

        boolean transformer = typeName != null && typeName.toLowerCase(Locale.ROOT).contains("transform");
        String desc = kind == NodeKind.SOLAR ? "Paneles solares"
                : transformer ? "Transformador"
                : kind == NodeKind.CFE ? "Entrada general (CFE)"
                : "Punto de consumo";
        ((TextView) v.findViewById(R.id.tvSub)).setText(desc + " · actualizado " + RelativeTime.ago(lastDate, lastHour));
        paintBadge();
        styleTabs();
        paintLegend();
        Anim.staggerIn((ViewGroup) v.findViewById(R.id.content));
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ───────────────────────── datos ─────────────────────────

    private void load() {
        if (vm.customerId == null || idNode == 0) return;
        DeviceLookup.find(vm.api(), vm.customerId, idCircuit, idNode, requireArguments().getInt("type", -1), serie, name, vm.circuitIds(), d -> {
            if (!isAdded() || d == null) return;
            data = d;
            render();
        });
        List<NodeTrafficLight> one = new ArrayList<>();
        NodeTrafficLight n = new NodeTrafficLight();
        n.setFields(idNode, name, typeName, idCircuit, 0, lastDate, lastHour, online ? "Green" : "Red", -1);
        one.add(n);
        Calendar now = Calendar.getInstance();
        EnergyLoader.powerDay(vm.api(), vm.customerId, one, EnergyLoader.ymd(now), kw -> {
            if (!isAdded()) return;
            rawToday = kw;
            render();
        });
        EnergyLoader.nodeEnergyToday(vm.api(), vm.customerId, idNode, EnergyLoader.ymd(now), kind == NodeKind.SOLAR, kwh -> {
            if (!isAdded() || kwh == null) return;
            nodeEnergyKwh = kwh;
            render();
        });
        if (rawYesterday == null) {
            Calendar y = (Calendar) now.clone();
            y.add(Calendar.DAY_OF_MONTH, -1);
            EnergyLoader.powerDay(vm.api(), vm.customerId, one, EnergyLoader.ymd(y), kw -> {
                if (!isAdded()) return;
                rawYesterday = kw;
                render();
            });
        }
        // Consumo de toda la planta, para el "% del consumo"
        if (kind != NodeKind.SOLAR && vm.locationId() != null) {
            final Integer loc = vm.locationId();
            vm.api().getLocationsEnergyPowerTotals(vm.customerId).enqueue(new Callback<LocationsEnergyPowerResponse>() {
                @Override public void onResponse(Call<LocationsEnergyPowerResponse> c, Response<LocationsEnergyPowerResponse> r) {
                    if (!isAdded() || !r.isSuccessful() || r.body() == null || r.body().getLocations() == null) return;
                    for (LocationEnergyPower l : r.body().getLocations()) {
                        if (loc.equals(l.getIdLocation())) { plantKw = l.getLocalityW() / 1000.0; break; }
                    }
                    render();
                }
                @Override public void onFailure(Call<LocationsEnergyPowerResponse> c, Throwable t) { }
            });
        }
    }

    /** Barras de energía por día de la semana / del mes (se piden una vez al abrir la pestaña). */
    private void loadBars(final int which) {
        if (vm.customerId == null) return;
        Calendar start = Calendar.getInstance();
        final int count;
        if (which == 1) {
            start.add(Calendar.DAY_OF_MONTH, -6);
            count = 7;
        } else {
            count = start.get(Calendar.DAY_OF_MONTH);
            start.set(Calendar.DAY_OF_MONTH, 1);
        }
        start.set(Calendar.HOUR_OF_DAY, 12);
        EnergyLoader.nodeDays(vm.api(), vm.customerId, idNode, start, count, (gen, cons, sum) -> {
            if (!isAdded()) return;
            double[][] r = gen == null ? new double[][]{new double[count], new double[count]} : new double[][]{gen, cons};
            if (which == 1) weekKwh = r; else monthKwh = r;
            if (tab == which) renderBars();
        });
    }

    // ───────────────────────── render ─────────────────────────

    private void render() {
        if (getView() == null) return;
        boolean solar = kind == NodeKind.SOLAR;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        // Energia de hoy: lectura en vivo; si no hay, la suma de getEnergyDay
        double kwh = -1;
        if (data != null) {
            double wh = solar ? data.getGenWhDay() : data.getConWhDay();
            if (wh <= 0) wh = solar ? data.getConWhDay() : data.getGenWhDay();
            if (wh > 0) kwh = wh / 1000.0;
        }
        if (kwh < 0 && nodeEnergyKwh >= 0) kwh = nodeEnergyKwh;
        calibrate(kwh, hour);
        // Potencia ahora: lectura en vivo, luego suma de fases, luego la hora actual de la curva
        double kw = -1;
        if (data != null) {
            double w = solar ? data.getGenW() : data.getConW();
            if (w == 0) w = solar ? data.getConW() : data.getGenW();
            if (w == 0) w = Math.abs(data.getW1() + data.getW2() + data.getW3());
            if (w > 0) kw = Math.abs(w) / 1000.0;
        }
        if (kw < 0 && todayKw != null) kw = todayKw[hour];
        if (kw >= 0) Anim.countUp(tvPower, kw, v -> Fmt.withUnit(requireContext(), Fmt.n1(v), " kW", 18f / 38f));
        if (kwh >= 0) Anim.countUp(tvToday, kwh, v -> Fmt.n0(v));
        if (!solar && plantKw > 0.05 && kw >= 0) {
            tvShare.setText(Math.round(100 * kw / plantKw) + "% del consumo de la planta");
        } else {
            tvShare.setText(solar ? "Lo que generan tus paneles" : "Consumo de este punto");
        }
        if (data != null) renderQuality();
        renderDay();
    }

    /**
     * La curva por hora del API no trae unidad fija (W en unos clientes, decimas de W en otros): se compara su suma
     * con la energia de hoy y se redondea a la potencia de diez mas cercana (0.1 a 100).
     */
    private void calibrate(double kwh, int hour) {
        double f = 1;
        if (rawToday != null && kwh > 0) {
            double sum = 0;
            for (int h = 0; h <= hour; h++) sum += rawToday[h];
            if (sum > 0) f = Math.max(0.1, Math.min(100, Math.pow(10, Math.round(Math.log10(kwh / sum)))));
        }
        todayKw = EnergyLoader.scale(rawToday, f);
        yesterdayKw = EnergyLoader.scale(rawYesterday, f);
    }

    /** Pico, promedio y "vs ayer" salen de la curva horaria; la gráfica se actualiza sin repetir el barrido. */
    private void renderDay() {
        if (getView() == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (todayKw != null) {
            double peak = 0, sum = 0, ySum = 0;
            int at = 0;
            for (int h = 0; h <= hour; h++) {
                sum += todayKw[h];
                if (yesterdayKw != null) ySum += yesterdayKw[h];
                if (todayKw[h] > peak) { peak = todayKw[h]; at = h; }
            }
            Anim.countUp(tvPeak, peak, v -> Fmt.n0(v));
            tvPeakAt.setText(peak > 0 ? String.format(Locale.US, "%02d:00", at) : " ");
            Anim.countUp(tvAvg, sum / (hour + 1), v -> Fmt.n0(v));
            if (yesterdayKw != null && ySum > 0) {
                long pct = Math.round(100 * (sum - ySum) / ySum);
                tvDelta.setText(pct == 0 ? "= igual que ayer" : (pct < 0 ? "↓ " : "↑ ") + Math.abs(pct) + "% vs ayer");
                tvDelta.setTextColor(ContextCompat.getColor(requireContext(), pct <= 0 ? R.color.am_ok_text : R.color.am_err_text));
            }
        }
        if (tab == 0) renderChart();
    }

    private void renderChart() {
        if (todayKw == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        double[] upToNow = Arrays.copyOf(todayKw, hour + 1);
        int at = 0;
        for (int h = 0; h <= hour; h++) if (todayKw[h] > todayKw[at]) at = h;
        String label = todayKw[at] > 0 ? String.format(Locale.US, "pico %s · %02d:00", Fmt.n0(todayKw[at]), at) : null;
        if (chartDrawn) {
            chart.update(upToNow, null, yesterdayKw, hour, label);
        } else {
            chart.set(upToNow, null, yesterdayKw, hour, label);
            chartDrawn = true;
        }
    }

    private void renderBars() {
        double[][] d = tab == 1 ? weekKwh : monthKwh;
        if (d == null) return;
        int n = d[0].length;
        String[] labels = new String[n];
        if (tab == 1) {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_MONTH, -(n - 1));
            String[] wd = {"D", "L", "M", "M", "J", "V", "S"};
            for (int i = 0; i < n; i++, c.add(Calendar.DAY_OF_MONTH, 1)) labels[i] = wd[c.get(Calendar.DAY_OF_WEEK) - 1];
        } else {
            for (int i = 0; i < n; i++) labels[i] = String.valueOf(i + 1);
        }
        boolean solar = kind == NodeKind.SOLAR;
        double[] zeros = new double[n];
        // Titulos del globito: "Lun 13 oct" (semana) / "19 oct" (mes)
        String[] tips = new String[n];
        Calendar t = Calendar.getInstance();
        if (tab == 1) t.add(Calendar.DAY_OF_MONTH, -(n - 1)); else t.set(Calendar.DAY_OF_MONTH, 1);
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(tab == 1 ? "EEE d MMM" : "d MMM", new Locale("es", "MX"));
        for (int i = 0; i < n; i++, t.add(Calendar.DAY_OF_MONTH, 1)) {
            String day = fmt.format(t.getTime()).replace(".", "");
            tips[i] = day.isEmpty() ? day : day.substring(0, 1).toUpperCase(new Locale("es", "MX")) + day.substring(1);
        }
        bars.setTips(tips);
        bars.setNames(solar ? "Generación" : "", solar ? "" : "Consumo");
        // Solar: generación en dorado · el resto: consumo en azul (el API entrega ambos en kWh)
        bars.set(labels, solar ? d[0] : zeros, solar ? zeros : d[1], true, tab == 1 ? 1 : 5);
    }

    private void selectTab(int t) {
        if (t == tab) return;
        tab = t;
        styleTabs();
        paintLegend();
        View from = t == 0 ? bars : chart, to = t == 0 ? chart : bars;
        if (to.getVisibility() != View.VISIBLE) {
            to.setAlpha(0f);
            to.setVisibility(View.VISIBLE);
            to.animate().alpha(1f).setDuration(220).start();
            from.setVisibility(View.GONE);
        }
        if (t == 0) {
            chartDrawn = false;
            renderChart();
        } else if ((t == 1 ? weekKwh : monthKwh) == null) {
            loadBars(t);
        } else {
            renderBars();
        }
    }

    private void styleTabs() {
        for (int i = 0; i < 3; i++) {
            boolean on = i == tab;
            tabs[i].setBackgroundResource(on ? R.drawable.am_seg_on : 0);
            tabs[i].setTextColor(ContextCompat.getColor(requireContext(), on ? R.color.am_ink : R.color.am_t888));
        }
    }

    private void paintLegend() {
        tvLegA.setText(tab == 0 ? "● Hoy" : "● kWh por día");
        tvLegA.setTextColor(kind == NodeKind.SOLAR ? 0xFFF2B01E : ContextCompat.getColor(requireContext(), R.color.am_cfe_on_dark));
        tvLegB.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
        tvLegB.setText("┄ Ayer");
    }

    private void renderQuality() {
        double pf = avgNonZero(data.getPf1(), data.getPf2(), data.getPf3());
        double v = avgNonZero(data.getV1(), data.getV2(), data.getV3());
        double imb = voltageImbalance(data.getV1(), data.getV2(), data.getV3());
        String q = quality(pf, data.getV1(), data.getV2(), data.getV3());
        tvQuality.setText(q != null ? q : "—");
        tvQuality.setBackgroundResource("Mala".equals(q) ? R.drawable.am_badge_err : R.drawable.am_badge_ok);
        tvQuality.setTextColor(ContextCompat.getColor(requireContext(),
                "Buena".equals(q) ? R.color.am_ok_text : "Regular".equals(q) ? R.color.am_virtual : "Mala".equals(q) ? R.color.am_err_text : R.color.am_t777));

        tvVolt.setText(v > 0 ? String.format(Locale.US, "%.0f V", v) : "—");
        tvVoltNote.setText(v > 0 ? (imb < 2 ? "estable" : "variable") : " ");
        tvImb.setText(imb >= 0 ? String.format(Locale.US, "%.1f %%", imb) : "—");
        tvImbNote.setText(imb >= 0 ? (imb < 2 ? "normal" : imb < 3.5 ? "atención" : "alto") : " ");
        tvPf.setText(pf > 0 ? String.format(Locale.US, "%.2f", pf) : "—");
        tvPfNote.setText(pf > 0 ? (pf >= 0.95 ? "bueno" : pf >= 0.90 ? "regular" : "bajo") : " ");

        // Corriente por fase: barras proporcionales a la fase más cargada
        double[] amps = {data.getI1(), data.getI2(), data.getI3()};
        double max = Math.max(1, Math.max(amps[0], Math.max(amps[1], amps[2])));
        for (int i = 0; i < 3; i++) {
            tvA[i].setText(amps[i] > 0 ? String.format(Locale.US, "%.1f A", amps[i]) : "—");
            growWeight(i, (float) (amps[i] / max));
        }
    }

    private void growWeight(final int i, float to) {
        final float from = shownWeight[i];
        shownWeight[i] = to;
        ValueAnimator a = ValueAnimator.ofFloat(from, to);
        a.setDuration(650);
        a.setInterpolator(new DecelerateInterpolator(1.6f));
        a.addUpdateListener(x -> {
            float w = (float) x.getAnimatedValue();
            setWeight(fill[i], w);
            setWeight(rest[i], 1f - w);
        });
        a.start();
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }

    private void paintBadge() {
        tvBadge.setText(online ? "Funcionando" : "Sin conexión");
        tvBadge.setBackgroundResource(online ? R.drawable.am_badge_ok : R.drawable.am_badge_err);
        tvBadge.setTextColor(ContextCompat.getColor(requireContext(), online ? R.color.am_ok_text : R.color.am_err_text));
    }

    /** Desbalance de voltaje en %, o -1 si hay menos de dos fases con voltaje. */
    private static double voltageImbalance(double v1, double v2, double v3) {
        List<Double> vs = new ArrayList<>();
        for (double v : new double[]{v1, v2, v3}) if (v > 0) vs.add(v);
        if (vs.size() < 2) return -1;
        double mean = 0;
        for (double v : vs) mean += v;
        mean /= vs.size();
        return 100 * Math.max(Math.abs(Collections.max(vs) - mean), Math.abs(Collections.min(vs) - mean)) / mean;
    }

    /**
     * Regla propuesta (el handoff la deja por definir): Buena = FP ≥ 0.95 y desbalance de voltaje &lt; 2 %;
     * Regular = FP ≥ 0.90 y desbalance &lt; 3.5 %; si no, Mala. Sin FP no se califica.
     */
    static String quality(double pf, double v1, double v2, double v3) {
        if (pf <= 0) return null;
        double imb = Math.max(0, voltageImbalance(v1, v2, v3));
        if (pf >= 0.95 && imb < 2) return "Buena";
        if (pf >= 0.90 && imb < 3.5) return "Regular";
        return "Mala";
    }

    private static double avgNonZero(double... v) {
        double s = 0;
        int n = 0;
        for (double x : v) if (x > 0) { s += x; n++; }
        return n == 0 ? 0 : s / n;
    }

    // ───────────────────────── datos técnicos ─────────────────────────

    /** K5: series cada 5 min del medidor. */
    private void openTecnicos() {
        ((ClienteActivity) requireActivity()).push(ClienteTecnicosFragment.newInstance(idNode, name));
    }
}
