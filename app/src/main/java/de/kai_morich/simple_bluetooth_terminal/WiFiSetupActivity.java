package de.kai_morich.simple_bluetooth_terminal;

import static de.kai_morich.simple_bluetooth_terminal.OctoNetCommandEncoder.bytesToHexString;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


import androidx.activity.OnBackPressedCallback;
import java.net.ConnectException;

// ===== IMPORT DEL MÓDULO DE VALIDACIÓN =====
import de.kai_morich.simple_bluetooth_terminal.wifi.WiFiValidationModule;

public class WiFiSetupActivity extends AppCompatActivity {

    // ===== CONSTANTES PERMISOS =====
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
    };

    // ===== VARIABLES UI PRINCIPALES =====
    private TextView textNetworkName, textNetworkStatus, textSetupStatus;
    private MaterialButton btnConnect, btnMediciones, btnBackToMenu;
    private MaterialButton btnConfigWifi, btnRefreshWifi;
    private ProgressBar progressSetup;

    // ===== VARIABLES UI PRE-VALIDACIÓN =====
    private TextView textValidationStats;
    private MaterialButton btnValidateWifi, btnManageNetworks;
    private boolean isPreValidationMode = true;

    // ===== VARIABLES UI INFORMACIÓN DEL DISPOSITIVO =====
    private LinearLayout layoutDeviceInfo, layoutDateTime, layoutWifiStatus, layoutNavigation;
    private TextView textDeviceSerial, textDeviceFabDate, textDeviceFabHour, textDeviceActCode;
    private TextView textDeviceHwVersion, textDeviceFwVersion, textDeviceDateTime;
    private TextView textConfiguredNetwork, textWifiStatus, textConfiguredPassword;

    // ===== VARIABLES TCP =====
    private Socket socket;
    private OutputStream outputStream;
    private BufferedReader inputReader;
    private Thread receiveThread;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private ExecutorService executor;
    private Handler handler = new Handler(Looper.getMainLooper());

    // ===== VARIABLES WIFI =====
    private WifiManager wifiManager;
    private BroadcastReceiver wifiReceiver;
    private WiFiValidationModule.ValidatedNetwork pendingNetworkToSend = null;
    private boolean autoSendEnabled = false;
    private String currentNetworkName = "";
    private boolean isESPNetwork = false;
    private boolean permissionsGranted = false;
    private List<ScanResult> availableNetworks = new ArrayList<>();

    // ===== VARIABLES CONTRASEÑA =====
    private String lastConfiguredPassword = "";
    private boolean passwordVisible = true;

    // ===== VARIABLES SETUP =====
    private int setupStep = 0;
    private boolean isSetupInProgress = false;
    private final String[] setupSteps = {
            "🔍 Conectando al dispositivo...",
            "🕐 Sincronizando hora...",
            "📱 Obteniendo información del dispositivo...",
            "📅 Verificando fecha y hora...",
            "📶 Verificando configuración WiFi...",
            "✅ Setup completado"
    };

    // ===== VARIABLES PARA VALIDACIÓN WIFI =====
    private WiFiValidationModule wifiValidationModule;
    private boolean isDeviceConnectionInProgress = false;
    private AlertDialog deviceConnectionDialog;
    private AlertDialog validationProgressDialog;

    // ===== CONSTANTES =====
    private static final String ESP_NETWORK_PREFIX = "ESP";
    private static final String DEVICE_IP = "192.168.4.1";
    private static final int DEVICE_PORT = 333;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_setup);

        executor = Executors.newCachedThreadPool();


        initializeViews();
        initializeWiFiValidationModule();

        if (checkAndRequestPermissions()) {
            initializeWiFi();
            setupListeners();
            checkCurrentNetwork();
        } else {
            setupListenersBasic();
        }

        updateValidationStats();
    }

    // ===== INICIALIZACIÓN MÓDULO DE VALIDACIÓN =====
    private void initializeWiFiValidationModule() {
        wifiValidationModule = new WiFiValidationModule(this, new WiFiValidationModule.ValidationListener() {
            @Override
            public void onValidationStarted(String ssid) {
                showValidationProgressDialog(ssid);
            }

            @Override
            public void onValidationSuccess(WiFiValidationModule.ValidatedNetwork network) {
                dismissValidationProgressDialog();
                showToast("✅ Red validada exitosamente: " + network.ssid);
                updateValidationStats();

                if (isPreValidationMode) {
                    showPreValidationSuccessDialog(network);
                } else {
                    showValidatedNetworkConfirmation(network);
                }
            }

            @Override
            public void onValidationFailed(String ssid, String error) {
                dismissValidationProgressDialog();
                showToast(error);
                showRetryValidationDialog(ssid);
            }

            @Override
            public void onValidationTimeout(String ssid) {
                dismissValidationProgressDialog();
                showToast("⏰ Timeout validando: " + ssid);
                showRetryValidationDialog(ssid);
            }

            @Override
            public void onDeviceConnectionStarted(String ssid) {
                isDeviceConnectionInProgress = true;
                showDeviceConnectionProgressDialog(ssid);
            }

            @Override
            public void onDeviceConnectionSuccess(String ssid) {
                isDeviceConnectionInProgress = false;
                dismissDeviceConnectionDialog();
                showToast("🎉 Dispositivo conectado a: " + ssid);
                wifiValidationModule.notifyDeviceConnectionSuccess(ssid);
                handler.postDelayed(() -> refreshWiFiStatus(), 2000);
            }

            @Override
            public void onDeviceConnectionTimeout(String ssid) {
                isDeviceConnectionInProgress = false;
                dismissDeviceConnectionDialog();
                showDeviceConnectionTimeoutDialog(ssid);
            }

            @Override
            public void onDeviceConnectionFailed(String ssid, String error) {
                isDeviceConnectionInProgress = false;
                dismissDeviceConnectionDialog();
                showToast(error);
            }

            @Override
            public void onOriginalNetworkRestored() {
                showToast("🔄 Red original restaurada");
            }

            @Override
            public void onValidationProgress(String ssid, String progress) {
                updateValidationProgressDialog(progress);
            }
        });
    }

    private void initializeViews() {
        // Views principales
        textNetworkName = findViewById(R.id.textNetworkName);
        textNetworkStatus = findViewById(R.id.textNetworkStatus);
        textSetupStatus = findViewById(R.id.textSetupStatus);
        btnConnect = findViewById(R.id.btnConnect);
        btnMediciones = findViewById(R.id.btnMediciones);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);
        progressSetup = findViewById(R.id.progressSetup);

        // Views pre-validación
        textValidationStats = findViewById(R.id.textValidationStats);
        btnValidateWifi = findViewById(R.id.btnValidateWifi);
        btnManageNetworks = findViewById(R.id.btnManageNetworks);

        // Layouts de secciones
        layoutDeviceInfo = findViewById(R.id.layoutDeviceInfo);
        layoutDateTime = findViewById(R.id.layoutDateTime);
        layoutWifiStatus = findViewById(R.id.layoutWifiStatus);
        layoutNavigation = findViewById(R.id.layoutNavigation);

        // TextViews de información del dispositivo
        textDeviceSerial = findViewById(R.id.textDeviceSerial);
        textDeviceFabDate = findViewById(R.id.textDeviceFabDate);
        textDeviceFabHour = findViewById(R.id.textDeviceFabHour);
        textDeviceActCode = findViewById(R.id.textDeviceActCode);
        textDeviceHwVersion = findViewById(R.id.textDeviceHwVersion);
        textDeviceFwVersion = findViewById(R.id.textDeviceFwVersion);
        textDeviceDateTime = findViewById(R.id.textDeviceDateTime);

        // TextViews de WiFi
        textConfiguredNetwork = findViewById(R.id.textConfiguredNetwork);
        textConfiguredPassword = findViewById(R.id.textConfiguredPassword);
        textWifiStatus = findViewById(R.id.textWifiStatus);

        // Botones WiFi
        btnConfigWifi = findViewById(R.id.btnConfigWifi);
        btnRefreshWifi = findViewById(R.id.btnRefreshWifi);

        // Estado inicial
        hideAllInfoSections();
        progressSetup.setVisibility(View.GONE);
    }

    private void hideAllInfoSections() {
        if (layoutDeviceInfo != null) layoutDeviceInfo.setVisibility(View.GONE);
        if (layoutDateTime != null) layoutDateTime.setVisibility(View.GONE);
        if (layoutWifiStatus != null) layoutWifiStatus.setVisibility(View.GONE);
        if (layoutNavigation != null) layoutNavigation.setVisibility(View.GONE);
    }

    private void initializeWiFi() {
        try {
            wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

            wifiReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (WifiManager.NETWORK_STATE_CHANGED_ACTION.equals(action)) {
                        checkCurrentNetwork();
                    } else if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(action)) {
                        updateAvailableNetworks();
                    }
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
            filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            registerReceiver(wifiReceiver, filter);

        } catch (Exception e) {
            showToast("❌ Error inicializando WiFi: " + e.getMessage());
        }
    }

    // ===== MÉTODOS DE PERMISOS =====

    private boolean checkAndRequestPermissions() {
        List<String> missingPermissions = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.NEARBY_WIFI_DEVICES") != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add("android.permission.NEARBY_WIFI_DEVICES");
            }
        }

        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this,
                    missingPermissions.toArray(new String[0]),
                    PERMISSION_REQUEST_CODE);
            return false;
        }

        permissionsGranted = true;
        return true;
    }

    private boolean hasWifiPermissions() {
        boolean wifiStatePermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED;
        boolean locationPermission = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        return wifiStatePermission && locationPermission && permissionsGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                permissionsGranted = true;
                showToast("✅ Permisos otorgados");
                initializeWiFi();
                setupListeners();
                checkCurrentNetwork();
                updateValidationStats();
            } else {
                showToast("❌ Permisos denegados - Funcionalidad limitada");
                setupListenersBasic();
            }
        }
    }

    // ===== SETUP DE LISTENERS =====

    private void setupListeners() {
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnect();
            } else {
                connectAndSetup();
                isPreValidationMode = false;
            }
        });

        btnMediciones.setOnClickListener(v -> goToFasores());
        btnBackToMenu.setOnClickListener(v -> backToMainMenu());
        btnConfigWifi.setOnClickListener(v -> showWiFiConfigDialog());
        btnRefreshWifi.setOnClickListener(v -> refreshWiFiStatus());
        btnValidateWifi.setOnClickListener(v -> showPreValidationDialog());
        btnManageNetworks.setOnClickListener(v -> showNetworkManagementDialog());
    }

    private void setupListenersBasic() {
        btnConnect.setOnClickListener(v -> {
            if (isConnected) {
                disconnect();
            } else {
                showManualConnectionDialog();
            }
        });

        btnMediciones.setOnClickListener(v -> {
            if (isConnected) {
                goToFasores();
            } else {
                showToast("❌ Conecte primero al dispositivo");
            }
        });

        btnBackToMenu.setOnClickListener(v -> backToMainMenu());
        btnConfigWifi.setOnClickListener(v -> showManualWiFiDialog());
        btnRefreshWifi.setOnClickListener(v -> refreshWiFiStatus());
        btnValidateWifi.setOnClickListener(v -> showPreValidationDialog());
        btnManageNetworks.setOnClickListener(v -> showNetworkManagementDialog());
    }

    // ===== MÉTODOS PRE-VALIDACIÓN CORREGIDOS =====

    private void updateValidationStats() {
        if (textValidationStats != null) {
            String stats = wifiValidationModule.getValidationStats();
            textValidationStats.setText(stats);
        }
    }

    private void showPreValidationDialog() {
        if (!hasWifiPermissions()) {
            showToast("❌ Se requieren permisos WiFi para validar redes");
            return;
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("🔍 Validar Red WiFi");
        builder.setMessage("💡 INFORMACIÓN:\n\n" +
                "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                "Se escaneará y mostrará la lista de redes WiFi de 2.4GHz disponibles para validar.");

        builder.setPositiveButton("📡 Escanear Redes", (dialog, which) -> {
            showNetworkScanForValidation();
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showNetworkScanForValidation() {
        AlertDialog scanDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("🔍 Escaneando Redes WiFi...")
                .setMessage("💡 INFORMACIÓN:\n\n" +
                        "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                        "Buscando redes de 2.4GHz disponibles para validar...")
                .setCancelable(false)
                .create();
        scanDialog.show();

        executor.execute(() -> {
            try {
                handler.post(() -> {
                    if (wifiManager != null && wifiManager.isWifiEnabled()) {
                        wifiManager.startScan();
                    }
                });

                Thread.sleep(4000); // Tiempo extendido para mejor detección

                handler.post(() -> {
                    scanDialog.dismiss();
                    updateAvailableNetworks();
                    displayNetworkSelectionForValidation();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    scanDialog.dismiss();
                    showToast("❌ Error al escanear: " + e.getMessage());
                    showNoNetworksFoundDialog();
                });
            }
        });
    }

    private void displayNetworkSelectionForValidation() {
        if (availableNetworks.isEmpty()) {
            showToast("❌ No se encontraron redes WiFi");
            showNoNetworksFoundDialog();
            return;
        }

        List<String> networkNames = new ArrayList<>();
        for (int i = 0; i < availableNetworks.size(); i++) {
            ScanResult result = availableNetworks.get(i);
            String signalLevel = getSignalStrength(result.level);
            int channel = getChannelFromFrequency(result.frequency);

            String displayName = String.format("#%d - %s (Canal %d - %s)",
                    (i + 1), result.SSID, channel, signalLevel);
            networkNames.add(displayName);
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📶 Seleccionar Red para Validar (" + availableNetworks.size() + " redes)");

        String infoMessage = "💡 INFORMACIÓN:\n\n" +
                "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                "Seleccione una red de la lista para validar:";
        builder.setMessage(infoMessage);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, networkNames);

        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        builder.setView(listView);

        builder.setPositiveButton("🔄 Refrescar Lista", (dialog, which) -> {
            dialog.dismiss();
            showToast("🔄 Refrescando lista de redes...");
            showNetworkScanForValidation();
        });

        builder.setNegativeButton("Cancelar", null);

        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            ScanResult selectedNetwork = availableNetworks.get(position);

            if (is24GHzNetwork(selectedNetwork)) {
                showPasswordDialogForValidation(selectedNetwork.SSID, selectedNetwork);
            } else {
                showToast("❌ Solo redes 2.4GHz son compatibles");
            }
        });

        dialog.show();
    }

    private void showNoNetworksFoundDialog() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📶 No se encontraron redes");
        builder.setMessage("💡 INFORMACIÓN:\n\n" +
                "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                "No se encontraron redes WiFi de 2.4GHz.\n\n" +
                "Posibles causas:\n" +
                "• WiFi deshabilitado\n" +
                "• Todas las redes son de 5GHz\n" +
                "• Problemas de señal\n\n" +
                "¿Desea intentar nuevamente?");

        builder.setPositiveButton("🔄 Refrescar", (dialog, which) -> {
            showNetworkScanForValidation();
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showPasswordDialogForValidation(String ssid, ScanResult selectedNetwork) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("🔍 Validar Credenciales");

        int channel = getChannelFromFrequency(selectedNetwork.frequency);
        String signalLevel = getSignalStrength(selectedNetwork.level);

        TextView infoTextView = new TextView(this);
        String networkInfo = String.format(
                "Red seleccionada: %s\n\n" +
                        "Canal: %d (%d MHz)\n\n" +
                        "Señal: %s (%d dBm)\n\n" +
                        "💡 INFORMACIÓN:\n" +
                        "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                        "⚠️ La app se conectará temporalmente para validar las credenciales.",
                ssid, channel, selectedNetwork.frequency, signalLevel, selectedNetwork.level
        );

        infoTextView.setText(networkInfo);
        infoTextView.setTextSize(14f);
        infoTextView.setPadding(60, 30, 60, 30);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(infoTextView);

        TextInputLayout passwordLayout = new TextInputLayout(this);
        passwordLayout.setHint("🔑 Contraseña WiFi");
        passwordLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);

        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(20, 40, 20, 20);
        passwordLayout.setLayoutParams(layoutParams);

        TextInputEditText passwordInput = new TextInputEditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT);
        passwordLayout.addView(passwordInput);
        layout.addView(passwordLayout);

        builder.setView(layout);

        builder.setPositiveButton("🔍 Validar", (dialog, which) -> {
            String password = passwordInput.getText().toString().trim();
            if (!password.isEmpty()) {
                wifiValidationModule.validateNetwork(ssid, password);
            } else {
                showToast("❌ Ingrese la contraseña");
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    // ===== DIÁLOGOS DE PROGRESO =====

    private void showValidationProgressDialog(String ssid) {
        dismissValidationProgressDialog();

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("🔍 Validando Red WiFi");
        builder.setMessage("Iniciando validación de: " + ssid + "\n\n" +
                "⚠️ Su conexión WiFi puede cambiar temporalmente");
        builder.setCancelable(false);

        validationProgressDialog = builder.create();
        validationProgressDialog.show();
    }

    private void updateValidationProgressDialog(String progress) {
        if (validationProgressDialog != null && validationProgressDialog.isShowing()) {
            validationProgressDialog.setMessage(progress);
        }
    }

    private void dismissValidationProgressDialog() {
        if (validationProgressDialog != null && validationProgressDialog.isShowing()) {
            validationProgressDialog.dismiss();
        }
        validationProgressDialog = null;
    }

    private void showDeviceConnectionProgressDialog(String ssid) {
        dismissDeviceConnectionDialog();

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("⏳ Conectando Dispositivo");
        builder.setMessage("Enviando credenciales al dispositivo:\n\n" +
                "Red: " + ssid + "\n\n" +
                "⏰ Esperando conexión (15 segundos)...\n\n" +
                "💡 El METER parpadeará en azul lentamente cuando esté conectado a Internet.");
        builder.setCancelable(false);

        deviceConnectionDialog = builder.create();
        deviceConnectionDialog.show();

        updateConnectionProgress(ssid, 15);
    }

    private void updateConnectionProgress(String ssid, int secondsLeft) {
        if (deviceConnectionDialog == null || !deviceConnectionDialog.isShowing()) {
            return;
        }

        if (secondsLeft <= 0) {
            return;
        }

        String message = "Enviando credenciales al dispositivo:\n\n" +
                "Red: " + ssid + "\n\n" +
                "⏰ Tiempo restante: " + secondsLeft + " segundos\n\n" +
                "💡 El METER parpadeará en azul lentamente cuando esté conectado a Internet.";

        deviceConnectionDialog.setMessage(message);

        if (secondsLeft > 1) {
            handler.postDelayed(() -> updateConnectionProgress(ssid, secondsLeft - 1), 1000);
        }
    }

    private void dismissDeviceConnectionDialog() {
        if (deviceConnectionDialog != null && deviceConnectionDialog.isShowing()) {
            deviceConnectionDialog.dismiss();
        }
        deviceConnectionDialog = null;
    }

    // ===== CONFIGURACIÓN WIFI PARA DISPOSITIVO CONECTADO =====

    private void showWiFiConfigDialog() {
        if (!isConnected) {
            showToast("❌ Conecte primero al dispositivo");
            return;
        }

        List<WiFiValidationModule.ValidatedNetwork> validatedNetworks =
                wifiValidationModule.getSuccessfullyValidatedNetworks();

        if (validatedNetworks.isEmpty()) {
            showToast("❌ No hay redes validadas. Valide una red primero.");
            return;
        }

        if (validatedNetworks.size() == 1) {
            WiFiValidationModule.ValidatedNetwork network = validatedNetworks.get(0);
            showQuickSendConfirmation(network);
        } else {
            showValidatedNetworksDialog(validatedNetworks);
        }
    }

    private void showQuickSendConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📡 Envío Rápido");
        builder.setMessage("¿Enviar la red validada al dispositivo?\n\n" +
                "Red: " + network.ssid + "\n\n" +
                "💡 El METER parpadeará en azul lentamente cuando esté conectado a Internet.\n\n" +
                "Esta acción es inmediata.");

        builder.setPositiveButton("📡 Enviar", (dialog, which) -> {
            sendValidatedNetworkToDevice(network);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showValidatedNetworksDialog(List<WiFiValidationModule.ValidatedNetwork> validatedNetworks) {
        if (validatedNetworks.isEmpty()) {
            showToast("❌ No hay redes validadas");
            return;
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📡 Seleccionar Red Validada");
        builder.setMessage("Seleccione una red validada para enviar al dispositivo:");

        List<String> networkNames = new ArrayList<>();
        for (WiFiValidationModule.ValidatedNetwork network : validatedNetworks) {
            String displayName = String.format("%s\n🔑 %s\n⏱️ %s",
                    network.ssid,
                    network.getMaskedPassword(),
                    formatTimestamp(network.validatedTimestamp));
            networkNames.add(displayName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, networkNames);

        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        builder.setView(listView);

        builder.setNegativeButton("Cancelar", null);

        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            WiFiValidationModule.ValidatedNetwork selectedNetwork = validatedNetworks.get(position);
            showValidatedNetworkConfirmation(selectedNetwork);
        });

        dialog.show();
    }

    private void showValidatedNetworkConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📡 Confirmar Envío");

        String message = String.format(
                "¿Enviar credenciales al dispositivo?\n\n" +
                        "Red: %s\n" +
                        "Contraseña: %s\n" +
                        "Validada: %s\n\n" +
                        "💡 El METER parpadeará en azul lentamente cuando esté conectado a Internet.\n\n" +
                        "Esta acción configurará el dispositivo.",
                network.ssid,
                network.getMaskedPassword(),
                formatTimestamp(network.validatedTimestamp)
        );

        builder.setMessage(message);

        builder.setPositiveButton("📡 Enviar", (dialog, which) -> {
            sendValidatedNetworkToDevice(network);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showPreValidationSuccessDialog(WiFiValidationModule.ValidatedNetwork network) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("✅ Red Validada Exitosamente");

        String message = String.format(
                "Red validada correctamente:\n\n" +
                        "SSID: %s\n" +
                        "Método: %s\n" +
                        "Timestamp: %s\n\n" +
                        "💡 INFORMACIÓN:\n" +
                        "🔵 Cuando el METER parpadea en azul lentamente significa que ya está conectado a Internet.\n\n" +
                        "La red está lista para ser enviada al dispositivo cuando se conecte.",
                network.ssid,
                network.validationMethod != null ? network.validationMethod : "Validación exitosa",
                formatTimestamp(network.validatedTimestamp)
        );

        builder.setMessage(message);

        builder.setPositiveButton("✅ Envío Automático", (dialog, which) -> {
            if (isConnected) {
                sendValidatedNetworkToDevice(network);
            } else {
                showToast("✅ Se enviará automáticamente al conectar al dispositivo");
                setAutoSendNetwork(network);
            }
        });

        builder.setNeutralButton("📋 Ver Redes", (dialog, which) -> {
            showNetworkManagementDialog();
        });

        builder.setNegativeButton("Solo Guardar", null);
        builder.show();
    }

    private void sendValidatedNetworkToDevice(WiFiValidationModule.ValidatedNetwork network) {
        clearAllWiFiConfiguration();

        wifiValidationModule.sendValidatedNetworkToDevice(network, new WiFiValidationModule.DeviceCommandSender() {
            @Override
            public void sendWiFiCredentials(String ssid, String password, WiFiValidationModule.ValidatedNetwork validatedNetwork) throws Exception {
                if (ssid == null || password == null || validatedNetwork == null) {
                    throw new Exception("❌ Error: Credenciales o red validada son null");
                }

                System.out.println("✅ Enviando credenciales: SSID=" + ssid + ", Password=" + (password.isEmpty() ? "VACÍA" : "CONFIGURADA") + ", Network=" + validatedNetwork.ssid);

                clearAllWiFiConfiguration();

                byte[] wifiCommand = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(ssid, password);
                sendTcpCommand(wifiCommand);

                lastConfiguredPassword = validatedNetwork.password;
                updatePasswordDisplay(validatedNetwork.password);
                updateUIFromValidatedNetwork(validatedNetwork);
            }
        });
    }

    private void clearAllWiFiConfiguration() {
        lastConfiguredPassword = "";
        passwordVisible = true;

        handler.post(() -> {
            try {
                if (textConfiguredNetwork != null) {
                    textConfiguredNetwork.setText("Red: Configurando...");
                }
                if (textConfiguredPassword != null) {
                    textConfiguredPassword.setText("Contraseña: Configurando...");
                    textConfiguredPassword.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    textConfiguredPassword.setOnClickListener(null);
                }
                if (textWifiStatus != null) {
                    textWifiStatus.setText("Estado: Configurando...");
                }
            } catch (Exception e) {
                System.out.println("❌ Error limpiando UI: " + e.getMessage());
            }
        });
    }

    private void updateUIFromValidatedNetwork(WiFiValidationModule.ValidatedNetwork network) {
        if (network == null) return;

        handler.post(() -> {
            try {
                if (textConfiguredNetwork != null) {
                    textConfiguredNetwork.setText("Red: " + network.ssid + " (Validada ✅)");
                }

                if (textWifiStatus != null) {
                    String status = "Configurado ✅ - " + (network.validationMethod != null ? network.validationMethod : "Validada");
                    textWifiStatus.setText("Estado: " + status);
                    textWifiStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                }
            } catch (Exception e) {
                System.out.println("❌ Error actualizando UI: " + e.getMessage());
            }
        });
    }

    private void showDeviceConnectionTimeoutDialog(String ssid) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("⏰ Timeout de Conexión");

        String message = "El dispositivo no se conectó a la red en el tiempo esperado.\n\n" +
                "Red: " + ssid + "\n\n" +
                "Posibles causas:\n" +
                "• Credenciales incorrectas\n" +
                "• Señal WiFi débil\n" +
                "• Red fuera de alcance\n" +
                "• Problemas de router\n\n" +
                "💡 El METER parpadeará en azul lentamente cuando esté conectado a Internet.";

        builder.setMessage(message);

        builder.setPositiveButton("🔄 Reintentar", (dialog, which) -> {
            WiFiValidationModule.ValidatedNetwork network = wifiValidationModule.getValidatedNetwork(ssid);
            if (network != null) {
                sendValidatedNetworkToDevice(network);
            }
        });

        builder.setNegativeButton("Cerrar", null);
        builder.show();
    }

    private void showRetryValidationDialog(String ssid) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("❌ Error de Validación");
        builder.setMessage("No se pudo validar la red: " + ssid + "\n\n¿Desea intentar nuevamente?");

        builder.setPositiveButton("🔄 Reintentar", (dialog, which) -> {
            showPasswordDialogForValidation(ssid, null);
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void showNetworkManagementDialog() {
        List<WiFiValidationModule.ValidatedNetwork> networks = wifiValidationModule.getValidatedNetworks();

        if (networks.isEmpty()) {
            showToast("📊 No hay redes almacenadas");
            return;
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📊 Gestionar Redes Validadas");

        String stats = wifiValidationModule.getValidationStats();
        builder.setMessage(stats + "\n\nSeleccione una red para gestionar:");

        List<String> networkNames = new ArrayList<>();
        for (WiFiValidationModule.ValidatedNetwork network : networks) {
            String status = network.isValidated ? "✅" : "⏳";
            String displayName = String.format("%s %s\n🔑 %s\n⏱️ %s",
                    status, network.ssid,
                    network.getMaskedPassword(),
                    formatTimestamp(network.validatedTimestamp));
            networkNames.add(displayName);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, networkNames);

        ListView listView = new ListView(this);
        listView.setAdapter(adapter);
        builder.setView(listView);

        builder.setPositiveButton("🗑️ Limpiar Todo", (dialog, which) -> {
            showClearNetworksConfirmation();
        });

        builder.setNegativeButton("Cerrar", null);

        AlertDialog dialog = builder.create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            dialog.dismiss();
            WiFiValidationModule.ValidatedNetwork selectedNetwork = networks.get(position);
            showNetworkDetailsDialog(selectedNetwork);
        });

        dialog.show();
    }

    private void showNetworkDetailsDialog(WiFiValidationModule.ValidatedNetwork network) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("📋 Detalles de Red");

        String details = String.format(
                "SSID: %s\n\n" +
                        "Estado: %s\n\n" +
                        "Contraseña: %s\n\n" +
                        "Validada: %s\n\n" +
                        "Método: %s\n\n" +
                        "Último resultado: %s\n\n" +
                        "Frecuencia: %d MHz (%s)\n\n" +
                        "Canal: %d",
                network.ssid,
                network.isValidated ? "✅ Validada" : "⏳ Pendiente",
                network.password.isEmpty() ? "(Red abierta)" : network.getMaskedPassword(),
                formatTimestamp(network.validatedTimestamp),
                network.validationMethod != null ? network.validationMethod : "N/A",
                network.lastConnectionResult != null ? network.lastConnectionResult : "N/A",
                network.frequency,
                network.is24GHz ? "2.4GHz" : "5GHz",
                network.channel
        );

        builder.setMessage(details);

        builder.setPositiveButton("🗑️ Eliminar", (dialog, which) -> {
            wifiValidationModule.removeValidatedNetwork(network.ssid);
            showToast("🗑️ Red eliminada");
            updateValidationStats();
        });

        builder.setNeutralButton("📡 Usar", (dialog, which) -> {
            if (network.isValidated) {
                if (isConnected) {
                    sendValidatedNetworkToDevice(network);
                } else {
                    pendingNetworkToSend = network;
                    showToast("💾 Red guardada para envío automático");
                }
            } else {
                showToast("❌ Red no validada aún");
            }
        });

        builder.setNegativeButton("Cerrar", null);
        builder.show();
    }

    private void showClearNetworksConfirmation() {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("⚠️ Confirmar Limpieza");
        builder.setMessage("¿Está seguro de eliminar todas las redes validadas?\n\nEsta acción no se puede deshacer.");

        builder.setPositiveButton("🗑️ Sí, eliminar todo", (dialog, which) -> {
            wifiValidationModule.clearValidatedNetworks();
            showToast("🗑️ Todas las redes eliminadas");
            updateValidationStats();
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    // ===== MÉTODOS WIFI AUXILIARES =====

    private boolean is24GHzNetwork(ScanResult scanResult) {
        int frequency = scanResult.frequency;
        return frequency >= 2400 && frequency <= 2500;
    }

    private int getChannelFromFrequency(int frequency) {
        if (frequency >= 2412 && frequency <= 2472) {
            return (frequency - 2412) / 5 + 1;
        } else if (frequency == 2484) {
            return 14;
        }
        return 0;
    }

    private String getSignalStrength(int level) {
        if (level >= -50) {
            return "Excelente";
        } else if (level >= -60) {
            return "Muy Buena";
        } else if (level >= -70) {
            return "Buena";
        } else if (level >= -80) {
            return "Regular";
        } else {
            return "Débil";
        }
    }

    @SuppressLint("MissingPermission")
    private void updateAvailableNetworks() {
        try {
            if (!hasWifiPermissions() || wifiManager == null) {
                return;
            }

            List<ScanResult> scanResults = wifiManager.getScanResults();
            availableNetworks.clear();

            for (ScanResult result : scanResults) {
                if (result.SSID != null && !result.SSID.isEmpty()) {
                    if (is24GHzNetwork(result)) {
                        availableNetworks.add(result);
                    }
                }
            }
        } catch (Exception e) {
            showToast("❌ Error al procesar redes: " + e.getMessage());
        }
    }

    private void showManualWiFiDialog() {
        if (!isConnected) {
            showToast("❌ Conecte primero al dispositivo");
            return;
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("⚙️ Configurar WiFi sin Validación");
        builder.setMessage("⚠️ ATENCIÓN: Enviará credenciales SIN validar.\n" +
                "Se recomienda validar primero la red.");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextInputLayout ssidLayout = new TextInputLayout(this);
        ssidLayout.setHint("Nombre de la red (SSID)");
        TextInputEditText ssidInput = new TextInputEditText(this);
        ssidLayout.addView(ssidInput);
        layout.addView(ssidLayout);

        TextView spacer = new TextView(this);
        spacer.setHeight(20);
        layout.addView(spacer);

        TextInputLayout passwordLayout = new TextInputLayout(this);
        passwordLayout.setHint("Contraseña WiFi");
        TextInputEditText passwordInput = new TextInputEditText(this);
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordLayout.addView(passwordInput);
        layout.addView(passwordLayout);

        builder.setView(layout);

        builder.setPositiveButton("📡 Enviar Directo", (dialog, which) -> {
            String ssid = ssidInput.getText().toString().trim();
            String password = passwordInput.getText().toString().trim();

            if (!ssid.isEmpty() && !password.isEmpty()) {
                sendWiFiCredentialsDirectly(ssid, password);
            } else {
                showToast("❌ Ingrese SSID y contraseña");
            }
        });

        builder.setNegativeButton("Cancelar", null);
        builder.show();
    }

    private void sendWiFiCredentialsDirectly(String ssid, String password) {
        clearAllWiFiConfiguration();

        AlertDialog progressDialog = new MaterialAlertDialogBuilder(this)
                .setTitle("📡 Configurando WiFi...")
                .setMessage("Enviando credenciales al dispositivo:\n\nRed: " + ssid)
                .setCancelable(false)
                .create();
        progressDialog.show();

        executor.execute(() -> {
            try {
                byte[] wifiCommand = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(ssid, password);
                sendTcpCommand(wifiCommand);
                Thread.sleep(3000);

                lastConfiguredPassword = password;

                handler.post(() -> {
                    progressDialog.dismiss();
                    updatePasswordDisplay(password);
                    showToast("✅ WiFi configurado - Use refrescar para verificar");
                    handler.postDelayed(() -> refreshWiFiStatus(), 1000);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    progressDialog.dismiss();
                    clearAllWiFiConfiguration();
                    showToast("❌ Error configurando WiFi: " + e.getMessage());
                });
            }
        });
    }

    private void refreshWiFiStatus() {
        if (!isConnected) {
            showToast("❌ Conecte primero al dispositivo");
            return;
        }

        showToast("🔄 Actualizando estado WiFi...");

        executor.execute(() -> {
            try {
                sendWiFiReadCommand();
                Thread.sleep(2000);
                handler.post(() -> showToast("✅ Estado WiFi actualizado"));
            } catch (Exception e) {
                handler.post(() -> showToast("❌ Error al actualizar: " + e.getMessage()));
            }
        });
    }

    // ===== MÉTODOS PARA MANEJO DE CONTRASEÑAS =====

    private void updatePasswordDisplay(String password) {
        if (textConfiguredPassword != null) {
            if (password == null || password.isEmpty()) {
                textConfiguredPassword.setText("Contraseña: No configurada");
                textConfiguredPassword.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                textConfiguredPassword.setOnClickListener(null);
            } else {
                String displayText = "Contraseña: " + password;
                textConfiguredPassword.setText(displayText);
                textConfiguredPassword.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                textConfiguredPassword.setOnClickListener(v -> togglePasswordVisibility());
            }
        }
    }

    private void togglePasswordVisibility() {
        if (lastConfiguredPassword.isEmpty()) return;

        passwordVisible = !passwordVisible;

        if (passwordVisible) {
            textConfiguredPassword.setText("Contraseña: " + lastConfiguredPassword);
            showToast("👁️ Contraseña visible - Toque para ocultar");
        } else {
            textConfiguredPassword.setText("Contraseña: " + maskPassword(lastConfiguredPassword));
            showToast("🙈 Contraseña oculta - Toque para mostrar");
        }
    }

    private String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        if (password.length() <= 2) {
            return "••••";
        }
        String masked = password.substring(0, 2);
        for (int i = 2; i < password.length() - 2; i++) {
            masked += "•";
        }
        if (password.length() > 2) {
            masked += password.substring(password.length() - 2);
        }
        return masked;
    }

    // ===== MÉTODOS DE RED =====

    @SuppressLint("MissingPermission")
    private void checkCurrentNetwork() {
        try {
            if (!hasWifiPermissions()) {
                textNetworkName.setText("Permisos WiFi requeridos");
                textNetworkStatus.setText("📱 Funcionalidad limitada");
                btnConnect.setEnabled(true);
                return;
            }

            if (wifiManager.isWifiEnabled()) {
                String ssid = wifiManager.getConnectionInfo().getSSID();
                if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.equals("")) {
                    currentNetworkName = ssid.replace("\"", "");
                    textNetworkName.setText("Red actual: " + currentNetworkName);

                    isESPNetwork = currentNetworkName.startsWith(ESP_NETWORK_PREFIX);

                    if (isESPNetwork) {
                        textNetworkStatus.setText("✅ Red ESP detectada - Listo para conectar");
                        textNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                    } else {
                        textNetworkStatus.setText("📱 Conexión manual disponible");
                        textNetworkStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
                        isESPNetwork = true;
                    }
                    btnConnect.setEnabled(true);
                } else {
                    showNoWifiConnectionState();
                }
            } else {
                showWifiDisabledState();
            }

        } catch (Exception e) {
            showToast("❌ Error verificando red: " + e.getMessage());
            showManualConnectionOption();
        }
    }

    private void showNoWifiConnectionState() {
        currentNetworkName = "Sin conexión WiFi";
        textNetworkName.setText("Estado: " + currentNetworkName);
        textNetworkStatus.setText("📱 Conexión manual disponible");
        btnConnect.setEnabled(true);
        isESPNetwork = true;
    }

    private void showWifiDisabledState() {
        currentNetworkName = "WiFi deshabilitado";
        textNetworkName.setText("Estado: " + currentNetworkName);
        textNetworkStatus.setText("📱 Conexión manual disponible");
        btnConnect.setEnabled(true);
        isESPNetwork = true;
    }

    private void showManualConnectionOption() {
        currentNetworkName = "Conexión manual";
        textNetworkName.setText("Estado: Error detectando red");
        textNetworkStatus.setText("🔧 Conexión manual disponible");
        btnConnect.setEnabled(true);
        isESPNetwork = true;
    }

    private void showManualConnectionDialog() {
        final String deviceIp = DEVICE_IP; // ✅ AGREGAR final
        final int devicePort = DEVICE_PORT; // ✅ AGREGAR final

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔌 Conexión Manual")
                .setMessage("Conectando directamente al dispositivo en:\n\n" +
                        "IP: " + deviceIp + "\n" +
                        "Puerto: " + devicePort + "\n\n" +
                        "Asegúrese de estar conectado a la red del dispositivo.")
                .setPositiveButton("Conectar", (dialog, which) -> {
                    connectAndSetup();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ===== MÉTODOS TCP =====

    private void connectAndSetup() {
        isConnecting = true;
        isSetupInProgress = true;
        setupStep = 0;
        isPreValidationMode = false;

        clearAllWiFiConfiguration();

        updateUI();
        progressSetup.setVisibility(View.VISIBLE);
        final String deviceIp = DEVICE_IP; // ✅ AGREGAR final
        final int devicePort = DEVICE_PORT; // ✅ AGREGAR final

        textSetupStatus.setText("🔍 Conectando a " + deviceIp + ":" + devicePort);

        executor.execute(() -> {
            try {
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(deviceIp, devicePort), 15000);
                socket.setSoTimeout(30000);
                outputStream = socket.getOutputStream();
                inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                isConnected = true;
                isConnecting = false;

                handler.post(() -> {
                    updateUI();
                    nextSetupStep();
                    showToast("✅ Conectado al dispositivo");
                });

                receiveThread = new Thread(this::receiveMessages);
                receiveThread.start();

                performAutomaticSetup();

            } catch (java.net.ConnectException e) {
                isConnecting = false;
                isSetupInProgress = false;
                handler.post(() -> {
                    updateUI();
                    progressSetup.setVisibility(View.GONE);
                    textSetupStatus.setText("❌ Dispositivo no responde");
                    showConnectionErrorDialog("Dispositivo no encontrado",
                            "El dispositivo no responde en " + deviceIp + ":" + devicePort);
                });
            } catch (Exception e) {
                isConnecting = false;
                isSetupInProgress = false;
                handler.post(() -> {
                    updateUI();
                    progressSetup.setVisibility(View.GONE);
                    textSetupStatus.setText("❌ Error: " + e.getMessage());
                    showConnectionErrorDialog("Error de conexión", "Error: " + e.getMessage());
                });
            }
        });
    }

    private void showConnectionErrorDialog(String title, String message) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("🔌 " + title)
                .setMessage(message)
                .setPositiveButton("Reintentar", (dialog, which) -> {
                    connectAndSetup(); // ✅ Esto ya no dará error
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void performAutomaticSetup() {
        executor.execute(() -> {
            try {
                handler.post(() -> {
                    nextSetupStep();
                    textSetupStatus.setText("🕐 Sincronizando hora del sistema...");
                });
                Thread.sleep(100);
                sendTimeWriteCommand();
                Thread.sleep(200);

                handler.post(() -> {
                    nextSetupStep();
                    textSetupStatus.setText("📱 Obteniendo información del dispositivo...");
                });
                Thread.sleep(100);
                sendDeviceIdReadCommand();
                Thread.sleep(300);

                handler.post(() -> {
                    nextSetupStep();
                    textSetupStatus.setText("📅 Verificando fecha y hora...");
                });
                Thread.sleep(100);
                sendDeviceTimeReadCommand();
                Thread.sleep(200);

                handler.post(() -> {
                    nextSetupStep();
                    textSetupStatus.setText("📶 Verificando configuración WiFi...");
                });
                Thread.sleep(100);
                sendWiFiReadCommand();
                Thread.sleep(200);

                handler.post(() -> {
                    nextSetupStep();
                    isSetupInProgress = false;
                    progressSetup.setVisibility(View.GONE);
                    textSetupStatus.setText("✅ Setup completado - Dispositivo configurado");
                    showAllInfoSections();
                    showToast("🎉 Setup completado exitosamente");

                    checkAndSendPendingNetwork();
                });

            } catch (Exception e) {
                handler.post(() -> {
                    isSetupInProgress = false;
                    progressSetup.setVisibility(View.GONE);
                    textSetupStatus.setText("❌ Error en setup: " + e.getMessage());
                    showToast("❌ Error en setup automático");
                });
            }
        });
    }

    private void nextSetupStep() {
        if (setupStep < setupSteps.length - 1) {
            setupStep++;
            textSetupStatus.setText(setupSteps[setupStep]);
        }
    }

    private void showAllInfoSections() {
        if (layoutDeviceInfo != null) layoutDeviceInfo.setVisibility(View.VISIBLE);
        if (layoutDateTime != null) layoutDateTime.setVisibility(View.VISIBLE);
        if (layoutWifiStatus != null) layoutWifiStatus.setVisibility(View.VISIBLE);
        if (layoutNavigation != null) layoutNavigation.setVisibility(View.VISIBLE);
    }

    // ===== MÉTODOS PARA COMANDOS OCTONET =====

    private void sendTimeWriteCommand() {
        try {
            Calendar cal = Calendar.getInstance();
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH) + 1;
            int day = cal.get(Calendar.DAY_OF_MONTH);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int minute = cal.get(Calendar.MINUTE);
            int second = cal.get(Calendar.SECOND);
            int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

            byte[] command = OctoNetCommandEncoder.createDeviceTimeWriteCommand(
                    year, month, day, hour, minute, second, dayOfWeek);
            sendTcpCommand(command);
        } catch (Exception e) {
            // Error silencioso
        }
    }

    private void setAutoSendNetwork(WiFiValidationModule.ValidatedNetwork network) {
        pendingNetworkToSend = network;
        autoSendEnabled = true;
        showToast("🔄 Red preparada para envío automático: " + network.ssid);
    }

    private void checkAndSendPendingNetwork() {
        if (autoSendEnabled && pendingNetworkToSend != null && isConnected && !isSetupInProgress) {
            showToast("📡 Enviando red automáticamente: " + pendingNetworkToSend.ssid);

            handler.postDelayed(() -> {
                if (isConnected) {
                    sendValidatedNetworkToDevice(pendingNetworkToSend);
                    pendingNetworkToSend = null;
                    autoSendEnabled = false;
                }
            }, 2000);
        }
    }

    private void sendDeviceIdReadCommand() {
        try {
            byte[] command = OctoNetCommandEncoder.createDeviceIdReadCommand();
            sendTcpCommand(command);
        } catch (Exception e) {
            // Error silencioso
        }
    }

    private void sendDeviceTimeReadCommand() {
        try {
            if (!isConnected || outputStream == null) {
                return;
            }
            byte[] command = OctoNetCommandEncoder.createDeviceTimeReadCommand();
            outputStream.write(command);
            outputStream.flush();
        } catch (Exception e) {
            // Error silencioso
        }
    }

    private void sendWiFiReadCommand() {
        try {
            byte[] command = OctoNetCommandEncoder.createWiFiSettingsReadCommand();
            sendTcpCommand(command);
            Thread.sleep(2000);
        } catch (Exception e) {
            // Error silencioso
        }
    }

    private void sendTcpCommand(byte[] command) throws Exception {
        if (!isConnected || outputStream == null) {
            throw new Exception("No conectado al dispositivo");
        }
        outputStream.write(command);
        outputStream.flush();
    }

// ===== MÉTODOS PARA RECIBIR MENSAJES =====

    private void receiveMessages() {
        byte[] buffer = new byte[2048];

        try {
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = socket.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);

                        System.out.println("📨 Datos recibidos: " + bytesToHexString(data));

                        // ✅ USAR VALIDACIÓN Y PROCESAMIENTO COMO EN TU CÓDIGO ANTERIOR
                        if (OctoNetCommandEncoder.validateCommandStructure(data)) {
                            OctoNetCommandEncoder.CmdSet commandType = OctoNetCommandEncoder.getCommandType(data);
                            byte[] commandData = OctoNetCommandEncoder.extractCommandData(data);

                            System.out.println("✅ Comando válido: " + commandType);
                            System.out.println("📊 Datos del comando: " + bytesToHexString(commandData));

                            processOctoNetCommand(commandType, commandData);
                        } else {
                            System.out.println("❌ Comando inválido recibido");
                        }
                    }
                } catch (SocketTimeoutException e) {
                    // Timeout normal, continuar
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                handler.post(() -> {
                    showToast("❌ Error en conexión");
                    disconnect();
                });
            }
        }
    }

    private void processOctoNetCommand(OctoNetCommandEncoder.CmdSet commandType, byte[] commandData) {
        System.out.println("🔍 Procesando comando: " + commandType);
        System.out.println("🔍 Datos recibidos: " + bytesToHexString(commandData));

        switch (commandType) {
            case DEVICE_ID_CONFIRMATION:
            case DEVICE_ID_READ:
                System.out.println("📱 Procesando DEVICE_ID");
                processDeviceIdDataFixed(commandData);
                break;

            case DEVICE_TIME_CONFIRMATION:
            case DEVICE_TIME_READ:
                System.out.println("🕐 Procesando DEVICE_TIME");
                processDeviceTimeDataFixed(commandData);
                break;

            case SETTINGS_WIFI_CONFIRMATION:
            case SETTINGS_WIFI_READ:
                System.out.println("📶 Procesando WIFI_SETTINGS");
                processWifiSettingsDataFixed(commandData);
                break;

            default:
                System.out.println("❓ Comando no manejado: " + commandType);
                break;
        }
    }

    private void processDeviceIdDataFixed(byte[] data) {
        try {
            if (data == null || data.length == 0) {
                System.out.println("❌ Datos DEVICE_ID vacíos");
                handler.post(() -> {
                    if (textDeviceSerial != null) textDeviceSerial.setText("📱 Serial: Sin datos");
                });
                return;
            }

            System.out.println("🔍 Procesando DEVICE_ID con " + data.length + " bytes");

            // ✅ USAR EL MISMO MÉTODO QUE EN TU CÓDIGO ANTERIOR
            String deviceInfo = new String(data, "UTF-8").trim();
            System.out.println("📄 Datos como string: " + deviceInfo);

            // Variables para almacenar información
            String serial = "--", fabDate = "--", fabHour = "--", actCode = "--", hwVersion = "--", fwVersion = "--";

            // ✅ LÓGICA IGUAL A TU CÓDIGO ANTERIOR QUE FUNCIONABA
            String[] lines = deviceInfo.split("\n");

            // Si es una sola línea larga (formato concatenado)
            if (lines.length == 1 && deviceInfo.length() >= 30) {
                System.out.println("🔍 Formato concatenado detectado, parseando...");

                try {
                    // Parsear según estructura esperada: 140423000046090224112325LVTXER4WW4B0D028
                    if (deviceInfo.length() >= 12) {
                        serial = deviceInfo.substring(0, 12); // 140423000046
                        System.out.println("   Serial: " + serial);
                    }

                    if (deviceInfo.length() >= 18) {
                        String dateStr = deviceInfo.substring(12, 18); // 090224
                        fabDate = dateStr.substring(0, 2) + "/" + dateStr.substring(2, 4) + "/" + dateStr.substring(4, 6);
                        System.out.println("   Fecha Fab: " + fabDate);
                    }

                    if (deviceInfo.length() >= 24) {
                        String timeStr = deviceInfo.substring(18, 24); // 112325
                        fabHour = timeStr.substring(0, 2) + ":" + timeStr.substring(2, 4) + ":" + timeStr.substring(4, 6);
                        System.out.println("   Hora Fab: " + fabHour);
                    }

                    if (deviceInfo.length() >= 28) {
                        actCode = deviceInfo.substring(24, 28); // LVTX
                        System.out.println("   Código Act: " + actCode);
                    }

                    if (deviceInfo.length() >= 34) {
                        hwVersion = deviceInfo.substring(28, 34); // ER4WW4
                        System.out.println("   HW Version: " + hwVersion);
                    }

                    if (deviceInfo.length() > 34) {
                        fwVersion = deviceInfo.substring(34); // B0D028
                        System.out.println("   FW Version: " + fwVersion);
                    }

                } catch (Exception parseError) {
                    System.out.println("❌ Error en parseo automático: " + parseError.getMessage());
                    serial = deviceInfo; // Usar como serial completo si falla
                }
            } else {
                // Buscar patrones en líneas separadas
                System.out.println("🔍 Buscando patrones en líneas separadas...");

                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    String lineLower = line.toLowerCase();
                    if (lineLower.contains("serial") && line.contains(":")) {
                        serial = extractValue(line);
                    } else if (lineLower.contains("fecha") && lineLower.contains("fab")) {
                        fabDate = extractValue(line);
                    } else if (lineLower.contains("hora") && lineLower.contains("fab")) {
                        fabHour = extractValue(line);
                    } else if (lineLower.contains("codigo") || lineLower.contains("act")) {
                        actCode = extractValue(line);
                    } else if (lineLower.contains("hw") && lineLower.contains("version")) {
                        hwVersion = extractValue(line);
                    } else if (lineLower.contains("fw") && lineLower.contains("version")) {
                        fwVersion = extractValue(line);
                    }
                }

                // Si no se encontraron patrones, usar toda la cadena como serial
                if (serial.equals("--") && !deviceInfo.isEmpty()) {
                    serial = deviceInfo;
                }
            }

            // ✅ CREAR VARIABLES FINALES PARA LAMBDA
            final String finalSerial = serial;
            final String finalFabDate = fabDate;
            final String finalFabHour = fabHour;
            final String finalActCode = actCode;
            final String finalHwVersion = hwVersion;
            final String finalFwVersion = fwVersion;

            System.out.println("✅ Datos parseados exitosamente");

            // ✅ ACTUALIZAR UI EN HILO PRINCIPAL
            handler.post(() -> {
                try {
                    if (textDeviceSerial != null) {
                        textDeviceSerial.setText("📱 Serial: " + finalSerial);
                        System.out.println("✅ Serial actualizado en UI");
                    }
                    if (textDeviceFabDate != null) {
                        textDeviceFabDate.setText("📅 Fecha Fab: " + finalFabDate);
                        System.out.println("✅ Fecha Fab actualizada en UI");
                    }
                    if (textDeviceFabHour != null) {
                        textDeviceFabHour.setText("🕐 Hora Fab: " + finalFabHour);
                        System.out.println("✅ Hora Fab actualizada en UI");
                    }
                    if (textDeviceActCode != null) {
                        textDeviceActCode.setText("🔑 Código Act: " + finalActCode);
                        System.out.println("✅ Código Act actualizado en UI");
                    }
                    if (textDeviceHwVersion != null) {
                        textDeviceHwVersion.setText("🔧 HW Version: " + finalHwVersion);
                        System.out.println("✅ HW Version actualizada en UI");
                    }
                    if (textDeviceFwVersion != null) {
                        textDeviceFwVersion.setText("💾 FW Version: " + finalFwVersion);
                        System.out.println("✅ FW Version actualizada en UI");
                    }

                    showToast("📱 Información del dispositivo actualizada");

                } catch (Exception uiError) {
                    System.out.println("❌ Error actualizando UI DEVICE_ID: " + uiError.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("❌ Error procesando DEVICE_ID: " + e.getMessage());
            e.printStackTrace();

            // Fallback - mostrar datos raw
            final String rawData = (data != null) ? new String(data) : "null";
            handler.post(() -> {
                if (textDeviceSerial != null) {
                    textDeviceSerial.setText("📱 Serial: " + rawData);
                }
            });
        }
    }

    private void processDeviceTimeDataFixed(byte[] data) {
        try {
            if (data == null || data.length < 5) {
                System.out.println("❌ Datos insuficientes para tiempo: " + (data != null ? data.length : 0) + " bytes");
                handler.post(() -> {
                    if (textDeviceDateTime != null) {
                        textDeviceDateTime.setText("🕐 Fecha/Hora: --/--/---- --:--:--");
                    }
                });
                return;
            }

            System.out.println("🔍 Procesando DEVICE_TIME con " + data.length + " bytes");
            System.out.println("🔍 Datos hex: " + bytesToHexString(data));

            int year, month, day, hour, minute, second;

            // ✅ USAR LA MISMA LÓGICA DE TU CÓDIGO ANTERIOR QUE FUNCIONABA
            try {
                // Intentar usar el decodificador oficial primero
                if (data.length >= 6) {
                    OctoNetCommandEncoder.DecodedDateTime decodedTime = OctoNetCommandEncoder.decodeDateTime(data);
                    year = decodedTime.year;
                    month = decodedTime.month;
                    day = decodedTime.day;
                    hour = decodedTime.hour;
                    minute = decodedTime.minute;
                    second = decodedTime.second;

                    System.out.println("✅ Decodificación exitosa con OctoNetCommandEncoder");
                } else {
                    throw new Exception("Datos insuficientes para decodificador oficial");
                }

            } catch (Exception decoderError) {
                System.out.println("⚠️ Decodificador oficial falló, usando interpretación manual");

                // ✅ INTERPRETACIÓN MANUAL IGUAL A TU CÓDIGO ANTERIOR
                year = 2000 + (data[0] & 0xFF);  // Byte 0: año

                // Byte 1: Día de semana (4 bits altos) + Mes (4 bits bajos)
                int dayWeekMonth = data[1] & 0xFF;
                month = dayWeekMonth & 0x0F;  // 4 bits bajos = mes
                int dayOfWeek = (dayWeekMonth >> 4) & 0x0F;  // 4 bits altos = día semana

                day = data[2] & 0xFF;     // Byte 2: día
                hour = data[3] & 0xFF;    // Byte 3: hora
                minute = data[4] & 0xFF;  // Byte 4: minuto
                second = data.length > 5 ? (data[5] & 0xFF) : 0; // Byte 5: segundo (si existe)

                System.out.println("🔍 Interpretación manual:");
                System.out.println("   Año: " + (data[0] & 0xFF) + " → " + year);
                System.out.println("   Mes+día_semana: " + dayWeekMonth + " → mes=" + month + ", día_semana=" + dayOfWeek);
                System.out.println("   Día: " + day);
                System.out.println("   Hora: " + hour);
                System.out.println("   Minuto: " + minute);
                System.out.println("   Segundo: " + second);
            }

            // ✅ VALIDACIONES IGUALES A TU CÓDIGO ANTERIOR
            if (year < 2020 || year > 2030) {
                System.out.println("⚠️ Año inválido (" + year + "), corrigiendo a 2025");
                year = 2025;
            }

            if (month < 1 || month > 12) {
                System.out.println("⚠️ Mes inválido (" + month + "), usando mes actual");
                Calendar cal = Calendar.getInstance();
                month = cal.get(Calendar.MONTH) + 1;
            }

            if (day < 1 || day > 31) {
                System.out.println("⚠️ Día inválido (" + day + "), corrigiendo");
                day = Math.max(1, Math.min(31, day));
            }

            if (hour > 23) {
                hour = Math.min(23, hour);
            }

            if (minute > 59) {
                minute = Math.min(59, minute);
            }

            if (second > 59) {
                second = Math.min(59, second);
            }

            // ✅ FORMATEAR IGUAL A TU CÓDIGO ANTERIOR
            final String dateTimeStr = String.format("%02d/%02d/%04d %02d:%02d:%02d",
                    day, month, year, hour, minute, second);

            System.out.println("✅ Resultado final: " + dateTimeStr);

            // ✅ ACTUALIZAR UI
            handler.post(() -> {
                try {
                    if (textDeviceDateTime != null) {
                        textDeviceDateTime.setText("🕐 Fecha/Hora: " + dateTimeStr);
                        System.out.println("✅ DateTime actualizado en UI");
                    }
                    showToast("🕐 Fecha y hora sincronizadas");
                } catch (Exception uiError) {
                    System.out.println("❌ Error actualizando UI TIME: " + uiError.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("❌ Error procesando DEVICE_TIME: " + e.getMessage());
            e.printStackTrace();

            // Fallback
            handler.post(() -> {
                if (textDeviceDateTime != null) {
                    textDeviceDateTime.setText("🕐 Fecha/Hora: Error procesando");
                }
            });
        }
    }

    // 5. NUEVO método processWifiSettingsDataFixed() - CORREGIDO
    private void processWifiSettingsDataFixed(byte[] data) {
        try {
            System.out.println("🔍 Procesando WIFI_SETTINGS con " + (data != null ? data.length : 0) + " bytes");

            if (data == null || data.length == 0) {
                System.out.println("❌ Datos WiFi vacíos");
                handler.post(() -> {
                    if (textConfiguredNetwork != null) {
                        textConfiguredNetwork.setText("📶 Red: Sin configurar");
                    }
                    if (textConfiguredPassword != null) {
                        updatePasswordDisplay("");
                    }
                    if (textWifiStatus != null) {
                        textWifiStatus.setText("📊 Estado: No configurado");
                    }
                });
                return;
            }

            // ✅ USAR EL PROCESADOR DE OctoNetCommandEncoder
            OctoNetCommandEncoder.WiFiSettings wifiSettings = OctoNetCommandEncoder.processWiFiSettingsResponse(data);

            System.out.println("✅ WiFi Settings parseados:");
            System.out.println("   SSID: " + wifiSettings.ssid);
            System.out.println("   IP: " + wifiSettings.ip);
            System.out.println("   RSSI: " + wifiSettings.rssi);

            // ✅ ACTUALIZAR UI
            handler.post(() -> {
                try {
                    // Actualizar red configurada
                    if (textConfiguredNetwork != null) {
                        String networkText = wifiSettings.ssid.isEmpty() ? "Sin configurar" : wifiSettings.ssid;
                        textConfiguredNetwork.setText("📶 Red: " + networkText);
                        System.out.println("✅ Red actualizada: " + networkText);
                    }

                    // ✅ MANTENER LA CONTRASEÑA QUE CONFIGURAMOS NOSOTROS
                    if (textConfiguredPassword != null) {
                        if (!lastConfiguredPassword.isEmpty()) {
                            // Mantener la contraseña que acabamos de configurar
                            updatePasswordDisplay(lastConfiguredPassword);
                            System.out.println("✅ Manteniendo contraseña configurada");
                        } else {
                            updatePasswordDisplay("");
                            System.out.println("✅ Sin contraseña configurada");
                        }
                    }

                    // Actualizar estado
                    if (textWifiStatus != null) {
                        String status = wifiSettings.ssid.isEmpty() ? "No configurado" : "Configurado ✅";
                        if (!wifiSettings.ssid.isEmpty() && wifiSettings.rssi != 0) {
                            status += " (RSSI: " + wifiSettings.rssi + " dBm)";
                        }
                        textWifiStatus.setText("📊 Estado: " + status);
                        System.out.println("✅ Estado actualizado: " + status);
                    }

                    showToast("📶 Estado WiFi actualizado");

                } catch (Exception uiError) {
                    System.out.println("❌ Error actualizando UI WiFi: " + uiError.getMessage());
                }
            });

        } catch (Exception e) {
            System.out.println("❌ Error procesando WiFi settings: " + e.getMessage());
            e.printStackTrace();

            // Fallback - mantener contraseña si existe
            handler.post(() -> {
                if (textConfiguredPassword != null && !lastConfiguredPassword.isEmpty()) {
                    updatePasswordDisplay(lastConfiguredPassword);
                }
            });
        }
    }


    private String extractValue(String line) {
        try {
            if (line.contains(":")) {
                String[] parts = line.split(":", 2);
                if (parts.length > 1) {
                    String value = parts[1].trim();
                    // Remover caracteres problemáticos
                    value = value.replace("\r", "");
                    value = value.replace("\n", "");
                    value = value.replace("\t", "");
                    value = value.replace("\0", "");
                    return value;
                }
            }
            String cleaned = line.trim();
            cleaned = cleaned.replace("\r", "");
            cleaned = cleaned.replace("\n", "");
            cleaned = cleaned.replace("\t", "");
            cleaned = cleaned.replace("\0", "");
            return cleaned;
        } catch (Exception e) {
            return "--";
        }
    }



    // MÉTODO CORREGIDO para actualizar estado WiFi
    private void updateWiFiStatusDisplayFixed(String status) {
        if (textWifiStatus == null) {
            System.out.println("⚠️ textWifiStatus es null");
            return;
        }

        try {
            String displayStatus;
            int color;

            String statusLower = status.toLowerCase().trim();

            switch (statusLower) {
                case "connected":
                case "conectado":
                    displayStatus = "🟢 Estado: Conectado";
                    color = android.R.color.holo_green_dark;
                    break;

                case "connecting":
                case "conectando":
                    displayStatus = "🟡 Estado: Conectando...";
                    color = android.R.color.holo_orange_dark;
                    break;

                case "disconnected":
                case "desconectado":
                    displayStatus = "🔴 Estado: Desconectado";
                    color = android.R.color.holo_red_dark;
                    break;

                case "error":
                    displayStatus = "⚠️ Estado: Error de conexión";
                    color = android.R.color.holo_red_dark;
                    break;

                case "timeout":
                    displayStatus = "⏰ Estado: Timeout";
                    color = android.R.color.holo_orange_dark;
                    break;

                default:
                    displayStatus = "📊 Estado: " + status;
                    color = android.R.color.darker_gray;
                    break;
            }

            textWifiStatus.setText(displayStatus);
            textWifiStatus.setTextColor(ContextCompat.getColor(this, color));

            System.out.println("✅ Estado WiFi actualizado: " + displayStatus);

        } catch (Exception e) {
            System.out.println("❌ Error actualizando estado WiFi: " + e.getMessage());
        }
    }

    // MÉTODO AUXILIAR para DEBUG - Agregar al final de la clase
    private void logDeviceResponse(String type, String message) {
        System.out.println("==========================================");
        System.out.println("📋 RESPUESTA " + type + ":");
        System.out.println("   Mensaje: " + message);
        System.out.println("   Timestamp: " + System.currentTimeMillis());
        System.out.println("==========================================");
    }

    // MÉTODO PARA VALIDAR RESPUESTAS - Agregar al final de la clase
    private boolean validateResponseFormat(String message, String expectedType, int expectedFields) {
        if (message == null || message.isEmpty()) {
            System.out.println("❌ Mensaje vacío para " + expectedType);
            return false;
        }

        if (!message.contains(expectedType + ":")) {
            System.out.println("❌ Mensaje no contiene " + expectedType);
            return false;
        }

        String[] parts = message.split(":");
        if (parts.length < 2) {
            System.out.println("❌ Formato inválido para " + expectedType);
            return false;
        }

        String[] fields = parts[1].split(",");
        if (fields.length < expectedFields) {
            System.out.println("❌ Campos insuficientes para " + expectedType + ": " + fields.length + "/" + expectedFields);
            return false;
        }

        System.out.println("✅ Formato válido para " + expectedType + ": " + fields.length + " campos");
        return true;
    }

    private void processDeviceIdResponse(String message) {
        try {
            // Formato esperado: "DEVICE_ID:serial,fab_date,fab_hour,act_code,hw_version,fw_version"
            String[] parts = message.split(":");
            if (parts.length > 1) {
                String[] deviceInfo = parts[1].split(",");

                if (deviceInfo.length >= 6) {
                    textDeviceSerial.setText("Serial: " + deviceInfo[0]);
                    textDeviceFabDate.setText("Fecha Fab: " + deviceInfo[1]);
                    textDeviceFabHour.setText("Hora Fab: " + deviceInfo[2]);
                    textDeviceActCode.setText("Código Act: " + deviceInfo[3]);
                    textDeviceHwVersion.setText("HW Version: " + deviceInfo[4]);
                    textDeviceFwVersion.setText("FW Version: " + deviceInfo[5]);

                    showToast("📱 Información del dispositivo actualizada");
                }
            }
        } catch (Exception e) {
            showToast("❌ Error procesando info del dispositivo: " + e.getMessage());
        }
    }

    private void processTimeResponse(String message) {
        try {
            // Formato esperado: "TIME:year,month,day,hour,minute,second,dayofweek"
            String[] parts = message.split(":");
            if (parts.length > 1) {
                String[] timeInfo = parts[1].split(",");

                if (timeInfo.length >= 7) {
                    String dateTime = String.format("%s-%s-%s %s:%s:%s (Día %s)",
                            timeInfo[0], timeInfo[1], timeInfo[2],
                            timeInfo[3], timeInfo[4], timeInfo[5], timeInfo[6]);

                    textDeviceDateTime.setText("Fecha/Hora: " + dateTime);
                    showToast("🕐 Fecha y hora sincronizadas");
                }
            }
        } catch (Exception e) {
            showToast("❌ Error procesando fecha/hora: " + e.getMessage());
        }
    }

    private void processWiFiResponse(String message) {
        try {
            // Formato esperado: "WIFI:ssid,password,status"
            String[] parts = message.split(":");
            if (parts.length > 1) {
                String[] wifiInfo = parts[1].split(",");

                if (wifiInfo.length >= 3) {
                    String ssid = wifiInfo[0].isEmpty() ? "No configurada" : wifiInfo[0];
                    String password = wifiInfo[1];
                    String status = wifiInfo[2];

                    textConfiguredNetwork.setText("Red: " + ssid);

                    if (!password.isEmpty()) {
                        lastConfiguredPassword = password;
                        updatePasswordDisplay(password);
                    } else {
                        textConfiguredPassword.setText("Contraseña: No configurada");
                        textConfiguredPassword.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray));
                    }

                    updateWiFiStatusDisplay(status);
                    showToast("📶 Estado WiFi actualizado");
                }
            }
        } catch (Exception e) {
            showToast("❌ Error procesando estado WiFi: " + e.getMessage());
        }
    }

    private void updateWiFiStatusDisplay(String status) {
        if (textWifiStatus == null) return;

        String displayStatus;
        int color;

        switch (status.toLowerCase()) {
            case "connected":
                displayStatus = "Estado: Conectado ✅";
                color = android.R.color.holo_green_dark;
                break;
            case "connecting":
                displayStatus = "Estado: Conectando ⏳";
                color = android.R.color.holo_orange_dark;
                break;
            case "disconnected":
                displayStatus = "Estado: Desconectado ❌";
                color = android.R.color.holo_red_dark;
                break;
            case "error":
                displayStatus = "Estado: Error de conexión ⚠️";
                color = android.R.color.holo_red_dark;
                break;
            default:
                displayStatus = "Estado: " + status;
                color = android.R.color.darker_gray;
        }

        textWifiStatus.setText(displayStatus);
        textWifiStatus.setTextColor(ContextCompat.getColor(this, color));
    }

    private void processErrorResponse(String message) {
        showToast("⚠️ Error del dispositivo: " + message);
    }

// ===== MÉTODOS DE DESCONEXIÓN =====

    private void disconnect() {
        isConnected = false;
        isConnecting = false;
        isSetupInProgress = false;

        try {
            if (receiveThread != null && receiveThread.isAlive()) {
                receiveThread.interrupt();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (inputReader != null) {
                inputReader.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            // Error silencioso al desconectar
        }

        hideAllInfoSections();
        progressSetup.setVisibility(View.GONE);
        updateUI();
        textSetupStatus.setText("🔌 Desconectado del dispositivo");
        showToast("🔌 Desconectado");
    }

// ===== MÉTODOS DE INTERFAZ =====

    private void updateUI() {
        if (isConnecting) {
            btnConnect.setText("⏳ Conectando...");
            btnConnect.setEnabled(false);
            btnMediciones.setEnabled(false);
            btnConfigWifi.setEnabled(false);
            btnRefreshWifi.setEnabled(false);
        } else if (isConnected) {
            btnConnect.setText("🔌 Desconectar");
            btnConnect.setEnabled(true);
            btnMediciones.setEnabled(true);
            btnConfigWifi.setEnabled(true);
            btnRefreshWifi.setEnabled(true);
        } else {
            btnConnect.setText("🔗 Conectar a Dispositivo");
            btnConnect.setEnabled(true);
            btnMediciones.setEnabled(false);
            btnConfigWifi.setEnabled(false);
            btnRefreshWifi.setEnabled(false);
        }
    }

// ===== MÉTODOS DE NAVEGACIÓN =====

    private void goToFasores() {
        if (!isConnected) {
            showToast("❌ Conecte primero al dispositivo");
            return;
        }

        Intent intent = new Intent(this, FasoresActivity.class);
        startActivity(intent);
    }

    private void backToMainMenu() {
        if (isConnected) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("⚠️ Dispositivo Conectado")
                    .setMessage("¿Desea desconectarse y volver al menú principal?")
                    .setPositiveButton("Sí, desconectar", (dialog, which) -> {
                        disconnect();
                        finish();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            finish();
        }
    }

// ===== MÉTODOS AUXILIARES =====

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private String formatTimestamp(long timestamp) {
        if (timestamp <= 0) return "N/A";

        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(
                    "dd/MM/yyyy HH:mm:ss", java.util.Locale.getDefault());
            return sdf.format(new java.util.Date(timestamp));
        } catch (Exception e) {
            return "Error en fecha";
        }
    }

// ===== MÉTODOS DE CICLO DE VIDA =====

    @Override
    protected void onResume() {
        super.onResume();
        if (hasWifiPermissions()) {
            checkCurrentNetwork();
            updateValidationStats();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // No desconectar automáticamente al pausar
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            if (wifiReceiver != null) {
                unregisterReceiver(wifiReceiver);
            }
        } catch (Exception e) {
            // Error silencioso
        }

        if (isConnected) {
            disconnect();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        dismissValidationProgressDialog();
        dismissDeviceConnectionDialog();
    }

}
