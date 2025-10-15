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

import androidx.appcompat.app.AlertDialog;
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
    private ImageButton btnRec;
    private boolean isRecording = false;

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

    private long readInt32LE(byte[] data, int offset) {
        return ((data[offset] & 0xFFL)) |
                ((data[offset + 1] & 0xFFL) << 8) |
                ((data[offset + 2] & 0xFFL) << 16) |
                ((data[offset + 3] & 0xFFL) << 24);
    }

    private int readUInt16LE(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8);
    }


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
        btnRec = findViewById(R.id.btnRec);
        btnPlay = findViewById(R.id.btnPlay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        // Botones WiFi y Device ID
        btnDeviceId = findViewById(R.id.btnDeviceId);
        btnConfigWifi = findViewById(R.id.btnConfigWifi);

        // Fasores con modo 3 ejes
        fasorVoltaje = findViewById(R.id.fasorVoltaje);
        fasorCorriente = findViewById(R.id.fasorCorriente);
        btnPlay = findViewById(R.id.btnPlay);


        if (btnPlay == null) {
            System.out.println("❌ FASORES - btnPlay es NULL - verificar R.id.btnPlay en XML");
        } else {
            System.out.println("✅ FASORES - btnPlay inicializado correctamente");

            btnPlay.setImageResource(R.drawable.ic_play);
        }

        if (fasorVoltaje != null) {
            fasorVoltaje.setThreeAxisMode(true);
            fasorVoltaje.setTitle("Voltajes Trifásicos");
            fasorVoltaje.setUnit("V");
            fasorVoltaje.setAutoScale(true);
            fasorVoltaje.setVoltageMode(true);
        }

        if (fasorCorriente != null) {
            fasorCorriente.setThreeAxisMode(true);
            fasorCorriente.setTitle("Corrientes Trifásicas");
            fasorCorriente.setUnit("A");
            fasorCorriente.setAutoScale(true);
            fasorCorriente.setVoltageMode(false);
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

    private void processIntentDataAndConnect() {
        Intent intent = getIntent();

        // Obtener IP y puerto del intent o usar valores por defecto
        String ip = intent.getStringExtra("device_ip");
        String port = intent.getStringExtra("device_port");

        if (ip != null && !ip.isEmpty()) {
            deviceIp = ip;
        }

        if (port != null && !port.isEmpty()) {
            try {
                devicePort = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                devicePort = 333;
            }
        }

        // Conectar inmediatamente con nuestra propia conexión
        handler.postDelayed(() -> connectToDeviceIndependent(), 1000);
    }



    private void setupSpinners() {
        String[] cableadoOptions = {"Carga Trifásica"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        // ✅ SOLO QUITAR 400A
        String[] amperesOptions = {
                "50A", "200A", "1000A", "3000A"
        };
        setupSpinnerAdapter(spinnerAmperes, amperesOptions);

        setupSpinnerListeners();

        skipSpinnerEvents = true;
        spinnerAmperes.setSelection(0);
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
            }

            @Override
            public void onValidationProgress(String ssid, String progress) {
            }

            @Override
            public void onValidationSuccess(WiFiValidationModule.ValidatedNetwork network) {
                showValidatedNetworkConfirmation(network);
            }

            @Override
            public void onValidationFailed(String ssid, String error) {
                showToast("❌ " + error);
            }

            @Override
            public void onValidationTimeout(String ssid) {
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
            System.out.println("❌ FASORES - No conectado para setup");
            return;
        }

        System.out.println("⚙️ FASORES - Iniciando setup automático...");

        executor.execute(() -> {
            try {
                // ✅ 1. Sincronizar hora
                System.out.println("🕐 FASORES - Enviando hora del sistema...");
                sendTimeWriteCommand();
                Thread.sleep(500);

                // ✅ 2. Leer configuración WiFi
                System.out.println("📡 FASORES - Leyendo configuración WiFi...");
                sendWiFiReadCommand();
                Thread.sleep(500);

                // ✅ 3. Leer configuración del dispositivo
                System.out.println("🔧 FASORES - Leyendo configuración...");
                readDeviceConfigurationIndependent();
                Thread.sleep(500);

                // ✅ MARCAR COMO SINCRONIZADO
                System.out.println("✅ FASORES - Setup completado");
                handler.post(() -> {
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                });

            } catch (InterruptedException e) {
                System.out.println("⚠️ FASORES - Setup interrumpido");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error en setup: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    configurationSynced = true; // Permitir uso manual
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
        sendDeviceIdReadCommand();

        // ✅ ESPERAR SOLO 2 SEGUNDOS Y MOSTRAR LO QUE HAYA
        handler.postDelayed(() -> {
            if (lastReadDeviceIdInfo != null) {
                displayDeviceIdModal();
            }
        }, 2000);
    }

    private void displayDeviceIdModal() {
        if (lastReadDeviceIdInfo == null) {
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
                            userRequestedWifiScan = true;
                            wifiManager.startScan();
                        })
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
        }

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
            showToast("❌ No se encontraron redes WiFi");
            return;
        }

        List<String> networkNames = new ArrayList<>();
        List<String> networkSSIDs = new ArrayList<>();

        // ✅ FILTRAR SOLO REDES DE 2.4GHz
        int total5GHz = 0;
        for (ScanResult result : availableNetworks) {
            if (result.SSID != null && !result.SSID.isEmpty() && !result.SSID.startsWith("ESP")) {
                if (is24GHzNetwork(result)) {
                    // ✅ Agregar red 2.4GHz
                    String displayName = result.SSID + " (2.4GHz - " + result.frequency + " MHz)";
                    networkNames.add(displayName);
                    networkSSIDs.add(result.SSID);

                    System.out.println("✅ Red 2.4GHz: " + result.SSID + " (" + result.frequency + " MHz)");
                } else {
                    total5GHz++;
                    System.out.println("⚠️ Red 5GHz filtrada: " + result.SSID + " (" + result.frequency + " MHz)");
                }
            }
        }

        if (networkNames.isEmpty()) {
            String mensaje = "❌ No se encontraron redes de 2.4GHz compatibles";
            if (total5GHz > 0) {
                mensaje += "\n\n⚠️ Se encontraron " + total5GHz + " redes de 5GHz que fueron filtradas.\n\n" +
                        "El medidor solo es compatible con redes de 2.4GHz.";
            }

            new MaterialAlertDialogBuilder(this)
                    .setTitle("Sin redes compatibles")
                    .setMessage(mensaje)
                    .setPositiveButton("Entendido", null)
                    .show();
            return;
        }

        String titulo = "📡 Redes WiFi 2.4GHz Disponibles (" + networkNames.size() + ")";
        if (total5GHz > 0) {
            titulo += "\n⚠️ " + total5GHz + " redes 5GHz filtradas";
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(titulo)
                .setItems(networkNames.toArray(new String[0]), (dialog, which) -> {
                    String selectedSsid = networkSSIDs.get(which);
                    showPasswordDialogForValidation(selectedSsid);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private boolean is24GHzNetwork(ScanResult result) {
        try {
            int frequency = result.frequency;

            // ✅ Verificar rango de frecuencia 2.4GHz
            if (frequency >= 2400 && frequency <= 2500) {
                return true;
            }

            // ✅ Excluir rango 5GHz
            if (frequency >= 5000 && frequency <= 6000) {
                return false;
            }

            // ✅ Verificar por nombre como fallback
            return verify24GHzBySSID(result.SSID);

        } catch (Exception e) {
            System.out.println("⚠️ Error verificando frecuencia: " + e.getMessage());
            return verify24GHzBySSID(result.SSID);
        }
    }

    private boolean verify24GHzBySSID(String ssid) {
        if (ssid == null || ssid.isEmpty()) return false;

        String ssidLower = ssid.toLowerCase();

        // ✅ Patrones de 5GHz (EXCLUIR)
        String[] fiveGHzPatterns = {
                "_5g", "-5g", " 5g", "(5g)", "[5g]",
                "_5ghz", "-5ghz", " 5ghz",
                "_ac", "-ac", " ac",
                "_ax", "-ax", " ax",
                "5.0", "5ghz", "5g"
        };

        for (String pattern : fiveGHzPatterns) {
            if (ssidLower.contains(pattern)) {
                return false;
            }
        }

        // ✅ Si no tiene marcadores de 5GHz, asumir 2.4GHz
        return true;
    }



    private void showPasswordDialogForValidation(String ssid) {
        // ✅ CREAR LAYOUT PRINCIPAL
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 40, 50, 40);
        mainLayout.setBackgroundColor(Color.WHITE);

        // ✅ TÍTULO PERSONALIZADO
        TextView titleView = new TextView(this);
        titleView.setText("📡 Configurar Red WiFi");
        titleView.setTextSize(20f);
        titleView.setTypeface(null, Typeface.BOLD);
        titleView.setTextColor(Color.rgb(33, 33, 33));
        titleView.setPadding(0, 0, 0, 20);
        mainLayout.addView(titleView);

        // ✅ SUBTÍTULO CON NOMBRE DE RED
        TextView ssidView = new TextView(this);
        ssidView.setText("Red: " + ssid);
        ssidView.setTextSize(16f);
        ssidView.setTextColor(Color.rgb(100, 100, 100));
        ssidView.setPadding(0, 0, 0, 30);
        mainLayout.addView(ssidView);

        // ✅ CAMPO DE CONTRASEÑA CON MATERIAL DESIGN
        final TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("🔐 Contraseña WiFi");
        textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        textInputLayout.setBoxStrokeColor(Color.rgb(33, 150, 243));
        textInputLayout.setHintTextColor(android.content.res.ColorStateList.valueOf(Color.rgb(100, 100, 100)));

        // ✅ BOTÓN PARA MOSTRAR/OCULTAR CONTRASEÑA
        textInputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        // ✅ LAYOUT PARAMS
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        layoutParams.setMargins(0, 10, 0, 30);
        textInputLayout.setLayoutParams(layoutParams);

        // ✅ EDITTEXT VISIBLE POR DEFECTO
        final TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        editText.setMaxLines(1);
        editText.setSingleLine(true);
        editText.setTextSize(16f);
        editText.setPadding(25, 35, 25, 35);

        textInputLayout.addView(editText);
        mainLayout.addView(textInputLayout);

        // ✅ TEXTO DE AYUDA
        TextView helpText = new TextView(this);
        helpText.setText("💡 La contraseña debe tener al menos 8 caracteres");
        helpText.setTextSize(12f);
        helpText.setTextColor(Color.rgb(150, 150, 150));
        helpText.setPadding(5, 0, 0, 20);
        mainLayout.addView(helpText);

        // ✅ REFERENCIA FINAL
        final String finalSsid = ssid;

        // ✅ CREAR DIALOG
        new MaterialAlertDialogBuilder(this)
                .setView(mainLayout)
                .setPositiveButton("✅ Validar", (dialog, which) -> {
                    String password = editText.getText().toString().trim();

                    if (password.isEmpty()) {
                        showToast("❌ La contraseña no puede estar vacía");
                        return;
                    }

                    if (password.length() < 8) {
                        new MaterialAlertDialogBuilder(FasoresActivity.this)
                                .setTitle("⚠️ Contraseña muy corta")
                                .setMessage("La contraseña \"" + password + "\" tiene solo " +
                                        password.length() + " caracteres.\n\n" +
                                        "¿Deseas continuar de todas formas?")
                                .setPositiveButton("✅ Continuar", (d, w) ->
                                        wifiValidationModule.validateNetwork(finalSsid, password))
                                .setNegativeButton("❌ Cancelar", null)
                                .show();
                        return;
                    }

                    wifiValidationModule.validateNetwork(finalSsid, password);
                })
                .setNegativeButton("❌ Cancelar", null)
                .setCancelable(true)
                .show();
    }

    private void showValidatedNetworkConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        if (network == null || network.ssid == null) {
            return;
        }

        final WiFiValidationModule.ValidatedNetwork finalNetwork = network;

        new MaterialAlertDialogBuilder(this)
                .setTitle("📤 Enviar Red al Medidor")
                .setMessage("La red \"" + network.ssid +
                        "\" fue validada correctamente.\n\n¿Deseas enviar estas credenciales al medidor ahora?")
                .setPositiveButton("✅ Enviar", (dialog, which) ->
                        sendWiFiCredentialsToDeviceWithModal(finalNetwork))
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }


    private void sendWiFiCredentialsToDeviceWithModal(WiFiValidationModule.ValidatedNetwork network) {
        try {
            if (network == null || network.ssid == null || network.password == null) {
                System.out.println("❌ FASORES - Network inválido");
                return;
            }

            final String finalSsid = network.ssid;
            final String finalPassword = network.password;

            // ✅ CREAR MODAL SIN ICONO
            LinearLayout modalLayout = new LinearLayout(this);
            modalLayout.setOrientation(LinearLayout.VERTICAL);
            modalLayout.setPadding(60, 50, 60, 50);
            modalLayout.setGravity(Gravity.CENTER);

            // ✅ TÍTULO
            TextView titleView = new TextView(this);
            titleView.setText("📡 Conectando al METER");
            titleView.setTextSize(24f);
            titleView.setTypeface(null, Typeface.BOLD);
            titleView.setTextColor(Color.rgb(33, 150, 243));
            titleView.setGravity(Gravity.CENTER);
            titleView.setPadding(0, 0, 0, 30);
            modalLayout.addView(titleView);

            // ✅ MENSAJE PRINCIPAL
            TextView messageView = new TextView(this);
            messageView.setText("✅ Credenciales enviadas al METER\n\n" +
                    "⏳Espera un momento hasta que el METER\n\n" +
                    " parpadee lentamente en color AZUL");
            messageView.setTextSize(16f);
            messageView.setTextColor(Color.rgb(66, 66, 66));
            messageView.setGravity(Gravity.CENTER);
            messageView.setLineSpacing(10f, 1.0f);
            messageView.setPadding(20, 0, 20, 40);
            modalLayout.addView(messageView);

            // ✅ PROGRESS BAR
            android.widget.ProgressBar progressBar = new android.widget.ProgressBar(this);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            progressParams.gravity = Gravity.CENTER;
            progressBar.setLayoutParams(progressParams);
            modalLayout.addView(progressBar);

            // ✅ CREAR DIALOG
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setView(modalLayout);
            builder.setCancelable(false);

            AlertDialog waitDialog = builder.create();

            // ✅ ENVIAR COMANDO
            byte[] command = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(
                    finalSsid,
                    finalPassword
            );

            if (command == null || command.length == 0) {
                System.out.println("❌ FASORES - Comando WiFi vacío");
                return;
            }

            waitDialog.show();

            handler.postDelayed(() -> {
                sendTcpCommandIndependent(command);

                System.out.println("📡 FASORES - Credenciales WiFi enviadas:");
                System.out.println("   SSID: " + finalSsid);

                handler.postDelayed(() -> {
                    if (waitDialog.isShowing()) {
                        waitDialog.dismiss();

                        new MaterialAlertDialogBuilder(FasoresActivity.this)
                                .setTitle("✅ Proceso Completado")
                                .setMessage("Las credenciales fueron enviadas correctamente.\n\n" +
                                        "Verifica que el LED del METER\n" +
                                        "💙 parpadee lentamente en color AZUL\n\n" +
                                        "Esto indica que el METER está conectándose a tu red WiFi.")
                                .setPositiveButton("Entendido", null)
                                .show();
                    }
                }, 12000);

            }, 500);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error enviando WiFi: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error enviando credenciales");
        }
    }



    private void sendWiFiCredentialsToDevice(WiFiValidationModule.ValidatedNetwork network) {
        try {
            // ✅ VALIDAR QUE NETWORK NO SEA NULL
            if (network == null || network.ssid == null || network.password == null) {
                System.out.println("❌ FASORES - Network inválido");
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
                return;
            }

            sendTcpCommandIndependent(command);

            System.out.println("📡 FASORES - Enviando WiFi al medidor:");
            System.out.println("   SSID: " + finalSsid);
            System.out.println("   Tamaño comando: " + command.length + " bytes");
            System.out.println("   Comando hex: " + OctoNetCommandEncoder.bytesToHexString(command));

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error enviando WiFi: " + e.getMessage());
            e.printStackTrace();
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
        spinnerAmperes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    updateAmperesRange(position);
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse) {
                            writeDeviceConfigurationFromSpinners();
                        }
                    }, 500);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // ✅ SIMPLIFICAR LISTENER DE CABLEADO (solo tiene 1 opción)
        spinnerCableado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    // Como solo hay "Carga Trifásica", siempre es tipoCableado = 3
                    tipoCableado = 3;
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse) {
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
            int periodValue = 0;

            int spinnerPosition = spinnerAmperes.getSelectedItemPosition();
            int sensorsValue;

            switch (spinnerPosition) {
                case 0:
                    sensorsValue = 1; // 50A
                    break;
                case 1:
                    sensorsValue = 2; // 200A
                    break;
                case 2:
                    sensorsValue = 4; // 1000A
                    break;
                case 3:
                    sensorsValue = 5; // 3000A
                    break;
                default:
                    sensorsValue = 1;
                    break;
            }

            int meteringTypeValue = 3;
            boolean recordingValue = true;

            System.out.println("📤 FASORES - Enviando configuración:");
            System.out.println("   Posición spinner: " + spinnerPosition);
            System.out.println("   Código sensor: 0x0" + sensorsValue);
            System.out.println("   Rango: " + rangoAmperes + "A");

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingValue, periodValue, sensorsValue, meteringTypeValue);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            showToast("✅ Configuración aplicada");

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                }
            }, 5000);

        } catch (Exception e) {
            isWaitingResponse = false;
        }
    }

    private void updateAmperesRange(int sensorIndex) {
        // 0 -> 0x01 -> 50A
        // 1 -> 0x02 -> 200A
        // 2 -> 0x04 -> 1000A
        // 3 -> 0x05 -> 3000A

        int[] amperesValues = {50, 200, 1000, 3000};

        if (sensorIndex >= 0 && sensorIndex < amperesValues.length) {
            rangoAmperes = amperesValues[sensorIndex];
            System.out.println("⚡ FASORES - Rango amperes actualizado:");
            System.out.println("   Índice spinner: " + sensorIndex);
            System.out.println("   Valor real: " + rangoAmperes + "A");

            updateDiagram();

        } else {
            System.out.println("❌ FASORES - Índice de sensor inválido: " + sensorIndex);
            rangoAmperes = 50;
            updateDiagram();
        }
    }

    // =========================================================================
    // ===== BOTONES ==========================================================
    // =========================================================================

    private void setupButtons() {

        btnRec.setOnClickListener(v -> {
            if (!isConnectedToDevice) {
                showToast("❌ No hay conexión");
                return;
            }
            if (!configurationSynced) {
                showToast("⏳ Esperando sincronización...");
                return;
            }

            toggleRecording();
        });
        btnPlay.setOnClickListener(v -> {
            if (!isConnectedToDevice) {
                showToast("❌ No hay conexión");
                return;
            }
            if (!configurationSynced) {
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
        // ✅ TÍTULO DINÁMICO SEGÚN RANGO
        String tituloModal = "Diagrama de Conexión Trifásico - " + rangoAmperes + "A";
        modalTitle.setText(tituloModal);
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

    private void toggleRecording() {
        isRecording = !isRecording;

        System.out.println("🎬 FASORES - Cambiando estado REC a: " +
                (isRecording ? "GRABANDO" : "PAUSADO"));

        // ✅ CAMBIAR FONDO DEL BOTÓN
        if (isRecording) {
            btnRec.setBackgroundResource(R.drawable.button_rec_recording);
            showToast("⏺ Grabación iniciada");

            // ✅ OPCIONAL: Animar el botón (pulso)
            startRecordingAnimation();
        } else {
            btnRec.setBackgroundResource(R.drawable.button_rec);
            showToast("⏸ Grabación pausada");

            // ✅ DETENER ANIMACIÓN
            stopRecordingAnimation();
        }

        // ✅ ENVIAR COMANDO AL DISPOSITIVO
        sendRecordingCommand(isRecording);
    }

    private void sendRecordingCommand(boolean recording) {
        if (!isConnectedToDevice || isWaitingResponse) {
            System.out.println("❌ FASORES - No se puede cambiar REC: " +
                    (!isConnectedToDevice ? "sin conexión" : "esperando respuesta"));
            return;
        }

        try {
            // Usar configuración actual
            int periodValue = periodConfig;
            int sensorsValue = sensorsConfig;
            int meteringTypeValue = meteringTypeConfig;
            boolean recordingValue = recording; // ✅ Nuevo estado

            System.out.println("📤 FASORES - Enviando comando REC:");
            System.out.println("   Recording: " + (recordingValue ? "ON (0x01)" : "OFF (0x00)"));
            System.out.println("   Period: " + periodValue);
            System.out.println("   Sensors: " + sensorsValue);
            System.out.println("   MeteringType: " + meteringTypeValue);

            // Crear comando
            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingValue, periodValue, sensorsValue, meteringTypeValue);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            // Timeout
            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                    System.out.println("⏰ FASORES - Timeout comando REC");
                }
            }, 5000);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error enviando REC: " + e.getMessage());
            e.printStackTrace();
            isWaitingResponse = false;

            // Revertir estado visual en caso de error
            isRecording = !isRecording;
            btnRec.setBackgroundResource(
                    isRecording ? R.drawable.button_rec_recording : R.drawable.button_rec
            );
        }
    }



    private void startRecordingAnimation() {
        if (btnRec == null) return;

        // Animación de opacidad MUY suave y lenta
        btnRec.animate()
                .alpha(0.7f) // ✅ Reducir solo al 70% (más sutil)
                .setDuration(2000) // ✅ 2 segundos para atenuar (MÁS LENTO)
                .setInterpolator(new DecelerateInterpolator(2.5f)) // ✅ Súper suave
                .withEndAction(() -> {
                    if (isRecording && btnRec != null) {
                        btnRec.animate()
                                .alpha(1.0f) // ✅ Volver a opacidad completa
                                .setDuration(2000) // ✅ 2 segundos para iluminar (MÁS LENTO)
                                .setInterpolator(new AccelerateInterpolator(2.5f)) // ✅ Súper suave
                                .withEndAction(() -> {
                                    if (isRecording) {
                                        startRecordingAnimation(); // Loop continuo
                                    }
                                })
                                .start();
                    }
                })
                .start();
    }

    private void stopRecordingAnimation() {
        if (btnRec == null) return;

        btnRec.clearAnimation();
        btnRec.setScaleX(1.0f);
        btnRec.setScaleY(1.0f);
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

        int diagramResource;

        switch (rangoAmperes) {
            case 50:
                diagramResource = R.drawable.a50;
                break;

            case 200:
                diagramResource = R.drawable.a200;
                break;

            case 1000:
            case 3000:
                diagramResource = R.drawable.a1000;
                break;

            default:
                diagramResource = R.drawable.a50;
                break;
        }

        modalDiagramImage.setImageResource(diagramResource);
    }

    // =========================================================================
    // ===== AUTO-LECTURA =====================================================
    // =========================================================================

    private void setupAutoReadTask() {
        System.out.println("🔧 FASORES - Configurando autoReadTask...");

        // ✅ ASEGURAR QUE autoReadHandler EXISTA
        if (autoReadHandler == null) {
            autoReadHandler = new Handler(Looper.getMainLooper());
            System.out.println("   ✓ autoReadHandler creado");
        }

        // ✅ CREAR RUNNABLE DE AUTO-LECTURA
        autoReadTask = new Runnable() {
            @Override
            public void run() {
                System.out.println("🔄 FASORES - Auto-read tick ejecutado");
                System.out.println("   autoReadEnabled: " + autoReadEnabled);
                System.out.println("   isConnectedToDevice: " + isConnectedToDevice);
                System.out.println("   configurationSynced: " + configurationSynced);
                System.out.println("   isWaitingResponse: " + isWaitingResponse);

                // ✅ VERIFICAR CONDICIONES ANTES DE CONTINUAR
                if (autoReadEnabled && isConnectedToDevice && configurationSynced) {
                    // ✅ SOLICITAR DATOS
                    System.out.println("📤 FASORES - Solicitando NODE_CURRENT...");
                    requestCurrentData();

                    // ✅ PROGRAMAR SIGUIENTE LECTURA
                    autoReadHandler.postDelayed(this, AUTO_READ_INTERVAL);
                    System.out.println("   ✓ Próxima lectura programada en " + AUTO_READ_INTERVAL + "ms");

                } else {
                    System.out.println("⚠️ FASORES - Auto-read detenido (condiciones no cumplidas)");
                    System.out.println("   Motivo: " +
                            (!autoReadEnabled ? "autoReadEnabled=false " : "") +
                            (!isConnectedToDevice ? "desconectado " : "") +
                            (!configurationSynced ? "no_sincronizado" : ""));

                    // ✅ ASEGURAR QUE EL BOTÓN VUELVA A PLAY
                    if (!autoReadEnabled && btnPlay != null) {
                        handler.post(() -> {
                            btnPlay.setImageResource(android.R.drawable.ic_media_play);
                            System.out.println("   ✓ Botón restaurado a PLAY");
                        });
                    }
                }
            }
        };

        System.out.println("✅ FASORES - autoReadTask configurado correctamente");

        // ✅ ACTUALIZAR DIAGRAMA
        updateDiagram();
    }

    // =========================================================================
    // ===== CONEXIÓN TCP =====================================================
    // =========================================================================

    private void connectToDeviceIndependent() {
        // ✅ EVITAR CONEXIONES DUPLICADAS
        if (isConnectedToDevice) {
            System.out.println("⚠️ FASORES - Ya hay conexión activa, evitando duplicado");
            return;
        }

        executor.execute(() -> {
            try {
                // ✅ CERRAR RECURSOS ANTERIORES CORRECTAMENTE
                disconnectFromDeviceInternal();

                System.out.println("🔗 FASORES - Iniciando nueva conexión a " + deviceIp + ":" + devicePort);

                // ✅ NUEVA CONEXIÓN CON CONFIGURACIÓN MEJORADA
                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(deviceIp, devicePort), 10000); // 10s timeout
                socket.setSoTimeout(15000); // 15 segundos timeout para lectura
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);
                socket.setReuseAddress(true);

                // ✅ OBTENER AMBOS STREAMS CORRECTAMENTE
                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream(); // ✅ ESTA LÍNEA FALTABA

                System.out.println("✅ FASORES - Streams obtenidos (InputStream + OutputStream)");

                handler.post(() -> {
                    isConnectedToDevice = true;
                    showToast("✅ Conectado a " + deviceIp);
                    System.out.println("✅ FASORES - Conexión independiente establecida");

                    // Iniciar hilo de recepción mejorado
                    startIndependentReceiveThreadImproved();

                    // ✅ INICIAR SETUP AUTOMÁTICO
                    handler.postDelayed(() -> performAutomaticSetup(), 500);
                });

            } catch (java.net.ConnectException e) {
                System.out.println("❌ FASORES - No se pudo conectar: " + e.getMessage());
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Sin conexión al dispositivo");
                });
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("❌ FASORES - Timeout de conexión: " + e.getMessage());
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                });
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error de conexión: " + e.getMessage());
                e.printStackTrace();
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                });
            }
        });
    }




    private void startIndependentReceiveThreadImproved() {
        executor.execute(() -> {
            byte[] buffer = new byte[2048];
            System.out.println("🔄 FASORES - Hilo de recepción mejorado iniciado");

            try {
                while (isConnectedToDevice && !Thread.currentThread().isInterrupted()) {
                    try {
                        // ✅ VERIFICAR QUE EL SOCKET Y STREAMS SIGAN VÁLIDOS
                        if (socket == null || socket.isClosed() || !socket.isConnected()) {
                            System.out.println("❌ FASORES - Socket desconectado");
                            break;
                        }

                        if (inputStream == null) {
                            System.out.println("❌ FASORES - InputStream es null");
                            break;
                        }

                        // ✅ LECTURA BLOQUEANTE CON TIMEOUT
                        int bytesRead = inputStream.read(buffer);

                        if (bytesRead > 0) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);

                            String hexString = OctoNetCommandEncoder.bytesToHexString(data);
                            System.out.println("📨 FASORES - Datos recibidos (" + bytesRead + " bytes): " + hexString);

                            handler.post(() -> processReceivedDataIndependent(data));

                        } else if (bytesRead == -1) {
                            System.out.println("❌ FASORES - Socket cerrado por el servidor (EOF)");
                            break;
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        // ✅ TIMEOUT NORMAL - NO ES ERROR
                        System.out.println("⏰ FASORES - Timeout de lectura (normal, reintentando...)");
                        continue;

                    } catch (java.net.SocketException e) {
                        if (isConnectedToDevice) {
                            System.out.println("🔌 FASORES - Socket desconectado: " + e.getMessage());
                            break;
                        }
                    } catch (IOException e) {
                        if (isConnectedToDevice) {
                            System.out.println("❌ FASORES - Error en recepción: " + e.getMessage());
                            e.printStackTrace();
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error general en recepción: " + e.getMessage());
                e.printStackTrace();
            }

            // ✅ MANEJO CORRECTO DE DESCONEXIÓN
            System.out.println("🔚 FASORES - Hilo de recepción terminado");

            if (isConnectedToDevice) {
                handler.post(() -> {
                    isConnectedToDevice = false;
                    configurationSynced = false;
                    setControlsEnabled(false);
                    showToast("🔌 Conexión perdida");

                    // ✅ RECONECTAR AUTOMÁTICAMENTE DESPUÉS DE 3 SEGUNDOS
                    handler.postDelayed(() -> {
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
    }

    private void disconnectFromDeviceInternal() {
        try {
            // ✅ 1. CERRAR INPUTSTREAM PRIMERO
            if (inputStream != null) {
                try {
                    inputStream.close();
                    System.out.println("✅ FASORES - InputStream cerrado");
                } catch (IOException e) {
                    System.out.println("⚠️ FASORES - Error cerrando InputStream: " + e.getMessage());
                } finally {
                    inputStream = null;
                }
            }

            // ✅ 2. CERRAR OUTPUTSTREAM
            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                    System.out.println("✅ FASORES - OutputStream cerrado");
                } catch (IOException e) {
                    System.out.println("⚠️ FASORES - Error cerrando OutputStream: " + e.getMessage());
                } finally {
                    outputStream = null;
                }
            }

            // ✅ 3. CERRAR SOCKET
            if (socket != null) {
                try {
                    if (!socket.isClosed()) {
                        socket.close();
                        System.out.println("✅ FASORES - Socket cerrado");
                    }
                } catch (IOException e) {
                    System.out.println("⚠️ FASORES - Error cerrando Socket: " + e.getMessage());
                } finally {
                    socket = null;
                }
            }

            System.out.println("✅ FASORES - Recursos liberados correctamente");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error en desconexión: " + e.getMessage());
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

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                }
            }, 5000);

        } catch (Exception e) {
            isWaitingResponse = false;
            configurationSynced = true;
            setControlsEnabled(true);
        }
    }

    private void requestCurrentData() {
        System.out.println("📤 FASORES - === SOLICITUD NODE_CURRENT ===");
        System.out.println("   isConnectedToDevice: " + isConnectedToDevice);
        System.out.println("   isWaitingResponse: " + isWaitingResponse);

        if (!isConnectedToDevice) {
            System.out.println("❌ FASORES - Sin conexión, abortando");
            return;
        }

        // ✅ VERIFICAR ESTADO DEL SOCKET
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            System.out.println("❌ FASORES - Socket no válido, reconectando...");
            connectToDeviceIndependent();
            return;
        }

        // ✅ SI HAY UNA RESPUESTA PENDIENTE, RESETEAR DESPUÉS DE 2 SEGUNDOS
        if (isWaitingResponse) {
            System.out.println("⏳ FASORES - Comando anterior en espera, programando reset...");

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    System.out.println("⚠️ FASORES - Forzando reset de isWaitingResponse (timeout 2s)");
                    isWaitingResponse = false;
                }
            }, 2000);

            return; // NO enviar nuevo comando mientras esperamos
        }

        try {
            byte[] command = OctoNetCommandEncoder.createNodeCurrentReadCommand();

            if (!OctoNetCommandEncoder.verifyChecksum(command)) {
                System.out.println("❌ FASORES - Checksum incorrecto en NODE_CURRENT");
                return;
            }

            String hex = OctoNetCommandEncoder.bytesToHexString(command);
            System.out.println("✅ FASORES - Comando NODE_CURRENT generado: " + hex);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            System.out.println("   ✓ Comando enviado, isWaitingResponse = true");

            // ✅ TIMEOUT DE 3 SEGUNDOS PARA NODE_CURRENT
            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    System.out.println("⏰ FASORES - Timeout NODE_CURRENT (3s), reseteando flag");
                    isWaitingResponse = false;
                }
            }, 3000);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error solicitando NODE_CURRENT: " + e.getMessage());
            e.printStackTrace();
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
                }
            } else {
                System.out.println("⚠️ FASORES - Tipo de respuesta desconocido: 0x" +
                        Integer.toHexString(responseType));
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error crítico: " + e.getMessage());
            e.printStackTrace();
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

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error crítico Device ID: " + e.getMessage());
            e.printStackTrace();

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
                lastReadWifiSettings = new OctoNetCommandEncoder.WiFiSettings();
                return;
            }

            lastReadWifiSettings = OctoNetCommandEncoder.processWiFiSettingsResponse(wifiData);

            if (lastReadWifiSettings != null) {
                System.out.println("✅ FASORES - WiFi guardado:");
                System.out.println("   SSID: '" + lastReadWifiSettings.ssid + "'");
                System.out.println("   IP: '" + lastReadWifiSettings.ip + "'");
                System.out.println("   MAC: '" + lastReadWifiSettings.mac + "'");
            } else {
                System.out.println("❌ FASORES - Error procesando WiFi");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Excepción WiFi: " + e.getMessage());
            e.printStackTrace();
            lastReadWifiSettings = new OctoNetCommandEncoder.WiFiSettings();
        }
    }

    // =========================================================================
    // ===== PROCESAMIENTO DE CONFIGURACIÓN ===================================
    // =========================================================================

    private void processConfigurationResponseIndependent(byte[] response) {
        try {
            System.out.println("🔍 FASORES - Procesando NODE_SETTINGS...");

            byte[] configData = OctoNetCommandEncoder.extractCommandData(response);

            if (configData == null || configData.length < 4) {
                System.out.println("❌ FASORES - Datos de configuración insuficientes");
                configurationSynced = true;
                setControlsEnabled(true);
                setSpinnersEnabled(true);
                return;
            }

            recordingConfig = (configData[0] & 0xFF) == 1;
            periodConfig = configData[1] & 0xFF;
            sensorsConfig = configData[2] & 0xFF;
            meteringTypeConfig = configData[3] & 0xFF;

            skipSpinnerEvents = true;

            isRecording = recordingConfig;
            handler.post(() -> {
                if (btnRec != null) {
                    btnRec.setBackgroundResource(
                            isRecording ? R.drawable.button_rec_recording : R.drawable.button_rec
                    );

                    if (isRecording) {
                        startRecordingAnimation();
                    } else {
                        stopRecordingAnimation();
                    }
                }
            });

            configurationSynced = true;
            setControlsEnabled(true);
            setSpinnersEnabled(true);

            // ✅ MAPEO INVERSO SIN 400A
            int spinnerIndex;
            switch (sensorsConfig) {
                case 1:
                    spinnerIndex = 0; // 50A
                    break;
                case 2:
                    spinnerIndex = 1; // 200A
                    break;
                case 4:
                    spinnerIndex = 2; // 1000A
                    break;
                case 5:
                    spinnerIndex = 3; // 3000A
                    break;
                default:
                    spinnerIndex = 0;
                    break;
            }

            if (spinnerIndex >= 0 && spinnerIndex < spinnerAmperes.getAdapter().getCount()) {
                spinnerAmperes.setSelection(spinnerIndex);
                updateAmperesRange(spinnerIndex);
            } else {
                spinnerAmperes.setSelection(0);
                updateAmperesRange(0);
            }

            meteringTypeConfig = 3;
            tipoCableado = 3;
            spinnerCableado.setSelection(0);

            skipSpinnerEvents = false;

            updateDiagram();

            System.out.println("✅ FASORES - Sincronización completa");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando configuración: " + e.getMessage());
            e.printStackTrace();

            skipSpinnerEvents = true;
            if (spinnerAmperes != null && spinnerAmperes.getAdapter() != null) {
                spinnerAmperes.setSelection(0);
                updateAmperesRange(0);
            }
            skipSpinnerEvents = false;

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
            System.out.println("⚡ FASORES - === PROCESANDO NODE_CURRENT ===");

            // ✅ VERIFICAR LONGITUD MÍNIMA
            if (response == null || response.length < 4) {
                System.out.println("❌ FASORES - Respuesta muy corta: " + (response != null ? response.length : 0) + " bytes");
                return;
            }

            // ✅ EXTRAER SIZE DEL HEADER
            int sizeFromDevice = response[3] & 0xFF;
            System.out.println("📊 FASORES - SIZE del header: " + sizeFromDevice);

            if (sizeFromDevice == 0) {
                System.out.println("❌ FASORES - Dispositivo envió SIZE=0 (sin datos)");
                return;
            }

            // ✅ CALCULAR TAMAÑO REAL DE DATOS (SIZE + 1)
            int realDataSize = sizeFromDevice + 1;
            System.out.println("🔧 FASORES - Tamaño real de datos: " + sizeFromDevice + " + 1 = " + realDataSize + " bytes");

            // ✅ VERIFICAR QUE TENGAMOS SUFICIENTES BYTES
            int expectedTotal = 4 + realDataSize + 2; // HEADER(4) + DATA(64) + CHECKSUM(2)
            if (response.length < expectedTotal) {
                System.out.println("❌ FASORES - Respuesta incompleta:");
                System.out.println("   Recibidos: " + response.length + " bytes");
                System.out.println("   Necesarios: " + expectedTotal + " bytes");
                return;
            }

            // ✅ EXTRAER LOS 64 BYTES DE DATOS (sin checksum)
            byte[] energyData = new byte[realDataSize];
            System.arraycopy(response, 4, energyData, 0, realDataSize);

            String hexEnergyData = OctoNetCommandEncoder.bytesToHexString(energyData);
            System.out.println("📊 FASORES - Energy data extraída (" + energyData.length + " bytes):");
            System.out.println("   " + hexEnergyData);

            // ✅ PROCESAR ESTRUCTURA DE 64 BYTES
            if (energyData.length >= 64) {
                System.out.println("✅ FASORES - Estructura completa de 64 bytes detectada");

                // ✅ BYTE 0: ID del tipo de medición
                int id = energyData[0] & 0xFF;
                String tipoMedicion = (id == 0xF3) ? "Fuente Trifásica" :
                        (id == 0xC3) ? "Carga Trifásica" :
                                String.format("Tipo: 0x%02X", id);
                System.out.println("   📌 ID: " + tipoMedicion);

                // ✅ BYTES 2-7: Fecha y hora
                if (energyData.length >= 8) {
                    int year = 2000 + (energyData[2] & 0xFF);
                    int month = energyData[3] & 0xFF;
                    int day = energyData[4] & 0xFF;
                    int hour = energyData[5] & 0xFF;
                    int minute = energyData[6] & 0xFF;
                    int second = energyData[7] & 0xFF;
                    System.out.printf("   📅 Fecha/Hora: %02d/%02d/%04d %02d:%02d:%02d%n",
                            day, month, year, hour, minute, second);
                }

                // ✅ PROCESAR LOS 3 CANALES
                System.out.println("🔍 FASORES - Procesando canales:");

                // CH1: Bytes 28-39 (12 bytes)
                if (energyData.length >= 40) {
                    processChannel64Bytes(energyData, 28, 0, "CH1");
                }

                // CH2: Bytes 40-51 (12 bytes)
                if (energyData.length >= 52) {
                    processChannel64Bytes(energyData, 40, 1, "CH2");
                }

                // CH3: Bytes 52-63 (12 bytes)
                if (energyData.length >= 64) {
                    processChannel64Bytes(energyData, 52, 2, "CH3");
                }

                // ✅ VERIFICAR QUE LOS DATOS SEAN VÁLIDOS
                boolean hasValidData = false;
                for (int i = 0; i < 3; i++) {
                    if (Float.isNaN(voltajes[i]) || Float.isInfinite(voltajes[i])) {
                        System.out.println("⚠️ FASORES - CH" + (i+1) + " Voltaje inválido, reseteando a 0");
                        voltajes[i] = 0.0f;
                    }
                    if (Float.isNaN(corrientes[i]) || Float.isInfinite(corrientes[i])) {
                        System.out.println("⚠️ FASORES - CH" + (i+1) + " Corriente inválida, reseteando a 0");
                        corrientes[i] = 0.0f;
                    }
                    if (Float.isNaN(potencias[i]) || Float.isInfinite(potencias[i])) {
                        System.out.println("⚠️ FASORES - CH" + (i+1) + " Potencia inválida, reseteando a 0");
                        potencias[i] = 0.0f;
                    }
                    if (Float.isNaN(frecuencias[i]) || Float.isInfinite(frecuencias[i])) {
                        System.out.println("⚠️ FASORES - CH" + (i+1) + " Frecuencia inválida, usando 50Hz");
                        frecuencias[i] = 50.0f;
                    }
                    if (Float.isNaN(angulos[i]) || Float.isInfinite(angulos[i])) {
                        System.out.println("⚠️ FASORES - CH" + (i+1) + " Ángulo inválido, usando " + (i * 120) + "°");
                        angulos[i] = i * 120.0f;
                    }

                    // Verificar si hay datos válidos
                    if (voltajes[i] > 0.1f || corrientes[i] > 0.01f || potencias[i] > 0.1f) {
                        hasValidData = true;
                    }
                }

                // ✅ MOSTRAR VALORES FINALES
                System.out.println("📊 FASORES - Valores procesados:");
                for (int i = 0; i < 3; i++) {
                    System.out.printf("   CH%d: V=%.1f, A=%.2f, W=%.1f, Hz=%.1f, Ang=%.1f°%n",
                            i+1, voltajes[i], corrientes[i], potencias[i], frecuencias[i], angulos[i]);
                }

                if (!hasValidData) {
                    System.out.println("⚠️ FASORES - Todos los valores son cero, usando ángulos por defecto");
                    for (int i = 0; i < 3; i++) {
                        if (angulos[i] == 0.0f && i > 0) {
                            angulos[i] = i * 120.0f;
                        }
                        if (frecuencias[i] == 0.0f) {
                            frecuencias[i] = 50.0f;
                        }
                    }
                }

                System.out.println("🔄 FASORES - Actualizando interfaz...");
                handler.post(() -> {
                    updateTextViews();
                    updateChannelLabels();
                    updateFasores();
                });

                contadorMuestras++;
                System.out.println("✅ FASORES - Muestra #" + contadorMuestras + " procesada exitosamente");

            } else {
                System.out.println("❌ FASORES - Estructura incompleta (" + energyData.length + " bytes)");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando NODE_CURRENT: " + e.getMessage());
            e.printStackTrace();
        }
    }




    private void processChannel64Bytes(byte[] data, int offset, int channelIndex, String channelName) {
        try {
            System.out.println("🔍 FASORES - Procesando " + channelName + " en offset " + offset);

            // ✅ W_CHx (Int32 Little Endian) - Bytes 0-3
            if (data.length >= offset + 4) {
                long powerRaw = readInt32LE(data, offset);
                float powerW = (int)powerRaw * 0.1f;
                potencias[channelIndex] = powerW;
                System.out.printf("   %s Power: %d raw -> %.1f W%n", channelName, (int)powerRaw, potencias[channelIndex]);
            }

            // ✅ V_CHx (UInt16 Little Endian) - Bytes 4-5
            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16LE(data, offset + 4);
                float voltageV = voltageRaw * 0.1f;
                voltajes[channelIndex] = voltageV;
                System.out.printf("   %s Voltage: %d raw -> %.1f V%n", channelName, voltageRaw, voltajes[channelIndex]);
            }

            // ✅ A_CHx (UInt16 Little Endian) - Bytes 6-7
            if (data.length >= offset + 8) {
                int currentRaw = readUInt16LE(data, offset + 6);
                float currentA = currentRaw * 0.1f;
                corrientes[channelIndex] = currentA;
                System.out.printf("   %s Current: %d raw -> %.2f A%n", channelName, currentRaw, corrientes[channelIndex]);
            }

            // ✅ HZ_CHx (UInt16 Little Endian) - Bytes 8-9
            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16LE(data, offset + 8);
                float freqHz = frequencyRaw * 0.1f;
                frecuencias[channelIndex] = freqHz;
                System.out.printf("   %s Frequency: %d raw -> %.1f Hz%n", channelName, frequencyRaw, frecuencias[channelIndex]);
            }

            // ✅ ANGLE_CHx (UInt16 Little Endian) - Bytes 10-11
            if (data.length >= offset + 12) {
                int angleRaw = readUInt16LE(data, offset + 10);
                float angleDeg = angleRaw * 0.1f;
                angulos[channelIndex] = angleDeg;
                System.out.printf("   %s Angle: %d raw -> %.1f° (del dispositivo)%n", channelName, angleRaw, angulos[channelIndex]);
            } else {
                // Por defecto si no hay datos de ángulo
                angulos[channelIndex] = channelIndex * 120.0f;
                System.out.printf("   %s Angle: %.1f° (por defecto)%n", channelName, angulos[channelIndex]);
            }

            System.out.println("✅ FASORES - " + channelName + " procesado correctamente");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando " + channelName + ": " + e.getMessage());
            e.printStackTrace();

            // ✅ VALORES POR DEFECTO EN CASO DE ERROR
            voltajes[channelIndex] = 0.0f;
            corrientes[channelIndex] = 0.0f;
            potencias[channelIndex] = 0.0f;
            frecuencias[channelIndex] = 50.0f;
            angulos[channelIndex] = channelIndex * 120.0f;
        }
    }

    private void updateDisplayWithRealData() {
        try {
            System.out.println("🔄 FASORES - Actualizando display con datos reales");

            // ✅ EJECUTAR EN EL HILO DE UI
            handler.post(() -> {
                try {
                    // ✅ ACTUALIZAR DISPLAYS DE CADA FASE
                    updatePhaseDisplay(0, voltajes[0], corrientes[0], potencias[0], frecuencias[0]);
                    updatePhaseDisplay(1, voltajes[1], corrientes[1], potencias[1], frecuencias[1]);
                    updatePhaseDisplay(2, voltajes[2], corrientes[2], potencias[2], frecuencias[2]);

                    // ✅ INDICADOR VISUAL DE ACTIVIDAD (timestamp)
                    if (tvCH1 != null) {
                        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss",
                                java.util.Locale.getDefault()).format(new java.util.Date());
                        tvCH1.setText("L1-N - " + timestamp);
                    }

                    System.out.println("✅ FASORES - Display actualizado correctamente");

                } catch (Exception e) {
                    System.out.println("❌ FASORES - Error actualizando UI: " + e.getMessage());
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error en updateDisplayWithRealData: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updatePhaseDisplay(int phase, float volt, float corr, float power, float freq) {
        TextView tvV, tvA, tvW, tvHz, tvPF;

        switch (phase) {
            case 1:
                tvV = tvV2; tvA = tvA2; tvW = tvW2; tvHz = tvHz2; tvPF = tvpF2;
                break;
            case 2:
                tvV = tvV3; tvA = tvA3; tvW = tvW3; tvHz = tvHz3; tvPF = tvpF3;
                break;
            default:
                tvV = tvV1; tvA = tvA1; tvW = tvW1; tvHz = tvHz1; tvPF = tvpF1;
                break;
        }

        // ✅ MOSTRAR VALORES (incluso si son cero)
        if (tvV != null) tvV.setText(String.format("%.1f V", volt));
        if (tvA != null) tvA.setText(String.format("%.2f A", corr));
        if (tvW != null) tvW.setText(String.format("%.1f W", power));
        if (tvHz != null) tvHz.setText(String.format("%.1f Hz", freq));

        // ✅ CALCULAR Y MOSTRAR ÁNGULO/FACTOR DE POTENCIA
        if (tvPF != null && phase >= 0 && phase < angulos.length) {
            float deviceAngle = angulos[phase];

            // Normalizar a rango -180° a +180°
            while (deviceAngle > 180f) deviceAngle -= 360f;
            while (deviceAngle < -180f) deviceAngle += 360f;

            // Mostrar ángulo normalizado
            tvPF.setText(String.format("%.0f°", deviceAngle));
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
            // ✅ ORDEN NORMAL - Los ángulos de ejes ya están corregidos
            fasorVoltaje.setMagnitudes(voltajes[0], voltajes[1], voltajes[2]);
            fasorVoltaje.setAngles(0.0f, 120.0f, 240.0f); // Se mapearán a ejes corregidos

            fasorCorriente.setMagnitudes(corrientes[0], corrientes[1], corrientes[2]);
            fasorCorriente.setAngles(angulos[0], angulos[1], angulos[2]);

            System.out.println("🔄 FASORES - Fasores actualizados:");
            System.out.printf("   L1 (0°/BLANCO): V=%.1f, A=%.2f∠%.1f°%n",
                    voltajes[0], corrientes[0], angulos[0]);
            System.out.printf("   L2 (240°/ROJO): V=%.1f, A=%.2f∠%.1f°%n",
                    voltajes[1], corrientes[1], angulos[1]);
            System.out.printf("   L3 (120°/AZUL): V=%.1f, A=%.2f∠%.1f°%n",
                    voltajes[2], corrientes[2], angulos[2]);

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

        if (tvCH1 != null) tvCH1.setText("L1");
        if (tvCH2 != null) tvCH2.setText("L2");
        if (tvCH3 != null) tvCH3.setText("L3");
    }

    // =========================================================================
    // ===== CONTROL DE ADQUISICIÓN ===========================================
    // =========================================================================

    private void startDataAcquisition() {
        System.out.println("▶️ FASORES - Intentando iniciar adquisición...");
        System.out.println("   isConnectedToDevice: " + isConnectedToDevice);
        System.out.println("   configurationSynced: " + configurationSynced);
        System.out.println("   autoReadEnabled: " + autoReadEnabled);

        // ✅ VALIDACIONES
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            System.out.println("❌ FASORES - Sin conexión");
            return;
        }

        if (!configurationSynced) {
            System.out.println("⏳ FASORES - Configuración no sincronizada");
            return;
        }

        if (autoReadEnabled) {
            System.out.println("⚠️ FASORES - Ya está en modo adquisición");
            return;
        }

        try {
            // ✅ 1. MARCAR COMO INICIADO
            autoReadEnabled = true;
            tiempoInicio = System.currentTimeMillis();
            contadorMuestras = 0;

            System.out.println("✅ FASORES - Flags actualizados:");
            System.out.println("   autoReadEnabled: " + autoReadEnabled);
            System.out.println("   tiempoInicio: " + tiempoInicio);

            // ✅ 2. DESHABILITAR SPINNERS DURANTE ADQUISICIÓN
            setSpinnersEnabled(false);
            System.out.println("   ✓ Spinners deshabilitados");

            // ✅ 3. CAMBIAR ICONO A PAUSE
            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause);
                System.out.println("   ✓ Icono cambiado a PAUSE");
            } else {
                System.out.println("⚠️ FASORES - btnPlay es NULL");
            }

            // ✅ 4. MOSTRAR TOAST
            showToast("▶️ Midiendo");

            // ✅ 5. PRIMERA LECTURA INMEDIATA
            System.out.println("📤 FASORES - Solicitando primera lectura inmediata...");
            requestCurrentData();

            // ✅ 6. PROGRAMAR LECTURAS PERIÓDICAS
            if (autoReadHandler != null && autoReadTask != null) {
                autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);
                System.out.println("✅ FASORES - Auto-read programado cada " + AUTO_READ_INTERVAL + "ms");
            } else {
                System.out.println("❌ FASORES - autoReadHandler o autoReadTask es NULL");
                autoReadEnabled = false;
                if (btnPlay != null) {
                    btnPlay.setImageResource(android.R.drawable.ic_media_play);
                }
            }

            System.out.println("✅ FASORES - Adquisición iniciada exitosamente");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error iniciando adquisición: " + e.getMessage());
            e.printStackTrace();

            // ✅ REVERTIR ESTADO EN CASO DE ERROR
            autoReadEnabled = false;
            setSpinnersEnabled(true);

            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void stopDataAcquisition() {
        System.out.println("⏹️ FASORES - Deteniendo adquisición de datos");

        // ✅ 1. MARCAR COMO DETENIDO PRIMERO
        autoReadEnabled = false;

        try {
            // ✅ 2. DETENER AUTO-READ TASK
            if (autoReadHandler != null && autoReadTask != null) {
                autoReadHandler.removeCallbacks(autoReadTask);
                System.out.println("   ✓ Auto-read task detenido");
            }

            // ✅ 3. LIMPIAR HANDLER PRINCIPAL
            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
                System.out.println("   ✓ Handler principal limpiado");
            }

            // ✅ 4. HABILITAR SPINNERS
            setSpinnersEnabled(true);
            System.out.println("   ✓ Spinners habilitados");

            // ✅ 5. CAMBIAR ICONO DEL BOTÓN A PLAY
            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
                System.out.println("   ✓ Icono cambiado a PLAY");
            }

            // ✅ 6. RESETEAR isWaitingResponse
            isWaitingResponse = false;

            // ✅ 7. MOSTRAR TOAST
            showToast("⏹️ Detenido");

            System.out.println("✅ FASORES - Adquisición detenida correctamente");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error deteniendo adquisición: " + e.getMessage());
            e.printStackTrace();

            // ✅ ASEGURAR QUE EL BOTÓN SE CAMBIE AUNQUE HAYA ERROR
            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
            }

            showToast("⏹️ Detenido");
        }
    }

    // =========================================================================
    // ===== DIAGRAMA =========================================================
    // =========================================================================

    private void updateDiagram() {
        if (imageDiagram == null) return;

        int diagramResource;

        switch (rangoAmperes) {
            case 50:
                diagramResource = R.drawable.a50;
                System.out.println("📊 FASORES - Mostrando diagrama: a50.jpg (50A)");
                break;

            case 200:
                diagramResource = R.drawable.a200;
                System.out.println("📊 FASORES - Mostrando diagrama: a200.jpg (200A)");
                break;

            case 1000:
            case 3000:
                diagramResource = R.drawable.a1000;
                System.out.println("📊 FASORES - Mostrando diagrama: a1000.jpg (" + rangoAmperes + "A)");
                break;

            default:
                diagramResource = R.drawable.a50;
                System.out.println("⚠️ FASORES - Rango desconocido (" + rangoAmperes + "A), usando a50.jpg");
                break;
        }

        imageDiagram.setImageResource(diagramResource);
    }

    // =========================================================================
    // ===== UTILIDADES =======================================================
    // =========================================================================

    private void setControlsEnabled(boolean enabled) {
        handler.post(() -> {
            if (btnPlay != null) btnPlay.setEnabled(enabled);
            if (btnRec != null) btnRec.setEnabled(enabled);
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
        stopRecordingAnimation();
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
        stopRecordingAnimation();

        System.out.println("🔚 FASORES - onDestroy llamado");

        // ✅ DETENER ACTUALIZACIONES
        stopCurrentDataUpdates();

        // ✅ MARCAR COMO DESCONECTADO
        isConnectedToDevice = false;
        isWaitingResponse = false;
        configurationSynced = false;


        // ✅ CERRAR EXECUTOR
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            System.out.println("   Executor cerrado");
        }
    }
}