package de.kai_morich.simple_bluetooth_terminal;

// ===== IMPORTS COMPLETOS =====
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FasoresActivity extends AppCompatActivity {

    // ===== UI ELEMENTS =====
    private Spinner spinnerCableado, spinnerTiempo, spinnerAmperes;
    private ImageView imageDiagram;
    private ImageButton btnPlay;
    private LinearLayout btnBackToMenu;
    private Handler handler = new Handler(Looper.getMainLooper());

    // ✅ USAR FasorView EXISTENTE PERO CON MODO 3 EJES
    private FasorView fasorVoltaje, fasorCorriente;

    // ===== MODAL DEL DIAGRAMA =====
    private View diagramModal;
    private ImageView modalDiagramImage;
    private boolean isModalVisible = false;

    // TextViews para mediciones
    private TextView tvV1, tvV2, tvV3, tvA1, tvA2, tvA3, tvW1, tvW2, tvW3, tvHz1, tvHz2, tvHz3;
    private TextView tvpF1, tvpF2, tvpF3; // Factor de potencia
    private TextView tvCH1, tvCH2, tvCH3; // Headers de canal

    // ===== CONEXIÓN TCP INDEPENDIENTE =====
    private Socket socket;
    private OutputStream outputStream;
    private ExecutorService executor;
    private String deviceIp = "192.168.4.1";
    private int devicePort = 333;
    private boolean isConnectedToDevice = false;

    // ===== CONFIGURACIÓN DEL MEDIDOR =====
    private int periodConfig = 1; // 0=1min, 1=5min, 2=10min, 3=15min
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
    private float[] voltajes = {0.0f, 0.0f, 0.0f}; // CH1, CH2, CH3
    private float[] corrientes = {0.0f, 0.0f, 0.0f}; // CH1, CH2, CH3
    private float[] potencias = {0.0f, 0.0f, 0.0f}; // CH1, CH2, CH3 (W)
    private float[] frecuencias = {0.0f, 0.0f, 0.0f}; // CH1, CH2, CH3
    private float[] angulos = {0.0f, 120.0f, 240.0f}; // CH1, CH2, CH3

    // ===== ESTADO =====
    private long tiempoInicio;
    private int contadorMuestras = 0;
    private boolean isWaitingResponse = false;
    private boolean configurationSynced = false;
    private boolean skipSpinnerEvents = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fasores);

        executor = Executors.newCachedThreadPool();

        initializeViews();
        setupSpinners();
        setupButtons();
        setupAutoReadTask();

        // CONEXIÓN INDEPENDIENTE
        processIntentDataAndConnect();
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

        showToast("📡 Conectando a " + deviceIp + ":" + devicePort);

        // Conectar inmediatamente con nuestra propia conexión
        handler.postDelayed(() -> connectToDeviceIndependent(), 1000);
    }

    private void initializeViews() {
        // Spinners y controles
        spinnerCableado = findViewById(R.id.spinnerCableado);
        spinnerTiempo = findViewById(R.id.spinnerTiempo);
        spinnerAmperes = findViewById(R.id.spinnerAmperes);
        imageDiagram = findViewById(R.id.imageDiagram);
        btnPlay = findViewById(R.id.btnPlay);
        btnBackToMenu = findViewById(R.id.btnBackToMenu);

        // ✅ FASORES CON FasorView EXISTENTE PERO EN MODO 3 EJES
        fasorVoltaje = findViewById(R.id.fasorVoltaje);
        fasorCorriente = findViewById(R.id.fasorCorriente);

        // ✅ CONFIGURAR PARA MODO 3 EJES
        if (fasorVoltaje != null) {
            fasorVoltaje.setThreeAxisMode(true);  // ← ACTIVAR MODO 3 EJES
            fasorVoltaje.setTitle("Voltajes Trifásicos");
            fasorVoltaje.setUnit("V");
            fasorVoltaje.setAutoScale(true);
            System.out.println("✅ FASORES - FasorVoltaje configurado en modo 3 ejes");
        }

        if (fasorCorriente != null) {
            fasorCorriente.setThreeAxisMode(true);  // ← ACTIVAR MODO 3 EJES
            fasorCorriente.setTitle("Corrientes Trifásicas");
            fasorCorriente.setUnit("A");
            fasorCorriente.setAutoScale(true);
            System.out.println("✅ FASORES - FasorCorriente configurado en modo 3 ejes");
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

        // Inicializar display con valores en cero
        initializeDisplayValues();
    }

    private void setupSpinners() {
        // CABLEADO - Solo Carga Trifásica
        String[] cableadoOptions = {"3 = Carga Trifásica"};
        setupSpinnerAdapter(spinnerCableado, cableadoOptions);

        // TIEMPO - Basado en PERIOD del protocolo
        String[] tiempoOptions = {
                "0 = 1 minuto", "1 = 5 minutos", "2 = 10 minutos", "3 = 15 minutos"
        };
        setupSpinnerAdapter(spinnerTiempo, tiempoOptions);

        // AMPERAJE - Basado en SENSORS del protocolo
        String[] amperesOptions = {
                "0 = Shunt-20A", "1 = CT-50A", "2 = CT-200A",
                "3 = CT-400A", "4 = RoGo-1000A", "5 = RoGo-3000A"
        };
        setupSpinnerAdapter(spinnerAmperes, amperesOptions);

        // Configurar listeners
        setupSpinnerListeners();

        // Valores por defecto
        skipSpinnerEvents = true;
        spinnerTiempo.setSelection(1); // 5 minutos por defecto
        spinnerAmperes.setSelection(1); // CT-50A por defecto
        spinnerCableado.setSelection(0); // Carga Trifásica
        skipSpinnerEvents = false;

        // Deshabilitar spinners hasta que se sincronice la configuración
        setSpinnersEnabled(false);
    }

    private void setupSpinnerAdapter(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    // ===== LISTENERS DE SPINNERS CON ESCRITURA DE CONFIGURACIÓN =====
    private void setupSpinnerListeners() {
        spinnerCableado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // ✅ CABLEADO SE MANTIENE FIJO EN TRIFÁSICO
                if (!skipSpinnerEvents && configurationSynced) {
                    System.out.println("📋 FASORES - Cableado fijo en Trifásico");
                    meteringTypeConfig = 3;
                    tipoCableado = 3;
                    updateDiagram();

                    // ✅ NO ESCRIBIR POR CAMBIO DE CABLEADO
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerTiempo.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    System.out.println("📋 FASORES - Cambio en TIEMPO: " + position);

                    // ✅ ESCRIBIR CONFIGURACIÓN AUTOMÁTICAMENTE
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse && validateConfigurationBeforeWrite()) {
                            writeDeviceConfigurationFromSpinners();
                        }
                    }, 500); // Delay para evitar múltiples escrituras
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerAmperes.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!skipSpinnerEvents && configurationSynced) {
                    System.out.println("📋 FASORES - Cambio en AMPERES: " + position);
                    updateAmperesRange(position);

                    // ✅ ESCRIBIR CONFIGURACIÓN AUTOMÁTICAMENTE
                    handler.postDelayed(() -> {
                        if (!isWaitingResponse && validateConfigurationBeforeWrite()) {
                            writeDeviceConfigurationFromSpinners();
                        }
                    }, 500); // Delay para evitar múltiples escrituras
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    /**
     * ✅ MÉTODO PARA ESCRIBIR CONFIGURACIÓN DESDE SPINNERS
     */
    private void writeDeviceConfigurationFromSpinners() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }

        if (isWaitingResponse) {
            showToast("⏳ Esperando respuesta anterior...");
            return;
        }

        try {
            // ✅ OBTENER VALORES DE SPINNERS
            int periodValue = spinnerTiempo.getSelectedItemPosition(); // 0=1min, 1=5min, 2=10min, 3=15min
            int sensorsValue = spinnerAmperes.getSelectedItemPosition(); // 0=20A, 1=50A, 2=200A, 3=400A, 4=1000A, 5=3000A
            int meteringTypeValue = 3; // ✅ FORZADO A CARGA TRIFÁSICA
            boolean recordingValue = true; // ✅ SIEMPRE ACTIVADO

            System.out.println("🔧 FASORES - === ESCRIBIENDO CONFIGURACIÓN ===");
            System.out.printf("   PERIOD: %d, SENSORS: %d, METERING_TYPE: %d, RECORDING: %s%n",
                    periodValue, sensorsValue, meteringTypeValue, recordingValue);

            // ✅ CREAR COMANDO NODE_SETTINGS_WRITE USANDO OctoNetCommandEncoder
            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recordingValue,    // REC_ON/OFF = true
                    periodValue,       // PERIOD desde spinner (0-3)
                    sensorsValue,      // SENSORS desde spinner (0-5)
                    meteringTypeValue  // METERING_TYPE = 3 (Carga Trifásica)
            );

            String hex = OctoNetCommandEncoder.bytesToHexString(command);
            System.out.println("✅ FASORES - Comando NODE_SETTINGS_WRITE: " + hex);

            // ✅ VERIFICAR COMANDO ANTES DE ENVIAR
            if (!OctoNetCommandEncoder.verifyChecksum(command)) {
                System.out.println("❌ FASORES - Checksum incorrecto en comando");
                showToast("❌ Error generando comando");
                return;
            }

            // ✅ ENVIAR COMANDO
            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            // ✅ ACTUALIZAR VARIABLES LOCALES
            periodConfig = periodValue;
            sensorsConfig = sensorsValue;
            meteringTypeConfig = meteringTypeValue;
            recordingConfig = recordingValue;
            updateAmperesRange(sensorsValue);

            showToast("📝 Escribiendo configuración...");
            System.out.println("📤 FASORES - Configuración enviada al dispositivo");

            // ✅ TIMEOUT PARA WRITE (5 segundos)
            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    System.out.println("⏰ FASORES - Timeout NODE_SETTINGS_WRITE (5s)");
                    isWaitingResponse = false;
                    showToast("⏰ Timeout escribiendo configuración");
                }
            }, 5000);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error escribiendo configuración: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error: " + e.getMessage());
            isWaitingResponse = false;
        }
    }

    /**
     * ✅ VALIDA QUE LA CONFIGURACIÓN SEA CONSISTENTE
     */
    private boolean validateConfigurationBeforeWrite() {
        try {
            int period = spinnerTiempo.getSelectedItemPosition();
            int sensors = spinnerAmperes.getSelectedItemPosition();

            if (period < 0 || period > 3) {
                showToast("❌ Período inválido");
                return false;
            }

            if (sensors < 0 || sensors > 5) {
                showToast("❌ Sensor inválido");
                return false;
            }

            return true;
        } catch (Exception e) {
            System.out.println("❌ FASORES - Error validando configuración: " + e.getMessage());
            return false;
        }
    }

    private void updateAmperesRange(int sensorIndex) {
        int[] amperesValues = {20, 50, 200, 400, 1000, 3000};
        if (sensorIndex >= 0 && sensorIndex < amperesValues.length) {
            rangoAmperes = amperesValues[sensorIndex];
        }
    }

    private void setupButtons() {
        // Botón Play/Stop
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

        // ✅ NUEVO: Click en imagen del diagrama para abrir modal
        imageDiagram.setOnClickListener(v -> {
            showDiagramModal();
        });

        // Botón de regreso
        btnBackToMenu.setOnClickListener(v -> {
            stopDataAcquisition();
            disconnectFromDevice();

            // ✅ CAMBIO: Ir al LoginActivity en lugar de MainActivity
            Intent intent = new Intent(FasoresActivity.this, de.kai_morich.simple_bluetooth_terminal.activities.LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });
    }

    // ===== MÉTODOS PARA EL MODAL DEL DIAGRAMA =====

    /**
     * ✅ CREAR EL MODAL DEL DIAGRAMA DINÁMICAMENTE
     */
    private void createDiagramModal() {
        if (diagramModal != null) return; // Ya existe

        // Crear el layout del modal
        LinearLayout modalContent = new LinearLayout(this);
        modalContent.setOrientation(LinearLayout.VERTICAL);
        modalContent.setGravity(Gravity.CENTER);
        modalContent.setPadding(40, 40, 40, 40);

        // Título del modal
        TextView modalTitle = new TextView(this);

        modalTitle.setTextColor(Color.WHITE);
        modalTitle.setTextSize(24f);
        modalTitle.setGravity(Gravity.CENTER);
        modalTitle.setTypeface(null, Typeface.BOLD);
        modalTitle.setPadding(0, 0, 0, 30);
        modalContent.addView(modalTitle);

        // Imagen del diagrama grande
        modalDiagramImage = new ImageView(this);
        modalDiagramImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        modalDiagramImage.setAdjustViewBounds(true);

        // Configurar tamaño del diagrama
        LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
        );
        imageParams.weight = 1; // Ocupar el espacio disponible
        imageParams.setMargins(20, 20, 20, 20);
        modalDiagramImage.setLayoutParams(imageParams);

        // Fondo blanco para la imagen
        modalDiagramImage.setBackgroundColor(Color.WHITE);
        modalDiagramImage.setPadding(20, 20, 20, 20);

        // Bordes redondeados
        modalDiagramImage.setBackground(createRoundedBackground());

        modalContent.addView(modalDiagramImage);

        // Botón de cerrar
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
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        buttonParams.setMargins(0, 30, 0, 0);
        buttonParams.gravity = Gravity.CENTER;
        closeButton.setLayoutParams(buttonParams);

        modalContent.addView(closeButton);

        // Instrucciones
        TextView instructions = new TextView(this);

        instructions.setTextColor(Color.GRAY);
        instructions.setTextSize(14f);
        instructions.setGravity(Gravity.CENTER);
        instructions.setPadding(0, 15, 0, 0);
        modalContent.addView(instructions);

        // Crear el overlay modal con fondo semi-transparente
        FrameLayout modalFrame = new FrameLayout(this) {
            @Override
            protected void onDraw(Canvas canvas) {
                // Fondo semi-transparente
                canvas.drawColor(Color.argb(200, 0, 0, 0));
                super.onDraw(canvas);
            }
        };

        modalFrame.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        modalFrame.setClickable(true);
        modalFrame.setFocusable(true);
        modalFrame.setVisibility(View.GONE);

        modalFrame.addView(modalContent);

        // Click fuera del contenido para cerrar
        modalFrame.setOnClickListener(v -> hideDiagramModal());

        // Prevenir que clicks en el contenido cierren el modal
        modalContent.setOnClickListener(v -> {
            // No hacer nada - evitar que se propague el click
        });

        diagramModal = modalFrame;
    }

    /**
     * ✅ CREAR FONDO REDONDEADO PARA LA IMAGEN
     */
    private Drawable createRoundedBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(15f);
        drawable.setStroke(3, Color.rgb(200, 200, 200));
        return drawable;
    }

    /**
     * ✅ MOSTRAR EL MODAL CON ANIMACIÓN
     */
    private void showDiagramModal() {
        if (isModalVisible) return;

        // Crear modal si no existe
        createDiagramModal();

        // Actualizar la imagen del modal con la misma que se muestra
        updateModalDiagram();

        // Agregar al layout principal
        ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
        rootView.addView(diagramModal);

        // Mostrar con animación
        diagramModal.setVisibility(View.VISIBLE);
        diagramModal.setAlpha(0f);
        diagramModal.animate()
                .alpha(1f)
                .setDuration(300)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        isModalVisible = true;

        showToast("📐 Diagrama ampliado - Toca ✕ para cerrar");
        System.out.println("✅ FASORES - Modal del diagrama mostrado");
    }

    /**
     * ✅ OCULTAR EL MODAL CON ANIMACIÓN
     */
    private void hideDiagramModal() {
        if (!isModalVisible || diagramModal == null) return;

        diagramModal.animate()
                .alpha(0f)
                .setDuration(250)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    diagramModal.setVisibility(View.GONE);

                    // Remover del layout
                    ViewGroup rootView = (ViewGroup) findViewById(android.R.id.content);
                    rootView.removeView(diagramModal);

                    isModalVisible = false;
                    System.out.println("✅ FASORES - Modal del diagrama cerrado");
                })
                .start();
    }

    /**
     * ✅ ACTUALIZAR LA IMAGEN DEL MODAL
     */
    private void updateModalDiagram() {
        if (modalDiagramImage == null) return;

        // Usar la misma imagen que el diagrama principal
        // Siempre será diagrama trifásico en esta actividad
        modalDiagramImage.setImageResource(R.drawable.diagram_3p4w_n);

        System.out.println("✅ FASORES - Imagen del modal actualizada");
    }

    private void setupAutoReadTask() {
        // Task para lectura automática CADA 5 SEGUNDOS
        autoReadTask = new Runnable() {
            @Override
            public void run() {
                if (autoReadEnabled && isConnectedToDevice && configurationSynced) {
                    requestCurrentData();
                    // Programar siguiente lectura en 5 segundos
                    autoReadHandler.postDelayed(this, AUTO_READ_INTERVAL);
                }
            }
        };

        updateDiagram();
    }

    // ===== CONEXIÓN TCP MEJORADA CON MANEJO CORRECTO DE RECURSOS =====

    private void connectToDeviceIndependent() {
        // ✅ EVITAR CONEXIONES DUPLICADAS
        if (isConnectedToDevice) {
            System.out.println("⚠️ FASORES - Ya hay conexión activa, evitando duplicado");
            return;
        }

        showToast("🔗 Estableciendo conexión independiente...");

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

                // ✅ OBTENER STREAMS CORRECTAMENTE
                outputStream = socket.getOutputStream();

                handler.post(() -> {
                    isConnectedToDevice = true;
                    showToast("✅ Conectado independientemente a " + deviceIp);
                    System.out.println("✅ FASORES - Conexión independiente establecida");

                    // Iniciar hilo de recepción mejorado
                    startIndependentReceiveThreadImproved();

                    // ✅ DELAY MÁS CORTO PARA SETTINGS
                    handler.postDelayed(() -> readDeviceConfigurationIndependent(), 500);
                });

            } catch (java.net.ConnectException e) {
                System.out.println("❌ FASORES - No se pudo conectar: " + e.getMessage());
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Dispositivo no responde en " + deviceIp + ":" + devicePort);
                });
            } catch (java.net.SocketTimeoutException e) {
                System.out.println("❌ FASORES - Timeout de conexión: " + e.getMessage());
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Timeout de conexión");
                });
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error de conexión: " + e.getMessage());
                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Error de conexión: " + e.getMessage());
                });
            }
        });
    }

    // ===== HILO DE RECEPCIÓN MEJORADO =====
    private void startIndependentReceiveThreadImproved() {
        executor.execute(() -> {
            byte[] buffer = new byte[2048];
            System.out.println("🔄 FASORES - Hilo de recepción mejorado iniciado");

            try {
                while (isConnectedToDevice && !Thread.currentThread().isInterrupted()) {
                    try {
                        // ✅ VERIFICAR QUE EL SOCKET SIGUE CONECTADO
                        if (socket == null || socket.isClosed() || !socket.isConnected()) {
                            System.out.println("❌ FASORES - Socket desconectado");
                            break;
                        }

                        System.out.println("⏳ FASORES - Esperando datos...");

                        int bytesRead = socket.getInputStream().read(buffer);

                        if (bytesRead > 0) {
                            byte[] data = new byte[bytesRead];
                            System.arraycopy(buffer, 0, data, 0, bytesRead);

                            String hexString = OctoNetCommandEncoder.bytesToHexString(data);
                            System.out.println("📨 FASORES - Datos recibidos (" + bytesRead + " bytes): " + hexString);

                            handler.post(() -> processReceivedDataIndependent(data));
                        } else {
                            System.out.println("❌ FASORES - Socket cerrado por el servidor");
                            break;
                        }

                    } catch (java.net.SocketTimeoutException e) {
                        // ✅ TIMEOUT NORMAL - NO ES ERROR
                        System.out.println("⏰ FASORES - Timeout de lectura (normal)");
                        continue;
                    } catch (java.net.SocketException e) {
                        if (isConnectedToDevice) {
                            System.out.println("🔌 FASORES - Socket desconectado: " + e.getMessage());
                            break;
                        }
                    } catch (IOException e) {
                        if (isConnectedToDevice) {
                            System.out.println("❌ FASORES - Error en recepción: " + e.getMessage());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error general en recepción: " + e.getMessage());
            }

            // ✅ MANEJO CORRECTO DE DESCONEXIÓN
            if (isConnectedToDevice) {
                handler.post(() -> {
                    isConnectedToDevice = false;
                    configurationSynced = false;
                    setControlsEnabled(false);
                    showToast("🔌 Conexión perdida");

                    // ✅ RECONECTAR AUTOMÁTICAMENTE
                    handler.postDelayed(() -> {
                        showToast("🔄 Intentando reconectar...");
                        connectToDeviceIndependent();
                    }, 3000);
                });
            }

            System.out.println("🔚 FASORES - Hilo de recepción terminado");
        });
    }

    private void disconnectFromDevice() {
        System.out.println("🔌 FASORES - Iniciando desconexión...");

        isConnectedToDevice = false;
        configurationSynced = false;
        stopDataAcquisition();

        executor.execute(() -> disconnectFromDeviceInternal());

        handler.post(() -> showToast("🔌 Desconectado"));
    }

    private void disconnectFromDeviceInternal() {
        try {
            // ✅ CERRAR OUTPUTSTREAM PRIMERO
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

            // ✅ CERRAR SOCKET
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

    // ===== COMUNICACIÓN USANDO OctoNetCommandEncoder =====

    private void readDeviceConfigurationIndependent() {
        if (!isConnectedToDevice) return;

        // ✅ RESET FORZADO SI ESTÁ COLGADO
        if (isWaitingResponse) {
            System.out.println("⚠️ FASORES - Reset forzado antes de leer config");
            isWaitingResponse = false;
        }

        try {
            System.out.println("🔍 FASORES - === INICIANDO LECTURA NODE_SETTINGS ===");

            byte[] command = OctoNetCommandEncoder.createNodeSettingsReadCommand();

            String hex = OctoNetCommandEncoder.bytesToHexString(command);
            System.out.println("✅ FASORES - Comando NODE_SETTINGS: " + hex);

            if (OctoNetCommandEncoder.verifyChecksum(command)) {
                System.out.println("✅ FASORES - Checksum verificado");
            }

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;
            showToast("📖 Leyendo configuración...");

            // ✅ TIMEOUT MÁS CORTO PARA CONFIG (5 segundos)
            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    System.out.println("⏰ FASORES - Timeout NODE_SETTINGS (5s) - continuando sin config");
                    isWaitingResponse = false;
                    configurationSynced = true; // Permitir continuar
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);
                    showToast("⚠️ Sin respuesta config - continuando...");
                }
            }, 5000); // ✅ REDUCIDO A 5 SEGUNDOS

        } catch (Exception e) {
            showToast("❌ Error al leer configuración");
            System.out.println("❌ FASORES - Error: " + e.getMessage());
            isWaitingResponse = false;
            configurationSynced = true; // Permitir continuar sin config
            setControlsEnabled(true);
        }
    }

    private void requestCurrentData() {
        if (!isConnectedToDevice) {
            System.out.println("❌ FASORES - Sin conexión para NODE_CURRENT");
            return;
        }

        // ✅ VERIFICAR ESTADO DEL SOCKET
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            System.out.println("❌ FASORES - Socket no válido, reconectando...");
            connectToDeviceIndependent();
            return;
        }

        // ✅ RESET FORZADO DEL FLAG
        if (isWaitingResponse) {
            System.out.println("⚠️ FASORES - Reset forzado de isWaitingResponse");
            isWaitingResponse = false;
        }

        try {
            System.out.println("🔍 FASORES - === SOLICITUD NODE_CURRENT ===");

            byte[] command = OctoNetCommandEncoder.createNodeCurrentReadCommand();

            String hex = OctoNetCommandEncoder.bytesToHexString(command);
            System.out.println("✅ FASORES - Comando NODE_CURRENT: " + hex);

            if (!OctoNetCommandEncoder.verifyChecksum(command)) {
                System.out.println("❌ FASORES - Checksum incorrecto");
                return;
            }

            sendTcpCommandIndependent(command);
            isWaitingResponse = true;

            // ✅ TIMEOUT CORTO PARA NODE_CURRENT (3 segundos)
            handler.postDelayed(() -> {
                if (isWaitingResponse) {
                    System.out.println("⏰ FASORES - Timeout NODE_CURRENT (3s)");
                    isWaitingResponse = false;
                }
            }, 3000); // ✅ REDUCIDO A 3 SEGUNDOS

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error NODE_CURRENT: " + e.getMessage());
            isWaitingResponse = false;
        }
    }

    // ===== MÉTODO sendTcpCommandIndependent MEJORADO =====
    private void sendTcpCommandIndependent(byte[] command) {
        if (!isConnectedToDevice || outputStream == null) {
            System.out.println("❌ FASORES - No hay conexión válida");
            return;
        }

        // ✅ VERIFICAR ESTADO DEL SOCKET ANTES DE ENVIAR
        if (socket == null || socket.isClosed() || !socket.isConnected()) {
            System.out.println("❌ FASORES - Socket no válido antes de enviar");
            isConnectedToDevice = false;
            return;
        }

        String hexString = OctoNetCommandEncoder.bytesToHexString(command);
        System.out.println("📤 FASORES - Enviando: " + hexString + " (" + command.length + " bytes)");

        executor.execute(() -> {
            try {
                // ✅ VERIFICAR STREAM ANTES DE ESCRIBIR
                if (outputStream != null) {
                    outputStream.write(command);
                    outputStream.flush();
                    System.out.println("✅ FASORES - Comando enviado correctamente");
                } else {
                    System.out.println("❌ FASORES - OutputStream es null");
                }

            } catch (IOException e) {
                System.out.println("❌ FASORES - Error enviando: " + e.getMessage());

                handler.post(() -> {
                    isConnectedToDevice = false;
                    setControlsEnabled(false);
                    showToast("❌ Error de envío - reconectando...");

                    // ✅ RECONECTAR AUTOMÁTICAMENTE
                    handler.postDelayed(() -> connectToDeviceIndependent(), 1000);
                });
            }
        });
    }

    // ===== PROCESAMIENTO CON MANEJO DE WRITE RESPONSE =====

    private void processReceivedDataIndependent(byte[] data) {
        // ✅ RESET INMEDIATO DEL FLAG
        isWaitingResponse = false;

        if (data == null || data.length < 4) {
            System.out.println("❌ FASORES - Respuesta inválida o muy corta");
            return;
        }

        // ✅ USAR MÉTODO DE VALIDACIÓN DE OctoNetCommandEncoder
        if (!OctoNetCommandEncoder.validateCommandStructure(data)) {
            System.out.println("❌ FASORES - Estructura de respuesta inválida");
            return;
        }

        // ✅ VERIFICAR CHECKSUM USANDO OctoNetCommandEncoder
        if (!OctoNetCommandEncoder.verifyChecksum(data)) {
            System.out.println("❌ FASORES - Checksum de respuesta incorrecto");
            return;
        }

        int startByte = data[0] & 0xFF;
        int responseType = data[1] & 0xFF;
        int command = data[2] & 0xFF;
        int dataSize = data[3] & 0xFF;

        System.out.printf("🔍 FASORES - STX:0x%02X TYPE:0x%02X CMD:0x%02X SIZE:%d%n",
                startByte, responseType, command, dataSize);

        // ✅ OBTENER TIPO DE COMANDO USANDO OctoNetCommandEncoder
        OctoNetCommandEncoder.CmdSet commandType = OctoNetCommandEncoder.getCommandType(data);
        System.out.println("🔍 FASORES - Tipo de comando: " + commandType);

        // ✅ MANEJO ESPECÍFICO DE ERRORES
        if (responseType == 0x45) {
            System.out.println("❌ FASORES - El dispositivo respondió con ERROR");

            String errorMsg = "Error del dispositivo";
            if (command == 0x20) {
                errorMsg = "Error en NODE_SETTINGS";
            } else if (command == 0x21) {
                errorMsg = "Error en NODE_CURRENT - ¿Datos no disponibles?";
            }

            showToast("❌ " + errorMsg);
            return;
        }

        if (responseType == 0x43) { // CONFIRMATION
            System.out.println("✅ FASORES - Respuesta CONFIRMATION recibida");

            try {
                if (command == 0x20) {
                    if (dataSize > 0) {
                        System.out.println("📖 FASORES - Procesando respuesta read NODE_SETTINGS");
                        processConfigurationResponseIndependent(data);
                    } else {
                        // ✅ RESPUESTA DE WRITE NODE_SETTINGS (SIZE=0)
                        System.out.println("📝 FASORES - Confirmación de write NODE_SETTINGS");
                        showToast("✅ Configuración aplicada correctamente");

                        // ✅ LEER CONFIGURACIÓN PARA VERIFICAR CAMBIOS
                        handler.postDelayed(() -> {
                            if (!isWaitingResponse) {
                                System.out.println("🔍 FASORES - Verificando configuración después de write");
                                readDeviceConfigurationIndependent();
                            }
                        }, 1000);
                    }
                } else if (command == 0x21) {
                    // ✅ PROCESAMIENTO ESPECÍFICO PARA NODE_CURRENT
                    System.out.println("⚡ FASORES - Procesando respuesta NODE_CURRENT");
                    String hexResponse = OctoNetCommandEncoder.bytesToHexString(data);
                    System.out.println("📊 FASORES - Datos NODE_CURRENT recibidos: " + hexResponse);

                    if (dataSize > 0) {
                        processCurrentDataResponseIndependent(data);
                    } else {
                        System.out.println("❌ FASORES - NODE_CURRENT sin datos (SIZE=0)");
                        showToast("❌ NODE_CURRENT sin datos disponibles");
                    }
                } else {
                    System.out.printf("⚠️ FASORES - Comando no manejado: 0x%02X con size:%d%n", command, dataSize);
                }
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error procesando respuesta: " + e.getMessage());
                e.printStackTrace();
                showToast("❌ Error procesando respuesta");
            }
        } else {
            System.out.printf("⚠️ FASORES - Tipo de respuesta no manejado: 0x%02X%n", responseType);
        }
    }

    private void processConfigurationResponseIndependent(byte[] response) {
        try {
            // ✅ EXTRAER DATOS USANDO MÉTODO DE OctoNetCommandEncoder
            byte[] configData = OctoNetCommandEncoder.extractCommandData(response);

            if (configData.length == 0) {
                System.out.println("❌ FASORES - No se pudieron extraer datos de configuración");
                return;
            }

            System.out.println("🔧 FASORES - Config data extraída: " + OctoNetCommandEncoder.bytesToHexString(configData));

            skipSpinnerEvents = true;

            try {
                if (configData.length >= 4) {
                    // REC_ON/OFF - Byte 0
                    recordingConfig = (configData[0] & 0xFF) == 1;
                    System.out.println("   REC_ON/OFF: " + (recordingConfig ? "ON" : "OFF"));

                    // PERIOD - Byte 1
                    int period = configData[1] & 0xFF;
                    if (period >= 0 && period <= 3 && spinnerTiempo != null) {
                        periodConfig = period;
                        spinnerTiempo.setSelection(period);
                        System.out.println("   PERIOD: " + period + " (spinner actualizado)");
                    }

                    // SENSORS - Byte 2
                    int sensors = configData[2] & 0xFF;
                    if (sensors >= 0 && sensors <= 5 && spinnerAmperes != null) {
                        sensorsConfig = sensors;
                        updateAmperesRange(sensors);
                        spinnerAmperes.setSelection(sensors);
                        System.out.println("   SENSORS: " + sensors + " (spinner actualizado)");
                    }

                    // METERING_TYPE - Byte 3
                    int meteringType = configData[3] & 0xFF;
                    meteringTypeConfig = 3; // Forzar Carga Trifásica
                    tipoCableado = 3;
                    if (spinnerCableado != null) {
                        spinnerCableado.setSelection(0);
                    }
                    updateDiagram();
                    System.out.println("   METERING_TYPE: " + meteringType + " (forzado a Carga Trifásica)");

                    configurationSynced = true;
                    setControlsEnabled(true);
                    setSpinnersEnabled(true);

                    showToast("✅ Configuración sincronizada y mostrada en spinners");
                    System.out.println("✅ FASORES - Configuración procesada y spinners actualizados");
                }
            } catch (Exception e) {
                System.out.println("❌ FASORES - Error actualizando spinners: " + e.getMessage());
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error config: " + e.getMessage());
        } finally {
            skipSpinnerEvents = false;
        }
    }

    private void processCurrentDataResponseIndependent(byte[] response) {
        try {
            System.out.println("⚡ FASORES - Procesando NODE_CURRENT response...");

            int sizeFromDevice = response[3] & 0xFF;
            System.out.println("📊 FASORES - SIZE del dispositivo: " + sizeFromDevice);

            if (sizeFromDevice > 0) {
                // ✅ USAR LA MISMA LÓGICA QUE TcpClientActivity
                int realDataSize = sizeFromDevice + 1;  // 63 + 1 = 64
                System.out.println("🔧 FASORES - CORRECCIÓN NODE_CURRENT: SIZE real = " + sizeFromDevice + " + 1 = " + realDataSize + " bytes");

                // Verificar que tenemos suficientes bytes
                if (response.length < 4 + realDataSize) {
                    System.out.println("❌ FASORES - Respuesta incompleta: recibidos " + response.length + ", necesarios " + (4 + realDataSize));
                    return;
                }

                // Extraer los datos reales (64 bytes)
                byte[] energyData = new byte[realDataSize];
                System.arraycopy(response, 4, energyData, 0, realDataSize);

                String hexEnergyData = OctoNetCommandEncoder.bytesToHexString(energyData);
                System.out.println("📊 FASORES - Energy data extraída (" + energyData.length + " bytes): " + hexEnergyData);

                // ✅ VERIFICAR SI TENEMOS ESTRUCTURA COMPLETA
                if (energyData.length >= 64) {
                    System.out.println("✅ FASORES - Estructura completa de 64 bytes - procesando igual que TcpClientActivity");

                    // Procesar información básica igual que TcpClientActivity
                    if (energyData.length > 0) {
                        int id = energyData[0] & 0xFF;
                        String sourceType = (id == 0xF3) ? "Fuente Trifásica" :
                                (id == 0xC3) ? "Carga Trifásica" :
                                        "Tipo: 0x" + String.format("%02X", id);
                        System.out.println("   ID: " + sourceType);
                    }

                    // Procesar fecha/hora igual que TcpClientActivity
                    if (energyData.length >= 8) {
                        int year = 2000 + (energyData[2] & 0xFF);
                        int month = energyData[3] & 0xFF;
                        int day = energyData[4] & 0xFF;
                        int hour = energyData[5] & 0xFF;
                        int minute = energyData[6] & 0xFF;
                        int second = energyData[7] & 0xFF;
                        System.out.printf("   Fecha/Hora: %02d/%02d/%04d %02d:%02d:%02d%n", day, month, year, hour, minute, second);
                    }

                    // ✅ PROCESAR CANALES IGUAL QUE TcpClientActivity
                    System.out.println("🔍 FASORES - Procesando canales con 64 bytes completos:");

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
                        System.out.println("✅ FASORES - CH3 procesado con ANGLE completo");
                    }

                } else {
                    System.out.println("⚠️ FASORES - Estructura incompleta (" + energyData.length + " bytes) - usando valores por defecto");

                    // Resetear a valores por defecto
                    for (int i = 0; i < 3; i++) {
                        voltajes[i] = 0.0f;
                        corrientes[i] = 0.0f;
                        potencias[i] = 0.0f;
                        frecuencias[i] = 50.0f;
                        angulos[i] = i * 120.0f;
                    }
                }

                // ✅ DEBUG DE VALORES PROCESADOS
                System.out.printf("📊 FASORES - Valores finales procesados:%n");
                System.out.printf("   CH1: V=%.1f, A=%.2f, W=%.1f, Hz=%.1f, Ang=%.1f°%n", voltajes[0], corrientes[0], potencias[0], frecuencias[0], angulos[0]);
                System.out.printf("   CH2: V=%.1f, A=%.2f, W=%.1f, Hz=%.1f, Ang=%.1f°%n", voltajes[1], corrientes[1], potencias[1], frecuencias[1], angulos[1]);
                System.out.printf("   CH3: V=%.1f, A=%.2f, W=%.1f, Hz=%.1f, Ang=%.1f°%n", voltajes[2], corrientes[2], potencias[2], frecuencias[2], angulos[2]);

                // ✅ VERIFICAR SI HAY DATOS VÁLIDOS
                boolean hasValidData = false;
                for (int i = 0; i < 3; i++) {
                    if (voltajes[i] > 0.1f || corrientes[i] > 0.01f || potencias[i] > 0.1f) {
                        hasValidData = true;
                        break;
                    }
                }

                if (hasValidData) {
                    System.out.println("✅ FASORES - Datos válidos encontrados - actualizando UI");
                } else {
                    System.out.println("⚠️ FASORES - Todos los valores son cero - manteniendo ángulos por defecto");
                    // Restaurar ángulos por defecto para visualización
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
                System.out.println("✅ FASORES - Muestra #" + contadorMuestras + " procesada exitosamente");

                if (contadorMuestras % 10 == 0) {
                    long tiempoTranscurrido = (System.currentTimeMillis() - tiempoInicio) / 1000;
                    showToast(String.format("📊 %d muestras (%ds)", contadorMuestras, tiempoTranscurrido));
                }

            } else {
                System.out.println("❌ FASORES - Dispositivo envió SIZE=0 para NODE_CURRENT");
                showToast("❌ Sin datos de energía disponibles");
            }

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando NODE_CURRENT: " + e.getMessage());
            e.printStackTrace();
            showToast("❌ Error procesando datos de energía");
        }
    }

    private void processChannel64TcpStyle(byte[] data, int offset, int channelIndex, String channelName) {
        try {
            System.out.println("🔍 FASORES - Procesando " + channelName + " en offset " + offset + " (estilo TcpClientActivity)");

            // W_CHx (Int32) - 4 bytes
            if (data.length >= offset + 4) {
                long powerRaw = readInt32(data, offset);
                float powerW = (int)powerRaw * 0.1f;  // ✅ IGUAL QUE TcpClientActivity
                potencias[channelIndex] = powerW;
                System.out.printf("   %s Power: %d raw -> %.1f W%n", channelName, (int)powerRaw, potencias[channelIndex]);
            }

            // V_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16(data, offset + 4);
                float voltageV = voltageRaw * 0.1f;  // ✅ IGUAL QUE TcpClientActivity
                voltajes[channelIndex] = voltageV;
                System.out.printf("   %s Voltage: %d raw -> %.1f V%n", channelName, voltageRaw, voltajes[channelIndex]);
            }

            // A_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 8) {
                int currentRaw = readUInt16(data, offset + 6);
                float currentA = currentRaw * 0.1f;  // ✅ IGUAL QUE TcpClientActivity
                corrientes[channelIndex] = currentA;
                System.out.printf("   %s Current: %d raw -> %.1f A%n", channelName, currentRaw, corrientes[channelIndex]);
            }

            // HZ_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16(data, offset + 8);
                float freqHz = frequencyRaw * 0.1f;  // ✅ IGUAL QUE TcpClientActivity
                frecuencias[channelIndex] = freqHz;
                System.out.printf("   %s Frequency: %d raw -> %.1f Hz%n", channelName, frequencyRaw, frecuencias[channelIndex]);
            }

            // ✅ ANGLE_CHx (UInt16) - 2 bytes - IGUAL QUE TcpClientActivity
            if (data.length >= offset + 12) {
                int angleRaw = readUInt16(data, offset + 10);
                float angleDeg = angleRaw * 0.1f;  // ✅ IGUAL QUE TcpClientActivity
                angulos[channelIndex] = angleDeg;
                System.out.printf("   %s Angle: %d raw -> %.1f° (REAL del dispositivo)%n", channelName, angleRaw, angulos[channelIndex]);
            } else {
                // Solo si no hay datos de ángulo, usar por defecto
                angulos[channelIndex] = channelIndex * 120.0f;
                System.out.printf("   %s Angle: %.1f° (por defecto)%n", channelName, angulos[channelIndex]);
            }

            System.out.println("✅ FASORES - " + channelName + " procesado correctamente estilo TcpClientActivity");

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error procesando " + channelName + ": " + e.getMessage());
            e.printStackTrace();
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

    // ===== CONTROL DE ADQUISICIÓN =====

    private void startDataAcquisition() {
        if (!isConnectedToDevice || autoReadEnabled) {
            System.out.println("❌ FASORES - No se puede iniciar: conectado=" + isConnectedToDevice + ", activo=" + autoReadEnabled);
            return;
        }

        try {
            autoReadEnabled = true;
            tiempoInicio = System.currentTimeMillis();
            contadorMuestras = 0;

            // Actualizar UI
            btnPlay.setImageResource(android.R.drawable.ic_media_pause);
            showToast("🚀 Iniciando lectura NODE_CURRENT cada 5 segundos");
            System.out.println("🚀 FASORES - Modo adquisición iniciado");

            // Primera lectura inmediata
            requestCurrentData();

            // Programar lecturas cada 5 segundos
            autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);

        } catch (Exception e) {
            autoReadEnabled = false;
            btnPlay.setImageResource(android.R.drawable.ic_media_play);
            showToast("❌ Error al iniciar adquisición");
            System.out.println("❌ FASORES - Error iniciando adquisición: " + e.getMessage());
        }
    }

    private void stopDataAcquisition() {
        if (autoReadEnabled) {
            autoReadEnabled = false;
            autoReadHandler.removeCallbacks(autoReadTask);

            // Habilitar spinners
            setSpinnersEnabled(true);

            // Actualizar UI
            btnPlay.setImageResource(android.R.drawable.ic_media_play);

            long tiempoTotal = (System.currentTimeMillis() - tiempoInicio) / 1000;
            showToast(String.format("⏹️ Detenido: %d muestras en %ds", contadorMuestras, tiempoTotal));
        }
    }

    // ===== VISUALIZACIÓN =====

    private void updateDisplayWithRealData() {
        // Actualizar displays con valores reales del medidor
        updatePhaseDisplay(0, voltajes[0], corrientes[0], potencias[0], frecuencias[0]);
        updatePhaseDisplay(1, voltajes[1], corrientes[1], potencias[1], frecuencias[1]);
        updatePhaseDisplay(2, voltajes[2], corrientes[2], potencias[2], frecuencias[2]);

        // ✅ INDICADOR VISUAL DE ACTIVIDAD
        if (tvCH1 != null) {
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            tvCH1.setText("CH1 - " + timestamp);
        }
    }

    private void updateFasores() {
        try {
            // ✅ USAR setPhasorValues EN LUGAR DE setVectors
            if (fasorVoltaje != null) {
                fasorVoltaje.setPhasorValues(voltajes, angulos);
            }

            if (fasorCorriente != null) {
                fasorCorriente.setPhasorValues(corrientes, angulos);
            }

            System.out.printf("🔄 FASORES - Actualizado: V[%.1f,%.1f,%.1f] A[%.1f,%.1f,%.1f] Ang[%.0f°,%.0f°,%.0f°]%n",
                    voltajes[0], voltajes[1], voltajes[2],
                    corrientes[0], corrientes[1], corrientes[2],
                    angulos[0], angulos[1], angulos[2]);

        } catch (Exception e) {
            System.out.println("❌ FASORES - Error actualizando fasores: " + e.getMessage());
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

        // ✅ MOSTRAR VALORES INCLUSO SI SON CERO
        if (tvV != null) tvV.setText(String.format("%.1f V", volt));
        if (tvA != null) tvA.setText(String.format("%.2f A", corr));
        if (tvW != null) tvW.setText(String.format("%.1f W", power)); // En W, no kW
        if (tvHz != null) tvHz.setText(String.format("%.1f Hz", freq));

        // ✅ SIMPLE: Solo normalizar ángulo a -180° a +180°
        if (tvPF != null) {
            if (phase >= 0 && phase < angulos.length) {
                float deviceAngle = angulos[phase]; // Ángulo del dispositivo

                // Normalizar a rango -180° a +180°
                while (deviceAngle > 180f) deviceAngle -= 360f;
                while (deviceAngle < -180f) deviceAngle += 360f;

                // Mostrar ángulo normalizado
                tvPF.setText(String.format("%.0f°", deviceAngle));

            } else {
                tvPF.setText("--");
            }
        }
    }

    private void updateDiagram() {
        // Siempre mostrar diagrama trifásico (Carga Trifásica)
        if (imageDiagram != null) {
            imageDiagram.setImageResource(R.drawable.diagram_3p4w_n);
        }

        // ✅ ACTUALIZAR TAMBIÉN EL MODAL SI ESTÁ VISIBLE
        if (isModalVisible && modalDiagramImage != null) {
            updateModalDiagram();
        }
    }

    // ===== CONTROL UI =====

    private void setSpinnersEnabled(boolean enabled) {
        if (spinnerCableado != null) {
            spinnerCableado.setEnabled(enabled);
            spinnerCableado.setAlpha(enabled ? 1.0f : 0.4f);
        }
        if (spinnerTiempo != null) {
            spinnerTiempo.setEnabled(enabled);
            spinnerTiempo.setAlpha(enabled ? 1.0f : 0.4f);
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

    private void initializeDisplayValues() {
        // Inicializar todos los displays en cero
        for (int i = 0; i < 3; i++) {
            updatePhaseDisplay(i, 0.0f, 0.0f, 0.0f, 0.0f);
            voltajes[i] = 0.0f;
            corrientes[i] = 0.0f;
            potencias[i] = 0.0f;
            frecuencias[i] = 50.0f;
            angulos[i] = i * 120.0f; // Ángulos por defecto: 0°, 120°, 240°
        }

        // ✅ INICIALIZAR FASORES CON MODO 3 EJES
        if (fasorVoltaje != null) {
            fasorVoltaje.setThreeAxisMode(true);
            fasorVoltaje.setPhasorValues(voltajes, angulos);
        }
        if (fasorCorriente != null) {
            fasorCorriente.setThreeAxisMode(true);
            fasorCorriente.setPhasorValues(corrientes, angulos);
        }
    }

    // ===== MÉTODOS AUXILIARES =====

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    // ===== MANEJO DEL BOTÓN BACK =====

    @Override
    public void onBackPressed() {
        // ✅ CERRAR MODAL PRIMERO SI ESTÁ ABIERTO
        if (isModalVisible) {
            hideDiagramModal();
            return;
        }

        // Si no hay modal, comportamiento normal
        if (autoReadEnabled) {
            stopDataAcquisition();
        }
        super.onBackPressed();
    }

    // ===== LIFECYCLE METHODS =====

    @Override
    protected void onDestroy() {
        super.onDestroy();

        System.out.println("🔄 FASORES - onDestroy iniciado");

        // ✅ CERRAR MODAL SI ESTÁ ABIERTO
        if (isModalVisible) {
            hideDiagramModal();
        }

        // Detener auto-lectura
        stopDataAcquisition();

        // Desconectar correctamente
        disconnectFromDevice();

        // Cerrar executor con timeout
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
                System.out.println("✅ FASORES - Executor cerrado correctamente");
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        // Limpiar handlers
        handler.removeCallbacksAndMessages(null);
        autoReadHandler.removeCallbacksAndMessages(null);

        System.out.println("✅ FASORES - onDestroy completado");
    }

    @Override
    protected void onPause() {
        super.onPause();
        System.out.println("🔄 FASORES - onPause");

        // Pausar auto-lectura pero mantener conexión
        if (autoReadEnabled) {
            autoReadHandler.removeCallbacks(autoReadTask);
            System.out.println("⏸️ FASORES - Auto-lectura pausada");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        System.out.println("🔄 FASORES - onResume");

        // Verificar conexión
        if (!isConnectedToDevice) {
            handler.postDelayed(() -> connectToDeviceIndependent(), 500);
        }

        // Reanudar auto-lectura si estaba activa
        if (autoReadEnabled && isConnectedToDevice && configurationSynced) {
            autoReadHandler.postDelayed(autoReadTask, AUTO_READ_INTERVAL);
            System.out.println("▶️ FASORES - Auto-lectura reanudada");
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // Pausar auto-lectura al salir de la actividad
        if (autoReadEnabled) {
            autoReadHandler.removeCallbacks(autoReadTask);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Verificar conexión al regresar a la actividad
        if (!isConnectedToDevice) {
            connectToDeviceIndependent();
        }
    }

    // ===== MÉTODOS ADICIONALES PARA DEBUG Y CONFIGURACIÓN MANUAL =====

    /**
     * ✅ MÉTODO OPCIONAL: Botón manual para escribir configuración
     * Puedes agregar un botón en el layout y conectarlo a este método
     */
    private void onManualWriteConfigClicked() {
        if (!isConnectedToDevice) {
            showToast("❌ No hay conexión");
            return;
        }

        if (!configurationSynced) {
            showToast("⚠️ Configuración no sincronizada");
            return;
        }

        // ✅ MOSTRAR DIALOG DE CONFIRMACIÓN
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Escribir Configuración")
                .setMessage(String.format(
                        "¿Escribir configuración al dispositivo?\n\n" +
                                "Tiempo: %s\n" +
                                "Amperes: %s\n" +
                                "Tipo: Carga Trifásica",
                        spinnerTiempo.getSelectedItem().toString(),
                        spinnerAmperes.getSelectedItem().toString()
                ))
                .setPositiveButton("✅ Escribir", (dialog, which) -> {
                    writeDeviceConfigurationFromSpinners();
                })
                .setNegativeButton("❌ Cancelar", null)
                .show();
    }

    /**
     * ✅ MUESTRA LA CONFIGURACIÓN ACTUAL EN LOGS
     */
    private void debugCurrentConfiguration() {
        System.out.println("🔧 FASORES - === CONFIGURACIÓN ACTUAL ===");
        System.out.printf("   Local - PERIOD: %d, SENSORS: %d, METERING: %d, RECORDING: %s%n",
                periodConfig, sensorsConfig, meteringTypeConfig, recordingConfig);

        if (spinnerTiempo != null && spinnerAmperes != null && spinnerCableado != null) {
            System.out.printf("   Spinners - TIEMPO: %d, AMPERES: %d, CABLEADO: %d%n",
                    spinnerTiempo.getSelectedItemPosition(),
                    spinnerAmperes.getSelectedItemPosition(),
                    spinnerCableado.getSelectedItemPosition());
        }

        System.out.printf("   Estado - Conectado: %s, Sincronizado: %s, Esperando: %s%n",
                isConnectedToDevice, configurationSynced, isWaitingResponse);
        System.out.println("=====================================");
    }

    /**
     * ✅ MÉTODO PARA FORZAR RECONEXIÓN
     */
    private void forceReconnect() {
        System.out.println("🔄 FASORES - Forzando reconexión...");
        disconnectFromDevice();
        handler.postDelayed(() -> connectToDeviceIndependent(), 2000);
        showToast("🔄 Reconectando...");
    }

    /**
     * ✅ MÉTODO PARA RESETEAR CONFIGURACIÓN LOCAL
     */
    private void resetLocalConfiguration() {
        System.out.println("🔄 FASORES - Reseteando configuración local...");

        configurationSynced = false;
        isWaitingResponse = false;

        // Valores por defecto
        periodConfig = 1; // 5 minutos
        sensorsConfig = 1; // CT-50A
        meteringTypeConfig = 3; // Carga Trifásica
        recordingConfig = true;

        // Actualizar spinners
        skipSpinnerEvents = true;
        if (spinnerTiempo != null) spinnerTiempo.setSelection(1);
        if (spinnerAmperes != null) spinnerAmperes.setSelection(1);
        if (spinnerCableado != null) spinnerCableado.setSelection(0);
        skipSpinnerEvents = false;

        updateAmperesRange(1);
        updateDiagram();

        showToast("🔄 Configuración reseteada");
    }
}