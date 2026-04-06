package de.kai_morich.simple_bluetooth_terminal;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.EnergyFlowGraphViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.MeterCurrentViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DevicesCurrentViewModel;
import de.kai_morich.simple_bluetooth_terminal.R;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {

    private static final String TAG = "EnergyDashboard";

    // UI elements
    private TextView tvHora;
    private Spinner spinnerCustomers;
    private Spinner spinnerLocations;
    private ImageView ivFactory;

    private CardView cardSolar, cardCFE, cardDiesel, cardBattery, cardFactory;
    private TextView tvSolarValue, tvCFEValue, tvDieselValue, tvBatteryValue, tvFactoryValue;
    private ImageView arrowSolarToFactory, arrowCFEToFactory, arrowDieselToFactory, arrowBatteryToFactory, arrowFactoryToCFE;

    // API and data
    private ApiService apiService;
    private SharedPreferences prefs;
    private List<CustomerViewModel> customersList = new ArrayList<>();
    private List<LocationsViewModel> locationsList = new ArrayList<>();
    private List<CircuitsViewModel> circuitsList = new ArrayList<>();
    private ArrayAdapter<CustomerViewModel> customersAdapter;
    private ArrayAdapter<LocationsViewModel> locationsAdapter;
    private Integer currentCustomerId;
    private Integer currentLocationId;
    private Integer currentCircuitId;
    private boolean isInitialLoad = true;
    private boolean isInitialLocationLoad = true;
    private long lastErrorTimestamp = 0L;

    private EnergyData energyData = new EnergyData();

    private Handler timeHandler = new Handler(Looper.getMainLooper());
    private Handler dataHandler = new Handler(Looper.getMainLooper());
    private Handler animationHandler = new Handler(Looper.getMainLooper());
    private Runnable timeRunnable, dataRunnable, animationRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        initializeRunnables();
        initializeUI(view);
        setupApiService();
        setupClickListeners();
        loadCustomers(); // Cargar clientes reales de la API

        timeRunnable.run();
        dataRunnable.run();
        animationRunnable.run();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        timeHandler.removeCallbacks(timeRunnable);
        dataHandler.removeCallbacks(dataRunnable);
        animationHandler.removeCallbacks(animationRunnable);
    }

    private void initializeRunnables() {
        timeRunnable = () -> {
            updateCurrentTime();
            timeHandler.postDelayed(timeRunnable, 60000);
        };
        dataRunnable = () -> {
            updateEnergyData();
            updateUI();
            dataHandler.postDelayed(dataRunnable, 10000);
        };
        animationRunnable = () -> {
            updateEnergyFlowAnimations();
            animationHandler.postDelayed(animationRunnable, 1000);
        };
    }

    private void initializeUI(View view) {
        tvHora = view.findViewById(R.id.tvHoraDashboard);
        spinnerCustomers = view.findViewById(R.id.spinnerCustomers);
        spinnerLocations = view.findViewById(R.id.spinnerLocations);
        ivFactory = view.findViewById(R.id.ivFactory);

        cardSolar = view.findViewById(R.id.cardSolar);
        cardCFE = view.findViewById(R.id.cardCFE);
        cardDiesel = view.findViewById(R.id.cardDiesel);
        cardBattery = view.findViewById(R.id.cardBattery);
        cardFactory = view.findViewById(R.id.cardFactory);

        tvSolarValue = view.findViewById(R.id.tvSolarValue);
        tvCFEValue = view.findViewById(R.id.tvCFEValue);
        tvDieselValue = view.findViewById(R.id.tvDieselValue);
        tvBatteryValue = view.findViewById(R.id.tvBatteryValue);
        tvFactoryValue = view.findViewById(R.id.tvFactoryValue);

        arrowSolarToFactory = view.findViewById(R.id.arrowSolarToFactory);
        arrowCFEToFactory = view.findViewById(R.id.arrowCFEToFactory);
        arrowDieselToFactory = view.findViewById(R.id.arrowDieselToFactory);
        arrowBatteryToFactory = view.findViewById(R.id.arrowBatteryToFactory);
        arrowFactoryToCFE = view.findViewById(R.id.arrowFactoryToCFE);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        currentCustomerId = prefs.getInt("current_customer_id", 1);
        int storedLocation = prefs.getInt("current_location_id", -1);
        currentLocationId = storedLocation > 0 ? storedLocation : null;
        int storedCircuit = prefs.getInt("current_circuit_id", -1);
        currentCircuitId = storedCircuit > 0 ? storedCircuit : null;

        setupCustomerSpinner();
        setupLocationSpinner();
        setDefaultValues();
    }

    private void setupClickListeners() {
        cardSolar.setOnClickListener(v -> showEnergyModal("Solar"));
        cardCFE.setOnClickListener(v -> showEnergyModal("CFE"));
        cardDiesel.setOnClickListener(v -> showEnergyModal("Diesel"));
        cardBattery.setOnClickListener(v -> showEnergyModal("Batería"));
        if (cardFactory != null) {
            cardFactory.setOnClickListener(v -> showEnergyModal("Fábrica"));
        }
    }

    private void showEnergyModal(String energyType) {
        if (!isAdded() || getActivity() == null) return;

        // Inflar el layout del modal
        View modalView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_energy_details, null);

        // Referencias a los elementos del modal
        TextView tvModalTitle = modalView.findViewById(R.id.tvModalTitle);
        ImageView ivModalIcon = modalView.findViewById(R.id.ivModalIcon);
        TextView tvModalMainValue = modalView.findViewById(R.id.tvModalMainValue);
        TextView tvModalDetail1Label = modalView.findViewById(R.id.tvModalDetail1Label);
        TextView tvModalDetail1Value = modalView.findViewById(R.id.tvModalDetail1Value);
        TextView tvModalDetail2Label = modalView.findViewById(R.id.tvModalDetail2Label);
        TextView tvModalDetail2Value = modalView.findViewById(R.id.tvModalDetail2Value);
        TextView tvModalDetail3Label = modalView.findViewById(R.id.tvModalDetail3Label);
        TextView tvModalDetail3Value = modalView.findViewById(R.id.tvModalDetail3Value);
        TextView tvModalStatus = modalView.findViewById(R.id.tvModalStatus);
        Button btnModalRefresh = modalView.findViewById(R.id.btnModalRefresh);
        Button btnModalClose = modalView.findViewById(R.id.btnModalClose);

        // Configurar contenido según el tipo de energía
        switch (energyType) {
            case "Solar":
                tvModalTitle.setText("Energía Solar");
                tvModalMainValue.setText(formatPower(energyData.solarGeneration));
                tvModalDetail1Label.setText("Generación:");
                tvModalDetail1Value.setText(formatPower(energyData.solarGeneration));
                tvModalDetail2Label.setText("Energía día:");
                tvModalDetail2Value.setText(formatWhDay(energyData.solarWhDay));
                tvModalDetail3Label.setText("Estado:");
                tvModalDetail3Value.setText(energyData.hasPanels ? "Con paneles" : "Sin paneles");
                tvModalStatus.setText(energyData.solarGeneration > 0.05 ? "Generando energía" : "Sin generación");
                break;

            case "CFE":
                tvModalTitle.setText("Red Eléctrica CFE");
                tvModalMainValue.setText(formatPower(energyData.cfeConsumption));
                tvModalDetail1Label.setText("Consumo:");
                tvModalDetail1Value.setText(formatPower(energyData.cfeConsumption));
                tvModalDetail2Label.setText("Inyección:");
                tvModalDetail2Value.setText(formatPower(energyData.gridInjection));
                tvModalDetail3Label.setText("Energía día:");
                tvModalDetail3Value.setText(formatWhDay(energyData.cfeWhDay));
                tvModalStatus.setText(energyData.gridInjection > 0 ? "Vendiendo energía a la red" : "Consumiendo de la red");
                break;

            case "Diesel":
                tvModalTitle.setText("Inyección a CFE");
                tvModalMainValue.setText(formatPower(energyData.gridInjection));
                tvModalDetail1Label.setText("Potencia inyectada:");
                tvModalDetail1Value.setText(formatPower(energyData.gridInjection));
                tvModalDetail2Label.setText("Energía inyectada día:");
                tvModalDetail2Value.setText(formatWhDay(energyData.injectionWhDay));
                tvModalDetail3Label.setText("Estado:");
                tvModalDetail3Value.setText(energyData.isInjectingCfe ? "Inyectando" : "Sin inyección");
                tvModalStatus.setText(energyData.isInjectingCfe ? "Flujo de fábrica a CFE" : "No hay inyección a la red");
                break;

            case "Batería":
                tvModalTitle.setText("Sistema de Baterías");
                tvModalMainValue.setText(formatPower(energyData.bessPower));
                tvModalDetail1Label.setText("Potencia BESS:");
                tvModalDetail1Value.setText(formatPower(energyData.bessPower));
                tvModalDetail2Label.setText("Energía día BESS:");
                tvModalDetail2Value.setText(formatWhDay(energyData.bessWhDay));
                tvModalDetail3Label.setText("Flujo BESS → Fábrica:");
                tvModalDetail3Value.setText(formatPower(energyData.bessToFactoryW));
                tvModalStatus.setText(
                        !energyData.hasBess ? "Sin baterías" :
                                energyData.isBessCharging ? "BESS cargando" :
                                        energyData.isBessDischarging ? "BESS descargando" : "BESS en espera"
                );
                break;

            case "Fábrica":
                tvModalTitle.setText("Consumo de Fábrica");
                tvModalMainValue.setText(formatPower(energyData.factoryConsumption));
                tvModalDetail1Label.setText("Consumo total:");
                tvModalDetail1Value.setText(formatPower(energyData.factoryConsumption));
                tvModalDetail2Label.setText("Paneles:");
                tvModalDetail2Value.setText(formatPower(energyData.solarGeneration));
                tvModalDetail3Label.setText("CFE:");
                tvModalDetail3Value.setText(formatPower(energyData.cfeConsumption));
                tvModalStatus.setText("Operando normalmente");
                break;
        }

        // Crear el AlertDialog
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(modalView)
                .setCancelable(true)
                .create();

        // Configurar botones
        btnModalRefresh.setOnClickListener(v -> {
            updateEnergyData();
            // Actualizar valores del modal
            switch (energyType) {
                case "Solar":
                    tvModalMainValue.setText(formatPower(energyData.solarGeneration));
                    tvModalDetail1Value.setText(formatPower(energyData.solarGeneration));
                    tvModalDetail2Value.setText(formatWhDay(energyData.solarWhDay));
                    tvModalDetail3Value.setText(energyData.hasPanels ? "Con paneles" : "Sin paneles");
                    break;
                case "CFE":
                    tvModalMainValue.setText(formatPower(energyData.cfeConsumption));
                    tvModalDetail1Value.setText(formatPower(energyData.cfeConsumption));
                    tvModalDetail2Value.setText(formatPower(energyData.gridInjection));
                    tvModalDetail3Value.setText(formatWhDay(energyData.cfeWhDay));
                    break;
                case "Diesel":
                    tvModalMainValue.setText(formatPower(energyData.gridInjection));
                    tvModalDetail1Value.setText(formatPower(energyData.gridInjection));
                    tvModalDetail2Value.setText(formatWhDay(energyData.injectionWhDay));
                    tvModalDetail3Value.setText(energyData.isInjectingCfe ? "Inyectando" : "Sin inyección");
                    break;
                case "Batería":
                    tvModalMainValue.setText(formatPower(energyData.bessPower));
                    tvModalDetail1Value.setText(formatPower(energyData.bessPower));
                    tvModalDetail2Value.setText(formatWhDay(energyData.bessWhDay));
                    tvModalDetail3Value.setText(formatPower(energyData.bessToFactoryW));
                    break;
                case "Fábrica":
                    tvModalMainValue.setText(formatPower(energyData.factoryConsumption));
                    tvModalDetail1Value.setText(formatPower(energyData.factoryConsumption));
                    tvModalDetail2Value.setText(formatPower(energyData.solarGeneration));
                    tvModalDetail3Value.setText(formatPower(energyData.cfeConsumption));
                    break;
            }
            Toast.makeText(getContext(), "Datos actualizados", Toast.LENGTH_SHORT).show();
        });

        btnModalClose.setOnClickListener(v -> dialog.dismiss());

        // Mostrar el modal
        dialog.show();
    }

    private void setupCustomerSpinner() {
        customersAdapter = new ArrayAdapter<CustomerViewModel>(requireContext(),
                android.R.layout.simple_spinner_item, customersList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                CustomerViewModel customer = getItem(position);
                if (customer != null) view.setText(customer.getName());
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                CustomerViewModel customer = getItem(position);
                if (customer != null) view.setText(customer.getName());
                return view;
            }
        };
        customersAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCustomers.setAdapter(customersAdapter);

        spinnerCustomers.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < customersList.size()) {
                    Integer selectedCustomerId = customersList.get(position).getId();
                    boolean changed = currentCustomerId == null || !currentCustomerId.equals(selectedCustomerId);
                    currentCustomerId = selectedCustomerId;
                    prefs.edit().putInt("current_customer_id", currentCustomerId != null ? currentCustomerId : 0).apply();

                    if (changed || isInitialLoad) {
                        isInitialLoad = false;
                        isInitialLocationLoad = true;
                        currentLocationId = null;
                        currentCircuitId = null;
                        prefs.edit().remove("current_location_id").remove("current_circuit_id").apply();
                        loadLocationsForCurrentCustomer(true);
                        loadCircuitsForCurrentCustomer(false);
                    }
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupLocationSpinner() {
        locationsAdapter = new ArrayAdapter<LocationsViewModel>(requireContext(),
                android.R.layout.simple_spinner_item, locationsList) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                LocationsViewModel location = getItem(position);
                if (location != null) view.setText(location.getName());
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                LocationsViewModel location = getItem(position);
                if (location != null) view.setText(location.getName());
                return view;
            }
        };
        locationsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLocations.setAdapter(locationsAdapter);

        spinnerLocations.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position < locationsList.size()) {
                    Integer selectedLocationId = locationsList.get(position).getIdLocation();
                    boolean changed = currentLocationId == null || !currentLocationId.equals(selectedLocationId);
                    currentLocationId = selectedLocationId;
                    prefs.edit().putInt("current_location_id", currentLocationId != null ? currentLocationId : 0).apply();

                    if (changed || isInitialLocationLoad) {
                        isInitialLocationLoad = false;
                        resolveCircuitFromLocationAndFetch(true);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // INTEGRAR: Cargar clientes reales de la API
    private void loadCustomers() {
        if (apiService == null) {
            loadFallbackCustomers();
            return;
        }

        apiService.getAllCustomers().enqueue(new Callback<List<CustomerViewModel>>() {
            @Override
            public void onResponse(Call<List<CustomerViewModel>> call, Response<List<CustomerViewModel>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    customersList.clear();
                    customersList.addAll(response.body());
                    customersAdapter.notifyDataSetChanged();
                    selectCurrentCustomer();
                    loadLocationsForCurrentCustomer(true);
                    loadCircuitsForCurrentCustomer(false);
                    Log.d(TAG, "Loaded " + customersList.size() + " customers from API");
                } else {
                    Log.w(TAG, "No customers from API, using fallback");
                    loadFallbackCustomers();
                }
            }

            @Override
            public void onFailure(Call<List<CustomerViewModel>> call, Throwable t) {
                Log.e(TAG, "Error loading customers from API, using fallback", t);
                loadFallbackCustomers();
                showError("Error cargando clientes, usando datos locales");
            }
        });
    }

    private void loadFallbackCustomers() {
        customersList.clear();
        // Datos de respaldo en caso de que la API no esté disponible
        customersList.add(new CustomerViewModel(1, "Fábrica Solar Principal", "", "", "Activo", null, null));
        customersList.add(new CustomerViewModel(2, "Planta Industrial Norte", "", "", "Activo", null, null));
        customersList.add(new CustomerViewModel(3, "Complejo Energético Sur", "", "", "Activo", null, null));
        customersAdapter.notifyDataSetChanged();
        selectCurrentCustomer();
        loadLocationsForCurrentCustomer(true);
        loadCircuitsForCurrentCustomer(false);
    }

    private void selectCurrentCustomer() {
        int index = 0;
        for (int i = 0; i < customersList.size(); i++) {
            if (customersList.get(i).getId().equals(currentCustomerId)) {
                index = i;
                break;
            }
        }
        spinnerCustomers.setSelection(index);
    }

    private void loadLocationsForCurrentCustomer(boolean showUserError) {
        if (apiService == null || currentCustomerId == null) {
            currentLocationId = null;
            locationsList.clear();
            if (locationsAdapter != null) {
                locationsAdapter.notifyDataSetChanged();
            }
            return;
        }

        apiService.getCustomerLocations(currentCustomerId).enqueue(new Callback<List<LocationsViewModel>>() {
            @Override
            public void onResponse(Call<List<LocationsViewModel>> call, Response<List<LocationsViewModel>> response) {
                locationsList.clear();
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    locationsList.addAll(response.body());
                    if (locationsAdapter != null) {
                        locationsAdapter.notifyDataSetChanged();
                    }
                    selectCurrentLocation();
                } else {
                    currentLocationId = null;
                    if (locationsAdapter != null) {
                        locationsAdapter.notifyDataSetChanged();
                    }
                    if (showUserError) {
                        throttledError("El cliente no tiene localidades configuradas.");
                    }
                }
            }

            @Override
            public void onFailure(Call<List<LocationsViewModel>> call, Throwable t) {
                Log.e(TAG, "Error loading locations", t);
                if (showUserError) {
                    throttledError("No se pudieron cargar las localidades del cliente.");
                }
            }
        });
    }

    private void selectCurrentLocation() {
        if (locationsList.isEmpty() || spinnerLocations == null) {
            return;
        }

        int index = 0;
        for (int i = 0; i < locationsList.size(); i++) {
            Integer id = locationsList.get(i).getIdLocation();
            if (id != null && id.equals(currentLocationId)) {
                index = i;
                break;
            }
        }

        if (currentLocationId == null) {
            currentLocationId = locationsList.get(0).getIdLocation();
            prefs.edit().putInt("current_location_id", currentLocationId != null ? currentLocationId : 0).apply();
        }

        spinnerLocations.setSelection(index);
    }

    private void loadCircuitsForCurrentCustomer(boolean showUserError) {
        if (apiService == null || currentCustomerId == null) {
            currentCircuitId = null;
            return;
        }

        apiService.getCustomerCircuits(currentCustomerId).enqueue(new Callback<List<CircuitsViewModel>>() {
            @Override
            public void onResponse(Call<List<CircuitsViewModel>> call, Response<List<CircuitsViewModel>> response) {
                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    circuitsList.clear();
                    circuitsList.addAll(response.body());

                    boolean hasStoredCircuit = currentCircuitId != null;
                    if (hasStoredCircuit) {
                        boolean exists = false;
                        for (CircuitsViewModel circuit : circuitsList) {
                            if (currentCircuitId.equals(circuit.getIdCircuit())) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            currentCircuitId = circuitsList.get(0).getIdCircuit();
                        }
                    } else {
                        currentCircuitId = circuitsList.get(0).getIdCircuit();
                    }

                    if (currentCircuitId != null) {
                        prefs.edit().putInt("current_circuit_id", currentCircuitId).apply();
                        resolveCircuitFromLocationAndFetch(showUserError);
                    }
                } else {
                    currentCircuitId = null;
                    if (showUserError) {
                        throttledError("El cliente no tiene circuitos configurados.");
                    }
                }
            }

            @Override
            public void onFailure(Call<List<CircuitsViewModel>> call, Throwable t) {
                Log.e(TAG, "Error loading circuits", t);
                if (showUserError) {
                    throttledError("No se pudieron cargar los circuitos del cliente.");
                }
            }
        });
    }

    private void resolveCircuitFromLocationAndFetch(boolean showUserError) {
        if (currentLocationId == null) {
            if (showUserError) {
                throttledError("Selecciona una localidad.");
            }
            return;
        }

        if (circuitsList.isEmpty()) {
            return;
        }

        Integer matchedCircuitId = null;
        for (CircuitsViewModel circuit : circuitsList) {
            if (circuit != null && circuit.getIdLocation() != null && circuit.getIdLocation().equals(currentLocationId)) {
                matchedCircuitId = circuit.getIdCircuit();
                break;
            }
        }

        if (matchedCircuitId == null) {
            currentCircuitId = null;
            if (showUserError) {
                throttledError("La localidad seleccionada no tiene circuitos.");
            }
            return;
        }

        currentCircuitId = matchedCircuitId;
        prefs.edit().putInt("current_circuit_id", currentCircuitId).apply();
        fetchEnergyFlowGraph(showUserError);
    }

    private void fetchEnergyFlowGraph(boolean showUserError) {
        if (apiService == null || currentCustomerId == null || currentCircuitId == null) {
            return;
        }

        apiService.getEnergyFlowGraph(currentCircuitId, currentCustomerId)
                .enqueue(new Callback<EnergyFlowGraphViewModel>() {
                    @Override
                    public void onResponse(Call<EnergyFlowGraphViewModel> call, Response<EnergyFlowGraphViewModel> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            mapApiDataToDashboard(response.body());
                        } else if (showUserError) {
                            throttledError("No se obtuvo flujo de energía para este cliente/circuito.");
                        }
                    }

                    @Override
                    public void onFailure(Call<EnergyFlowGraphViewModel> call, Throwable t) {
                        Log.e(TAG, "Error fetching energy flow graph", t);
                        if (showUserError) {
                            throttledError("Error consultando flujo de energía.");
                        }
                    }
                });
    }

    private void mapApiDataToDashboard(EnergyFlowGraphViewModel data) {
        energyData.hasPanels = safeBool(data.getHasPanels());
        energyData.hasCfe = safeBool(data.getHasCfe());
        energyData.isInjectingCfe = safeBool(data.getIsInjectingCfe());
        energyData.hasBess = safeBool(data.getHasBess());
        energyData.isBessCharging = safeBool(data.getIsBessCharging());
        energyData.isBessDischarging = safeBool(data.getIsBessDischarging());

        energyData.solarGeneration = wattsToKw(data.getPanelsW());
        energyData.cfeConsumption = wattsToKw(data.getCfeW());
        energyData.factoryConsumption = wattsToKw(data.getLocalityW());
        energyData.gridInjection = wattsToKw(data.getInyectionW());
        energyData.bessPower = wattsToKw(data.getBessW());

        energyData.solarWhDay = safeNumber(data.getPanelsWhDay());
        energyData.cfeWhDay = safeNumber(data.getCfeWhDay());
        energyData.injectionWhDay = safeNumber(data.getInyectionWhDay());
        energyData.bessWhDay = safeNumber(data.getBessWhDay());
        energyData.bessFromCfeW = wattsToKw(data.getBessFromCfeW());
        energyData.bessToFactoryW = wattsToKw(data.getBessToFactoryW());
        energyData.bessToCfeW = wattsToKw(data.getBessToCfeW());
    }

    private double wattsToKw(Double watts) {
        return safeNumber(watts) / 1000.0;
    }

    private double safeNumber(Double value) {
        return value != null ? value : 0.0;
    }

    private boolean safeBool(Boolean value) {
        return value != null && value;
    }

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setDefaultValues() {
        tvHora.setText("--:--");
        tvSolarValue.setText("0 kW");
        tvCFEValue.setText("0 kW");
        tvDieselValue.setText("0 kW");
        tvBatteryValue.setText("0 kW");
        if (tvFactoryValue != null) tvFactoryValue.setText("0 kW");
    }

    private void updateCurrentTime() {
        tvHora.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    private void updateEnergyData() {
        fetchEnergyFlowGraph(false);
    }

    private void updateUI() {
        if (!isAdded()) return;
        getActivity().runOnUiThread(() -> {
            tvSolarValue.setText(formatPower(energyData.solarGeneration));
            tvCFEValue.setText(formatPower(energyData.cfeConsumption));
            tvDieselValue.setText(formatPower(energyData.gridInjection));
            tvBatteryValue.setText(formatPower(energyData.bessPower));
            if (tvFactoryValue != null) {
                tvFactoryValue.setText(formatPower(energyData.factoryConsumption));
            }
            updateCardColors();
        });
    }

    private void updateCardColors() {
        // Establecer todas las tarjetas con fondo blanco
        if (cardSolar != null) cardSolar.setCardBackgroundColor(Color.WHITE);
        if (cardCFE != null) cardCFE.setCardBackgroundColor(Color.WHITE);
        if (cardDiesel != null) cardDiesel.setCardBackgroundColor(Color.WHITE);
        if (cardBattery != null) cardBattery.setCardBackgroundColor(Color.WHITE);
        if (cardFactory != null) cardFactory.setCardBackgroundColor(Color.WHITE);
    }

// También puedes eliminar o comentar el método updateCardColor ya que no se usará
/*
private void updateCardColor(CardView card, double value, int highColor, int lowColor) {
    if (card == null) return;
    int color = value > 1.0 ? highColor : lowColor;
    card.setCardBackgroundColor(color);
}
*/

    private void updateCardColor(CardView card, double value, int highColor, int lowColor) {
        if (card == null) return;
        int color = value > 1.0 ? highColor : lowColor;
        card.setCardBackgroundColor(color);
    }

    private void updateEnergyFlowAnimations() {
        if (!isAdded()) return;

        // Animar flechas según el flujo de energía
        animateArrow(arrowSolarToFactory, energyData.hasPanels && energyData.solarGeneration > 0.05);
        animateArrow(arrowCFEToFactory, energyData.hasCfe && !energyData.isInjectingCfe && energyData.cfeConsumption > 0.05);
        animateArrow(arrowDieselToFactory, false);
        animateArrow(arrowBatteryToFactory, energyData.hasBess && (energyData.bessToFactoryW > 0.05 || energyData.isBessDischarging));
        animateArrow(arrowFactoryToCFE, energyData.isInjectingCfe && energyData.gridInjection > 0.05);
    }

    private void animateArrow(ImageView arrow, boolean shouldAnimate) {
        if (arrow == null) return;

        if (shouldAnimate) {
            arrow.setVisibility(View.VISIBLE);
            if (arrow.getAnimation() == null) {
                ObjectAnimator fadeIn = ObjectAnimator.ofFloat(arrow, "alpha", 0.3f, 1.0f);
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(arrow, "alpha", 1.0f, 0.3f);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playSequentially(fadeIn, fadeOut);
                animatorSet.setDuration(800);
                animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (shouldAnimate && isAdded()) {
                            animatorSet.start();
                        }
                    }
                });
                animatorSet.start();
            }
        } else {
            arrow.setVisibility(View.INVISIBLE);
            arrow.clearAnimation();
        }
    }

    private String formatPower(double power) {
        DecimalFormat df = new DecimalFormat("#.##");
        if (power >= 1000) {
            return df.format(power / 1000) + " MW";
        } else {
            return df.format(power) + " kW";
        }
    }

    private String formatWhDay(double whDay) {
        DecimalFormat df = new DecimalFormat("#.##");
        double kwh = whDay / 1000.0;
        if (kwh >= 1000) {
            return df.format(kwh / 1000.0) + " MWh/día";
        }
        return df.format(kwh) + " kWh/día";
    }

    private void showError(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private void throttledError(String message) {
        long now = System.currentTimeMillis();
        if (now - lastErrorTimestamp > 10000) {
            showError(message);
            lastErrorTimestamp = now;
        }
    }

    // Clase para almacenar datos de energía
    private static class EnergyData {
        double solarGeneration = 0;
        double cfeConsumption = 0;
        double bessPower = 0;
        double gridInjection = 0;
        double factoryConsumption = 0;

        double solarWhDay = 0;
        double cfeWhDay = 0;
        double injectionWhDay = 0;
        double bessWhDay = 0;
        double bessFromCfeW = 0;
        double bessToFactoryW = 0;
        double bessToCfeW = 0;

        boolean hasPanels = false;
        boolean hasCfe = false;
        boolean isInjectingCfe = false;
        boolean hasBess = false;
        boolean isBessCharging = false;
        boolean isBessDischarging = false;
    }
}