package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
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
import de.kai_morich.simple_bluetooth_terminal.ui.charts.SparklineView;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import de.kai_morich.simple_bluetooth_terminal.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A2 · Mediciones (admin): resumen de la localidad, filtros por tipo y los nodos agrupados en Acometida / Cargas / Solar,
 * cada uno con su mini curva del día, su potencia y (en las cargas) su porcentaje del total.
 */
public class AdminMedicionesFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L, SPARK_MS = 5 * 60_000L;
    private static final int F_ALL = 0, F_CFE = 1, F_LOADS = 2, F_SOLAR = 3, F_BESS = 4;

    private ApiService api;
    private Integer customerId, locationId;
    private String customerName, locationName;
    private TextView tvLive, tvCtxAvatar, tvCtxCustomer, tvCtxLocation, tvOffline, tvEmpty;
    private TextView tvSumSolar, tvSumSolarSub, tvSumCfe, tvSumCfeSub, tvSumCons, tvSumConsSub;
    private View barSolar, barCfe;
    private LinearLayout nodesList, filters;
    private int gen, filter = F_ALL;
    private long lastOk, sparkAt;
    private boolean shownOnce;

    private List<NodeTrafficLight> nodes = new ArrayList<>();
    private Map<Integer, DeviceCurrent> devices = Collections.emptyMap();
    private final Map<Integer, double[]> sparks = new HashMap<>();

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
        View v = inflater.inflate(R.layout.fragment_admin_mediciones, container, false);
        api = ApiClient.getApiService();
        tvLive = v.findViewById(R.id.tvLive);
        tvCtxAvatar = v.findViewById(R.id.tvCtxAvatar);
        tvCtxCustomer = v.findViewById(R.id.tvCtxCustomer);
        tvCtxLocation = v.findViewById(R.id.tvCtxLocation);
        tvOffline = v.findViewById(R.id.tvOffline);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvSumSolar = v.findViewById(R.id.tvSumSolar);
        tvSumSolarSub = v.findViewById(R.id.tvSumSolarSub);
        tvSumCfe = v.findViewById(R.id.tvSumCfe);
        tvSumCfeSub = v.findViewById(R.id.tvSumCfeSub);
        tvSumCons = v.findViewById(R.id.tvSumCons);
        tvSumConsSub = v.findViewById(R.id.tvSumConsSub);
        barSolar = v.findViewById(R.id.barSolar);
        barCfe = v.findViewById(R.id.barCfe);
        nodesList = v.findViewById(R.id.nodesList);
        filters = v.findViewById(R.id.filters);
        ((TextView) v.findViewById(R.id.tvHeaderTitle)).setText("Mediciones");

        customerId = ContextPicker.customerId(requireContext());
        locationId = ContextPicker.locationId(requireContext());
        customerName = ContextPicker.customerName(requireContext());
        locationName = ContextPicker.locationName(requireContext());
        paintContext();

        v.findViewById(R.id.btnLogout).setOnClickListener(x -> ((MainActivity) requireActivity()).confirmLogout());
        // Al cambiar de cliente o localidad se consulta solo
        v.findViewById(R.id.ctxCard).setOnClickListener(x -> ContextPicker.show(requireActivity(), (cid, cname, lid, lname, locs) -> {
            ContextPicker.save(requireContext(), cid, cname, lid, lname);
            customerId = cid;
            customerName = cname;
            locationId = lid;
            locationName = lname;
            gen++;
            lastOk = 0;
            sparkAt = 0;
            nodes = new ArrayList<>();
            devices = Collections.emptyMap();
            sparks.clear();
            tvOffline.setVisibility(View.GONE);
            paintContext();
            clearData();
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

    private void paintContext() {
        tvCtxCustomer.setText(customerName != null ? customerName : "Selecciona cliente");
        tvCtxLocation.setText(locationName != null ? locationName : "—");
        tvCtxAvatar.setText(ContextPicker.initials(customerName));
    }

    private void clearData() {
        nodesList.removeAllViews();
        filters.removeAllViews();
        tvEmpty.setVisibility(View.GONE);
        tvLive.setText("");
        for (TextView t : new TextView[]{tvSumSolar, tvSumCfe, tvSumCons}) t.setText("--");
        for (TextView t : new TextView[]{tvSumSolarSub, tvSumCfeSub, tvSumConsSub}) t.setText(" ");
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
                for (LocationEnergyPower l : r.body().getLocations()) {
                    if (l.getIdLocation() != null && l.getIdLocation() == loc) { showSummary(l); break; }
                }
                lastOk = System.currentTimeMillis();
                tvLive.setText("● en vivo · " + Fmt.hhmm(lastOk));
                tvOffline.setVisibility(View.GONE);
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
                showNodes();
                LiveLoader.devices(api, cust, list, map -> {
                    if (!isAdded() || g != gen) return;
                    devices = map;
                    showNodes();
                });
                loadSparks(g, cust, list);
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { }
        });
    }

    /** Mini curvas: una consulta por nodo, cada 5 minutos (cambian poco y son varias llamadas). */
    private void loadSparks(final int g, int cust, List<NodeTrafficLight> list) {
        long now = System.currentTimeMillis();
        if (now - sparkAt < SPARK_MS && !sparks.isEmpty()) return;
        sparkAt = now;
        final int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        final String date = EnergyLoader.ymd(Calendar.getInstance());
        for (final NodeTrafficLight n : list) {
            if (n.getIdNode() == null) continue;
            List<NodeTrafficLight> one = new ArrayList<>();
            one.add(n);
            EnergyLoader.powerDay(api, cust, one, date, kw -> {
                if (!isAdded() || g != gen || kw == null) return;
                double[] upToNow = new double[hour + 1];
                System.arraycopy(kw, 0, upToNow, 0, hour + 1);
                sparks.put(n.getIdNode(), upToNow);
                showNodes();
            });
        }
    }

    private void offline() {
        tvOffline.setText(lastOk > 0 ? "Sin conexión · mostrando último dato de " + Fmt.hhmm(lastOk) : "Sin conexión");
        tvOffline.setVisibility(View.VISIBLE);
    }

    // ───────────────────────── pintar ─────────────────────────

    private void showSummary(LocationEnergyPower l) {
        tvSumSolar.setText(Fmt.n1(l.getPanelsW() / 1000.0));
        tvSumSolarSub.setText("hoy " + Fmt.n0(l.getPanelsWhDay() / 1000.0) + " kWh");
        tvSumCfe.setText(Fmt.n1(l.getCfeW() / 1000.0));
        tvSumCfeSub.setText("hoy " + Fmt.n0(l.getCfeWhDay() / 1000.0) + " kWh");
        tvSumCons.setText(Fmt.n1(l.getLocalityW() / 1000.0));
        tvSumConsSub.setText(l.getInjectionW() > 50 ? "kW · inyectando" : "kW · sin inyección");
        double s = Math.max(0, l.getPanelsW()), c = Math.max(0, l.getCfeW());
        setWeight(barSolar, (float) s);
        setWeight(barCfe, (float) (s + c > 0 ? c : 1)); // sin datos, la barra queda toda azul como en el diseño
    }

    private int kindFilter(NodeTrafficLight n) {
        int k = NodeKind.of(n);
        return k == NodeKind.CFE ? F_CFE : k == NodeKind.SOLAR ? F_SOLAR : k == NodeKind.BESS ? F_BESS : F_LOADS;
    }

    private void showNodes() {
        if (getView() == null) return;
        int[] count = new int[5];
        for (NodeTrafficLight n : nodes) count[kindFilter(n)]++;
        count[F_ALL] = nodes.size();
        paintFilters(count);
        tvEmpty.setVisibility(nodes.isEmpty() ? View.VISIBLE : View.GONE);

        nodesList.removeAllViews();
        addSection("ACOMETIDA", F_CFE, false);
        addSection("CARGAS · % DEL TOTAL", F_LOADS, true);
        addSection("SOLAR", F_SOLAR, false);
        addSection("BATERÍA", F_BESS, false);
        if (!shownOnce && !nodes.isEmpty()) {
            shownOnce = true;
            Anim.staggerIn(nodesList);
        }
    }

    private void paintFilters(int[] count) {
        filters.removeAllViews();
        String[] names = {"Todos", "CFE", "Cargas", "Solar", "Batería"};
        for (int f = 0; f < 5; f++) {
            if (f == F_BESS && count[f] == 0) continue;
            final int fi = f;
            boolean on = filter == f, enabled = count[f] > 0 || f == F_ALL;
            TextView t = new TextView(requireContext());
            t.setText(names[f] + " " + count[f]);
            t.setTextSize(13);
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            float d = getResources().getDisplayMetrics().density;
            t.setPadding(Math.round(14 * d), Math.round(7 * d), Math.round(14 * d), Math.round(7 * d));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(100 * d);
            if (on) bg.setColor(0xFF161616); else { bg.setColor(0xFFFFFFFF); bg.setStroke(Math.round(1.5f * d), 0xFFDDDDDD); }
            t.setBackground(bg);
            t.setTextColor(on ? 0xFFFFFFFF : enabled ? 0xFF161616 : 0xFFBBBBBB);
            t.setEnabled(enabled);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(6 * d));
            t.setOnClickListener(x -> { filter = fi; showNodes(); });
            filters.addView(t, lp);
        }
    }

    /** Un grupo de nodos de un tipo, dentro de una tarjeta; en las cargas se calcula el % del total. */
    private void addSection(String title, int kind, boolean pct) {
        if (filter != F_ALL && filter != kind) return;
        final List<NodeTrafficLight> list = new ArrayList<>();
        for (NodeTrafficLight n : nodes) if (kindFilter(n) == kind) list.add(n);
        if (list.isEmpty()) return;
        // Mayor potencia primero; los que no reportan, al final
        Collections.sort(list, (a, b) -> {
            if (a.isGreen() != b.isGreen()) return a.isGreen() ? -1 : 1;
            return Double.compare(Math.abs(kwOf(b)), Math.abs(kwOf(a)));
        });
        double total = 0;
        for (NodeTrafficLight n : list) total += Math.abs(kwOf(n));

        float d = getResources().getDisplayMetrics().density;
        TextView label = new TextView(requireContext());
        label.setText(title);
        label.setTextSize(11);
        label.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        label.setLetterSpacing(0.08f);
        label.setTextColor(0xFF777777);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Math.round(14 * d);
        llp.bottomMargin = Math.round(6 * d);
        nodesList.addView(label, llp);

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.am_card14);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                View line = new View(requireContext());
                line.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.am_line));
                card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            card.addView(row(list.get(i), pct && total > 0 ? Math.round(100 * Math.abs(kwOf(list.get(i))) / total) : -1, card));
        }
        nodesList.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private double kwOf(NodeTrafficLight n) { return LiveLoader.kw(devices.get(n.getIdNode())); }

    private View row(final NodeTrafficLight n, long pct, ViewGroup parent) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_node_card, parent, false);
        int kind = NodeKind.of(n);
        TextView tvPct = row.findViewById(R.id.tvPct);
        ImageView icon = row.findViewById(R.id.icon);
        View box = row.findViewById(R.id.iconBox);
        if (pct >= 0) {
            tvPct.setVisibility(View.VISIBLE);
            tvPct.setText(pct + "%");
        } else {
            icon.setVisibility(View.VISIBLE);
            icon.setImageResource(kind == NodeKind.SOLAR ? R.drawable.ic_am_sun : kind == NodeKind.BESS ? R.drawable.ic_battery : R.drawable.ic_am_bolt);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            g.setColor(kind == NodeKind.SOLAR ? 0xFFFFF3D6 : 0xFFE8F0FF);
            box.setBackground(g);
        }
        ((TextView) row.findViewById(R.id.tvName)).setText(n.getName());

        boolean virtual = n.getSerie() == null || n.getSerie().trim().isEmpty();
        String serie = virtual ? "virtual" : "…" + n.getSerie().substring(Math.max(0, n.getSerie().length() - 4));
        String ago = n.isGreen() ? shortAgo(n) : RelativeTime.ago(n.getLastDate(), n.getLastHour());
        String head = (n.getTypeName() != null ? n.getTypeName() : "Nodo") + " · " + serie + " · ";
        SpannableString sub = new SpannableString(head + ago);
        sub.setSpan(new ForegroundColorSpan(ContextCompat.getColor(requireContext(), n.isGreen() ? R.color.am_ok_text : R.color.am_err_text)),
                head.length(), sub.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) row.findViewById(R.id.tvSub)).setText(sub);

        SparklineView spark = row.findViewById(R.id.spark);
        spark.set(sparks.get(n.getIdNode()));
        if (!n.isGreen()) spark.setColor(0xFFBBBBBB);
        TextView val = row.findViewById(R.id.tvVal);
        DeviceCurrent dev = devices.get(n.getIdNode());
        if (dev != null && n.isGreen()) {
            val.setText(Fmt.n1(Math.abs(LiveLoader.kw(dev))));
        } else {
            val.setText("—");
            val.setTextColor(0xFF999999);
        }
        Anim.pressable(row);
        row.setOnClickListener(x -> openNode(n));
        return row;
    }

    /** "44 s" / "3 min" (sin el "hace", como en el diseño). */
    private static String shortAgo(NodeTrafficLight n) {
        String s = RelativeTime.ago(n.getLastDate(), n.getLastHour());
        return s.startsWith("hace ") ? s.substring(5) : s;
    }

    private void openNode(NodeTrafficLight n) {
        Intent i = new Intent(requireContext(), NodoActivity.class);
        i.putExtra("idMeasure", n.getIdNode() != null ? n.getIdNode() : 0);
        i.putExtra("idCustomer", customerId != null ? customerId : 0);
        i.putExtra("idCircuit", n.getIdCircuit() != null ? n.getIdCircuit() : 0);
        i.putExtra("idLocation", locationId != null ? locationId : 0);
        i.putExtra("nodeName", n.getName());
        i.putExtra("nodeType", n.getType() != null ? n.getType() : 0);
        i.putExtra("nodeTypeName", n.getTypeName());
        i.putExtra("nodeSerie", n.getSerie());
        i.putExtra("nodeLastDate", n.getLastDate());
        i.putExtra("nodeLastHour", n.getLastHour());
        startActivity(i);
    }

    private static void setWeight(View v, float w) {
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.weight = w;
        v.setLayoutParams(lp);
    }
}
