package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** C2 · Cliente: árbol Localidad → Circuito → Dispositivo con alta, edición, mover y eliminar en el mismo lugar. */
public class ClienteArbolFragment extends Fragment {

    static ClienteArbolFragment newInstance(int id, String name) {
        Bundle b = new Bundle();
        b.putInt("id", id);
        b.putString("name", name);
        ClienteArbolFragment f = new ClienteArbolFragment();
        f.setArguments(b);
        return f;
    }

    private static final int ACCENT = 0xFFF28C28, GREEN = 0xFF1F9D4C, RED = 0xFFD23B2B, GRAY = 0xFFBBBBBB;

    private ApiService api;
    private int customerId;
    private CustomerViewModel customer;
    private final List<LocationsViewModel> locations = new ArrayList<>();
    private final List<CircuitsViewModel> circuits = new ArrayList<>();
    private final List<DevicesViewModel> devices = new ArrayList<>();
    private final List<NodeTrafficLight> nodes = new ArrayList<>();
    private TextView tvName, tvInfo, tvIds, tvStatLoc, tvStatDev, tvStatOff;
    private View cardOff;
    private LinearLayout tree, filters;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inflater.inflate(R.layout.fragment_cliente_arbol, container, false);
        api = ApiClient.getApiService();
        customerId = requireArguments().getInt("id");
        tvName = v.findViewById(R.id.tvName);
        tvInfo = v.findViewById(R.id.tvInfo);
        tvIds = v.findViewById(R.id.tvIds);
        tree = v.findViewById(R.id.tree);
        filters = v.findViewById(R.id.filters);
        tvStatLoc = v.findViewById(R.id.tvStatLoc);
        tvStatDev = v.findViewById(R.id.tvStatDev);
        tvStatOff = v.findViewById(R.id.tvStatOff);
        cardOff = v.findViewById(R.id.cardOff);
        tvName.setText(requireArguments().getString("name"));
        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());
        v.findViewById(R.id.btnEdit).setOnClickListener(x -> editCustomer());
        v.findViewById(R.id.btnMenu).setOnClickListener(x -> new MaterialAlertDialogBuilder(requireContext())
                .setItems(new String[]{"Eliminar cliente"}, (d, w) -> deleteCustomer()).show());
        reload();
        return v;
    }

    // ───────────────────────── datos ─────────────────────────

    private void reload() {
        final int[] pending = {5};
        final Runnable done = () -> { if (--pending[0] == 0 && isAdded()) { paintInfo(); renderTree(); } };

        api.getCustomerInformation(customerId).enqueue(new Callback<CustomerViewModel>() {
            @Override public void onResponse(Call<CustomerViewModel> c, Response<CustomerViewModel> r) {
                if (r.isSuccessful() && r.body() != null) customer = r.body();
                done.run();
            }
            @Override public void onFailure(Call<CustomerViewModel> c, Throwable t) { done.run(); }
        });
        api.getCustomerLocations(customerId).enqueue(new Callback<List<LocationsViewModel>>() {
            @Override public void onResponse(Call<List<LocationsViewModel>> c, Response<List<LocationsViewModel>> r) {
                locations.clear();
                if (r.isSuccessful() && r.body() != null) locations.addAll(r.body());
                done.run();
            }
            @Override public void onFailure(Call<List<LocationsViewModel>> c, Throwable t) { done.run(); }
        });
        api.getCustomerCircuitsForAdmin(customerId).enqueue(new Callback<List<CircuitsViewModel>>() {
            @Override public void onResponse(Call<List<CircuitsViewModel>> c, Response<List<CircuitsViewModel>> r) {
                circuits.clear();
                if (r.isSuccessful() && r.body() != null) circuits.addAll(r.body());
                done.run();
            }
            @Override public void onFailure(Call<List<CircuitsViewModel>> c, Throwable t) { done.run(); }
        });
        api.getCustomerDevices(customerId).enqueue(new Callback<List<DevicesViewModel>>() {
            @Override public void onResponse(Call<List<DevicesViewModel>> c, Response<List<DevicesViewModel>> r) {
                devices.clear();
                if (r.isSuccessful() && r.body() != null) devices.addAll(r.body());
                done.run();
            }
            @Override public void onFailure(Call<List<DevicesViewModel>> c, Throwable t) { done.run(); }
        });
        api.getCustomerNodesTrafficLight(customerId).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                nodes.clear();
                if (r.isSuccessful() && r.body() != null && r.body().getNodes() != null) nodes.addAll(r.body().getNodes());
                done.run();
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { done.run(); }
        });
    }

    private void paintInfo() {
        if (customer == null) return;
        tvName.setText(customer.getName());
        StringBuilder sb = new StringBuilder();
        for (String s : new String[]{customer.getEmail(), customer.getTelephone(), customer.getState()}) {
            if (s != null && !s.trim().isEmpty()) sb.append(sb.length() > 0 ? " · " : "").append(s.trim());
        }
        tvInfo.setText(sb.length() > 0 ? sb : "Sin datos de contacto");
        tvIds.setText("ID " + customerId + (customer.getConfig() != null ? " · config " + customer.getConfig() : ""));
    }

    /** Semáforo del dispositivo: por id de nodo y, si no coincide, por nombre dentro del mismo circuito. */
    private NodeTrafficLight statusOf(DevicesViewModel d) {
        for (NodeTrafficLight n : nodes) {
            if (d.getIdNode() != null && d.getIdNode().equals(n.getIdNode())) return n;
        }
        for (NodeTrafficLight n : nodes) {
            if (d.getName() != null && d.getName().equals(n.getName())
                    && (d.getIdCircuit() == null || d.getIdCircuit().equals(n.getIdCircuit()))) return n;
        }
        return null;
    }

    private static String typeName(Integer t) {
        if (t == null) return "Nodo";
        switch (t) {
            case 0: return "CFE";
            case 1: return "Transformador";
            case 2: return "Solar";
            case 3: return "Batería";
            case 255: return "HUB";
            default: return "Nodo " + t;
        }
    }

    // ───────────────────────── árbol ─────────────────────────

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private static final int F_ALL = 0, F_OFF = 1, F_SOLAR = 2, F_CFE = 3;

    private final java.util.Set<Integer> open = new java.util.HashSet<>(); // localidades abiertas
    private boolean openInit;
    private int filter = F_ALL;

    private static boolean isVirtual(DevicesViewModel d) { return d.getSerie() == null || d.getSerie().trim().isEmpty(); }

    /** Sin conexión: el semáforo del nodo no está en verde (los virtuales no cuentan, dependen de su acometida). */
    private boolean isOffline(DevicesViewModel d) {
        NodeTrafficLight st = statusOf(d);
        return !isVirtual(d) && st != null && !st.isGreen();
    }

    private boolean matches(DevicesViewModel d) {
        Integer t = d.getType();
        switch (filter) {
            case F_OFF: return isOffline(d);
            case F_SOLAR: return t != null && t == 2;
            case F_CFE: return t != null && (t == 0 || t == 1);
            default: return true;
        }
    }

    private void renderTree() {
        if (!openInit && !locations.isEmpty()) {
            openInit = true;
            if (locations.get(0).getIdLocation() != null) open.add(locations.get(0).getIdLocation());
        }
        int off = 0, solar = 0, cfe = 0;
        for (DevicesViewModel d : devices) {
            if (isOffline(d)) off++;
            if (d.getType() != null && d.getType() == 2) solar++;
            if (d.getType() != null && (d.getType() == 0 || d.getType() == 1)) cfe++;
        }
        tvStatLoc.setText(String.valueOf(locations.size()));
        tvStatDev.setText(String.valueOf(devices.size()));
        tvStatOff.setText(String.valueOf(off));
        cardOff.setBackgroundResource(off > 0 ? R.drawable.am_card12_err : R.drawable.am_card12);
        tvStatOff.setTextColor(off > 0 ? 0xFFB3261E : 0xFF161616);
        paintFilters(off, solar, cfe);

        tree.removeAllViews();
        for (final LocationsViewModel loc : locations) {
            View card = locationCard(loc);
            if (card != null) tree.addView(card);
        }
        TextView add = text("+ Localidad", 14, ACCENT, true);
        add.setGravity(Gravity.CENTER);
        add.setBackgroundResource(R.drawable.am_dashed_btn);
        add.setPadding(0, dp(13), 0, dp(13));
        add.setOnClickListener(x -> addLocation());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        tree.addView(add, lp);
    }

    private void paintFilters(int off, int solar, int cfe) {
        filters.removeAllViews();
        String[] names = {"Todos", "Sin conexión " + off, "Solar", "CFE"};
        for (int f = 0; f < 4; f++) {
            final int fi = f;
            boolean on = filter == f;
            boolean alert = f == F_OFF && off > 0;
            TextView t = text(names[f], 13, 0xFF161616, true);
            t.setPadding(dp(14), dp(7), dp(14), dp(7));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setCornerRadius(dp(100));
            if (on) bg.setColor(alert ? 0xFFD23B2B : 0xFF161616); else { bg.setColor(0xFFFFFFFF); bg.setStroke(dp(1) + 1, 0xFFDDDDDD); }
            t.setBackground(bg);
            t.setTextColor(on ? 0xFFFFFFFF : 0xFF161616);
            t.setOnClickListener(x -> { filter = fi; renderTree(); });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(6));
            filters.addView(t, lp);
        }
    }

    /** Una localidad como acordeón: cabecera con "n/m en línea" y puntitos; al abrir, sus circuitos y dispositivos. */
    private View locationCard(final LocationsViewModel loc) {
        List<CircuitsViewModel> circs = new ArrayList<>();
        int total = 0, online = 0;
        final List<Boolean> dots = new ArrayList<>();
        for (CircuitsViewModel c : circuits) {
            if (c.getIdLocation() != null && c.getIdLocation().equals(loc.getIdLocation())) circs.add(c);
        }
        for (DevicesViewModel d : devices) {
            if (d.getIdLocation() != null && !d.getIdLocation().equals(loc.getIdLocation())) continue;
            boolean inLoc = false;
            for (CircuitsViewModel c : circs) if (d.getIdCircuit() != null && d.getIdCircuit().equals(c.getIdCircuit())) inLoc = true;
            if (!inLoc || isVirtual(d)) continue;
            NodeTrafficLight st = statusOf(d);
            total++;
            boolean on = st != null && st.isGreen();
            if (on) online++;
            dots.add(on);
        }
        boolean anyMatch = filter == F_ALL;
        if (!anyMatch) {
            for (DevicesViewModel d : devices) {
                if (!matches(d)) continue;
                for (CircuitsViewModel c : circs) if (d.getIdCircuit() != null && d.getIdCircuit().equals(c.getIdCircuit())) anyMatch = true;
            }
        }
        if (!anyMatch) return null; // con filtro activo se ocultan las localidades sin coincidencias

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.am_card14);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(8);
        card.setLayoutParams(clp);

        final boolean expanded = loc.getIdLocation() != null && open.contains(loc.getIdLocation());
        // Cabecera
        LinearLayout head = new LinearLayout(requireContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(expanded ? "▾" : "▸", 13, 0xFF888888, false));
        LinearLayout names = new LinearLayout(requireContext());
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(10), 0, dp(8), 0);
        names.addView(text(loc.getName(), 16, 0xFF161616, true));
        String desc = loc.getDescription() != null && !loc.getDescription().trim().isEmpty() ? loc.getDescription().trim() + " · " : "";
        names.addView(text(desc + circs.size() + (circs.size() == 1 ? " circuito" : " circuitos"), 12, 0xFF777777, false));
        head.addView(names, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout status = new LinearLayout(requireContext());
        status.setOrientation(LinearLayout.VERTICAL);
        status.setGravity(Gravity.END);
        if (total > 0) {
            int color = online == total ? GREEN : online == 0 ? RED : 0xFFB07A00;
            TextView ratio = text(online + "/" + total + " en línea", 12.5f, color, false);
            ratio.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            status.addView(ratio);
            LinearLayout bar = new LinearLayout(requireContext());
            for (boolean on : dots) {
                View seg = new View(requireContext());
                android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
                g.setCornerRadius(dp(2));
                g.setColor(on ? GREEN : RED);
                seg.setBackground(g);
                LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(14), dp(4));
                sp.setMarginStart(dp(3));
                bar.addView(seg, sp);
            }
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(5);
            status.addView(bar, bp);
        }
        head.addView(status);
        head.setClickable(true);
        head.setOnClickListener(x -> {
            if (loc.getIdLocation() == null) return;
            if (!open.remove(loc.getIdLocation())) open.add(loc.getIdLocation());
            renderTree();
        });
        head.setOnLongClickListener(x -> {
            menu(loc.getName(), new String[]{"Editar localidad", "Eliminar localidad"}, i -> {
                if (i == 0) editLocation(loc);
                else confirmDelete("la localidad «" + loc.getName() + "»", () -> deleteLocation(loc));
            });
            return true;
        });
        card.addView(head);
        if (!expanded) return card;

        // Circuitos y dispositivos
        for (final CircuitsViewModel circ : circs) {
            View line = new View(requireContext());
            line.setBackgroundColor(0xFFEEEEEE);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            ll.topMargin = dp(10);
            card.addView(line, ll);

            LinearLayout ch = new LinearLayout(requireContext());
            ch.setGravity(Gravity.CENTER_VERTICAL);
            ch.setPadding(0, dp(10), 0, dp(4));
            TextView lab = text("CIRCUITO · " + String.valueOf(circ.getName()).toUpperCase(java.util.Locale.ROOT), 11, 0xFF777777, false);
            lab.setTypeface(Typeface.MONOSPACE);
            ch.addView(lab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ch.addView(text("✎", 14, 0xFFBBBBBB, false));
            ch.setClickable(true);
            ch.setOnClickListener(x -> editCircuit(circ));
            ch.setOnLongClickListener(x -> {
                menu(circ.getName(), new String[]{"Eliminar circuito"},
                        i -> confirmDelete("el circuito «" + circ.getName() + "»", () -> deleteCircuit(circ)));
                return true;
            });
            card.addView(ch);

            for (DevicesViewModel d : devices) {
                if (d.getIdCircuit() != null && d.getIdCircuit().equals(circ.getIdCircuit()) && matches(d)) card.addView(deviceRow(d, circ));
            }
            LinearLayout links = new LinearLayout(requireContext());
            links.addView(link("+ Dispositivo", () -> NewDeviceSheet.show(requireActivity(), customerId,
                    customer != null ? customer.getName() : null, loc, circ, this::reload)));
            View gap = new View(requireContext());
            links.addView(gap, new LinearLayout.LayoutParams(dp(16), 1));
            links.addView(link("+ Circuito", () -> addCircuit(loc)));
            links.setPadding(dp(4), 0, 0, 0);
            card.addView(links);
        }
        if (circs.isEmpty()) {
            card.addView(link("+ Circuito", () -> addCircuit(loc)));
        }
        return card;
    }

    private View deviceRow(final DevicesViewModel d, CircuitsViewModel circ) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));

        Integer t = d.getType();
        boolean virtual = isVirtual(d);
        // Icono: rayo (CFE), sol (solar) o cuadro (virtual / otros)
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(t != null && t == 2 ? 0xFFFFF3D6 : virtual ? 0xFFF0F0F0 : 0xFFE8F0FF);
        box.setBackground(bg);
        android.widget.ImageView icon = new android.widget.ImageView(requireContext());
        icon.setImageResource(t != null && t == 2 ? R.drawable.ic_am_sun : R.drawable.ic_am_bolt);
        icon.setVisibility(virtual ? View.GONE : View.VISIBLE);
        box.addView(icon, new android.widget.FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        if (virtual) {
            TextView sq = text("▣", 16, 0xFF555555, false);
            box.addView(sq, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        }
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(40), dp(40));
        blp.rightMargin = dp(12);
        row.addView(box, blp);

        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nameRow = new LinearLayout(requireContext());
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(d.getName(), 14.5f, 0xFF161616, true);
        nameRow.addView(name);
        if (virtual) {
            TextView tag = text("VIRTUAL", 9.5f, 0xFFB07A00, true);
            tag.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            android.graphics.drawable.GradientDrawable tg = new android.graphics.drawable.GradientDrawable();
            tg.setCornerRadius(dp(4));
            tg.setColor(0xFFFFF3D6);
            tag.setBackground(tg);
            tag.setPadding(dp(5), dp(1), dp(5), dp(1));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tp.setMarginStart(dp(8));
            nameRow.addView(tag, tp);
        }
        col.addView(nameRow);
        String sub = virtual ? "Nodo " + (d.getIdNode() != null ? d.getIdNode() : "—") + " · " + typeName(t) + " — PV"
                : d.getSerie() + (d.getLoad() != null ? " · Load " + d.getLoad() : "");
        TextView subtitle = text(sub, 11, 0xFF777777, false);
        subtitle.setTypeface(Typeface.MONOSPACE);
        col.addView(subtitle);
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Estado a la derecha: "sin datos hace 3 d" / "depende de CFE" / en línea
        NodeTrafficLight st = statusOf(d);
        LinearLayout stCol = new LinearLayout(requireContext());
        stCol.setOrientation(LinearLayout.VERTICAL);
        stCol.setGravity(Gravity.END);
        if (virtual) {
            boolean cfeOff = false;
            for (NodeTrafficLight n : nodes) {
                if (circ.getIdCircuit() != null && circ.getIdCircuit().equals(n.getIdCircuit()) && n.getType() != null && n.getType() == 0 && !n.isGreen()) cfeOff = true;
            }
            stCol.addView(text(cfeOff ? "depende" : "virtual", 12, cfeOff ? RED : 0xFF777777, true));
            if (cfeOff) stCol.addView(text("de CFE", 12, RED, true));
        } else if (st == null) {
            stCol.addView(text("sin estado", 12, 0xFF999999, false));
        } else if (st.isGreen()) {
            stCol.addView(text("en línea", 12, GREEN, true));
            stCol.addView(text(de.kai_morich.simple_bluetooth_terminal.util.RelativeTime.ago(st.getLastDate(), st.getLastHour()), 11, 0xFF777777, false));
        } else {
            stCol.addView(text("sin datos", 12, RED, true));
            stCol.addView(text(de.kai_morich.simple_bluetooth_terminal.util.RelativeTime.ago(st.getLastDate(), st.getLastHour()), 11, RED, true));
        }
        row.addView(stCol);

        row.setClickable(true);
        row.setOnClickListener(x -> editDevice(d));
        row.setOnLongClickListener(x -> {
            menu(d.getName(), new String[]{"Mover a otro circuito", "Eliminar"}, i -> {
                if (i == 0) moveDevice(d);
                else confirmDelete("el dispositivo «" + d.getName() + "»", () -> deleteDevice(d));
            });
            return true;
        });
        return row;
    }

    private View link(String label, final Runnable action) {
        TextView t = text(label, 13, ACCENT, true);
        t.setPadding(0, dp(9), 0, dp(3));
        t.setClickable(true);
        t.setOnClickListener(x -> action.run());
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(requireContext());
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return t;
    }

    private interface IntAction { void run(int index); }

    private void menu(String title, String[] items, final IntAction action) {
        new MaterialAlertDialogBuilder(requireContext()).setTitle(title)
                .setItems(items, (d, w) -> action.run(w)).show();
    }

    private void confirmDelete(String what, Runnable yes) {
        Forms.confirm(requireContext(), "Eliminar", "¿Eliminar " + what + "? Esta acción no se puede deshacer.", "Eliminar", yes);
    }

    // ───────────────────────── cambios ─────────────────────────

    /** Ejecuta una escritura y, si sale bien, vuelve a leer el árbol. */
    private <T> void run(Call<T> call, final String ok, final String fail) {
        call.enqueue(new Callback<T>() {
            @Override public void onResponse(Call<T> c, Response<T> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) { Forms.toast(getContext(), ok); reload(); }
                else Forms.toast(getContext(), Forms.error(fail, r));
            }
            @Override public void onFailure(Call<T> c, Throwable t) { if (isAdded()) Forms.toast(getContext(), "Sin conexión"); }
        });
    }

    private void editCustomer() {
        if (customer == null) return;
        Forms.show(requireContext(), "Editar cliente", new String[]{"Nombre", "Correo", "Teléfono", "Estado"},
                new String[]{customer.getName(), customer.getEmail(), customer.getTelephone(), customer.getState()}, 2, "Guardar",
                v -> run(api.saveCustomer(new CustomerViewModel(customerId, v[0], v[1], v[2], v[3], customer.getLogo(), customer.getConfig())),
                        "Cliente actualizado", "No se pudo guardar el cliente"));
    }

    private void deleteCustomer() {
        Forms.confirm(requireContext(), "Eliminar cliente", "Se eliminará el cliente y todo lo que tiene (localidades, circuitos y dispositivos). Esta acción no se puede deshacer.",
                "Eliminar", () -> {
                    Map<String, Integer> p = new HashMap<>();
                    p.put("IdClient", customerId);
                    p.put("IdCustomer", customerId);
                    api.deleteCustomer(p).enqueue(new Callback<Void>() {
                        @Override public void onResponse(Call<Void> c, Response<Void> r) {
                            if (!isAdded()) return;
                            if (r.isSuccessful()) { Forms.toast(getContext(), "Cliente eliminado"); requireActivity().onBackPressed(); }
                            else Forms.toast(getContext(), Forms.error("No se pudo eliminar el cliente", r));
                        }
                        @Override public void onFailure(Call<Void> c, Throwable t) { if (isAdded()) Forms.toast(getContext(), "Sin conexión"); }
                    });
                });
    }

    private void addLocation() {
        Forms.show(requireContext(), "Nueva localidad", new String[]{"Nombre"}, null, 1, "Crear",
                v -> run(api.addNewLocation(new LocationsViewModel(customerId, v[0], "", "", null, null)), "Localidad creada", "No se pudo crear la localidad"));
    }

    private void editLocation(final LocationsViewModel l) {
        Forms.show(requireContext(), "Editar localidad", new String[]{"Nombre", "Descripción"},
                new String[]{l.getName(), l.getDescription()}, 1, "Guardar", v -> {
                    l.setName(v[0]);
                    l.setDescription(v[1]);
                    l.setIdCustomer(customerId);
                    run(api.saveLocationInformation(l), "Localidad actualizada", "No se pudo guardar la localidad");
                });
    }

    private void deleteLocation(LocationsViewModel l) {
        Map<String, Integer> p = new HashMap<>();
        p.put("IdLocation", l.getIdLocation());
        p.put("IdCustomer", customerId);
        run(api.deleteLocation(p), "Localidad eliminada", "No se pudo eliminar la localidad");
    }

    private void addCircuit(LocationsViewModel loc) {
        Forms.show(requireContext(), "Nuevo circuito", new String[]{"Nombre", "Descripción"}, null, 1, "Crear",
                v -> run(api.addNewCircuit(new CircuitsViewModel(loc.getIdLocation(), v[0], v[1], null, null, null, customerId)),
                        "Circuito creado", "No se pudo crear el circuito"));
    }

    private void editCircuit(final CircuitsViewModel c) {
        Forms.show(requireContext(), "Editar circuito", new String[]{"Nombre", "Descripción"},
                new String[]{c.getName(), c.getDescription()}, 1, "Guardar", v -> {
                    c.setName(v[0]);
                    c.setDescription(v[1]);
                    c.setIdCustomer(customerId);
                    run(api.saveCircuitInformation(c), "Circuito actualizado", "No se pudo guardar el circuito");
                });
    }

    private void deleteCircuit(CircuitsViewModel c) {
        Map<String, Integer> p = new HashMap<>();
        p.put("IdCircuit", c.getIdCircuit());
        p.put("IdClient", customerId);
        p.put("IdCustomer", customerId);
        run(api.deleteCircuit(p), "Circuito eliminado", "No se pudo eliminar el circuito");
    }

    private void editDevice(final DevicesViewModel d) {
        Forms.show(requireContext(), "Editar dispositivo", new String[]{"Nombre", "Descripción"},
                new String[]{d.getName(), d.getDescription()}, 1, "Guardar", v -> {
                    d.setName(v[0]);
                    d.setDescription(v[1]);
                    prepare(d);
                    run(api.saveDeviceInformation(d), "Dispositivo actualizado", "No se pudo guardar el dispositivo");
                });
    }

    /** Completa los campos que el servidor pide al guardar o borrar un dispositivo. */
    private void prepare(DevicesViewModel d) {
        d.setIdCustomer(customerId);
        if (d.getIdHW() == null) d.setIdHW(d.getType() != null ? d.getType() : 0);
        if (d.getNodeType() == null) d.setNodeType(d.getType());
    }

    private void moveDevice(final DevicesViewModel d) {
        final List<CircuitsViewModel> targets = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (CircuitsViewModel c : circuits) {
            if (c.getIdCircuit() != null && !c.getIdCircuit().equals(d.getIdCircuit())) {
                targets.add(c);
                String loc = "";
                for (LocationsViewModel l : locations) if (l.getIdLocation() != null && l.getIdLocation().equals(c.getIdLocation())) loc = l.getName() + " › ";
                labels.add(loc + c.getName());
            }
        }
        if (targets.isEmpty()) { Forms.toast(getContext(), "No hay otro circuito al que moverlo"); return; }
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Mover a…")
                .setItems(labels.toArray(new String[0]), (dlg, w) -> {
                    d.setIdCircuit(targets.get(w).getIdCircuit());
                    d.setIdLocation(targets.get(w).getIdLocation());
                    prepare(d);
                    run(api.saveDeviceInformation(d), "Dispositivo movido", "No se pudo mover el dispositivo");
                }).show();
    }

    private void deleteDevice(DevicesViewModel d) {
        DevicesViewModel p = new DevicesViewModel();
        p.setIdDevice(d.getIdDevice());
        p.setIdCustomer(customerId);
        p.setIdHW(d.getIdHW() != null ? d.getIdHW() : d.getType() != null ? d.getType() : 0);
        run(api.deleteDevice(p), "Dispositivo eliminado", "No se pudo eliminar el dispositivo");
    }
}
