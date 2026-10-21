package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.data.DeviceLookup;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.data.LiveLoader;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DarkChartView;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.PhaseTableView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;

/** A3 · Nodo · Ahora: lectura en vivo (potencia, energía de hoy, FP), tabla por fase, desbalances y la curva de hoy. */
public class NodoAhoraFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L;

    private ApiService api;
    private int idNode, idCustomer, idCircuit, type;
    private String name, serie;
    private boolean solar, shownOnce, chartDrawn;

    private DeviceCurrent data;
    private double[] todayKw;
    private double nodeEnergyKwh = -1;

    private TextView tvPowerLabel, tvPower, tvEnergy, tvFp, tvImbA, tvImbV, tvMax, tvAvg, tvMin;
    private PhaseTableView phases;
    private DarkChartView chart;
    private View content;

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
        idNode = a.getInt("idMeasure");
        idCustomer = a.getInt("idCustomer");
        idCircuit = a.getInt("idCircuit");
        type = a.getInt("nodeType", -1);
        name = a.getString("nodeName");
        serie = a.getString("nodeSerie");
        solar = NodeKind.of(type, a.getString("nodeTypeName")) == NodeKind.SOLAR;
        api = ApiClient.getApiService();

        View v = inflater.inflate(R.layout.fragment_nodo_ahora, container, false);
        content = v.findViewById(R.id.content);
        tvPowerLabel = v.findViewById(R.id.tvPowerLabel);
        tvPower = v.findViewById(R.id.tvPower);
        tvEnergy = v.findViewById(R.id.tvEnergy);
        tvFp = v.findViewById(R.id.tvFp);
        tvImbA = v.findViewById(R.id.tvImbA);
        tvImbV = v.findViewById(R.id.tvImbV);
        tvMax = v.findViewById(R.id.tvMax);
        tvAvg = v.findViewById(R.id.tvAvg);
        tvMin = v.findViewById(R.id.tvMin);
        phases = v.findViewById(R.id.phases);
        chart = v.findViewById(R.id.chart);
        tvPowerLabel.setText(solar ? "Generación" : "Potencia");
        chart.setLight(true);
        chart.setYLabels(true);
        chart.setNames(solar ? "Generación" : "Potencia", "", "Ayer");
        v.findViewById(R.id.btnVerDia).setOnClickListener(x -> ((NodoActivity) requireActivity()).openTab(1));
        Anim.pressable(v.findViewById(R.id.btnVerDia));
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
        if (idCustomer == 0 || idNode == 0) return;
        DeviceLookup.find(api, idCustomer, idCircuit, idNode, type, serie, name, null, d -> {
            if (!isAdded() || d == null) return;
            data = d;
            render();
        });
        NodeTrafficLight n = new NodeTrafficLight();
        n.setFields(idNode, name, null, idCircuit, 0, null, null, "Green", -1);
        List<NodeTrafficLight> one = new ArrayList<>();
        one.add(n);
        EnergyLoader.powerDay(api, idCustomer, one, EnergyLoader.ymd(Calendar.getInstance()), kw -> {
            if (!isAdded()) return;
            todayKw = kw;
            render();
        });
        EnergyLoader.nodeEnergyToday(api, idCustomer, idNode, EnergyLoader.ymd(Calendar.getInstance()), solar, kwh -> {
            if (!isAdded() || kwh == null) return;
            nodeEnergyKwh = kwh;
            render();
        });
    }

    // ───────────────────────── pintar ─────────────────────────

    private void render() {
        if (getView() == null) return;
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        // Energía de hoy: la lectura en vivo; si no hay, la suma de las horas
        double kwh = -1;
        if (data != null) {
            double wh = solar ? data.getGenWhDay() : data.getConWhDay();
            if (wh == 0) wh = solar ? data.getConWhDay() : data.getGenWhDay();
            if (wh != 0) kwh = Math.abs(wh) / 1000.0;
        }
        if (kwh < 0 && nodeEnergyKwh >= 0) kwh = nodeEnergyKwh;
        // Potencia: la lectura en vivo; si no, la hora actual de la curva
        double kw = -1;
        if (data != null) kw = Math.abs(LiveLoader.kw(data));
        if ((kw <= 0) && todayKw != null) kw = todayKw[hour];

        if (kw >= 0) Anim.countUp(tvPower, kw, v -> Fmt.withUnit(requireContext(), Fmt.n1(v), " kW", 14f / 30f));
        if (kwh >= 0) Anim.countUp(tvEnergy, kwh, v -> Fmt.n1(v));

        if (data != null) {
            double[] v = {data.getV1(), data.getV2(), data.getV3()};
            double[] am = {data.getI1(), data.getI2(), data.getI3()};
            double[] pf = {data.getPf1(), data.getPf2(), data.getPf3()};
            phases.set(v, am, pf);
            double avgPf = avgAbs(pf);
            tvFp.setText(avgPf > 0 ? String.format(Locale.US, "%.2f", avgPf) : "--");
            tvFp.setTextColor(ContextCompat.getColor(requireContext(), avgPf >= 0.95 ? R.color.am_ok_text : avgPf >= 0.9 ? R.color.am_virtual : R.color.am_err_text));
            paintImbalance(tvImbA, imbalance(am), 10, 20);
            paintImbalance(tvImbV, imbalance(v), 2, 3.5);
        }

        if (todayKw != null) {
            double sum = 0, max = -1, min = Double.MAX_VALUE;
            int n = 0;
            for (int h = 0; h <= hour; h++) {
                double v = todayKw[h];
                sum += v;
                n++;
                max = Math.max(max, v);
                if (v > 0) min = Math.min(min, v);
            }
            tvMax.setText("Máx " + Fmt.n1(max));
            tvAvg.setText("Prom " + Fmt.n1(sum / Math.max(1, n)));
            tvMin.setText(min == Double.MAX_VALUE ? "Mín —" : "Mín " + Fmt.n1(min));
            double[] up = Arrays.copyOf(todayKw, hour + 1);
            if (chartDrawn) chart.update(up, null, null, hour, null); else { chart.set(up, null, null, hour, null); chartDrawn = true; }
        }
        if (!shownOnce && (data != null || todayKw != null)) {
            shownOnce = true;
            Anim.staggerIn((ViewGroup) content);
        }
    }

    private static double avgAbs(double[] v) {
        double s = 0;
        int n = 0;
        for (double x : v) if (x != 0) { s += Math.abs(x); n++; }
        return n == 0 ? 0 : s / n;
    }

    /** Desbalance en %: mayor desvío respecto al promedio de las fases con valor; -1 si hay menos de dos. */
    private static double imbalance(double[] v) {
        double mean = 0;
        int n = 0;
        for (double x : v) if (x > 0) { mean += x; n++; }
        if (n < 2) return -1;
        mean /= n;
        double dev = 0;
        for (double x : v) if (x > 0) dev = Math.max(dev, Math.abs(x - mean));
        return 100 * dev / mean;
    }

    /** OK / Atención / Alto según los umbrales de cada magnitud (corriente 10 y 20 %, voltaje 2 y 3.5 %). */
    private void paintImbalance(TextView t, double pct, double warn, double bad) {
        if (pct < 0) { t.setText("—"); t.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_t777)); return; }
        String label = pct < warn ? "OK" : pct < bad ? "Atención" : "Alto";
        t.setText(String.format(Locale.US, "%.1f%% · %s", pct, label));
        t.setTextColor(ContextCompat.getColor(requireContext(), pct < warn ? R.color.am_ok_text : pct < bad ? R.color.am_virtual : R.color.am_err_text));
    }
}
