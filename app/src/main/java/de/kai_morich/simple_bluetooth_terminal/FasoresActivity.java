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
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private android.widget.TextView tvRecLabel;
    private LinearLayout btnBackToMenu;
    private Button btnDeviceId, btnConfigWifi, btnConfigSub1g, btnMemoria;
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isRecording = false;

    // ===== FASORES =====
    private FasorView fasorVoltaje, fasorCorriente;

    // ===== MODALES =====
    private View diagramModal;
    private ImageView modalDiagramImage;
    private boolean isModalVisible = false;

    private TextView tvV4, tvA4, tvW4, tvHz4, tvpF4, tvCH4;

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
    private LinearLayout measurementTableLayout;

    // ===== CONEXIÓN TCP =====
    private Socket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private ExecutorService executor;
    private String deviceIp = "192.168.4.1";
    private int devicePort = 333;
    private boolean isConnectedToDevice = false;

    // ===== SENSORES (índice = valor 01h..05h - 1) =====
    private static final String[] SENSOR_LABELS = {"CT-50A", "CT-200A", "CT-1000A", "RoGo-2000A", "RoGo-4000A"};
    private static final int[]    SENSOR_AMPS   = {50, 200, 1000, 2000, 4000};

    // ===== CONFIGURACIÓN =====
    private int periodConfig = 1;           // 1 = 5 MINUTOS
    private int sensorsConfig = 2;          // 2 = CT-200A (valor inicial)
    private int meteringTypeConfig = 2;     // 2 = 3PH, 3W
    private boolean recordingConfig = false;
    // ===== ESTADO =====
    private volatile boolean shouldStopReceiving = false;
    private int rangoAmperes = 200;         // Valor inicial en la UI

    // Android acepta 4 escaneos WiFi cada 2 minutos; pasado ese limite devuelve
    // los resultados viejos sin avisar.
    private static final long SCAN_COOLDOWN_MS = 10000;
    private long ultimoEscaneoMs = 0;

    // ===== AUTO-LECTURA =====
    private boolean autoReadEnabled = false;
    private static final int AUTO_READ_INTERVAL = 5000;
    private Runnable autoReadTask;
    private Handler autoReadHandler = new Handler(Looper.getMainLooper());

    // ===== DATOS DEL MEDIDOR =====
    // 4 canales: en 4LOADS son Load1..Load4; en trifasico se usan los 3 primeros
    private float[] voltajes = {0.0f, 0.0f, 0.0f, 0.0f};
    private float[] corrientes = {0.0f, 0.0f, 0.0f, 0.0f};
    private float[] potencias = {0.0f, 0.0f, 0.0f, 0.0f};
    private float[] frecuencias = {0.0f, 0.0f, 0.0f, 0.0f};
    private float[] angulos = {0.0f, 120.0f, 240.0f, 0.0f};
    private OctoNetCommandEncoder.WiFiSettings lastReadWifiSettings = null;
    private OctoNetCommandEncoder.Sub1gSettings lastReadSub1gSettings = null;

    // ===== ESTADO =====
    private long tiempoInicio;
    private int contadorMuestras = 0;
    private boolean isWaitingResponse = false;
    private boolean configurationSynced = false;
    private boolean skipSpinnerEvents = false;

    // Agregar junto a:  private ImageView modalDiagramImage;
    private ZoomableImageView zoomableModalImage;

    private DeviceIdInfo lastReadDeviceIdInfo;
    private OctoNetCommandEncoder.NodeRecInfo lastNodeRecInfo;
    private boolean pendingRecordingAfterErase = false;
    private Runnable eraseTimeoutRunnable;

    private static class DeviceIdInfo {
        String serial = "N/A", facDate = "N/A", facHour = "N/A",
                actCode = "N/A", hwVersion = "N/A", fwVersion = "N/A";
    }

    // ===== HELPERS =====
    // Int32 con signo (Tabla 15): la potencia puede ser negativa cuando hay inyección
    private int readInt32LE(byte[] data, int offset) {
        return ((data[offset] & 0xFF)) |
                ((data[offset + 1] & 0xFF) << 8) |
                ((data[offset + 2] & 0xFF) << 16) |
                (data[offset + 3] << 24);
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
        tvRecLabel = findViewById(R.id.tvRecLabel);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);
        btnDeviceId = findViewById(R.id.btnDeviceId);
        btnConfigWifi = findViewById(R.id.btnConfigWifi);
        btnConfigSub1g = findViewById(R.id.btnConfigSub1g);
        btnMemoria = findViewById(R.id.btnMemoria);

        if (btnRec != null) {
            btnRec.setImageResource(R.drawable.ic_rec);
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

        measurementTableLayout = findViewById(R.id.measurementTableLayout);
        // TextViews are built dynamically — see rebuildMeasurementTable()
        rebuildMeasurementTable();

        //  CONFIGURAR LISTENERS PARA MODALES
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

                            //  Factor muy gradual
                            if (Math.abs(spacingDiff) > 1f) {
                                float[] values = new float[9];
                                matrix.getValues(values);
                                float currentScale = values[android.graphics.Matrix.MSCALE_X];

                                //  Zoom gradual: cada pixel = muy poco cambio
                                float scaleDelta = 1f + (spacingDiff * 0.005f);

                                float newScale = currentScale * scaleDelta;

                                //  Limitar escala
                                if (newScale < MIN_SCALE) scaleDelta = MIN_SCALE / currentScale;
                                else if (newScale > MAX_SCALE) scaleDelta = MAX_SCALE / currentScale;

                                matrix.postScale(scaleDelta, scaleDelta, midX, midY);
                                fixTranslation();
                                setImageMatrix(matrix);
                                savedMatrix.set(matrix);
                                lastSpacing = newSpacing; //  frame a frame
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

                //  Al abrir: escala 1:1
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

                //  Reset: ajustar para que quepa completa
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
        // NUEVAS opciones de cableado
        String[] cableadoOptions = {"1-Fase 4-Cargas", "2-Fases 2-Cargas", "3-Fases 1-Carga"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        setupSpinnerAdapter(spinnerAmperes, SENSOR_LABELS);

        setupSpinnerListeners();

        skipSpinnerEvents = true;
        spinnerAmperes.setSelection(1);   // 200A por defecto
        spinnerCableado.setSelection(2);  // 3PH 4W 1-LOAD por defecto (índice 2)
        skipSpinnerEvents = false;

        setSpinnersEnabled(false);
        setControlsEnabled(false);
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
                if (!skipSpinnerEvents && configurationSynced && position + 1 != sensorsConfig) {
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
                if (!skipSpinnerEvents && configurationSynced && position != meteringTypeConfig) {
                    System.out.println("📊 SPINNER CABLEADO - posición: " + position);

                    //  Actualizar meteringTypeConfig PRIMERO
                    switch (position) {
                        case 0: meteringTypeConfig = 0; break;
                        case 1: meteringTypeConfig = 1; break;
                        case 2: meteringTypeConfig = 2; break;
                        default: meteringTypeConfig = 2; break;
                    }

                    //  Actualizar imagen DESPUÉS de actualizar meteringTypeConfig
                    updateDiagram();
                    rebuildMeasurementTable();

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
            if (isRecording) {
                toggleRecording(); // detener — sin modal
            } else {
                showRecordingModeModal();
            }
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
        btnConfigSub1g.setOnClickListener(v -> showSub1gModal());
        btnMemoria.setOnClickListener(v -> showMemoriaModal());
    }

    private void showRecordingModeModal() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("▶ Iniciar Grabación")
                .setMessage("¿Cómo deseas iniciar la grabación?")
                .setPositiveButton("Continuar Nodo", (d, w) -> toggleRecording())
                .setNeutralButton("Nuevo Nodo", (d, w) -> confirmNuevoNodo())
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void confirmNuevoNodo() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("⚠️ Advertencia — Nuevo Nodo")
                .setMessage("Se borrarán TODOS los datos almacenados en la memoria antes de iniciar la grabación.\n\nEsta acción no se puede deshacer.\n\n¿Continuar?")
                .setPositiveButton("Borrar y Grabar", (d, w) -> {
                    pendingRecordingAfterErase = true;
                    sendTcpCommandIndependent(OctoNetCommandEncoder.createNodeRecEraseCommand());
                    showToast("🗑 Borrando memoria...");
                    eraseTimeoutRunnable = () -> {
                        if (pendingRecordingAfterErase) {
                            pendingRecordingAfterErase = false;
                            toggleRecording();
                        }
                    };
                    handler.postDelayed(eraseTimeoutRunnable, 10000);
                })
                .setNegativeButton("Cancelar", null)
                .show();
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

            if (!Constants.isMeterAp(currentSsid)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("⚠️ Advertencia")
                        .setMessage("No estás conectado a la red del medidor.\n\n¿Continuar?")
                        .setPositiveButton("Continuar", (dialog, which) -> escanearRedes())
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
        }

        escanearRedes();
    }

    private void escanearRedes() {
        ultimoEscaneoMs = System.currentTimeMillis();
        userRequestedWifiScan = true;
        wifiManager.startScan();
    }

    @SuppressLint("MissingPermission")
    private void updateAvailableNetworks() {
        availableNetworks.clear();
        availableNetworks.addAll(wifiManager.getScanResults());
    }

    private void displayNetworkSelectionForValidation() {
        // Una sola entrada por SSID (el escaneo repite la misma red por cada AP): nos
        // quedamos con la antena mas fuerte y ordenamos de mejor a peor senal.
        Map<String, ScanResult> porSsid = new LinkedHashMap<>();
        for (ScanResult red : availableNetworks) {
            if (red.SSID == null || red.SSID.isEmpty()) continue;
            if (Constants.isMeterAp(red.SSID)) continue;   // el AP del propio medidor
            if (!is24GHzNetwork(red)) continue;            // el medidor solo hace 2.4GHz

            ScanResult previa = porSsid.get(red.SSID);
            if (previa == null || red.level > previa.level) porSsid.put(red.SSID, red);
        }

        List<ScanResult> redes = new ArrayList<>(porSsid.values());
        Collections.sort(redes, (a, b) -> b.level - a.level);

        if (redes.isEmpty()) {
            showToast("❌ Sin redes 2.4GHz disponibles");
            return;
        }

        LinearLayout lista = new LinearLayout(this);
        lista.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] dialogRef = new AlertDialog[1];

        for (int i = 0; i < redes.size(); i++) {
            if (i > 0) lista.addView(makeListDivider());
            final String ssid = redes.get(i).SSID;
            View fila = makeNetworkRow(redes.get(i));
            fila.setOnClickListener(v -> {
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showPasswordDialogForValidation(ssid);
            });
            lista.addView(fila);
        }

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(lista);

        dialogRef[0] = new MaterialAlertDialogBuilder(this)
                .setTitle("Red WiFi para el medidor")
                .setMessage("Elegí la red a la que se va a conectar (solo 2,4 GHz)")
                .setView(scroll)
                // El refrescar vive en la barra de botones del dialogo: ahi se lee
                // como boton sin necesidad de disfrazarlo.
                .setNeutralButton("🔄  Refrescar", (dialog, which) -> {
                    showToast("🔄 Buscando redes...");
                    escanearRedes();
                })
                .setNegativeButton("Cancelar", null)
                .show();

        aplicarCooldownRefrescar(dialogRef[0].getButton(AlertDialog.BUTTON_NEUTRAL));
    }

    /** Deshabilita Refrescar hasta que se pueda escanear de nuevo, con la cuenta regresiva. */
    private void aplicarCooldownRefrescar(Button boton) {
        if (boton == null) return;

        long restante = SCAN_COOLDOWN_MS - (System.currentTimeMillis() - ultimoEscaneoMs);
        if (restante <= 0) {
            boton.setEnabled(true);
            boton.setText("🔄  Refrescar");
            return;
        }

        boton.setEnabled(false);
        boton.setText("🔄  " + (restante / 1000 + 1) + " s");
        handler.postDelayed(() -> aplicarCooldownRefrescar(boton), 1000);
    }

    /** Fila de la lista de redes: nombre, seguridad y barras de senal */
    private View makeNetworkRow(ScanResult red) {
        LinearLayout fila = new LinearLayout(this);
        fila.setOrientation(LinearLayout.HORIZONTAL);
        fila.setGravity(Gravity.CENTER_VERTICAL);
        fila.setPadding(dp(20), dp(14), dp(20), dp(14));
        fila.setClickable(true);
        fila.setFocusable(true);

        // Fondo con ripple del tema: deja claro que la fila se toca
        android.util.TypedValue fondo = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, fondo, true);
        fila.setBackgroundResource(fondo.resourceId);

        LinearLayout textos = new LinearLayout(this);
        textos.setOrientation(LinearLayout.VERTICAL);
        textos.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView nombre = new TextView(this);
        nombre.setText(red.SSID);
        nombre.setTextSize(16f);
        nombre.setTextColor(Color.rgb(33, 33, 33));
        nombre.setTypeface(null, Typeface.BOLD);
        textos.addView(nombre);

        boolean protegida = red.capabilities != null
                && (red.capabilities.contains("WPA") || red.capabilities.contains("WEP")
                    || red.capabilities.contains("PSK") || red.capabilities.contains("SAE"));

        TextView detalle = new TextView(this);
        detalle.setText("2,4 GHz  ·  " + (protegida ? "🔒 Protegida" : "Abierta"));
        detalle.setTextSize(12f);
        detalle.setTextColor(Color.rgb(120, 120, 120));
        textos.addView(detalle);

        fila.addView(textos);

        int barras = nivelSenal(red.level);
        TextView senal = new TextView(this);
        StringBuilder marcas = new StringBuilder();
        for (int i = 0; i < barras; i++) marcas.append("▮");
        senal.setText(marcas.toString());
        senal.setTextSize(15f);
        senal.setTextColor(barras >= 3 ? Color.rgb(46, 160, 67)
                         : barras == 2 ? Color.rgb(230, 160, 30)
                                       : Color.rgb(200, 80, 80));
        fila.addView(senal);

        return fila;
    }

    /** RSSI en dBm a 1-4 barras */
    private int nivelSenal(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -78) return 2;
        return 1;
    }

    private View makeListDivider() {
        View linea = new View(this);
        linea.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(1) / 2)));
        linea.setBackgroundColor(Color.rgb(232, 232, 232));
        return linea;
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
        //  Si la contraseña es vacía o solo espacios, enviar 0x20 (un espacio)
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
    // ===== CONFIGURACIÓN SUB-1G (E4h / Tabla 23) ============================
    // =========================================================================

    // Opciones de los campos enumerados (índice = valor que se envía al dispositivo)
    private static final String[] SUB1G_MODEM_OPTIONS = {
            "GFSK 915MHz",
            "LoRa 915MHz"
    };
    private static final String[] SUB1G_HOP_OPTIONS = {
            "Canales: 0, 1, 2, 3, 4",
            "Canales: 10, 11, 12, 13, 14",
            "Canales: 20, 21, 22, 23, 24"
    };
    private static final String[] SUB1G_GFSK_BR_OPTIONS = {
            "BR:38.4kbps BW:166.2kHz",
            "BR:4.8kbps BW:23.4kHz",
            "BR:1.2kbps BW:19.5kHz",
            "BR:0.6kbps BW:4.8kHz"
    };
    private static final String[] SUB1G_LORA_BR_OPTIONS = {
            "BR:31.25kbps BW:250kHz",
            "BR:10.94kbps BW:250kHz",
            "BR:3.52kbps BW:250kHz",
            "BR:15.63kbps BW:125kHz",
            "BR:5.47kbps BW:125kHz",
            "BR:1.76kbps BW:125kHz"
    };

    private void showSub1gModal() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }

        // Pedir la configuración actual al dispositivo y mostrar el modal con esos datos
        sendSub1gReadCommand();
        showToast("📡 Leyendo configuración Sub-1G...");
        handler.postDelayed(this::displaySub1gConfigDialog, 2000);
    }

    private void displaySub1gConfigDialog() {
        OctoNetCommandEncoder.Sub1gSettings current = lastReadSub1gSettings;

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        // MODEM
        Spinner spModem = addSub1gSpinner(layout, "Modulación (MODEM)", SUB1G_MODEM_OPTIONS,
                current != null ? current.modem : 0);

        // CHANNEL
        android.widget.EditText etChannel = addSub1gNumberField(layout, "Channel",
                current != null ? current.channel : 0);

        // HOP_SCHEMA
        Spinner spHop = addSub1gSpinner(layout, "Esquema de salto (HOP_SCHEMA)", SUB1G_HOP_OPTIONS,
                current != null ? current.hopSchema : 0);

        // BROADCAST_ADDR
        android.widget.EditText etBroadcast = addSub1gNumberField(layout, "Dirección Broadcast",
                current != null ? current.broadcastAddr : 0);

        // UNICAST_ADDR
        android.widget.EditText etUnicast = addSub1gNumberField(layout, "Dirección Unicast",
                current != null ? current.unicastAddr : 0);

        // GFKS_BR
        Spinner spGfsk = addSub1gSpinner(layout, "Config. Modem GFSK (GFKS_BR)", SUB1G_GFSK_BR_OPTIONS,
                current != null ? current.gfskBr : 0);

        // LORA_BR
        Spinner spLora = addSub1gSpinner(layout, "Config. Modem LoRa (LORA_BR)", SUB1G_LORA_BR_OPTIONS,
                current != null ? current.loraBr : 0);

        // Sólo se edita el modem seleccionado (0 = GFSK, 1 = LoRa)
        spModem.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                boolean lora = (position == 1);
                setSpinnerAvailable(spGfsk, !lora);
                setSpinnerAvailable(spLora, lora);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        boolean loraInicial = spModem.getSelectedItemPosition() == 1;
        setSpinnerAvailable(spGfsk, !loraInicial);
        setSpinnerAvailable(spLora, loraInicial);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(layout);

        String title = (current == null)
                ? "📶 Configurar Sub-1G (sin lectura)"
                : "📶 Configurar Sub-1G";

        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton("📤 Enviar", (dialog, which) -> {
                    int modem    = spModem.getSelectedItemPosition();
                    int channel  = parseByteField(etChannel, 0);
                    int hop      = spHop.getSelectedItemPosition();
                    int bcast    = parseByteField(etBroadcast, 0);
                    int ucast    = parseByteField(etUnicast, 0);
                    int gfskBr   = spGfsk.getSelectedItemPosition();
                    int loraBr   = spLora.getSelectedItemPosition();

                    byte[] command = OctoNetCommandEncoder.createSub1gSettingsWriteCommand(
                            modem, channel, hop, bcast, ucast, gfskBr, loraBr);
                    sendTcpCommandIndependent(command);
                    showToast("📤 Configuración Sub-1G enviada");
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setSpinnerAvailable(Spinner spinner, boolean available) {
        spinner.setEnabled(available);
        spinner.setAlpha(available ? 1f : 0.35f);
    }

    private Spinner addSub1gSpinner(LinearLayout parent, String label, String[] options, int selected) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#666666"));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextSize(12);
        int topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        tv.setLayoutParams(lp);
        parent.addView(tv);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        if (selected >= 0 && selected < options.length) {
            spinner.setSelection(selected);
        }
        parent.addView(spinner);
        return spinner;
    }

    private android.widget.EditText addSub1gNumberField(LinearLayout parent, String label, int value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.parseColor("#666666"));
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextSize(12);
        int topMargin = (int) (12 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        tv.setLayoutParams(lp);
        parent.addView(tv);

        android.widget.EditText et = new android.widget.EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setText(String.valueOf(value));
        parent.addView(et);
        return et;
    }

    // Convierte el texto a un valor de 0-255 (un byte), con valor por defecto si es inválido
    private int parseByteField(android.widget.EditText et, int defaultValue) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            if (v < 0) v = 0;
            if (v > 255) v = 255;
            return v;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // =========================================================================
    // ===== CONFIGURACIÓN ====================================================
    // =========================================================================

    private void performAutomaticSetup() {
        if (!isConnectedToDevice) return;

        executor.execute(() -> {
            try {
                sendTimeWriteCommand();
                Thread.sleep(1000); // más tiempo

                sendWiFiReadCommand();
                Thread.sleep(1000); //  más tiempo

                readDeviceConfigurationIndependent();
                Thread.sleep(1500); //  más tiempo para config

                sendDeviceIdReadCommand(); // agregar aquí para tenerlo listo
                Thread.sleep(1000);

                handler.post(() -> {
                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                    isWaitingResponse = false; //  asegurar que queda en false
                    updateDiagram();           //  actualizar imagen al arrancar
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
        if (sensorIndex >= 0 && sensorIndex < SENSOR_AMPS.length) {
            rangoAmperes = SENSOR_AMPS[sensorIndex];
            updateDiagram();
            System.out.println("📊 Rango UI actualizado a: " + rangoAmperes + "A");
        }
    }

    private void writeAmperesConfiguration() {
        if (!isConnectedToDevice || isWaitingResponse) return;

        try {
            int spinnerPosition = spinnerAmperes.getSelectedItemPosition();
            if (spinnerPosition < 0 || spinnerPosition >= SENSOR_AMPS.length) spinnerPosition = 1;
            sensorsConfig = spinnerPosition + 1;   // posición 0 = sensor 01h
            rangoAmperes  = SENSOR_AMPS[spinnerPosition];

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
            btnRec.setImageResource(R.drawable.ic_stop);
            showToast("⏺ Grabación iniciada");
            startRecordingAnimation();
        } else {
            btnRec.setBackgroundResource(R.drawable.button_rec);
            btnRec.setImageResource(R.drawable.ic_rec);
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
            //  CRÍTICO: Usar sensorsConfig actual sin modificar rangoAmperes
            System.out.println("📤 Enviando REC con configuración actual:");
            System.out.println("   Recording: " + recording);
            System.out.println("   Sensor: " + sensorsConfig);
            System.out.println("   Rango UI (no cambia): " + rangoAmperes + "A");

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recording,
                    periodConfig,
                    sensorsConfig,  //  Mantener sensor actual
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
            if (isRecording) {
                btnRec.setBackgroundResource(R.drawable.button_rec_recording);
                btnRec.setImageResource(R.drawable.ic_stop);
            } else {
                btnRec.setBackgroundResource(R.drawable.button_rec);
                btnRec.setImageResource(R.drawable.ic_rec);
            }
        }
    }

    // ponytail: animaciones que se repiten solas (REVERSE + INFINITE).
    // La versión anterior se re-lanzaba a sí misma cada ciclo y acumulaba
    // parpadeadores, por eso el label terminaba titilando cada vez más rápido.
    private void startRecordingAnimation() {
        if (btnRec == null) return;

        // Pulso en el botón
        ScaleAnimation pulso = new ScaleAnimation(
                1f, 1.15f, 1f, 1.15f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        pulso.setDuration(600);
        pulso.setInterpolator(new AccelerateDecelerateInterpolator());
        pulso.setRepeatMode(Animation.REVERSE);
        pulso.setRepeatCount(Animation.INFINITE);
        btnRec.startAnimation(pulso);

        // Parpadeo del label "● GRABANDO"
        if (tvRecLabel != null) {
            tvRecLabel.setText("● GRABANDO");
            tvRecLabel.setTextColor(android.graphics.Color.RED);

            AlphaAnimation parpadeo = new AlphaAnimation(1f, 0.15f);
            parpadeo.setDuration(600);
            parpadeo.setRepeatMode(Animation.REVERSE);
            parpadeo.setRepeatCount(Animation.INFINITE);
            tvRecLabel.startAnimation(parpadeo);
        }
    }

    private void stopRecordingAnimation() {
        if (btnRec != null) {
            btnRec.clearAnimation();
            btnRec.setScaleX(1.0f);
            btnRec.setScaleY(1.0f);
            btnRec.setAlpha(1.0f);
        }

        // Restablecer label
        if (tvRecLabel != null) {
            tvRecLabel.clearAnimation();
            tvRecLabel.setVisibility(android.view.View.VISIBLE);
            tvRecLabel.setText("GRABAR");
            tvRecLabel.setTextColor(android.graphics.Color.parseColor("#666666"));
        }
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

        // Contenedor de imagen
        FrameLayout imageContainer = new FrameLayout(this);
        LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0);
        containerParams.weight = 1;
        containerParams.setMargins(20, 0, 20, 20);
        imageContainer.setLayoutParams(containerParams);
        imageContainer.setBackground(createRoundedBackground());
        imageContainer.setClipToOutline(true);

        //  Usar ZoomableImageView
        zoomableModalImage = new ZoomableImageView(this);
        modalDiagramImage = zoomableModalImage;

        FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        modalDiagramImage.setLayoutParams(imageParams);
        modalDiagramImage.setPadding(20, 20, 20, 20);
        imageContainer.addView(modalDiagramImage);
        modalContent.addView(imageContainer);

        //  Fila de botones
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

        //  Reset zoom
        resetZoomButton.setOnClickListener(v -> {
            if (zoomableModalImage != null) {
                zoomableModalImage.resetMatrix(); // antes decía initMatrix()
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
        aplicarFasores(modalFasorVoltaje, modalFasorCorriente);
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

        String[] labels;
        int[] canales;   // canal de datos que muestra cada bloque
        int[] colores;   // indice de color: agrupa los bloques de una misma carga

        switch (meteringTypeConfig) {
            case 0:  // 1PH-2W-4LOADS
                labels  = new String[]{"Load 1 (L1-N)", "Load 2 (L2-N)",
                                       "Load 3 (L3-N)", "Load 4 (L4-N)"};
                canales = new int[]{0, 1, 2, 3};
                colores = new int[]{0, 1, 2, 3};
                break;
            case 1:  // 1PH-3W-2LOADS: los dos sensores de cada carga van juntos
                labels  = new String[]{"LOAD_0 · CT-0", "LOAD_0 · CT-2",
                                       "LOAD_1 · CT-1", "LOAD_1 · CT-3"};
                canales = new int[]{0, 2, 1, 3};
                colores = new int[]{0, 0, 1, 1};
                break;
            default: // 3PH-4W-1LOAD
                labels  = new String[]{"L1-N", "L2-N", "L3-N"};
                canales = new int[]{0, 1, 2};
                colores = new int[]{0, 1, 2};
                break;
        }

        for (int i = 0; i < labels.length; i++) {
            if (i > 0) infoTableContainer.addView(makeDivider());
            infoTableContainer.addView(createPhaseTable(canales[i], colores[i], labels[i]));
        }
    }

    private View makeDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 2);
        dividerParams.setMargins(0, 20, 0, 20);
        divider.setLayoutParams(dividerParams);
        divider.setBackgroundColor(Color.rgb(200, 200, 200));
        return divider;
    }

    private LinearLayout createPhaseTable(int dataIndex, int colorIndex, String phaseName) {
        LinearLayout phaseContainer = new LinearLayout(this);
        phaseContainer.setOrientation(LinearLayout.VERTICAL);

        TextView phaseTitle = new TextView(this);
        phaseTitle.setText(phaseName);
        phaseTitle.setTextSize(22f);
        phaseTitle.setTypeface(null, Typeface.BOLD);
        phaseTitle.setGravity(Gravity.CENTER);
        phaseTitle.setPadding(0, 0, 0, 20);

        //  Colores: negro, rojo, azul, gris (para L4 en modo 4LOADS)
        int[] phaseColors = {
                Color.rgb(66, 66, 66),
                Color.rgb(255, 50, 50),
                Color.rgb(70, 130, 255),
                Color.rgb(120, 120, 120)
        };
        phaseTitle.setTextColor(phaseColors[Math.min(colorIndex, phaseColors.length - 1)]);
        phaseContainer.addView(phaseTitle);

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setPadding(15, 15, 15, 15);

        grid.addView(createInfoRow("⚡ Voltaje:",    String.format("%.2f V",  voltajes[dataIndex])));
        grid.addView(createInfoRow("🔌 Corriente:",  String.format("%.3f A",  corrientes[dataIndex])));
        grid.addView(createInfoRow("💡 Potencia:",   String.format("%.2f W",  potencias[dataIndex])));
        grid.addView(createInfoRow("📊 Frecuencia:", String.format("%.2f Hz", frecuencias[dataIndex])));

        float deviceAngle = angulos[dataIndex];
        while (deviceAngle >  180f) deviceAngle -= 360f;
        while (deviceAngle < -180f) deviceAngle += 360f;
        grid.addView(createInfoRow("📐 Ángulo:", String.format("%.1f°", deviceAngle)));

        float pf = 0.0f;
        if (voltajes[dataIndex] > 0 && corrientes[dataIndex] > 0) {
            float potenciaAparente = voltajes[dataIndex] * corrientes[dataIndex];
            if (potenciaAparente > 0) {
                pf = potencias[dataIndex] / potenciaAparente;
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

            //  No intentar reconectar si estamos cerrando
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

    private void sendSub1gReadCommand() {
        byte[] command = OctoNetCommandEncoder.createSub1gSettingsReadCommand();
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
                    case 0x22:
                        processNodeRecInfoResponse(data);
                        break;
                    case 0x25:
                        showToast("✅ Memoria borrada");
                        if (pendingRecordingAfterErase) {
                            pendingRecordingAfterErase = false;
                            if (eraseTimeoutRunnable != null) {
                                handler.removeCallbacks(eraseTimeoutRunnable);
                            }
                            toggleRecording();
                        }
                        break;
                    case 0xE3:
                        if (dataSize > 0) processWiFiSettingsResponse(data);
                        break;
                    case 0xE4:
                        if (dataSize > 0) processSub1gSettingsResponse(data);
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

    private void processSub1gSettingsResponse(byte[] response) {
        try {
            byte[] sub1gData = OctoNetCommandEncoder.extractCommandData(response);
            if (sub1gData == null || sub1gData.length < 14) return;

            lastReadSub1gSettings = OctoNetCommandEncoder.processSub1gSettingsResponse(sub1gData);
        } catch (Exception e) {
        }
    }

    private void processNodeRecInfoResponse(byte[] response) {
        try {
            byte[] recData = OctoNetCommandEncoder.extractCommandData(response);
            if (recData == null || recData.length < 20) return;
            lastNodeRecInfo = OctoNetCommandEncoder.processNodeRecInfoResponse(recData);
        } catch (Exception e) {
        }
    }

    // =========================================================================
    // ===== MEMORIA REC ======================================================
    // =========================================================================

    private void showMemoriaModal() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }
        lastNodeRecInfo = null;
        byte[] cmd = OctoNetCommandEncoder.createNodeRecInfoReadCommand();
        sendTcpCommandIndependent(cmd);
        showToast("📂 Leyendo memoria...");
        handler.postDelayed(this::displayMemoriaModal, 2000);
    }

    private void displayMemoriaModal() {
        OctoNetCommandEncoder.NodeRecInfo info = lastNodeRecInfo;

        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, pad);

        if (info == null || info.totalRecords == 0) {
            TextView tvEmpty = new TextView(this);
            tvEmpty.setText("Sin mediciones en memoria");
            tvEmpty.setTextSize(16f);
            tvEmpty.setGravity(Gravity.CENTER);
            tvEmpty.setPadding(0, 32, 0, 32);
            layout.addView(tvEmpty);
        } else {
            addModalRow(layout, "Registros totales:", String.valueOf(info.totalRecords));
            addModalRow(layout, "Registros nuevos:", String.valueOf(info.newRecords));

            View divider = new View(this);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 2);
            dp.setMargins(0, 16, 0, 16);
            divider.setLayoutParams(dp);
            divider.setBackgroundColor(Color.rgb(200, 200, 200));
            layout.addView(divider);

            addModalRow(layout, "Primera medición:", info.firstDate + "  " + info.firstTime);
            addModalRow(layout, "Última medición:",  info.lastDate  + "  " + info.lastTime);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle("📂 Memoria de Grabación")
                .setView(layout)
                .setPositiveButton("🗑 Formateo Rápido", (dialog, which) -> {
                    new MaterialAlertDialogBuilder(this)
                            .setTitle("⚠️ Confirmar borrado")
                            .setMessage("¿Borrar la tabla de contenido de la memoria?\n(Formateo rápido)")
                            .setPositiveButton("Borrar", (d2, w2) -> {
                                byte[] eraseCmd = OctoNetCommandEncoder.createNodeRecEraseCommand();
                                sendTcpCommandIndependent(eraseCmd);
                                lastNodeRecInfo = null;
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                })
                .setNegativeButton("Cerrar", null)
                .show();
    }

    private void addModalRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 10, 0, 10);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextSize(14f);
        tvLabel.setTextColor(Color.rgb(80, 80, 80));
        tvLabel.setTypeface(null, Typeface.BOLD);
        tvLabel.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tvLabel);

        TextView tvValue = new TextView(this);
        tvValue.setText(value);
        tvValue.setTextSize(14f);
        tvValue.setTextColor(Color.rgb(33, 33, 33));
        tvValue.setGravity(Gravity.END);
        tvValue.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(tvValue);

        parent.addView(row);
    }

    private void processConfigurationResponseIndependent(byte[] data) {
        try {
            byte[] configData = OctoNetCommandEncoder.extractCommandData(data);
            if (configData == null || configData.length < 12) return;

            // Tabla 11: el byte 0 SIEMPRE es EAh. El medidor tambien contesta 43/20 con
            // otra estructura (payload que arranca en 20h); si se parsea como ajustes
            // salen valores inventados en sensors/wiring.
            int idAjustes = configData[0] & 0xFF;
            if (idAjustes != 0xEA) {
                System.out.println("📥 Respuesta 43/20 que no es METER_SETTINGS (ID=0x"
                        + String.format("%02X", idAjustes) + "), ignorada");
                System.out.println("   📦 Trama cruda: " + OctoNetCommandEncoder.bytesToHexString(data));
                return;
            }

            boolean recording = (configData[8] & 0xFF) == 0x01;
            int period = configData[9] & 0xFF;
            int sensors = configData[10] & 0xFF;
            int wiringSet = configData[11] & 0xFF;

            System.out.println("📥 Config recibida del dispositivo:");
            System.out.println("   Recording: " + recording);
            System.out.println("   Sensor: " + sensors);
            System.out.println("   WiringSet: " + wiringSet);

            // Trama corrupta: antes se aceptaba igual y el default del switch
            // terminaba tirando el spinner a 3 fases. Mejor ignorarla.
            if (sensors < 1 || sensors > SENSOR_AMPS.length
                    || wiringSet < 0 || wiringSet > 2) {
                System.out.println("   ⚠️ Valores fuera de rango, trama descartada");
                System.out.println("   📦 Trama cruda: " + OctoNetCommandEncoder.bytesToHexString(data));
                return;
            }

            if (period != OctoNetCommandEncoder.PERIOD_5_MIN) {
                System.out.println("   ⚠️ PERIOD " + period + " no soportado, se usa 5 minutos");
            }

            //  Actualizar variables de configuración
            sensorsConfig = sensors;
            meteringTypeConfig = wiringSet;
            periodConfig = OctoNetCommandEncoder.PERIOD_5_MIN;  // unico valor valido
            // Reflejar estado de grabación del dispositivo sin modificarlo
            isRecording = recording;
            recordingConfig = recording;

            skipSpinnerEvents = true;
            final int finalSpinnerPos  = sensors - 1;   // sensor 01h..05h -> posicion 0..4
            final int finalCableadoPos = wiringSet;     // 0/1/2 coinciden con el spinner

            final boolean finalRecording = recording;
            handler.post(() -> {
                if (spinnerAmperes != null) {
                    spinnerAmperes.setSelection(finalSpinnerPos);
                }
                if (spinnerCableado != null) {
                    spinnerCableado.setSelection(finalCableadoPos);
                }
                // setSelection() entrega onItemSelected en el siguiente layout, no aca:
                // soltar el guard en esta misma linea hacia que el cambio programatico
                // se leyera como toque del usuario y disparara otro write.
                if (spinnerCableado != null) {
                    spinnerCableado.post(() -> skipSpinnerEvents = false);
                } else {
                    skipSpinnerEvents = false;
                }
                // Actualizar botón REC según estado real del dispositivo
                if (btnRec != null) {
                    if (finalRecording) {
                        btnRec.setBackgroundResource(R.drawable.button_rec_recording);
                        btnRec.setImageResource(R.drawable.ic_stop);
                        startRecordingAnimation();
                    } else {
                        btnRec.setBackgroundResource(R.drawable.button_rec);
                        btnRec.setImageResource(R.drawable.ic_rec);
                        stopRecordingAnimation();
                    }
                }
                if (tvRecLabel != null) {
                    tvRecLabel.setText(finalRecording ? "● GRABANDO" : "GRABAR");
                    tvRecLabel.setTextColor(finalRecording
                            ? android.graphics.Color.RED
                            : android.graphics.Color.parseColor("#666666"));
                }
                updateDiagram(); //  Actualizar imagen con la config recibida
                rebuildMeasurementTable(); //  Reconstruir tabla según cableado recibido
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
                int deviceId = energyData[0] & 0xFF;

                // Tabla 15 - METER_BASIC_STRC. El byte ID dice el cableado, y de ahí
                // salen cuántos canales trae la trama y desde cuál arranca.
                int canales, canalBase;
                switch (deviceId) {
                    case 0x12: canales = 4; canalBase = 0; break; // 1PH_2W_4LOADS  -> Load0..Load3
                    case 0x13: canales = 4; canalBase = 0; break; // 1PH_3W_2LOADS  -> CT-0..CT-3 (2 por carga)
                    case 0x34:                                    // 3PH_4W_1LOAD
                    case 0x44: canales = 3; canalBase = 1; break; // 3PH_1N_4W_1LOAD -> canal 0 = N
                    default:
                        canales = 0; canalBase = 0;   // ID fuera de Tabla 15 -> formato viejo
                        System.out.println("⚠️ NODE_CURRENT con ID 0x"
                                + String.format("%02X", deviceId) + " (no es Tabla 15)");
                        break;
                }

                if (canales > 0 && energyData.length >= 112) {
                    // W_WB[4]   bytes 16-31   (Int32  x4, 0.1 W)
                    // V_RMS[4]  bytes 80-87   (Uint16 x4, 0.1 Vrms)
                    // C_RMS[4]  bytes 88-95   (Uint16 x4, 0.1 Arms)
                    // HZ[4]     bytes 96-103  (Uint16 x4, 0.1 Hz)
                    // ANGLE[4]  bytes 104-111 (Uint16 x4, 0.1 grados)
                    for (int i = 0; i < voltajes.length; i++) {
                        if (i >= canales) {   // canal no usado por este cableado
                            voltajes[i] = corrientes[i] = potencias[i] = frecuencias[i] = angulos[i] = 0f;
                            continue;
                        }
                        int ch = canalBase + i;
                        voltajes[i]    = readUInt16LE(energyData, 80 + ch * 2) * 0.1f;
                        corrientes[i]  = readUInt16LE(energyData, 88 + ch * 2) * 0.1f;
                        frecuencias[i] = readUInt16LE(energyData, 96 + ch * 2) * 0.1f;
                        angulos[i]     = readUInt16LE(energyData, 104 + ch * 2) * 0.1f;
                        potencias[i]   = readInt32LE(energyData, 16 + ch * 4) * 0.1f;
                    }
                } else {
                    // ENERGY_3PHA_PAGE_STRC: channels at offsets 28, 40, 52
                    if (energyData.length >= 40) processChannel64Bytes(energyData, 28, 0, "CH1");
                    if (energyData.length >= 52) processChannel64Bytes(energyData, 40, 1, "CH2");
                    if (energyData.length >= 64) processChannel64Bytes(energyData, 52, 2, "CH3");
                    voltajes[3] = corrientes[3] = potencias[3] = frecuencias[3] = angulos[3] = 0f;
                }

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
                potencias[channelIndex] = readInt32LE(data, offset) * 0.1f;
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
            // Voltajes
            if (tvV1 != null) tvV1.setText(String.format("%.2f V",   voltajes[0]));
            if (tvV2 != null) tvV2.setText(String.format("%.2f V",   voltajes[1]));
            if (tvV3 != null) tvV3.setText(String.format("%.2f V",   voltajes[2]));
            if (tvV4 != null) tvV4.setText(String.format("%.2f V",   voltajes[3]));

            // Corrientes
            if (tvA1 != null) tvA1.setText(String.format("%.3f A",   corrientes[0]));
            if (tvA2 != null) tvA2.setText(String.format("%.3f A",   corrientes[1]));
            if (tvA3 != null) tvA3.setText(String.format("%.3f A",   corrientes[2]));
            if (tvA4 != null) tvA4.setText(String.format("%.3f A",   corrientes[3]));

            // Potencias
            if (tvW1 != null) tvW1.setText(String.format("%.2f W",   potencias[0]));
            if (tvW2 != null) tvW2.setText(String.format("%.2f W",   potencias[1]));
            if (tvW3 != null) tvW3.setText(String.format("%.2f W",   potencias[2]));
            if (tvW4 != null) tvW4.setText(String.format("%.2f W",   potencias[3]));

            // Frecuencias
            if (tvHz1 != null) tvHz1.setText(String.format("%.2f Hz", frecuencias[0]));
            if (tvHz2 != null) tvHz2.setText(String.format("%.2f Hz", frecuencias[1]));
            if (tvHz3 != null) tvHz3.setText(String.format("%.2f Hz", frecuencias[2]));
            if (tvHz4 != null) tvHz4.setText(String.format("%.2f Hz", frecuencias[3]));

            // Ángulos (convertidos a ±180°)
            for (int i = 0; i < 3; i++) {
                float angle = angulos[i];
                while (angle >  180f) angle -= 360f;
                while (angle < -180f) angle += 360f;
                String angleText = String.format("%.0f°", angle);

                if (i == 0) { if (tvpF1 != null) tvpF1.setText(angleText); }
                if (i == 1) { if (tvpF2 != null) tvpF2.setText(angleText); }
                if (i == 2) { if (tvpF3 != null) tvpF3.setText(angleText); }
            }
            if (tvpF4 != null) {
                float angle = angulos[3];
                while (angle >  180f) angle -= 360f;
                while (angle < -180f) angle += 360f;
                tvpF4.setText(String.format("%.0f°", angle));
            }

        } catch (Exception e) { }

    }

    private void updateChannelLabels() {
        try {
            switch (meteringTypeConfig) {
                case 0: // 1PH-2W-4LOADS
                    if (tvCH1 != null) tvCH1.setText("L1-N");
                    if (tvCH2 != null) tvCH2.setText("L2-N");
                    if (tvCH3 != null) tvCH3.setText("L3-N");
                    if (tvCH4 != null) tvCH4.setText("L4-N");
                    break;
                case 1: // 1PH-3W-2LOADS: 2 sensores por carga
                    if (tvCH1 != null) tvCH1.setText("CT-0");
                    if (tvCH2 != null) tvCH2.setText("CT-1");
                    if (tvCH3 != null) tvCH3.setText("CT-2");
                    if (tvCH4 != null) tvCH4.setText("CT-3");
                    break;
                default: // 3PH-4W-1LOAD
                    if (tvCH1 != null) tvCH1.setText("L1-N");
                    if (tvCH2 != null) tvCH2.setText("L2-N");
                    if (tvCH3 != null) tvCH3.setText("L3-N");
                    break;
            }
        } catch (Exception e) { }
    }

    private void updateFasores() {
        aplicarFasores(fasorVoltaje, fasorCorriente);
    }

    /** Canales que mide el cableado actual: 4 cargas / 2 cargas / 3 fases */
    private int canalesActivos() {
        switch (meteringTypeConfig) {
            case 0:  return 4;   // 1PH_2W_4LOADS
            case 1:  return 2;   // 1PH_3W_2LOADS
            default: return 3;   // 3PH_4W_1LOAD
        }
    }

    private void aplicarFasores(FasorView vistaVoltaje, FasorView vistaCorriente) {
        if (vistaVoltaje == null || vistaCorriente == null) return;

        try {
            int n = canalesActivos();
            String canales = (n == 3) ? null : " (" + n + " canales)";

            // El equipo no manda ángulo de tensión: cada fasor se dibuja sobre su propio eje
            vistaVoltaje.setChannelCount(n);
            vistaVoltaje.setTitle(canales == null ? "Voltajes Trifásicos" : "Voltajes" + canales);
            vistaVoltaje.setValues(voltajes, new float[n]);

            vistaCorriente.setChannelCount(n);
            vistaCorriente.setTitle(canales == null ? "Corrientes Trifásicas" : "Corrientes" + canales);
            vistaCorriente.setValues(corrientes, angulos);

        } catch (Exception e) {
        }
    }

    // =========================================================================
    // ===== TABLA DE MEDICIÓN DINÁMICA =======================================
    // =========================================================================

    private void rebuildMeasurementTable() {
        if (measurementTableLayout == null) return;
        measurementTableLayout.removeAllViews();
        // Reset TV refs so updateTextViews() doesn't write to stale views
        tvV1 = tvV2 = tvV3 = tvV4 = null;
        tvA1 = tvA2 = tvA3 = tvA4 = null;
        tvW1 = tvW2 = tvW3 = tvW4 = null;
        tvHz1 = tvHz2 = tvHz3 = tvHz4 = null;
        tvpF1 = tvpF2 = tvpF3 = tvpF4 = null;
        tvCH1 = tvCH2 = tvCH3 = tvCH4 = null;

        switch (meteringTypeConfig) {
            case 0: buildTable_1PH_2W_4LOADS(); break;
            case 1: buildTable_1PH_3W_2LOADS(); break;
            default: buildTable_3PH_4W_1LOAD(); break;
        }
        initializeDisplayValues();
    }

    /** 3PH-4W-1LOAD: tabla única Variable | PH1 | PH2 | PH3 */
    private void buildTable_3PH_4W_1LOAD() {
        // Fila de encabezados de columna (L1 negro, L2 rojo, L3 azul)
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        headerRow.addView(makeColHeader("L1", Color.BLACK,             Color.WHITE));
        headerRow.addView(makeColHeader("L2", Color.parseColor("#FF0000"), Color.WHITE));
        headerRow.addView(makeColHeader("L3", Color.parseColor("#007BFF"), Color.WHITE));
        measurementTableLayout.addView(headerRow);

        // Tres columnas de datos
        LinearLayout colsRow = new LinearLayout(this);
        colsRow.setOrientation(LinearLayout.HORIZONTAL);
        colsRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col1 = makeDataColumn(Color.parseColor("#424242"));
        tvV1  = addValueCell(col1, "0.00 V");
        tvA1  = addValueCell(col1, "0.000 A");
        tvW1  = addValueCell(col1, "0.00 W");
        tvHz1 = addValueCell(col1, "0.00 Hz");
        tvpF1 = addValueCell(col1, "0°");

        LinearLayout col2 = makeDataColumn(Color.parseColor("#FF0000"));
        tvV2  = addValueCell(col2, "0.00 V");
        tvA2  = addValueCell(col2, "0.000 A");
        tvW2  = addValueCell(col2, "0.00 W");
        tvHz2 = addValueCell(col2, "0.00 Hz");
        tvpF2 = addValueCell(col2, "0°");

        LinearLayout col3 = makeDataColumn(Color.parseColor("#007BFF"));
        tvV3  = addValueCell(col3, "0.00 V");
        tvA3  = addValueCell(col3, "0.000 A");
        tvW3  = addValueCell(col3, "0.00 W");
        tvHz3 = addValueCell(col3, "0.00 Hz");
        tvpF3 = addValueCell(col3, "0°");

        LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        colLp.setMargins(0, 0, dp(2), 0);
        colsRow.addView(col1, colLp);
        LinearLayout.LayoutParams colLp2 = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        colLp2.setMargins(dp(1), 0, dp(1), 0);
        colsRow.addView(col2, colLp2);
        colsRow.addView(col3, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        measurementTableLayout.addView(colsRow);
    }

    /** 1PH-3W-2LOADS: 2 cargas, cada una con sus 2 sensores (CT-0/CT-1 y CT-2/CT-3) */
    private void buildTable_1PH_3W_2LOADS() {
        int oscuro = Color.parseColor("#424242");
        int rojo   = Color.parseColor("#FF0000");

        // Encabezado: cada carga ocupa el ancho de sus dos sensores
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        headerRow.addView(makeColHeader("LOAD_0", Color.BLACK, Color.WHITE),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        headerRow.addView(makeColHeader("LOAD_1", rojo, Color.WHITE),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
        measurementTableLayout.addView(headerRow);

        // Segun el diagrama: LOAD_0 mide con CT-0 y CT-2, LOAD_1 con CT-1 y CT-3.
        // La columna i muestra el CT ctPorColumna[i] (indice directo del arreglo de datos).
        int[] ctPorColumna = {0, 2, 1, 3};
        int[] colores      = {oscuro, Color.parseColor("#616161"),
                              rojo,   Color.parseColor("#C62828")};

        LinearLayout colsRow = new LinearLayout(this);
        colsRow.setOrientation(LinearLayout.HORIZONTAL);
        colsRow.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        for (int i = 0; i < 4; i++) {
            int ct = ctPorColumna[i];
            LinearLayout col = makeDataColumn(colores[i]);
            TextView tvCH = addChannelCell(col, "CT-" + ct);
            TextView tvV  = addValueCell(col, "0.00 V");
            TextView tvA  = addValueCell(col, "0.000 A");
            TextView tvW  = addValueCell(col, "0.00 W");
            TextView tvHz = addValueCell(col, "0.00 Hz");
            TextView tvPF = addValueCell(col, "0°");

            // Los tv*N siguen atados al canal N-1 del arreglo, no a la posicion visual
            switch (ct) {
                case 0: tvCH1=tvCH; tvV1=tvV; tvA1=tvA; tvW1=tvW; tvHz1=tvHz; tvpF1=tvPF; break;
                case 1: tvCH2=tvCH; tvV2=tvV; tvA2=tvA; tvW2=tvW; tvHz2=tvHz; tvpF2=tvPF; break;
                case 2: tvCH3=tvCH; tvV3=tvV; tvA3=tvA; tvW3=tvW; tvHz3=tvHz; tvpF3=tvPF; break;
                case 3: tvCH4=tvCH; tvV4=tvV; tvA4=tvA; tvW4=tvW; tvHz4=tvHz; tvpF4=tvPF; break;
            }

            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            // separacion mayor entre cargas que entre los sensores de una misma carga
            colLp.setMargins(0, 0, i == 1 ? dp(6) : (i < 3 ? dp(1) : 0), 0);
            colsRow.addView(col, colLp);
        }
        measurementTableLayout.addView(colsRow);
    }

    /** 1PH-2W-4LOADS: 4 bloques de carga, colores alternos negro/rojo */
    private void buildTable_1PH_2W_4LOADS() {
        int[] bgColors   = {Color.parseColor("#424242"), Color.parseColor("#FF0000"),
                            Color.parseColor("#424242"), Color.parseColor("#FF0000")};
        String[] chLabels   = {"L1-N", "L2-N", "L3-N", "L4-N"};
        String[] loadLabels = {"Load 1", "Load 2", "Load 3", "Load 4"};

        // 4 columnas en una sola fila horizontal para que todas sean visibles
        LinearLayout allCols = new LinearLayout(this);
        allCols.setOrientation(LinearLayout.HORIZONTAL);
        allCols.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        for (int li = 0; li < 4; li++) {
            LinearLayout col = makeDataColumn(bgColors[li]);

            // Load label header cell
            TextView tvLoad = new TextView(this);
            tvLoad.setText(loadLabels[li]);
            tvLoad.setTextColor(Color.WHITE);
            tvLoad.setTextSize(9f);
            tvLoad.setTypeface(null, Typeface.BOLD);
            tvLoad.setGravity(Gravity.CENTER);
            tvLoad.setPadding(dp(2), dp(3), dp(2), dp(3));
            tvLoad.setBackgroundColor(Color.parseColor("#1A1A1A"));
            tvLoad.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            col.addView(tvLoad);

            TextView tvCH = addChannelCell(col, chLabels[li]);
            TextView tvV  = addValueCell(col, "0.00 V");
            TextView tvA  = addValueCell(col, "0.000 A");
            TextView tvW  = addValueCell(col, "0.00 W");
            TextView tvHz = addValueCell(col, "0.00 Hz");
            TextView tvPF = addValueCell(col, "0°");

            switch (li) {
                case 0: tvCH1=tvCH; tvV1=tvV; tvA1=tvA; tvW1=tvW; tvHz1=tvHz; tvpF1=tvPF; break;
                case 1: tvCH2=tvCH; tvV2=tvV; tvA2=tvA; tvW2=tvW; tvHz2=tvHz; tvpF2=tvPF; break;
                case 2: tvCH3=tvCH; tvV3=tvV; tvA3=tvA; tvW3=tvW; tvHz3=tvHz; tvpF3=tvPF; break;
                case 3: tvCH4=tvCH; tvV4=tvV; tvA4=tvA; tvW4=tvW; tvHz4=tvHz; tvpF4=tvPF; break;
            }

            LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (li < 3) colLp.setMargins(0, 0, dp(2), 0);
            allCols.addView(col, colLp);
        }
        measurementTableLayout.addView(allCols, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));
    }

    /** Columna de datos (vertical, fondo de color) */
    private LinearLayout makeDataColumn(int bgColor) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(bgColor);
        col.setPadding(dp(4), dp(4), dp(4), dp(4));
        return col;
    }

    /** Celda de encabezado de columna (nombre de fase: L1, L2, L3) */
    private TextView makeColHeader(String text, int bgColor, int textColor) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(textColor);
        tv.setTextSize(13f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(bgColor);
        tv.setPadding(dp(4), dp(6), dp(4), dp(6));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return tv;
    }

    /** Celda de canal (L1-N, L2-N…) dentro de una columna */
    private TextView addChannelCell(LinearLayout col, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(10f);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(2), dp(3), dp(2), dp(3));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        col.addView(tv);
        return tv;
    }

    /** Celda de valor eléctrico dentro de una columna */
    private TextView addValueCell(LinearLayout col, String defaultText) {
        // AppCompat + autosize: con 4 columnas "134.00 V" no entra a 10sp y se partia
        // en dos renglones, lo que estiraba la tabla y cortaba la fila del angulo.
        androidx.appcompat.widget.AppCompatTextView tv =
                new androidx.appcompat.widget.AppCompatTextView(this);
        tv.setText(defaultText);
        tv.setTextColor(Color.WHITE);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundColor(0xAA000000);
        tv.setSingleLine(true);
        tv.setPadding(dp(2), dp(4), dp(2), dp(4));
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(
                tv, 7, 10, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lp.setMargins(0, dp(1), 0, 0);
        tv.setLayoutParams(lp);
        col.addView(tv);
        return tv;
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
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

        if (tvV4  != null) tvV4.setText("0.00 V");
        if (tvA4  != null) tvA4.setText("0.000 A");
        if (tvW4  != null) tvW4.setText("0.00 W");
        if (tvHz4 != null) tvHz4.setText("0.00 Hz");
        if (tvpF4 != null) tvpF4.setText("0°");
        if (tvCH4 != null) tvCH4.setText("L4-N");
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
            if (btnConfigSub1g != null) btnConfigSub1g.setEnabled(enabled);
            if (btnMemoria != null) btnMemoria.setEnabled(enabled);
        });
    }

    private void setSpinnersEnabled(boolean enabled) {
        handler.post(() -> {
            if (spinnerCableado != null) spinnerCableado.setEnabled(enabled); //  Ya no es siempre false
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

        //  Detener handlers
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }

        if (autoReadHandler != null) {
            autoReadHandler.removeCallbacksAndMessages(null);
        }

        //  Desconectar socket
        disconnectFromDeviceInternal();

        //  Shutdown del executor con timeout
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