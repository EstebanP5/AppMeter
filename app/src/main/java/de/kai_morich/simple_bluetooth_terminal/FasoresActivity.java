package de.kai_morich.simple_bluetooth_terminal;

// ===== IMPORTS COMPLETOS =====
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.kai_morich.simple_bluetooth_terminal.wifi.WiFiValidationModule;

public class FasoresActivity extends AppCompatActivity {

    // ===== WIFI & VALIDATION =====
    private WifiManager wifiManager;
    private BroadcastReceiver wifiReceiver;
    private WiFiValidationModule wifiValidationModule;
    private List<ScanResult> availableNetworks = new ArrayList<>();
    private boolean userRequestedWifiScan = false;

    // ===== UI ELEMENTS =====
    private Spinner spinnerCableado, spinnerAmperes;
    private ImageView imageDiagram;
    private ImageButton btnPlay;
    private LinearLayout btnBackToMenu;
    private Button btnDeviceId, btnConfigWifi;
    private Handler handler = new Handler(Looper.getMainLooper());

    // ===== FASORES =====
    private FasorView fasorVoltaje, fasorCorriente;

    // ===== MODAL DEL DIAGRAMA =====
    private View diagramModal;
    private ImageView modalDiagramImage;
    private boolean isModalVisible = false;

    // TextViews para mediciones
    private TextView tvV1, tvV2, tvV3, tvA1, tvA2, tvA3, tvW1, tvW2, tvW3, tvHz1, tvHz2, tvHz3;
    private TextView tvpF1, tvpF2, tvpF3;
    private TextView tvCH1, tvCH2, tvCH3;

    // ===== CONEXIÓN TCP INDEPENDIENTE =====
    private Socket socket;
    private OutputStream outputStream;
    private ExecutorService executor;
    private String deviceIp = "192.168.4.1";
    private int devicePort = 333;
    private boolean isConnectedToDevice = false;

    // ===== CONFIGURACIÓN DEL MEDIDOR =====
    private int periodConfig = 0; // FIJO EN 1 MINUTO
    private int sensorsConfig = 1; // 0=20A, 1=50A, 2=200A, 3=400A, 4=1000A, 5=3000A
    private int meteringTypeConfig = 3; // FORZAR Carga Trifásica
    private boolean recordingConfig = true;

    // Configuración de interfaz
    private int rangoAmperes = 50;
    private int tipoCableado = 3; // Carga Trifásica

    // ===== AUTO-LECTURA =====
    private boolean autoReadEnabled = false;
    private static final int AUTO_READ_INTERVAL = 5000; // 5 segundos FIJO
    private Runnable autoReadTask;
    private Handler autoReadHandler = new Handler(Looper.getMainLooper());

    // ===== DATOS DEL MEDIDOR =====
    private float[] voltajes = {0.0f, 0.0f, 0.0f};
    private float[] corrientes = {0.0f, 0.0f, 0.0f};
    private float[] potencias = {0.0f, 0.0f, 0.0f};
    private float[] frecuencias = {0.0f, 0.0f, 0.0f};
    private float[] angulos = {0.0f, 120.0f, 240.0f};
    private OctoNetCommandEncoder.WiFiSettings lastReadWifiSettings = null;

    // ===== ESTADO =====
    private long tiempoInicio;

    private Runnable updateCurrentDataRunnable;

    private InputStream inputStream;
    private int contadorMuestras = 0;
    private boolean isWaitingResponse = false;
    private boolean configurationSynced = false;
    private boolean skipSpinnerEvents = false;

    // ===== DATOS DEVICE ID Y WIFI =====
    private DeviceIdInfo lastReadDeviceIdInfo;

    private static class DeviceIdInfo {
        String serial = "N/A", facDate = "N/A", facHour = "N/A",
                actCode = "N/A", hwVersion = "N/A", fwVersion = "N/A";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fasores);

        executor = Executors.newCachedThreadPool();

        initializeViews();
        initializeWiFi();
        initializeWiFiValidationModule();
        setupSpinners();
        setupButtons();
        setupAutoReadTask();

