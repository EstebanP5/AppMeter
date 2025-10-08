package de.kai_morich.simple_bluetooth_terminal;

import android.os.Bundle;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpClientActivity extends AppCompatActivity {

    // ===== VARIABLES TCP =====
    private TextInputEditText editTextIp, editTextPort;
    private ScrollView scrollViewStatus;
    private MaterialButton btnConnect, btnClear;
    private TextView textViewStatus;
    private Handler handler = new Handler(Looper.getMainLooper());

    private Socket socket;
    private OutputStream outputStream;
    private BufferedReader inputReader;
    private Thread receiveThread;
    private boolean isConnected = false;
    private boolean isConnecting = false;
    private ExecutorService executor;
    private FloatingActionButton btnScrollTop, btnScrollBottom;

    // Variables para control de scroll continuo
    private Handler scrollHandler = new Handler(Looper.getMainLooper());
    private Runnable scrollUpRunnable, scrollDownRunnable;
    private boolean isScrollingUp = false;
    private boolean isScrollingDown = false;
    private static final int SCROLL_SPEED = 20;
    private static final int SCROLL_DELAY = 50;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    // ===== VARIABLES OCTONET =====
    private Spinner spinnerCommand;
    private TextView textViewCurrentTime;
    private LinearLayout layoutCommandInfo, layoutDeviceData;
    private TextView textViewCommandDescription;
    private MaterialButton btnRead, btnWrite;
    private OctoNetCommandEncoder.StateMachine stateMachine;

    // Campos editables
    private TextInputEditText editDeviceTime;
    private TextView textDeviceId, textDeviceSerial, textDeviceFacDate, textDeviceFacHour;
    private TextView textDeviceActCode, textDeviceHwVersion, textDeviceFwVersion;

    // ===== VARIABLES CONFIGURACIÓN MEDIDORES =====
    private LinearLayout layoutMeterSettings;
    private Switch switchRecOnOff;
    private Spinner spinnerPeriod, spinnerSensors, spinnerMeteringType;
    private MaterialButton btnReadConfig, btnWriteConfig;

    // ===== VARIABLES ENERGY DATA =====
    private LinearLayout layoutEnergyData;
    private TextView textEnergyDateTime, textEnergyFlashAddress, textEnergySensorStatus;
    private TextView textEnergyWCH1, textEnergyVCH1, textEnergyACH1, textEnergyHZCH1, textEnergyAngleCH1;
    private TextView textEnergyWCH2, textEnergyVCH2, textEnergyACH2, textEnergyHZCH2, textEnergyAngleCH2;
    private TextView textEnergyWCH3, textEnergyVCH3, textEnergyACH3, textEnergyHZCH3, textEnergyAngleCH3;
    private TextView textEnergyWHWBDelta, textEnergyWHNBDelta, textEnergyWHWBDay, textEnergyWHFunDay;

    // Comandos y estado
    private String[] commands = {"DEVICE_ID", "DEVICE_TIME", "NODE_SETTINGS", "NODE_CURRENT"};
    private DeviceIdInfo currentDeviceInfo;
    private String currentSelectedCommand = "";
    private boolean isWaitingResponse = false;
    private String deviceTimeFromResponse = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tcp_client);

        executor = Executors.newCachedThreadPool();

        initializeTcpElements();
        initializeStateMachine();
        initializeOctoNetElements();
        initializeMeterConfigElements();
        initializeEnergyDataElements();

        setupTcpListeners();
        setupOctoNetListeners();
        setupMeterConfigListeners();
        setupScrollRunnables();
        setupNavigationButtons();

        startTimeUpdater();
        updateUI();
    }

    private void showToast(String message) {
        handler.post(() -> Toast.makeText(TcpClientActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void initializeStateMachine() {
        stateMachine = new OctoNetCommandEncoder.StateMachine(new OctoNetCommandEncoder.CommandCallback() {
            @Override
            public void onCommandReceived(OctoNetCommandEncoder.CmdSet commandType, byte[] commandData) {
                handler.post(() -> {
                    showStatusMessage("✅ Comando decodificado: " + commandType, MessageType.SUCCESS);
                    isWaitingResponse = false;
                    updateCommandUI();
                });
            }

            @Override
            public void onCommandError(OctoNetCommandEncoder.CmdSet errorType, String errorMessage) {
                handler.post(() -> {
                    showStatusMessage("❌ Error de decodificación: " + errorType, MessageType.ERROR);
                    showStatusMessage("   Detalle: " + errorMessage, MessageType.ERROR);
                    isWaitingResponse = false;
                    updateCommandUI();
                });
            }
        });

        showStatusMessage("🔧 Máquina de estados OctoNet inicializada", MessageType.INFO);
    }

    private void initializeTcpElements() {
        editTextIp = findViewById(R.id.editTextIp);
        editTextPort = findViewById(R.id.editTextPort);
        textViewStatus = findViewById(R.id.textViewStatus);
        btnClear = findViewById(R.id.btnClear);
        btnScrollTop = findViewById(R.id.btnScrollTop);
        btnScrollBottom = findViewById(R.id.btnScrollBottom);
        btnConnect = findViewById(R.id.btnConnect);

        scrollViewStatus = (ScrollView) textViewStatus.getParent();

        textViewStatus.setMovementMethod(new ScrollingMovementMethod());
        textViewStatus.setVerticalScrollBarEnabled(true);
        textViewStatus.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        textViewStatus.setTextIsSelectable(true);
        textViewStatus.setHorizontallyScrolling(false);
        textViewStatus.setMaxLines(Integer.MAX_VALUE);
        textViewStatus.setTextSize(12);

        scrollViewStatus.setVerticalScrollBarEnabled(true);
        scrollViewStatus.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        scrollViewStatus.setSmoothScrollingEnabled(true);
        scrollViewStatus.setFillViewport(true);

        if (editTextIp.getText().toString().trim().isEmpty()) {
            editTextIp.setText("192.168.4.1");
        }
        if (editTextPort.getText().toString().trim().isEmpty()) {
            editTextPort.setText("333");
        }

        showStatusMessage("🔌 Cliente TCP listo", MessageType.INFO);
    }

    private void initializeOctoNetElements() {
        spinnerCommand = findViewById(R.id.spinnerCommand);
        textViewCurrentTime = findViewById(R.id.textViewCurrentTime);
        layoutCommandInfo = findViewById(R.id.layoutCommandInfo);
        textViewCommandDescription = findViewById(R.id.textViewCommandDescription);
        layoutDeviceData = findViewById(R.id.layoutDeviceData);
        btnRead = findViewById(R.id.btnRead);
        btnWrite = findViewById(R.id.btnWrite);

        editDeviceTime = findViewById(R.id.editDeviceTime);
        textDeviceId = findViewById(R.id.textDeviceId);
        textDeviceSerial = findViewById(R.id.textDeviceSerial);
        textDeviceFacDate = findViewById(R.id.textDeviceFacDate);
        textDeviceFacHour = findViewById(R.id.textDeviceFacHour);
        textDeviceActCode = findViewById(R.id.textDeviceActCode);
        textDeviceHwVersion = findViewById(R.id.textDeviceHwVersion);
        textDeviceFwVersion = findViewById(R.id.textDeviceFwVersion);

        ArrayAdapter<String> commandAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, commands);
        commandAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCommand.setAdapter(commandAdapter);

        currentDeviceInfo = new DeviceIdInfo();
        layoutCommandInfo.setVisibility(View.GONE);
        layoutDeviceData.setVisibility(View.GONE);
    }

    private void initializeMeterConfigElements() {
        layoutMeterSettings = findViewById(R.id.layout_meter_settings);
        switchRecOnOff = findViewById(R.id.switch_rec_on_off);
        spinnerPeriod = findViewById(R.id.spinner_period);
        spinnerSensors = findViewById(R.id.spinner_sensors);
        spinnerMeteringType = findViewById(R.id.spinner_metering_type);
        btnReadConfig = findViewById(R.id.btn_read_config);
        btnWriteConfig = findViewById(R.id.btn_write_config);

        setupMeterSpinners();
        layoutMeterSettings.setVisibility(View.GONE);
    }

    private void initializeEnergyDataElements() {
        layoutEnergyData = findViewById(R.id.layout_energy_data);
        textEnergyDateTime = findViewById(R.id.text_energy_datetime);
        textEnergyFlashAddress = findViewById(R.id.text_energy_flash_address);
        textEnergySensorStatus = findViewById(R.id.text_energy_sensor_status);

        textEnergyWHWBDelta = findViewById(R.id.text_energy_wh_wb_delta);
        textEnergyWHNBDelta = findViewById(R.id.text_energy_wh_nb_delta);
        textEnergyWHWBDay = findViewById(R.id.text_energy_wh_wb_day);
        textEnergyWHFunDay = findViewById(R.id.text_energy_wh_fun_day);

        textEnergyWCH1 = findViewById(R.id.text_energy_w_ch1);
        textEnergyVCH1 = findViewById(R.id.text_energy_v_ch1);
        textEnergyACH1 = findViewById(R.id.text_energy_a_ch1);
        textEnergyHZCH1 = findViewById(R.id.text_energy_hz_ch1);
        textEnergyAngleCH1 = findViewById(R.id.text_energy_angle_ch1);

        textEnergyWCH2 = findViewById(R.id.text_energy_w_ch2);
        textEnergyVCH2 = findViewById(R.id.text_energy_v_ch2);
        textEnergyACH2 = findViewById(R.id.text_energy_a_ch2);
        textEnergyHZCH2 = findViewById(R.id.text_energy_hz_ch2);
        textEnergyAngleCH2 = findViewById(R.id.text_energy_angle_ch2);

        textEnergyWCH3 = findViewById(R.id.text_energy_w_ch3);
        textEnergyVCH3 = findViewById(R.id.text_energy_v_ch3);
        textEnergyACH3 = findViewById(R.id.text_energy_a_ch3);
        textEnergyHZCH3 = findViewById(R.id.text_energy_hz_ch3);
        textEnergyAngleCH3 = findViewById(R.id.text_energy_angle_ch3);

        layoutEnergyData.setVisibility(View.GONE);
    }

    private void setupMeterSpinners() {
        String[] periodValues = {
                "0 = 1 minuto", "1 = 5 minutos", "2 = 10 minutos", "3 = 15 minutos"
        };
        setupSpinnerAdapter(spinnerPeriod, periodValues);

        String[] sensorValues = {
                "0 = Shunt-20A", "1 = CT-50A", "2 = CT-200A",
                "3 = CT-400A", "4 = RoGo-1000A", "5 = RoGo-3000A"
        };
        setupSpinnerAdapter(spinnerSensors, sensorValues);

        String[] meteringTypeValues = {"3 = Carga Trifásica"};
        setupSpinnerAdapter(spinnerMeteringType, meteringTypeValues);
    }

    private void setupSpinnerAdapter(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void setupMeterConfigListeners() {
        if (btnReadConfig != null) {
            btnReadConfig.setOnClickListener(v -> readMeterConfiguration());
        }
        if (btnWriteConfig != null) {
            btnWriteConfig.setOnClickListener(v -> writeMeterConfiguration());
        }
    }

    private void readMeterConfiguration() {
        if (!isConnected) {
            showToast("❌ No hay conexión TCP activa");
            return;
        }
        if (isWaitingResponse) {
            showToast("⏳ Esperando respuesta anterior...");
            return;
        }

        try {
            byte[] command = OctoNetCommandEncoder.createNodeSettingsReadCommand();
            sendTcpCommand(command, "📖 Leer Configuración Medidor");
        } catch (Exception e) {
            showStatusMessage("❌ Error al leer configuración: " + e.getMessage(), MessageType.ERROR);
            showToast("❌ Error al leer configuración: " + e.getMessage());
        }
    }

    private void writeMeterConfiguration() {
        if (!isConnected) {
            showToast("❌ No hay conexión TCP activa");
            return;
        }
        if (isWaitingResponse) {
            showToast("⏳ Esperando respuesta anterior...");
            return;
        }

        try {
            boolean recOnOff = switchRecOnOff != null ? switchRecOnOff.isChecked() : true;
            int period = spinnerPeriod != null ? spinnerPeriod.getSelectedItemPosition() : 1;
            int sensors = spinnerSensors != null ? spinnerSensors.getSelectedItemPosition() : 1;
            int meteringType = 3; // FORZAR Carga Trifásica

            byte[] command = OctoNetCommandEncoder.createNodeSettingsWriteCommand(
                    recOnOff, period, sensors, meteringType);
            sendTcpCommand(command, "⚙️ Escribir Configuración Medidor");

        } catch (Exception e) {
            showStatusMessage("❌ Error al escribir configuración: " + e.getMessage(), MessageType.ERROR);
            showToast("❌ Error al escribir configuración: " + e.getMessage());
        }
    }

    private void setupOctoNetListeners() {
        spinnerCommand.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedCommand = (String) parent.getSelectedItem();
                onCommandSelected(selectedCommand);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnRead.setOnClickListener(v -> readCurrentCommand());
        btnWrite.setOnClickListener(v -> writeCurrentCommand());
    }

    private void onCommandSelected(String command) {
        currentSelectedCommand = command;
        showCommandInfo(command);

        if (isConnected && !isWaitingResponse && !"NODE_SETTINGS".equals(command)) {
            readCurrentCommand();
        }
    }

    private void showCommandInfo(String command) {
        layoutCommandInfo.setVisibility(View.VISIBLE);
        layoutDeviceData.setVisibility(View.VISIBLE);

        String description;
        boolean canWrite = false;
        boolean showMeterConfig = false;
        boolean showEnergyData = false;

        hideAllDataFields();

        switch (command) {
            case "DEVICE_ID":
                description = "📱 Identificación del Dispositivo\nObtiene información básica del dispositivo.";
                showDeviceIdFields(true);
                break;

            case "DEVICE_TIME":
                description = "⏰ Fecha y Hora del Dispositivo\nPermite leer y configurar la fecha/hora del dispositivo.";
                canWrite = true;
                showDeviceTimeField(true);
                if (deviceTimeFromResponse != null) {
                    editDeviceTime.setText(deviceTimeFromResponse);
                } else {
                    updateCurrentTimeDisplay();
                }
                break;

            case "NODE_SETTINGS":
                description = "⚙️ Configuración del Medidor\nPermite leer y modificar la configuración del medidor.";
                canWrite = true;
                showMeterConfig = true;
                break;

            case "NODE_CURRENT":
                description = "⚡ Datos ENERGY_3PHA_PAGE_STRC\nMuestra datos de energía trifásica en tiempo real.";
                canWrite = false;
                showEnergyData = true;
                break;

            default:
                description = "❓ Comando no reconocido";
                break;
        }

        textViewCommandDescription.setText(description);
        layoutMeterSettings.setVisibility(showMeterConfig ? View.VISIBLE : View.GONE);
        layoutEnergyData.setVisibility(showEnergyData ? View.VISIBLE : View.GONE);

        btnRead.setText("📖 Read " + command);
        btnWrite.setText("📝 Write " + command);
        btnWrite.setVisibility(canWrite ? View.VISIBLE : View.GONE);

        showStatusMessage("📋 Comando seleccionado: " + command, MessageType.INFO);
    }

    private void hideAllDataFields() {
        showDeviceIdFields(false);
        showDeviceTimeField(false);
    }

    private void showDeviceIdFields(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        if (textDeviceId != null) textDeviceId.setVisibility(visibility);
        if (textDeviceSerial != null) textDeviceSerial.setVisibility(visibility);
        if (textDeviceFacDate != null) textDeviceFacDate.setVisibility(visibility);
        if (textDeviceFacHour != null) textDeviceFacHour.setVisibility(visibility);
        if (textDeviceActCode != null) textDeviceActCode.setVisibility(visibility);
        if (textDeviceHwVersion != null) textDeviceHwVersion.setVisibility(visibility);
        if (textDeviceFwVersion != null) textDeviceFwVersion.setVisibility(visibility);
    }

    private void showDeviceTimeField(boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        if (editDeviceTime != null) editDeviceTime.setVisibility(visibility);
    }

    private void readCurrentCommand() {
        if (!isConnected) {
            showToast("❌ No hay conexión TCP activa");
            return;
        }
        if (currentSelectedCommand.isEmpty()) {
            showToast("❌ No hay comando seleccionado");
            return;
        }
        if (isWaitingResponse) {
            showToast("⏳ Esperando respuesta anterior...");
            return;
        }

        sendReadCommand(currentSelectedCommand);
    }

    private void writeCurrentCommand() {
        if (!isConnected) {
            showToast("❌ No hay conexión TCP activa");
            return;
        }
        if (currentSelectedCommand.isEmpty()) {
            showToast("❌ No hay comando seleccionado");
            return;
        }
        if (isWaitingResponse) {
            showToast("⏳ Esperando respuesta anterior...");
            return;
        }

        if ("NODE_SETTINGS".equals(currentSelectedCommand)) {
            writeMeterConfiguration();
        } else {
            sendWriteCommand(currentSelectedCommand);
        }
    }

    private void sendReadCommand(String command) {
        byte[] cmd;
        String commandDescription;

        switch (command) {
            case "DEVICE_ID":
                cmd = OctoNetCommandEncoder.createDeviceIdReadCommand();
                commandDescription = "📱 DEVICE_ID (Read)";
                break;
            case "DEVICE_TIME":
                cmd = OctoNetCommandEncoder.createDeviceTimeReadCommand();
                commandDescription = "⏰ DEVICE_TIME (Read)";
                break;
            case "NODE_SETTINGS":
                cmd = OctoNetCommandEncoder.createNodeSettingsReadCommand();
                commandDescription = "⚙️ NODE_SETTINGS (Read)";
                break;
            case "NODE_CURRENT":
                cmd = OctoNetCommandEncoder.createNodeCurrentReadCommand();
                commandDescription = "⚡ NODE_CURRENT (Read)";
                break;
            default:
                showToast("❌ Comando no soportado: " + command);
                return;
        }

        sendTcpCommand(cmd, commandDescription);
    }

    // ✅ MÉTODO CORREGIDO PARA DEVICE_TIME WRITE
    private void sendWriteCommand(String command) {
        byte[] cmd;
        String commandDescription;

        switch (command) {
            case "DEVICE_TIME":
                String timeText = editDeviceTime.getText().toString().trim();
                if (timeText.isEmpty()) {
                    showToast("❌ Ingrese fecha y hora válida");
                    return;
                }

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                    Date date = sdf.parse(timeText);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(date);

                    // ✅ CORRECCIÓN: Obtener el día de la semana correctamente
                    int year = cal.get(Calendar.YEAR);
                    int month = cal.get(Calendar.MONTH) + 1; // Calendar.MONTH es 0-based
                    int day = cal.get(Calendar.DAY_OF_MONTH);
                    int hour = cal.get(Calendar.HOUR_OF_DAY);
                    int minute = cal.get(Calendar.MINUTE);
                    int second = cal.get(Calendar.SECOND);

                    // ✅ CORRECCIÓN IMPORTANTE: Mapear el día de la semana correctamente
                    // Calendar.DAY_OF_WEEK: 1=Sunday, 2=Monday, ..., 7=Saturday
                    // Protocolo OctoNet: 1=Sunday, 2=Monday, ..., 7=Saturday (igual)
                    int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);

                    showStatusMessage("🔍 Datos para DEVICE_TIME_WRITE:", MessageType.INFO);
                    showStatusMessage(String.format("   Año: %d, Mes: %d, Día: %d", year, month, day), MessageType.INFO);
                    showStatusMessage(String.format("   Hora: %02d:%02d:%02d, Día semana: %d", hour, minute, second, dayOfWeek), MessageType.INFO);

                    cmd = OctoNetCommandEncoder.createDeviceTimeWriteCommand(
                            year, month, day, hour, minute, second, dayOfWeek);
                    commandDescription = "⏰ DEVICE_TIME (Write)";

                    // Debug del comando generado
                    String hexCmd = OctoNetCommandEncoder.bytesToHexString(cmd);
                    showStatusMessage("🔍 Comando DEVICE_TIME_WRITE generado: " + hexCmd, MessageType.INFO);

                } catch (Exception e) {
                    showToast("❌ Formato de fecha/hora inválido. Use: dd/MM/yyyy HH:mm:ss");
                    showStatusMessage("" + e.getMessage(), MessageType.ERROR);
                    return;
                }
                break;

            default:
                showToast("❌ Comando no permite escritura: " + command);
                return;
        }

        sendTcpCommand(cmd, commandDescription);
    }

    // ===== PROCESAMIENTO DE RESPUESTAS CORREGIDO =====

    private void receiveMessages() {
        byte[] buffer = new byte[2048];

        try {
            while (isConnected && !Thread.currentThread().isInterrupted()) {
                try {
                    int bytesRead = socket.getInputStream().read(buffer);
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);

                        String hexString = bytesToHexString(data);

                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showStatusMessage("📨 Datos recibidos (" + bytesRead + " bytes):", MessageType.RECEIVED);
                                showStatusMessage("   " + hexString, MessageType.DATA);

                                // USAR SOLO PROCESAMIENTO DIRECTO
                                if (OctoNetCommandEncoder.verifyChecksum(data)) {
                                    showStatusMessage("✅ Checksum verificado correctamente", MessageType.SUCCESS);
                                } else {
                                    showStatusMessage("❌ Checksum incorrecto en la respuesta", MessageType.ERROR);
                                }

                                processReceivedDataCorrected(data);
                            }
                        });

                    } else {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                showStatusMessage("🔌 Conexión cerrada por el servidor", MessageType.WARNING);
                                disconnect();
                            }
                        });
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showStatusMessage("⏰ Timeout en lectura (60s), siguiendo conectado...", MessageType.WARNING);
                        }
                    });
                }
            }
        } catch (IOException e) {
            if (isConnected) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        showStatusMessage("🔌 Error en recepción: " + e.getMessage(), MessageType.ERROR);
                        disconnect();
                    }
                });
            }
        }
    }

    private void processReceivedDataCorrected(byte[] data) {
        try {
            if (currentSelectedCommand.isEmpty()) {
                showStatusMessage("⚠️ No hay comando seleccionado", MessageType.WARNING);
                return;
            }

            if (data == null || data.length < 4) {
                showStatusMessage("❌ Respuesta inválida", MessageType.ERROR);
                return;
            }

            int startByte = data[0] & 0xFF;
            int responseType = data[1] & 0xFF;
            int command = data[2] & 0xFF;

            if (startByte != 0x02) {
                showStatusMessage("❌ Byte de inicio inválido", MessageType.ERROR);
                return;
            }

            if (responseType == 0x45) {
                showStatusMessage("❌ El dispositivo respondió con ERROR", MessageType.ERROR);
                return;
            }

            if (responseType == 0x43) { // CONFIRMATION
                switch (currentSelectedCommand) {
                    case "DEVICE_ID":
                        if (command == 0x00) {
                            processDeviceIdCorrected(data);
                        }
                        break;
                    case "DEVICE_TIME":
                        if (command == 0x01) {
                            processDeviceTimeCorrected(data);
                        }
                        break;
                    case "NODE_SETTINGS":
                        if (command == 0x20) {
                            processNodeSettingsCorrected(data);
                        }
                        break;
                    case "NODE_CURRENT":
                        if (command == 0x21) {
                            processNodeCurrentCorrected(data);
                        }
                        break;
                    default:
                        showStatusMessage("❓ Comando no manejado: " + currentSelectedCommand, MessageType.WARNING);
                        break;
                }

                isWaitingResponse = false;
                updateCommandUI();
            }

        } catch (Exception e) {
            showStatusMessage("❌ Error al procesar datos: " + e.getMessage(), MessageType.ERROR);
            e.printStackTrace();
        }
    }

    private void processDeviceIdCorrected(byte[] data) {
        try {
            currentDeviceInfo = parseDeviceIdResponse(data);
            displayDeviceInfo(currentDeviceInfo);
            updateDeviceIdFields();
            showStatusMessage("✅ DEVICE_ID procesado y mostrado", MessageType.SUCCESS);
        } catch (Exception e) {
            showStatusMessage("❌ Error procesando DEVICE_ID: " + e.getMessage(), MessageType.ERROR);
        }
    }

    private void processDeviceTimeCorrected(byte[] data) {
        try {
            if (data.length >= 10) {
                byte[] timeData = new byte[6];
                System.arraycopy(data, 4, timeData, 0, 6);

                OctoNetCommandEncoder.DecodedDateTime timeInfo = OctoNetCommandEncoder.decodeDateTime(timeData);

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
                Calendar cal = Calendar.getInstance();
                cal.set(timeInfo.year, timeInfo.month - 1, timeInfo.day,
                        timeInfo.hour, timeInfo.minute, timeInfo.second);

                deviceTimeFromResponse = sdf.format(cal.getTime());

                if (editDeviceTime != null) {
                    editDeviceTime.setText(deviceTimeFromResponse);
                    showStatusMessage("✅ Campo DEVICE_TIME actualizado: " + deviceTimeFromResponse, MessageType.SUCCESS);
                }
            } else {
                showStatusMessage("❌ Datos insuficientes para DEVICE_TIME", MessageType.ERROR);
            }
        } catch (Exception e) {
            showStatusMessage("❌ Error procesando DEVICE_TIME: " + e.getMessage(), MessageType.ERROR);
        }
    }

    private void processNodeSettingsCorrected(byte[] data) {
        try {
            int dataSize = data[3] & 0xFF;
            showStatusMessage("⚙️ NODE_SETTINGS - Tamaño: " + dataSize + " bytes", MessageType.INFO);

            if (dataSize > 0) {
                byte[] configData = new byte[dataSize];
                System.arraycopy(data, 4, configData, 0, dataSize);

                updateMeterConfigCorrected(configData);

                if (layoutMeterSettings != null) {
                    layoutMeterSettings.setVisibility(View.VISIBLE);
                    showStatusMessage("✅ Layout configuración mostrado", MessageType.SUCCESS);
                }

                showStatusMessage("✅ NODE_SETTINGS procesado", MessageType.SUCCESS);
            }
        } catch (Exception e) {
            showStatusMessage("❌ Error procesando NODE_SETTINGS: " + e.getMessage(), MessageType.ERROR);
        }
    }

    // ===== MÉTODO CORREGIDO PARA NODE_CURRENT (63+1=64 bytes) =====
    private void processNodeCurrentCorrected(byte[] data) {
        try {
            int sizeFromDevice = data[3] & 0xFF;  // SIZE reportado por el dispositivo
            showStatusMessage("⚡ NODE_CURRENT - SIZE reportado: " + sizeFromDevice, MessageType.INFO);

            if (sizeFromDevice > 0) {
                // ✅ CORRECCIÓN: Para NODE_CURRENT, el tamaño real es SIZE + 1
                int realDataSize = sizeFromDevice + 1;  // 63 + 1 = 64

                showStatusMessage("🔧 CORRECCIÓN NODE_CURRENT: SIZE real = " + sizeFromDevice + " + 1 = " + realDataSize + " bytes", MessageType.INFO);

                // Verificar que tenemos suficientes bytes
                if (data.length < 4 + realDataSize) {
                    showStatusMessage("❌ Respuesta incompleta: recibidos " + data.length + ", necesarios " + (4 + realDataSize), MessageType.ERROR);
                    return;
                }

                // Extraer los datos reales (64 bytes)
                byte[] energyData = new byte[realDataSize];
                System.arraycopy(data, 4, energyData, 0, realDataSize);

                showStatusMessage("✅ Datos extraídos: " + energyData.length + " bytes (estructura completa)", MessageType.SUCCESS);

                // Procesar información básica
                if (energyData.length > 0) {
                    int id = energyData[0] & 0xFF;
                    String sourceType = (id == 0xF3) ? "Fuente Trifásica" :
                            (id == 0xC3) ? "Carga Trifásica" :
                                    "Tipo: 0x" + String.format("%02X", id);
                    showStatusMessage("   ID: " + sourceType, MessageType.INFO);
                }

                if (energyData.length > 1) {
                    int sensorStatus = energyData[1] & 0xFF;
                    if (textEnergySensorStatus != null) {
                        textEnergySensorStatus.setText("🔍 Status: 0x" + String.format("%02X", sensorStatus));
                    }
                }

                // Procesar fecha/hora
                if (energyData.length >= 8) {
                    int year = 2000 + (energyData[2] & 0xFF);
                    int month = energyData[3] & 0xFF;
                    int day = energyData[4] & 0xFF;
                    int hour = energyData[5] & 0xFF;
                    int minute = energyData[6] & 0xFF;
                    int second = energyData[7] & 0xFF;

                    String dateTime = String.format("%02d/%02d/%04d %02d:%02d:%02d", day, month, year, hour, minute, second);
                    if (textEnergyDateTime != null) {
                        textEnergyDateTime.setText("📅 " + dateTime);
                    }
                }

                // Procesar FLASH_ADDRESS
                if (energyData.length >= 12) {
                    long flashAddress = readInt32(energyData, 8);
                    if (textEnergyFlashAddress != null) {
                        textEnergyFlashAddress.setText("📍 Flash: " + flashAddress);
                    }
                }

                // Procesar energías totales
                if (energyData.length >= 28) {
                    long whWbDelta = readInt32(energyData, 12);
                    long whFunDelta = readInt32(energyData, 16);
                    long whWbDay = readInt32(energyData, 20);
                    long whFunDay = readInt32(energyData, 24);

                    if (textEnergyWHWBDelta != null) textEnergyWHWBDelta.setText("⚡ WH_WB_Δ: " + whWbDelta + " Wh");
                    if (textEnergyWHNBDelta != null) textEnergyWHNBDelta.setText("🔄 WH_FUN_Δ: " + whFunDelta + " Wh");
                    if (textEnergyWHWBDay != null) textEnergyWHWBDay.setText("📅 WH_WB_Day: " + whWbDay + " Wh");
                    if (textEnergyWHFunDay != null) textEnergyWHFunDay.setText("🔧 WH_Fun_Day: " + whFunDay + " Wh");
                }

                // Procesar canales con estructura completa de 64 bytes
                showStatusMessage("🔍 Procesando canales con 64 bytes completos:", MessageType.INFO);

                // CH1: Bytes 28-39
                if (energyData.length >= 40) {
                    processChannel64(energyData, 28, 1, textEnergyWCH1, textEnergyVCH1, textEnergyACH1, textEnergyHZCH1, textEnergyAngleCH1);
                }

                // CH2: Bytes 40-51
                if (energyData.length >= 52) {
                    processChannel64(energyData, 40, 2, textEnergyWCH2, textEnergyVCH2, textEnergyACH2, textEnergyHZCH2, textEnergyAngleCH2);
                }

                // CH3: Bytes 52-63 (¡AHORA COMPLETO!)
                if (energyData.length >= 64) {
                    processChannel64(energyData, 52, 3, textEnergyWCH3, textEnergyVCH3, textEnergyACH3, textEnergyHZCH3, textEnergyAngleCH3);
                    showStatusMessage("✅ CH3 procesado con ANGLE completo", MessageType.SUCCESS);
                }

                // Mostrar layout
                if (layoutEnergyData != null) {
                    layoutEnergyData.setVisibility(View.VISIBLE);
                }

                showStatusMessage("✅ NODE_CURRENT procesado exitosamente", MessageType.SUCCESS);
            }
        } catch (Exception e) {
            showStatusMessage("❌ Error procesando NODE_CURRENT: " + e.getMessage(), MessageType.ERROR);
            e.printStackTrace();
        }
    }

    // ===== MÉTODO AUXILIAR PARA PROCESAR CADA CANAL CON 64 BYTES =====
    private void processChannel64(byte[] data, int offset, int channelNum,
                                  TextView wText, TextView vText, TextView aText, TextView hzText, TextView angleText) {
        try {
            String channelName = "CH" + channelNum;

            // W_CHx (Int32) - 4 bytes
            if (data.length >= offset + 4) {
                long powerRaw = readInt32(data, offset);
                float powerW = (int)powerRaw * 0.1f;
                if (wText != null) {
                    wText.setText("⚡ W: " + String.format("%.1f", powerW) + " W");
                }
            }

            // V_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 6) {
                int voltageRaw = readUInt16(data, offset + 4);
                float voltageV = voltageRaw * 0.1f;
                if (vText != null) {
                    vText.setText("🔌 V: " + String.format("%.1f", voltageV) + " V");
                }
            }

            // A_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 8) {
                int currentRaw = readUInt16(data, offset + 6);
                float currentA = currentRaw * 0.1f;
                if (aText != null) {
                    aText.setText("🔋 A: " + String.format("%.1f", currentA) + " A");
                }
            }

            // HZ_CHx (UInt16) - 2 bytes
            if (data.length >= offset + 10) {
                int frequencyRaw = readUInt16(data, offset + 8);
                float freqHz = frequencyRaw * 0.1f;
                if (hzText != null) {
                    hzText.setText("📊 Hz: " + String.format("%.1f", freqHz) + " Hz");
                }
            }

            // ANGLE_CHx (UInt16) - 2 bytes - ¡AHORA DISPONIBLE CON 64 BYTES!
            if (data.length >= offset + 12) {
                int angleRaw = readUInt16(data, offset + 10);
                float angleDeg = angleRaw * 0.1f;
                if (angleText != null) {
                    angleText.setText("📐 Ang: " + String.format("%.1f", angleDeg) + "°");
                }
            } else {
                if (angleText != null) {
                    angleText.setText("📐 Ang: N/A");
                }
            }

            showStatusMessage("   " + channelName + " procesado ✅", MessageType.SUCCESS);

        } catch (Exception e) {
            showStatusMessage("❌ Error procesando CH" + channelNum + ": " + e.getMessage(), MessageType.ERROR);
        }
    }

    private void updateMeterConfigCorrected(byte[] configData) {
        try {
            showStatusMessage("🔧 Actualizando configuración UI...", MessageType.INFO);

            if (configData.length >= 4) {
                // REC_ON/OFF - Byte 0
                if (switchRecOnOff != null) {
                    boolean recording = (configData[0] & 0xFF) == 1;
                    switchRecOnOff.setChecked(recording);
                    showStatusMessage("   REC_ON/OFF actualizado: " + (recording ? "ON" : "OFF"), MessageType.SUCCESS);
                }

                // PERIOD - Byte 1
                if (spinnerPeriod != null) {
                    int periodValue = configData[1] & 0xFF;
                    if (periodValue >= 0 && periodValue <= 3) {
                        spinnerPeriod.setSelection(periodValue);
                        showStatusMessage("   PERIOD actualizado: " + periodValue, MessageType.SUCCESS);
                    }
                }

                // SENSORS - Byte 2
                if (spinnerSensors != null) {
                    int sensorsValue = configData[2] & 0xFF;
                    if (sensorsValue >= 0 && sensorsValue <= 5) {
                        spinnerSensors.setSelection(sensorsValue);
                        showStatusMessage("   SENSORS actualizado: " + sensorsValue, MessageType.SUCCESS);
                    }
                }

                // METERING_TYPE - Byte 3
                if (spinnerMeteringType != null) {
                    int meteringTypeValue = configData[3] & 0xFF;
                    spinnerMeteringType.setSelection(0); // Solo una opción
                    showStatusMessage("   METERING_TYPE: " + meteringTypeValue + " (forzado a Carga Trifásica)", MessageType.SUCCESS);
                }
            }
        } catch (Exception e) {
            showStatusMessage("❌ Error actualizando configuración: " + e.getMessage(), MessageType.ERROR);
        }
    }

    // ===== MÉTODOS AUXILIARES =====

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

    private String decodeSensorStatus(int sensorStatus) {
        StringBuilder status = new StringBuilder();
        if ((sensorStatus & 0x01) != 0) status.append("CH1_OK ");
        if ((sensorStatus & 0x02) != 0) status.append("CH2_OK ");
        if ((sensorStatus & 0x04) != 0) status.append("CH3_OK ");
        if ((sensorStatus & 0x08) != 0) status.append("VOLTAGE_OK ");
        if ((sensorStatus & 0x10) != 0) status.append("CURRENT_OK ");
        if ((sensorStatus & 0x20) != 0) status.append("FREQUENCY_OK ");
        if ((sensorStatus & 0x40) != 0) status.append("PHASE_OK ");
        if ((sensorStatus & 0x80) != 0) status.append("CALIBRATED ");
        return status.length() > 0 ? status.toString().trim() : "SIN_ESTADO";
    }

    private void updateDeviceIdFields() {
        if (currentDeviceInfo != null && "DEVICE_ID".equals(currentSelectedCommand)) {
            if (textDeviceSerial != null) {
                textDeviceSerial.setText("Serial: " + currentDeviceInfo.serial);
            }
            if (textDeviceFacDate != null) {
                textDeviceFacDate.setText("Fecha Fab.: " + currentDeviceInfo.facDate);
            }
            if (textDeviceFacHour != null) {
                textDeviceFacHour.setText("Hora Fab.: " + currentDeviceInfo.facHour);
            }
            if (textDeviceActCode != null) {
                textDeviceActCode.setText("Código Act.: " + currentDeviceInfo.actCode);
            }
            if (textDeviceHwVersion != null) {
                textDeviceHwVersion.setText("HW Version: " + currentDeviceInfo.hwVersion);
            }
            if (textDeviceFwVersion != null) {
                textDeviceFwVersion.setText("FW Version: " + currentDeviceInfo.fwVersion);
            }
        }
    }

    private DeviceIdInfo parseDeviceIdResponse(byte[] data) {
        DeviceIdInfo info = new DeviceIdInfo();

        if (data.length < 45) {
            info.serial = "Error: Datos insuficientes (" + data.length + " bytes, esperados 45+)";
            info.facDate = "N/A";
            info.facHour = "N/A";
            info.actCode = "N/A";
            info.hwVersion = "N/A";
            info.fwVersion = "N/A";
            return info;
        }

        try {
            int offset = 4;

            // Serial (12 bytes)
            byte[] serialBytes = new byte[12];
            System.arraycopy(data, offset, serialBytes, 0, 12);
            info.serial = parseSerial(serialBytes);
            offset += 12;

            // Fecha de fabricación (6 bytes)
            byte[] dateBytes = new byte[6];
            System.arraycopy(data, offset, dateBytes, 0, 6);
            info.facDate = parseDate(dateBytes);
            offset += 6;

            // Hora de fabricación (6 bytes)
            byte[] timeBytes = new byte[6];
            System.arraycopy(data, offset, timeBytes, 0, 6);
            info.facHour = parseTime(timeBytes);
            offset += 6;

            // Código de activación (4 bytes)
            byte[] codeBytes = new byte[4];
            System.arraycopy(data, offset, codeBytes, 0, 4);
            info.actCode = parseCode(codeBytes);
            offset += 4;

            // Versión de Hardware (6 bytes)
            byte[] hwBytes = new byte[6];
            System.arraycopy(data, offset, hwBytes, 0, 6);
            info.hwVersion = parseVersion(hwBytes);
            offset += 6;

            // Versión de Firmware (6 bytes)
            byte[] fwBytes = new byte[6];
            System.arraycopy(data, offset, fwBytes, 0, 6);
            info.fwVersion = parseVersion(fwBytes);

        } catch (Exception e) {
            info.serial = "Error al procesar datos: " + e.getMessage();
            info.facDate = "N/A";
            info.facHour = "N/A";
            info.actCode = "N/A";
            info.hwVersion = "N/A";
            info.fwVersion = "N/A";
        }

        return info;
    }

    private String parseSerial(byte[] serialBytes) {
        StringBuilder serial = new StringBuilder();
        for (int i = 0; i < serialBytes.length; i++) {
            char c = (char) (serialBytes[i] & 0xFF);
            if (c >= 32 && c <= 126) {
                serial.append(c);
            }
        }
        return serial.toString().trim();
    }

    private String parseDate(byte[] dateBytes) {
        if (dateBytes.length < 6) return "000000";
        StringBuilder date = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            char c = (char) (dateBytes[i] & 0xFF);
            if (c >= '0' && c <= '9') {
                date.append(c);
            }
        }
        return date.toString();
    }

    private String parseTime(byte[] timeBytes) {
        if (timeBytes.length < 6) return "000000";
        StringBuilder time = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            char c = (char) (timeBytes[i] & 0xFF);
            if (c >= '0' && c <= '9') {
                time.append(c);
            }
        }
        return time.toString();
    }

    private String parseCode(byte[] codeBytes) {
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeBytes.length; i++) {
            char c = (char) (codeBytes[i] & 0xFF);
            if (c >= 32 && c <= 126) {
                code.append(c);
            }
        }
        return code.toString().trim();
    }

    private String parseVersion(byte[] versionBytes) {
        StringBuilder version = new StringBuilder();
        for (int i = 0; i < versionBytes.length; i++) {
            char c = (char) (versionBytes[i] & 0xFF);
            if (c >= 32 && c <= 126) {
                version.append(c);
            }
        }
        return version.toString().trim();
    }

    private void displayDeviceInfo(DeviceIdInfo deviceInfo) {
        showStatusMessage("", MessageType.INFO);
        showStatusMessage("📋 INFORMACIÓN DEL DISPOSITIVO", MessageType.SUCCESS);

        String deviceInfoString = String.format(
                "{Serial = \"%s\", FacDate = \"%s\", FacHour = \"%s\", ActCode = \"%s\", HwVersion = \"%s\", FwVersion = \"%s\"}",
                deviceInfo.serial.trim(),
                deviceInfo.facDate.trim(),
                deviceInfo.facHour.trim(),
                deviceInfo.actCode.trim(),
                deviceInfo.hwVersion.trim(),
                deviceInfo.fwVersion.trim()
        );

        showStatusMessage(deviceInfoString, MessageType.INFO);
        showStatusMessage("", MessageType.INFO);
        showSnackbar("✅ Device ID recibido", true);
    }

    private void startTimeUpdater() {
        Handler timeHandler = new Handler(Looper.getMainLooper());
        Runnable timeRunnable = new Runnable() {
            @Override
            public void run() {
                updateCurrentTimeDisplay();
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void updateCurrentTimeDisplay() {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(cal.getTime());

        if (textViewCurrentTime != null) {
            textViewCurrentTime.setText("Hora actual: " + currentTime);
        }

        if ("DEVICE_TIME".equals(currentSelectedCommand) && editDeviceTime != null) {
            if (deviceTimeFromResponse != null) {
                editDeviceTime.setText(deviceTimeFromResponse);
            } else {
                editDeviceTime.setText(currentTime);
            }
        }
    }

    private void setupScrollRunnables() {
        scrollUpRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScrollingUp) {
                    scrollViewStatus.scrollBy(0, -SCROLL_SPEED);
                    scrollHandler.postDelayed(this, SCROLL_DELAY);
                }
            }
        };

        scrollDownRunnable = new Runnable() {
            @Override
            public void run() {
                if (isScrollingDown) {
                    scrollViewStatus.scrollBy(0, SCROLL_SPEED);
                    scrollHandler.postDelayed(this, SCROLL_DELAY);
                }
            }
        };
    }

    private void setupTcpListeners() {
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isConnected) {
                    disconnect();
                } else if (!isConnecting) {
                    connectToServer();
                }
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                textViewStatus.setText("");
                showStatusMessage("🗑️ Área de datos limpiada", MessageType.INFO);
            }
        });

        if (btnScrollTop != null) {
            btnScrollTop.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            isScrollingUp = true;
                            scrollHandler.post(scrollUpRunnable);
                            v.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            isScrollingUp = false;
                            scrollHandler.removeCallbacks(scrollUpRunnable);
                            v.setPressed(false);
                            return true;
                    }
                    return false;
                }
            });

            btnScrollTop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollViewStatus.smoothScrollTo(0, 0);
                }
            });
        }

        if (btnScrollBottom != null) {
            btnScrollBottom.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            isScrollingDown = true;
                            scrollHandler.post(scrollDownRunnable);
                            v.setPressed(true);
                            return true;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            isScrollingDown = false;
                            scrollHandler.removeCallbacks(scrollDownRunnable);
                            v.setPressed(false);
                            return true;
                    }
                    return false;
                }
            });

            btnScrollBottom.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    scrollToBottom();
                }
            });
        }
    }

    private void sendTcpCommand(byte[] cmd, String description) {
        try {
            String hex = OctoNetCommandEncoder.bytesToHexString(cmd);

            showStatusMessage("🔍 COMANDO GENERADO:", MessageType.INFO);
            showStatusMessage("   Longitud total: " + cmd.length + " bytes", MessageType.INFO);
            showStatusMessage("   Hex completo: " + hex, MessageType.DATA);

            if (cmd.length >= 4) {
                showStatusMessage("   STX: 0x" + String.format("%02X", cmd[0] & 0xFF), MessageType.INFO);
                showStatusMessage("   TYPE: 0x" + String.format("%02X", cmd[1] & 0xFF), MessageType.INFO);
                showStatusMessage("   CMD: 0x" + String.format("%02X", cmd[2] & 0xFF), MessageType.INFO);
                showStatusMessage("   SIZE: " + (cmd[3] & 0xFF) + " bytes", MessageType.INFO);

                if (cmd.length >= 7) {
                    showStatusMessage("   CHECKSUM: 0x" + String.format("%02X", cmd[cmd.length-2] & 0xFF), MessageType.INFO);
                    showStatusMessage("   ETX: 0x" + String.format("%02X", cmd[cmd.length-1] & 0xFF), MessageType.INFO);
                }
            }

            sendTcpData(cmd);
            isWaitingResponse = true;

            showStatusMessage("📤 " + description + " enviado: " + hex, MessageType.SENT);
            showToast("📤 " + description + " enviado");

            String debugInfo = OctoNetCommandEncoder.debugCommand(cmd, description);
            showStatusMessage("🔍 " + debugInfo, MessageType.INFO);

            updateCommandUI();

        } catch (Exception e) {
            showStatusMessage("❌ Error al enviar comando: " + e.getMessage(), MessageType.ERROR);
            showToast("❌ Error al enviar: " + e.getMessage());
        }
    }

    private void updateCommandUI() {
        boolean canInteract = isConnected && !isWaitingResponse;
        btnRead.setEnabled(canInteract);
        btnWrite.setEnabled(canInteract);

        if (btnReadConfig != null) btnReadConfig.setEnabled(canInteract);
        if (btnWriteConfig != null) btnWriteConfig.setEnabled(canInteract);

        if (isWaitingResponse) {
            btnRead.setText("⏳ Esperando respuesta...");
        } else {
            btnRead.setText("📖 Read " + currentSelectedCommand);
        }
    }

    private void sendTcpData(byte[] data) {
        if (!isConnected || outputStream == null) {
            showToast("No hay conexión activa");
            return;
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    outputStream.write(data);
                    outputStream.flush();

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showStatusMessage("📤 Datos enviados (" + data.length + " bytes)", MessageType.SENT);
                        }
                    });
                } catch (IOException e) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            showStatusMessage("❌ Error al enviar datos: " + e.getMessage(), MessageType.ERROR);
                            disconnect();
                        }
                    });
                }
            }
        });
    }

    private void connectToServer() {
        String ip = editTextIp.getText().toString().trim();
        String portStr = editTextPort.getText().toString().trim();

        if (ip.isEmpty() || portStr.isEmpty()) {
            showSnackbar("⚠️ IP y puerto son requeridos", false);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                throw new NumberFormatException("Puerto fuera de rango");
            }
        } catch (NumberFormatException e) {
            showSnackbar("⚠️ Puerto inválido (1-65535)", false);
            return;
        }

        isConnecting = true;
        updateUI();
        showStatusMessage("🔄 Conectando a " + ip + ":" + port + "...", MessageType.INFO);

        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket();
                    socket.connect(new java.net.InetSocketAddress(ip, port), 15000);
                    socket.setSoTimeout(60000);
                    socket.setReceiveBufferSize(8192);
                    socket.setSendBufferSize(4096);
                    socket.setTcpNoDelay(true);

                    outputStream = socket.getOutputStream();
                    inputReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    isConnected = true;
                    isConnecting = false;

                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateUI(); // ✅ ESTO CAMBIARÁ EL BOTÓN A "DESCONECTAR"
                            showStatusMessage("✅ Conectado exitosamente a " + ip + ":" + port, MessageType.SUCCESS);
                            showStatusMessage("⚡ Listo para comandos OctoNet", MessageType.INFO);
                            showSnackbar("Conectado exitosamente", true);

                            if (!currentSelectedCommand.isEmpty() && !"NODE_SETTINGS".equals(currentSelectedCommand)) {
                                handler.postDelayed(() -> readCurrentCommand(), 1000);
                            }
                        }
                    });

                    receiveThread = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            receiveMessages();
                        }
                    });
                    receiveThread.start();

                } catch (SocketTimeoutException e) {
                    isConnecting = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateUI();
                            showStatusMessage("⏰ Timeout: No se pudo conectar al servidor", MessageType.ERROR);
                            showSnackbar("Timeout de conexión", false);
                        }
                    });
                } catch (IOException e) {
                    isConnecting = false;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            updateUI();
                            showStatusMessage("❌ Error de conexión: " + e.getMessage(), MessageType.ERROR);
                            showSnackbar("Error al conectar", false);
                        }
                    });
                }
            }
        });
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0 && i % 16 == 0) {
                hex.append("\n   ");
            } else if (i > 0) {
                hex.append(" ");
            }
            hex.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return hex.toString();
    }

    private void disconnect() {
        isConnected = false;
        isConnecting = false;
        isWaitingResponse = false;

        clearDeviceData();

        if (stateMachine != null) {
            stateMachine.reset();
        }

        executor.execute(new Runnable() {
            @Override
            public void run() {
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
                } catch (IOException e) {
                    // Ignorar errores al cerrar
                }

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateUI(); // ✅ ESTO CAMBIARÁ EL BOTÓN A "CONECTAR"
                        updateCommandUI();
                        showStatusMessage("🔌 Desconectado del servidor", MessageType.WARNING);
                        showSnackbar("Desconectado", false);
                    }
                });
            }
        });
    }

    private void clearDeviceData() {
        deviceTimeFromResponse = null;
    }

    // ✅ MÉTODO updateUI CORREGIDO PARA CAMBIAR EL BOTÓN CORRECTAMENTE
    private void updateUI() {
        if (isConnected) {
            // ✅ CAMBIAR A MODO DESCONECTADO
            btnConnect.setText("🔌 Desconectar");
            btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_red_dark));
            btnConnect.setEnabled(true); // ✅ IMPORTANTE: Mantener habilitado para poder desconectar
            editTextIp.setEnabled(false);
            editTextPort.setEnabled(false);
        } else if (isConnecting) {
            // ✅ MODO CONECTANDO
            btnConnect.setText("🔄 Conectando...");
            btnConnect.setEnabled(false);
            editTextIp.setEnabled(false);
            editTextPort.setEnabled(false);
        } else {
            // ✅ MODO DESCONECTADO
            btnConnect.setText("🔌 Conectar");
            btnConnect.setBackgroundTintList(ContextCompat.getColorStateList(this, android.R.color.holo_green_dark));
            btnConnect.setEnabled(true);
            editTextIp.setEnabled(true);
            editTextPort.setEnabled(true);
        }
    }

    private void scrollToBottom() {
        scrollViewStatus.post(new Runnable() {
            @Override
            public void run() {
                scrollViewStatus.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private void showStatusMessage(String message, MessageType type) {
        String timestamp = timeFormat.format(new Date());
        String formattedMessage;

        switch (type) {
            case SENT:
                formattedMessage = timestamp + " 📤 " + message;
                break;
            case RECEIVED:
                formattedMessage = timestamp + " 📨 " + message;
                break;
            case ERROR:
                formattedMessage = timestamp + " ❌ " + message;
                break;
            case SUCCESS:
                formattedMessage = timestamp + " ✅ " + message;
                break;
            case WARNING:
                formattedMessage = timestamp + " ⚠️ " + message;
                break;
            case INFO:
                formattedMessage = timestamp + " ℹ️ " + message;
                break;
            case DATA:
                formattedMessage = timestamp + " 📊 " + message;
                break;
            default:
                formattedMessage = timestamp + " " + message;
                break;
        }

        textViewStatus.append(formattedMessage + "\n");
        scrollToBottom();
    }

    private void showSnackbar(String message, boolean isSuccess) {
        View rootView = findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT);

        if (isSuccess) {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        } else {
            snackbar.setBackgroundTint(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        }

        snackbar.show();
    }

    private void setupNavigationButtons() {
        MaterialButton btnGoToFasores = findViewById(R.id.btnGoToFasores);
        if (btnGoToFasores != null) {
            btnGoToFasores.setOnClickListener(v -> goToFasoresActivity());
        }

        MaterialButton btnBackToMainMenu = findViewById(R.id.btnBackToMainMenu);
        if (btnBackToMainMenu != null) {
            btnBackToMainMenu.setOnClickListener(v -> backToMainMenu());
        }
    }

    private void goToFasoresActivity() {
        try {
            Intent intent = new Intent(TcpClientActivity.this, FasoresActivity.class);

            if (isConnected) {
                intent.putExtra("auto_connect", true);
                intent.putExtra("device_ip", editTextIp.getText().toString().trim());
                intent.putExtra("device_port", editTextPort.getText().toString().trim());
                showToast("📊 Abriendo Fasores con datos de conexión");
            } else {
                showToast("📊 Abriendo Fasores");
            }

            startActivity(intent);

        } catch (Exception e) {
            showToast("❌ Error al abrir Fasores: " + e.getMessage());
            showStatusMessage("❌ Error al navegar a Fasores: " + e.getMessage(), MessageType.ERROR);
        }
    }

    private void backToMainMenu() {
        try {
            if (isConnected) {
                showStatusMessage("🔌 Cerrando conexión antes de salir...", MessageType.INFO);
                disconnect();

                handler.postDelayed(() -> {
                    finishAndGoToMain();
                }, 500);
            } else {
                finishAndGoToMain();
            }

        } catch (Exception e) {
            showToast("❌ Error al regresar al menú: " + e.getMessage());
            finishAndGoToMain();
        }
    }

    private void finishAndGoToMain() {
        try {
            // ✅ CAMBIO: Ir al LoginActivity que está en la carpeta activities
            Intent intent = new Intent(TcpClientActivity.this, de.kai_morich.simple_bluetooth_terminal.activities.LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

            showToast("🏠 Regresando al login");
            startActivity(intent);
            finish();

        } catch (Exception e) {
            showToast("❌ Error al regresar, cerrando actividad");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        isScrollingUp = false;
        isScrollingDown = false;
        scrollHandler.removeCallbacks(scrollUpRunnable);
        scrollHandler.removeCallbacks(scrollDownRunnable);

        if (isConnected) {
            disconnect();
        }

        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }

    // ===== ENUMS Y CLASES AUXILIARES =====

    private enum MessageType {
        SENT, RECEIVED, ERROR, SUCCESS, WARNING, INFO, DATA
    }

    private static class DeviceIdInfo {
        String serial = "N/A";
        String facDate = "N/A";
        String facHour = "N/A";
        String actCode = "N/A";
        String hwVersion = "N/A";
        String fwVersion = "N/A";
    }
}