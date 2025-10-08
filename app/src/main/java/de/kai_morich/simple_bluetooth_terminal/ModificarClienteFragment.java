package de.kai_morich.simple_bluetooth_terminal;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ModificarClienteFragment extends Fragment {

    private static final String TAG = "ModificarCliente";

    // Vistas principales
    private Spinner spClientes;
    private Button btnEditarCliente, btnEditarCircuito, btnEditarDispositivo, btnEliminarCliente;

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
            btnEditarCircuito = view.findViewById(R.id.btnEditarCircuito);
            btnEditarDispositivo = view.findViewById(R.id.btnEditarDispositivo);
            btnEliminarCliente = view.findViewById(R.id.btnEliminarCliente);

            if (spClientes == null || btnEditarCliente == null ||
                    btnEditarCircuito == null || btnEditarDispositivo == null ||
                    btnEliminarCliente == null) {
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
        btnEditarCircuito.setOnClickListener(v -> mostrarSelectorCircuito());
        btnEditarDispositivo.setOnClickListener(v -> mostrarSelectorDispositivo());
        btnEliminarCliente.setOnClickListener(v -> confirmarEliminarCliente());
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
            TextInputEditText etTarifa = dialogView.findViewById(R.id.etEditarCircuitoTarifa);
            TextInputEditText etRPU = dialogView.findViewById(R.id.etEditarCircuitoRPU);
            TextInputEditText etPrecioKwh = dialogView.findViewById(R.id.etEditarCircuitoPrecioKwh);

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
            if (etTarifa != null) etTarifa.setText(circuito.getRate());
            if (etRPU != null) etRPU.setText(circuito.getRpu());
            if (etPrecioKwh != null && circuito.getkWhPrice() != null) {
                etPrecioKwh.setText(String.valueOf(circuito.getkWhPrice()));
            }

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
                        actualizarCircuito(dialog, circuito, spLocalidad, etNombre, etDescripcion, etTarifa, etRPU, etPrecioKwh));
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
                                    TextInputEditText etDescripcion, TextInputEditText etTarifa,
                                    TextInputEditText etRPU, TextInputEditText etPrecioKwh) {
        try {
            String localidadSeleccionada = spLocalidad.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";
            String tarifa = etTarifa != null ? etTarifa.getText().toString().trim() : "";
            String rpu = etRPU != null ? etRPU.getText().toString().trim() : "";
            String precioKwhStr = etPrecioKwh != null ? etPrecioKwh.getText().toString().trim() : "";

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

            Double precioKwh = null;
            try {
                if (!TextUtils.isEmpty(precioKwhStr)) {
                    precioKwh = Double.parseDouble(precioKwhStr);
                }
            } catch (NumberFormatException e) {
                etPrecioKwh.setError("Precio debe ser un número válido");
                etPrecioKwh.requestFocus();
                return;
            }

            CircuitsViewModel circuitoActualizado = new CircuitsViewModel(
                    circuitoOriginal.getIdCircuit(), // Conservar ID
                    localidadObj.getIdLocation(),
                    nombre,
                    descripcion,
                    tarifa,
                    rpu,
                    precioKwh,
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
            TextInputEditText etNombre = dialogView.findViewById(R.id.etEditarDispositivoNombre);
            TextInputEditText etSerie = dialogView.findViewById(R.id.etEditarDispositivoSerie);
            TextInputEditText etDescripcion = dialogView.findViewById(R.id.etEditarDispositivoDescripcion);
            TextInputEditText etUbicacion = dialogView.findViewById(R.id.etEditarDispositivoUbicacion);
            TextInputEditText etLatitud = dialogView.findViewById(R.id.etEditarDispositivoLatitud);
            TextInputEditText etLongitud = dialogView.findViewById(R.id.etEditarDispositivoLongitud);
            TextInputEditText etIcono = dialogView.findViewById(R.id.etEditarDispositivoIcono);

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

            // Pre-llenar campos
            if (etNombre != null) etNombre.setText(dispositivo.getName());
            if (etSerie != null) etSerie.setText(dispositivo.getSerie());
            if (etDescripcion != null) etDescripcion.setText(dispositivo.getDescription());
            if (etUbicacion != null) etUbicacion.setText(dispositivo.getUbiety());
            if (etLatitud != null && dispositivo.getLatitude() != null) {
                etLatitud.setText(String.valueOf(dispositivo.getLatitude()));
            }
            if (etLongitud != null && dispositivo.getLongitude() != null) {
                etLongitud.setText(String.valueOf(dispositivo.getLongitude()));
            }
            if (etIcono != null) etIcono.setText(dispositivo.getIcon());

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
                        actualizarDispositivo(dialog, dispositivo, spCircuito, etNombre, etSerie,
                                etDescripcion, etUbicacion, etLatitud, etLongitud, etIcono));
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
                                       TextInputEditText etSerie, TextInputEditText etDescripcion,
                                       TextInputEditText etUbicacion, TextInputEditText etLatitud,
                                       TextInputEditText etLongitud, TextInputEditText etIcono) {
        try {
            String circuitoSeleccionado = spCircuito.getText().toString().trim();
            String nombre = etNombre.getText().toString().trim();
            String serie = etSerie != null ? etSerie.getText().toString().trim() : "";
            String descripcion = etDescripcion != null ? etDescripcion.getText().toString().trim() : "";
            String ubicacion = etUbicacion != null ? etUbicacion.getText().toString().trim() : "";
            String latitudStr = etLatitud != null ? etLatitud.getText().toString().trim() : "";
            String longitudStr = etLongitud != null ? etLongitud.getText().toString().trim() : "";
            String icono = etIcono != null ? etIcono.getText().toString().trim() : "";

            if (TextUtils.isEmpty(nombre)) {
                etNombre.setError("El nombre es obligatorio");
                etNombre.requestFocus();
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

            Double latitud = null;
            Double longitud = null;

            try {
                if (!TextUtils.isEmpty(latitudStr)) {
                    latitud = Double.parseDouble(latitudStr);
                }
                if (!TextUtils.isEmpty(longitudStr)) {
                    longitud = Double.parseDouble(longitudStr);
                }
            } catch (NumberFormatException e) {
                showError("Las coordenadas deben ser números válidos");
                return;
            }

            DevicesViewModel dispositivoActualizado = new DevicesViewModel(
                    dispositivoOriginal.getIdDevice(), // Conservar ID
                    circuitoObj.getIdCircuit(),
                    nombre,
                    serie,
                    descripcion,
                    ubicacion,
                    latitud,
                    longitud,
                    icono,
                    dispositivoOriginal.getIdCustomer() // Conservar cliente
            );

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

        Map<String, Integer> payload = new HashMap<>();
        payload.put("IdDevice", dispositivo.getIdDevice());

        api.deleteDevice(payload).enqueue(new Callback<Void>() {
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
        btnEliminarCliente = null;
        api = null;
    }
}