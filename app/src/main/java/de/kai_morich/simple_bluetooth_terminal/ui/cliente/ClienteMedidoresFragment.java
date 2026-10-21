package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import android.content.res.ColorStateList;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.util.Anim;
import de.kai_morich.simple_bluetooth_terminal.util.Fmt;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import de.kai_morich.simple_bluetooth_terminal.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** K3 · Mis medidores: los que necesitan atención primero, luego los que funcionan. */
public class ClienteMedidoresFragment extends Fragment {

    private static final long REFRESH_MS = 30_000L;

    private ClienteViewModel vm;
    private LinearLayout list;
    private TextView tvSubtitle, tvEmpty, tvOffline;
    private List<NodeTrafficLight> nodes = new ArrayList<>();
    private final Map<Integer, DeviceCurrent> current = new HashMap<>();
    private long lastOk;
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
        View v = inflater.inflate(R.layout.fragment_cliente_medidores, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        list = v.findViewById(R.id.list);
        tvSubtitle = v.findViewById(R.id.tvSubtitle);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        tvOffline = v.findViewById(R.id.tvOffline);
        vm.location.observe(getViewLifecycleOwner(), l -> {
            gen++;
            nodes = new ArrayList<>();
            current.clear();
            render();
            load();
        });
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, REFRESH_MS);
    }

    @Override public void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    // ───────────────────────── datos ─────────────────────────

    private void load() {
        if (vm.customerId == null || vm.location.getValue() == null) return;
        final int g = gen;
        vm.fetchNodes(list -> {
            if (!isAdded() || g != gen) return;
            if (list == null) {
                tvOffline.setText(lastOk > 0 ? "Sin conexión · mostrando datos de " + Fmt.hhmm(lastOk) : "Sin conexión");
                tvOffline.setVisibility(View.VISIBLE);
                return;
            }
            tvOffline.setVisibility(View.GONE);
            lastOk = System.currentTimeMillis();
            nodes = list;
            render();
            loadPower(g);
        });
    }

    /** La potencia de cada medidor sale del "current" por circuito. */
    private void loadPower(final int g) {
        for (Integer circuit : vm.circuitIds()) {
            vm.api().getCustomerDevicesCurrentDetailed(circuit, vm.customerId, 0).enqueue(new Callback<List<DeviceCurrent>>() {
                @Override public void onResponse(Call<List<DeviceCurrent>> c, Response<List<DeviceCurrent>> r) {
                    if (!isAdded() || g != gen || !r.isSuccessful() || r.body() == null) return;
                    for (DeviceCurrent d : r.body()) if (d.getIdMeasure() != null) current.put(d.getIdMeasure(), d);
                    render();
                }
                @Override public void onFailure(Call<List<DeviceCurrent>> c, Throwable t) { /* se queda sin potencia */ }
            });
        }
    }

    // ───────────────────────── render ─────────────────────────

    private void render() {
        if (getView() == null) return;
        Context ctx = requireContext();
        list.removeAllViews();
        LocationsViewModel loc = vm.location.getValue();
        int n = nodes.size();
        tvSubtitle.setText((loc != null ? loc.getName() + " · " : "") + n + (n == 1 ? " medidor" : " medidores"));
        tvEmpty.setVisibility(n == 0 && Boolean.TRUE.equals(vm.loaded.getValue()) ? View.VISIBLE : View.GONE);

        List<NodeTrafficLight> bad = new ArrayList<>(), ok = new ArrayList<>();
        for (NodeTrafficLight node : nodes) (node.isGreen() ? ok : bad).add(node);

        if (!bad.isEmpty()) {
            list.addView(section("NECESITAN ATENCIÓN", R.color.am_err_text, 16));
            for (int i = 0; i < bad.size(); i++) {
                final NodeTrafficLight node = bad.get(i);
                if (i == 0) {
                    View card = LayoutInflater.from(ctx).inflate(R.layout.item_medidor_alerta, list, false);
                    icon(ctx, card.findViewById(R.id.iconBox), (ImageView) card.findViewById(R.id.icon), node);
                    ((TextView) card.findViewById(R.id.tvName)).setText(node.getName());
                    ((TextView) card.findViewById(R.id.tvStatus)).setText(offlineText(node));
                    card.findViewById(R.id.head).setOnClickListener(x -> open(node));
                    card.findViewById(R.id.btnHow).setOnClickListener(x -> howToCheck());
                    list.addView(card, lp(8));
                } else {
                    View row = row(node);
                    row.setBackgroundResource(R.drawable.am_card14_err);
                    ((TextView) row.findViewById(R.id.tvStatus)).setText(offlineText(node));
                    ((TextView) row.findViewById(R.id.tvStatus)).setTextColor(ContextCompat.getColor(ctx, R.color.am_err_text));
                    list.addView(row, lp(8));
                }
            }
        }
        if (!ok.isEmpty()) {
            list.addView(section("FUNCIONANDO", R.color.am_ok_text, bad.isEmpty() ? 16 : 20));
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.am_card14);
            for (int i = 0; i < ok.size(); i++) {
                NodeTrafficLight node = ok.get(i);
                if (i > 0) {
                    View line = new View(ctx);
                    line.setBackgroundColor(ContextCompat.getColor(ctx, R.color.am_line));
                    card.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
                }
                View row = row(node);
                String ago = RelativeTime.ago(node.getLastDate(), node.getLastHour());
                String kw = powerText(node);
                ((TextView) row.findViewById(R.id.tvStatus)).setText("Actualizado " + ago + (kw != null ? " · " + kw : ""));
                ((TextView) row.findViewById(R.id.tvStatus)).setTextColor(ContextCompat.getColor(ctx, R.color.am_ok_text));
                card.addView(row);
            }
            list.addView(card, lp(8));
        }
        if (!staggered && list.getChildCount() > 0) { // sólo la primera vez que hay lista
            staggered = true;
            Anim.staggerIn(list);
        }
    }

    private boolean staggered;

    private View row(final NodeTrafficLight node) {
        View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_medidor_row, list, false);
        icon(requireContext(), row.findViewById(R.id.iconBox), (ImageView) row.findViewById(R.id.icon), node);
        ((TextView) row.findViewById(R.id.tvName)).setText(node.getName());
        Anim.pressable(row);
        row.setOnClickListener(x -> open(node));
        return row;
    }

    private TextView section(String text, int colorRes, int topDp) {
        TextView t = new TextView(requireContext(), null, 0, R.style.AmSectionLabel);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
        t.setLayoutParams(lp(topDp));
        return t;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = (int) (topDp * getResources().getDisplayMetrics().density);
        return p;
    }

    private String offlineText(NodeTrafficLight node) {
        long ts = RelativeTime.parse(node.getLastDate(), node.getLastHour());
        if (ts < 0) return "Sin conexión";
        return "Sin conexión desde el " + new SimpleDateFormat("d MMM, HH:mm", new Locale("es", "MX")).format(new java.util.Date(ts)).replace(".", "");
    }

    /** Potencia actual en kW: generación si es solar, consumo en los demás. */
    @Nullable
    private String powerText(NodeTrafficLight node) {
        DeviceCurrent d = node.getIdNode() != null ? current.get(node.getIdNode()) : null;
        if (d == null) return null;
        double w = NodeKind.of(node) == NodeKind.SOLAR ? d.getGenW() : d.getConW();
        return Fmt.n0(w / 1000.0) + " kW";
    }

    /** Icono por tipo: solar ☀ (amarillo), CFE ⚡ (azul), transformador ▣ (gris). */
    static void icon(Context ctx, View box, ImageView img, NodeTrafficLight node) {
        int kind = NodeKind.of(node);
        boolean transformer = node.getTypeName() != null && node.getTypeName().toLowerCase(Locale.ROOT).contains("transform");
        int res, bg, tint;
        if (kind == NodeKind.SOLAR) { res = R.drawable.ic_am_sun; bg = R.drawable.am_icon_solar; tint = R.color.am_solar_text; }
        else if (kind == NodeKind.CFE && !transformer) { res = R.drawable.ic_am_bolt; bg = R.drawable.am_icon_cfe; tint = R.color.am_cfe_text; }
        else { res = R.drawable.ic_memory; bg = R.drawable.am_icon_gray; tint = R.color.am_ink; }
        box.setBackgroundResource(bg);
        img.setImageResource(res);
        ImageViewCompat.setImageTintList(img, ColorStateList.valueOf(ContextCompat.getColor(ctx, tint)));
    }

    // ───────────────────────── acciones ─────────────────────────

    private void open(NodeTrafficLight node) {
        ((ClienteActivity) requireActivity()).push(ClienteMedidorFragment.newInstance(node));
    }

    private void howToCheck() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Cómo revisar")
                .setMessage("1. Confirma que el router de internet del sitio esté encendido y con luz de conexión.\n\n"
                        + "2. Revisa que el medidor tenga su luz encendida.\n\n"
                        + "3. Espera unos minutos: al volver el internet, el medidor se reconecta solo.")
                .setPositiveButton("Entendido", null)
                .show();
    }
}
