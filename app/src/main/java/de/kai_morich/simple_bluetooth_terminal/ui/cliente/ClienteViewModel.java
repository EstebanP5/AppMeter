package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Estado compartido de la vista Cliente: quién es, sus localidades (sólo las asignadas) y la localidad activa.
 * El token ya trae el IdClient, así que el cliente nunca elige "cliente", sólo localidad.
 */
public class ClienteViewModel extends ViewModel {

    public final MutableLiveData<String> customerName = new MutableLiveData<>();
    public final MutableLiveData<List<LocationsViewModel>> locations = new MutableLiveData<>(new ArrayList<>());
    /** Localidad activa; null hasta resolverla. Los fragmentos recargan cuando cambia. */
    public final MutableLiveData<LocationsViewModel> location = new MutableLiveData<>();
    /** true si ya terminó de cargar (con o sin localidades), para mostrar el estado vacío. */
    public final MutableLiveData<Boolean> loaded = new MutableLiveData<>(false);

    public Integer customerId;
    public final List<CircuitsViewModel> circuits = new ArrayList<>();
    private boolean started;
    private SharedPreferences prefs;
    private final ApiService api = ApiClient.getApiService();

    public ApiService api() { return api; }

    public void start(Context ctx) {
        if (started) return;
        started = true;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
        String token = new TokenManager(ctx).getToken();
        customerId = JwtUtils.getIdCustomer(token);
        customerName.setValue(JwtUtils.getUserName(token));
        if (customerId == null) { loaded.setValue(true); return; }

        api.getCustomerInformation(customerId).enqueue(new Callback<CustomerViewModel>() {
            @Override public void onResponse(Call<CustomerViewModel> c, Response<CustomerViewModel> r) {
                if (r.isSuccessful() && r.body() != null && r.body().getName() != null) customerName.setValue(r.body().getName());
            }
            @Override public void onFailure(Call<CustomerViewModel> c, Throwable t) { /* queda el nombre del token */ }
        });
        // Circuitos primero: cuando la localidad se publica, los fragmentos ya pueden resolver su circuito
        api.getCustomerCircuits(customerId).enqueue(new Callback<List<CircuitsViewModel>>() {
            @Override public void onResponse(Call<List<CircuitsViewModel>> c, Response<List<CircuitsViewModel>> r) {
                if (r.isSuccessful() && r.body() != null) circuits.addAll(r.body());
                loadLocations();
            }
            @Override public void onFailure(Call<List<CircuitsViewModel>> c, Throwable t) { loadLocations(); }
        });
    }

    private void loadLocations() {
        api.getCustomerLocations(customerId).enqueue(new Callback<List<LocationsViewModel>>() {
            @Override public void onResponse(Call<List<LocationsViewModel>> c, Response<List<LocationsViewModel>> r) {
                List<LocationsViewModel> list = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<>();
                locations.setValue(list);
                if (!list.isEmpty()) {
                    int stored = prefs.getInt("current_location_id", -1);
                    LocationsViewModel pick = list.get(0);
                    for (LocationsViewModel l : list) if (Objects.equals(l.getIdLocation(), stored)) { pick = l; break; }
                    select(pick);
                }
                loaded.setValue(true);
            }
            @Override public void onFailure(Call<List<LocationsViewModel>> c, Throwable t) { loaded.setValue(true); }
        });
    }

    public void select(LocationsViewModel l) {
        if (l.getIdLocation() != null) prefs.edit().putInt("current_location_id", l.getIdLocation()).apply();
        location.setValue(l);
    }

    public Integer locationId() {
        LocationsViewModel l = location.getValue();
        return l != null ? l.getIdLocation() : null;
    }

    /** Circuitos de la localidad activa. */
    public List<Integer> circuitIds() {
        List<Integer> out = new ArrayList<>();
        Integer loc = locationId();
        for (CircuitsViewModel c : circuits) if (c.getIdCircuit() != null && Objects.equals(c.getIdLocation(), loc)) out.add(c.getIdCircuit());
        return out;
    }

    /** Nodos de la localidad activa con su semáforo; entrega null si falla la red. */
    public void fetchNodes(final Callback2<List<NodeTrafficLight>> cb) {
        if (customerId == null) { cb.done(null); return; }
        final Integer loc = locationId();
        api.getCustomerNodesTrafficLight(customerId).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                if (!r.isSuccessful() || r.body() == null || r.body().getNodes() == null) { cb.done(null); return; }
                List<NodeTrafficLight> out = new ArrayList<>();
                for (NodeTrafficLight n : r.body().getNodes()) {
                    if (loc == null || n.getIdLocation() == null || n.getIdLocation().equals(loc)) out.add(n);
                }
                cb.done(out);
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { cb.done(null); }
        });
    }

    public interface Callback2<T> { void done(T value); }

    public static List<NodeTrafficLight> ofKind(List<NodeTrafficLight> nodes, int kind) {
        List<NodeTrafficLight> out = new ArrayList<>();
        if (nodes != null) for (NodeTrafficLight n : nodes) if (NodeKind.of(n) == kind) out.add(n);
        return out;
    }
}
