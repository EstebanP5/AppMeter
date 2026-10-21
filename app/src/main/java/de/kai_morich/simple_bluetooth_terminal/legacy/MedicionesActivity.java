package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.LocationEnergyPower;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsEnergyPowerResponse;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import de.kai_morich.simple_bluetooth_terminal.ui.login.LoginActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MedicionesActivity extends AppCompatActivity
        implements NodeTrafficLightAdapter.OnNodeClickListener,
                   DeviceCurrentCardAdapter.OnHistoricoClickListener,
                   DeviceCurrentCardAdapter.OnNodeClickListener {

    private static final String TAG = "MedicionesActivity";
    private static final long REFRESH_INTERVAL_MS = 60_000L;

    // Views
    private LinearLayout layoutClienteSpinner;
    private Spinner spinnerCliente, spinnerLocalidad;
    private MaterialButton btnConsultar;
    private SwipeRefreshLayout swipeRefresh;
    private CardView cardEnergySummary, cardNodeStatus;
    private TextView tvSolarW, tvSolarWhDay, tvCfeW, tvCfeWhDay, tvConsumoTotal;
    private TextView tvInyeccionW, tvBessW;
    private LinearLayout layoutBess;
    private TextView tvNodeStatusTitle, tvNodosTitle, tvEmpty;
    private RecyclerView rvNodeStatus, rvDeviceCurrent;

    // Adapters
    private NodeTrafficLightAdapter nodeStatusAdapter;
    private DeviceCurrentCardAdapter deviceCardAdapter;

    // State
    private ApiService apiService;
    private TokenManager tokenManager;
    private boolean isAdmin;
    private int selectedCustomerId = -1;
    private int selectedLocationId = -1;

    private List<CustomerViewModel> clientes = new ArrayList<>();
    private List<LocationsViewModel> localidades = new ArrayList<>();
    private List<CircuitsViewModel> circuitos = new ArrayList<>();
    private List<NodeTrafficLight> trafficLightNodes = new ArrayList<>();
    private List<DeviceCurrent> allDeviceCurrent = new ArrayList<>();

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = () -> {
        refreshData();
        scheduleRefresh();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mediciones);

        tokenManager = new TokenManager(this);
        apiService = ApiClient.getApiService();

        String token = tokenManager.getToken();
        isAdmin = JwtUtils.isInstallerAdmin(token);
        Log.d(TAG, "isAdmin=" + isAdmin + " token=" + (token != null ? "present" : "null"));

        initViews();

        // Always load customers (like DashboardFragment does)
        // For admin: show spinner and let them pick
        // For non-admin: hide spinner, auto-select first customer
        loadCustomers();
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mediciones");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        layoutClienteSpinner = findViewById(R.id.layoutClienteSpinner);
        spinnerCliente       = findViewById(R.id.spinnerCliente);
        spinnerLocalidad     = findViewById(R.id.spinnerLocalidad);
        btnConsultar         = findViewById(R.id.btnConsultar);
        swipeRefresh         = findViewById(R.id.swipeRefreshLayout);

        cardEnergySummary = findViewById(R.id.cardEnergySummary);
        tvSolarW          = findViewById(R.id.tvSolarW);
        tvSolarWhDay      = findViewById(R.id.tvSolarWhDay);
        tvCfeW            = findViewById(R.id.tvCfeW);
        tvCfeWhDay        = findViewById(R.id.tvCfeWhDay);
        tvConsumoTotal    = findViewById(R.id.tvConsumoTotal);
        tvInyeccionW      = findViewById(R.id.tvInyeccionW);
        tvBessW           = findViewById(R.id.tvBessW);
        layoutBess        = findViewById(R.id.layoutBess);

        cardNodeStatus    = findViewById(R.id.cardNodeStatus);
        tvNodeStatusTitle = findViewById(R.id.tvNodeStatusTitle);
        rvNodeStatus      = findViewById(R.id.rvNodeStatus);

        tvNodosTitle      = findViewById(R.id.tvNodosTitle);
        rvDeviceCurrent   = findViewById(R.id.rvDeviceCurrent);
        tvEmpty           = findViewById(R.id.tvEmpty);

        // Admin spinner visibility - starts hidden, shown after loadCustomers if multiple
        layoutClienteSpinner.setVisibility(View.GONE);

        // Node status adapter
        nodeStatusAdapter = new NodeTrafficLightAdapter(this, this);
        rvNodeStatus.setLayoutManager(new LinearLayoutManager(this));
        rvNodeStatus.setAdapter(nodeStatusAdapter);

        // Device current adapter
        deviceCardAdapter = new DeviceCurrentCardAdapter(this, this, this);
        rvDeviceCurrent.setLayoutManager(new LinearLayoutManager(this));
        rvDeviceCurrent.setAdapter(deviceCardAdapter);

        // Consultar button
        btnConsultar.setOnClickListener(v -> onConsultar());

        // Swipe refresh
        swipeRefresh.setColorSchemeResources(R.color.web_accent);
        swipeRefresh.setProgressBackgroundColorSchemeColor(0xFF242E42);
        swipeRefresh.setOnRefreshListener(this::refreshData);
    }

    // ---- Data loading ----

    private void loadCustomers() {
        Log.d(TAG, "loadCustomers() called");
        apiService.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call,
                                   Response<List<CustomerViewModel>> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                Log.d(TAG, "getAllCustomers response code=" + response.code()
                        + " body=" + (response.body() != null ? response.body().size() : "null"));
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    clientes = response.body();
                    if (clientes.size() > 1) {
                        // Multiple customers: show spinner for selection
                        layoutClienteSpinner.setVisibility(View.VISIBLE);
                        setupClienteSpinner();
                    } else {
                        // Single customer: auto-select and load locations
                        layoutClienteSpinner.setVisibility(View.GONE);
                        selectedCustomerId = clientes.get(0).getId();
                        Log.d(TAG, "Auto-selected single customerId=" + selectedCustomerId);
                        loadLocalidades(selectedCustomerId);
                    }
                } else {
                    Toast.makeText(MedicionesActivity.this,
                            "No se encontraron clientes", Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                Log.e(TAG, "Error loading customers", t);
                Toast.makeText(MedicionesActivity.this,
                        "Error cargando clientes: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupClienteSpinner() {
        List<String> names = new ArrayList<>();
        for (CustomerViewModel c : clientes) names.add(c.getName() != null ? c.getName() : "Sin nombre");
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                return tv;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCliente.setAdapter(a);
        spinnerCliente.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedCustomerId = clientes.get(pos).getId();
                loadLocalidades(selectedCustomerId);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void loadLocalidades(int idCustomer) {
        Log.d(TAG, "loadLocalidades idCustomer=" + idCustomer);
        apiService.getCustomerLocations(idCustomer)
                .enqueue(new Callback<List<LocationsViewModel>>() {
                    @Override
                    public void onResponse(Call<List<LocationsViewModel>> call,
                                           Response<List<LocationsViewModel>> response) {
                        Log.d(TAG, "Localidades response code=" + response.code()
                                + " body=" + (response.body() != null ? response.body().size() : "null"));
                        if (response.isSuccessful() && response.body() != null) {
                            localidades = response.body();
                            setupLocalidadSpinner();
                        } else {
                            Toast.makeText(MedicionesActivity.this,
                                    "Sin localidades (código " + response.code() + ")",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<List<LocationsViewModel>> call, Throwable t) {
                        Log.e(TAG, "Error cargando localidades", t);
                        Toast.makeText(MedicionesActivity.this,
                                "Error cargando localidades: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void setupLocalidadSpinner() {
        List<String> names = new ArrayList<>();
        for (LocationsViewModel l : localidades) names.add(l.getName() != null ? l.getName() : "Sin nombre");
        ArrayAdapter<String> a = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, names) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                return tv;
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
                tv.setTextColor(Color.BLACK);
                return tv;
            }
        };
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocalidad.setAdapter(a);
        spinnerLocalidad.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedLocationId = localidades.get(pos).getIdLocation();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (!localidades.isEmpty()) {
            selectedLocationId = localidades.get(0).getIdLocation();
            // Auto-trigger data load for non-admin users
            if (!isAdmin) {
                Log.d(TAG, "Auto-triggering onConsultar for non-admin");
                onConsultar();
            }
        }
    }

    private void onConsultar() {
        if (selectedCustomerId <= 0 || selectedLocationId <= 0) {
            Toast.makeText(this, "Selecciona cliente y localidad", Toast.LENGTH_SHORT).show();
            return;
        }
        swipeRefresh.setRefreshing(true);
        tvEmpty.setVisibility(View.GONE);

        // Step 3: Load circuits, then execute parallel data loads
        loadCircuitsThenData();
    }

    private void refreshData() {
        if (selectedCustomerId <= 0 || selectedLocationId <= 0) {
            swipeRefresh.setRefreshing(false);
            return;
        }
        swipeRefresh.setRefreshing(true);
        loadCircuitsThenData();
    }

    private void loadCircuitsThenData() {
        apiService.getCustomerCircuits(selectedCustomerId)
                .enqueue(new Callback<List<CircuitsViewModel>>() {
                    @Override
                    public void onResponse(Call<List<CircuitsViewModel>> call,
                                           Response<List<CircuitsViewModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            // Filter circuits for selected location
                            circuitos.clear();
                            for (CircuitsViewModel c : response.body()) {
                                if (c.getIdLocation() != null &&
                                    c.getIdLocation().equals(selectedLocationId)) {
                                    circuitos.add(c);
                                }
                            }
                            // Now load all data in parallel
                            loadEnergyTotals();
                            loadTrafficLight();
                            loadDeviceCurrentForAllCircuits();
                        } else {
                            swipeRefresh.setRefreshing(false);
                        }
                    }
                    @Override
                    public void onFailure(Call<List<CircuitsViewModel>> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(MedicionesActivity.this,
                                "Error cargando circuitos", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // ---- Step 4: Energy totals ----
    private void loadEnergyTotals() {
        apiService.getLocationsEnergyPowerTotals(selectedCustomerId)
                .enqueue(new Callback<LocationsEnergyPowerResponse>() {
                    @Override
                    public void onResponse(Call<LocationsEnergyPowerResponse> call,
                                           Response<LocationsEnergyPowerResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getLocations() != null) {
                            LocationEnergyPower loc = null;
                            for (LocationEnergyPower l : response.body().getLocations()) {
                                if (l.getIdLocation() != null
                                        && l.getIdLocation().equals(selectedLocationId)) {
                                    loc = l;
                                    break;
                                }
                            }
                            if (loc != null) {
                                bindEnergySummary(loc);
                            }
                        }
                        checkStopRefreshing();
                    }
                    @Override
                    public void onFailure(Call<LocationsEnergyPowerResponse> call, Throwable t) {
                        Log.e(TAG, "Error energy totals", t);
                        checkStopRefreshing();
                    }
                });
    }

    private void bindEnergySummary(LocationEnergyPower loc) {
        cardEnergySummary.setVisibility(View.VISIBLE);
        tvSolarW.setText(formatPower(loc.getPanelsW()));
        tvSolarWhDay.setText("Hoy: " + formatEnergy(loc.getPanelsWhDay()));
        tvCfeW.setText(formatPower(loc.getCfeW()));
        tvCfeWhDay.setText("Hoy: " + formatEnergy(loc.getCfeWhDay()));
        tvConsumoTotal.setText(formatPower(loc.getLocalityW()));
        tvInyeccionW.setText(formatPower(loc.getInjectionW()));

        if (loc.getHasBess()) {
            layoutBess.setVisibility(View.VISIBLE);
            tvBessW.setText(formatPower(loc.getBessW()));
        } else {
            layoutBess.setVisibility(View.GONE);
        }
    }

    // ---- Step 5: Traffic light ----
    private void loadTrafficLight() {
        apiService.getCustomerNodesTrafficLight(selectedCustomerId)
                .enqueue(new Callback<NodeTrafficLightResponse>() {
                    @Override
                    public void onResponse(Call<NodeTrafficLightResponse> call,
                                           Response<NodeTrafficLightResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getNodes() != null) {
                            trafficLightNodes.clear();
                            for (NodeTrafficLight n : response.body().getNodes()) {
                                if (n.getIdLocation() != null
                                        && n.getIdLocation().equals(selectedLocationId)) {
                                    trafficLightNodes.add(n);
                                }
                            }
                            bindTrafficLight();
                        }
                        checkStopRefreshing();
                    }
                    @Override
                    public void onFailure(Call<NodeTrafficLightResponse> call, Throwable t) {
                        Log.e(TAG, "Error traffic light", t);
                        checkStopRefreshing();
                    }
                });
    }

    private void bindTrafficLight() {
        if (trafficLightNodes.isEmpty()) {
            cardNodeStatus.setVisibility(View.GONE);
            return;
        }
        cardNodeStatus.setVisibility(View.VISIBLE);
        nodeStatusAdapter.setData(trafficLightNodes);

        int green = 0, yellow = 0, red = 0;
        for (NodeTrafficLight n : trafficLightNodes) {
            if (n.isGreen()) green++;
            else if (n.isYellow()) yellow++;
            else red++;
        }
        tvNodeStatusTitle.setText(String.format(Locale.US,
                "Estado de nodos • %d🟢 %d🟡 %d🔴", green, yellow, red));

        // Build status map for device cards (by IdMeasure + by Serie fallback)
        Map<Integer, String> map = new HashMap<>();
        for (NodeTrafficLight n : trafficLightNodes) {
            if (n.getIdNode() != null) {
                map.put(n.getIdNode(), n.getStatus());
            }
        }
        // Also build Serie-based map
        Map<String, String> serieMap = new HashMap<>();
        for (NodeTrafficLight n : trafficLightNodes) {
            if (n.getSerie() != null) {
                serieMap.put(n.getSerie(), n.getStatus());
            }
        }
        deviceCardAdapter.setStatusMap(map);
        deviceCardAdapter.setSerieStatusMap(serieMap);
    }

    // ---- Step 6: Device current for all circuits ----
    private int pendingCircuitCalls = 0;

    private void loadDeviceCurrentForAllCircuits() {
        allDeviceCurrent.clear();
        if (circuitos.isEmpty()) {
            deviceCardAdapter.setData(allDeviceCurrent);
            tvNodosTitle.setVisibility(View.GONE);
            checkStopRefreshing();
            return;
        }
        pendingCircuitCalls = circuitos.size();
        for (CircuitsViewModel c : circuitos) {
            apiService.getCustomerDevicesCurrentDetailed(
                    c.getIdCircuit(), selectedCustomerId, 0
            ).enqueue(new Callback<List<DeviceCurrent>>() {
                @Override
                public void onResponse(Call<List<DeviceCurrent>> call,
                                       Response<List<DeviceCurrent>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        allDeviceCurrent.addAll(response.body());
                    }
                    onCircuitCallDone();
                }
                @Override
                public void onFailure(Call<List<DeviceCurrent>> call, Throwable t) {
                    Log.e(TAG, "Error device current", t);
                    onCircuitCallDone();
                }
            });
        }
    }

    private synchronized void onCircuitCallDone() {
        pendingCircuitCalls--;
        if (pendingCircuitCalls <= 0) {
            runOnUiThread(() -> {
                deviceCardAdapter.setData(allDeviceCurrent);
                tvNodosTitle.setVisibility(allDeviceCurrent.isEmpty() ? View.GONE : View.VISIBLE);
                checkStopRefreshing();
            });
        }
    }

    private int completedCalls = 0;

    private synchronized void checkStopRefreshing() {
        completedCalls++;
        // Energy + TrafficLight + DeviceCurrent = 3 parallel streams
        if (completedCalls >= 3) {
            completedCalls = 0;
            runOnUiThread(() -> swipeRefresh.setRefreshing(false));
        }
    }

    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    // ---- Listeners ----

    @Override
    public void onNodeClick(NodeTrafficLight node) {
        // Navigate to historico for this node
        Intent intent = new Intent(this, HistoricoActivity.class);
        intent.putExtra("idMeasure", node.getIdNode() != null ? node.getIdNode() : 0);
        intent.putExtra("idCustomer", selectedCustomerId);
        intent.putExtra("idCircuit", node.getIdCircuit() != null ? node.getIdCircuit() : 0);
        intent.putExtra("idLocation", selectedLocationId);
        intent.putExtra("nodeName", node.getName());
        intent.putExtra("nodeType", node.getType() != null ? node.getType() : 0);
        startActivity(intent);
    }

    @Override
    public void onHistoricoClick(DeviceCurrent device) {
        Intent intent = new Intent(this, HistoricoActivity.class);
        intent.putExtra("idMeasure", device.getIdMeasure() != null ? device.getIdMeasure() : 0);
        intent.putExtra("idCustomer", selectedCustomerId);
        intent.putExtra("idCircuit", device.getIdCircuit() != null ? device.getIdCircuit() : 0);
        intent.putExtra("idLocation", selectedLocationId);
        intent.putExtra("nodeName", device.getName());
        intent.putExtra("nodeType", device.getType() != null ? device.getType() : 0);
        startActivity(intent);
    }

    @Override
    public void onNodeClick(DeviceCurrent device) {
        // Optional: show bottom sheet with device detail
    }

    // ---- Helpers ----

    private String formatPower(double w) {
        if (w == 0) return "—";
        if (Math.abs(w) >= 1000) return String.format(Locale.US, "%.1f kW", w / 1000.0);
        return String.format(Locale.US, "%.1f W", w);
    }

    private String formatEnergy(double wh) {
        if (wh == 0) return "—";
        if (Math.abs(wh) >= 1000) return String.format(Locale.US, "%.1f kWh", wh / 1000.0);
        return String.format(Locale.US, "%.1f Wh", wh);
    }

    private void handleUnauthorized() {
        Toast.makeText(this, "Sesión expirada", Toast.LENGTH_SHORT).show();
        tokenManager.clearToken();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