        // Conectar directamente
        handler.postDelayed(() -> connectToDeviceIndependent(), 500);
    }

    private void initializeViews() {
        // Spinners y controles
        spinnerCableado = findViewById(R.id.spinnerCableado);
        spinnerAmperes = findViewById(R.id.spinnerAmperes);
        imageDiagram = findViewById(R.id.imageDiagram);
        btnPlay = findViewById(R.id.btnPlay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        // Botones WiFi y Device ID
        btnDeviceId = findViewById(R.id.btnDeviceId);
        btnConfigWifi = findViewById(R.id.btnConfigWifi);

        // Fasores con modo 3 ejes
        fasorVoltaje = findViewById(R.id.fasorVoltaje);
        fasorCorriente = findViewById(R.id.fasorCorriente);

        if (fasorVoltaje != null) {
            fasorVoltaje.setThreeAxisMode(true);
            fasorVoltaje.setTitle("Voltajes Trifásicos");
            fasorVoltaje.setUnit("V");
            fasorVoltaje.setAutoScale(true);
        }

        if (fasorCorriente != null) {
            fasorCorriente.setThreeAxisMode(true);
            fasorCorriente.setTitle("Corrientes Trifásicas");
            fasorCorriente.setUnit("A");
            fasorCorriente.setAutoScale(true);
        }

        // TextViews para mediciones
        tvV1 = findViewById(R.id.tvV1);
        tvV2 = findViewById(R.id.tvV2);
        tvV3 = findViewById(R.id.tvV3);
        tvA1 = findViewById(R.id.tvA1);
        tvA2 = findViewById(R.id.tvA2);
        tvA3 = findViewById(R.id.tvA3);
        tvW1 = findViewById(R.id.tvW1);
        tvW2 = findViewById(R.id.tvW2);
        tvW3 = findViewById(R.id.tvW3);
        tvHz1 = findViewById(R.id.tvHz1);
        tvHz2 = findViewById(R.id.tvHz2);
        tvHz3 = findViewById(R.id.tvHz3);
        tvpF1 = findViewById(R.id.tvpF1);
        tvpF2 = findViewById(R.id.tvpF2);
        tvpF3 = findViewById(R.id.tvpF3);
        tvCH1 = findViewById(R.id.tvCH1);
        tvCH2 = findViewById(R.id.tvCH2);
        tvCH3 = findViewById(R.id.tvCH3);

        initializeDisplayValues();
    }

    private void setupSpinners() {
        String[] cableadoOptions = {"Carga Trifásica"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        String[] amperesOptions = {
                 "50A", "200A",
                "400A", "1000A", "3000A"
        };
        setupSpinnerAdapter(spinnerAmperes, amperesOptions);

        setupSpinnerListeners();

        skipSpinnerEvents = true;
        spinnerAmperes.setSelection(1);
        spinnerCableado.setSelection(0);
        skipSpinnerEvents = false;

        setSpinnersEnabled(false);
    }

    private void setupSpinnerAdapter(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // =========================================================================
    // ===== WIFI INITIALIZATION ==============================================
    // =========================================================================

    private void initializeWiFi() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    if (userRequestedWifiScan) {
                        userRequestedWifiScan = false;
                        updateAvailableNetworks();
                        displayNetworkSelectionForValidation();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    private void initializeWiFiValidationModule() {
        wifiValidationModule = new WiFiValidationModule(this, new WiFiValidationModule.ValidationListener() {
            @Override
            public void onValidationStarted(String ssid) {
                showToast("🔍 Validando red: " + ssid);
            }

            @Override
            public void onValidationProgress(String ssid, String progress) {
                showToast(progress);
            }

            @Override
            public void onValidationSuccess(WiFiValidationModule.ValidatedNetwork network) {
                showToast("✅ Red " + network.ssid + " validada");
                showValidatedNetworkConfirmation(network);
            }

            @Override
            public void onValidationFailed(String ssid, String error) {
                showToast("❌ " + error);
            }

            @Override
            public void onValidationTimeout(String ssid) {
                showToast("⏰ Timeout validando " + ssid);
            }

            @Override
            public void onDeviceConnectionStarted(String ssid) {
            }

            @Override
            public void onDeviceConnectionSuccess(String ssid) {
            }

            @Override
            public void onDeviceConnectionTimeout(String ssid) {
            }

            @Override
            public void onDeviceConnectionFailed(String ssid, String error) {
            }

            @Override
            public void onOriginalNetworkRestored() {
            }
        });
    }

    // =========================================================================
    // ===== SETUP AUTOMÁTICO =================================================
    // =========================================================================

    private void performAutomaticSetup() {
        if (!isConnectedToDevice) {
            System.out.println("❌ FASORES - No hay conexión para setup");
            return;
        }

        System.out.println("⚙️ FASORES - Iniciando setup automático...");

        executor.execute(() -> {
            try {
                handler.post(() -> showToast("⚙️ Configuración automática..."));

                // 1. Sincronizar hora (SIN ESPERA LARGA)
                System.out.println("🕐 FASORES - Paso 1: Sincronizando hora...");
                handler.post(() -> showToast("🕐 Sincronizando hora..."));
                sendTimeWriteCommand();
                Thread.sleep(500); // ✅ SOLO 500ms

                // 2. Verificar WiFi
                System.out.println("📡 FASORES - Paso 2: Verificando WiFi...");
                handler.post(() -> showToast("📡 Verificando WiFi..."));
                sendWiFiReadCommand();
                Thread.sleep(500); // ✅ SOLO 500ms

                // 3. Leer configuración
                System.out.println("🔧 FASORES - Paso 3: Leyendo configuración...");
                handler.post(() -> showToast("🔧 Leyendo configuración..."));
                readDeviceConfigurationIndependent();
                Thread.sleep(500); // ✅ SOLO 500ms

                // ✅ MARCAR COMO SINCRONIZADO INMEDIATAMENTE
                System.out.println("✅ FASORES - Setup completado");
                handler.post(() -> {
                    showToast("🎉 Setup completado");
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                });

            } catch (InterruptedException e) {
                System.out.println("⚠️ FASORES - Setup interrumpido");
                handler.post(() -> showToast("⚠️ Setup interrumpido"));
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error en setup: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    showToast("❌ Error en setup: " + e.getMessage());
                    configurationSynced = true; // ✅ PERMITIR USO AUNQUE FALLE
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                });
            }
        });
    }

    // =========================================================================
    // ===== DEVICE ID MODAL ==================================================
    // =========================================================================

    private void showDeviceIdModal() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }

        // ✅ Si ya hay datos, mostrar inmediatamente
        if (lastReadDeviceIdInfo != null &&
                !lastReadDeviceIdInfo.serial.equals("N/A") &&
                !lastReadDeviceIdInfo.serial.equals("ERROR") &&
                !lastReadDeviceIdInfo.serial.equals("DESCONOCIDO")) {
            System.out.println("✓ FASORES - Mostrando Device ID desde caché");
            displayDeviceIdModal();
            return;
        }

        // ✅ Solicitar datos
        System.out.println("📤 FASORES - Solicitando Device ID...");
        showToast("📋 Solicitando información...");
        sendDeviceIdReadCommand();

        // ✅ ESPERAR SOLO 2 SEGUNDOS Y MOSTRAR LO QUE HAYA
        handler.postDelayed(() -> {
            if (lastReadDeviceIdInfo != null) {
                displayDeviceIdModal();
            } else {
                showToast("⏰ Sin respuesta del dispositivo");
            }
        }, 2000); // ✅ SOLO 2 SEGUNDOS
    }

    private void displayDeviceIdModal() {
        if (lastReadDeviceIdInfo == null) {
            showToast("❌ No hay información disponible");
            return;
        }

        String deviceInfo =
                "Serial: " + lastReadDeviceIdInfo.serial + "\n\n" +
                        "Fecha Fab.: " + lastReadDeviceIdInfo.facDate + "\n\n" +
                        "Hora Fab.: " + lastReadDeviceIdInfo.facHour + "\n\n" +
                        "Código Act.: " + lastReadDeviceIdInfo.actCode + "\n\n" +
                        "HW Version: " + lastReadDeviceIdInfo.hwVersion + "\n\n" +
                        "FW Version: " + lastReadDeviceIdInfo.fwVersion;

        new MaterialAlertDialogBuilder(this)
                .setTitle("📱 Información del Dispositivo")
                .setMessage(deviceInfo)
                .setPositiveButton("OK", null)
                .show();

        System.out.println("✅ FASORES - Modal Device ID mostrado correctamente");
    }

    // =========================================================================
    // ===== WIFI CONFIGURATION ===============================================
    // =========================================================================

    private void showNetworkScanForValidation() {
        if (!isConnectedToDevice) {
            showToast("❌ Debes estar conectado al medidor para configurar el WiFi.");
            return;
        }

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            @SuppressLint("MissingPermission")
            String currentSsid = wifiManager.getConnectionInfo().getSSID().replace("\"", "");

            if (!currentSsid.startsWith("ESP")) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("⚠️ Advertencia")
                        .setMessage("No estás conectado a la red del medidor (ESP).\n\n" +
                                "Estás en: " + currentSsid + "\n\n" +
                                "¿Deseas continuar de todas formas?")
                        .setPositiveButton("Continuar", (dialog, which) -> {
                            showToast("🔍 Escaneando redes WiFi...");
                            userRequestedWifiScan = true;
                            wifiManager.startScan();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
        }

        showToast("🔍 Escaneando redes WiFi...");
        userRequestedWifiScan = true;
        wifiManager.startScan();
    }

    @SuppressLint("MissingPermission")
    private void updateAvailableNetworks() {
        availableNetworks.clear();
        availableNetworks.addAll(wifiManager.getScanResults());
    }

    private void displayNetworkSelectionForValidation() {
        if (availableNetworks.isEmpty()) {
            showToast("❌ No se encontraron redes WiFi.");
            return;
        }

        List<String> networkNames = new ArrayList<>();
        for (ScanResult result : availableNetworks) {
            if (result.SSID != null && !result.SSID.isEmpty() && !result.SSID.startsWith("ESP")) {
                networkNames.add(result.SSID);
            }
        }

        if (networkNames.isEmpty()) {
            showToast("❌ No hay redes disponibles (se excluyen redes ESP)");
            return;
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("📡 Selecciona una Red WiFi para el Medidor")
                .setItems(networkNames.toArray(new String[0]), (dialog, which) -> {
                    String selectedSsid = networkNames.get(which);
                    showPasswordDialogForValidation(selectedSsid);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showPasswordDialogForValidation(String ssid) {
        final FrameLayout container = new FrameLayout(this);
        final TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("🔐 Contraseña para " + ssid);
        textInputLayout.setPadding(48, 16, 48, 16);

        final TextInputEditText editText = new TextInputEditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        textInputLayout.addView(editText);
        container.addView(textInputLayout);

        // ✅ CREAR REFERENCIA FINAL PARA USAR EN LAMBDA
        final String finalSsid = ssid;

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔑 Validar Credenciales")
                .setView(container)
                .setPositiveButton("Validar", (dialog, which) -> {
                    String password = editText.getText().toString();
                    if (password.isEmpty()) {
                        showToast("❌ La contraseña no puede estar vacía");
                        return;
                    }
                    // ✅ USAR finalSsid
                    wifiValidationModule.validateNetwork(finalSsid, password);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showValidatedNetworkConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        // ✅ VALIDAR PRIMERO
        if (network == null || network.ssid == null) {
            showToast("❌ Red inválida");
            return;
        }

        // ✅ CREAR REFERENCIA FINAL
        final WiFiValidationModule.ValidatedNetwork finalNetwork = network;

        new MaterialAlertDialogBuilder(this)
                .setTitle("📤 Enviar Red al Medidor")
                .setMessage("La red \"" + network.ssid +
                        "\" fue validada correctamente.\n\n¿Deseas enviar estas credenciales al medidor ahora?")
                .setPositiveButton("✅ Enviar", (dialog, which) ->
                        sendWiFiCredentialsToDevice(finalNetwork))
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }

    private void sendWiFiCredentialsToDevice(WiFiValidationModule.ValidatedNetwork network) {
        try {
            // ✅ VALIDAR QUE NETWORK NO SEA NULL
            if (network == null || network.ssid == null || network.password == null) {
                System.out.println("❌ FASORES - Network inválido");
                showToast("❌ Datos de red inválidos");
                return;
            }

            // ✅ CREAR VARIABLES FINALES
            final String finalSsid = network.ssid;
            final String finalPassword = network.password;

            byte[] command = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(
                    finalSsid,
                    finalPassword
            );

            if (command == null || command.length == 0) {
                System.out.println("❌ FASORES - Comando WiFi vacío");
                showToast("❌ Error creando comando WiFi");
                return;
            }

            sendTcpCommandIndependent(command);

            System.out.println("📡 FASORES - Enviando WiFi al medidor:");
            System.out.println("   SSID: " + finalSsid);
            System.out.println("   Tamaño comando: " + command.length + " bytes");
            System.out.println("   Comando hex: " + OctoNetCommandEncoder.bytesToHexString(command));

            showToast("📡 Enviando credenciales de " + finalSsid + " al medidor");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error enviando WiFi: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error al enviar credenciales WiFi");
        }
    }

    // =========================================================================
    // ===== COMANDOS TCP =====================================================
    // =========================================================================

    private void sendTimeWriteCommand() {
        Calendar cal = Calendar.getInstance();
        byte[] command = OctoNetCommandEncoder.createDeviceTimeWriteCommand(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                cal.get(Calendar.SECOND),
                cal.get(Calendar.DAY_OF_WEEK)
        );
        sendTcpCommandIndependent(command);
    }

    private void sendDeviceIdReadCommand() {
        byte[] command = OctoNetCommandEncoder.createDeviceIdReadCommand();
        sendTcpCommandIndependent(command);
        System.out.println("📤 FASORES - DEVICE_ID READ enviado (0x00): " +
                OctoNetCommandEncoder.bytesToHexString(command));
    }

    private void sendWiFiReadCommand() {
        byte[] command = OctoNetCommandEncoder.createWiFiSettingsReadCommand();
        sendTcpCommandIndependent(command);
        System.out.println("📤 FASORES - WIFI_SETTINGS READ enviado (0xE3): " +
                OctoNetCommandEncoder.bytesToHexString(command));
    }

    // =========================================================================
    // ===== LISTENERS DE SPINNERS ============================================
    // =========================================================================

    private void setupSpinnerListeners() {
        spinnerCableado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents) {
                    meteringTypeConfig = 3;
                    tipoCableado = 3;
                    updateDiagram();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spinnerAmperes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    updateAmperesRange(position);
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse && validateConfigurationBeforeWrite()) {
                            writeDeviceConfigurationFromSpinners();
                        }
                    }, 500);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void writeDeviceConfigurationFromSpinners() {
        if (!isConnectedToDevice || isWaitingResponse) {
            return;
        }

        try {
            int periodValue = 0; // FIJO EN 1 MINUTO
            int sensorsValue = spinnerAmperes.getSelectedItemPosition();
            int meteringTypeValue = 3; // TRIFÁSICO
            boolean recordingValue = true;

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingValue, periodValue, sensorsValue, meteringTypeValue);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            showToast("📝 Escribiendo configuración...");

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                    showToast("⏰ Timeout escribiendo configuración");
                }
            }, 5000);

        } catch (Exception e) {
            showToast("❌ Error al escribir configuración: " + e.getMessage());
            isWaitingResponse = false;
        }
    }

    private boolean validateConfigurationBeforeWrite() {
        try {
            int sensors = spinnerAmperes.getSelectedItemPosition();
            return sensors >= 0 && sensors <= 5;
        } catch (Exception e) {
            return false;
        }
    }

    private void updateAmperesRange(int sensorIndex) {
        int[] amperesValues = {20, 50, 200, 400, 1000, 3000};
        if (sensorIndex >= 0 && sensorIndex < amperesValues.length) {
            rangoAmperes = amperesValues[sensorIndex];
        }
    }

    // =========================================================================
    // ===== BOTONES ==========================================================
    // =========================================================================

    private void setupButtons() {
        btnPlay.setOnClickListener(v -> {
            if (!isConnectedToDevice) {
                showToast("❌ No hay conexión");
                return;
            }
            if (!configurationSynced) {
                showToast("⏳ Sincronizando configuración...");
                return;
            }
            if (!autoReadEnabled) {
                startDataAcquisition();
            } else {
                stopDataAcquisition();
            }
        });

        imageDiagram.setOnClickListener(v -> showDiagramModal());

        btnBackToMenu.setOnClickListener(v -> {
            stopDataAcquisition();
            disconnectFromDevice();
            finish();
        });

        btnDeviceId.setOnClickListener(v -> showDeviceIdModal());
        btnConfigWifi.setOnClickListener(v -> showNetworkScanForValidation());
    }

    // =========================================================================
    // ===== MODAL DEL DIAGRAMA ===============================================
    // =========================================================================

    private void createDiagramModal() {
        if (diagramModal != null) return;

        LinearLayout modalContent = new LinearLayout(this);
        modalContent.setOrientation(LinearLayout.VERTICAL);
        modalContent.setGravity(Gravity.CENTER);
        modalContent.setPadding(40, 40, 40, 40);

        TextView modalTitle = new TextView(this);
        modalTitle.setText("📐 Diagrama de Conexión");
        modalTitle.setTextColor(Color.WHITE);
        modalTitle.setTextSize(24f);
        modalTitle.setGravity(Gravity.CENTER);
        modalTitle.setTypeface(null, Typeface.BOLD);
        modalTitle.setPadding(0, 0, 0, 30);
        modalContent.addView(modalTitle);

        modalDiagramImage = new ImageView(this);
        modalDiagramImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        modalDiagramImage.setAdjustViewBounds(true);

        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        imageParams.weight = 1;
        imageParams.setMargins(20, 20, 20, 20);
        modalDiagramImage.setLayoutParams(imageParams);
        modalDiagramImage.setBackgroundColor(Color.WHITE);
        modalDiagramImage.setPadding(20, 20, 20, 20);
        modalDiagramImage.setBackground(createRoundedBackground());
        modalContent.addView(modalDiagramImage);

        Button closeButton = new Button(this);
        closeButton.setText("✕ Cerrar");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(18f);
        closeButton.setTypeface(null, Typeface.BOLD);
        closeButton.setBackgroundColor(Color.rgb(220, 50, 50));
        closeButton.setPadding(40, 20, 40, 20);
        closeButton.setOnClickListener(v -> hideDiagramModal());

        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        buttonParams.setMargins(0, 30, 0, 0);
        buttonParams.gravity = Gravity.CENTER;
        closeButton.setLayoutParams(buttonParams);
        modalContent.addView(closeButton);

        FrameLayout modalFrame = new FrameLayout(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                canvas.drawColor(Color.argb(200, 0, 0, 0));
                super.onDraw(canvas);
            }
        };

        modalFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        modalFrame.setClickable(true);
        modalFrame.setFocusable(true);
        modalFrame.setVisibility(View.GONE);
        modalFrame.addView(modalContent);
        modalFrame.setOnClickListener(v -> hideDiagramModal());
        modalContent.setOnClickListener(v -> {
        });

        diagramModal = modalFrame;
    }

    private Drawable createRoundedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(15f);
        drawable.setStroke(3, Color.rgb(200, 200, 200));
        return drawable;
    }

    private void showDiagramModal() {
        if (isModalVisible) return;
        createDiagramModal();
        updateModalDiagram();

        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        rootView.addView(diagramModal);

        diagramModal.setVisibility(View.VISIBLE);
        diagramModal.setAlpha(0f);
        diagramModal.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        isModalVisible = true;
    }

    private void hideDiagramModal() {
        if (!isModalVisible || diagramModal == null) return;

        diagramModal.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    diagramModal.setVisibility(View.GONE);
                    ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                    rootView.removeView(diagramModal);
                    isModalVisible = false;
                })
                .start();
    }

    private void updateModalDiagram() {
        if (modalDiagramImage == null) return;
        modalDiagramImage.setImageResource(R.drawable.diagram_3p4w_n);
    }

    // =========================================================================
    // ===== AUTO-LECTURA =====================================================
    // =========================================================================

    private void setupAutoReadTask() {
        autoReadTask = new Runnable() {
            @Override
            public void run() {
                if (autoReadEnabled && isConnectedToDevice && configurationSynced) {
                    requestCurrentData();
                    autoReadHandler.postDelayed(this, AUTO_READ_INTERVAL);
                }
            }
        };
        updateDiagram();
    }

    // =========================================================================
    // ===== CONEXIÓN TCP =====================================================
    // =========================================================================

    private void connectToDeviceIndependent() {
        if (isConnectedToDevice) {
            System.out.println("⚠️ FASORES - Ya hay una conexión activa");
            return;
        }

        String ip = deviceIp;
        int port = devicePort;

        System.out.println("🔄 FASORES - Iniciando conexión a " + ip + ":" + port);
        showToast("🔄 Conectando a " + ip + ":" + port + "...");

        executor.execute(() -> {
            try {
                // ✅ PASO 1: Crear socket
                socket = new Socket();
                System.out.println("   Socket creado");

                // ✅ PASO 2: Conectar con timeout de 15 segundos
                socket.connect(new java.net.InetSocketAddress(ip, port), 15000);
                System.out.println("   Socket conectado");

                // ✅ PASO 3: Configurar socket (IGUAL A WiFiSetupActivity)
                socket.setSoTimeout(30000); // 30 segundos timeout de lectura
                socket.setReceiveBufferSize(8192);
                socket.setSendBufferSize(4096);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                System.out.println("   Socket configurado");

                // ✅ PASO 4: Obtener streams
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();
                System.out.println("   Streams obtenidos");

                // ✅ PASO 5: Marcar como conectado
                isConnectedToDevice = true;
                System.out.println("✅ FASORES - Conexión establecida exitosamente");

                // ✅ PASO 6: Actualizar UI
                handler.post(() -> {
                    showToast("✅ Conectado a " + ip + ":" + port);
                    setControlsEnabled(false); // Deshabilitado hasta sincronizar
                    configurationSynced = false;
                });

                // ✅ PASO 7: Iniciar thread de recepción
                System.out.println("🔄 FASORES - Iniciando thread de recepción...");
                startIndependentReceiveThreadImproved();

                // ✅ PASO 8: Esperar 500ms y hacer setup
                Thread.sleep(500);
                handler.post(() -> performAutomaticSetup());

            } catch (java.net.SocketTimeoutException e) {
                System.out.println("⏰ FASORES - Timeout al conectar: " + e.getMessage());
                handler.post(() -> {
                    showToast("⏰ Timeout de conexión");
                    isConnectedToDevice = false;
                });
            } catch (java.net.ConnectException e) {
                System.out.println("❌ FASORES - Error de conexión: " + e.getMessage());
                handler.post(() -> {
                    showToast("❌ No se pudo conectar al dispositivo");
                    isConnectedToDevice = false;
                });
            } catch (IOException e) {
                System.out.println("❌ FASORES - Error I/O: " + e.getMessage());
                handler.post(() -> {
                    showToast("❌ Error de conexión: " + e.getMessage());
                    isConnectedToDevice = false;
                });
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error inesperado: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    showToast("❌ Error: " + e.getMessage());
                    isConnectedToDevice = false;
                });
            }
        });
    }

    // ✅ AGREGAR ESTE MÉTODO NUEVO (IGUAL A WiFiSetupActivity):
    private void closeSocketSafely() {
        executor.execute(() -> {
            try {
                System.out.println("🔌 FASORES - Cerrando socket...");

                if (outputStream != null) {
                    try {
                        outputStream.close();
                        System.out.println("   OutputStream cerrado");
                    } catch (IOException e) {
                        System.out.println("   Error cerrando OutputStream: " + e.getMessage());
                    }
                    outputStream = null;
                }

                if (inputStream != null) {
                    try {
                        inputStream.close();
                        System.out.println("   InputStream cerrado");
                    } catch (IOException e) {
                        System.out.println("   Error cerrando InputStream: " + e.getMessage());
                    }
                    inputStream = null;
                }

                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                        System.out.println("   Socket cerrado");
                    } catch (IOException e) {
                        System.out.println("   Error cerrando Socket: " + e.getMessage());
                    }
                    socket = null;
                }

                System.out.println("✅ FASORES - Socket cerrado correctamente");

            } catch (Exception e) {
                System.out.println("❌ FASORES - Error cerrando socket: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // ✅ REEMPLAZAR startIndependentReceiveThreadImproved() COMPLETO:
    private void startIndependentReceiveThreadImproved() {
        executor.execute(() -> {
            byte[] buffer = new byte[2048];

            System.out.println("🔄 FASORES - Thread de recepción iniciado");

            try {
                while (isConnectedToDevice && !Thread.currentThread().isInterrupted()) {
                    try {
                        // ✅ IGUAL QUE WiFiSetupActivity - SIMPLE Y DIRECTO
                        int bytesRead = socket.getInputStream().read(buffer);

                        if (bytesRead > 0) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);

                            System.out.println("📨 FASORES - Recibidos " + bytesRead + " bytes");
                            System.out.println("📊 FASORES - Hex: " + OctoNetCommandEncoder.bytesToHexString(data));

                            // ✅ PROCESAR EN HANDLER (IGUAL QUE WiFiSetupActivity)
                            handler.post(() -> processReceivedDataIndependent(data));

                        } else if (bytesRead == -1) {
                            System.out.println("🔌 FASORES - Conexión cerrada por servidor");
                            break;
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        // Timeout esperado, continuar
                        continue;
                    }
                }
            } catch (IOException e) {
                if (isConnectedToDevice) {
                    System.out.println("❌ FASORES - IOException: " + e.getMessage());
                    handler.post(() -> {
                        showToast("❌ Error en conexión");
                        disconnectFromDevice();
                    });
                }
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error general: " + e.getMessage());
                e.printStackTrace();
            }

            System.out.println("🔚 FASORES - Thread de recepción terminado");

            // ✅ CERRAR SOCKET Y ACTUALIZAR UI
            handler.post(() -> {
                if (isConnectedToDevice) {
                    isConnectedToDevice = false;
                    configurationSynced = false;
                    setControlsEnabled(false);
                    showToast("🔌 Conexión perdida");
                    closeSocketSafely();
                }
            });
        });
    }

    private void disconnectFromDevice() {
        isConnectedToDevice = false;
        configurationSynced = false;
        stopDataAcquisition();
        executor.execute(() -> disconnectFromDeviceInternal());
        handler.post(() -> showToast("🔌 Desconectado"));
    }

    private void disconnectFromDeviceInternal() {
        try {
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                    // Ignorar
                } finally {
                    outputStream = null;
                }
            }

            if (socket != null) {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    // Ignorar
                } finally {
                    socket = null;
                }
            }
        } catch (Exception e) {
            // Ignorar
        }
    }

    // =========================================================================
    // ===== COMUNICACIÓN TCP =================================================
    // =========================================================================

    private void readDeviceConfigurationIndependent() {
        if (!isConnectedToDevice) return;

        if (isWaitingResponse) {
            isWaitingResponse = false;
        }

        try {
            byte[] command = OctoNetCommandEncoder.createNodeSettingsReadCommand();
            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            showToast("📖 Leyendo configuración...");

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                    showToast("⚠️ Sin respuesta config - continuando...");
                }
            }, 5000);

        } catch (Exception e) {
            isWaitingResponse = false;
            configurationSynced = true;
            setControlsEnabled(true);
        }
    }

    private void requestCurrentData() {
        if (!isConnectedToDevice) {
            return;
        }

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            connectToDeviceIndependent();
            return;
        }

        if (isWaitingResponse) {
            isWaitingResponse = false;
        }

        try {
            byte[] command = OctoNetCommandEncoder.createNodeCurrentReadCommand();

            if (!OctoNetCommandEncoder.verifyChecksum(command)) {
                return;
            }

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                }
            }, 3000);

        } catch (Exception e) {
            isWaitingResponse = false;
        }
    }

    private void sendTcpCommandIndependent(byte[] command) {
        if (!isConnectedToDevice || outputStream == null) {
            System.out.println("❌ FASORES - No se puede enviar comando: sin conexión");
            return;
        }

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            System.out.println("❌ FASORES - Socket no disponible");
            isConnectedToDevice = false;
            return;
        }

        // Identificar el tipo de comando
        String commandName = "DESCONOCIDO";
        if (command.length >= 3) {
            int cmd = command[2] & 0xFF;
            switch (cmd) {
                case 0x00:
                    commandName = "DEVICE_ID_READ";
                    break;
                case 0x02:
                    commandName = "DEVICE_TIME_WRITE";
                    break;
                case 0x20:
                    commandName = "NODE_SETTINGS";
                    break;
                case 0x21:
                    commandName = "NODE_CURRENT_READ";
                    break;
                case 0xE3:
                    commandName = "WIFI_SETTINGS";
                    break;
            }
        }

        System.out.println("📤 FASORES - Enviando comando: " + commandName);
        System.out.println("   Hex: " + OctoNetCommandEncoder.bytesToHexString(command));

        final byte[] finalCommand = command;
        final String finalCommandName = commandName;

        executor.execute(() -> {
            try {
                if (outputStream != null) {
                    outputStream.write(finalCommand);
                    outputStream.flush();
                    System.out.println("✅ FASORES - Comando " + finalCommandName + " enviado exitosamente");
                }
            } catch (IOException e) {
                System.out.println("❌ FASORES - Error enviando comando " + finalCommandName + ": " + e.getMessage());

                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Error de envío - reconectando...");

                    handler.postDelayed(() -> connectToDeviceIndependent(), 1000);
                });
            }
        });
    }

    // =========================================================================
    // ===== PROCESAMIENTO DE RESPUESTAS ======================================
    // =========================================================================

    private void processReceivedDataIndependent(byte[] data) {
        try {
            // ✅ IMPORTANTE: Marcar que YA NO estamos esperando respuesta
            isWaitingResponse = false;

            if (data == null || data.length < 4) {
                System.out.println("❌ FASORES - Datos insuficientes: " +
                        (data != null ? data.length : "null") + " bytes");
                return;
            }

            String hexData = OctoNetCommandEncoder.bytesToHexString(data);
            System.out.println("🔍 FASORES - Procesando respuesta: " + data.length + " bytes");
            System.out.println("   Hex: " + hexData);

            // ✅ VALIDAR ESTRUCTURA
            if (!OctoNetCommandEncoder.validateCommandStructure(data)) {
                System.out.println("❌ FASORES - Estructura inválida");
                return;
            }

            // ✅ VALIDAR CHECKSUM
            if (!OctoNetCommandEncoder.verifyChecksum(data)) {
                System.out.println("❌ FASORES - Checksum incorrecto");
                return;
            }

            int responseType = data[1] & 0xFF;
            int command = data[2] & 0xFF;
            int dataSize = data[3] & 0xFF;

            System.out.println("📋 FASORES - Respuesta válida:");
            System.out.println("   Type: 0x" + Integer.toHexString(responseType));
            System.out.println("   Command: 0x" + Integer.toHexString(command));
            System.out.println("   Size: " + dataSize);

            // ✅ MANEJAR ERROR DEL DISPOSITIVO
            if (responseType == 0x45) { // ERROR
                System.out.println("❌ FASORES - Error del dispositivo (0x45)");
                showToast("❌ Error del dispositivo");
                return;
            }

            // ✅ PROCESAR CONFIRMACIÓN
            if (responseType == 0x43) { // CONFIRMATION
                try {
                    switch (command) {
                        case 0x00: // DEVICE_ID
                            System.out.println("📱 FASORES - Procesando DEVICE_ID");
                            processDeviceIdResponse(data);
                            break;

                        case 0x02: // DEVICE_TIME
                            System.out.println("🕐 FASORES - DEVICE_TIME confirmado");
                            showToast("✅ Hora sincronizada");
                            break;

                        case 0x20: // NODE_SETTINGS
                            System.out.println("🔧 FASORES - Procesando NODE_SETTINGS");
                            if (dataSize > 0) {
                                // Respuesta READ con datos
                                processConfigurationResponseIndependent(data);
                            } else {
                                // Confirmación WRITE sin datos
                                showToast("✅ Configuración aplicada");
                                // Leer configuración después de escribir
                                handler.postDelayed(() -> {
                                    if (!isWaitingResponse) {
                                        readDeviceConfigurationIndependent();
                                    }
                                }, 1000);
                            }
                            break;

                        case 0x21: // NODE_CURRENT
                            System.out.println("📊 FASORES - Procesando NODE_CURRENT");
                            if (dataSize > 0) {
                                processCurrentDataResponseIndependent(data);
                            } else {
                                System.out.println("⚠️ FASORES - NODE_CURRENT sin datos");
                                showToast("❌ Sin datos disponibles");
                            }
                            break;

                        case 0xE3: // WIFI_SETTINGS
                            System.out.println("📡 FASORES - Procesando WIFI_SETTINGS");
                            if (dataSize > 0) {
                                // Respuesta READ
                                processWiFiSettingsResponse(data);
                            } else {
                                // Confirmación WRITE
                                showToast("✅ Credenciales WiFi enviadas");
                            }
                            break;

                        default:
                            System.out.println("⚠️ FASORES - Comando no reconocido: 0x" +
                                    Integer.toHexString(command));
                            break;
                    }
                } catch (Exception e) {
                    System.out.println("❌ FASORES - Error procesando comando: " + e.getMessage());
                    e.printStackTrace();
                    showToast("❌ Error procesando respuesta");
                }
            } else {
                System.out.println("⚠️ FASORES - Tipo de respuesta desconocido: 0x" +
                        Integer.toHexString(responseType));
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error crítico: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando datos");
        } finally {
            // ✅ ASEGURAR QUE isWaitingResponse SIEMPRE SE RESETEA
            isWaitingResponse = false;
        }
    }

    // ✅ PROCESAMIENTO DEVICE ID CORREGIDO
    private void processDeviceIdResponse(byte[] response) {
        try {
            System.out.println("🔍 FASORES - Procesando respuesta Device ID...");
            System.out.println("📊 FASORES - Respuesta completa hex: " +
                    OctoNetCommandEncoder.bytesToHexString(response));

            byte[] deviceData = OctoNetCommandEncoder.extractCommandData(response);

            if (deviceData == null || deviceData.length == 0) {
                System.out.println("❌ FASORES - Datos Device ID vacíos");
                showToast("❌ Sin datos de Device ID");
                return;
            }

            System.out.println("📊 FASORES - Datos extraídos: " + deviceData.length + " bytes");
            System.out.println("📊 FASORES - Datos hex: " +
                    OctoNetCommandEncoder.bytesToHexString(deviceData));

            lastReadDeviceIdInfo = new DeviceIdInfo();

            // ✅ MÉTODO 1: Intentar formato string completo
            String deviceInfoString = new String(deviceData, "UTF-8").trim();
            System.out.println("📄 FASORES - Datos como string: '" + deviceInfoString + "'");

            // Formato concatenado típico: 140423000046090224112325LVTXER4WW4B0D028
            if (deviceInfoString.length() >= 30 && !deviceInfoString.contains("\n")) {
                System.out.println("🔍 FASORES - Formato concatenado detectado (" +
                        deviceInfoString.length() + " chars)");

                try {
                    // Serial: primeros 12 caracteres
                    if (deviceInfoString.length() >= 12) {
                        lastReadDeviceIdInfo.serial = deviceInfoString.substring(0, 12);
                        System.out.println("   ✓ Serial: " + lastReadDeviceIdInfo.serial);
                    }

                    // Fecha: caracteres 12-18 (DDMMYY)
                    if (deviceInfoString.length() >= 18) {
                        String dateStr = deviceInfoString.substring(12, 18);
                        lastReadDeviceIdInfo.facDate = dateStr.substring(0, 2) + "/" +
                                dateStr.substring(2, 4) + "/20" +
                                dateStr.substring(4, 6);
                        System.out.println("   ✓ Fecha Fab: " + lastReadDeviceIdInfo.facDate);
                    }

                    // Hora: caracteres 18-24 (HHMMSS)
                    if (deviceInfoString.length() >= 24) {
                        String timeStr = deviceInfoString.substring(18, 24);
                        lastReadDeviceIdInfo.facHour = timeStr.substring(0, 2) + ":" +
                                timeStr.substring(2, 4) + ":" +
                                timeStr.substring(4, 6);
                        System.out.println("   ✓ Hora Fab: " + lastReadDeviceIdInfo.facHour);
                    }

                    // Código Act: caracteres 24-28
                    if (deviceInfoString.length() >= 28) {
                        lastReadDeviceIdInfo.actCode = deviceInfoString.substring(24, 28);
                        System.out.println("   ✓ Código Act: " + lastReadDeviceIdInfo.actCode);
                    }

                    // HW Version: caracteres 28-34
                    if (deviceInfoString.length() >= 34) {
                        lastReadDeviceIdInfo.hwVersion = deviceInfoString.substring(28, 34);
                        System.out.println("   ✓ HW Version: " + lastReadDeviceIdInfo.hwVersion);
                    }

                    // FW Version: resto de caracteres
                    if (deviceInfoString.length() > 34) {
                        lastReadDeviceIdInfo.fwVersion = deviceInfoString.substring(34);
                        System.out.println("   ✓ FW Version: " + lastReadDeviceIdInfo.fwVersion);
                    }

                    System.out.println("✅ FASORES - Device ID parseado (formato concatenado)");
                    showToast("✅ Información del dispositivo recibida");
                    return;

                } catch (Exception parseError) {
                    System.out.println("⚠️ FASORES - Error parseo concatenado: " +
                            parseError.getMessage());
                    parseError.printStackTrace();
                }
            }

            // ✅ MÉTODO 2: Formato binario (32+ bytes)
            if (deviceData.length >= 32) {
                System.out.println("🔍 FASORES - Intentando formato binario (" +
                        deviceData.length + " bytes)");

                try {
                    // Serial (10 bytes) - bytes 0-9
                    byte[] serialBytes = new byte[10];
                    System.arraycopy(deviceData, 0, serialBytes, 0, 10);
                    lastReadDeviceIdInfo.serial = new String(deviceData, "UTF-8").trim();
                    System.out.println("   ✓ Serial: " + lastReadDeviceIdInfo.serial);

                    // Fecha y hora - bytes 10-15
                    if (deviceData.length >= 16) {
                        int year = 2000 + (deviceData[10] & 0xFF);
                        int month = deviceData[11] & 0xFF;
                        int day = deviceData[12] & 0xFF;
                        lastReadDeviceIdInfo.facDate = String.format("%02d/%02d/%04d",
                                day, month, year);
                        System.out.println("   ✓ Fecha Fab: " + lastReadDeviceIdInfo.facDate);

                        int hour = deviceData[13] & 0xFF;
                        int minute = deviceData[14] & 0xFF;
                        int second = deviceData[15] & 0xFF;
                        lastReadDeviceIdInfo.facHour = String.format("%02d:%02d:%02d",
                                hour, minute, second);
                        System.out.println("   ✓ Hora Fab: " + lastReadDeviceIdInfo.facHour);
                    }

                    // Código activación (10 bytes) - bytes 16-25
                    if (deviceData.length >= 26) {
                        byte[] actCodeBytes = new byte[10];
                        System.arraycopy(deviceData, 16, actCodeBytes, 0, 10);
                        lastReadDeviceIdInfo.actCode = new String(deviceData, "UTF-8").trim();
                        System.out.println("   ✓ Código Act: " + lastReadDeviceIdInfo.actCode);
                    }

                    // Versiones HW y FW - bytes 26-31
                    if (deviceData.length >= 32) {
                        byte[] hwBytes = new byte[3];
                        System.arraycopy(deviceData, 26, hwBytes, 0, 3);
                        lastReadDeviceIdInfo.hwVersion = new String(deviceData, "UTF-8").trim();
                        System.out.println("   ✓ HW Version: " + lastReadDeviceIdInfo.hwVersion);

                        byte[] fwBytes = new byte[3];
                        System.arraycopy(deviceData, 29, fwBytes, 0, 3);
                        lastReadDeviceIdInfo.fwVersion = new String(deviceData, "UTF-8").trim();
                        System.out.println("   ✓ FW Version: " + lastReadDeviceIdInfo.fwVersion);
                    }

                    System.out.println("✅ FASORES - Device ID parseado (formato binario)");
                    showToast("✅ Información del dispositivo recibida");
                    return;

                } catch (Exception binaryError) {
                    System.out.println("⚠️ FASORES - Error parseo binario: " +
                            binaryError.getMessage());
                    binaryError.printStackTrace();
                }
            }

            // ✅ MÉTODO 3: Fallback - usar string completo como serial
            System.out.println("⚠️ FASORES - Usando fallback");
            lastReadDeviceIdInfo.serial = deviceInfoString.isEmpty() ? "DESCONOCIDO" : deviceInfoString;
            lastReadDeviceIdInfo.facDate = "N/A";
            lastReadDeviceIdInfo.facHour = "N/A";
            lastReadDeviceIdInfo.actCode = "N/A";
            lastReadDeviceIdInfo.hwVersion = "N/A";
            lastReadDeviceIdInfo.fwVersion = "N/A";

            System.out.println("⚠️ FASORES - Información parcial guardada");
            showToast("⚠️ Información parcial recibida");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error crítico Device ID: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando Device ID");

            lastReadDeviceIdInfo = new DeviceIdInfo();
            lastReadDeviceIdInfo.serial = "ERROR";
            lastReadDeviceIdInfo.facDate = "N/A";
            lastReadDeviceIdInfo.facHour = "N/A";
            lastReadDeviceIdInfo.actCode = "N/A";
            lastReadDeviceIdInfo.hwVersion = "N/A";
            lastReadDeviceIdInfo.fwVersion = "N/A";
        }
    }

    private void processWiFiSettingsResponse(byte[] response) {
        try {
            System.out.println("🔍 FASORES - Procesando WiFi Settings...");
            System.out.println("📊 FASORES - Respuesta hex: " +
                    OctoNetCommandEncoder.bytesToHexString(response));

            byte[] wifiData = OctoNetCommandEncoder.extractCommandData(response);

            System.out.println("📊 FASORES - WiFi datos: " +
                    (wifiData != null ? wifiData.length : 0) + " bytes");

            if (wifiData == null || wifiData.length < 64) {
                System.out.println("❌ FASORES - Datos WiFi insuficientes");
                showToast("❌ Datos WiFi incompletos");
                lastReadWifiSettings = new OctoNetCommandEncoder.WiFiSettings();
                return;
            }

            lastReadWifiSettings = OctoNetCommandEncoder.processWiFiSettingsResponse(wifiData);

            if (lastReadWifiSettings != null) {
                System.out.println("✅ FASORES - WiFi guardado:");
                System.out.println("   SSID: '" + lastReadWifiSettings.ssid + "'");
                System.out.println("   IP: '" + lastReadWifiSettings.ip + "'");
                System.out.println("   MAC: '" + lastReadWifiSettings.mac + "'");

                if (lastReadWifiSettings.ssid.isEmpty()) {
                    showToast("📡 WiFi: Sin configurar");
                } else {
                    showToast("📡 WiFi: " + lastReadWifiSettings.ssid);
                }
            } else {
                System.out.println("❌ FASORES - Error procesando WiFi");
                showToast("❌ Error procesando WiFi");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Excepción WiFi: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando WiFi");
            lastReadWifiSettings = new OctoNetCommandEncoder.WiFiSettings();
        }
    }

    // =========================================================================
    // ===== PROCESAMIENTO DE CONFIGURACIÓN ===================================
    // =========================================================================

    private void processConfigurationResponseIndependent(byte[] response) {
        try {
            System.out.println("🔍 FASORES - Procesando NODE_SETTINGS...");
            System.out.println("📊 FASORES - Respuesta hex: " +
                    OctoNetCommandEncoder.bytesToHexString(response));

            byte[] configData = OctoNetCommandEncoder.extractCommandData(response);

            if (configData == null || configData.length < 4) {
                System.out.println("❌ FASORES - Datos de configuración insuficientes");
                showToast("❌ Datos de configuración incompletos");
                configurationSynced = true;
                setControlsEnabled(true);
                setSpinnersEnabled(true);
                return;
            }

            System.out.println("📊 FASORES - Config datos: " + configData.length + " bytes");
            System.out.println("   Byte 0 (recording): 0x" + Integer.toHexString(configData[0] & 0xFF));
            System.out.println("   Byte 1 (period): 0x" + Integer.toHexString(configData[1] & 0xFF));
            System.out.println("   Byte 2 (sensors): 0x" + Integer.toHexString(configData[2] & 0xFF));
            System.out.println("   Byte 3 (metering): 0x" + Integer.toHexString(configData[3] & 0xFF));

            // Extraer valores
            recordingConfig = (configData[0] & 0xFF) == 1;
            periodConfig = configData[1] & 0xFF;
            sensorsConfig = configData[2] & 0xFF;
            meteringTypeConfig = configData[3] & 0xFF;

            System.out.println("✅ FASORES - Configuración leída:");
            System.out.println("   Recording: " + recordingConfig);
            System.out.println("   Period: " + periodConfig);
            System.out.println("   Sensors: " + sensorsConfig);
            System.out.println("   Metering Type: " + meteringTypeConfig);

            // Actualizar UI
            skipSpinnerEvents = true;

            if (sensorsConfig >= 0 && sensorsConfig <= 5) {
                spinnerAmperes.setSelection(sensorsConfig);
                updateAmperesRange(sensorsConfig);
                System.out.println("   ✓ Spinner Amperes actualizado a posición: " + sensorsConfig);
            }

            // Tipo de cableado siempre trifásico
            meteringTypeConfig = 3;
            tipoCableado = 3;
            spinnerCableado.setSelection(0);

            skipSpinnerEvents = false;

            configurationSynced = true;
            setControlsEnabled(true);
            setSpinnersEnabled(true);

            updateDiagram();
            showToast("✅ Configuración sincronizada");

            System.out.println("✅ FASORES - Sincronización completa");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando configuración: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando configuración");

            configurationSynced = true;
            setControlsEnabled(true);
            setSpinnersEnabled(true);
        }
    }

    // =========================================================================
    // ===== PROCESAMIENTO DE DATOS ACTUALES ==================================
    // =========================================================================

    private void processCurrentDataResponseIndependent(byte[] response) {
        try {
            byte[] currentData = OctoNetCommandEncoder.extractCommandData(response);

            if (currentData == null || currentData.length < 48) {
                System.out.println("❌ FASORES - Datos NODE_CURRENT insuficientes: " +
                        (currentData != null ? currentData.length : "null") + " bytes");
                return;
            }

            System.out.println("📊 FASORES - Procesando NODE_CURRENT: " + currentData.length + " bytes");

            // Extraer datos de las 3 fases (16 bytes por fase)
            float[] newVoltajes = new float[3];
            float[] newCorrientes = new float[3];
            float[] newPotencias = new float[3];
            float[] newFrecuencias = new float[3];

            for (int fase = 0; fase < 3; fase++) {
                int offset = fase * 16;

                // Voltaje RMS (4 bytes float, little-endian)
                newVoltajes[fase] = OctoNetCommandEncoder.bytesToFloat(
                        currentData[offset],
                        currentData[offset + 1],
                        currentData[offset + 2],
                        currentData[offset + 3]
                );

                // Corriente RMS (4 bytes float, little-endian)
                newCorrientes[fase] = OctoNetCommandEncoder.bytesToFloat(
                        currentData[offset + 4],
                        currentData[offset + 5],
                        currentData[offset + 6],
                        currentData[offset + 7]
                );

                // Potencia Activa (4 bytes float, little-endian)
                newPotencias[fase] = OctoNetCommandEncoder.bytesToFloat(
                        currentData[offset + 8],
                        currentData[offset + 9],
                        currentData[offset + 10],
                        currentData[offset + 11]
                );

                // Frecuencia (4 bytes float, little-endian)
                newFrecuencias[fase] = OctoNetCommandEncoder.bytesToFloat(
                        currentData[offset + 12],
                        currentData[offset + 13],
                        currentData[offset + 14],
                        currentData[offset + 15]
                );
            }

            // Validar datos
            boolean dataValid = true;
            for (int i = 0; i < 3; i++) {
                if (Float.isNaN(newVoltajes[i]) || Float.isInfinite(newVoltajes[i]) ||
                        Float.isNaN(newCorrientes[i]) || Float.isInfinite(newCorrientes[i]) ||
                        Float.isNaN(newPotencias[i]) || Float.isInfinite(newPotencias[i]) ||
                        Float.isNaN(newFrecuencias[i]) || Float.isInfinite(newFrecuencias[i])) {
                    dataValid = false;
                    break;
                }
            }

            if (!dataValid) {
                System.out.println("❌ FASORES - Datos inválidos (NaN o Infinito)");
                return;
            }

            // Actualizar arrays
            System.arraycopy(newVoltajes, 0, voltajes, 0, 3);
            System.arraycopy(newCorrientes, 0, corrientes, 0, 3);
            System.arraycopy(newPotencias, 0, potencias, 0, 3);
            System.arraycopy(newFrecuencias, 0, frecuencias, 0, 3);

            contadorMuestras++;

            System.out.println("✅ FASORES - Datos actualizados (#" + contadorMuestras + "):");
            for (int i = 0; i < 3; i++) {
                System.out.println(String.format("   Fase %d: V=%.2f V, I=%.3f A, P=%.2f W, Hz=%.2f Hz",
                        i + 1, voltajes[i], corrientes[i], potencias[i], frecuencias[i]));
            }

            // Actualizar UI
            handler.post(() -> {
                updateTextViews();
                updateFasores();
            });

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando NODE_CURRENT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // =========================================================================
    // ===== ACTUALIZACIÓN DE UI ==============================================
    // =========================================================================

    private void updateTextViews() {
        try {
            // Voltajes
            if (tvV1 != null) tvV1.setText(String.format("%.2f V", voltajes[0]));
            if (tvV2 != null) tvV2.setText(String.format("%.2f V", voltajes[1]));
            if (tvV3 != null) tvV3.setText(String.format("%.2f V", voltajes[2]));

            // Corrientes
            if (tvA1 != null) tvA1.setText(String.format("%.3f A", corrientes[0]));
            if (tvA2 != null) tvA2.setText(String.format("%.3f A", corrientes[1]));
            if (tvA3 != null) tvA3.setText(String.format("%.3f A", corrientes[2]));

            // Potencias
            if (tvW1 != null) tvW1.setText(String.format("%.2f W", potencias[0]));
            if (tvW2 != null) tvW2.setText(String.format("%.2f W", potencias[1]));
            if (tvW3 != null) tvW3.setText(String.format("%.2f W", potencias[2]));

            // Frecuencias
            if (tvHz1 != null) tvHz1.setText(String.format("%.2f Hz", frecuencias[0]));
            if (tvHz2 != null) tvHz2.setText(String.format("%.2f Hz", frecuencias[1]));
            if (tvHz3 != null) tvHz3.setText(String.format("%.2f Hz", frecuencias[2]));

            // Factor de potencia (calculado)
            for (int i = 0; i < 3; i++) {
                float pf = 0.0f;
                if (voltajes[i] > 0 && corrientes[i] > 0) {
                    float potenciaAparente = voltajes[i] * corrientes[i];
                    if (potenciaAparente > 0) {
                        pf = potencias[i] / potenciaAparente;
                        pf = Math.max(-1.0f, Math.min(1.0f, pf)); // Limitar entre -1 y 1
                    }
                }

                String pfText = String.format("%.3f", pf);
                if (i == 0 && tvpF1 != null) tvpF1.setText(pfText);
                if (i == 1 && tvpF2 != null) tvpF2.setText(pfText);
                if (i == 2 && tvpF3 != null) tvpF3.setText(pfText);
            }

            // Actualizar etiquetas de canal
            updateChannelLabels();

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error actualizando TextViews: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateChannelLabels() {
        try {
            String[] labels = {"CH1", "CH2", "CH3"};

            // Para Carga Trifásica (3P4W-N)
            labels[0] = "L1-N";
            labels[1] = "L2-N";
            labels[2] = "L3-N";

            if (tvCH1 != null) tvCH1.setText(labels[0]);
            if (tvCH2 != null) tvCH2.setText(labels[1]);
            if (tvCH3 != null) tvCH3.setText(labels[2]);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error actualizando etiquetas: " + e.getMessage());
        }
    }

    private void updateFasores() {
        if (fasorVoltaje == null || fasorCorriente == null) return;

        try {
            // Actualizar fasores de voltaje
            fasorVoltaje.setMagnitudes(voltajes[0], voltajes[1], voltajes[2]);
            fasorVoltaje.setAngles(0.0f, 120.0f, 240.0f); // Ángulos teóricos trifásicos

            // Actualizar fasores de corriente
            fasorCorriente.setMagnitudes(corrientes[0], corrientes[1], corrientes[2]);
            fasorCorriente.setAngles(0.0f, 120.0f, 240.0f); // Ángulos teóricos trifásicos

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error actualizando fasores: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void initializeDisplayValues() {
        if (tvV1 != null) tvV1.setText("0.00 V");
        if (tvV2 != null) tvV2.setText("0.00 V");
        if (tvV3 != null) tvV3.setText("0.00 V");

        if (tvA1 != null) tvA1.setText("0.000 A");
        if (tvA2 != null) tvA2.setText("0.000 A");
        if (tvA3 != null) tvA3.setText("0.000 A");

        if (tvW1 != null) tvW1.setText("0.00 W");
        if (tvW2 != null) tvW2.setText("0.00 W");
        if (tvW3 != null) tvW3.setText("0.00 W");

        if (tvHz1 != null) tvHz1.setText("0.00 Hz");
        if (tvHz2 != null) tvHz2.setText("0.00 Hz");
        if (tvHz3 != null) tvHz3.setText("0.00 Hz");

        if (tvpF1 != null) tvpF1.setText("0.000");
        if (tvpF2 != null) tvpF2.setText("0.000");
        if (tvpF3 != null) tvpF3.setText("0.000");

        if (tvCH1 != null) tvCH1.setText("L1-N");
        if (tvCH2 != null) tvCH2.setText("L2-N");
        if (tvCH3 != null) tvCH3.setText("L3-N");
    }

    // =========================================================================
    // ===== CONTROL DE ADQUISICIÓN ===========================================
    // =========================================================================

    private void startDataAcquisition() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión con el dispositivo");
            return;
        }

        if (!configurationSynced) {
            showToast("⏳ Esperando sincronización de configuración...");
            return;
        }

        System.out.println("▶️ FASORES - Iniciando adquisición de datos");

        autoReadEnabled = true;
        tiempoInicio = System.currentTimeMillis();
        contadorMuestras = 0;

        btnPlay.setImageResource(R.drawable.ic_pause);
        showToast("▶️ Adquisición iniciada (cada 5s)");

        // Iniciar lectura automática
        autoReadHandler.post(autoReadTask);
    }

    private void stopDataAcquisition() {
        if (!autoReadEnabled) return;

        System.out.println("⏹️ FASORES - Deteniendo adquisición de datos");

        autoReadEnabled = false;
        autoReadHandler.removeCallbacks(autoReadTask);

        btnPlay.setImageResource(R.drawable.ic_play);

        long tiempoTotal = (System.currentTimeMillis() - tiempoInicio) / 1000;
        System.out.println("📊 FASORES - Estadísticas de adquisición:");
        System.out.println("   Tiempo total: " + tiempoTotal + " segundos");
        System.out.println("   Muestras: " + contadorMuestras);
        System.out.println("   Promedio: " + (contadorMuestras > 0 ? (tiempoTotal / contadorMuestras) : 0) + " s/muestra");

        showToast("⏹️ Adquisición detenida");
    }

    // =========================================================================
    // ===== DIAGRAMA =========================================================
    // =========================================================================

    private void updateDiagram() {
        if (imageDiagram == null) return;
        // Siempre mostrar diagrama 3P4W-N (Carga Trifásica)
        imageDiagram.setImageResource(R.drawable.diagram_3p4w_n);
    }

    // =========================================================================
    // ===== UTILIDADES =======================================================
    // =========================================================================

    private void setControlsEnabled(boolean enabled) {
        handler.post(() -> {
            if (btnPlay != null) btnPlay.setEnabled(enabled);
            if (btnDeviceId != null) btnDeviceId.setEnabled(enabled);
            if (btnConfigWifi != null) btnConfigWifi.setEnabled(enabled);
        });
    }

    private void setSpinnersEnabled(boolean enabled) {
        handler.post(() -> {
            if (spinnerCableado != null) spinnerCableado.setEnabled(false); // Siempre deshabilitado
            if (spinnerAmperes != null) spinnerAmperes.setEnabled(enabled);
        });
    }

    private void showToast(String message) {
        handler.post(() -> {
            try {
                Toast.makeText(FasoresActivity.this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error mostrando toast: " + e.getMessage());
            }
        });
    }

    // =========================================================================
    // ===== LIFECYCLE ========================================================
    // =========================================================================

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("⏸️ FASORES - Activity pausada");

        if (autoReadEnabled) {
            stopDataAcquisition();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("▶️ FASORES - Activity resumida");

        // Reconectar si es necesario
        if (!isConnectedToDevice) {
            handler.postDelayed(() -> connectToDeviceIndependent(), 500);
        }
    }



    private void stopCurrentDataUpdates() {
        try {
            // Detener el handler que actualiza datos periódicamente
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
                System.out.println("✅ FASORES - Handler limpiado");
            }

            // Si hay un Runnable de actualización continua, detenerlo
            if (updateCurrentDataRunnable != null) {
                handler.removeCallbacks(updateCurrentDataRunnable);
                System.out.println("✅ FASORES - Actualizaciones de datos detenidas");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error deteniendo actualizaciones: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        System.out.println("🔚 FASORES - onDestroy llamado");

        // ✅ DETENER ACTUALIZACIONES
        stopCurrentDataUpdates();

        // ✅ MARCAR COMO DESCONECTADO
        isConnectedToDevice = false;
        isWaitingResponse = false;
        configurationSynced = false;

        // ✅ CERRAR SOCKET
        closeSocketSafely();

        // ✅ CERRAR EXECUTOR
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            System.out.println("   Executor cerrado");
        }
    }
}


