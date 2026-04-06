package de.kai_morich.simple_bluetooth_terminal;

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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
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
    private boolean userRequestedWifiScan = false;

    // ===== UI ELEMENTS =====
    private Spinner spinnerCableado, spinnerAmperes;
    private ImageView imageDiagram;
    private ImageButton btnPlay, btnRec;
    private LinearLayout btnBackToMenu;
    private Button btnDeviceId, btnConfigWifi;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRecording = false;

    // ===== FASORES =====
    private FasorView fasorVoltaje, fasorCorriente;

    // ===== MODALES =====
    private View diagramModal;
    private ImageView modalDiagramImage;
    private boolean isModalVisible = false;

    private View fasorModal;
    private FasorView modalFasorVoltaje, modalFasorCorriente;
    private boolean isFasorModalVisible = false;

    private View infoTableModal;
    private LinearLayout infoTableContainer;
    private boolean isInfoTableModalVisible = false;

    // TextViews para mediciones
    private TextView tvV1, tvV2, tvV3, tvA1, tvA2, tvA3, tvW1, tvW2, tvW3, tvHz1, tvHz2, tvHz3;
    private TextView tvpF1, tvpF2, tvpF3;
    private TextView tvCH1, tvCH2, tvCH3;

    // ===== CONEXIÓN TCP =====
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ExecutorService executor;
    private String deviceIp = "192.168.4.1";
    private int devicePort = 333;
    private boolean isConnectedToDevice = false;

    // ===== CONFIGURACIÓN =====
    private int periodConfig = 1;           // 1 = 5 MINUTOS
    private int sensorsConfig = 2;          // 2 = CT-200A (valor inicial)
    private int meteringTypeConfig = 2;     // 2 = 3PH, 3W
    private boolean recordingConfig = false;
    // ===== ESTADO =====
    private volatile boolean shouldStopReceiving = false;
    private int rangoAmperes = 200;         // Valor inicial en la UI
    private int tipoCableado = 2;

    // ===== AUTO-LECTURA =====
    private boolean autoReadEnabled = false;
    private static final int AUTO_READ_INTERVAL = 5000;
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
    private int contadorMuestras = 0;
    private boolean isWaitingResponse = false;
    private boolean configurationSynced = false;
    private boolean skipSpinnerEvents = false;

    // Agregar junto a:  private ImageView modalDiagramImage;
    private ZoomableImageView zoomableModalImage;

    private DeviceIdInfo lastReadDeviceIdInfo;

    private static class DeviceIdInfo {
        String serial = "N/A", facDate = "N/A", facHour = "N/A",
                actCode = "N/A", hwVersion = "N/A", fwVersion = "N/A";
    }

    // ===== HELPERS =====
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

        handler.postDelayed(() -> {
            connectToDeviceIndependent();
            handler.postDelayed(() -> {
                if (isConnectedToDevice) {
                    sendTimeWriteCommand();
                }
            }, 2000);
        }, 500);
    }

    // =========================================================================
    // ===== INICIALIZACIÓN ===================================================
    // =========================================================================

    private void initializeViews() {
        spinnerCableado = findViewById(R.id.spinnerCableado);
        spinnerAmperes = findViewById(R.id.spinnerAmperes);
        imageDiagram = findViewById(R.id.imageDiagram);
        btnRec = findViewById(R.id.btnRec);
        btnPlay = findViewById(R.id.btnPlay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);
        btnDeviceId = findViewById(R.id.btnDeviceId);
        btnConfigWifi = findViewById(R.id.btnConfigWifi);

        if (btnRec != null) {
            btnRec.setImageResource(android.R.drawable.presence_invisible);
            recordingConfig = false;
        }

        if (btnPlay != null) {
            btnPlay.setImageResource(R.drawable.ic_play);
        }

        fasorVoltaje = findViewById(R.id.fasorVoltaje);
        fasorCorriente = findViewById(R.id.fasorCorriente);

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

        // ✅ CONFIGURAR LISTENERS PARA MODALES
        View fasorVoltajeContainer = findViewById(R.id.fasorVoltajeContainer);
        if (fasorVoltajeContainer != null) {
            fasorVoltajeContainer.setOnClickListener(v -> showFasorModal());
        }

        View fasorCorrienteContainer = findViewById(R.id.fasorCorrienteContainer);
        if (fasorCorrienteContainer != null) {
            fasorCorrienteContainer.setOnClickListener(v -> showFasorModal());
        }

        View infoTableContainerView = findViewById(R.id.infoTableContainer);
        if (infoTableContainerView != null) {
            infoTableContainerView.setOnClickListener(v -> showInfoTableModal());
        }
    }

    @SuppressLint("AppCompatCustomView")
    private class ZoomableImageView extends ImageView {
        private final android.graphics.Matrix matrix = new android.graphics.Matrix();
        private final android.graphics.Matrix savedMatrix = new android.graphics.Matrix();

        private static final float MIN_SCALE = 0.5f;
        private static final float MAX_SCALE = 8.0f;

        private float startX = 0f, startY = 0f;
        private float midX = 0f, midY = 0f;
        private float lastSpacing = 0f;

        private static final int NONE = 0, DRAG = 1, ZOOM = 2;
        private int mode = NONE;

        public ZoomableImageView(android.content.Context context) {
            super(context);
            setScaleType(ScaleType.MATRIX);

            setOnTouchListener((v, event) -> {
                switch (event.getActionMasked()) {

                    case android.view.MotionEvent.ACTION_DOWN:
                        savedMatrix.set(matrix);
                        startX = event.getX();
                        startY = event.getY();
                        mode = DRAG;
                        break;

                    case android.view.MotionEvent.ACTION_POINTER_DOWN:
                        if (event.getPointerCount() == 2) {
                            lastSpacing = getSpacing(event);
                            midX = (event.getX(0) + event.getX(1)) / 2f;
                            midY = (event.getY(0) + event.getY(1)) / 2f;
                            savedMatrix.set(matrix);
                            mode = ZOOM;
                        }
                        break;

                    case android.view.MotionEvent.ACTION_MOVE:
                        if (mode == ZOOM && event.getPointerCount() >= 2) {
                            float newSpacing = getSpacing(event);
                            float spacingDiff = newSpacing - lastSpacing;

                            // ✅ Factor muy gradual
                            if (Math.abs(spacingDiff) > 1f) {
                                float[] values = new float[9];
                                matrix.getValues(values);
                                float currentScale = values[android.graphics.Matrix.MSCALE_X];

                                // ✅ Zoom gradual: cada pixel = muy poco cambio
                                float scaleDelta = 1f + (spacingDiff * 0.005f);

                                float newScale = currentScale * scaleDelta;

                                // ✅ Limitar escala
                                if (newScale < MIN_SCALE) scaleDelta = MIN_SCALE / currentScale;
                                else if (newScale > MAX_SCALE) scaleDelta = MAX_SCALE / currentScale;

                                matrix.postScale(scaleDelta, scaleDelta, midX, midY);
                                fixTranslation();
                                setImageMatrix(matrix);
                                savedMatrix.set(matrix);
                                lastSpacing = newSpacing; // ✅ frame a frame
                            }

                        } else if (mode == DRAG && event.getPointerCount() == 1) {
                            float dx = event.getX() - startX;
                            float dy = event.getY() - startY;

                            if (Math.abs(dx) > 1f || Math.abs(dy) > 1f) {
                                matrix.set(savedMatrix);
                                matrix.postTranslate(dx, dy);
                                fixTranslation();
                                setImageMatrix(matrix);
                            }
                        }
                        break;

                    case android.view.MotionEvent.ACTION_POINTER_UP:
                        savedMatrix.set(matrix);
                        if (event.getPointerCount() == 2) {
                            int remainingIndex = (event.getActionIndex() == 0) ? 1 : 0;
                            startX = event.getX(remainingIndex);
                            startY = event.getY(remainingIndex);
                            mode = DRAG;
                        } else {
                            mode = NONE;
                        }
                        break;

                    case android.view.MotionEvent.ACTION_UP:
                        mode = NONE;
                        break;

                    case android.view.MotionEvent.ACTION_CANCEL:
                        mode = NONE;
                        break;
                }
                return true;
            });
        }

        public void initMatrix() {
            post(() -> {
                android.graphics.drawable.Drawable d = getDrawable();
                if (d == null) return;
                float vW = getWidth(), vH = getHeight();
                if (vW == 0 || vH == 0) return;
                float dW = d.getIntrinsicWidth(), dH = d.getIntrinsicHeight();
                if (dW == 0 || dH == 0) return;

                // ✅ Al abrir: escala 1:1
                float scale = 1.0f;
                float dx = (vW - dW * scale) / 2f;
                float dy = (vH - dH * scale) / 2f;
                matrix.reset();
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                savedMatrix.set(matrix);
                setImageMatrix(matrix);
            });
        }

        public void resetMatrix() {
            post(() -> {
                android.graphics.drawable.Drawable d = getDrawable();
                if (d == null) return;
                float vW = getWidth(), vH = getHeight();
                if (vW == 0 || vH == 0) return;
                float dW = d.getIntrinsicWidth(), dH = d.getIntrinsicHeight();
                if (dW == 0 || dH == 0) return;

                // ✅ Reset: ajustar para que quepa completa
                float scale = Math.min(vW / dW, vH / dH);
                float dx = (vW - dW * scale) / 2f;
                float dy = (vH - dH * scale) / 2f;
                matrix.reset();
                matrix.setScale(scale, scale);
                matrix.postTranslate(dx, dy);
                savedMatrix.set(matrix);
                setImageMatrix(matrix);
            });
        }
        private float getSpacing(android.view.MotionEvent event) {
            float dx = event.getX(0) - event.getX(1);
            float dy = event.getY(0) - event.getY(1);
            return (float) Math.sqrt(dx * dx + dy * dy);
        }

        private void fixTranslation() {
            float[] values = new float[9];
            matrix.getValues(values);
            float currentScale = values[android.graphics.Matrix.MSCALE_X];
            float transX = values[android.graphics.Matrix.MTRANS_X];
            float transY = values[android.graphics.Matrix.MTRANS_Y];

            android.graphics.drawable.Drawable d = getDrawable();
            if (d == null) return;

            float vW = getWidth(), vH = getHeight();
            float scaledW = d.getIntrinsicWidth() * currentScale;
            float scaledH = d.getIntrinsicHeight() * currentScale;

            float fixX = 0f, fixY = 0f;

            if (scaledW <= vW) {
                fixX = (vW - scaledW) / 2f - transX;
            } else {
                if (transX > 0) fixX = -transX;
                else if (transX + scaledW < vW) fixX = vW - (transX + scaledW);
            }

            if (scaledH <= vH) {
                fixY = (vH - scaledH) / 2f - transY;
            } else {
                if (transY > 0) fixY = -transY;
                else if (transY + scaledH < vH) fixY = vH - (transY + scaledH);
            }

            matrix.postTranslate(fixX, fixY);
        }
    }

    private void setupSpinners() {
        // ✅ NUEVAS opciones de cableado
        String[] cableadoOptions = {"1-Fase 4-Cargas", "2-Fases 2-Cargas", "3-Fases 1-Carga"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        String[] amperesOptions = {"50A", "200A", "1000A", "3000A"};
        setupSpinnerAdapter(spinnerAmperes, amperesOptions);

        setupSpinnerListeners();

        skipSpinnerEvents = true;
        spinnerAmperes.setSelection(1);   // 200A por defecto
        spinnerCableado.setSelection(2);  // ✅ 3PH 4W 1-LOAD por defecto (índice 2)
        skipSpinnerEvents = false;

        setSpinnersEnabled(false);
    }

    private void setupSpinnerAdapter(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupSpinnerListeners() {

        spinnerAmperes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    System.out.println("📊 SPINNER AMPERES - posición: " + position);
                    updateAmperesRange(position); // actualiza rangoAmperes
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse) {
                            writeAmperesConfiguration();
                        }
                    }, 500);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerCableado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    System.out.println("📊 SPINNER CABLEADO - posición: " + position);

                    // ✅ Actualizar meteringTypeConfig PRIMERO
                    switch (position) {
                        case 0: meteringTypeConfig = 0; break;
                        case 1: meteringTypeConfig = 1; break;
                        case 2: meteringTypeConfig = 2; break;
                        default: meteringTypeConfig = 2; break;
                    }
                    tipoCableado = meteringTypeConfig;

                    // ✅ Actualizar imagen DESPUÉS de actualizar meteringTypeConfig
                    updateDiagram();

                    handler.postDelayed(() -> {
                        if (!isWaitingResponse) {
                            writeCableadoConfiguration();
                        }
                    }, 500);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

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

    private void setupAutoReadTask() {
        if (autoReadHandler == null) {
            autoReadHandler = new Handler(Looper.getMainLooper());
        }

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
    // ===== WIFI =============================================================
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

    private void showNetworkScanForValidation() {
        if (!isConnectedToDevice) {
            showToast("❌ Debes estar conectado al medidor");
            return;
        }

        if (wifiManager != null && wifiManager.isWifiEnabled()) {
            @SuppressLint("MissingPermission")
            String currentSsid = wifiManager.getConnectionInfo().getSSID().replace("\"", "");

            if (!currentSsid.startsWith("ESP")) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("⚠️ Advertencia")
                        .setMessage("No estás conectado a la red del medidor.\n\n¿Continuar?")
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

        for (ScanResult result : availableNetworks) {
            if (result.SSID != null && !result.SSID.isEmpty() && !result.SSID.startsWith("ESP")) {
                if (is24GHzNetwork(result)) {
                    networkNames.add(result.SSID + " (2.4GHz)");
                    networkSSIDs.add(result.SSID);
                }
            }
        }

        if (networkNames.isEmpty()) {
            showToast("❌ Sin redes 2.4GHz disponibles");
            return;
        }

        // ✅ Construir vista personalizada con botón de refrescar destacado
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        // ✅ BOTÓN REFRESCAR DESTACADO
        Button btnRefrescar = new Button(this);
        btnRefrescar.setText("🔄  REFRESCAR REDES");
        btnRefrescar.setTextColor(Color.WHITE);
        btnRefrescar.setTextSize(15f);
        btnRefrescar.setTypeface(null, Typeface.BOLD);
        btnRefrescar.setAllCaps(false);

        GradientDrawable btnBackground = new GradientDrawable();
        btnBackground.setShape(GradientDrawable.RECTANGLE);
        btnBackground.setColor(Color.rgb(33, 150, 243)); // azul
        btnBackground.setCornerRadius(12f);
        btnRefrescar.setBackground(btnBackground);

        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(24, 20, 24, 8);
        btnRefrescar.setLayoutParams(btnParams);
        btnRefrescar.setPadding(0, 28, 0, 28);

        mainLayout.addView(btnRefrescar);

        // ✅ SEPARADOR CON TEXTO
        TextView separador = new TextView(this);
        separador.setText("── Redes disponibles ──");
        separador.setTextColor(Color.rgb(150, 150, 150));
        separador.setTextSize(12f);
        separador.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sepParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sepParams.setMargins(0, 8, 0, 4);
        separador.setLayoutParams(sepParams);
        mainLayout.addView(separador);

        // ✅ LISTA DE REDES
        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout listaRedes = new LinearLayout(this);
        listaRedes.setOrientation(LinearLayout.VERTICAL);
        listaRedes.setPadding(0, 0, 0, 16);

        for (int i = 0; i < networkNames.size(); i++) {
            final String ssid = networkSSIDs.get(i);

            TextView redItem = new TextView(this);
            redItem.setText("📶  " + networkNames.get(i));
            redItem.setTextSize(14f);
            redItem.setTextColor(Color.rgb(33, 33, 33));
            redItem.setPadding(48, 28, 48, 28);

            // alternar fondo para mejor legibilidad
            if (i % 2 == 0) {
                redItem.setBackgroundColor(Color.rgb(245, 245, 245));
            } else {
                redItem.setBackgroundColor(Color.WHITE);
            }

            redItem.setOnClickListener(v -> {
                showPasswordDialogForValidation(ssid);
            });

            listaRedes.addView(redItem);
        }

        scrollView.addView(listaRedes);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);
        mainLayout.addView(scrollView);

        // ✅ Construir y mostrar el dialog
        final androidx.appcompat.app.AlertDialog[] dialogRef = new androidx.appcompat.app.AlertDialog[1];

        dialogRef[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("📡 Redes 2.4GHz (" + networkNames.size() + ")")
                .setView(mainLayout)
                .setNegativeButton("Cancelar", null)
                .show();

        // ✅ Acción del botón refrescar — cerrar dialog y re-escanear
        btnRefrescar.setOnClickListener(v -> {
            if (dialogRef[0] != null) dialogRef[0].dismiss();
            showToast("🔄 Buscando redes...");
            userRequestedWifiScan = true;
            wifiManager.startScan();
        });
    }

    private boolean is24GHzNetwork(ScanResult result) {
        try {
            int frequency = result.frequency;
            return frequency >= 2400 && frequency <= 2500;
        } catch (Exception e) {
            return verify24GHzBySSID(result.SSID);
        }
    }

    private boolean verify24GHzBySSID(String ssid) {
        if (ssid == null) return false;
        String ssidLower = ssid.toLowerCase();
        String[] patterns5GHz = {"_5g", "-5g", " 5g", "_5ghz", "-5ghz", "5ghz"};
        for (String pattern : patterns5GHz) {
            if (ssidLower.contains(pattern)) return false;
        }
        return true;
    }

    private void showPasswordDialogForValidation(String ssid) {
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(50, 40, 50, 40);

        final TextInputLayout textInputLayout = new TextInputLayout(this);
        textInputLayout.setHint("🔐 Contraseña WiFi (vacío = red abierta)");
        textInputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        textInputLayout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);

        final TextInputEditText editText = new TextInputEditText(textInputLayout.getContext());
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        textInputLayout.addView(editText);
        mainLayout.addView(textInputLayout);

        new MaterialAlertDialogBuilder(this)
                .setTitle("📡 Red: " + ssid)
                .setView(mainLayout)
                .setPositiveButton("✅ Validar", (dialog, which) -> {
                    String password = editText.getText().toString(); // ← Sin trim()

                    if (password.isEmpty()) {
                        // ✅ Red abierta: mostrar confirmación antes de enviar
                        new MaterialAlertDialogBuilder(this)
                                .setTitle("⚠️ Red sin contraseña")
                                .setMessage("¿Estás seguro que deseas conectar al medidor a la red:\n\n\"" + ssid + "\"\n\nsin contraseña? (red abierta)")
                                .setPositiveButton("✅ Sí, enviar sin contraseña", (d2, w2) -> {
                                    // Crear objeto con contraseña vacía para red abierta
                                    WiFiValidationModule.ValidatedNetwork openNetwork =
                                            new WiFiValidationModule.ValidatedNetwork();
                                    openNetwork.ssid = ssid;
                                    openNetwork.password = " "; // 0x20 = espacio
                                    sendWiFiCredentialsToDevice(openNetwork);
                                })
                                .setNegativeButton("❌ Cancelar", null)
                                .show();
                    } else {
                        // Red con contraseña: flujo normal de validación
                        wifiValidationModule.validateNetwork(ssid, password);
                    }
                })
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }

    private void showValidatedNetworkConfirmation(WiFiValidationModule.ValidatedNetwork network) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("📤 Enviar al Medidor")
                .setMessage("Red validada: " + network.ssid)
                .setPositiveButton("✅ Enviar", (d, w) ->
                        sendWiFiCredentialsToDevice(network))
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }

    private void sendWiFiCredentialsToDevice(WiFiValidationModule.ValidatedNetwork network) {
        // ✅ Si la contraseña es vacía o solo espacios, enviar 0x20 (un espacio)
        String passwordToSend = (network.password == null || network.password.trim().isEmpty())
                ? " "   // 0x20 en ASCII
                : network.password;

        byte[] command = OctoNetCommandEncoder.createWiFiSettingsWriteCommand(
                network.ssid, passwordToSend);
        sendTcpCommandIndependent(command);

        if (passwordToSend.equals(" ")) {
            showToast("📡 Red abierta enviada al medidor");
        } else {
            showToast("📡 Credenciales enviadas");
        }
    }

    // =========================================================================
    // ===== DEVICE ID ========================================================
    // =========================================================================

    private void showDeviceIdModal() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }

        if (lastReadDeviceIdInfo != null && !lastReadDeviceIdInfo.serial.equals("N/A")) {
            displayDeviceIdModal();
            return;
        }

        sendDeviceIdReadCommand();
        handler.postDelayed(() -> {
            if (lastReadDeviceIdInfo != null) {
                displayDeviceIdModal();
            }
        }, 2000);
    }

    private void displayDeviceIdModal() {
        if (lastReadDeviceIdInfo == null) return;

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
    }

    // =========================================================================
    // ===== CONFIGURACIÓN ====================================================
    // =========================================================================

    private void performAutomaticSetup() {
        if (!isConnectedToDevice) return;

        executor.execute(() -> {
            try {
                sendTimeWriteCommand();
                Thread.sleep(1000); // ✅ más tiempo

                sendWiFiReadCommand();
                Thread.sleep(1000); // ✅ más tiempo

                readDeviceConfigurationIndependent();
                Thread.sleep(1500); // ✅ más tiempo para config

                sendDeviceIdReadCommand(); // ✅ agregar aquí para tenerlo listo
                Thread.sleep(1000);

                handler.post(() -> {
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                    isWaitingResponse = false; // ✅ asegurar que queda en false
                    updateDiagram();           // ✅ actualizar imagen al arrancar
                });

            } catch (Exception e) {
                handler.post(() -> {
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                    isWaitingResponse = false;
                });
            }
        });
    }

    private void updateAmperesRange(int sensorIndex) {
        int[] amperesValues = {50, 200, 1000, 3000};

        if (sensorIndex >= 0 && sensorIndex < amperesValues.length) {
            rangoAmperes = amperesValues[sensorIndex];
            updateDiagram();
            System.out.println("📊 Rango UI actualizado a: " + rangoAmperes + "A");
        }
    }

    private void writeAmperesConfiguration() {
        if (!isConnectedToDevice || isWaitingResponse) return;

        try {
            int spinnerPosition = spinnerAmperes.getSelectedItemPosition();
            switch (spinnerPosition) {
                case 0: sensorsConfig = 1; rangoAmperes = 50;   break;
                case 1: sensorsConfig = 2; rangoAmperes = 200;  break;
                case 2: sensorsConfig = 4; rangoAmperes = 1000; break;
                case 3: sensorsConfig = 5; rangoAmperes = 3000; break;
                default: sensorsConfig = 2; rangoAmperes = 200; break;
            }

            System.out.println("📤 Enviando AMPERES: " + rangoAmperes + "A (sensor=" + sensorsConfig + ")");

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingConfig, periodConfig, sensorsConfig, meteringTypeConfig);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            showToast("✅ Amperes: " + rangoAmperes + "A");

            handler.postDelayed(() -> { if (isWaitingResponse) isWaitingResponse = false; }, 5000);

        } catch (Exception e) {
            isWaitingResponse = false;
        }
    }

    private void writeCableadoConfiguration() {
        if (!isConnectedToDevice || isWaitingResponse) return;

        try {
            System.out.println("📤 Enviando CABLEADO: meteringType=" + meteringTypeConfig);

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingConfig, periodConfig, sensorsConfig, meteringTypeConfig);

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            String[] nombres = {"1PH 2W 4-LOADS", "1PH 3W 2-LOAD", "3PH 4W 1-LOAD"};
            showToast("✅ Cableado: " + nombres[Math.min(meteringTypeConfig, 2)]);

            handler.postDelayed(() -> { if (isWaitingResponse) isWaitingResponse = false; }, 5000);

        } catch (Exception e) {
            isWaitingResponse = false;
        }
    }

    // =========================================================================
    // ===== GRABACIÓN ========================================================
    // =========================================================================

    private void toggleRecording() {
        isRecording = !isRecording;
        recordingConfig = isRecording;

        System.out.println("🎬 Cambio REC: " + (isRecording ? "ON" : "OFF"));

        if (isRecording) {
            btnRec.setBackgroundResource(R.drawable.button_rec_recording);
            showToast("⏺ Grabación iniciada");
            startRecordingAnimation();
        } else {
            btnRec.setBackgroundResource(R.drawable.button_rec);
            showToast("⏸ Grabación pausada");
            stopRecordingAnimation();
        }

        sendRecordingCommand(isRecording);
    }

    private void sendRecordingCommand(boolean recording) {
        if (!isConnectedToDevice || isWaitingResponse) {
            System.out.println("⚠️ No se puede cambiar REC");
            return;
        }

        try {
            // ✅ CRÍTICO: Usar sensorsConfig actual sin modificar rangoAmperes
            System.out.println("📤 Enviando REC con configuración actual:");
            System.out.println("   Recording: " + recording);
            System.out.println("   Sensor: " + sensorsConfig);
            System.out.println("   Rango UI (no cambia): " + rangoAmperes + "A");

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recording,
                    periodConfig,
                    sensorsConfig,  // ✅ Mantener sensor actual
                    meteringTypeConfig
            );

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    isWaitingResponse = false;
                }
            }, 5000);

        } catch (Exception e) {
            System.out.println("❌ Error REC: " + e.getMessage());
            isWaitingResponse = false;

            // Revertir estado visual
            isRecording = !isRecording;
            btnRec.setBackgroundResource(
                    isRecording ? R.drawable.button_rec_recording : R.drawable.button_rec
            );
        }
    }

    private void startRecordingAnimation() {
        if (btnRec == null) return;

        btnRec.animate()
                .alpha(0.7f)
                .setDuration(2000)
                .setInterpolator(new DecelerateInterpolator(2.5f))
                .withEndAction(() -> {
                    if (isRecording && btnRec != null) {
                        btnRec.animate()
                                .alpha(1.0f)
                                .setDuration(2000)
                                .setInterpolator(new AccelerateInterpolator(2.5f))
                                .withEndAction(() -> {
                                    if (isRecording) {
                                        startRecordingAnimation();
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
        btnRec.setAlpha(1.0f);
    }

    // =========================================================================
    // ===== MODALES ==========================================================
    // =========================================================================

    @SuppressLint("AppCompatCustomView")
    private void createDiagramModal() {
        if (diagramModal != null) return;

        LinearLayout modalContent = new LinearLayout(this);
        modalContent.setOrientation(LinearLayout.VERTICAL);
        modalContent.setGravity(Gravity.CENTER);
        modalContent.setPadding(40, 40, 40, 40);

        TextView modalTitle = new TextView(this);
        modalTitle.setText("Diagrama de conexión");
        modalTitle.setTextColor(Color.WHITE);
        modalTitle.setTextSize(24f);
        modalTitle.setGravity(Gravity.CENTER);
        modalTitle.setTypeface(null, Typeface.BOLD);
        modalTitle.setPadding(0, 0, 0, 10);
        modalContent.addView(modalTitle);

        TextView zoomHint = new TextView(this);
        zoomHint.setText("👆 Pellizca para zoom • Arrastra para mover");
        zoomHint.setTextColor(Color.rgb(180, 180, 180));
        zoomHint.setTextSize(11f);
        zoomHint.setGravity(Gravity.CENTER);
        zoomHint.setPadding(0, 0, 0, 16);
        modalContent.addView(zoomHint);

        // ✅ Contenedor de imagen
        FrameLayout imageContainer = new FrameLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        containerParams.weight = 1;
        containerParams.setMargins(20, 0, 20, 20);
        imageContainer.setLayoutParams(containerParams);
        imageContainer.setBackground(createRoundedBackground());
        imageContainer.setClipToOutline(true);

        // ✅ Usar ZoomableImageView
        zoomableModalImage = new ZoomableImageView(this);
        modalDiagramImage = zoomableModalImage;

        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        modalDiagramImage.setLayoutParams(imageParams);
        modalDiagramImage.setPadding(20, 20, 20, 20);
        imageContainer.addView(modalDiagramImage);
        modalContent.addView(imageContainer);

        // ✅ Fila de botones
        LinearLayout buttonsRow = new LinearLayout(this);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER);

        // Botón reset zoom
        Button resetZoomButton = new Button(this);
        resetZoomButton.setText("🔍 Reset zoom");
        resetZoomButton.setTextColor(Color.WHITE);
        resetZoomButton.setTextSize(14f);
        resetZoomButton.setTypeface(null, Typeface.BOLD);
        resetZoomButton.setAllCaps(false);
        GradientDrawable resetBg = new GradientDrawable();
        resetBg.setShape(GradientDrawable.RECTANGLE);
        resetBg.setColor(Color.rgb(33, 150, 243));
        resetBg.setCornerRadius(12f);
        resetZoomButton.setBackground(resetBg);
        resetZoomButton.setPadding(40, 20, 40, 20);
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.setMargins(0, 0, 20, 0);
        resetZoomButton.setLayoutParams(resetParams);

        // Botón cerrar
        Button closeButton = new Button(this);
        closeButton.setText("✕ Cerrar");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(14f);
        closeButton.setTypeface(null, Typeface.BOLD);
        closeButton.setAllCaps(false);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setShape(GradientDrawable.RECTANGLE);
        closeBg.setColor(Color.rgb(220, 50, 50));
        closeBg.setCornerRadius(12f);
        closeButton.setBackground(closeBg);
        closeButton.setPadding(40, 20, 40, 20);
        closeButton.setOnClickListener(v -> hideDiagramModal());

        buttonsRow.addView(resetZoomButton);
        buttonsRow.addView(closeButton);
        modalContent.addView(buttonsRow);

        // ✅ Reset zoom
        resetZoomButton.setOnClickListener(v -> {
            if (zoomableModalImage != null) {
                zoomableModalImage.resetMatrix(); // ✅ antes decía initMatrix()
            }
        });

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
        modalContent.setOnClickListener(v -> {});

        diagramModal = modalFrame;
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
        switch (meteringTypeConfig) {
            case 0: diagramResource = R.drawable.w1ph_2w_4loads; break;
            case 1: diagramResource = R.drawable.w1ph_3w_2load;  break;
            case 2: diagramResource = R.drawable.w3ph_4w_1load;  break;
            default: diagramResource = R.drawable.w3ph_4w_1load; break;
        }

        modalDiagramImage.setImageResource(diagramResource);
    }

    // ===== MODAL FASORES =====

    private void createFasorModal() {
        if (fasorModal != null) return;

        LinearLayout modalContent = new LinearLayout(this);
        modalContent.setOrientation(LinearLayout.VERTICAL);
        modalContent.setGravity(Gravity.CENTER);
        modalContent.setPadding(40, 40, 40, 40);

        TextView modalTitle = new TextView(this);
        modalTitle.setText("📊 Diagrama Fasorial Detallado");
        modalTitle.setTextColor(Color.WHITE);
        modalTitle.setTextSize(24f);
        modalTitle.setGravity(Gravity.CENTER);
        modalTitle.setTypeface(null, Typeface.BOLD);
        modalTitle.setPadding(0, 0, 0, 30);
        modalContent.addView(modalTitle);

        LinearLayout fasoresContainer = new LinearLayout(this);
        fasoresContainer.setOrientation(LinearLayout.VERTICAL);
        fasoresContainer.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        containerParams.weight = 1;
        fasoresContainer.setLayoutParams(containerParams);
        fasoresContainer.setBackgroundColor(Color.WHITE);
        fasoresContainer.setPadding(20, 20, 20, 20);
        fasoresContainer.setBackground(createRoundedBackground());

        modalFasorVoltaje = new FasorView(this);
        modalFasorVoltaje.setThreeAxisMode(true);
        modalFasorVoltaje.setTitle("Voltajes Trifásicos");
        modalFasorVoltaje.setUnit("V");
        modalFasorVoltaje.setAutoScale(true);
        modalFasorVoltaje.setVoltageMode(true);

        LinearLayout.LayoutParams fasorVoltParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        fasorVoltParams.weight = 1;
        fasorVoltParams.setMargins(0, 10, 0, 10);
        modalFasorVoltaje.setLayoutParams(fasorVoltParams);
        fasoresContainer.addView(modalFasorVoltaje);

        View divider = new View(this);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 3));
        divider.setBackgroundColor(Color.rgb(200, 200, 200));
        fasoresContainer.addView(divider);

        modalFasorCorriente = new FasorView(this);
        modalFasorCorriente.setThreeAxisMode(true);
        modalFasorCorriente.setTitle("Corrientes Trifásicas");
        modalFasorCorriente.setUnit("A");
        modalFasorCorriente.setAutoScale(true);
        modalFasorCorriente.setVoltageMode(false);

        LinearLayout.LayoutParams fasorCorrParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        fasorCorrParams.weight = 1;
        fasorCorrParams.setMargins(0, 10, 0, 10);
        modalFasorCorriente.setLayoutParams(fasorCorrParams);
        fasoresContainer.addView(modalFasorCorriente);

        modalContent.addView(fasoresContainer);

        Button closeButton = new Button(this);
        closeButton.setText("✕ Cerrar");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(18f);
        closeButton.setTypeface(null, Typeface.BOLD);
        closeButton.setBackgroundColor(Color.rgb(220, 50, 50));
        closeButton.setPadding(40, 20, 40, 20);
        closeButton.setOnClickListener(v -> hideFasorModal());

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
        modalFrame.setOnClickListener(v -> hideFasorModal());
        modalContent.setOnClickListener(v -> {
        });

        fasorModal = modalFrame;
    }

    private void showFasorModal() {
        if (isFasorModalVisible) return;

        createFasorModal();
        updateModalFasores();

        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        rootView.addView(fasorModal);

        fasorModal.setVisibility(View.VISIBLE);
        fasorModal.setAlpha(0f);
        fasorModal.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        isFasorModalVisible = true;
    }

    private void hideFasorModal() {
        if (!isFasorModalVisible || fasorModal == null) return;

        fasorModal.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    fasorModal.setVisibility(View.GONE);
                    ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                    rootView.removeView(fasorModal);
                    isFasorModalVisible = false;
                })
                .start();
    }

    private void updateModalFasores() {
        if (modalFasorVoltaje == null || modalFasorCorriente == null) return;

        try {
            modalFasorVoltaje.setMagnitudes(voltajes[0], voltajes[2], voltajes[1]);
            modalFasorVoltaje.setAngles(0.0f, 120.0f, 240.0f);

            modalFasorCorriente.setMagnitudes(corrientes[0], corrientes[1], corrientes[2]);
            modalFasorCorriente.setAngles(angulos[0], angulos[1], angulos[2]);
        } catch (Exception e) {
        }
    }

    // ===== MODAL TABLA INFO =====

    private void createInfoTableModal() {
        if (infoTableModal != null) return;

        LinearLayout modalContent = new LinearLayout(this);
        modalContent.setOrientation(LinearLayout.VERTICAL);
        modalContent.setGravity(Gravity.CENTER);
        modalContent.setPadding(40, 40, 40, 40);

        TextView modalTitle = new TextView(this);
        modalTitle.setText("📋 Información Detallada");
        modalTitle.setTextColor(Color.WHITE);
        modalTitle.setTextSize(24f);
        modalTitle.setGravity(Gravity.CENTER);
        modalTitle.setTypeface(null, Typeface.BOLD);
        modalTitle.setPadding(0, 0, 0, 30);
        modalContent.addView(modalTitle);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        scrollParams.weight = 1;
        scrollView.setLayoutParams(scrollParams);

        infoTableContainer = new LinearLayout(this);
        infoTableContainer.setOrientation(LinearLayout.VERTICAL);
        infoTableContainer.setBackgroundColor(Color.WHITE);
        infoTableContainer.setPadding(30, 30, 30, 30);
        infoTableContainer.setBackground(createRoundedBackground());

        updateInfoTableContent();

        scrollView.addView(infoTableContainer);
        modalContent.addView(scrollView);

        Button closeButton = new Button(this);
        closeButton.setText("✕ Cerrar");
        closeButton.setTextColor(Color.WHITE);
        closeButton.setTextSize(18f);
        closeButton.setTypeface(null, Typeface.BOLD);
        closeButton.setBackgroundColor(Color.rgb(220, 50, 50));
        closeButton.setPadding(40, 20, 40, 20);
        closeButton.setOnClickListener(v -> hideInfoTableModal());

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
        modalFrame.setOnClickListener(v -> hideInfoTableModal());
        modalContent.setOnClickListener(v -> {
        });

        infoTableModal = modalFrame;
    }

    private void updateInfoTableContent() {
        if (infoTableContainer == null) return;

        infoTableContainer.removeAllViews();

        for (int i = 0; i < 3; i++) {
            String phaseName = "L" + (i + 1) + "-N";
            LinearLayout phaseTable = createPhaseTable(i, phaseName);
            infoTableContainer.addView(phaseTable);

            if (i < 2) {
                View divider = new View(this);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 2);
                dividerParams.setMargins(0, 20, 0, 20);
                divider.setLayoutParams(dividerParams);
                divider.setBackgroundColor(Color.rgb(200, 200, 200));
                infoTableContainer.addView(divider);
            }
        }
    }

    private LinearLayout createPhaseTable(int phaseIndex, String phaseName) {
        LinearLayout phaseContainer = new LinearLayout(this);
        phaseContainer.setOrientation(LinearLayout.VERTICAL);

        TextView phaseTitle = new TextView(this);
        phaseTitle.setText(phaseName);
        phaseTitle.setTextSize(22f);
        phaseTitle.setTypeface(null, Typeface.BOLD);
        phaseTitle.setGravity(Gravity.CENTER);
        phaseTitle.setPadding(0, 0, 0, 20);

        int[] phaseColors = {
                Color.rgb(66, 66, 66),
                Color.rgb(255, 50, 50),
                Color.rgb(70, 130, 255)
        };
        phaseTitle.setTextColor(phaseColors[phaseIndex]);
        phaseContainer.addView(phaseTitle);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(15, 15, 15, 15);

        grid.addView(createInfoRow("⚡ Voltaje:", String.format("%.2f V", voltajes[phaseIndex])));
        grid.addView(createInfoRow("🔌 Corriente:", String.format("%.3f A", corrientes[phaseIndex])));
        grid.addView(createInfoRow("💡 Potencia:", String.format("%.2f W", potencias[phaseIndex])));
        grid.addView(createInfoRow("📊 Frecuencia:", String.format("%.2f Hz", frecuencias[phaseIndex])));

        float deviceAngle = angulos[phaseIndex];
        while (deviceAngle > 180f) deviceAngle -= 360f;
        while (deviceAngle < -180f) deviceAngle += 360f;
        grid.addView(createInfoRow("📐 Ángulo:", String.format("%.1f°", deviceAngle)));

        float pf = 0.0f;
        if (voltajes[phaseIndex] > 0 && corrientes[phaseIndex] > 0) {
            float potenciaAparente = voltajes[phaseIndex] * corrientes[phaseIndex];
            if (potenciaAparente > 0) {
                pf = potencias[phaseIndex] / potenciaAparente;
                pf = Math.max(-1.0f, Math.min(1.0f, pf));
            }
        }
        grid.addView(createInfoRow("🎯 FP:", String.format("%.3f", pf)));

        phaseContainer.addView(grid);
        return phaseContainer;
    }

    private LinearLayout createInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(10, 8, 10, 8);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(16f);
        labelView.setTextColor(Color.rgb(66, 66, 66));
        labelView.setTypeface(null, Typeface.BOLD);
        labelView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(labelView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextSize(18f);
        valueView.setTextColor(Color.rgb(33, 150, 243));
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setGravity(Gravity.END);
        valueView.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(valueView);

        return row;
    }

    private void showInfoTableModal() {
        if (isInfoTableModalVisible) return;

        createInfoTableModal();

        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        rootView.addView(infoTableModal);

        infoTableModal.setVisibility(View.VISIBLE);
        infoTableModal.setAlpha(0f);
        infoTableModal.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        isInfoTableModalVisible = true;
    }

    private void hideInfoTableModal() {
        if (!isInfoTableModalVisible || infoTableModal == null) return;

        infoTableModal.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    infoTableModal.setVisibility(View.GONE);
                    ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                    rootView.removeView(infoTableModal);
                    isInfoTableModalVisible = false;
                })
                .start();
    }

    private Drawable createRoundedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(15f);
        drawable.setStroke(3, Color.rgb(200, 200, 200));
        return drawable;
    }

    // =========================================================================
    // ===== CONEXIÓN TCP =====================================================
    // =========================================================================

    private void connectToDeviceIndependent() {
        if (isConnectedToDevice) return;

        executor.execute(() -> {
            try {
                disconnectFromDeviceInternal();

                socket = new Socket();
                socket.connect(new java.net.InetSocketAddress(deviceIp, devicePort), 10000);
                socket.setSoTimeout(15000);
                socket.setTcpNoDelay(true);
                socket.setKeepAlive(true);

                outputStream = socket.getOutputStream();
                inputStream = socket.getInputStream();

                handler.post(() -> {
                    isConnectedToDevice = true;
                    showToast("✅ Conectado");

                    startIndependentReceiveThreadImproved();
                    handler.postDelayed(() -> performAutomaticSetup(), 500);
                });

            } catch (Exception e) {
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                });
            }
        });
    }

    private void startIndependentReceiveThreadImproved() {
        shouldStopReceiving = false; //  Resetear al iniciar

        executor.execute(() -> {
            byte[] buffer = new byte[2048];

            try {
                while (isConnectedToDevice && !Thread.currentThread().isInterrupted() && !shouldStopReceiving) { //  Agregar condición
                    try {
                        if (socket == null || socket.isClosed() || !socket.isConnected()) break;
                        if (inputStream == null) break;

                        int bytesRead = inputStream.read(buffer);

                        if (bytesRead > 0) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);
                            handler.post(() -> processReceivedDataIndependent(data));
                        } else if (bytesRead == -1) {
                            break;
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        if (shouldStopReceiving) break; //  Verificar durante timeout
                        continue;
                    } catch (Exception e) {
                        if (isConnectedToDevice && !shouldStopReceiving) break; // Verificar
                    }
                }
            } catch (Exception e) {
            }

            // ✅ No intentar reconectar si estamos cerrando
            if (isConnectedToDevice && !shouldStopReceiving) {
                handler.post(() -> {
                    isConnectedToDevice = false;
                    configurationSynced = false;
                    setControlsEnabled(false);
                    handler.postDelayed(() -> connectToDeviceIndependent(), 3000);
                });
            }
        });
    }

    private void disconnectFromDevice() {
        shouldStopReceiving = true;
        isConnectedToDevice = false;
        configurationSynced = false;
        stopDataAcquisition();

        executor.execute(() -> disconnectFromDeviceInternal());
    }

    private void disconnectFromDeviceInternal() {
        try {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                }
                inputStream = null;
            }

            if (outputStream != null) {
                try {
                    outputStream.flush();
                    outputStream.close();
                } catch (IOException e) {
                }
                outputStream = null;
            }

            if (socket != null) {
                try {
                    if (!socket.isClosed()) socket.close();
                } catch (IOException e) {
                }
                socket = null;
            }
        } catch (Exception e) {
        }
    }

    // =========================================================================
    // ===== COMANDOS =========================================================
    // =========================================================================

    private void sendDeviceIdReadCommand() {
        byte[] command = OctoNetCommandEncoder.createDeviceIdReadCommand();
        sendTcpCommandIndependent(command);
    }

    private void sendWiFiReadCommand() {
        byte[] command = OctoNetCommandEncoder.createWiFiSettingsReadCommand();
        sendTcpCommandIndependent(command);
    }

    private void sendTimeWriteCommand() {
        executor.execute(() -> {
            try {
                if (!isConnectedToDevice || outputStream == null) return;

                Calendar calendar = Calendar.getInstance();
                int year = calendar.get(Calendar.YEAR);
                int month = calendar.get(Calendar.MONTH) + 1;
                int day = calendar.get(Calendar.DAY_OF_MONTH);
                int hour = calendar.get(Calendar.HOUR_OF_DAY);
                int minute = calendar.get(Calendar.MINUTE);
                int second = calendar.get(Calendar.SECOND);
                int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

                byte[] command = OctoNetCommandEncoder.createDeviceTimeWriteCommand(
                        year, month, day, hour, minute, second, dayOfWeek);

                outputStream.write(command);
                outputStream.flush();
            } catch (Exception e) {
            }
        });
    }

    private void readDeviceConfigurationIndependent() {
        if (!isConnectedToDevice) return;

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
            configurationSynced = true;
            setControlsEnabled(true);
        }
    }

    private void requestCurrentData() {
        if (!isConnectedToDevice || socket == null || socket.isClosed() || isWaitingResponse)
            return;

        try {
            byte[] command = OctoNetCommandEncoder.createNodeCurrentReadCommand();
            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            handler.postDelayed(() -> {
                if (isWaitingResponse) isWaitingResponse = false;
            }, 3000);

        } catch (Exception e) {
            isWaitingResponse = false;
        }
    }

    private void sendTcpCommandIndependent(byte[] command) {
        if (!isConnectedToDevice || outputStream == null) return;

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
                });
            }
        });
    }

    // =========================================================================
    // ===== PROCESAMIENTO ====================================================
    // =========================================================================

    private void processReceivedDataIndependent(byte[] data) {
        try {
            isWaitingResponse = false;

            if (data == null || data.length < 4) return;
            if (!OctoNetCommandEncoder.validateCommandStructure(data)) return;
            if (!OctoNetCommandEncoder.verifyChecksum(data)) return;

            int responseType = data[1] & 0xFF;
            int command = data[2] & 0xFF;
            int dataSize = data[3] & 0xFF;

            if (responseType == 0x45) return;

            if (responseType == 0x43) {
                switch (command) {
                    case 0x00:
                        processDeviceIdResponse(data);
                        break;
                    case 0x20:
                        if (dataSize > 0) processConfigurationResponseIndependent(data);
                        break;
                    case 0x21:
                        if (dataSize > 0) processCurrentDataResponseIndependent(data);
                        break;
                    case 0xE3:
                        if (dataSize > 0) processWiFiSettingsResponse(data);
                        break;
                }
            }

        } catch (Exception e) {
        } finally {
            isWaitingResponse = false;
        }
    }

    private void processDeviceIdResponse(byte[] response) {
        try {
            byte[] deviceData = OctoNetCommandEncoder.extractCommandData(response);
            if (deviceData == null || deviceData.length == 0) return;

            lastReadDeviceIdInfo = new DeviceIdInfo();
            String deviceInfoString = new String(deviceData, "UTF-8").trim();

            if (deviceInfoString.length() >= 30) {
                try {
                    if (deviceInfoString.length() >= 12)
                        lastReadDeviceIdInfo.serial = deviceInfoString.substring(0, 12);

                    if (deviceInfoString.length() >= 18) {
                        String dateStr = deviceInfoString.substring(12, 18);
                        lastReadDeviceIdInfo.facDate = dateStr.substring(0, 2) + "/" +
                                dateStr.substring(2, 4) + "/20" + dateStr.substring(4, 6);
                    }

                    if (deviceInfoString.length() >= 24) {
                        String timeStr = deviceInfoString.substring(18, 24);
                        lastReadDeviceIdInfo.facHour = timeStr.substring(0, 2) + ":" +
                                timeStr.substring(2, 4) + ":" + timeStr.substring(4, 6);
                    }

                    if (deviceInfoString.length() >= 28)
                        lastReadDeviceIdInfo.actCode = deviceInfoString.substring(24, 28);

                    if (deviceInfoString.length() >= 34)
                        lastReadDeviceIdInfo.hwVersion = deviceInfoString.substring(28, 34);

                    if (deviceInfoString.length() > 34)
                        lastReadDeviceIdInfo.fwVersion = deviceInfoString.substring(34);

                    return;

                } catch (Exception e) {
                }
            }

            lastReadDeviceIdInfo.serial = deviceInfoString.isEmpty() ? "DESCONOCIDO" : deviceInfoString;

        } catch (Exception e) {
            lastReadDeviceIdInfo = new DeviceIdInfo();
            lastReadDeviceIdInfo.serial = "ERROR";
        }
    }

    private void processWiFiSettingsResponse(byte[] response) {
        try {
            byte[] wifiData = OctoNetCommandEncoder.extractCommandData(response);
            if (wifiData == null || wifiData.length < 64) return;

            lastReadWifiSettings = OctoNetCommandEncoder.processWiFiSettingsResponse(wifiData);
        } catch (Exception e) {
        }
    }

    private void processConfigurationResponseIndependent(byte[] data) {
        try {
            byte[] configData = OctoNetCommandEncoder.extractCommandData(data);
            if (configData == null || configData.length < 12) return;

            boolean recording = (configData[8] & 0xFF) == 0x01;
            int period = configData[9] & 0xFF;
            int sensors = configData[10] & 0xFF;
            int wiringSet = configData[11] & 0xFF;

            System.out.println("📥 Config recibida del dispositivo:");
            System.out.println("   Recording: " + recording);
            System.out.println("   Sensor: " + sensors);
            System.out.println("   WiringSet: " + wiringSet);

            // ✅ Actualizar variables de configuración
            sensorsConfig = sensors;
            meteringTypeConfig = wiringSet;
            periodConfig = period;

            // ✅ Calcular posición spinner Amperes
            skipSpinnerEvents = true;
            int spinnerPos;
            switch (sensors) {
                case 1:  spinnerPos = 0; break; // CT-50A
                case 2:  spinnerPos = 1; break; // CT-200A
                case 4:  spinnerPos = 2; break; // RoGo-1000A
                case 5:  spinnerPos = 3; break; // RoGo-3000A
                default: spinnerPos = 1; break; // Default 200A
            }

            // ✅ Calcular posición spinner Cableado
            int cableadoPos;
            switch (wiringSet) {
                case 0:  cableadoPos = 0; break; // 1PH_2W_4LOADS
                case 1:  cableadoPos = 1; break; // 1PH_3W_2LOAD
                case 2:  cableadoPos = 2; break; // 3PH_4W_1LOAD
                default: cableadoPos = 2; break;
            }

            final int finalSpinnerPos  = spinnerPos;
            final int finalCableadoPos = cableadoPos;

            handler.post(() -> {
                if (spinnerAmperes != null) {
                    spinnerAmperes.setSelection(finalSpinnerPos);
                }
                if (spinnerCableado != null) {
                    spinnerCableado.setSelection(finalCableadoPos);
                }
                skipSpinnerEvents = false;
                updateDiagram(); // ✅ Actualizar imagen con la config recibida
            });

            System.out.println("✅ sensorsConfig actualizado: " + sensorsConfig);
            System.out.println("✅ meteringTypeConfig actualizado: " + meteringTypeConfig);

            configurationSynced = true;

        } catch (Exception e) {
            System.out.println("❌ Error procesando config: " + e.getMessage());
        }
    }

    private void processCurrentDataResponseIndependent(byte[] response) {
        try {
            if (response == null || response.length < 4) return;

            int sizeFromDevice = response[3] & 0xFF;
            if (sizeFromDevice == 0) return;

            int realDataSize = sizeFromDevice + 1;
            int expectedTotal = 4 + realDataSize + 2;

            if (response.length < expectedTotal) return;

            byte[] energyData = new byte[realDataSize];
            System.arraycopy(response, 4, energyData, 0, realDataSize);

            if (energyData.length >= 64) {
                if (energyData.length >= 40) processChannel64Bytes(energyData, 28, 0, "CH1");
                if (energyData.length >= 52) processChannel64Bytes(energyData, 40, 1, "CH2");
                if (energyData.length >= 64) processChannel64Bytes(energyData, 52, 2, "CH3");

                handler.post(() -> {
                    updateTextViews();
                    updateChannelLabels();
                    updateFasores();

                    if (isFasorModalVisible) {
                        updateModalFasores();
                    }

                    if (isInfoTableModalVisible) {
                        updateInfoTableContent();
                    }
                });

                contadorMuestras++;
            }

        } catch (Exception e) {
        }
    }

    private void processChannel64Bytes(byte[] data, int offset, int channelIndex, String channelName) {
        try {
            if (data.length >= offset + 4) {
                long powerRaw = readInt32LE(data, offset);
                potencias[channelIndex] = (int) powerRaw * 0.1f;
            }

            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16LE(data, offset + 4);
                voltajes[channelIndex] = voltageRaw * 0.1f;
            }

            if (data.length >= offset + 8) {
                int currentRaw = readUInt16LE(data, offset + 6);
                corrientes[channelIndex] = currentRaw * 0.1f;
            }

            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16LE(data, offset + 8);
                frecuencias[channelIndex] = frequencyRaw * 0.1f;
            }

            if (data.length >= offset + 12) {
                int angleRaw = readUInt16LE(data, offset + 10);
                angulos[channelIndex] = angleRaw * 0.1f;
            } else {
                angulos[channelIndex] = channelIndex * 120.0f;
            }

        } catch (Exception e) {
            voltajes[channelIndex] = 0.0f;
            corrientes[channelIndex] = 0.0f;
            potencias[channelIndex] = 0.0f;
            frecuencias[channelIndex] = 50.0f;
            angulos[channelIndex] = channelIndex * 120.0f;
        }
    }

    // =========================================================================
    // ===== ACTUALIZACIÓN UI =================================================
    // =========================================================================

    private void updateTextViews() {
        try {
            if (tvV1 != null) tvV1.setText(String.format("%.2f V", voltajes[0]));
            if (tvV2 != null) tvV2.setText(String.format("%.2f V", voltajes[1]));
            if (tvV3 != null) tvV3.setText(String.format("%.2f V", voltajes[2]));

            if (tvA1 != null) tvA1.setText(String.format("%.3f A", corrientes[0]));
            if (tvA2 != null) tvA2.setText(String.format("%.3f A", corrientes[1]));
            if (tvA3 != null) tvA3.setText(String.format("%.3f A", corrientes[2]));

            if (tvW1 != null) tvW1.setText(String.format("%.2f W", potencias[0]));
            if (tvW2 != null) tvW2.setText(String.format("%.2f W", potencias[1]));
            if (tvW3 != null) tvW3.setText(String.format("%.2f W", potencias[2]));

            if (tvHz1 != null) tvHz1.setText(String.format("%.2f Hz", frecuencias[0]));
            if (tvHz2 != null) tvHz2.setText(String.format("%.2f Hz", frecuencias[1]));
            if (tvHz3 != null) tvHz3.setText(String.format("%.2f Hz", frecuencias[2]));

            for (int i = 0; i < 3; i++) {
                float deviceAngle = angulos[i];
                while (deviceAngle > 180f) deviceAngle -= 360f;
                while (deviceAngle < -180f) deviceAngle += 360f;

                String angleText = String.format("%.0f°", deviceAngle);
                if (i == 0 && tvpF1 != null) tvpF1.setText(angleText);
                if (i == 1 && tvpF2 != null) tvpF2.setText(angleText);
                if (i == 2 && tvpF3 != null) tvpF3.setText(angleText);
            }

        } catch (Exception e) {
        }
    }

    private void updateChannelLabels() {
        try {
            if (tvCH1 != null) tvCH1.setText("L1-N");
            if (tvCH2 != null) tvCH2.setText("L2-N");
            if (tvCH3 != null) tvCH3.setText("L3-N");
        } catch (Exception e) {
        }
    }

    private void updateFasores() {
        if (fasorVoltaje == null || fasorCorriente == null) return;

        try {
            fasorVoltaje.setMagnitudes(voltajes[0], voltajes[2], voltajes[1]);
            fasorVoltaje.setAngles(0.0f, 120.0f, 240.0f);

            fasorCorriente.setMagnitudes(corrientes[0], corrientes[1], corrientes[2]);
            fasorCorriente.setAngles(angulos[0], angulos[1], angulos[2]);

        } catch (Exception e) {
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

        if (tvpF1 != null) tvpF1.setText("0°");
        if (tvpF2 != null) tvpF2.setText("0°");
        if (tvpF3 != null) tvpF3.setText("0°");

        if (tvCH1 != null) tvCH1.setText("L1");
        if (tvCH2 != null) tvCH2.setText("L2");
        if (tvCH3 != null) tvCH3.setText("L3");
    }

    private void startDataAcquisition() {
        if (!isConnectedToDevice || !configurationSynced || autoReadEnabled) return;

        try {
            autoReadEnabled = true;
            tiempoInicio = System.currentTimeMillis();
            contadorMuestras = 0;

            setSpinnersEnabled(false);

            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_pause);
            }

            showToast("▶️ Midiendo");
            requestCurrentData();

            if (autoReadHandler != null && autoReadTask != null) {
                autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);
            }

        } catch (Exception e) {
            autoReadEnabled = false;
            setSpinnersEnabled(true);

            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
            }
        }
    }

    private void stopDataAcquisition() {
        autoReadEnabled = false;

        try {
            if (autoReadHandler != null && autoReadTask != null) {
                autoReadHandler.removeCallbacks(autoReadTask);
            }

            if (handler != null) {
                handler.removeCallbacksAndMessages(null);
            }

            setSpinnersEnabled(true);

            if (btnPlay != null) {
                btnPlay.setImageResource(android.R.drawable.ic_media_play);
            }

            isWaitingResponse = false;
            showToast("⏹️ Detenido");

        } catch (Exception e) {
        }
    }

    private void updateDiagram() {
        if (imageDiagram == null) return;

        int diagramResource;
        switch (meteringTypeConfig) {
            case 0: diagramResource = R.drawable.w1ph_2w_4loads; break;
            case 1: diagramResource = R.drawable.w1ph_3w_2load;  break;
            case 2: diagramResource = R.drawable.w3ph_4w_1load;  break;
            default: diagramResource = R.drawable.w3ph_4w_1load; break;
        }

        System.out.println("🖼️ updateDiagram() meteringTypeConfig=" + meteringTypeConfig + " → resource=" + diagramResource);
        imageDiagram.setImageResource(diagramResource);
    }

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
            if (spinnerCableado != null) spinnerCableado.setEnabled(enabled); // ✅ Ya no es siempre false
            if (spinnerAmperes != null) spinnerAmperes.setEnabled(enabled);
        });
    }

    private void showToast(String message) {
        handler.post(() -> {
            try {
                Toast.makeText(FasoresActivity.this, message, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopRecordingAnimation();

        if (autoReadEnabled) {
            stopDataAcquisition();
        }


    }

    @Override
    protected void onResume() {
        super.onResume();

        if (!isConnectedToDevice) {
            handler.postDelayed(() -> connectToDeviceIndependent(), 500);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Detener el hilo de recepción PRIMERO
        shouldStopReceiving = true;
        isConnectedToDevice = false;
        isWaitingResponse = false;
        configurationSynced = false;

        stopRecordingAnimation();

        if (isFasorModalVisible) {
            hideFasorModal();
        }

        if (isInfoTableModalVisible) {
            hideInfoTableModal();
        }

        if (isModalVisible) {
            hideDiagramModal();
        }

        // ✅ Detener handlers
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (autoReadHandler != null) {
            autoReadHandler.removeCallbacksAndMessages(null);
        }

        // ✅ Desconectar socket
        disconnectFromDeviceInternal();

        // ✅ Shutdown del executor con timeout
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }

        if (wifiReceiver != null) {
            try {
                unregisterReceiver(wifiReceiver);
            } catch (Exception e) {
            }
        }
    }
}