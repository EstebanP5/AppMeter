package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** C1 · Configurar: clientes recientes (con resumen y estado), lista A–Z, búsqueda por cliente / serie / dispositivo y alta. */
public class ConfigClientesFragment extends Fragment {

    private static final String K_RECENT = "recent_clients";
    private static final int MAX_RECENT = 3;

    private ApiService api;
    private final List<CustomerViewModel> customers = new ArrayList<>();
    private List<Map<String, Object>> devices; // getAllDevices: se carga la primera vez que se busca
    private boolean devicesLoading;
    private LinearLayout recentList, allList;
    private TextView lblRecent, lblAll, tvEmpty;
    private EditText etSearch;

    /** Marca un cliente como reciente (el más nuevo primero, máximo 3). */
    static void markRecent(Context c, int id) {
        String cur = ContextPicker.prefs(c).getString(K_RECENT, "");
        List<String> ids = new ArrayList<>();
        ids.add(String.valueOf(id));
        for (String s : cur.split(",")) if (!s.isEmpty() && !s.equals(String.valueOf(id))) ids.add(s);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(ids.size(), MAX_RECENT); i++) sb.append(i > 0 ? "," : "").append(ids.get(i));
        ContextPicker.prefs(c).edit().putString(K_RECENT, sb.toString()).apply();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inflater.inflate(R.layout.fragment_config_clientes, container, false);
        api = ApiClient.getApiService();
        recentList = v.findViewById(R.id.recentList);
        allList = v.findViewById(R.id.allList);
        lblRecent = v.findViewById(R.id.lblRecent);
        lblAll = v.findViewById(R.id.lblAll);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        etSearch = v.findViewById(R.id.etSearch);
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> ((MainActivity) requireActivity()).confirmLogout());
        v.findViewById(R.id.fabNew).setOnClickListener(x -> newCustomer());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                if (s.length() >= 2 && devices == null) loadDevices();
                render();
            }
        });
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        loadCustomers();
    }

    // ───────────────────────── datos ─────────────────────────

    private void loadCustomers() {
        api.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override public void onResponse(Call<List<CustomerViewModel>> c, Response<List<CustomerViewModel>> r) {
                if (!isAdded()) return;
                if (!r.isSuccessful() || r.body() == null) { Forms.toast(getContext(), Forms.error("No se pudo cargar la lista de clientes", r)); return; }
                customers.clear();
                for (CustomerViewModel cv : r.body()) {
                    if (cv.getId() == null || cv.getId() != ContextPicker.ADMIN_CLIENT_ID) customers.add(cv); // la cuenta del admin no se lista
                }
                Collections.sort(customers, (a, bb) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(bb.getName())));
                render();
            }
            @Override public void onFailure(Call<List<CustomerViewModel>> c, Throwable t) {
                if (isAdded()) Forms.toast(getContext(), "Sin conexión");
            }
        });
    }

    /** Todos los dispositivos, para buscar por serie o nombre de dispositivo. */
    private void loadDevices() {
        if (devicesLoading) return;
        devicesLoading = true;
        api.getAllDevices().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override public void onResponse(Call<List<Map<String, Object>>> c, Response<List<Map<String, Object>>> r) {
                devicesLoading = false;
                devices = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<>();
                if (isAdded()) render();
            }
            @Override public void onFailure(Call<List<Map<String, Object>>> c, Throwable t) {
                devicesLoading = false;
                devices = new ArrayList<>();
            }
        });
    }

    // ───────────────────────── pintar ─────────────────────────

    private CustomerViewModel byId(int id) {
        for (CustomerViewModel c : customers) if (c.getId() != null && c.getId() == id) return c;
        return null;
    }

    private void render() {
        if (getView() == null) return;
        String q = etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);

        // Recientes: sólo sin búsqueda activa
        recentList.removeAllViews();
        boolean showRecent = false;
        if (q.isEmpty()) {
            int i = 0;
            for (String s : ContextPicker.prefs(requireContext()).getString(K_RECENT, "").split(",")) {
                if (s.isEmpty()) continue;
                CustomerViewModel cv = byId(Integer.parseInt(s));
                if (cv == null) continue;
                recentList.addView(recentCard(cv, i++));
                showRecent = true;
            }
        }
        lblRecent.setVisibility(showRecent ? View.VISIBLE : View.GONE);
        lblAll.setText(q.isEmpty() ? "TODOS · A–Z" : "RESULTADOS");

        // Lista A–Z (filtrada por nombre/correo y, si hay búsqueda, por serie o dispositivo)
        Map<Integer, String> deviceHits = q.length() >= 2 ? matchDevices(q) : new LinkedHashMap<>();
        allList.removeAllViews();
        int shown = 0;
        for (CustomerViewModel cv : customers) {
            String hit = cv.getId() != null ? deviceHits.get(cv.getId()) : null;
            boolean match = q.isEmpty()
                    || lower(cv.getName()).contains(q) || lower(cv.getEmail()).contains(q) || hit != null;
            if (!match) continue;
            if (shown++ > 0) {
                View line = new View(requireContext());
                line.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.am_line));
                allList.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            View row = LayoutInflater.from(requireContext()).inflate(R.layout.item_client_row, allList, false);
            ((TextView) row.findViewById(R.id.tvName)).setText(cv.getName());
            ((TextView) row.findViewById(R.id.tvSub)).setText(hit != null && !lower(cv.getName()).contains(q) ? "Dispositivo: " + hit : subtitle(cv));
            row.setOnClickListener(x -> open(cv));
            allList.addView(row);
        }
        tvEmpty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
        allList.setVisibility(shown == 0 ? View.GONE : View.VISIBLE);
    }

    private static String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }

    private static String subtitle(CustomerViewModel cv) {
        String email = cv.getEmail() != null ? cv.getEmail() : "", state = cv.getState() != null ? cv.getState() : "";
        return email.isEmpty() ? state : state.isEmpty() ? email : email + " · " + state;
    }

    /** idCliente → "nombre · serie" de un dispositivo que coincide con la búsqueda (sin depender del formato exacto de la respuesta). */
    private Map<Integer, String> matchDevices(String q) {
        Map<Integer, String> hits = new LinkedHashMap<>();
        if (devices == null) return hits;
        for (Map<String, Object> d : devices) {
            Integer client = null;
            String name = "", serie = "";
            for (Map.Entry<String, Object> e : d.entrySet()) {
                String k = e.getKey().toLowerCase(Locale.ROOT);
                Object val = e.getValue();
                if ((k.equals("idclient") || k.equals("idcustomer")) && val instanceof Number) client = ((Number) val).intValue();
                else if (k.contains("serie") || k.contains("serial")) serie = String.valueOf(val);
                else if (k.equals("name") || k.equals("nodename") || k.equals("devicename")) name = String.valueOf(val);
            }
            if (client != null && !hits.containsKey(client) && (lower(name).contains(q) || lower(serie).contains(q))) {
                hits.put(client, name + (serie.isEmpty() || serie.equals("null") ? "" : " · " + serie));
            }
        }
        return hits;
    }

    private View recentCard(final CustomerViewModel cv, int index) {
        View card = LayoutInflater.from(requireContext()).inflate(R.layout.item_client_recent, recentList, false);
        TextView ini = card.findViewById(R.id.tvInitials);
        ini.setText(initials(cv.getName()));
        boolean blue = index % 2 == 1;
        ini.setBackgroundResource(blue ? R.drawable.am_icon_cfe : R.drawable.am_icon_solar);
        ini.setTextColor(ContextCompat.getColor(requireContext(), blue ? R.color.am_cfe_text : R.color.am_accent_dark));
        ((TextView) card.findViewById(R.id.tvName)).setText(cv.getName());
        card.setOnClickListener(x -> open(cv));
        loadSummary(cv, (TextView) card.findViewById(R.id.tvSummary), (TextView) card.findViewById(R.id.tvBadge));
        return card;
    }

    /** "1 localidad · 1 circuito · 6 dispositivos" y el badge: "N ●" rojo si hay nodos sin reportar, "OK" verde si no. */
    private void loadSummary(final CustomerViewModel cv, final TextView summary, final TextView badge) {
        final int[] n = new int[4]; // localidades, circuitos, dispositivos, sin reportar
        final int[] pending = {3};
        final Runnable done = () -> {
            if (--pending[0] > 0 || !isAdded()) return;
            summary.setText(plural(n[0], "localidad", "localidades") + " · " + plural(n[1], "circuito", "circuitos")
                    + " · " + plural(n[2], "dispositivo", "dispositivos"));
            badge.setVisibility(View.VISIBLE);
            if (n[3] > 0) {
                badge.setText(n[3] + " ●");
                badge.setBackgroundResource(R.drawable.am_badge_err);
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_err_text));
            } else {
                badge.setText("OK");
                badge.setBackgroundResource(R.drawable.am_badge_ok);
                badge.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_ok_text));
            }
        };
        api.getCustomerLocations(cv.getId()).enqueue(new Callback<List<LocationsViewModel>>() {
            @Override public void onResponse(Call<List<LocationsViewModel>> c, Response<List<LocationsViewModel>> r) {
                if (r.isSuccessful() && r.body() != null) n[0] = r.body().size();
                done.run();
            }
            @Override public void onFailure(Call<List<LocationsViewModel>> c, Throwable t) { done.run(); }
        });
        api.getCustomerCircuitsForAdmin(cv.getId()).enqueue(new Callback<List<CircuitsViewModel>>() {
            @Override public void onResponse(Call<List<CircuitsViewModel>> c, Response<List<CircuitsViewModel>> r) {
                if (r.isSuccessful() && r.body() != null) n[1] = r.body().size();
                done.run();
            }
            @Override public void onFailure(Call<List<CircuitsViewModel>> c, Throwable t) { done.run(); }
        });
        api.getCustomerNodesTrafficLight(cv.getId()).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getNodes() != null) {
                    n[2] = r.body().getNodes().size();
                    for (NodeTrafficLight node : r.body().getNodes()) if (!node.isGreen()) n[3]++;
                }
                done.run();
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { done.run(); }
        });
    }

    private static String plural(int n, String one, String many) { return n + " " + (n == 1 ? one : many); }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] p = name.trim().split("\\s+");
        return (p[0].substring(0, 1) + (p.length > 1 ? p[1].substring(0, 1) : p[0].length() > 1 ? p[0].substring(1, 2) : "")).toUpperCase();
    }

    // ───────────────────────── acciones ─────────────────────────

    private void open(CustomerViewModel cv) {
        if (cv.getId() == null) return;
        markRecent(requireContext(), cv.getId());
        ((MainActivity) requireActivity()).push(ClienteArbolFragment.newInstance(cv.getId(), cv.getName()));
    }

    private void newCustomer() {
        Forms.show(requireContext(), "Nuevo cliente", new String[]{"Nombre", "Correo", "Teléfono", "Estado"},
                null, 2, "Crear", v -> api.addNewCustomer(new CustomerViewModel(v[0], v[1], v[2], v[3])).enqueue(new Callback<Void>() {
                    @Override public void onResponse(Call<Void> c, Response<Void> r) {
                        if (!isAdded()) return;
                        if (r.isSuccessful()) { Forms.toast(getContext(), "Cliente creado"); loadCustomers(); }
                        else Forms.toast(getContext(), Forms.error("No se pudo crear el cliente", r));
                    }
                    @Override public void onFailure(Call<Void> c, Throwable t) { if (isAdded()) Forms.toast(getContext(), "Sin conexión"); }
                }));
    }
}
