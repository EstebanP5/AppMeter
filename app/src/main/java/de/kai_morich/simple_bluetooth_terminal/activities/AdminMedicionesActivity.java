package de.kai_morich.simple_bluetooth_terminal.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.NodeDetailBottomSheet;
import de.kai_morich.simple_bluetooth_terminal.NodeTrafficLightAdapter;
import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.EnergyFlowTotals;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsEnergyResponse;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Pantalla de mediciones en tiempo real para el Installer Admin.
 * Solo accesible si isInstallerAdmin() == true.
 */
public class AdminMedicionesActivity extends AppCompatActivity
        implements NodeTrafficLightAdapter.OnNodeClickListener {

    private static final long REFRESH_INTERVAL_MS = 30_000L;

    // ---- Views ----
    private Spinner spinnerCliente, spinnerLocalidad;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView rvNodes;
    private NodeTrafficLightAdapter nodeAdapter;

    // Energy summary cards
    private TextView tvSolar, tvCfe, tvConsumo, tvInyeccion, tvBateria;
    private View cardBattery, cardInyeccion;
    private TextView tvNodeSummary;

    // ---- State ----
    private ApiService apiService;
    private TokenManager tokenManager;
    private int selectedCustomerId = -1;
    private int selectedLocationId = -1;

    private List<CustomerViewModel> clientes = new ArrayList<>();
    private List<LocationsViewModel> localidades = new ArrayList<>();

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = () -> {
        loadAllData();
        scheduleRefresh();
    };

    // -----------------------------------------------
    // Lifecycle
    // -----------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_mediciones);

        tokenManager = new TokenManager(this);
        apiService   = ApiClient.getApiService();

        initViews();
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

    // -----------------------------------------------
    // Init
    // -----------------------------------------------

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mediciones Admin");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerCliente   = findViewById(R.id.spinnerCliente);
        spinnerLocalidad = findViewById(R.id.spinnerLocalidad);
        swipeRefresh     = findViewById(R.id.swipeRefreshLayout);
        rvNodes          = findViewById(R.id.rvNodes);
        tvNodeSummary    = findViewById(R.id.tvNodeSummary);

        // Energy cards
        tvSolar     = findViewById(R.id.tvSolar);
        tvCfe       = findViewById(R.id.tvCfe);
        tvConsumo   = findViewById(R.id.tvConsumo);
        tvInyeccion = findViewById(R.id.tvInyeccion);
        tvBateria   = findViewById(R.id.tvBateria);
        cardBattery = findViewById(R.id.cardBattery);
        cardInyeccion = findViewById(R.id.cardInyeccion);

        rvNodes.setLayoutManager(new LinearLayoutManager(this));
        nodeAdapter = new NodeTrafficLightAdapter(this, this);
        rvNodes.setAdapter(nodeAdapter);

        swipeRefresh.setColorSchemeResources(R.color.web_accent);
        swipeRefresh.setOnRefreshListener(this::loadAllData);
    }

    // -----------------------------------------------
    // Data
    // -----------------------------------------------

    private void loadCustomers() {
        apiService.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call,
                                   Response<List<CustomerViewModel>> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null) {
                    clientes = response.body();
                    setupClienteSpinner();
                }
            }
            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                Toast.makeText(AdminMedicionesActivity.this,
                        "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupClienteSpinner() {
        List<String> names = new ArrayList<>();
        for (CustomerViewModel c : clientes) names.add(c.getName());
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
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
        if (!clientes.isEmpty()) {
            selectedCustomerId = clientes.get(0).getId();
            loadLocalidades(selectedCustomerId);
        }
    }

    private void loadLocalidades(int idCustomer) {
        apiService.getCustomerLocations(idCustomer)
                .enqueue(new Callback<List<LocationsViewModel>>() {
                    @Override
                    public void onResponse(Call<List<LocationsViewModel>> call,
                                           Response<List<LocationsViewModel>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            localidades = response.body();
                            setupLocalidadSpinner();
                        }
                    }
                    @Override
                    public void onFailure(Call<List<LocationsViewModel>> call, Throwable t) { }
                });
    }

    private void setupLocalidadSpinner() {
        List<String> names = new ArrayList<>();
        names.add("Todas las localidades");
        for (LocationsViewModel l : localidades) names.add(l.getName());
        ArrayAdapter<String> a = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocalidad.setAdapter(a);
        spinnerLocalidad.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedLocationId = (pos == 0) ? -1
                        : localidades.get(pos - 1).getIdLocation();
                loadAllData();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        // Load data immediately with first selection
        loadAllData();
    }

    private void loadAllData() {
        if (selectedCustomerId <= 0) return;
        swipeRefresh.setRefreshing(true);
        loadEnergyTotals();
        loadTrafficLight();
    }

    private void loadEnergyTotals() {
        apiService.getCustomerLocationsEnergyPowerTotals(selectedCustomerId)
                .enqueue(new Callback<LocationsEnergyResponse>() {
                    @Override
                    public void onResponse(Call<LocationsEnergyResponse> call,
                                           Response<LocationsEnergyResponse> response) {
                        swipeRefresh.setRefreshing(false);
                        if (response.code() == 401) { handleUnauthorized(); return; }
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getTotals() != null) {
                            bindEnergyTotals(response.body().getTotals());
                        }
                    }
                    @Override
                    public void onFailure(Call<LocationsEnergyResponse> call, Throwable t) {
                        swipeRefresh.setRefreshing(false);
                    }
                });
    }

    private void bindEnergyTotals(EnergyFlowTotals t) {
        tvSolar.setText(String.format("%.2f kW", t.getPanelsW() / 1000.0));
        tvCfe.setText(String.format("%.2f kW", t.getCfeW() / 1000.0));
        tvConsumo.setText(String.format("%.2f kW", t.getLocalityW() / 1000.0));
        tvInyeccion.setText(String.format("%.2f kW", t.getInjectionW() / 1000.0));
        tvBateria.setText(String.format("%.2f kW", t.getBessW() / 1000.0));
        cardBattery.setVisibility(t.getHasBess() ? View.VISIBLE : View.GONE);
    }

    private void loadTrafficLight() {
        apiService.getCustomerNodesTrafficLight(selectedCustomerId)
                .enqueue(new Callback<NodeTrafficLightResponse>() {
                    @Override
                    public void onResponse(Call<NodeTrafficLightResponse> call,
                                           Response<NodeTrafficLightResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getNodes() != null) {
                            List<NodeTrafficLight> nodes = response.body().getNodes();
                            nodeAdapter.setData(nodes);
                            updateNodeSummary();
                        }
                    }
                    @Override
                    public void onFailure(Call<NodeTrafficLightResponse> call, Throwable t) { }
                });
    }

    private void updateNodeSummary() {
        int green  = nodeAdapter.countByStatus("Green");
        int yellow = nodeAdapter.countByStatus("Yellow");
        int red    = nodeAdapter.countByStatus("Red");
        tvNodeSummary.setText(green + " ● / " + yellow + " ● / " + red + " ●");
    }

    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    // -----------------------------------------------
    // OnNodeClickListener
    // -----------------------------------------------

    @Override
    public void onNodeClick(NodeTrafficLight node) {
        NodeDetailBottomSheet sheet = NodeDetailBottomSheet.newInstance(node, selectedCustomerId);
        sheet.show(getSupportFragmentManager(), "node_detail");
    }

    // -----------------------------------------------
    // Helpers
    // -----------------------------------------------

    private void handleUnauthorized() {
        tokenManager.clearToken();
        Intent i = new Intent(this, LoginActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
