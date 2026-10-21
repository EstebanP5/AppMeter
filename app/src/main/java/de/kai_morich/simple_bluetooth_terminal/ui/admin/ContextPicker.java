package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.preference.PreferenceManager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Contexto del admin: cliente + localidad activos, compartidos entre Inicio y Mediciones (se guardan en las
 * preferencias) y el selector con búsqueda (cliente → localidad) que lo cambia.
 */
public final class ContextPicker {

    /** Cuenta propia del admin: no es un cliente real y se oculta de las listas. */
    public static final int ADMIN_CLIENT_ID = 1010;

    private static final String K_CUSTOMER = "current_customer_id", K_LOCATION = "current_location_id",
            K_CUSTOMER_NAME = "current_customer_name", K_LOCATION_NAME = "current_location_name";

    public interface OnPick {
        void picked(int customerId, String customerName, int locationId, String locationName, List<LocationsViewModel> locations);
    }

    private ContextPicker() {}

    public interface Ready { void ready(int customerId, String customerName, int locationId, String locationName); }

    /** "La Italiana" → "LI": iniciales de las dos primeras palabras. */
    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] w = name.trim().split("\\s+");
        String a = w[0].substring(0, 1);
        String b = w.length > 1 ? w[1].substring(0, 1) : (w[0].length() > 1 ? w[0].substring(1, 2) : "");
        return (a + b).toUpperCase(Locale.ROOT);
    }

    /**
     * Sin contexto guardado: toma el primer cliente de la lista (sin la cuenta propia del admin) y su primera localidad,
     * lo guarda y avisa. No avisa si no hay clientes o falla la red.
     */
    public static void ensure(final Context ctx, final ApiService api, final Ready cb) {
        api.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override public void onResponse(Call<List<CustomerViewModel>> c, Response<List<CustomerViewModel>> r) {
                if (!r.isSuccessful() || r.body() == null) return;
                CustomerViewModel pick = null;
                for (CustomerViewModel cv : r.body()) {
                    if (cv.getId() != null && cv.getId() != ADMIN_CLIENT_ID) { pick = cv; break; }
                }
                if (pick == null) return;
                final CustomerViewModel chosen = pick;
                api.getCustomerLocations(chosen.getId()).enqueue(new Callback<List<LocationsViewModel>>() {
                    @Override public void onResponse(Call<List<LocationsViewModel>> c2, Response<List<LocationsViewModel>> r2) {
                        if (!r2.isSuccessful() || r2.body() == null || r2.body().isEmpty()) return;
                        LocationsViewModel l = r2.body().get(0);
                        save(ctx, chosen.getId(), chosen.getName(), l.getIdLocation(), l.getName());
                        cb.ready(chosen.getId(), chosen.getName(), l.getIdLocation(), l.getName());
                    }
                    @Override public void onFailure(Call<List<LocationsViewModel>> c2, Throwable t) { }
                });
            }
            @Override public void onFailure(Call<List<CustomerViewModel>> c, Throwable t) { }
        });
    }

    // ───────────────────────── contexto guardado ─────────────────────────

    public static SharedPreferences prefs(Context c) {
        return PreferenceManager.getDefaultSharedPreferences(c.getApplicationContext());
    }

    public static Integer customerId(Context c) {
        int v = prefs(c).getInt(K_CUSTOMER, -1);
        return v > 0 ? v : null;
    }

    public static Integer locationId(Context c) {
        int v = prefs(c).getInt(K_LOCATION, -1);
        return v > 0 ? v : null;
    }

    public static String customerName(Context c) { return prefs(c).getString(K_CUSTOMER_NAME, null); }

    public static String locationName(Context c) { return prefs(c).getString(K_LOCATION_NAME, null); }

    public static void save(Context c, Integer customerId, String customerName, Integer locationId, String locationName) {
        SharedPreferences.Editor e = prefs(c).edit();
        if (customerId != null) e.putInt(K_CUSTOMER, customerId); else e.remove(K_CUSTOMER);
        if (locationId != null) e.putInt(K_LOCATION, locationId); else e.remove(K_LOCATION);
        e.putString(K_CUSTOMER_NAME, customerName).putString(K_LOCATION_NAME, locationName).apply();
    }

    // ───────────────────────── selector ─────────────────────────

    public static void show(Activity act, OnPick cb) {
        View v = LayoutInflater.from(act).inflate(R.layout.sheet_context_picker, null);
        final BottomSheetDialog dialog = new BottomSheetDialog(act);
        dialog.setContentView(v);
        dialog.setOnShowListener(x -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent); // el contenido trae su fondo redondeado
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
                // La lista no avisa de su scroll a la hoja: al subir, la hoja interpretaba el gesto como "cerrar".
                // Sin arrastre la lista scrollea libre; la hoja se cierra con atrás o tocando fuera.
                behavior.setDraggable(false);
            }
        });

        final ApiService api = ApiClient.getApiService();
        final TextView title = v.findViewById(R.id.pickerTitle);
        final View back = v.findViewById(R.id.pickerBack);
        final EditText search = v.findViewById(R.id.pickerSearch);
        final ListView list = v.findViewById(R.id.pickerList);
        final List<String> labels = new ArrayList<>();
        final ArrayAdapter<String> adapter = new ArrayAdapter<>(act, R.layout.item_picker_row, labels);
        list.setAdapter(adapter);

        final List<CustomerViewModel> customers = new ArrayList<>();
        final List<LocationsViewModel> locations = new ArrayList<>();
        final List<Object> shown = new ArrayList<>();
        final CustomerViewModel[] picked = {null}; // cliente elegido: null = paso de clientes

        final Runnable refill = () -> {
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            labels.clear();
            shown.clear();
            if (picked[0] == null) {
                for (CustomerViewModel c : customers) {
                    if (c.getName() != null && c.getName().toLowerCase(Locale.ROOT).contains(q)) { labels.add(c.getName()); shown.add(c); }
                }
            } else {
                for (LocationsViewModel l : locations) {
                    if (l.getName() != null && l.getName().toLowerCase(Locale.ROOT).contains(q)) { labels.add(l.getName()); shown.add(l); }
                }
            }
            adapter.notifyDataSetChanged();
        };

        title.setText("Cliente");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refill.run(); }
        });
        back.setOnClickListener(x -> {
            picked[0] = null;
            title.setText("Cliente");
            back.setVisibility(View.GONE);
            search.setText("");
            refill.run();
        });

        api.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override public void onResponse(Call<List<CustomerViewModel>> call, Response<List<CustomerViewModel>> r) {
                if (!r.isSuccessful() || r.body() == null) return;
                for (CustomerViewModel c : r.body()) {
                    if (c.getId() == null || c.getId() != ADMIN_CLIENT_ID) customers.add(c);
                }
                Collections.sort(customers, (a, b) -> String.valueOf(a.getName()).compareToIgnoreCase(String.valueOf(b.getName())));
                refill.run();
            }
            @Override public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                Toast.makeText(act, "No se pudieron cargar los clientes.", Toast.LENGTH_SHORT).show();
            }
        });

        list.setOnItemClickListener((parent, view, pos, id) -> {
            Object item = shown.get(pos);
            if (item instanceof CustomerViewModel) {
                final CustomerViewModel c = (CustomerViewModel) item;
                api.getCustomerLocations(c.getId()).enqueue(new Callback<List<LocationsViewModel>>() {
                    @Override public void onResponse(Call<List<LocationsViewModel>> call, Response<List<LocationsViewModel>> r) {
                        List<LocationsViewModel> locs = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<>();
                        if (locs.isEmpty()) {
                            Toast.makeText(act, "El cliente no tiene localidades configuradas.", Toast.LENGTH_SHORT).show();
                        } else if (locs.size() == 1) {
                            cb.picked(c.getId(), c.getName(), locs.get(0).getIdLocation(), locs.get(0).getName(), locs);
                            dialog.dismiss();
                        } else {
                            picked[0] = c;
                            locations.clear();
                            locations.addAll(locs);
                            title.setText(c.getName());
                            back.setVisibility(View.VISIBLE);
                            search.setText("");
                            refill.run();
                        }
                    }
                    @Override public void onFailure(Call<List<LocationsViewModel>> call, Throwable t) {
                        Toast.makeText(act, "No se pudieron cargar las localidades.", Toast.LENGTH_SHORT).show();
                    }
                });
            } else {
                LocationsViewModel l = (LocationsViewModel) item;
                cb.picked(picked[0].getId(), picked[0].getName(), l.getIdLocation(), l.getName(), new ArrayList<>(locations));
                dialog.dismiss();
            }
        });
        dialog.show();
    }
}
