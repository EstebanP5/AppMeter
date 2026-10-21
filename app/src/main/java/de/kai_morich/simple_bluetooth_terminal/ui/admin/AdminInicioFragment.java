package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.data.LiveLoader;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.LocationEnergyPower;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsEnergyPowerResponse;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DarkChartView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A1 · Inicio (admin): consumo de la planta ahora, flujo CFE → Planta ← Solar (más batería e inyección si hay), curva del
 * día contra ayer, tres datos del día y "quién consume ahora" con las cargas de la localidad.
 */
public class AdminInicioFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L;

    private ApiService api;
    private Integer customerId, locationId;
    private String customerName, locationName;

    private View content, cardLoads, rowExtras;
    private TextView tvLive, tvCtxAvatar, tvCtxCustomer, tvCtxLocation, tvOffline, tvEmpty, tvVsY, tvNow;
    private TextView tvFlowCfe, tvFlowPlant, tvFlowSolar, tvLblCfe, tvLblPlant, tvLblSolar, chipBattery, chipInjection;
    private TextView tvEnergy, tvEnergySub, tvPeak, tvPeakSub, tvFp, tvFpSub, tvLoadsBadge, tvLoadsFoot;
    private View boxCfe, boxPlant, boxSolar;
    private LinearLayout loadsList;
    private DarkChartView chart;

    // Datos
    private boolean hasData, hasPanels, hasCfe, shownOnce, chartDrawn;
    private double solarKw, cfeKw, factoryKw, injectionKw, solarWh, cfeWh;
    private long lastOk;
    private List<NodeTrafficLight> nodes = new ArrayList<>();
    private Map<Integer, DeviceCurrent> devices = Collections.emptyMap();
    private LiveLoader.BatteryInfo battery = new LiveLoader.BatteryInfo();
    private double[] consToday, solarToday, consYesterday;
    private boolean curvesLoading;
    private int gen;

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
        View v = inflater.inflate(R.layout.fragment_admin_inicio, container, false);
        api = ApiClient.getApiService();
        content = v.findViewById(R.id.content);
        cardLoads = v.findViewById(R.id.cardLoads);
        rowExtras = v.findViewById(R.id.rowExtras);
        tvLive = v.findViewById(R.id.tvLive);
        tvCtxAvatar = v.findViewById(R.id.tvCtxAvatar);
        tvCtxCustomer = v.findViewById(R.id.tvCtxCustomer);
        tvCtxLocation = v.findViewById(R.id.tvCtxLocation);
        tvOffline = v.findViewById(R.id.tvOffline);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvVsY = v.findViewById(R.id.tvVsY);
        tvNow = v.findViewById(R.id.tvNow);
        tvFlowCfe = v.findViewById(R.id.tvFlowCfe);
        tvFlowPlant = v.findViewById(R.id.tvFlowPlant);
        tvFlowSolar = v.findViewById(R.id.tvFlowSolar);
        tvLblCfe = v.findViewById(R.id.tvLblCfe);
        tvLblPlant = v.findViewById(R.id.tvLblPlant);
        tvLblSolar = v.findViewById(R.id.tvLblSolar);
        boxCfe = v.findViewById(R.id.boxCfe);
        boxPlant = v.findViewById(R.id.boxPlant);
        boxSolar = v.findViewById(R.id.boxSolar);
        chipBattery = v.findViewById(R.id.chipBattery);
        chipInjection = v.findViewById(R.id.chipInjection);
        tvEnergy = v.findViewById(R.id.tvEnergy);
        tvEnergySub = v.findViewById(R.id.tvEnergySub);
        tvPeak = v.findViewById(R.id.tvPeak);
        tvPeakSub = v.findViewById(R.id.tvPeakSub);
        tvFp = v.findViewById(R.id.tvFp);
        tvFpSub = v.findViewById(R.id.tvFpSub);
        tvLoadsBadge = v.findViewById(R.id.tvLoadsBadge);
        tvLoadsFoot = v.findViewById(R.id.tvLoadsFoot);
        loadsList = v.findViewById(R.id.loadsList);
        chart = v.findViewById(R.id.chart);
        ((TextView) v.findViewById(R.id.tvHeaderTitle)).setText("Inicio");
        chart.setLight(true);
        chart.setYLabels(true);
        chart.setNames("Consumo", "Solar", "Ayer");

        customerId = ContextPicker.customerId(requireContext());
        locationId = ContextPicker.locationId(requireContext());
        customerName = ContextPicker.customerName(requireContext());
        locationName = ContextPicker.locationName(requireContext());
        paintContext();
        paintBoxes();

        v.findViewById(R.id.btnLogout).setOnClickListener(x -> ((MainActivity) requireActivity()).confirmLogout());
        v.findViewById(R.id.ctxCard).setOnClickListener(x -> ContextPicker.show(requireActivity(), (cid, cname, lid, lname, locs) -> {
            ContextPicker.save(requireContext(), cid, cname, lid, lname);
            customerId = cid;
            customerName = cname;
            locationId = lid;
            locationName = lname;
            resetData();
            paintContext();
            load();
        }));
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

    @Override public void onDestroyView() {
        super.onDestroyView();
        handler.removeCallbacks(tick);
        gen++;
    }

    private void resetData() {
        gen++;
        hasData = false;
        chartDrawn = false;
        lastOk = 0;
        curvesLoading = false;
        nodes = new ArrayList<>();
        devices = Collections.emptyMap();
        battery = new LiveLoader.BatteryInfo();
        consToday = solarToday = consYesterday = null;
        tvOffline.setVisibility(View.GONE);
        render();
    }

    private void paintContext() {
        tvCtxCustomer.setText(customerName != null ? customerName : "Selecciona cliente");
        tvCtxLocation.setText(locationName != null ? locationName : "—");
        tvCtxAvatar.setText(ContextPicker.initials(customerName));
    }

    // ───────────────────────── datos ─────────────────────────

    private void load() {
        if (customerId == null || locationId == null) {
            ContextPicker.ensure(requireContext(), api, (cid, cname, lid, lname) -> {
                if (!isAdded()) return;
                customerId = cid;
                customerName = cname;
                locationId = lid;
                locationName = lname;
                paintContext();
                load();
            });
            return;
        }
        final int g = gen;
        final int cust = customerId, loc = locationId;

        api.getLocationsEnergyPowerTotals(cust).enqueue(new Callback<LocationsEnergyPowerResponse>() {
            @Override public void onResponse(Call<LocationsEnergyPowerResponse> c, Response<LocationsEnergyPowerResponse> r) {
                if (!isAdded() || g != gen) return;
                if (!r.isSuccessful() || r.body() == null || r.body().getLocations() == null) { offline(); return; }
                LocationEnergyPower d = null;
                for (LocationEnergyPower l : r.body().getLocations()) {
                    if (l.getIdLocation() != null && l.getIdLocation() == loc) { d = l; break; }
                }
                if (d == null) { hasData = false; render(); return; }
                hasPanels = d.getHasPanels();
                hasCfe = d.getHasCfe();
                solarKw = d.getPanelsW() / 1000.0;
                cfeKw = d.getCfeW() / 1000.0;
                factoryKw = d.getLocalityW() / 1000.0;
                injectionKw = d.getInjectionW() / 1000.0;
                solarWh = d.getPanelsWhDay();
                cfeWh = d.getCfeWhDay();
                hasData = true;
                lastOk = System.currentTimeMillis();
                tvOffline.setVisibility(View.GONE);
                render();
                loadCurves(g, cust, loc);
            }
            @Override public void onFailure(Call<LocationsEnergyPowerResponse> c, Throwable t) { if (isAdded() && g == gen) offline(); }
        });

        api.getCustomerNodesTrafficLight(cust).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                if (!isAdded() || g != gen || !r.isSuccessful() || r.body() == null || r.body().getNodes() == null) return;
                List<NodeTrafficLight> list = new ArrayList<>();
                for (NodeTrafficLight n : r.body().getNodes()) {
                    if (n.getIdLocation() == null || n.getIdLocation() == loc) list.add(n);
                }
                nodes = list;
                render();
                LiveLoader.devices(api, cust, list, map -> {
                    if (!isAdded() || g != gen) return;
                    devices = map;
                    render();
                });
                loadBattery(g, cust, loc, list);
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { }
        });
    }

    /** Circuitos de la localidad (los del admin) más los de sus nodos; en cada uno getEnergyFlowGraph dice si hay batería. */
    private void loadBattery(final int g, final int cust, final int loc, final List<NodeTrafficLight> list) {
        final java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (NodeTrafficLight n : list) if (n.getIdCircuit() != null) ids.add(n.getIdCircuit());
        api.getCustomerCircuitsForAdmin(cust).enqueue(new Callback<List<de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel>>() {
            @Override public void onResponse(Call<List<de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel>> c,
                                             Response<List<de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    for (de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel cv : r.body()) {
                        if (cv.getIdCircuit() != null && cv.getIdLocation() != null && cv.getIdLocation() == loc) ids.add(cv.getIdCircuit());
                    }
                }
                run();
            }
            @Override public void onFailure(Call<List<de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel>> c, Throwable t) { run(); }
            private void run() {
                if (!isAdded() || g != gen) return;
                LiveLoader.battery(api, cust, ids, info -> {
                    if (!isAdded() || g != gen) return;
                    battery = info;
                    render();
                });
            }
        });
    }

    /** Curva de hoy y de ayer (kW por hora). Algunas series llegan en W: se normalizan con el consumo actual. */
    private void loadCurves(final int g, int cust, int loc) {
        if (curvesLoading) return;
        curvesLoading = true;
        Calendar now = Calendar.getInstance(), y = (Calendar) now.clone();
        y.add(Calendar.DAY_OF_MONTH, -1);
        final double[][] box = new double[3][];
        final int[] pending = {2};
        Runnable done = () -> {
            if (--pending[0] > 0) return;
            curvesLoading = false;
            if (!isAdded() || g != gen) return;
            double f = EnergyLoader.unitFactor(box[0], factoryKw);
            consToday = EnergyLoader.scale(box[0], f);
            solarToday = EnergyLoader.scale(box[1], f);
            consYesterday = EnergyLoader.scale(box[2], f);
            render();
        };
        EnergyLoader.dayFull(api, cust, loc, EnergyLoader.ymd(now), (s, gr, co, inj) -> { box[0] = co; box[1] = s; done.run(); });
        EnergyLoader.dayFull(api, cust, loc, EnergyLoader.ymd(y), (s, gr, co, inj) -> { box[2] = co; done.run(); });
    }

    private void offline() {
        tvOffline.setText(lastOk > 0 ? "Sin conexión · mostrando último dato de " + Fmt.hhmm(lastOk) : "Sin conexión");
        tvOffline.setVisibility(View.VISIBLE);
    }

    // ───────────────────────── pintar ─────────────────────────

    private void paintBoxes() {
        box(boxCfe, 0xFFE8F0FF);
        box(boxPlant, 0xFF161616);
        box(boxSolar, 0xFFF0F0F0);
        int blue = ContextCompat.getColor(requireContext(), R.color.am_cfe);
        tvLblCfe.setTextColor(blue);
        tvLblPlant.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_accent));
        tvFlowPlant.setTextColor(0xFFFFFFFF);
    }

    private void box(View v, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(10 * getResources().getDisplayMetrics().density);
        v.setBackground(g);
    }

    private void render() {
        if (getView() == null) return;
        paintContext();
        tvEmpty.setVisibility(customerId == null ? View.VISIBLE : View.GONE);
        if (!hasData) {
            tvNow.setText("--");
            tvLive.setText("");
            return;
        }
        tvLive.setText("● en vivo · " + Fmt.hhmm(lastOk));
        Anim.countUp(tvNow, factoryKw, v -> Fmt.withUnit(requireContext(), Fmt.n1(v), " kW", 18f / 42f));

        // Flujo
        tvFlowCfe.setText(hasCfe ? Fmt.n1(cfeKw) : "—");
        tvFlowPlant.setText(Fmt.n1(factoryKw));
        tvFlowSolar.setText(hasPanels ? Fmt.n1(solarKw) : "—");
        boolean solarOn = hasPanels && solarKw > 0.05;
        box(boxSolar, solarOn ? 0xFFFFF3D6 : 0xFFF0F0F0);
        tvLblSolar.setTextColor(solarOn ? 0xFFB07800 : 0xFF999999);
        tvFlowSolar.setTextColor(solarOn ? 0xFF161616 : 0xFF999999);

        // Batería e inyección (la batería sale de getEnergyFlowGraph; los totales por localidad no la traen)
        boolean inj = injectionKw > 0.05;
        chipBattery.setVisibility(battery.has ? View.VISIBLE : View.GONE);
        if (battery.has) {
            String st = battery.charging ? "cargando" : battery.discharging ? "descargando" : "en reposo";
            chipBattery.setText("Batería " + (battery.watts >= 1000 ? Fmt.n1(battery.watts / 1000.0) + " kW" : Fmt.n0(battery.watts) + " W") + " · " + st);
        }
        chipInjection.setVisibility(inj ? View.VISIBLE : View.GONE);
        if (inj) chipInjection.setText("Inyectando " + Fmt.n1(injectionKw) + " kW");
        rowExtras.setVisibility(battery.has || inj ? View.VISIBLE : View.GONE);

        // Curva, pico y "vs ayer"
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        double peak = -1;
        int peakAt = 0;
        if (consToday != null) {
            double sumT = 0, sumY = 0;
            for (int h = 0; h <= hour; h++) {
                sumT += consToday[h];
                if (consYesterday != null) sumY += consYesterday[h];
                if (consToday[h] > peak) { peak = consToday[h]; peakAt = h; }
            }
            double[] c = Arrays.copyOf(consToday, hour + 1);
            double[] s = solarToday != null && hasPanels ? Arrays.copyOf(solarToday, hour + 1) : null;
            String label = peak > 0 ? "pico " + Fmt.n0(peak) : null;
            if (chartDrawn) chart.update(c, s, consYesterday, hour, label); else { chart.set(c, s, consYesterday, hour, label); chartDrawn = true; }
            paintDelta(sumY > 0 ? Math.round(100 * (sumT - sumY) / sumY) : null);
        } else {
            paintDelta(null);
        }

        // Datos del día
        double kwh = (solarWh + cfeWh) / 1000.0;
        Anim.countUp(tvEnergy, kwh, v -> Fmt.n0(v));
        tvEnergySub.setText("kWh" + (deltaPct != null ? " · " + arrow(deltaPct) + Math.abs(deltaPct) + "%" : ""));
        if (peak >= 0) {
            Anim.countUp(tvPeak, peak, v -> Fmt.n0(v));
            tvPeakSub.setText(String.format(Locale.US, "kW · %02d:00", peakAt));
        } else {
            tvPeak.setText("--");
            tvPeakSub.setText("kW");
        }
        paintFp();
        paintLoads();

        if (!shownOnce) {
            shownOnce = true;
            Anim.staggerIn((ViewGroup) content);
        }
    }

    private Long deltaPct;

    private void paintDelta(Long pct) {
        deltaPct = pct;
        if (pct == null) { tvVsY.setVisibility(View.GONE); return; }
        tvVsY.setVisibility(View.VISIBLE);
        tvVsY.setText(pct == 0 ? "= igual que ayer" : arrow(pct) + Math.abs(pct) + "% vs ayer");
        boolean down = pct <= 0;
        tvVsY.setBackgroundResource(down ? R.drawable.am_badge_ok : R.drawable.am_badge_err);
        tvVsY.setTextColor(ContextCompat.getColor(requireContext(), down ? R.color.am_ok_text : R.color.am_err_text));
    }

    private static String arrow(long pct) { return pct < 0 ? "↓ " : "↑ "; }

    /** FP de la planta: promedio de las fases del medidor de acometida (CFE). */
    private void paintFp() {
        double sum = 0;
        int n = 0;
        for (NodeTrafficLight node : nodes) {
            if (NodeKind.of(node) != NodeKind.CFE) continue;
            DeviceCurrent d = devices.get(node.getIdNode());
            if (d == null) continue;
            for (double pf : new double[]{d.getPf1(), d.getPf2(), d.getPf3()}) if (pf != 0) { sum += Math.abs(pf); n++; }
        }
        if (n == 0) { tvFp.setText("--"); tvFp.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_ink)); return; }
        double pf = sum / n;
        tvFp.setText(String.format(Locale.US, "%.2f", pf));
        tvFp.setTextColor(ContextCompat.getColor(requireContext(), pf >= 0.95 ? R.color.am_ok_text : pf >= 0.9 ? R.color.am_virtual : R.color.am_err_text));
    }

    /** Las cargas ordenadas de mayor a menor consumo, con su barra relativa a la mayor. */
    private void paintLoads() {
        List<NodeTrafficLight> loads = new ArrayList<>();
        double cfeNodeKw = 0;
        for (NodeTrafficLight n : nodes) {
            int kind = NodeKind.of(n);
            if (kind == NodeKind.OTHER) loads.add(n);
            else if (kind == NodeKind.CFE && devices.containsKey(n.getIdNode())) cfeNodeKw += Math.abs(LiveLoader.kw(devices.get(n.getIdNode())));
        }
        cardLoads.setVisibility(loads.isEmpty() ? View.GONE : View.VISIBLE);
        if (loads.isEmpty()) return;
        final double[] kw = new double[loads.size()];
        for (int i = 0; i < kw.length; i++) kw[i] = Math.abs(LiveLoader.kw(devices.get(loads.get(i).getIdNode())));
        Integer[] order = new Integer[kw.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> Double.compare(kw[b], kw[a]));

        int on = 0;
        double sum = 0, max = 0;
        for (int i = 0; i < kw.length; i++) { sum += kw[i]; max = Math.max(max, kw[i]); if (loads.get(i).isGreen()) on++; }
        boolean all = on == loads.size();
        tvLoadsBadge.setText(on + "/" + loads.size() + " en línea");
        tvLoadsBadge.setBackgroundResource(all ? R.drawable.am_badge_ok : R.drawable.am_badge_err);
        tvLoadsBadge.setTextColor(ContextCompat.getColor(requireContext(), all ? R.color.am_ok_text : R.color.am_err_text));

        loadsList.removeAllViews();
        for (int idx : order) {
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_load_bar, loadsList, false);
            double share = max > 0 ? kw[idx] / max : 0;
            ((TextView) row.findViewById(R.id.tvName)).setText(loads.get(idx).getName());
            ((TextView) row.findViewById(R.id.tvVal)).setText(Fmt.n0(kw[idx]) + " kW");
            View fill = row.findViewById(R.id.fill), rest = row.findViewById(R.id.rest);
            setWeight(fill, (float) share);
            setWeight(rest, (float) (1 - share));
            int color = share > 0.6 ? 0xFF161616 : share > 0.3 ? 0xFF555555 : 0xFF8A8A8A;
            fill.setBackgroundTintList(ColorStateList.valueOf(color));
            loadsList.addView(row);
        }
        tvLoadsFoot.setText("Suma de cargas " + Fmt.n0(sum) + " kW" + (cfeNodeKw > 0 ? " · acometida " + Fmt.n1(cfeNodeKw) + " kW" : ""));
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }
}
