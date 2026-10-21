package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.*;
import de.kai_morich.simple_bluetooth_terminal.models.RegisteredSerial;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ModificarClienteFragment extends Fragment {

    private static final String TAG = "ModificarCliente";

    // Vistas principales
    private Spinner spClientes;
    private Button btnEditarCliente, btnEditarLocalidad, btnEditarCircuito, btnEditarDispositivo, btnEliminarCliente;
    private Button btnRegistrarSerial, btnVincularSerialNodo;

    // Datos
    private List<CustomerViewModel> allClientes = new ArrayList<>();
    private List<LocationsViewModel> localidadesList = new ArrayList<>();
    private List<CircuitsViewModel> circuitosList = new ArrayList<>();
    private List<DevicesViewModel> dispositivosList = new ArrayList<>();
    private ApiService api;

    // Cliente seleccionado actualmente
    private Integer clienteSeleccionadoId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_modificar_cliente, container, false);

        api = ApiClient.getApiService();

        if (!initViews(view)) {
            showError("Error: No se pudieron encontrar los elementos de la interfaz");
            return view;
        }

        setupListeners();
        cargarClientes();

        return view;
    }

    private boolean initViews(View view) {
        try {
            spClientes = view.findViewById(R.id.spClientes);
            btnEditarCliente = view.findViewById(R.id.btnEditarCliente);
            btnEditarLocalidad = view.findViewById(R.id.btnEditarLocalidad);
            btnEditarCircuito = view.findViewById(R.id.btnEditarCircuito);
            btnEditarDispositivo = view.findViewById(R.id.btnEditarDispositivo);
            btnEliminarCliente = view.findViewById(R.id.btnEliminarCliente);
                btnRegistrarSerial = view.findViewById(R.id.btnRegistrarSerial);
                btnVincularSerialNodo = view.findViewById(R.id.btnVincularSerialNodo);

            if (spClientes == null || btnEditarCliente == null ||
                    btnEditarLocalidad == null || btnEditarCircuito == null ||
                    btnEditarDispositivo == null ||
                    btnEliminarCliente == null || btnRegistrarSerial == null ||
                    btnVincularSerialNodo == null) {
                Log.e(TAG, "Algunas vistas no se encontraron en el XML");
                return false;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar vistas: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupListeners() {
        btnEditarCliente.setOnClickListener(v -> mostrarDialogoInfoCliente());
        btnEditarLocalidad.setOnClickListener(v -> mostrarSelectorLocalidad());
        btnEditarCircuito.setOnClickListener(v -> mostrarSelectorCircuito());
        btnEditarDispositivo.setOnClickListener(v -> mostrarSelectorDispositivo());
        btnRegistrarSerial.setOnClickListener(v -> mostrarDialogoRegistrarSerial());
        btnVincularSerialNodo.setOnClickListener(v -> mostrarDialogoVincularSerialNodo());
        btnEliminarCliente.setOnClickListener(v -> confirmarEliminarCliente());

        spClientes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < allClientes.size()) {
                    CustomerViewModel cliente = allClientes.get(position);
                    if (cliente.getId() != null) {
                        clienteSeleccionadoId = cliente.getId();
                        cargarDatosCliente(clienteSeleccionadoId);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private List<String> getTiposSerial() {
        List<String> tipos = new ArrayList<>();
        tipos.add("METER");
        tipos.add("HUB");
        return tipos;
    }

    private int tipoToValue(String tipo) {
        // Contract backend RegisterDeviceSerial/MapFrontendIdHWToDeviceType:
        // 0=METER, 1=PHOTOCELL, 2=HUB
        return "HUB".equalsIgnoreCase(tipo) ? 2 : 0;
    }

    private int nodeTypeFromSerialType(String tipo) {
        // NodeType para EnergyNode
        return "HUB".equalsIgnoreCase(tipo) ? 255 : 0;
    }

    private String getErrorBodySafe(Response<?> response) {
        if (response == null || response.errorBody() == null) return "";
        try {
            return response.errorBody().string();
        } catch (IOException e) {
            return "";
        }
    }

    private void intentarRegistroSerialConFallback(String serie,
                                                   int idCustomer,
                                                   int[][] candidates,
                                                   int index,
                                                   Dialog dialog) {
        if (index >= candidates.length) {
            showError("No se pudo registrar serial. Verifica si ya existe o si el tipo/hardware no es válido.");
            return;
        }

            int type = candidates[index][0];
            int idHW = candidates[index][1];

        DevicesViewModel serialParaRegistrar = new DevicesViewModel();
        serialParaRegistrar.setIdCustomer(idCustomer);
        serialParaRegistrar.setSerie(serie);
        serialParaRegistrar.setType(type);
        serialParaRegistrar.setIdHW(idHW);

        Log.d(TAG, "Intento registerDeviceSerial #" + (index + 1) + " -> Type=" + type + ", IdHW=" + idHW + ", Serie=" + serie + ", IdCustomer=" + idCustomer);

        api.registerDeviceSerial(serialParaRegistrar).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    showSuccess("Serial registrada para el cliente exitosamente");
                    dialog.dismiss();
                    return;
                }

                String errorBody = getErrorBodySafe(response);
                Log.w(TAG, "registerDeviceSerial falló (" + response.code() + ") Type=" + type + ", IdHW=" + idHW + ", body=" + errorBody);

                if (response.code() == 400 && index < candidates.length - 1) {
                    intentarRegistroSerialConFallback(serie, idCustomer, candidates, index + 1, dialog);
                } else {
                    if (!TextUtils.isEmpty(errorBody)) {
                        showError("No se pudo registrar serial: " + response.code() + " - " + errorBody);
                    } else {
                        showError("No se pudo registrar serial: " + response.code());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError("Error registrando serial: " + t.getMessage());
            }
        });
    }

    private void mostrarDialogoRegistrarSerial() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        if (getContext() == null) return;

        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_registrar_serial, null);
        dialog.setContentView(dialogView);

        Spinner spTipoSerial = dialogView.findViewById(R.id.spTipoSerial);
        EditText etSerialRegistro = dialogView.findViewById(R.id.etSerialRegistro);
        Button btnCancelar = dialogView.findViewById(R.id.btnCancelarRegistroSerial);
        Button btnGuardar = dialogView.findViewById(R.id.btnGuardarRegistroSerial);

        ArrayAdapter<String> tiposAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                getTiposSerial()
        );
        tiposAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTipoSerial.setAdapter(tiposAdapter);

        btnCancelar.setOnClickListener(v -> dialog.dismiss());
        btnGuardar.setOnClickListener(v -> {
            String serie = etSerialRegistro.getText() != null ? etSerialRegistro.getText().toString().trim() : "";
            String tipoSeleccionado = spTipoSerial.getSelectedItem() != null ? spTipoSerial.getSelectedItem().toString() : "METER";

            if (TextUtils.isEmpty(serie)) {
                etSerialRegistro.setError("Ingresa un número de serie");
                etSerialRegistro.requestFocus();
                return;
            }

            if (clienteSeleccionadoId == null) {
                showError("Selecciona un cliente primero");
                return;
            }

                int[][] candidates = new int[][]{
                    {nodeTypeFromSerialType(tipoSeleccionado), tipoToValue(tipoSeleccionado)}
                };
            intentarRegistroSerialConFallback(serie, clienteSeleccionadoId, candidates, 0, dialog);
        });

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    private void mostrarDialogoVincularSerialNodo() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        if (dispositivosList.isEmpty()) {
            showError("Este cliente no tiene nodos/dispositivos para vincular");
            return;
        }

        if (getContext() == null) return;

        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_vincular_serial_nodo, null);
        dialog.setContentView(dialogView);

        Spinner spTipoVinculo = dialogView.findViewById(R.id.spTipoVinculo);
        Spinner spNodoVinculo = dialogView.findViewById(R.id.spNodoVinculo);
        Spinner spSerialVinculo = dialogView.findViewById(R.id.spSerialVinculo);
        EditText etLoadVinculo = dialogView.findViewById(R.id.etLoadVinculo);
        Button btnCancelar = dialogView.findViewById(R.id.btnCancelarVinculo);
        Button btnGuardar = dialogView.findViewById(R.id.btnGuardarVinculo);

        ArrayAdapter<String> tiposAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                getTiposSerial()
        );
        tiposAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spTipoVinculo.setAdapter(tiposAdapter);

        List<String> nodosDisplay = new ArrayList<>();
        Map<String, Integer> nodoIdMap = new HashMap<>();
        for (DevicesViewModel device : dispositivosList) {
            Integer idNodo = device.getIdDevice();
            if (idNodo == null) continue;
            String nombreNodo = (device.getName() != null ? device.getName() : "Nodo") + " (ID: " + idNodo + ")";
            nodosDisplay.add(nombreNodo);
            nodoIdMap.put(nombreNodo, idNodo);
        }

        ArrayAdapter<String> nodosAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                nodosDisplay
        );
        nodosAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spNodoVinculo.setAdapter(nodosAdapter);

        List<String> serialesList = new ArrayList<>();
        ArrayAdapter<String> serialesAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                serialesList
        );
        serialesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spSerialVinculo.setAdapter(serialesAdapter);

        Runnable cargarSeriales = () -> {
            String tipoSeleccionado = spTipoVinculo.getSelectedItem() != null ? spTipoVinculo.getSelectedItem().toString() : "METER";
            int tipo = tipoToValue(tipoSeleccionado);
            api.getRegisteredSerials(clienteSeleccionadoId, tipo, false).enqueue(new Callback<List<RegisteredSerial>>() {
                @Override
                public void onResponse(Call<List<RegisteredSerial>> call, Response<List<RegisteredSerial>> response) {
                    serialesList.clear();
                    if (response.isSuccessful() && response.body() != null) {
                        for (RegisteredSerial serialItem : response.body()) {
                            if (!TextUtils.isEmpty(serialItem.getSerie())) {
                                serialesList.add(serialItem.getSerie());
                            }
                        }
                    }
                    if (serialesList.isEmpty()) {
                        serialesList.add("Sin seriales disponibles");
                    }
                    serialesAdapter.notifyDataSetChanged();
                }

                @Override
                public void onFailure(Call<List<RegisteredSerial>> call, Throwable t) {
                    serialesList.clear();
                    serialesList.add("Error cargando seriales");
                    serialesAdapter.notifyDataSetChanged();
                }
            });
        };

        spTipoVinculo.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                cargarSeriales.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
        cargarSeriales.run();

        btnCancelar.setOnClickListener(v -> dialog.dismiss());
        btnGuardar.setOnClickListener(v -> {
            if (spNodoVinculo.getSelectedItem() == null || spSerialVinculo.getSelectedItem() == null) {
                showError("Selecciona nodo y serial");
                return;
            }

            String nodoDisplay = spNodoVinculo.getSelectedItem().toString();
            Integer idNodo = nodoIdMap.get(nodoDisplay);
            String serie = spSerialVinculo.getSelectedItem().toString();
            String tipoSeleccionado = spTipoVinculo.getSelectedItem() != null ? spTipoVinculo.getSelectedItem().toString() : "METER";
            int tipo = tipoToValue(tipoSeleccionado);

            if (idNodo == null || TextUtils.isEmpty(serie) || serie.startsWith("Sin seriales") || serie.startsWith("Error")) {
                showError("Datos de vínculo no válidos");
                return;
            }

            Integer load = null;
            String loadStr = etLoadVinculo.getText() != null ? etLoadVinculo.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(loadStr)) {
                try {
                    load = Integer.parseInt(loadStr);
                } catch (NumberFormatException e) {
                    etLoadVinculo.setError("Load debe ser número");
                    etLoadVinculo.requestFocus();
                    return;
                }
            }

            final Integer loadFinal = load;

            DevicesViewModel payload = new DevicesViewModel();
            payload.setIdCustomer(clienteSeleccionadoId);
            payload.setIdNode(idNodo);
            payload.setSerie(serie);
            payload.setType(tipo);
            payload.setLoad(loadFinal);

            api.linkNodeWithSerial(payload).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    if (!response.isSuccessful()) {
                        showError("No se pudo vincular serial con nodo: " + response.code());
                        return;
                    }

                    if (loadFinal == null) {
                        showSuccess("Serial vinculada al nodo exitosamente");
                        dialog.dismiss();
                        return;
                    }

                    Map<String, Object> loadPayload = new HashMap<>();
                    loadPayload.put("Serie", serie);
                    loadPayload.put("DeviceType", tipo);
                    loadPayload.put("IdNode", idNodo);
                    loadPayload.put("Load", loadFinal);
                    loadPayload.put("IdCustomer", clienteSeleccionadoId);

                    api.updateSerialLoad(loadPayload).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                showSuccess("Serial vinculada y load actualizado");
                                dialog.dismiss();
                            } else {
                                showError("Vinculado, pero no se pudo actualizar load: " + response.code());
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            showError("Vinculado, pero falló actualización load: " + t.getMessage());
                        }
                    });
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    showError("Error vinculando serial con nodo: " + t.getMessage());
                }
            });
        });

        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    // ============ MÉTODOS PARA CARGAR DATOS ============

    private void cargarClientes() {
        if (api == null) {
            showError("Error: Servicio API no disponible");
            return;
        }

        api.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call,
                                   Response<List<CustomerViewModel>> response) {
                try {
                    if (response.isSuccessful() && response.body() != null) {
                        allClientes.clear();
                        allClientes.addAll(response.body());

                        setupSpinnerClientes();

                        Log.d(TAG, "Clientes cargados: " + allClientes.size());

                    } else {
                        String errorMsg = "Error al obtener clientes: " + response.code();
                        Log.e(TAG, errorMsg);
                        showError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                    showError("Error procesando datos: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                String errorMsg = "Error de conexión: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                showError(errorMsg);
            }
        });
    }

    private void setupSpinnerClientes() {
        if (spClientes != null && getContext() != null) {
            ArrayAdapter<CustomerViewModel> adapter = new ArrayAdapter<>(
                    getContext(),
                    android.R.layout.simple_spinner_item,
                    allClientes
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spClientes.setAdapter(adapter);
        }
    }

    private void cargarDatosCliente(Integer clienteId) {
        if (api == null || clienteId == null) return;

        // Cargar localidades
        api.getCustomerLocations(clienteId).enqueue(new Callback<List<LocationsViewModel>>() {
            @Override
            public void onResponse(Call<List<LocationsViewModel>> call, Response<List<LocationsViewModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    localidadesList.clear();
                    localidadesList.addAll(response.body());
                    Log.d(TAG, "Localidades cargadas: " + localidadesList.size());
                }
            }

            @Override
            public void onFailure(Call<List<LocationsViewModel>> call, Throwable t) {
                Log.e(TAG, "Error cargando localidades: " + t.getMessage());
            }
        });

        // Cargar circuitos
        api.getCustomerCircuits(clienteId).enqueue(new Callback<List<CircuitsViewModel>>() {
            @Override
            public void onResponse(Call<List<CircuitsViewModel>> call, Response<List<CircuitsViewModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    circuitosList.clear();
                    circuitosList.addAll(response.body());
                    Log.d(TAG, "Circuitos cargados: " + circuitosList.size());
                }
            }

            @Override
            public void onFailure(Call<List<CircuitsViewModel>> call, Throwable t) {
                Log.e(TAG, "Error cargando circuitos: " + t.getMessage());
            }
        });

        // Cargar dispositivos
        api.getCustomerDevices(clienteId).enqueue(new Callback<List<DevicesViewModel>>() {
            @Override
            public void onResponse(Call<List<DevicesViewModel>> call, Response<List<DevicesViewModel>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    dispositivosList.clear();
                    dispositivosList.addAll(response.body());
                    Log.d(TAG, "Dispositivos cargados: " + dispositivosList.size());
                }
            }

            @Override
            public void onFailure(Call<List<DevicesViewModel>> call, Throwable t) {
                Log.e(TAG, "Error cargando dispositivos: " + t.getMessage());
            }
        });
    }

    // ============ MÉTODOS PARA CLIENTES ============

    private void mostrarDialogoInfoCliente() {
        if (getContext() == null) {
            Log.w(TAG, "Context es null, no se puede mostrar diálogo");
            return;
        }

        int pos = spClientes.getSelectedItemPosition();
        if (pos < 0 || pos >= allClientes.size()) {
            showError("Selecciona un cliente válido");
            return;
        }

        CustomerViewModel clienteBasico = allClientes.get(pos);
        Integer idCliente = clienteBasico.getId();

        if (idCliente == null) {
            showError("Error: ID del cliente no válido");
            return;
        }

        // Actualizar cliente seleccionado
        this.clienteSeleccionadoId = idCliente;
        cargarDatosCliente(idCliente);

        // Mostrar loading
        Toast.makeText(getContext(), "Cargando información del cliente...", Toast.LENGTH_SHORT).show();

        // Hacer la consulta específica para obtener información completa
        cargarInformacionCompleta(idCliente);
    }

    private void cargarInformacionCompleta(Integer idCliente) {
        if (api == null) {
            showError("Error: Servicio API no disponible");
            return;
        }

        api.getCustomerInformation(idCliente)
                .enqueue(new Callback<CustomerViewModel>() {
                    @Override
                    public void onResponse(Call<CustomerViewModel> call, Response<CustomerViewModel> response) {
                        if (!isAdded() || getContext() == null) {
                            Log.w(TAG, "Fragment no está activo, ignorando respuesta de información");
                            return;
                        }

                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                CustomerViewModel clienteCompleto = response.body();
                                mostrarDialogoConInformacionCompleta(clienteCompleto);
                            } else {
                                String errorMsg = "Error al obtener información del cliente: " + response.code();
                                Log.e(TAG, errorMsg);
                                showError(errorMsg);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando información del cliente: " + e.getMessage(), e);
                            showError("Error procesando información: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onFailure(Call<CustomerViewModel> call, Throwable t) {
                        if (!isAdded() || getContext() == null) {
                            Log.w(TAG, "Fragment no está activo, ignorando error de información");
                            return;
                        }

                        String errorMsg = "Error de red al obtener información: " + t.getMessage();
                        Log.e(TAG, errorMsg, t);
                        showError(errorMsg);
                    }
                });
    }

    private void mostrarDialogoConInformacionCompleta(CustomerViewModel cliente) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_info_cliente, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de información");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar y llenar los campos de información
            TextView tvInfoClienteId = dialogView.findViewById(R.id.tvInfoClienteId);
            TextView tvInfoClienteNombre = dialogView.findViewById(R.id.tvInfoClienteNombre);
            TextView tvInfoClienteEmail = dialogView.findViewById(R.id.tvInfoClienteEmail);
            TextView tvInfoClienteTelefono = dialogView.findViewById(R.id.tvInfoClienteTelefono);
            TextView tvInfoClienteEstado = dialogView.findViewById(R.id.tvInfoClienteEstado);
            TextView tvInfoClienteLogo = dialogView.findViewById(R.id.tvInfoClienteLogo);
            TextView tvInfoClienteConfig = dialogView.findViewById(R.id.tvInfoClienteConfig);

            Button btnEditarClienteInfo = dialogView.findViewById(R.id.btnEditarClienteInfo);
            Button btnCerrarInfo = dialogView.findViewById(R.id.btnCerrarInfo);

            // Llenar la información
            if (tvInfoClienteId != null) tvInfoClienteId.setText(String.valueOf(cliente.getId()));
            if (tvInfoClienteNombre != null) tvInfoClienteNombre.setText(cliente.getName() != null ? cliente.getName() : "Sin nombre");
            if (tvInfoClienteEmail != null) tvInfoClienteEmail.setText(cliente.getEmail() != null ? cliente.getEmail() : "Sin email");
            if (tvInfoClienteTelefono != null) tvInfoClienteTelefono.setText(cliente.getTelephone() != null ? cliente.getTelephone() : "Sin teléfono");
            if (tvInfoClienteEstado != null) tvInfoClienteEstado.setText(cliente.getState() != null ? cliente.getState() : "Sin estado");
            if (tvInfoClienteLogo != null) tvInfoClienteLogo.setText(cliente.getLogo() != null ? cliente.getLogo() : "Sin logo");
            if (tvInfoClienteConfig != null) tvInfoClienteConfig.setText(cliente.getConfig() != null ? cliente.getConfig() : "Sin configuración");

            // Configurar botones
            if (btnCerrarInfo != null) {
                btnCerrarInfo.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnEditarClienteInfo != null) {
                btnEditarClienteInfo.setOnClickListener(v -> {
                    dialog.dismiss();
                    mostrarDialogoEditarCliente(cliente);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar diálogo de información: " + e.getMessage(), e);
            showError("Error al mostrar información: " + e.getMessage());
        }
    }

    private void mostrarDialogoEditarCliente(CustomerViewModel cliente) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_editar_cliente, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de edición");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar campos del formulario
            TextInputEditText etNombre = dialogView.findViewById(R.id.etEditarClienteNombre);
            TextInputEditText etEmail = dialogView.findViewById(R.id.etEditarClienteEmail);
            TextInputEditText etTelefono = dialogView.findViewById(R.id.etEditarClienteTelefono);
            TextInputEditText etEstado = dialogView.findViewById(R.id.etEditarClienteEstado);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarEdicion);
            Button btnActualizar = dialogView.findViewById(R.id.btnActualizarCliente);

            // Pre-llenar campos con datos existentes
            if (etNombre != null) etNombre.setText(cliente.getName());
            if (etEmail != null) etEmail.setText(cliente.getEmail());
            if (etTelefono != null) etTelefono.setText(cliente.getTelephone());
            if (etEstado != null) etEstado.setText(cliente.getState());

            // Configurar listeners
            if (btnCancelar != null) {
                btnCancelar.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnActualizar != null) {
                btnActualizar.setOnClickListener(v ->
                        actualizarCliente(dialog, cliente, etNombre, etEmail, etTelefono, etEstado));
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar diálogo de edición: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }

    private void actualizarCliente(Dialog dialog, CustomerViewModel clienteOriginal,
                                   TextInputEditText etNombre, TextInputEditText etEmail,
                                   TextInputEditText etTelefono, TextInputEditText etEstado) {
        try {
            String nombre = etNombre.getText().toString().trim();
            String email = etEmail.getText().toString().trim();
            String telefono = etTelefono != null ? etTelefono.getText().toString().trim() : "";
            String estado = etEstado != null ? etEstado.getText().toString().trim() : "";

            // Validar campos obligatorios
            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("El nombre es obligatorio");
                etNombre.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(email)) {
                etEmail.setError("El email es obligatorio");
                etEmail.requestFocus();
                return;
            }

            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.setError("Ingresa un email válido");
                etEmail.requestFocus();
                return;
            }

            // Crear objeto actualizado conservando el ID y otros campos
            CustomerViewModel clienteActualizado = new CustomerViewModel(
                    clienteOriginal.getId(), // Conservar ID
                    nombre,
                    email,
                    telefono,
                    estado,
                    clienteOriginal.getLogo(), // Conservar logo
                    clienteOriginal.getConfig() // Conservar config
            );

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Actualizando cliente: " + nombre);

            api.saveCustomer(clienteActualizado).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Cliente actualizado exitosamente");
                            showSuccess("Cliente actualizado exitosamente");
                            cargarClientes(); // Refrescar lista
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al actualizar cliente: " + response.code();
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                        showError("Error procesando respuesta: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    String errorMsg = "Error de conexión: " + t.getMessage();
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar cliente: " + e.getMessage(), e);
            showError("Error al actualizar cliente: " + e.getMessage());
        }
    }

    // ============ MÉTODOS PARA CIRCUITOS ============

    // ============ LOCALIDADES ============

    private void mostrarSelectorLocalidad() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        if (localidadesList.isEmpty()) {
            showError("Este cliente no tiene localidades");
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Seleccionar Localidad");

        final String[] localidadesArray = new String[localidadesList.size()];
        for (int i = 0; i < localidadesList.size(); i++) {
            LocationsViewModel loc = localidadesList.get(i);
            localidadesArray[i] = loc.getName() + " (ID: " + loc.getIdLocation() + ")";
        }

        builder.setItems(localidadesArray, (dialog, which) -> {
            LocationsViewModel localidadSeleccionada = localidadesList.get(which);
            mostrarDialogoInfoLocalidad(localidadSeleccionada);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void mostrarDialogoInfoLocalidad(LocationsViewModel localidad) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_info_localidad, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de localidad");
                return;
            }

            dialog.setContentView(dialogView);

            TextView tvInfoLocalidadId = dialogView.findViewById(R.id.tvInfoLocalidadId);
            TextView tvInfoLocalidadNombre = dialogView.findViewById(R.id.tvInfoLocalidadNombre);
            TextView tvInfoLocalidadDescripcion = dialogView.findViewById(R.id.tvInfoLocalidadDescripcion);
            TextView tvInfoLocalidadCoordenadas = dialogView.findViewById(R.id.tvInfoLocalidadCoordenadas);
            Button btnEditar = dialogView.findViewById(R.id.btnEditarLocalidadInfo);
            Button btnEliminar = dialogView.findViewById(R.id.btnEliminarLocalidadInfo);
            Button btnCerrar = dialogView.findViewById(R.id.btnCerrarInfoLocalidad);

            if (tvInfoLocalidadId != null)
                tvInfoLocalidadId.setText(String.valueOf(localidad.getIdLocation()));
            if (tvInfoLocalidadNombre != null)
                tvInfoLocalidadNombre.setText(localidad.getName() != null ? localidad.getName() : "Sin nombre");
            if (tvInfoLocalidadDescripcion != null)
                tvInfoLocalidadDescripcion.setText(localidad.getDescription() != null && !localidad.getDescription().isEmpty()
                        ? localidad.getDescription() : "Sin descripción");
            if (tvInfoLocalidadCoordenadas != null) {
                String ubiety = localidad.getUbiety();
                if (ubiety != null && !ubiety.isEmpty()) {
                    tvInfoLocalidadCoordenadas.setText(ubiety);
                } else if (localidad.getLatitude() != null && localidad.getLongitude() != null) {
                    tvInfoLocalidadCoordenadas.setText(localidad.getLatitude() + ", " + localidad.getLongitude());
                } else {
                    tvInfoLocalidadCoordenadas.setText("—");
                }
            }

            if (btnCerrar != null) btnCerrar.setOnClickListener(v -> dialog.dismiss());
            if (btnEditar != null) {
                btnEditar.setOnClickListener(v -> {
                    dialog.dismiss();
                    mostrarDialogoEditarLocalidad(localidad);
                });
            }
            if (btnEliminar != null) {
                btnEliminar.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmarEliminarLocalidad(localidad);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar información de localidad: " + e.getMessage(), e);
            showError("Error al mostrar información: " + e.getMessage());
        }
    }

    private void mostrarDialogoEditarLocalidad(LocationsViewModel localidad) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_editar_localidad, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de editar localidad");
                return;
            }

            dialog.setContentView(dialogView);

            TextView tvIdLocalidad = dialogView.findViewById(R.id.tvIdLocalidad);
            TextInputEditText etNombre = dialogView.findViewById(R.id.etEditarLocalidadNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etEditarLocalidadDescripcion);
            TextInputEditText etCoordenadas = dialogView.findViewById(R.id.etEditarLocalidadCoordenadas);
            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarEditarLocalidad);
            Button btnGuardar = dialogView.findViewById(R.id.btnGuardarEditarLocalidad);

            // Pre-llenar datos
            if (tvIdLocalidad != null) tvIdLocalidad.setText(String.valueOf(localidad.getIdLocation()));
            if (etNombre != null) etNombre.setText(localidad.getName());
            if (etDescripcion != null) etDescripcion.setText(localidad.getDescription());
            if (etCoordenadas != null) {
                String ubiety = localidad.getUbiety();
                if (ubiety != null && !ubiety.isEmpty()) {
                    etCoordenadas.setText(ubiety);
                } else if (localidad.getLatitude() != null && localidad.getLongitude() != null) {
                    etCoordenadas.setText(localidad.getLatitude() + ", " + localidad.getLongitude());
                }
            }

            if (btnCancelar != null) btnCancelar.setOnClickListener(v -> dialog.dismiss());
            if (btnGuardar != null) {
                btnGuardar.setOnClickListener(v ->
                        actualizarLocalidad(dialog, localidad, etNombre, etDescripcion, etCoordenadas));
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);
            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar diálogo de editar localidad: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }

    private void actualizarLocalidad(Dialog dialog, LocationsViewModel localidadOriginal,
                                     TextInputEditText etNombre, TextInputEditText etDescripcion,
                                     TextInputEditText etCoordenadas) {
        try {
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";
            String coordenadasStr = etCoordenadas != null ? etCoordenadas.getText().toString().trim() : "";

            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("El nombre es obligatorio");
                etNombre.requestFocus();
                return;
            }

            String ubiety = localidadOriginal.getUbiety();

            if (!TextUtils.isEmpty(coordenadasStr)) {
                // Normalise: replace optional space after comma so server gets "lat,lon"
                ubiety = coordenadasStr.replace(", ", ",").replace(" ,", ",");
            }

            LocationsViewModel localidadActualizada = new LocationsViewModel(
                    localidadOriginal.getIdLocation(),
                    localidadOriginal.getIdCustomer(),
                    nombre,
                    descripcion,
                    ubiety,
                    localidadOriginal.getLatitude(),
                    localidadOriginal.getLongitude()
            );

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Actualizando localidad: " + nombre);

            api.saveLocationInformation(localidadActualizada).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Localidad actualizada exitosamente");
                            showSuccess("Localidad actualizada exitosamente");
                            cargarDatosCliente(clienteSeleccionadoId);
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al actualizar localidad: " + response.code();
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                        showError("Error procesando respuesta: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    String errorMsg = "Error de conexión: " + t.getMessage();
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar localidad: " + e.getMessage(), e);
            showError("Error al actualizar localidad: " + e.getMessage());
        }
    }

    private void confirmarEliminarLocalidad(LocationsViewModel localidad) {
        String nombre = localidad.getName() != null ? localidad.getName() : "Sin nombre";

        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que quieres eliminar la localidad '" + nombre + "'?\n\n" +
                        "Esta acción también eliminará todos los circuitos y dispositivos asociados.")
                .setPositiveButton("Eliminar", (dialogInterface, which) -> eliminarLocalidad(localidad))
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void eliminarLocalidad(LocationsViewModel localidad) {
        if (api == null || localidad.getIdLocation() == null) {
            showError("Error: No se puede eliminar la localidad");
            return;
        }

        Map<String, Integer> payload = new HashMap<>();
        payload.put("IdLocation", localidad.getIdLocation());
        payload.put("IdCustomer", localidad.getIdCustomer());

        api.deleteLocation(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                try {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Localidad eliminada exitosamente");
                        showSuccess("Localidad eliminada exitosamente");
                        cargarDatosCliente(clienteSeleccionadoId);
                    } else {
                        String errorMsg = "Error al eliminar localidad: " + response.code();
                        Log.e(TAG, errorMsg);
                        showError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                    showError("Error procesando respuesta: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String errorMsg = "Error de conexión: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                showError(errorMsg);
            }
        });
    }

    private void mostrarSelectorCircuito() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        if (circuitosList.isEmpty()) {
            showError("Este cliente no tiene circuitos");
            return;
        }

        // Crear diálogo selector
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Seleccionar Circuito");

        final String[] circuitosArray = new String[circuitosList.size()];
        for (int i = 0; i < circuitosList.size(); i++) {
            CircuitsViewModel circuito = circuitosList.get(i);
            circuitosArray[i] = circuito.getName() + " (ID: " + circuito.getIdCircuit() + ")";
        }

        builder.setItems(circuitosArray, (dialog, which) -> {
            CircuitsViewModel circuitoSeleccionado = circuitosList.get(which);
            mostrarDialogoInfoCircuito(circuitoSeleccionado);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void mostrarDialogoInfoCircuito(CircuitsViewModel circuito) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_info_circuito, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de información del circuito");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar y llenar los campos de información
            TextView tvInfoCircuitoId = dialogView.findViewById(R.id.tvInfoCircuitoId);
            TextView tvInfoCircuitoNombre = dialogView.findViewById(R.id.tvInfoCircuitoNombre);
            TextView tvInfoCircuitoLocalidad = dialogView.findViewById(R.id.tvInfoCircuitoLocalidad);
            TextView tvInfoCircuitoDescripcion = dialogView.findViewById(R.id.tvInfoCircuitoDescripcion);
            TextView tvInfoCircuitoTarifa = dialogView.findViewById(R.id.tvInfoCircuitoTarifa);
            TextView tvInfoCircuitoRPU = dialogView.findViewById(R.id.tvInfoCircuitoRPU);
            TextView tvInfoCircuitoPrecioKwh = dialogView.findViewById(R.id.tvInfoCircuitoPrecioKwh);

            Button btnEditarCircuitoInfo = dialogView.findViewById(R.id.btnEditarCircuitoInfo);
            Button btnEliminarCircuitoInfo = dialogView.findViewById(R.id.btnEliminarCircuitoInfo);
            Button btnCerrarInfoCircuito = dialogView.findViewById(R.id.btnCerrarInfoCircuito);

            // Llenar la información
            if (tvInfoCircuitoId != null) {
                tvInfoCircuitoId.setText(String.valueOf(circuito.getIdCircuit()));
            }
            if (tvInfoCircuitoNombre != null) {
                tvInfoCircuitoNombre.setText(circuito.getName() != null ? circuito.getName() : "Sin nombre");
            }

            // Buscar nombre de la localidad
            String nombreLocalidad = "Sin localidad";
            for (LocationsViewModel localidad : localidadesList) {
                if (localidad.getIdLocation().equals(circuito.getIdLocation())) {
                    nombreLocalidad = localidad.getName();
                    break;
                }
            }
            if (tvInfoCircuitoLocalidad != null) {
                tvInfoCircuitoLocalidad.setText(nombreLocalidad);
            }

            if (tvInfoCircuitoDescripcion != null) {
                tvInfoCircuitoDescripcion.setText(circuito.getDescription() != null ? circuito.getDescription() : "Sin descripción");
            }
            if (tvInfoCircuitoTarifa != null) {
                tvInfoCircuitoTarifa.setText(circuito.getRate() != null ? circuito.getRate() : "Sin tarifa");
            }
            if (tvInfoCircuitoRPU != null) {
                tvInfoCircuitoRPU.setText(circuito.getRpu() != null ? circuito.getRpu() : "Sin RPU");
            }
            if (tvInfoCircuitoPrecioKwh != null) {
                String precio = circuito.getkWhPrice() != null ? "$" + circuito.getkWhPrice() : "Sin precio";
                tvInfoCircuitoPrecioKwh.setText(precio);
            }

            // Configurar botones
            if (btnCerrarInfoCircuito != null) {
                btnCerrarInfoCircuito.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnEditarCircuitoInfo != null) {
                btnEditarCircuitoInfo.setOnClickListener(v -> {
                    dialog.dismiss();
                    mostrarDialogoEditarCircuito(circuito);
                });
            }

            if (btnEliminarCircuitoInfo != null) {
                btnEliminarCircuitoInfo.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmarEliminarCircuito(circuito);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar información del circuito: " + e.getMessage(), e);
            showError("Error al mostrar información: " + e.getMessage());
        }
    }

    private void mostrarDialogoEditarCircuito(CircuitsViewModel circuito) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_editar_circuito, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de circuito");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar vistas
            TextView tvIdCircuito = dialogView.findViewById(R.id.tvIdCircuito);
            AutoCompleteTextView spLocalidad = dialogView.findViewById(R.id.spEditarCircuitoLocalidad);
            TextInputEditText etNombre = dialogView.findViewById(R.id.etEditarCircuitoNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etEditarCircuitoDescripcion);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarEditarCircuito);
            Button btnActualizar = dialogView.findViewById(R.id.btnActualizarCircuito);
            Button btnEliminar = dialogView.findViewById(R.id.btnEliminarCircuito);

            // Mostrar ID del circuito
            if (tvIdCircuito != null) {
                tvIdCircuito.setText(String.valueOf(circuito.getIdCircuit()));
            }

            // Configurar spinner de localidades
            ArrayAdapter<LocationsViewModel> localidadesAdapter = new ArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line, localidadesList);
            spLocalidad.setAdapter(localidadesAdapter);

            // Pre-llenar campos
            if (etNombre != null) etNombre.setText(circuito.getName());
            if (etDescripcion != null) etDescripcion.setText(circuito.getDescription());

            // Seleccionar la localidad actual
            for (int i = 0; i < localidadesList.size(); i++) {
                if (localidadesList.get(i).getIdLocation().equals(circuito.getIdLocation())) {
                    spLocalidad.setText(localidadesList.get(i).toString(), false);
                    break;
                }
            }

            // Configurar listeners
            if (btnCancelar != null) {
                btnCancelar.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnActualizar != null) {
                btnActualizar.setOnClickListener(v ->
                        actualizarCircuito(dialog, circuito, spLocalidad, etNombre, etDescripcion));
            }

            if (btnEliminar != null) {
                btnEliminar.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmarEliminarCircuito(circuito);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar diálogo de circuito: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }
    private void actualizarCircuito(Dialog dialog, CircuitsViewModel circuitoOriginal,
                                    AutoCompleteTextView spLocalidad, TextInputEditText etNombre,
                                    TextInputEditText etDescripcion) {
        try {
            String localidadSeleccionada = spLocalidad.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";

            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("El nombre es obligatorio");
                etNombre.requestFocus();
                return;
            }

            // Buscar la localidad seleccionada
            LocationsViewModel localidadObj = null;
            for (LocationsViewModel loc : localidadesList) {
                if (loc.toString().equals(localidadSeleccionada)) {
                    localidadObj = loc;
                    break;
                }
            }

            if (localidadObj == null) {
                showError("Localidad no válida");
                return;
            }

            CircuitsViewModel circuitoActualizado = new CircuitsViewModel(
                    circuitoOriginal.getIdCircuit(), // Conservar ID
                    localidadObj.getIdLocation(),
                    nombre,
                    descripcion,
                    circuitoOriginal.getRate(),      // Conservar tarifa original
                    circuitoOriginal.getRpu(),       // Conservar RPU original
                    circuitoOriginal.getkWhPrice(),  // Conservar precio original
                    circuitoOriginal.getIdCustomer() // Conservar cliente
            );

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Actualizando circuito: " + nombre);

            api.saveCircuitInformation(circuitoActualizado).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Circuito actualizado exitosamente");
                            showSuccess("Circuito actualizado exitosamente");
                            cargarDatosCliente(clienteSeleccionadoId); // Refrescar datos
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al actualizar circuito: " + response.code();
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                        showError("Error procesando respuesta: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    String errorMsg = "Error de conexión: " + t.getMessage();
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar circuito: " + e.getMessage(), e);
            showError("Error al actualizar circuito: " + e.getMessage());
        }
    }

    // ============ MÉTODOS PARA DISPOSITIVOS ============

    private void mostrarSelectorDispositivo() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        if (dispositivosList.isEmpty()) {
            showError("Este cliente no tiene dispositivos");
            return;
        }

        // Crear diálogo selector
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Seleccionar Dispositivo");

        String[] dispositivosArray = new String[dispositivosList.size()];
        for (int i = 0; i < dispositivosList.size(); i++) {
            DevicesViewModel dispositivo = dispositivosList.get(i);
            dispositivosArray[i] = dispositivo.getName() + " (" + dispositivo.getSerie() + ")";
        }

        builder.setItems(dispositivosArray, (dialog, which) -> {
            DevicesViewModel dispositivoSeleccionado = dispositivosList.get(which);
            mostrarDialogoInfoDispositivo(dispositivoSeleccionado);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void mostrarDialogoInfoDispositivo(DevicesViewModel dispositivo) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_info_dispositivo, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de información del dispositivo");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar y llenar los campos de información
            TextView tvInfoDispositivoId = dialogView.findViewById(R.id.tvInfoDispositivoId);
            TextView tvInfoDispositivoSerie = dialogView.findViewById(R.id.tvInfoDispositivoSerie);
            TextView tvInfoDispositivoNombre = dialogView.findViewById(R.id.tvInfoDispositivoNombre);
            TextView tvInfoDispositivoCircuito = dialogView.findViewById(R.id.tvInfoDispositivoCircuito);
            TextView tvInfoDispositivoDescripcion = dialogView.findViewById(R.id.tvInfoDispositivoDescripcion);
            TextView tvInfoDispositivoUbicacion = dialogView.findViewById(R.id.tvInfoDispositivoUbicacion);
            TextView tvInfoDispositivoLatitud = dialogView.findViewById(R.id.tvInfoDispositivoLatitud);
            TextView tvInfoDispositivoLongitud = dialogView.findViewById(R.id.tvInfoDispositivoLongitud);
            TextView tvInfoDispositivoIcono = dialogView.findViewById(R.id.tvInfoDispositivoIcono);

            Button btnEditarDispositivoInfo = dialogView.findViewById(R.id.btnEditarDispositivoInfo);
            Button btnEliminarDispositivoInfo = dialogView.findViewById(R.id.btnEliminarDispositivoInfo);
            Button btnCerrarInfoDispositivo = dialogView.findViewById(R.id.btnCerrarInfoDispositivo);

            // Llenar la información
            if (tvInfoDispositivoId != null) {
                tvInfoDispositivoId.setText(String.valueOf(dispositivo.getIdDevice()));
            }
            if (tvInfoDispositivoSerie != null) {
                tvInfoDispositivoSerie.setText(dispositivo.getSerie() != null ? dispositivo.getSerie() : "Sin serie");
            }
            if (tvInfoDispositivoNombre != null) {
                tvInfoDispositivoNombre.setText(dispositivo.getName() != null ? dispositivo.getName() : "Sin nombre");
            }

            // Buscar nombre del circuito
            String nombreCircuito = "Sin circuito";
            for (CircuitsViewModel circuito : circuitosList) {
                if (circuito.getIdCircuit().equals(dispositivo.getIdCircuit())) {
                    nombreCircuito = circuito.getName();
                    break;
                }
            }
            if (tvInfoDispositivoCircuito != null) {
                tvInfoDispositivoCircuito.setText(nombreCircuito);
            }

            if (tvInfoDispositivoDescripcion != null) {
                tvInfoDispositivoDescripcion.setText(dispositivo.getDescription() != null ? dispositivo.getDescription() : "Sin descripción");
            }
            if (tvInfoDispositivoUbicacion != null) {
                tvInfoDispositivoUbicacion.setText(dispositivo.getUbiety() != null ? dispositivo.getUbiety() : "Sin ubicación");
            }
            if (tvInfoDispositivoLatitud != null) {
                String lat = dispositivo.getLatitude() != null ? "Lat: " + dispositivo.getLatitude() : "Lat: Sin coordenada";
                tvInfoDispositivoLatitud.setText(lat);
            }
            if (tvInfoDispositivoLongitud != null) {
                String lng = dispositivo.getLongitude() != null ? "Lng: " + dispositivo.getLongitude() : "Lng: Sin coordenada";
                tvInfoDispositivoLongitud.setText(lng);
            }
            if (tvInfoDispositivoIcono != null) {
                tvInfoDispositivoIcono.setText(dispositivo.getIcon() != null ? dispositivo.getIcon() : "Sin icono");
            }

            // Configurar botones
            if (btnCerrarInfoDispositivo != null) {
                btnCerrarInfoDispositivo.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnEditarDispositivoInfo != null) {
                btnEditarDispositivoInfo.setOnClickListener(v -> {
                    dialog.dismiss();
                    mostrarDialogoEditarDispositivo(dispositivo);
                });
            }

            if (btnEliminarDispositivoInfo != null) {
                btnEliminarDispositivoInfo.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmarEliminarDispositivo(dispositivo);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar información del dispositivo: " + e.getMessage(), e);
            showError("Error al mostrar información: " + e.getMessage());
        }
    }

    private void mostrarDialogoEditarDispositivo(DevicesViewModel dispositivo) {
        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_editar_dispositivo, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de dispositivo");
                return;
            }

            dialog.setContentView(dialogView);

            // Encontrar vistas
            TextView tvIdDispositivo = dialogView.findViewById(R.id.tvIdDispositivo);
            TextView tvSerieActual = dialogView.findViewById(R.id.tvSerieActual);
            AutoCompleteTextView spCircuito = dialogView.findViewById(R.id.spEditarDispositivoCircuito);
            AutoCompleteTextView spTipoNodo = dialogView.findViewById(R.id.spEditarDispositivoTipoNodo);
            TextInputEditText etNombre = dialogView.findViewById(R.id.etEditarDispositivoNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etEditarDispositivoDescripcion);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarEditarDispositivo);
            Button btnActualizar = dialogView.findViewById(R.id.btnActualizarDispositivo);
            Button btnEliminar = dialogView.findViewById(R.id.btnEliminarDispositivo);

            // Mostrar información del dispositivo
            if (tvIdDispositivo != null) {
                tvIdDispositivo.setText(String.valueOf(dispositivo.getIdDevice()));
            }
            if (tvSerieActual != null) {
                tvSerieActual.setText(dispositivo.getSerie() != null ? dispositivo.getSerie() : "Sin serie");
            }

            // Configurar spinner de circuitos
            ArrayAdapter<CircuitsViewModel> circuitosAdapter = new ArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line, circuitosList);
            spCircuito.setAdapter(circuitosAdapter);

            // Configurar spinner de tipo de nodo
            String[] nodeTypes = {"0 - TOTAL_Wh", "1 - TRANSFORMER", "2 - PV", "3 - BESS",
                    "4 - GENERATOR", "5 - PANEL", "6 - CIRCUIT", "7 - LOAD"};
            ArrayAdapter<String> nodeTypesAdapter = new ArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line, nodeTypes);
            if (spTipoNodo != null) spTipoNodo.setAdapter(nodeTypesAdapter);

            // Pre-llenar campos
            if (etNombre != null) etNombre.setText(dispositivo.getName());
            if (etDescripcion != null) etDescripcion.setText(dispositivo.getDescription());
            // Pre-seleccionar tipo de nodo actual
            if (spTipoNodo != null && dispositivo.getType() != null) {
                int idx = dispositivo.getType();
                if (idx >= 0 && idx < nodeTypes.length) {
                    spTipoNodo.setText(nodeTypes[idx], false);
                }
            }

            // Seleccionar el circuito actual
            for (int i = 0; i < circuitosList.size(); i++) {
                if (circuitosList.get(i).getIdCircuit().equals(dispositivo.getIdCircuit())) {
                    spCircuito.setText(circuitosList.get(i).toString(), false);
                    break;
                }
            }

            // Configurar listeners
            if (btnCancelar != null) {
                btnCancelar.setOnClickListener(v -> dialog.dismiss());
            }

            if (btnActualizar != null) {
                btnActualizar.setOnClickListener(v ->
                        actualizarDispositivo(dialog, dispositivo, spCircuito, etNombre,
                                etDescripcion, spTipoNodo));
            }

            if (btnEliminar != null) {
                btnEliminar.setOnClickListener(v -> {
                    dialog.dismiss();
                    confirmarEliminarDispositivo(dispositivo);
                });
            }

            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(false);

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );
            }

            dialog.show();

        } catch (Exception e) {
            Log.e(TAG, "Error al mostrar diálogo de dispositivo: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }

    private void actualizarDispositivo(Dialog dialog, DevicesViewModel dispositivoOriginal,
                                       AutoCompleteTextView spCircuito, TextInputEditText etNombre,
                                       TextInputEditText etDescripcion, AutoCompleteTextView spTipoNodo) {
        try {
            String circuitoSeleccionado = spCircuito.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";
            String tipoNodoStr = spTipoNodo != null ? spTipoNodo.getText().toString().trim() : "";

            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("El nombre es obligatorio");
                etNombre.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(tipoNodoStr)) {
                showError("Selecciona un tipo de nodo");
                return;
            }

            // Buscar el circuito seleccionado
            CircuitsViewModel circuitoObj = null;
            for (CircuitsViewModel circ : circuitosList) {
                if (circ.toString().equals(circuitoSeleccionado)) {
                    circuitoObj = circ;
                    break;
                }
            }

            if (circuitoObj == null) {
                showError("Circuito no válido");
                return;
            }

            // Parsear tipo de nodo (formato "N - NOMBRE")
            int nodeType;
            try {
                nodeType = Integer.parseInt(tipoNodoStr.split(" ")[0]);
            } catch (NumberFormatException e) {
                showError("Tipo de nodo inválido");
                return;
            }

            DevicesViewModel dispositivoActualizado = new DevicesViewModel(
                    dispositivoOriginal.getIdDevice(), // Conservar ID
                    circuitoObj.getIdCircuit(),
                    nombre,
                    dispositivoOriginal.getSerie(),   // Conservar serie original
                    descripcion,
                    dispositivoOriginal.getUbiety(),  // Conservar ubicación original
                    dispositivoOriginal.getLatitude(),
                    dispositivoOriginal.getLongitude(),
                    dispositivoOriginal.getIcon(),    // Conservar icono original
                    dispositivoOriginal.getIdCustomer() // Conservar cliente
            );
            dispositivoActualizado.setType(nodeType);
            dispositivoActualizado.setNodeType(nodeType);
            dispositivoActualizado.setIdHW(nodeType);

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Actualizando dispositivo: " + nombre);

            api.saveDeviceInformation(dispositivoActualizado).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Dispositivo actualizado exitosamente");
                            showSuccess("Dispositivo actualizado exitosamente");
                            cargarDatosCliente(clienteSeleccionadoId); // Refrescar datos
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al actualizar dispositivo: " + response.code();
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                        showError("Error procesando respuesta: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<Void> call, Throwable t) {
                    String errorMsg = "Error de conexión: " + t.getMessage();
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al actualizar dispositivo: " + e.getMessage(), e);
            showError("Error al actualizar dispositivo: " + e.getMessage());
        }
    }

    // ============ MÉTODOS DE ELIMINACIÓN ============

    private void confirmarEliminarCliente() {
        if (clienteSeleccionadoId == null) {
            showError("Selecciona un cliente primero");
            return;
        }

        // Buscar el cliente seleccionado
        CustomerViewModel clienteSeleccionado = null;
        int pos = spClientes.getSelectedItemPosition();
        if (pos >= 0 && pos < allClientes.size()) {
            clienteSeleccionado = allClientes.get(pos);
        }

        if (clienteSeleccionado == null) {
            showError("Cliente no encontrado");
            return;
        }

        final CustomerViewModel clienteFinal = clienteSeleccionado;
        final String nombreCliente = clienteFinal.getName() != null ? clienteFinal.getName() : "Sin nombre";

        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que quieres eliminar el cliente '" + nombreCliente + "'?\n\n" +
                        "Esta acción también eliminará todas sus localidades, circuitos y dispositivos asociados.")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarCliente(clienteFinal)) // Usar clienteFinal
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void eliminarCliente(CustomerViewModel cliente) {
        if (api == null || cliente.getId() == null) {
            showError("Error: No se puede eliminar el cliente");
            return;
        }

        Map<String, Integer> payload = new HashMap<>();
        payload.put("IdCustomer", cliente.getId());
        payload.put("IdClient", cliente.getId());

        api.deleteCustomer(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                try {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Cliente eliminado exitosamente");
                        showSuccess("Cliente eliminado exitosamente");

                        // Limpiar datos y recargar lista
                        clienteSeleccionadoId = null;
                        localidadesList.clear();
                        circuitosList.clear();
                        dispositivosList.clear();
                        cargarClientes();

                    } else {
                        String errorMsg = "Error al eliminar cliente: " + response.code();
                        Log.e(TAG, errorMsg);
                        showError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                    showError("Error procesando respuesta: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String errorMsg = "Error de conexión: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                showError(errorMsg);
            }
        });
    }

    private void confirmarEliminarCircuito(CircuitsViewModel circuito) {
        String nombreCircuito = circuito.getName() != null ? circuito.getName() : "Sin nombre";

        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que quieres eliminar el circuito '" + nombreCircuito + "'?\n\n" +
                        "Esta acción también eliminará todos los dispositivos asociados a este circuito.")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarCircuito(circuito))
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void eliminarCircuito(CircuitsViewModel circuito) {
        if (api == null || circuito.getIdCircuit() == null) {
            showError("Error: No se puede eliminar el circuito");
            return;
        }

        Map<String, Integer> payload = new HashMap<>();
        payload.put("IdCircuit", circuito.getIdCircuit());

        api.deleteCircuit(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                try {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Circuito eliminado exitosamente");
                        showSuccess("Circuito eliminado exitosamente");
                        cargarDatosCliente(clienteSeleccionadoId); // Refrescar datos
                    } else {
                        String errorMsg = "Error al eliminar circuito: " + response.code();
                        Log.e(TAG, errorMsg);
                        showError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                    showError("Error procesando respuesta: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String errorMsg = "Error de conexión: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                showError(errorMsg);
            }
        });
    }

    private void confirmarEliminarDispositivo(DevicesViewModel dispositivo) {
        String nombreDispositivo = dispositivo.getName() != null ? dispositivo.getName() : "Sin nombre";

        new AlertDialog.Builder(getContext())
                .setTitle("Confirmar eliminación")
                .setMessage("¿Estás seguro de que quieres eliminar el dispositivo '" + nombreDispositivo + "'?")
                .setPositiveButton("Eliminar", (dialog, which) -> eliminarDispositivo(dispositivo))
                .setNegativeButton("Cancelar", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void eliminarDispositivo(DevicesViewModel dispositivo) {
        if (api == null || dispositivo.getIdDevice() == null) {
            showError("Error: No se puede eliminar el dispositivo");
            return;
        }

        api.deleteDevice(dispositivo).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                try {
                    if (response.isSuccessful()) {
                        Log.d(TAG, "Dispositivo eliminado exitosamente");
                        showSuccess("Dispositivo eliminado exitosamente");
                        cargarDatosCliente(clienteSeleccionadoId); // Refrescar datos
                    } else {
                        String errorMsg = "Error al eliminar dispositivo: " + response.code();
                        Log.e(TAG, errorMsg);
                        showError(errorMsg);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                    showError("Error procesando respuesta: " + e.getMessage());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                String errorMsg = "Error de conexión: " + t.getMessage();
                Log.e(TAG, errorMsg, t);
                showError(errorMsg);
            }
        });
    }

    // ============ MÉTODOS DE UTILIDAD ============

    private void showError(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
        Log.e(TAG, message);
    }

    private void showSuccess(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
        Log.d(TAG, message);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        spClientes = null;
        btnEditarCliente = null;
        btnEditarCircuito = null;
        btnEditarDispositivo = null;
        btnRegistrarSerial = null;
        btnVincularSerialNodo = null;
        btnEliminarCliente = null;
        api = null;
    }
}