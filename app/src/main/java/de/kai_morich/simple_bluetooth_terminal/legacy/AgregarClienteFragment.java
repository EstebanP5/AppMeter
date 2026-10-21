package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class    AgregarClienteFragment extends Fragment {

    private static final String TAG = "AgregarClienteFragment";

    // Vistas principales
    private RecyclerView rvClientes;
    private EditText etBuscarCliente;
    private ClientesAdapter adapter;
    private Button btnAgregarCliente, btnAgregarLocalidad, btnAgregarCircuito, btnAgregarDispositivo;

    // Datos
    private List<CustomerViewModel> clienteList = new ArrayList<>();
    private List<CustomerViewModel> allClientesFull = new ArrayList<>();
    private List<LocationsViewModel> localidadesList = new ArrayList<>();
    private List<CircuitsViewModel> circuitosList = new ArrayList<>();
    private ApiService api;

    // Cliente seleccionado actualmente
    private Integer clienteSeleccionadoId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_agregar_cliente, container, false);

        api = ApiClient.getApiService();

        if (!initViews(view)) {
            showError("Error: No se pudieron encontrar los elementos de la interfaz");
            return view;
        }

        setupRecyclerView();
        setupListeners();
        cargarClientes();

        return view;
    }

    private boolean initViews(View view) {
        try {
            rvClientes = view.findViewById(R.id.rvClientes);
            etBuscarCliente = view.findViewById(R.id.etBuscarCliente);
            btnAgregarCliente = view.findViewById(R.id.btnAgregarCliente);
            btnAgregarLocalidad = view.findViewById(R.id.btnAgregarLocalidad);
            btnAgregarCircuito = view.findViewById(R.id.btnAgregarCircuito);
            btnAgregarDispositivo = view.findViewById(R.id.btnAgregarDispositivo);

            if (rvClientes == null || btnAgregarCliente == null ||
                    btnAgregarLocalidad == null || btnAgregarCircuito == null ||
                    btnAgregarDispositivo == null) {
                Log.e(TAG, "Algunas vistas no se encontraron en el XML");
                return false;
            }

            return true;

        } catch (Exception e) {
            Log.e(TAG, "Error al inicializar vistas: " + e.getMessage(), e);
            return false;
        }
    }

    private void setupRecyclerView() {
        if (rvClientes != null && getContext() != null) {
            try {
                LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
                rvClientes.setLayoutManager(layoutManager);

                // CRÍTICO: Usar el constructor con 3 parámetros
                adapter = new ClientesAdapter(clienteList, this::cargarClientes, this::onClienteSeleccionado);
                rvClientes.setAdapter(adapter);

                Log.d(TAG, "RecyclerView configurado correctamente");

            } catch (Exception e) {
                Log.e(TAG, "Error al configurar RecyclerView: " + e.getMessage(), e);
                showError("Error al configurar la lista: " + e.getMessage());
            }
        }
    }

    private void mostrarClienteSeleccionado() {
        if (clienteSeleccionadoId != null) {
            CustomerViewModel clienteSeleccionado = null;
            for (CustomerViewModel cliente : clienteList) {
                if (cliente.getId().equals(clienteSeleccionadoId)) {
                    clienteSeleccionado = cliente;
                    break;
                }
            }

            if (clienteSeleccionado != null) {
                showSuccess("Cliente seleccionado: " + clienteSeleccionado.getName());
            }
        }
    }

    private void setupListeners() {
        btnAgregarCliente.setOnClickListener(v -> mostrarDialogoAgregarCliente());
        btnAgregarLocalidad.setOnClickListener(v -> mostrarDialogoAgregarLocalidad());
        btnAgregarCircuito.setOnClickListener(v -> mostrarDialogoAgregarCircuito());
        btnAgregarDispositivo.setOnClickListener(v -> mostrarDialogoAgregarDispositivo());

        if (etBuscarCliente != null) {
            etBuscarCliente.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    filtrarClientes(s.toString().trim());
                }
            });
        }
    }

    private void filtrarClientes(String query) {
        clienteList.clear();
        if (query.isEmpty()) {
            clienteList.addAll(allClientesFull);
        } else {
            String lowerQuery = query.toLowerCase();
            for (CustomerViewModel c : allClientesFull) {
                String nombre = c.getName() != null ? c.getName().toLowerCase() : "";
                String email = c.getEmail() != null ? c.getEmail().toLowerCase() : "";
                if (nombre.contains(lowerQuery) || email.contains(lowerQuery)) {
                    clienteList.add(c);
                }
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // Callback cuando se selecciona un cliente
    private void onClienteSeleccionado(Integer clienteId) {
        Log.d(TAG, "=== INICIO onClienteSeleccionado ===");
        Log.d(TAG, "clienteId recibido: " + clienteId);
        Log.d(TAG, "clienteSeleccionadoId anterior: " + this.clienteSeleccionadoId);

        this.clienteSeleccionadoId = clienteId;

        Log.d(TAG, "clienteSeleccionadoId nuevo: " + this.clienteSeleccionadoId);

        if (clienteId != null) {
            Log.d(TAG, "Cargando datos para cliente: " + clienteId);

            // Buscar el cliente en la lista para mostrar su nombre
            CustomerViewModel clienteSeleccionado = null;
            for (CustomerViewModel cliente : clienteList) {
                if (cliente.getId().equals(clienteId)) {
                    clienteSeleccionado = cliente;
                    break;
                }
            }

            if (clienteSeleccionado != null) {
                String mensaje = "Cliente seleccionado: " + clienteSeleccionado.getName();
                showSuccess(mensaje);
                Log.d(TAG, mensaje);
            }

            // Cargar localidades y circuitos del cliente seleccionado
            cargarLocalidadesCliente(clienteId);
            cargarCircuitosCliente(clienteId);

            // Si el cliente no tiene información completa, cargarla
            if (clienteSeleccionado != null && necesitaInformacionCompleta(clienteSeleccionado)) {
                Log.d(TAG, "Cargando información completa para cliente seleccionado");
                cargarInformacionCompletaCliente(clienteId);
            }

        } else {
            Log.w(TAG, "clienteId es null!");
        }

        Log.d(TAG, "=== FIN onClienteSeleccionado ===");
    }

    // Método para verificar si un cliente necesita información completa
    private boolean necesitaInformacionCompleta(CustomerViewModel cliente) {
        return (cliente.getTelephone() == null || cliente.getTelephone().trim().isEmpty()) ||
                (cliente.getState() == null || cliente.getState().trim().isEmpty());
    }

    // Método para cargar información completa de un cliente específico
    private void cargarInformacionCompletaCliente(Integer idCliente) {
        if (api == null || idCliente == null) {
            return;
        }

        api.getCustomerInformation(idCliente)
                .enqueue(new Callback<CustomerViewModel>() {
                    @Override
                    public void onResponse(Call<CustomerViewModel> call, Response<CustomerViewModel> response) {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }

                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                CustomerViewModel clienteCompleto = response.body();

                                // Actualizar en la lista local
                                actualizarClienteEnLista(idCliente, clienteCompleto);

                                // Actualizar en el adapter
                                if (adapter != null) {
                                    adapter.notifyDataSetChanged();
                                }

                                Log.d(TAG, "Información completa cargada para cliente: " + idCliente);
                            } else {
                                Log.w(TAG, "Error al obtener información completa del cliente " + idCliente + ": " + response.code());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando información completa del cliente " + idCliente + ": " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onFailure(Call<CustomerViewModel> call, Throwable t) {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        Log.w(TAG, "Error de red al obtener información completa del cliente " + idCliente + ": " + t.getMessage());
                    }
                });
    }

    // Método para actualizar un cliente en la lista local
    private void actualizarClienteEnLista(Integer clienteId, CustomerViewModel clienteCompleto) {
        for (int i = 0; i < clienteList.size(); i++) {
            CustomerViewModel cliente = clienteList.get(i);
            if (cliente.getId() != null && cliente.getId().equals(clienteId)) {
                clienteList.set(i, clienteCompleto);
                Log.d(TAG, "Cliente actualizado en lista local: " + clienteId);
                break;
            }
        }
    }

    private void verificarEstadoSeleccion() {
        Log.d(TAG, "=== VERIFICACIÓN DE ESTADO ===");
        Log.d(TAG, "clienteSeleccionadoId: " + clienteSeleccionadoId);
        Log.d(TAG, "clienteList size: " + clienteList.size());
        Log.d(TAG, "localidadesList size: " + localidadesList.size());
        Log.d(TAG, "circuitosList size: " + circuitosList.size());
        Log.d(TAG, "adapter != null: " + (adapter != null));

        if (adapter != null) {
            CustomerViewModel seleccionado = adapter.getSelectedCliente();
            Log.d(TAG, "adapter.getSelectedCliente(): " + (seleccionado != null ? seleccionado.getName() : "null"));
            Log.d(TAG, "adapter.getSelectedPosition(): " + adapter.getSelectedPosition());
        }

        // Mostrar información del cliente seleccionado
        if (clienteSeleccionadoId != null) {
            for (CustomerViewModel cliente : clienteList) {
                if (cliente.getId().equals(clienteSeleccionadoId)) {
                    Log.d(TAG, "Cliente seleccionado encontrado: " + cliente.getName());
                    Log.d(TAG, "Teléfono: " + cliente.getTelephone());
                    Log.d(TAG, "Estado: " + cliente.getState());
                    break;
                }
            }
        }

        Log.d(TAG, "=== FIN VERIFICACIÓN ===");
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
                        clienteList.clear();
                        allClientesFull.clear();
                        clienteList.addAll(response.body());
                        allClientesFull.addAll(response.body());

                        if (adapter != null) {
                            adapter.notifyDataSetChanged();
                        }

                        Log.d(TAG, "Clientes cargados: " + clienteList.size());

                        // NUEVO: Cargar información completa automáticamente para todos los clientes
                        cargarInformacionCompletaTodosLosClientes();

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

    private void cargarInformacionCompletaTodosLosClientes() {
        Log.d(TAG, "Iniciando carga automática de información completa para todos los clientes");

        for (int i = 0; i < clienteList.size(); i++) {
            CustomerViewModel cliente = clienteList.get(i);
            if (cliente.getId() != null) {
                final int posicion = i; // Para usar en el callback
                cargarInformacionCompletaClienteEnPosicion(cliente.getId(), posicion);
            }
        }
    }

    private void cargarInformacionCompletaClienteEnPosicion(Integer idCliente, int posicion) {
        if (api == null || idCliente == null) {
            return;
        }

        api.getCustomerInformation(idCliente)
                .enqueue(new Callback<CustomerViewModel>() {
                    @Override
                    public void onResponse(Call<CustomerViewModel> call, Response<CustomerViewModel> response) {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }

                        try {
                            if (response.isSuccessful() && response.body() != null) {
                                CustomerViewModel clienteCompleto = response.body();

                                // Actualizar en la lista local en la posición específica
                                if (posicion < allClientesFull.size()) {
                                    allClientesFull.set(posicion, clienteCompleto);
                                }
                                // Find and update in clienteList by ID
                                for (int j = 0; j < clienteList.size(); j++) {
                                    if (clienteList.get(j).getId() != null &&
                                            clienteList.get(j).getId().equals(clienteCompleto.getId())) {
                                        clienteList.set(j, clienteCompleto);
                                        if (adapter != null) adapter.notifyItemChanged(j);
                                        break;
                                    }
                                }

                            } else {
                                Log.w(TAG, "Error al obtener información completa del cliente " + idCliente + ": " + response.code());
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error procesando información completa del cliente " + idCliente + ": " + e.getMessage(), e);
                        }
                    }

                    @Override
                    public void onFailure(Call<CustomerViewModel> call, Throwable t) {
                        if (!isAdded() || getContext() == null) {
                            return;
                        }
                        Log.w(TAG, "Error de red al obtener información completa del cliente " + idCliente + ": " + t.getMessage());
                    }
                });
    }



    private void cargarLocalidadesCliente(Integer clienteId) {
        if (api == null || clienteId == null) return;

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
    }

    private void cargarCircuitosCliente(Integer clienteId) {
        if (api == null || clienteId == null) return;

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
    }

    // ============ DIÁLOGOS ============

    private void mostrarDialogoAgregarCliente() {
        if (getContext() == null) return;

        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_agregar_cliente, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de cliente");
                return;
            }

            dialog.setContentView(dialogView);

            TextInputEditText etNombre = dialogView.findViewById(R.id.etClienteNombre);
            TextInputEditText etEmail = dialogView.findViewById(R.id.etClienteEmail);
            TextInputEditText etTelefono = dialogView.findViewById(R.id.etClienteTelefono);
            TextInputEditText etEstado = dialogView.findViewById(R.id.etClienteEstado);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelar);
            Button btnGuardar = dialogView.findViewById(R.id.btnGuardar);

            if (etNombre == null || etEmail == null || btnCancelar == null || btnGuardar == null) {
                showError("Error: Elementos del diálogo no encontrados");
                return;
            }

            btnCancelar.setOnClickListener(v -> dialog.dismiss());
            btnGuardar.setOnClickListener(v -> guardarCliente(dialog, etNombre, etEmail, etTelefono, etEstado));

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
            Log.e(TAG, "Error al mostrar diálogo de cliente: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }

    private void mostrarDialogoAgregarLocalidad() {
        if (getContext() == null) return;

        if (clienteSeleccionadoId == null) {
            showError("Por favor selecciona un cliente primero");
            return;
        }

        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_agregar_localidad, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de localidad");
                return;
            }

            dialog.setContentView(dialogView);

            TextInputEditText etNombre = dialogView.findViewById(R.id.etLocalidadNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etLocalidadDescripcion);
            TextInputEditText etCoordenadas = dialogView.findViewById(R.id.etLocalidadCoordenadas);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarLocalidad);
            Button btnGuardar = dialogView.findViewById(R.id.btnGuardarLocalidad);

            if (etNombre == null || btnCancelar == null || btnGuardar == null) {
                showError("Error: Elementos del diálogo no encontrados");
                return;
            }

            btnCancelar.setOnClickListener(v -> dialog.dismiss());
            btnGuardar.setOnClickListener(v -> guardarLocalidad(dialog, etNombre, etDescripcion, etCoordenadas));

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
            Log.e(TAG, "Error al mostrar diálogo de localidad: " + e.getMessage(), e);
            showError("Error al mostrar diálogo: " + e.getMessage());
        }
    }

    private void mostrarDialogoAgregarCircuito() {
        if (getContext() == null) return;

        if (clienteSeleccionadoId == null) {
            showError("Por favor selecciona un cliente primero");
            return;
        }

        if (localidadesList.isEmpty()) {
            showError("Este cliente no tiene localidades. Agrega una localidad primero.");
            return;
        }

        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_agregar_circuito, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de circuito");
                return;
            }

            dialog.setContentView(dialogView);

            AutoCompleteTextView spLocalidad = dialogView.findViewById(R.id.spCircuitoLocalidad);
            TextInputEditText etNombre = dialogView.findViewById(R.id.etCircuitoNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etCircuitoDescripcion);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarCircuito);
            Button btnGuardar = dialogView.findViewById(R.id.btnGuardarCircuito);

            if (spLocalidad == null || etNombre == null || btnCancelar == null || btnGuardar == null) {
                showError("Error: Elementos del diálogo no encontrados");
                return;
            }

            // Configurar spinner de localidades
            ArrayAdapter<LocationsViewModel> localidadesAdapter = new ArrayAdapter<>(
                    getContext(), android.R.layout.simple_dropdown_item_1line, localidadesList);
            spLocalidad.setAdapter(localidadesAdapter);

            btnCancelar.setOnClickListener(v -> dialog.dismiss());
            btnGuardar.setOnClickListener(v -> guardarCircuito(dialog, spLocalidad, etNombre, etDescripcion));

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

    private void mostrarDialogoAgregarDispositivo() {
        if (getContext() == null) return;

        if (clienteSeleccionadoId == null) {
            showError("Por favor selecciona un cliente primero");
            return;
        }

        if (circuitosList.isEmpty()) {
            showError("Este cliente no tiene circuitos. Agrega un circuito primero.");
            return;
        }

        try {
            Dialog dialog = new Dialog(getContext());
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            View dialogView = LayoutInflater.from(getContext())
                    .inflate(R.layout.dialog_agregar_dispositivo, null);

            if (dialogView == null) {
                showError("Error: No se pudo cargar el diálogo de dispositivo");
                return;
            }

            dialog.setContentView(dialogView);

            AutoCompleteTextView spCircuito = dialogView.findViewById(R.id.spDispositivoCircuito);
            AutoCompleteTextView spTipoNodo = dialogView.findViewById(R.id.spDispositivoTipoNodo);
            TextInputEditText etNombre = dialogView.findViewById(R.id.etDispositivoNombre);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etDispositivoDescripcion);

            Button btnCancelar = dialogView.findViewById(R.id.btnCancelarDispositivo);
            Button btnGuardar = dialogView.findViewById(R.id.btnGuardarDispositivo);

            if (spCircuito == null || etNombre == null || btnCancelar == null || btnGuardar == null) {
                showError("Error: Elementos del diálogo no encontrados");
                return;
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
            spTipoNodo.setAdapter(nodeTypesAdapter);

            btnCancelar.setOnClickListener(v -> dialog.dismiss());
            btnGuardar.setOnClickListener(v -> guardarDispositivo(dialog, spCircuito, etNombre,
                    etDescripcion, spTipoNodo));

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

    // ============ MÉTODOS PARA GUARDAR ============

    private void guardarCliente(Dialog dialog, TextInputEditText etNombre, TextInputEditText etEmail,
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

            CustomerViewModel nuevoCliente = new CustomerViewModel(
                    null, // ID (se asigna automáticamente en el servidor)
                    nombre,
                    email,
                    telefono,
                    estado,
                    null, // logo
                    null  // config
            );

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Enviando cliente: " + nombre + " - " + email);

            api.addNewCustomer(nuevoCliente).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Cliente creado exitosamente");
                            showSuccess("Cliente creado exitosamente");
                            cargarClientes();
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al crear cliente: " + response.code();
                            if (response.errorBody() != null) {
                                try {
                                    errorMsg += " - " + response.errorBody().string();
                                } catch (Exception e) {
                                    Log.e(TAG, "Error leyendo error body", e);
                                }
                            }
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta de creación: " + e.getMessage(), e);
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
            Log.e(TAG, "Error al guardar cliente: " + e.getMessage(), e);
            showError("Error al guardar cliente: " + e.getMessage());
        }
    }

    private void guardarCoordenadasLocalidad(LocationsViewModel loc, Dialog dialog) {
        api.saveLocationInformation(loc).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> c2, Response<Void> r2) {
                showSuccess("Localidad creada exitosamente");
                cargarLocalidadesCliente(clienteSeleccionadoId);
                dialog.dismiss();
            }
            @Override
            public void onFailure(Call<Void> c2, Throwable t) {
                showSuccess("Localidad creada (error guardando coordenadas)");
                cargarLocalidadesCliente(clienteSeleccionadoId);
                dialog.dismiss();
            }
        });
    }

    private void guardarLocalidad(Dialog dialog, TextInputEditText etNombre, TextInputEditText etDescripcion,
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

            Double latitud = null;
            Double longitud = null;

            if (!TextUtils.isEmpty(coordenadasStr)) {
                String[] partes = coordenadasStr.split(",");
                if (partes.length == 2) {
                    try {
                        latitud = Double.parseDouble(partes[0].trim());
                        longitud = Double.parseDouble(partes[1].trim());
                    } catch (NumberFormatException e) {
                        if (etCoordenadas != null) etCoordenadas.setError("Formato inválido. Usa: lat, lon");
                        return;
                    }
                } else { 
                    if (etCoordenadas != null) etCoordenadas.setError("Formato inválido. Usa: lat, lon");
                    return;
                }
            }

            String ubietyInicial = (latitud != null && longitud != null) ? (latitud + "," + longitud) : "";
            LocationsViewModel nuevaLocalidad = new LocationsViewModel(
                    clienteSeleccionadoId, nombre, descripcion, ubietyInicial, latitud, longitud
            );

            final Double latFinal = latitud;
            final Double lonFinal = longitud;
            final String descripcionFinal = descripcion;
            final String nombreFinal = nombre;

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }
            Log.d(TAG, "Enviando localidad: " + nombre);

            api.addNewLocation(nuevaLocalidad).enqueue(new Callback<LocationsViewModel>() {
                @Override
                public void onResponse(Call<LocationsViewModel> call, Response<LocationsViewModel> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Localidad creada exitosamente");
                            if (latFinal == null || lonFinal == null) {
                                showSuccess("Localidad creada exitosamente");
                                cargarLocalidadesCliente(clienteSeleccionadoId);
                                dialog.dismiss();
                                return;
                            }
                            // Try to get IdLocation from response body first
                            LocationsViewModel creada = response.body();
                            if (creada != null && creada.getIdLocation() != null) {
                                creada.setIdCustomer(clienteSeleccionadoId);
                                creada.setName(nombreFinal);
                                creada.setDescription(descripcionFinal);
                                creada.setUbiety(latFinal + "," + lonFinal);
                                guardarCoordenadasLocalidad(creada, dialog);
                            } else {
                                // Response didn't include IdLocation — fetch all locations to find the new one
                                api.getCustomerLocations(clienteSeleccionadoId).enqueue(new Callback<List<LocationsViewModel>>() {
                                    @Override
                                    public void onResponse(Call<List<LocationsViewModel>> c2, Response<List<LocationsViewModel>> r2) {
                                        if (r2.isSuccessful() && r2.body() != null) {
                                            LocationsViewModel encontrada = null;
                                            for (LocationsViewModel loc : r2.body()) {
                                                if (nombreFinal.equals(loc.getName())) {
                                                    encontrada = loc;
                                                    break;
                                                }
                                            }
                                            if (encontrada != null) {
                                                encontrada.setDescription(descripcionFinal);
                                                encontrada.setUbiety(latFinal + "," + lonFinal);
                                                guardarCoordenadasLocalidad(encontrada, dialog);
                                            } else {
                                                showSuccess("Localidad creada exitosamente");
                                                cargarLocalidadesCliente(clienteSeleccionadoId);
                                                dialog.dismiss();
                                            }
                                        } else {
                                            showSuccess("Localidad creada exitosamente");
                                            cargarLocalidadesCliente(clienteSeleccionadoId);
                                            dialog.dismiss();
                                        }
                                    }
                                    @Override
                                    public void onFailure(Call<List<LocationsViewModel>> c2, Throwable t) {
                                        showSuccess("Localidad creada exitosamente");
                                        cargarLocalidadesCliente(clienteSeleccionadoId);
                                        dialog.dismiss();
                                    }
                                });
                            }
                        } else {
                            String errorMsg = "Error al crear localidad: " + response.code();
                            Log.e(TAG, errorMsg);
                            showError(errorMsg);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error procesando respuesta: " + e.getMessage(), e);
                        showError("Error procesando respuesta: " + e.getMessage());
                    }
                }

                @Override
                public void onFailure(Call<LocationsViewModel> call, Throwable t) {
                    String errorMsg = "Error de conexión: " + t.getMessage();
                    Log.e(TAG, errorMsg, t);
                    showError(errorMsg);
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Error al guardar localidad: " + e.getMessage(), e);
            showError("Error al guardar localidad: " + e.getMessage());
        }
    }

    private void guardarCircuito(Dialog dialog, AutoCompleteTextView spLocalidad, TextInputEditText etNombre,
                                 TextInputEditText etDescripcion) {
        try {
            String localidadSeleccionada = spLocalidad.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";

            if (TextUtils.isEmpty(localidadSeleccionada)) {
                showError("Selecciona una localidad");
                return;
            }

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

            CircuitsViewModel nuevoCircuito = new CircuitsViewModel(
                    localidadObj.getIdLocation(), nombre, descripcion, "", "", null, clienteSeleccionadoId
            );

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Enviando circuito: " + nombre);

            api.addNewCircuit(nuevoCircuito).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Circuito creado exitosamente");
                            showSuccess("Circuito creado exitosamente");
                            cargarCircuitosCliente(clienteSeleccionadoId);
                            dialog.dismiss();
                        } else {
                            String errorMsg = "Error al crear circuito: " + response.code();
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
            Log.e(TAG, "Error al guardar circuito: " + e.getMessage(), e);
            showError("Error al guardar circuito: " + e.getMessage());
        }
    }

    private void guardarDispositivo(Dialog dialog, AutoCompleteTextView spCircuito, TextInputEditText etNombre,
                                    TextInputEditText etDescripcion, AutoCompleteTextView spTipoNodo) {
        try {
            String circuitoSeleccionado = spCircuito.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";
            String tipoNodoStr = spTipoNodo != null ? spTipoNodo.getText().toString().trim() : "";

            if (TextUtils.isEmpty(circuitoSeleccionado)) {
                showError("Selecciona un circuito");
                return;
            }

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

            DevicesViewModel nuevoDispositivo = new DevicesViewModel(
                    circuitoObj.getIdCircuit(), nombre, "", descripcion, "",
                    null, null, "", clienteSeleccionadoId
            );
            nuevoDispositivo.setIdLocation(circuitoObj.getIdLocation());
            nuevoDispositivo.setType(nodeType);
            nuevoDispositivo.setNodeType(nodeType);
            nuevoDispositivo.setIdHW(nodeType);

            if (api == null) {
                showError("Error: Servicio API no disponible");
                return;
            }

            Log.d(TAG, "Enviando dispositivo: " + nombre);

            api.addNewDevice(nuevoDispositivo).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(Call<Void> call, Response<Void> response) {
                    try {
                        if (response.isSuccessful()) {
                            Log.d(TAG, "Dispositivo creado exitosamente");
                            registrarSerialYVinculosSiAplica(dialog, nuevoDispositivo);
                        } else {
                            String errorMsg = "Error al crear dispositivo: " + response.code();
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
            Log.e(TAG, "Error al guardar dispositivo: " + e.getMessage(), e);
            showError("Error al guardar dispositivo: " + e.getMessage());
        }
    }

    private void registrarSerialYVinculosSiAplica(Dialog dialog, DevicesViewModel dispositivo) {
        if (api == null || dispositivo == null) {
            showSuccess("Dispositivo creado exitosamente");
            if (dialog != null) {
                dialog.dismiss();
            }
            return;
        }

        String serie = dispositivo.getSerie();
        if (TextUtils.isEmpty(serie)) {
            showSuccess("Dispositivo creado exitosamente");
            if (dialog != null) {
                dialog.dismiss();
            }
            return;
        }

        DevicesViewModel registerPayload = new DevicesViewModel();
        registerPayload.setIdCustomer(dispositivo.getIdCustomer());
        registerPayload.setSerie(serie);
        registerPayload.setType(dispositivo.getType());

        // Backend espera idHW frontend: 0=METER, 1=PHOTOCELL, 2=HUB
        int frontendIdHw = (dispositivo.getType() != null && dispositivo.getType() == 255) ? 2 : 0;
        registerPayload.setIdHW(frontendIdHw);

        api.registerDeviceSerial(registerPayload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    vincularNodoYActualizarLoadSiAplica(dialog, dispositivo);
                } else {
                    showError("Dispositivo creado, pero no se pudo registrar serial: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError("Dispositivo creado, pero falló registro de serial: " + t.getMessage());
            }
        });
    }

    private void vincularNodoYActualizarLoadSiAplica(Dialog dialog, DevicesViewModel dispositivo) {
        if (api == null || dispositivo == null || dispositivo.getIdNode() == null) {
            showSuccess("Dispositivo y serial registrados exitosamente");
            if (dialog != null) {
                dialog.dismiss();
            }
            return;
        }

        api.linkNodeWithSerial(dispositivo).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    actualizarLoadSiAplica(dialog, dispositivo);
                } else {
                    showError("Serial registrado, pero no se pudo vincular nodo: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError("Serial registrado, pero falló vínculo nodo-serial: " + t.getMessage());
            }
        });
    }

    private void actualizarLoadSiAplica(Dialog dialog, DevicesViewModel dispositivo) {
        if (api == null || dispositivo == null || dispositivo.getLoad() == null || dispositivo.getIdNode() == null) {
            showSuccess("Dispositivo, serial y nodo vinculados exitosamente");
            if (dialog != null) {
                dialog.dismiss();
            }
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("Serie", dispositivo.getSerie());
        payload.put("DeviceType", dispositivo.getType() != null ? dispositivo.getType() : 0);
        payload.put("IdNode", dispositivo.getIdNode());
        payload.put("Load", dispositivo.getLoad());
        payload.put("IdCustomer", dispositivo.getIdCustomer());

        api.updateSerialLoad(payload).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    showSuccess("Dispositivo, serial, vínculo y load guardados exitosamente");
                } else {
                    showError("Vínculo creado, pero no se pudo actualizar load: " + response.code());
                }
                if (dialog != null) {
                    dialog.dismiss();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                showError("Vínculo creado, pero falló actualización de load: " + t.getMessage());
                if (dialog != null) {
                    dialog.dismiss();
                }
            }
        });
    }

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
        rvClientes = null;
        adapter = null;
        btnAgregarCliente = null;
        btnAgregarLocalidad = null;
        btnAgregarCircuito = null;
        btnAgregarDispositivo = null;
        api = null;
    }
}