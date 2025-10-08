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
import java.util.Random;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
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
    private ImageView ivFactory;

    private CardView cardSolar, cardCFE, cardDiesel, cardBattery, cardFactory;
    private TextView tvSolarValue, tvCFEValue, tvDieselValue, tvBatteryValue, tvFactoryValue;
    private ImageView arrowSolarToFactory, arrowCFEToFactory, arrowDieselToFactory, arrowBatteryToFactory, arrowFactoryToCFE;

    // API and data
    private ApiService apiService;
    private SharedPreferences prefs;
    private List<CustomerViewModel> customersList = new ArrayList<>();
    private ArrayAdapter<CustomerViewModel> customersAdapter;
    private Integer currentCustomerId;
    private boolean isInitialLoad = true;

    private EnergyData energyData = new EnergyData();
    private Random random = new Random();

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
            dataHandler.postDelayed(dataRunnable, 3000);
        };
        animationRunnable = () -> {
            updateEnergyFlowAnimations();
            animationHandler.postDelayed(animationRunnable, 1000);
        };
    }

    private void initializeUI(View view) {
        tvHora = view.findViewById(R.id.tvHoraDashboard);
        spinnerCustomers = view.findViewById(R.id.spinnerCustomers);
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

        setupCustomerSpinner();
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
                // ivModalIcon.setImageResource(R.drawable.ic_solar); // Descomenta si tienes el icono
                tvModalMainValue.setText(formatPower(energyData.solarGeneration));
                tvModalDetail1Label.setText("Generación:");
                tvModalDetail1Value.setText(formatPower(energyData.solarGeneration));
                tvModalDetail2Label.setText("Eficiencia:");
                tvModalDetail2Value.setText(String.format(Locale.getDefault(), "%.1f%%", energyData.solarEfficiency));
                tvModalDetail3Label.setText("Ahorro mensual:");
                tvModalDetail3Value.setText(String.format(Locale.getDefault(), "$%.2f", energyData.monthlySavings));
                tvModalStatus.setText(energyData.solarGeneration > 1.0 ? "Generando energía óptima" : "Generación limitada");
                break;

            case "CFE":
                tvModalTitle.setText("Red Eléctrica CFE");
                // ivModalIcon.setImageResource(R.drawable.ic_grid); // Descomenta si tienes el icono
                tvModalMainValue.setText(formatPower(energyData.cfeConsumption));
                tvModalDetail1Label.setText("Consumo:");
                tvModalDetail1Value.setText(formatPower(energyData.cfeConsumption));
                tvModalDetail2Label.setText("Inyección:");
                tvModalDetail2Value.setText(formatPower(energyData.gridInjection));
                tvModalDetail3Label.setText("Tarifa:");
                tvModalDetail3Value.setText("$3.50/kWh");
                tvModalStatus.setText(energyData.gridInjection > 0 ? "Vendiendo energía a la red" : "Consumiendo de la red");
                break;

            case "Diesel":
                tvModalTitle.setText("Generador Diesel");
                // ivModalIcon.setImageResource(R.drawable.ic_diesel); // Descomenta si tienes el icono
                tvModalMainValue.setText(formatPower(energyData.dieselGeneration));
                tvModalDetail1Label.setText("Generación:");
                tvModalDetail1Value.setText(formatPower(energyData.dieselGeneration));
                tvModalDetail2Label.setText("Horas de operación:");
                tvModalDetail2Value.setText(String.format(Locale.getDefault(), "%.1f h", energyData.dieselHours));
                tvModalDetail3Label.setText("Nivel de combustible:");
                tvModalDetail3Value.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.fuelLevel));
                tvModalStatus.setText(energyData.dieselGeneration > 0 ? "Generador en funcionamiento" : "Generador en standby");
                break;

            case "Batería":
                tvModalTitle.setText("Sistema de Baterías");
                // ivModalIcon.setImageResource(R.drawable.ic_battery); // Descomenta si tienes el icono
                tvModalMainValue.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.batteryLevel));
                tvModalDetail1Label.setText("Nivel de carga:");
                tvModalDetail1Value.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.batteryLevel));
                tvModalDetail2Label.setText("Capacidad:");
                tvModalDetail2Value.setText("100 kWh");
                tvModalDetail3Label.setText("Tiempo restante:");
                tvModalDetail3Value.setText(String.format(Locale.getDefault(), "%.1f h", energyData.batteryLevel * 0.8));
                tvModalStatus.setText(energyData.batteryLevel > 50 ? "Estado: Bueno" : energyData.batteryLevel > 20 ? "Estado: Medio" : "Estado: Crítico");
                break;

            case "Fábrica":
                tvModalTitle.setText("Consumo de Fábrica");
                // ivModalIcon.setImageResource(R.drawable.ic_factory); // Descomenta si tienes el icono
                tvModalMainValue.setText(formatPower(energyData.factoryConsumption));
                tvModalDetail1Label.setText("Consumo total:");
                tvModalDetail1Value.setText(formatPower(energyData.factoryConsumption));
                tvModalDetail2Label.setText("Eficiencia:");
                tvModalDetail2Value.setText("92%");
                tvModalDetail3Label.setText("Dispositivos activos:");
                tvModalDetail3Value.setText("24");
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
                    tvModalDetail2Value.setText(String.format(Locale.getDefault(), "%.1f%%", energyData.solarEfficiency));
                    tvModalDetail3Value.setText(String.format(Locale.getDefault(), "$%.2f", energyData.monthlySavings));
                    break;
                case "CFE":
                    tvModalMainValue.setText(formatPower(energyData.cfeConsumption));
                    tvModalDetail1Value.setText(formatPower(energyData.cfeConsumption));
                    tvModalDetail2Value.setText(formatPower(energyData.gridInjection));
                    break;
                case "Diesel":
                    tvModalMainValue.setText(formatPower(energyData.dieselGeneration));
                    tvModalDetail1Value.setText(formatPower(energyData.dieselGeneration));
                    tvModalDetail2Value.setText(String.format(Locale.getDefault(), "%.1f h", energyData.dieselHours));
                    tvModalDetail3Value.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.fuelLevel));
                    break;
                case "Batería":
                    tvModalMainValue.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.batteryLevel));
                    tvModalDetail1Value.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.batteryLevel));
                    tvModalDetail3Value.setText(String.format(Locale.getDefault(), "%.1f h", energyData.batteryLevel * 0.8));
                    break;
                case "Fábrica":
                    tvModalMainValue.setText(formatPower(energyData.factoryConsumption));
                    tvModalDetail1Value.setText(formatPower(energyData.factoryConsumption));
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
                if (!isInitialLoad && position < customersList.size()) {
                    currentCustomerId = customersList.get(position).getId();
                    prefs.edit().putInt("current_customer_id", currentCustomerId).apply();
                    updateEnergyData(); // Actualizar datos cuando cambie el cliente
                } else if (isInitialLoad) {
                    isInitialLoad = false;
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

    private void setupApiService() {
        apiService = ApiClient.getApiService();
    }

    private void setDefaultValues() {
        tvHora.setText("--:--");
        tvSolarValue.setText("0 kW");
        tvCFEValue.setText("0 kW");
        tvDieselValue.setText("0 kW");
        tvBatteryValue.setText("0%");
        if (tvFactoryValue != null) tvFactoryValue.setText("0 kW");
    }

    private void updateCurrentTime() {
        tvHora.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
    }

    private void updateEnergyData() {
        double mult = getCustomerEnergyMultiplier(currentCustomerId);
        energyData.solarGeneration = (2.5 + random.nextGaussian() * 0.5) * mult;
        energyData.cfeConsumption = (1.8 + random.nextGaussian() * 0.3) * mult;
        energyData.dieselGeneration = random.nextBoolean() ? ((0.5 + random.nextGaussian() * 0.2) * mult) : 0;
        energyData.batteryLevel = Math.max(0, Math.min(100, energyData.batteryLevel + random.nextGaussian() * 2));
        energyData.gridInjection = Math.max(0, energyData.solarGeneration - energyData.cfeConsumption);
        energyData.solarEfficiency = Math.min(95, 70 + random.nextGaussian() * 10);
        energyData.monthlySavings = energyData.solarGeneration * 24 * 30 * 3.5;
        energyData.dieselHours += energyData.dieselGeneration > 0 ? 0.05 : 0;
        energyData.fuelLevel = Math.max(0, energyData.fuelLevel - (energyData.dieselGeneration > 0 ? 0.1 : 0));
        energyData.factoryConsumption = energyData.solarGeneration + energyData.cfeConsumption + energyData.dieselGeneration;
    }

    private double getCustomerEnergyMultiplier(Integer id) {
        if (id == null) return 1.0;
        switch (id) {
            case 2:
                return 1.5;
            case 3:
                return 2.0;
            default:
                return 1.0;
        }
    }

    private void updateUI() {
        if (!isAdded()) return;
        getActivity().runOnUiThread(() -> {
            tvSolarValue.setText(formatPower(energyData.solarGeneration));
            tvCFEValue.setText(formatPower(energyData.cfeConsumption));
            tvDieselValue.setText(formatPower(energyData.dieselGeneration));
            tvBatteryValue.setText(String.format(Locale.getDefault(), "%.0f%%", energyData.batteryLevel));
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
        animateArrow(arrowSolarToFactory, energyData.solarGeneration > 0.1);
        animateArrow(arrowCFEToFactory, energyData.cfeConsumption > 0.1);
        animateArrow(arrowDieselToFactory, energyData.dieselGeneration > 0.1);
        animateArrow(arrowBatteryToFactory, energyData.batteryLevel > 20);
        animateArrow(arrowFactoryToCFE, energyData.gridInjection > 0.1);
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

    private void showError(String message) {
        if (isAdded() && getContext() != null) {
            Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    // Clase para almacenar datos de energía
    private static class EnergyData {
        double solarGeneration = 0;
        double cfeConsumption = 0;
        double dieselGeneration = 0;
        double batteryLevel = 75;
        double gridInjection = 0;
        double solarEfficiency = 85;
        double monthlySavings = 0;
        double dieselHours = 0;
        double fuelLevel = 80;
        double factoryConsumption = 0;
    }
}