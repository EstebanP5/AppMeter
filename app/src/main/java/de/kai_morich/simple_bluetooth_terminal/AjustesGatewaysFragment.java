package de.kai_morich.simple_bluetooth_terminal;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.RegisteredSerial;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Tab "GATEWAYS" dentro de Configuración.
 * Gestiona el árbol Hub → Nodos asignados del cliente.
 */
public class AjustesGatewaysFragment extends Fragment
        implements GatewayHubAdapter.HubActionListener {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private GatewayHubAdapter hubAdapter;
    private Spinner spinnerCliente;
    private View emptyView;

    private ApiService apiService;
    private TokenManager tokenManager;
    private boolean isAdmin;
    private int selectedCustomerId = -1;

    private List<CustomerViewModel> clientes = new ArrayList<>();
    private List<DevicesViewModel> allNodes  = new ArrayList<>(); // nodos disponibles para asignar

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ajustes_gateways, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tokenManager = new TokenManager(requireContext());
        apiService   = ApiClient.getApiService();
        isAdmin      = JwtUtils.isInstallerAdmin(tokenManager.getToken());

        if (!isAdmin) {
            Integer id = JwtUtils.getIdCustomer(tokenManager.getToken());
            selectedCustomerId = id != null ? id : -1;
        }

        initViews(view);

        if (isAdmin) {
            loadCustomers();
        } else if (selectedCustomerId > 0) {
            loadHubs();
        }
    }

    private void initViews(View view) {
        spinnerCliente = view.findViewById(R.id.spinnerCliente);
        spinnerCliente.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        swipeRefresh = view.findViewById(R.id.swipeRefreshLayout);
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyView    = view.findViewById(R.id.tvEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        hubAdapter = new GatewayHubAdapter(requireContext(), this);
        recyclerView.setAdapter(hubAdapter);

        swipeRefresh.setColorSchemeResources(R.color.web_accent);
        swipeRefresh.setOnRefreshListener(this::loadHubs);
    }

    // -----------------------------------------------
    // Data
    // -----------------------------------------------

    private void loadCustomers() {
        apiService.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call,
                                   Response<List<CustomerViewModel>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null) {
                    clientes = response.body();
                    setupCustomerSpinner();
                }
            }
            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                if (isAdded())
                    Toast.makeText(requireContext(), "Error: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupCustomerSpinner() {
        List<String> names = new ArrayList<>();
        for (CustomerViewModel c : clientes) names.add(c.getName());
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, names);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCliente.setAdapter(a);
        spinnerCliente.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedCustomerId = clientes.get(pos).getId();
                loadHubs();
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        if (!clientes.isEmpty()) {
            selectedCustomerId = clientes.get(0).getId();
            loadHubs();
        }
    }

    private void loadHubs() {
        if (selectedCustomerId <= 0) return;
        swipeRefresh.setRefreshing(true);

        apiService.getCustomerDevices(selectedCustomerId)
                .enqueue(new Callback<List<DevicesViewModel>>() {
                    @Override
                    public void onResponse(Call<List<DevicesViewModel>> call,
                                           Response<List<DevicesViewModel>> response) {
                        if (!isAdded()) return;
                        swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null) {
                            // Filtrar solo HUBs (idHW == 255)
                            List<DevicesViewModel> hubs = new ArrayList<>();
                            for (DevicesViewModel d : response.body()) {
                                if (d.getIdHW() != null && d.getIdHW() == 255) hubs.add(d);
                            }
                            hubAdapter.setData(hubs);
                            emptyView.setVisibility(hubs.isEmpty() ? View.VISIBLE : View.GONE);
                        }
                    }
                    @Override
                    public void onFailure(Call<List<DevicesViewModel>> call, Throwable t) {
                        if (!isAdded()) return;
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Error: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // -----------------------------------------------
    // HubActionListener
    // -----------------------------------------------

    @Override
    public void onExpandHub(DevicesViewModel hub, LinearLayout container) {
        if (hub.getIdDevice() == null) return;
        apiService.getAllHubNodes(hub.getIdDevice())
                .enqueue(new Callback<List<RegisteredSerial>>() {
                    @Override
                    public void onResponse(Call<List<RegisteredSerial>> call,
                                           Response<List<RegisteredSerial>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            requireActivity().runOnUiThread(() ->
                                    fillNodeRows(container, response.body(), hub));
                        }
                    }
                    @Override
                    public void onFailure(Call<List<RegisteredSerial>> call, Throwable t) { }
                });
    }

    private void fillNodeRows(LinearLayout container,
                              List<RegisteredSerial> nodes, DevicesViewModel hub) {
        container.removeAllViews();
        if (nodes.isEmpty()) {
            TextView tv = new TextView(requireContext());
            tv.setText("Sin nodos asignados");
            tv.setTextColor(0xFF9AA5B4);
            tv.setPadding(32, 8, 16, 8);
            container.addView(tv);
            return;
        }
        for (RegisteredSerial n : nodes) {
            View row = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_gateway_node, container, false);
            ((TextView) row.findViewById(R.id.tvNodeSerial)).setText(
                    n.getSerie() != null ? n.getSerie() : "—");
            ((TextView) row.findViewById(R.id.tvNodeType)).setText(n.getNodeTypeName());
            ((TextView) row.findViewById(R.id.tvNodeName)).setText(
                    n.getNodeName() != null ? n.getNodeName() : "—");

            row.findViewById(R.id.btnDesasignar).setOnClickListener(v ->
                    confirmDesasignar(n, hub));
            container.addView(row);
        }
    }

    @Override
    public void onDeleteHub(DevicesViewModel hub) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Eliminar Hub")
                .setMessage("¿Eliminar hub " + hub.getName() + "? Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (d, w) -> deleteHub(hub))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void deleteHub(DevicesViewModel hub) {
        DevicesViewModel payload = new DevicesViewModel();
        payload.setIdDevice(hub.getIdDevice());
        payload.setIdCustomer(selectedCustomerId);
        payload.setIdHW(255);

        apiService.deleteDevice(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "Hub eliminado", Toast.LENGTH_SHORT).show();
                    loadHubs();
                } else {
                    Toast.makeText(requireContext(),
                            "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                if (isAdded())
                    Toast.makeText(requireContext(), "Error: " + t.getMessage(),
                            Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onAssignNode(DevicesViewModel hub) {
        if (hub.getIdDevice() == null) return;
        // Cargar nodos disponibles y mostrar diálogo
        apiService.getCustomerHubNodeMap(selectedCustomerId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            showAssignDialog(hub, response.body());
                        }
                    }
                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) { }
                });
    }

    private void showAssignDialog(DevicesViewModel hub, List<Map<String, Object>> nodes) {
        if (!isAdded() || nodes.isEmpty()) {
            Toast.makeText(requireContext(), "Sin nodos disponibles", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = new String[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            Object name = nodes.get(i).get("NodeName");
            Object serie = nodes.get(i).get("Serie");
            labels[i] = (name != null ? name : "—") + " (" + (serie != null ? serie : "—") + ")";
        }

        int[] selected = {0};
        new AlertDialog.Builder(requireContext())
                .setTitle("Asignar Nodo a Hub")
                .setSingleChoiceItems(labels, 0, (d, w) -> selected[0] = w)
                .setPositiveButton("Asignar", (d, w) -> {
                    Map<String, Object> node = nodes.get(selected[0]);
                    Object idNodeRaw = node.get("IdNode");
                    if (idNodeRaw == null) return;
                    double idNodeD = (double) idNodeRaw; // Gson parses numbers as Double
                    Map<String, Object> body = new HashMap<>();
                    body.put("IdNode",     (int) idNodeD);
                    body.put("IdHub",      hub.getIdDevice());
                    body.put("IdCustomer", selectedCustomerId);
                    assignNode(body);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void assignNode(Map<String, Object> body) {
        apiService.assignNodeToHub(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "Nodo asignado", Toast.LENGTH_SHORT).show();
                    loadHubs();
                } else {
                    Toast.makeText(requireContext(),
                            "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) { }
        });
    }

    private void confirmDesasignar(RegisteredSerial node, DevicesViewModel hub) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Desasignar Nodo")
                .setMessage("¿Desasignar " + node.getNodeName() + " del hub?")
                .setPositiveButton("Desasignar", (d, w) -> desasignarNode(node))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void desasignarNode(RegisteredSerial node) {
        Map<String, Object> body = new HashMap<>();
        body.put("IdNode",     node.getIdNode());
        body.put("IdCustomer", selectedCustomerId);

        apiService.unassignNodeFromHub(body).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "Nodo desasignado", Toast.LENGTH_SHORT).show();
                    loadHubs();
                } else {
                    Toast.makeText(requireContext(),
                            "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) { }
        });
    }
}
