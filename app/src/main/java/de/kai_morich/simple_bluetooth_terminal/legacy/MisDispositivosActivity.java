package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.RegisteredSerial;
import de.kai_morich.simple_bluetooth_terminal.ui.login.LoginActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MisDispositivosActivity extends AppCompatActivity
        implements MisDispositivosAdapter.ActionListener {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView recyclerView;
    private MisDispositivosAdapter adapter;
    private ChipGroup chipGroupFilter;
    private Spinner spinnerCliente;

    private ApiService apiService;
    private TokenManager tokenManager;
    private boolean isAdmin;
    private int selectedCustomerId = -1;
    private Integer filterType = null; // null=TODOS, 0=METER, 255=HUB

    private List<CustomerViewModel> clientes = new ArrayList<>();

    // -----------------------------------------------
    // Lifecycle
    // -----------------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mis_dispositivos);

        tokenManager = new TokenManager(this);
        apiService   = ApiClient.getApiService();
        isAdmin      = JwtUtils.isInstallerAdmin(tokenManager.getToken());

        if (!isAdmin) {
            Integer id = JwtUtils.getIdCustomer(tokenManager.getToken());
            selectedCustomerId = id != null ? id : -1;
        }

        initViews();

        if (isAdmin) {
            loadCustomers();
        } else if (selectedCustomerId > 0) {
            loadSerials();
        }
    }

    // -----------------------------------------------
    // Init
    // -----------------------------------------------

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Mis Dispositivos");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        spinnerCliente = findViewById(R.id.spinnerCliente);
        spinnerCliente.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        chipGroupFilter       = findViewById(R.id.chipGroupFilter);
        swipeRefreshLayout    = findViewById(R.id.swipeRefreshLayout);
        recyclerView          = findViewById(R.id.recyclerView);
        FloatingActionButton fab = findViewById(R.id.fabAgregar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MisDispositivosAdapter(this, this);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setColorSchemeResources(R.color.web_accent);
        swipeRefreshLayout.setOnRefreshListener(this::loadSerials);

        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                filterType = null;
            } else {
                int id = checkedIds.get(0);
                if (id == R.id.chipMeter)     filterType = 0;
                else if (id == R.id.chipHub)  filterType = 255;
                else                          filterType = null;
            }
            loadSerials();
        });

        fab.setOnClickListener(v -> showRegistrarSerialDialog());
    }

    // -----------------------------------------------
    // Data loading
    // -----------------------------------------------

    private void loadCustomers() {
        apiService.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call,
                                   Response<List<CustomerViewModel>> response) {
                if (response.code() == 401) { handleUnauthorized(); return; }
                if (response.isSuccessful() && response.body() != null) {
                    clientes = response.body();
                    setupCustomerSpinner();
                }
            }

            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                Toast.makeText(MisDispositivosActivity.this,
                        "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupCustomerSpinner() {
        List<String> names = new ArrayList<>();
        for (CustomerViewModel c : clientes) names.add(c.getName());

        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, names);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCliente.setAdapter(spinAdapter);

        spinnerCliente.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedCustomerId = clientes.get(pos).getId();
                loadSerials();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        if (!clientes.isEmpty()) {
            selectedCustomerId = clientes.get(0).getId();
            loadSerials();
        }
    }

    private void loadSerials() {
        if (selectedCustomerId <= 0) return;
        swipeRefreshLayout.setRefreshing(true);

        apiService.getRegisteredSerials(selectedCustomerId, filterType, null)
                .enqueue(new Callback<List<RegisteredSerial>>() {
                    @Override
                    public void onResponse(Call<List<RegisteredSerial>> call,
                                           Response<List<RegisteredSerial>> response) {
                        swipeRefreshLayout.setRefreshing(false);
                        if (response.code() == 401) { handleUnauthorized(); return; }
                        if (response.isSuccessful() && response.body() != null) {
                            adapter.setData(response.body());
                        }
                    }

                    @Override
                    public void onFailure(Call<List<RegisteredSerial>> call, Throwable t) {
                        swipeRefreshLayout.setRefreshing(false);
                        Toast.makeText(MisDispositivosActivity.this,
                                "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // -----------------------------------------------
    // Dialogs
    // -----------------------------------------------

    private void showRegistrarSerialDialog() {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_registrar_serial_nuevo, null);

        RadioGroup rgTipo   = dialogView.findViewById(R.id.rgTipoDispositivo);
        EditText   etSerie  = dialogView.findViewById(R.id.etSerieNueva);

        // Límite 12 chars, solo mayúsculas
        etSerie.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(12),
                (source, start, end, dest, dstart, dend) ->
                        source.toString().toUpperCase()
        });

        new AlertDialog.Builder(this)
                .setTitle("Registrar Serial")
                .setView(dialogView)
                .setPositiveButton("Registrar", (dlg, w) -> {
                    String serie = etSerie.getText().toString().trim().toUpperCase();
                    if (serie.isEmpty()) {
                        Toast.makeText(this, "Ingresa el número de serie", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int idHW = rgTipo.getCheckedRadioButtonId() == R.id.rbHub ? 255 : 0;
                    registrarSerial(serie, idHW);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void registrarSerial(String serie, int idHW) {
        de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel dev =
                new de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel();
        dev.setSerie(serie);
        dev.setIdHW(idHW);
        dev.setIdCustomer(selectedCustomerId);

        apiService.registerDeviceSerial(dev).enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.isSuccessful()) {
                            Toast.makeText(MisDispositivosActivity.this,
                                    "Serial registrado", Toast.LENGTH_SHORT).show();
                            loadSerials();
                        } else {
                            Toast.makeText(MisDispositivosActivity.this,
                                    "Error al registrar: " + response.code(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        Toast.makeText(MisDispositivosActivity.this,
                                "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // -----------------------------------------------
    // ActionListener (adapter callbacks)
    // -----------------------------------------------

    @Override
    public void onEditLoad(RegisteredSerial serial) {
        int frontendType = serial.getDeviceType() != null ? serial.getDeviceType() : 0;
        EditLoadBottomSheet sheet = EditLoadBottomSheet.newInstance(
                serial.getSerie(), frontendType, selectedCustomerId);
        sheet.show(getSupportFragmentManager(), "edit_load");
    }

    @Override
    public void onDelete(RegisteredSerial serial) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar Serial")
                .setMessage("¿Eliminar " + serial.getSerie() + "? Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (dlg, w) -> deleteSerial(serial))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    @Override
    public void onHubExpand(RegisteredSerial hub, LinearLayout container) {
        // getHubAssignments by serieHub + idCustomer
        apiService.getHubAssignments(null, hub.getSerie(), selectedCustomerId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (!isFinishing() && response.isSuccessful() && response.body() != null) {
                            runOnUiThread(() -> fillHubNodes(container, response.body()));
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) { }
                });
    }

    private void fillHubNodes(LinearLayout container, List<Map<String, Object>> nodes) {
        container.removeAllViews();
        if (nodes.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setText("Sin nodos asignados");
            tv.setTextColor(0xFF9AA5B4);
            tv.setPadding(16, 8, 16, 8);
            container.addView(tv);
            return;
        }
        for (Map<String, Object> n : nodes) {
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_gateway_node, container, false);
            String serieNode = nvl(n.get("SerieNode"));
            String typeName  = nvl(n.get("TypeName"));
            String nodeName  = nvl(n.get("NodeName"));
            ((TextView) row.findViewById(R.id.tvNodeSerial)).setText(serieNode);
            ((TextView) row.findViewById(R.id.tvNodeType)).setText(typeName);
            ((TextView) row.findViewById(R.id.tvNodeName)).setText(nodeName);
            container.addView(row);
        }
    }

    private void deleteSerial(RegisteredSerial serial) {
        Map<String, Object> body = new HashMap<>();
        body.put("Serie",      serial.getSerie());
        body.put("IdCustomer", selectedCustomerId);

        apiService.deleteRegisteredSerial(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MisDispositivosActivity.this,
                            "Serial eliminado", Toast.LENGTH_SHORT).show();
                    loadSerials();
                } else {
                    Toast.makeText(MisDispositivosActivity.this,
                            "Error al eliminar: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(MisDispositivosActivity.this,
                        "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

    private static String nvl(Object o) { return o != null ? o.toString() : "—"; }
}
