package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.data.EnergyLoader;
import de.kai_morich.simple_bluetooth_terminal.models.LocationEnergyPower;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsEnergyPowerResponse;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DarkChartView;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.DonutView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * K1b · Inicio del cliente (sólo kWh, sin tarifa): consumo ahora con dona y curva del día, consumo / solar / pico de hoy,
 * el mes en curso contra el mes anterior al mismo día y el aviso de medidores sin conexión.
 */
public class ClienteInicioFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L;
    private static final Locale ES = new Locale("es", "MX");

    private ClienteViewModel vm;
    private View content, cardAlert, locationPicker, cardSolarToday;
    private TextView tvOffline, tvHola, tvLocation, tvLocCount, btnChange, tvEmpty, tvHeroKw, tvLegSolar, tvLegCfe;
    private TextView tvToday, tvDelta, tvSolarToday, tvSolarShare, tvPeak, tvPeakAt, tvAlertTitle, tvAlertSub;
    private TextView tvMonthTitle, tvMonthCons, tvMonthSolar, tvVsLabel, tvVsValue, tvBarCur, tvBarPrev;
    private View barCurSolar, barCurCfe, barPrevSolar, barPrevCfe, barCurTrack, barPrevTrack;
    private DonutView donut;
    private DarkChartView heroChart;

    // Último flujo recibido (totales por localidad)
    private boolean hasData, hasPanels, hasCfe;
    private double solarKw, cfeKw, factoryKw, solarWh, cfeWh;
    private long lastOk;
    private List<NodeTrafficLight> nodes = new ArrayList<>();
    // Curvas del día (kW por hora)
    private double[] consToday, solarToday, consYesterday;
    private double peakKw = -1;
    private int peakHour = -1;
    // Mes en curso y mes anterior al mismo día
    private double monthCons = -1, monthSolar, prevCons, prevSolar;
    private boolean extraLoading, shownOnce, chartDrawn, monthAnimated;
    private int gen; // descarta respuestas de otra localidad

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
        View v = inflater.inflate(R.layout.fragment_cliente_inicio, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        content = v.findViewById(R.id.content);
        cardAlert = v.findViewById(R.id.cardAlert);
        cardSolarToday = v.findViewById(R.id.cardSolarToday);
        locationPicker = v.findViewById(R.id.locationPicker);
        tvOffline = v.findViewById(R.id.tvOffline);
        tvHola = v.findViewById(R.id.tvHola);
        tvLocation = v.findViewById(R.id.tvLocation);
        tvLocCount = v.findViewById(R.id.tvLocCount);
        btnChange = v.findViewById(R.id.btnChange);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvHeroKw = v.findViewById(R.id.tvHeroKw);
        tvLegSolar = v.findViewById(R.id.tvLegSolar);
        tvLegCfe = v.findViewById(R.id.tvLegCfe);
        tvToday = v.findViewById(R.id.tvToday);
        tvDelta = v.findViewById(R.id.tvDelta);
        tvSolarToday = v.findViewById(R.id.tvSolarToday);
        tvSolarShare = v.findViewById(R.id.tvSolarShare);
        tvPeak = v.findViewById(R.id.tvPeak);
        tvPeakAt = v.findViewById(R.id.tvPeakAt);
        tvAlertTitle = v.findViewById(R.id.tvAlertTitle);
        tvAlertSub = v.findViewById(R.id.tvAlertSub);
        tvMonthTitle = v.findViewById(R.id.tvMonthTitle);
        tvMonthCons = v.findViewById(R.id.tvMonthCons);
        tvMonthSolar = v.findViewById(R.id.tvMonthSolar);
        tvVsLabel = v.findViewById(R.id.tvVsLabel);
        tvVsValue = v.findViewById(R.id.tvVsValue);
        tvBarCur = v.findViewById(R.id.tvBarCur);
        tvBarPrev = v.findViewById(R.id.tvBarPrev);
        barCurSolar = v.findViewById(R.id.barCurSolar);
        barCurCfe = v.findViewById(R.id.barCurCfe);
        barPrevSolar = v.findViewById(R.id.barPrevSolar);
        barPrevCfe = v.findViewById(R.id.barPrevCfe);
        barCurTrack = v.findViewById(R.id.barCurTrack);
        barPrevTrack = v.findViewById(R.id.barPrevTrack);
        donut = v.findViewById(R.id.donut);
        heroChart = v.findViewById(R.id.heroChart);
        heroChart.setNames("Consumo", "Solar", "Ayer");

        Anim.pulse(v.findViewById(R.id.dotLive));
        Anim.pressable(cardAlert);
        Anim.pressable(v.findViewById(R.id.btnDetail));
        cardAlert.setOnClickListener(x -> ((ClienteActivity) requireActivity()).selectTab(2));
        v.findViewById(R.id.btnDetail).setOnClickListener(x -> ((ClienteActivity) requireActivity()).selectTab(1));
        Anim.pressable(locationPicker);
        locationPicker.setOnClickListener(x -> pickLocation());

        vm.customerName.observe(getViewLifecycleOwner(), n -> tvHola.setText(n != null ? "Hola, " + n : "Hola"));
        vm.locations.observe(getViewLifecycleOwner(), l -> paintLocation());
        vm.loaded.observe(getViewLifecycleOwner(), x -> showEmptyIfNeeded());
        // Cambiar de localidad reinicia todo y vuelve a consultar
        vm.location.observe(getViewLifecycleOwner(), l -> {
            gen++;
            hasData = false;
            nodes = new ArrayList<>();
            consToday = solarToday = consYesterday = null;
            peakKw = -1;
            monthCons = -1;
            chartDrawn = monthAnimated = false;
            paintLocation();
            render();
            load();
        });
        render();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, REFRESH_MS); // la carga inmediata la hace el observer / el primer arranque
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void pickLocation() {
        LocalidadSheet.show(requireContext(), vm.locations.getValue(), vm.locationId(), vm::select);
    }

    /** "LOCALIDAD · 1 DE 3" y el botón Cambiar sólo si hay más de una. */
    private void paintLocation() {
        if (getView() == null) return;
        LocationsViewModel cur = vm.location.getValue();
        List<LocationsViewModel> all = vm.locations.getValue();
        int n = all != null ? all.size() : 0;
        tvLocation.setText(cur != null ? cur.getName() : "—");
        int idx = cur == null || all == null ? -1 : all.indexOf(cur);
        tvLocCount.setText(n > 1 && idx >= 0 ? "LOCALIDAD · " + (idx + 1) + " DE " + n : "LOCALIDAD");
        btnChange.setVisibility(n > 1 ? View.VISIBLE : View.GONE);
        locationPicker.setClickable(n > 1);
    }

    private void showEmptyIfNeeded() {
        boolean empty = Boolean.TRUE.equals(vm.loaded.getValue()) && vm.location.getValue() == null;
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        content.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    // ───────────────────────── datos ─────────────────────────

    private void load() {
        if (vm.customerId == null || vm.location.getValue() == null) return;
        final int g = gen;
        final Integer loc = vm.locationId();
        tvEmpty.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);

        // Totales por localidad (Sol, CFE, consumo y energía del día)
        vm.api().getLocationsEnergyPowerTotals(vm.customerId).enqueue(new Callback<LocationsEnergyPowerResponse>() {
            @Override public void onResponse(Call<LocationsEnergyPowerResponse> c, Response<LocationsEnergyPowerResponse> r) {
                if (!isAdded() || g != gen) return;
                if (!r.isSuccessful() || r.body() == null || r.body().getLocations() == null) { offline(); return; }
                LocationEnergyPower d = null;
                for (LocationEnergyPower l : r.body().getLocations()) {
                    if (l.getIdLocation() != null && l.getIdLocation().equals(loc)) { d = l; break; }
                }
                tvOffline.setVisibility(View.GONE);
                if (d == null) { // la localidad no devolvió datos
                    tvEmpty.setVisibility(View.VISIBLE);
                    content.setVisibility(View.GONE);
                    return;
                }
                hasPanels = d.getHasPanels();
                hasCfe = d.getHasCfe();
                solarKw = d.getPanelsW() / 1000.0;
                cfeKw = d.getCfeW() / 1000.0;
                factoryKw = d.getLocalityW() / 1000.0;
                solarWh = d.getPanelsWhDay();
                cfeWh = d.getCfeWhDay();
                hasData = true;
                lastOk = System.currentTimeMillis();
                render();
                loadExtras(g);
            }
            @Override public void onFailure(Call<LocationsEnergyPowerResponse> c, Throwable t) {
                if (isAdded() && g == gen) offline();
            }
        });

        vm.fetchNodes(list -> {
            if (!isAdded() || g != gen || list == null) return;
            nodes = list;
            render();
        });
    }

    /** Curva de hoy, "vs ayer" (misma hora) y el mes contra el anterior al mismo día. */
    private void loadExtras(final int g) {
        if (extraLoading || vm.locationId() == null) return;
        extraLoading = true;
        final int loc = vm.locationId();
        final Calendar now = Calendar.getInstance();
        final int hour = now.get(Calendar.HOUR_OF_DAY);
        Calendar y = (Calendar) now.clone();
        y.add(Calendar.DAY_OF_MONTH, -1);

        final int dom = now.get(Calendar.DAY_OF_MONTH);
        final Calendar monthStart = (Calendar) now.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);
        monthStart.set(Calendar.HOUR_OF_DAY, 12);
        final Calendar prevStart = (Calendar) monthStart.clone();
        prevStart.add(Calendar.MONTH, -1);
        final int prevDays = Math.min(dom, prevStart.getActualMaximum(Calendar.DAY_OF_MONTH));

        final double[][] sums = new double[2][]; // [mes actual: solar, consumo], [anterior: solar, consumo]
        final int[] pending = {4};
        final Runnable done = () -> {
            if (--pending[0] > 0) return;
            extraLoading = false;
            if (!isAdded() || g != gen) return;
            if (consToday != null) { // pico: la hora de mayor consumo de hoy (algunas series llegan en W)
                double f = EnergyLoader.unitFactor(consToday, factoryKw);
                consToday = EnergyLoader.scale(consToday, f);
                solarToday = EnergyLoader.scale(solarToday, f);
                consYesterday = EnergyLoader.scale(consYesterday, f);
                peakKw = 0;
                for (int h = 0; h <= hour; h++) if (consToday[h] > peakKw) { peakKw = consToday[h]; peakHour = h; }
            }
            if (sums[0] != null) { monthSolar = sums[0][0]; monthCons = sums[0][1]; }
            if (sums[1] != null) { prevSolar = sums[1][0]; prevCons = sums[1][1]; }
            render();
        };
        EnergyLoader.dayFull(vm.api(), vm.customerId, loc, EnergyLoader.ymd(now), (s, gr, co, inj) -> {
            if (co != null) { consToday = co; solarToday = s; }
            done.run();
        });
        EnergyLoader.dayFull(vm.api(), vm.customerId, loc, EnergyLoader.ymd(y), (s, gr, co, inj) -> {
            consYesterday = co;
            done.run();
        });
        EnergyLoader.days(vm.api(), vm.customerId, loc, monthStart, dom, (s, gr, co) -> {
            if (co != null) sums[0] = new double[]{sum(s), sum(co)};
            done.run();
        });
        EnergyLoader.days(vm.api(), vm.customerId, loc, prevStart, prevDays, (s, gr, co) -> {
            if (co != null) sums[1] = new double[]{sum(s), sum(co)};
            done.run();
        });
    }

    private static double sum(double[] v) {
        double t = 0;
        for (double x : v) t += x;
        return t;
    }

    private void offline() {
        tvOffline.setText(lastOk > 0 ? "Sin conexión · mostrando datos de " + Fmt.hhmm(lastOk) : "Sin conexión");
        tvOffline.setVisibility(View.VISIBLE);
    }

    // ───────────────────────── render ─────────────────────────

    private void render() {
        if (getView() == null) return;
        showEmptyIfNeeded();
        if (!hasData) {
            tvHeroKw.setText("--");
            return;
        }
        // Hero: consumo ahora, dona y leyenda
        Anim.countUp(tvHeroKw, factoryKw, v -> Fmt.withUnit(requireContext(), Fmt.n0(v), " kW", 16f / 40f));
        double solar = hasPanels ? solarKw : 0, grid = hasCfe ? cfeKw : 0, src = solar + grid;
        donut.set(src > 0.05 ? (float) (100 * solar / src) : 0f);
        tvLegSolar.setText(legend("Sol ", solar, 0xFFF2B01E));
        tvLegCfe.setText(legend("CFE ", grid, ContextCompat.getColor(requireContext(), R.color.am_cfe_on_dark)));

        // Curva del día: consumo (línea) y solar (área) hasta la hora actual
        if (consToday != null) {
            int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            double[] c = Arrays.copyOf(consToday, hour + 1);
            double[] s = solarToday != null && hasPanels ? Arrays.copyOf(solarToday, hour + 1) : null;
            String label = peakKw > 0 ? "pico " + Fmt.n0(peakKw) : null;
            if (chartDrawn) heroChart.update(c, s, null, hour, label); else { heroChart.set(c, s, null, hour, label); chartDrawn = true; }
        }

        // Consumo hoy / solar hoy / pico
        double todayKwh = (solarWh + cfeWh) / 1000.0;
        Anim.countUp(tvToday, todayKwh, v -> Fmt.n0(v));
        cardSolarToday.setVisibility(hasPanels ? View.VISIBLE : View.GONE); // sin solar instalado se oculta
        Anim.countUp(tvSolarToday, solarWh / 1000.0, v -> Fmt.n0(v));
        tvSolarShare.setText(todayKwh > 0 ? Math.round(100 * (solarWh / 1000.0) / todayKwh) + "% del total" : "—");
        paintDelta();
        if (peakKw >= 0) {
            Anim.countUp(tvPeak, peakKw, v -> Fmt.n0(v));
            tvPeakAt.setText(String.format(Locale.US, "%02d:00", peakHour));
        } else {
            tvPeak.setText("--");
            tvPeakAt.setText(" ");
        }

        paintMonth();

        // Aviso de medidores sin conexión
        int off = 0;
        boolean solarOff = false;
        for (NodeTrafficLight n : nodes) {
            if (!n.isGreen()) {
                off++;
                if (NodeKind.of(n) == NodeKind.SOLAR) solarOff = true;
            }
        }
        cardAlert.setVisibility(off > 0 ? View.VISIBLE : View.GONE);
        if (off > 0) {
            tvAlertTitle.setText(off == 1 ? "1 medidor sin conexión" : off + " medidores sin conexión");
            tvAlertSub.setText(solarOff ? "Los datos solares pueden estar incompletos." : "Los datos pueden estar incompletos.");
        }

        if (!shownOnce) { // primera vez con datos: las tarjetas entran una tras otra
            shownOnce = true;
            Anim.staggerIn((ViewGroup) content);
        }
    }

    /** "↓ 4% vs ayer": hasta la misma hora de cada día; verde si baja, rojo si sube. */
    private void paintDelta() {
        if (consToday == null || consYesterday == null) { tvDelta.setText(" "); return; }
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        double a = 0, b = 0;
        for (int h = 0; h <= hour; h++) { a += consToday[h]; b += consYesterday[h]; }
        if (b <= 0) { tvDelta.setText(" "); return; }
        long r = Math.round(100 * (a - b) / b);
        tvDelta.setText(r == 0 ? "= igual que ayer" : (r < 0 ? "↓ " : "↑ ") + Math.abs(r) + "% vs ayer");
        tvDelta.setTextColor(ContextCompat.getColor(requireContext(), r <= 0 ? R.color.am_ok_text : R.color.am_err_text));
    }

    /** Mes en curso: consumo y solar acumulados, comparación con el mes anterior al mismo día y barras proporcionales. */
    private void paintMonth() {
        Calendar now = Calendar.getInstance();
        Calendar prev = (Calendar) now.clone();
        prev.set(Calendar.DAY_OF_MONTH, 1);
        prev.add(Calendar.MONTH, -1);
        String month = cap(new SimpleDateFormat("MMMM", ES).format(now.getTime()));
        tvMonthTitle.setText(month + ", al día " + now.get(Calendar.DAY_OF_MONTH));
        tvBarCur.setText(cap(new SimpleDateFormat("MMM", ES).format(now.getTime()).replace(".", "")));
        tvBarPrev.setText(cap(new SimpleDateFormat("MMM", ES).format(prev.getTime()).replace(".", "")));
        tvVsLabel.setText("vs " + new SimpleDateFormat("MMMM", ES).format(prev.getTime()) + " al mismo día");
        if (monthCons < 0) return;

        Anim.countUp(tvMonthCons, monthCons, v -> Fmt.withUnit(requireContext(), Fmt.n0(v), " kWh", 14f / 26f));
        Anim.countUp(tvMonthSolar, monthSolar, v -> Fmt.withUnit(requireContext(), Fmt.n0(v), " kWh", 11f / 18f));
        if (prevCons > 0) {
            double diff = monthCons - prevCons;
            long pct = Math.round(100 * diff / prevCons);
            tvVsValue.setText((pct == 0 ? "= " : diff < 0 ? "↓ " : "↑ ") + Math.abs(pct) + "% · " + (diff < 0 ? "−" : "+") + Fmt.n0(Math.abs(diff)) + " kWh");
            tvVsValue.setTextColor(ContextCompat.getColor(requireContext(), diff <= 0 ? R.color.am_ok_text : R.color.am_err_text));
        } else {
            tvVsValue.setText("—");
        }
        double max = Math.max(monthCons, prevCons);
        if (max <= 0) return;
        setWeight(barCurSolar, (float) (monthSolar / max));
        setWeight(barCurCfe, (float) (Math.max(0, monthCons - monthSolar) / max));
        setWeight(barPrevSolar, (float) (prevSolar / max));
        setWeight(barPrevCfe, (float) (Math.max(0, prevCons - prevSolar) / max));
        if (!monthAnimated) { // las barras del mes crecen la primera vez
            monthAnimated = true;
            barCurSolar.setPivotX(0f);
            Anim.growX(barCurTrack, 250);
            Anim.growX(barPrevTrack, 380);
        }
    }

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1); }

    private CharSequence legend(String label, double kw, int dotColor) {
        SpannableString s = new SpannableString("● " + label + Fmt.n0(kw));
        s.setSpan(new ForegroundColorSpan(dotColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }
}
