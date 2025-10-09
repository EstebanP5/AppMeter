package de.kai_morich.simple_bluetooth_terminal;

// ===== IMPORTS COMPLETOS =====
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
    private OctoNetCommandEncoder.WiFiSettings lastReadWifiSettings;
    private DeviceIdInfo lastReadDeviceIdInfo;
    private boolean userRequestedWifiScan = false; // ✅ FLAG PARA SABER SI EL USUARIO SOLICITÓ EL ESCANEO

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

    // ===== ESTADO =====
    private long tiempoInicio;
    private int contadorMuestras = 0;
    private boolean isWaitingResponse = false;
    private boolean configurationSynced = false;
    private boolean skipSpinnerEvents = false;

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

        // ✅ CONECTAR DIRECTAMENTE SIN VALIDACIÓN DE RED
        handler.postDelayed(() -> connectToDeviceIndependent(), 500);
    }

    private void initializeViews() {
        // Spinners y controles
        spinnerCableado = findViewById(R.id.spinnerCableado);
        spinnerAmperes = findViewById(R.id.spinnerAmperes);
        imageDiagram = findViewById(R.id.imageDiagram);
        btnPlay = findViewById(R.id.btnPlay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        // ✅ NUEVOS BOTONES
        btnDeviceId = findViewById(R.id.btnDeviceId);
        btnConfigWifi = findViewById(R.id.btnConfigWifi);

        // ✅ FASORES CON MODO 3 EJES
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

    // ✅ SETUP SPINNERS SIN TIEMPO
    private void setupSpinners() {
        String[] cableadoOptions = {"3 = Carga Trifásica"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        String[] amperesOptions = {
                "0 = Shunt-20A", "1 = CT-50A", "2 = CT-200A",
                "3 = CT-400A", "4 = RoGo-1000A", "5 = RoGo-3000A"
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
    // ===== LÓGICA DE WIFI Y VALIDACIÓN ======================================
    // =========================================================================

    private void initializeWiFi() {
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                    // ✅ SOLO PROCESAR SI EL USUARIO SOLICITÓ EL ESCANEO
                    if (userRequestedWifiScan) {
                        userRequestedWifiScan = false; // ✅ RESETEAR FLAG
                        updateAvailableNetworks();
                        displayNetworkSelectionForValidation();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(wifiReceiver, filter);
    }

    // ✅ SECUENCIA AUTOMÁTICA DE SETUP OPTIMIZADA
    private void performAutomaticSetup() {
        if (!isConnectedToDevice) return;

        executor.execute(() -> {
            try {
                handler.post(() -> showToast("⚙️ Iniciando configuración automática..."));
                Thread.sleep(500);

                // 1. Sincronizar hora
                handler.post(() -> showToast("🕐 Sincronizando hora..."));
                sendTimeWriteCommand();
                Thread.sleep(1500);

                // ❌ QUITAR ESTO - NO PEDIR DEVICE ID EN SETUP
            /*
            handler.post(() -> showToast("📋 Obteniendo ID del dispositivo..."));
            boolean deviceIdReceived = false;
            for (int attempt = 0; attempt < 3 && !deviceIdReceived; attempt++) {
                ...
            }
            */

                // 2. Verificar WiFi
                handler.post(() -> showToast("📡 Verificando WiFi del medidor..."));
                sendWiFiReadCommand();
                Thread.sleep(1500);

                // 3. Leer configuración
                handler.post(() -> showToast("🔧 Leyendo configuración..."));
                readDeviceConfigurationIndependent();
                Thread.sleep(2000);

                handler.post(() -> {
                    showToast("🎉 Setup automático completado");
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                });

            } catch (Exception e) {
                handler.post(() -> showToast("❌ Error en setup automático: " + e.getMessage()));
            }
        });
    }

    // ✅ MODAL DE DEVICE ID MEJORADO
    private void showDeviceIdModal() {
        // ✅ VERIFICAR CONEXIÓN PRIMERO
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión con el medidor");
            return;
        }

        // ✅ SI YA HAY DATOS, MOSTRAR INMEDIATAMENTE
        if (lastReadDeviceIdInfo != null) {
            displayDeviceIdModal();
            return;
        }

        // ✅ SI NO HAY DATOS, SOLICITARLOS
        showToast("📋 Solicitando información del dispositivo...");
        sendDeviceIdReadCommand();

        // ✅ ESPERAR RESPUESTA CON MÚLTIPLES INTENTOS
        final int[] attempts = {0};
        final Handler checkHandler = new Handler(Looper.getMainLooper());

        Runnable checkDataRunnable = new Runnable() {
            @Override
            public void run() {
                attempts[0]++;

                if (lastReadDeviceIdInfo != null) {
                    // ✅ DATOS RECIBIDOS - MOSTRAR MODAL
                    displayDeviceIdModal();
                } else if (attempts[0] < 5) {
                    // ✅ REINTENTAR (máximo 5 intentos = 5 segundos)
                    checkHandler.postDelayed(this, 1000);
                    System.out.println("🔄 FASORES - Esperando Device ID... intento " + attempts[0]);
                } else {
                    // ✅ TIMEOUT - MOSTRAR ERROR
                    showToast("⏰ Timeout: No se pudo obtener la información del dispositivo");
                    System.out.println("❌ FASORES - Timeout esperando Device ID después de 5 intentos");
                }
            }
        };

        // Iniciar verificación después de 1 segundo
        checkHandler.postDelayed(checkDataRunnable, 1000);
    }

    // ✅ MÉTODO SEPARADO PARA MOSTRAR EL MODAL
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

    // ✅ ESCANEO DE REDES WIFI - SOLO CUANDO EL USUARIO LO SOLICITE
    private void showNetworkScanForValidation() {
        if (!isConnectedToDevice) {
            showToast("❌ Debes estar conectado al medidor para configurar el WiFi.");
            return;
        }

        // ✅ VERIFICAR RED ESP SOLO AQUÍ
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
                            userRequestedWifiScan = true; // ✅ MARCAR QUE EL USUARIO SOLICITÓ EL ESCANEO
                            wifiManager.startScan();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
        }

        showToast("🔍 Escaneando redes WiFi...");
        userRequestedWifiScan = true; // ✅ MARCAR QUE EL USUARIO SOLICITÓ EL ESCANEO
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

        new MaterialAlertDialogBuilder(this)
                .setTitle("🔑 Validar Credenciales")
                .setView(container)
                .setPositiveButton("Validar", (dialog, which) -> {
                    String password = editText.getText().toString();
                    if (password.isEmpty()) {
                        showToast("❌ La contraseña no puede estar vacía");
                        return;
                    }
                    wifiValidationModule.validateNetwork(ssid, password);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void showValidatedNetworkConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("📤 Enviar Red al Medidor")
                .setMessage("La red \"" + network.ssid + "\" fue validada correctamente.\n\n¿Deseas enviar estas credenciales al medidor ahora?")
                .setPositiveButton("✅ Enviar", (dialog, which) -> sendWiFiCredentialsToDevice(network))
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }

    private void sendWiFiCredentialsToDevice(WiFiValidationModule.ValidatedNetwork network) {
        try {
            byte[] command = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(network.ssid, network.password);
            sendTcpCommandIndependent(command);
            showToast("📡 Enviando credenciales de " + network.ssid + " al medidor.");
        } catch (Exception e) {
            showToast("❌ Error al crear el comando para enviar credenciales.");
        }
    }

    // ✅ COMANDOS DE CONFIGURACIÓN
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
    }

    private void sendWiFiReadCommand() {
        byte[] command = OctoNetCommandEncoder.createWiFiSettingsReadCommand();
        sendTcpCommandIndependent(command);
    }

    // =========================================================================
    // ===== LISTENERS DE SPINNERS ============================================
    // =========================================================================

    private void setupSpinnerListeners() {
        // ✅ CABLEADO FIJO EN TRIFÁSICO - NO CAMBIA
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

        // ✅ AMPERES - ESCRIBE AUTOMÁTICAMENTE
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

        // ✅ NUEVOS BOTONES
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
        if (isConnectedToDevice || executor == null) return;

        showToast("🔗 Estableciendo conexión...");
        executor.execute(() -> {
            try {
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(deviceIp, devicePort), 10000);
                outputStream = socket.getOutputStream();
                isConnectedToDevice = true;

                handler.post(() -> {
                    showToast("✅ Conectado al dispositivo");
                    performAutomaticSetup();
                });

                startIndependentReceiveThreadImproved();
            } catch (Exception e) {
                handler.post(() -> showToast("❌ Error de conexión: " + e.getMessage()));
            }
        });
    }

    private void startIndependentReceiveThreadImproved() {
        executor.execute(() -> {
            byte[] buffer = new byte[2048];

            try {
                while (isConnectedToDevice && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (socket == null || socket.isClosed() || !socket.isConnected()) {
                            break;
                        }

                        int bytesRead = socket.getInputStream().read(buffer);

                        if (bytesRead > 0) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);
                            handler.post(() -> processReceivedDataIndependent(data));
                        } else {
                            break;
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        continue;
                    } catch (java.net.SocketException e) {
                        if (isConnectedToDevice) {
                            break;
                        }
                    } catch (IOException e) {
                        if (isConnectedToDevice) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                // Error general
            }

            if (isConnectedToDevice) {
                handler.post(() -> {
                    isConnectedToDevice = false;
                    configurationSynced = false;
                    setControlsEnabled(false);
                    showToast("🔌 Conexión perdida");

                    handler.postDelayed(() -> {
                        showToast("🔄 Intentando reconectar...");
                        connectToDeviceIndependent();
                    }, 3000);
                });
            }
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
            return;
        }

        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            isConnectedToDevice = false;
            return;
        }

        executor.execute(() -> {
            try {
                if (outputStream != null) {
                    outputStream.write(command);
                    outputStream.flush();
                }
            } catch (IOException e) {
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
        isWaitingResponse = false;

        if (data == null || data.length < 4) {
            return;
        }

        if (!OctoNetCommandEncoder.validateCommandStructure(data)) {
            return;
        }

        if (!OctoNetCommandEncoder.verifyChecksum(data)) {
            return;
        }

        int responseType = data[1] & 0xFF;
        int command = data[2] & 0xFF;
        int dataSize = data[3] & 0xFF;

        OctoNetCommandEncoder.CmdSet commandType = OctoNetCommandEncoder.getCommandType(data);

        if (responseType == 0x45) {
            showToast("❌ Error del dispositivo");
            return;
        }

        if (responseType == 0x43) {
            try {
                if (command == 0x20) {
                    if (dataSize > 0) {
                        processConfigurationResponseIndependent(data);
                    } else {
                        showToast("✅ Configuración aplicada correctamente");
                        handler.postDelayed(() -> {
                            if (!isWaitingResponse) {
                                readDeviceConfigurationIndependent();
                            }
                        }, 1000);
                    }
                } else if (command == 0x21) {
                    if (dataSize > 0) {
                        processCurrentDataResponseIndependent(data);
                    } else {
                        showToast("❌ NODE_CURRENT sin datos disponibles");
                    }
                } else if (command == 0x30) {
                    // Device ID Response
                    processDeviceIdResponse(data);
                } else if (command == 0x40) {
                    // WiFi Settings Response
                    processWiFiSettingsResponse(data);
                }
            } catch (Exception e) {
                showToast("❌ Error procesando respuesta");
            }
        }
    }

    // ✅ PROCESAMIENTO DEVICE ID CORREGIDO PARA FASORES ACTIVITY
    private void processDeviceIdResponse(byte[] response) {
        try {
            System.out.println("🔍 FASORES - Procesando respuesta Device ID...");
            System.out.println("📊 FASORES - Respuesta completa hex: " + OctoNetCommandEncoder.bytesToHexString(response));

            byte[] deviceData = OctoNetCommandEncoder.extractCommandData(response);

            System.out.println("📊 FASORES - Datos extraídos: " + deviceData.length + " bytes");
            System.out.println("📊 FASORES - Datos hex: " + OctoNetCommandEncoder.bytesToHexString(deviceData));

            if (deviceData == null || deviceData.length == 0) {
                System.out.println("❌ FASORES - Datos vacíos");
                return;
            }

            lastReadDeviceIdInfo = new DeviceIdInfo();

            // ✅ INTENTAR PARSEO COMO STRING PRIMERO
            String deviceInfoString = new String(deviceData, "UTF-8").trim();
            System.out.println("📄 FASORES - Datos como string: " + deviceInfoString);

            // Si es formato concatenado (como: 140423000046090224112325LVTXER4WW4B0D028)
            if (deviceInfoString.length() >= 30 && !deviceInfoString.contains("\n")) {
                System.out.println("🔍 FASORES - Formato concatenado detectado");

                try {
                    // Serial: primeros 12 caracteres
                    if (deviceInfoString.length() >= 12) {
                        lastReadDeviceIdInfo.serial = deviceInfoString.substring(0, 12);
                        System.out.println("   Serial: " + lastReadDeviceIdInfo.serial);
                    }

                    // Fecha: caracteres 12-18 (DDMMYY)
                    if (deviceInfoString.length() >= 18) {
                        String dateStr = deviceInfoString.substring(12, 18);
                        lastReadDeviceIdInfo.facDate = dateStr.substring(0, 2) + "/" +
                                dateStr.substring(2, 4) + "/" +
                                dateStr.substring(4, 6);
                        System.out.println("   Fecha Fab: " + lastReadDeviceIdInfo.facDate);
                    }

                    // Hora: caracteres 18-24 (HHMMSS)
                    if (deviceInfoString.length() >= 24) {
                        String timeStr = deviceInfoString.substring(18, 24);
                        lastReadDeviceIdInfo.facHour = timeStr.substring(0, 2) + ":" +
                                timeStr.substring(2, 4) + ":" +
                                timeStr.substring(4, 6);
                        System.out.println("   Hora Fab: " + lastReadDeviceIdInfo.facHour);
                    }

                    // Código Act: caracteres 24-28
                    if (deviceInfoString.length() >= 28) {
                        lastReadDeviceIdInfo.actCode = deviceInfoString.substring(24, 28);
                        System.out.println("   Código Act: " + lastReadDeviceIdInfo.actCode);
                    }

                    // HW Version: caracteres 28-34
                    if (deviceInfoString.length() >= 34) {
                        lastReadDeviceIdInfo.hwVersion = deviceInfoString.substring(28, 34);
                        System.out.println("   HW Version: " + lastReadDeviceIdInfo.hwVersion);
                    }

                    // FW Version: resto de caracteres
                    if (deviceInfoString.length() > 34) {
                        lastReadDeviceIdInfo.fwVersion = deviceInfoString.substring(34);
                        System.out.println("   FW Version: " + lastReadDeviceIdInfo.fwVersion);
                    }

                    System.out.println("✅ FASORES - Device ID parseado (formato concatenado)");
                    showToast("✅ Información del dispositivo recibida");
                    return;

                } catch (Exception parseError) {
                    System.out.println("⚠️ FASORES - Error en parseo concatenado: " + parseError.getMessage());
                    // Continuar con el método binario
                }
            }

            // ✅ MÉTODO BINARIO (formato de 32+ bytes)
            if (deviceData.length >= 32) {
                // Serial (10 bytes) - bytes 0-9
                byte[] serialBytes = new byte[10];
                System.arraycopy(deviceData, 0, serialBytes, 0, 10);
                lastReadDeviceIdInfo.serial = new String(serialBytes).trim();
                System.out.println("   Serial: " + lastReadDeviceIdInfo.serial);

                // Fecha y hora de fabricación - bytes 10-15
                if (deviceData.length >= 16) {
                    int year = 2000 + (deviceData[10] & 0xFF);
                    int month = deviceData[11] & 0xFF;
                    int day = deviceData[12] & 0xFF;
                    lastReadDeviceIdInfo.facDate = String.format("%02d/%02d/%04d", day, month, year);
                    System.out.println("   Fecha Fab: " + lastReadDeviceIdInfo.facDate);

                    int hour = deviceData[13] & 0xFF;
                    int minute = deviceData[14] & 0xFF;
                    int second = deviceData[15] & 0xFF;
                    lastReadDeviceIdInfo.facHour = String.format("%02d:%02d:%02d", hour, minute, second);
                    System.out.println("   Hora Fab: " + lastReadDeviceIdInfo.facHour);
                }

                // Código de activación (10 bytes) - bytes 16-25
                if (deviceData.length >= 26) {
                    byte[] actCodeBytes = new byte[10];
                    System.arraycopy(deviceData, 16, actCodeBytes, 0, 10);
                    lastReadDeviceIdInfo.actCode = new String(actCodeBytes).trim();
                    System.out.println("   Código Act: " + lastReadDeviceIdInfo.actCode);
                }

                // Versiones HW y FW - bytes 26-31
                if (deviceData.length >= 32) {
                    byte[] hwBytes = new byte[3];
                    System.arraycopy(deviceData, 26, hwBytes, 0, 3);
                    lastReadDeviceIdInfo.hwVersion = new String(hwBytes).trim();
                    System.out.println("   HW Version: " + lastReadDeviceIdInfo.hwVersion);

                    byte[] fwBytes = new byte[3];
                    System.arraycopy(deviceData, 29, fwBytes, 0, 3);
                    lastReadDeviceIdInfo.fwVersion = new String(fwBytes).trim();
                    System.out.println("   FW Version: " + lastReadDeviceIdInfo.fwVersion);
                }

                System.out.println("✅ FASORES - Device ID parseado (formato binario)");
                showToast("✅ Información del dispositivo recibida");
            } else {
                // ✅ FALLBACK: usar toda la cadena como serial
                System.out.println("⚠️ FASORES - Datos insuficientes, usando string completo");
                lastReadDeviceIdInfo.serial = deviceInfoString;
                lastReadDeviceIdInfo.facDate = "N/A";
                lastReadDeviceIdInfo.facHour = "N/A";
                lastReadDeviceIdInfo.actCode = "N/A";
                lastReadDeviceIdInfo.hwVersion = "N/A";
                lastReadDeviceIdInfo.fwVersion = "N/A";

                showToast("⚠️ Información parcial recibida");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando Device ID: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando Device ID");
        }
    }

    private void processWiFiSettingsResponse(byte[] response) {
        try {
            byte[] wifiData = OctoNetCommandEncoder.extractCommandData(response);

            if (wifiData.length >= 64) {
                String ssid = "";
                String pass = "";

                // SSID (32 bytes)
                byte[] ssidBytes = new byte[32];
                System.arraycopy(wifiData, 0, ssidBytes, 0, 32);
                ssid = new String(ssidBytes).trim();

                // Password (32 bytes)
                byte[] passBytes = new byte[32];
                System.arraycopy(wifiData, 32, passBytes, 0, 32);
                pass = new String(passBytes).trim();

                showToast("✅ WiFi del medidor: " + ssid);
            }
        } catch (Exception e) {
            showToast("❌ Error procesando WiFi Settings");
        }
    }

    private void processConfigurationResponseIndependent(byte[] response) {
        try {
            byte[] configData = OctoNetCommandEncoder.extractCommandData(response);

            if (configData.length == 0) {
                return;
            }

            skipSpinnerEvents = true;

            try {
                if (configData.length >= 4) {
                    recordingConfig = (configData[0] & 0xFF) == 1;

                    int period = configData[1] & 0xFF;
                    periodConfig = 0; // SIEMPRE 1 MINUTO

                    int sensors = configData[2] & 0xFF;
                    if (sensors >= 0 && sensors <= 5 && spinnerAmperes != null) {
                        sensorsConfig = sensors;
                        updateAmperesRange(sensors);
                        spinnerAmperes.setSelection(sensors);
                    }

                    meteringTypeConfig = 3;
                    tipoCableado = 3;
                    if (spinnerCableado != null) {
                        spinnerCableado.setSelection(0);
                    }
                    updateDiagram();

                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);

                    showToast("✅ Configuración sincronizada");
                }
            } catch (Exception e) {
                // Ignorar
            }

        } catch (Exception e) {
            // Ignorar
        } finally {
            skipSpinnerEvents = false;
        }
    }

    private void processCurrentDataResponseIndependent(byte[] response) {
        try {
            int sizeFromDevice = response[3] & 0xFF;

            if (sizeFromDevice > 0) {
                int realDataSize = sizeFromDevice + 1;

                if (response.length < 4 + realDataSize) {
                    return;
                }

                byte[] energyData = new byte[realDataSize];
                System.arraycopy(response, 4, energyData, 0, realDataSize);

                if (energyData.length >= 64) {
                    // CH1: Bytes 28-39
                    if (energyData.length >= 40) {
                        processChannel64TcpStyle(energyData, 28, 0, "CH1");
                    }

                    // CH2: Bytes 40-51
                    if (energyData.length >= 52) {
                        processChannel64TcpStyle(energyData, 40, 1, "CH2");
                    }

                    // CH3: Bytes 52-63
                    if (energyData.length >= 64) {
                        processChannel64TcpStyle(energyData, 52, 2, "CH3");
                    }

                } else {
                    for (int i = 0; i < 3; i++) {
                        voltajes[i] = 0.0f;
                        corrientes[i] = 0.0f;
                        potencias[i] = 0.0f;
                        frecuencias[i] = 50.0f;
                        angulos[i] = i * 120.0f;
                    }
                }

                boolean hasValidData = false;
                for (int i = 0; i < 3; i++) {
                    if (voltajes[i] > 0.1f || corrientes[i] > 0.01f || potencias[i] > 0.1f) {
                        hasValidData = true;
                        break;
                    }
                }

                if (!hasValidData) {
                    for (int i = 0; i < 3; i++) {
                        if (angulos[i] == 0.0f && i > 0) {
                            angulos[i] = i * 120.0f;
                        }
                        frecuencias[i] = 50.0f;
                    }
                }

                updateDisplayWithRealData();
                updateFasores();

                contadorMuestras++;

                if (contadorMuestras % 10 == 0) {
                    long tiempoTranscurrido = (System.currentTimeMillis() - tiempoInicio) / 1000;
                    showToast(String.format("📊 %d muestras (%ds)", contadorMuestras, tiempoTranscurrido));
                }

            } else {
                showToast("❌ Sin datos de energía disponibles");
            }

        } catch (Exception e) {
            showToast("❌ Error procesando datos de energía");
        }
    }

    private void processChannel64TcpStyle(byte[] data, int offset, int channelIndex, String channelName) {
        try {
            // W_CHx (Int32) - 4 bytes
            if (data.length >= offset + 4) {
                long powerRaw = readInt32(data, offset);
                float powerW = (int) powerRaw * 0.1f;
                potencias[channelIndex] = powerW;
            }

            // V_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16(data, offset + 4);
                float voltageV = voltageRaw * 0.1f;
                voltajes[channelIndex] = voltageV;
            }

            // A_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 8) {
                int currentRaw = readUInt16(data, offset + 6);
                float currentA = currentRaw * 0.1f;
                corrientes[channelIndex] = currentA;
            }

            // HZ_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16(data, offset + 8);
                float freqHz = frequencyRaw * 0.1f;
                frecuencias[channelIndex] = freqHz;
            }

            // ANGLE_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 12) {
                int angleRaw = readUInt16(data, offset + 10);
                float angleDeg = angleRaw * 0.1f;
                angulos[channelIndex] = angleDeg;
            } else {
                angulos[channelIndex] = channelIndex * 120.0f;
            }

        } catch (Exception e) {
            // Ignorar
        }
    }

    private long readInt32(byte[] data, int offset) {
        return ((data[offset] & 0xFFL)) |
                ((data[offset + 1] & 0xFFL) << 8) |
                ((data[offset + 2] & 0xFFL) << 16) |
                ((data[offset + 3] & 0xFFL) << 24);
    }

    private int readUInt16(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }

    // =========================================================================
    // ===== CONTROL DE ADQUISICIÓN ===========================================
    // =========================================================================

    private void startDataAcquisition() {
        if (!isConnectedToDevice || autoReadEnabled) {
            return;
        }

        try {
            autoReadEnabled = true;
            tiempoInicio = System.currentTimeMillis();
            contadorMuestras = 0;

            btnPlay.setImageResource(android.R.drawable.ic_media_pause);
            showToast("🚀 Iniciando lectura cada 5 segundos");

            requestCurrentData();
            autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);

        } catch (Exception e) {
            autoReadEnabled = false;
            btnPlay.setImageResource(android.R.drawable.ic_media_play);
            showToast("❌ Error al iniciar adquisición");
        }
    }

    private void stopDataAcquisition() {
        if (autoReadEnabled) {
            autoReadEnabled = false;
            autoReadHandler.removeCallbacks(autoReadTask);
            setSpinnersEnabled(true);
            btnPlay.setImageResource(android.R.drawable.ic_media_play);

            long tiempoTotal = (System.currentTimeMillis() - tiempoInicio) / 1000;
            showToast(String.format("⏹️ Detenido: %d muestras en %ds", contadorMuestras, tiempoTotal));
        }
    }

    // =========================================================================
    // ===== VISUALIZACIÓN ====================================================
    // =========================================================================

    private void updateDisplayWithRealData() {
        updatePhaseDisplay(0, voltajes[0], corrientes[0], potencias[0], frecuencias[0]);
        updatePhaseDisplay(1, voltajes[1], corrientes[1], potencias[1], frecuencias[1]);
        updatePhaseDisplay(2, voltajes[2], corrientes[2], potencias[2], frecuencias[2]);

        if (tvCH1 != null) {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
            tvCH1.setText("CH1 - " + timestamp);
        }
    }

    private void updateFasores() {
        try {
            if (fasorVoltaje != null) {
                fasorVoltaje.setPhasorValues(voltajes, angulos);
            }

            if (fasorCorriente != null) {
                fasorCorriente.setPhasorValues(corrientes, angulos);
            }
        } catch (Exception e) {
            // Ignorar
        }
    }

    private void updatePhaseDisplay(int phase, float volt, float corr, float power, float freq) {
        TextView tvV, tvA, tvW, tvHz, tvPF;

        switch (phase) {
            case 1:
                tvV = tvV2;
                tvA = tvA2;
                tvW = tvW2;
                tvHz = tvHz2;
                tvPF = tvpF2;
                break;
            case 2:
                tvV = tvV3;
                tvA = tvA3;
                tvW = tvW3;
                tvHz = tvHz3;
                tvPF = tvpF3;
                break;
            default:
                tvV = tvV1;
                tvA = tvA1;
                tvW = tvW1;
                tvHz = tvHz1;
                tvPF = tvpF1;
                break;
        }

        if (tvV != null) tvV.setText(String.format("%.1f V", volt));
        if (tvA != null) tvA.setText(String.format("%.2f A", corr));
        if (tvW != null) tvW.setText(String.format("%.1f W", power));
        if (tvHz != null) tvHz.setText(String.format("%.1f Hz", freq));

        if (tvPF != null) {
            if (phase >= 0 && phase < angulos.length) {
                float deviceAngle = angulos[phase];

                while (deviceAngle > 180f) deviceAngle -= 360f;
                while (deviceAngle < -180f) deviceAngle += 360f;

                tvPF.setText(String.format("%.0f°", deviceAngle));
            } else {
                tvPF.setText("--");
            }
        }
    }

    private void updateDiagram() {
        if (imageDiagram != null) {
            imageDiagram.setImageResource(R.drawable.diagram_3p4w_n);
        }

        if (isModalVisible && modalDiagramImage != null) {
            updateModalDiagram();
        }
    }

    // =========================================================================
    // ===== CONTROL UI =======================================================
    // =========================================================================

    private void setSpinnersEnabled(boolean enabled) {
        if (spinnerCableado != null) {
            spinnerCableado.setEnabled(enabled);
            spinnerCableado.setAlpha(enabled ? 1.0f : 0.4f);
        }
        if (spinnerAmperes != null) {
            spinnerAmperes.setEnabled(enabled);
            spinnerAmperes.setAlpha(enabled ? 1.0f : 0.4f);
        }
    }

    private void setControlsEnabled(boolean enabled) {
        if (btnPlay != null) {
            btnPlay.setEnabled(enabled);
            btnPlay.setAlpha(enabled ? 1.0f : 0.4f);
        }
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

    private void initializeDisplayValues() {
        for (int i = 0; i < 3; i++) {
            updatePhaseDisplay(i, 0.0f, 0.0f, 0.0f, 0.0f);
            voltajes[i] = 0.0f;
            corrientes[i] = 0.0f;
            potencias[i] = 0.0f;
            frecuencias[i] = 50.0f;
            angulos[i] = i * 120.0f;
        }

        if (fasorVoltaje != null) {
            fasorVoltaje.setThreeAxisMode(true);
            fasorVoltaje.setPhasorValues(voltajes, angulos);
        }
        if (fasorCorriente != null) {
            fasorCorriente.setThreeAxisMode(true);
            fasorCorriente.setPhasorValues(corrientes, angulos);
        }
    }

    // =========================================================================
    // ===== MÉTODOS AUXILIARES ===============================================
    // =========================================================================

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onBackPressed() {
        if (isModalVisible) {
            hideDiagramModal();
            return;
        }

        if (autoReadEnabled) {
            stopDataAcquisition();
        }
        super.onBackPressed();
    }

    // =========================================================================
    // ===== LIFECYCLE METHODS ================================================
    // =========================================================================

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (wifiReceiver != null) {
            unregisterReceiver(wifiReceiver);
        }
        stopDataAcquisition();
        disconnectFromDevice();
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        handler.removeCallbacksAndMessages(null);
        autoReadHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (autoReadEnabled) {
            autoReadHandler.removeCallbacks(autoReadTask);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isConnectedToDevice) {
            handler.postDelayed(() -> connectToDeviceIndependent(), 500);
        }

        if (autoReadEnabled && isConnectedToDevice && configurationSynced) {
            autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (autoReadEnabled) {
            autoReadHandler.removeCallbacks(autoReadTask);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!isConnectedToDevice) {
            connectToDeviceIndependent();
        }
    }
}