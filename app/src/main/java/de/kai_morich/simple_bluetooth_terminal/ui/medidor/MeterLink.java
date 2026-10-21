package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Enlace TCP con el ErgoMeter (AP del medidor, 192.168.4.1:333) y protocolo OctoNet.
 * Toda la lógica de conexión/comandos/respuestas vive aquí; la pantalla sólo pinta el estado.
 * Los callbacks del {@link Listener} llegan siempre en el hilo principal.
 */
public class MeterLink {

    public interface Listener {
        void onConnectionChanged(boolean connected);
        /** Terminó la lectura inicial (hora, WiFi, ajustes, ID): ya se pueden usar los controles. */
        void onSynced();
        /** Ajustes del medidor: sensor 1..5, cableado 0..2 y si está grabando. */
        void onConfig(int sensors, int wiring, boolean recording);
        void onReading();
        void onDeviceId();
        void onWifi();
        void onSub1g();
        void onRecInfo();
        void onErased();
    }

    public static class DeviceIdInfo {
        public String serial = "N/A", facDate = "N/A", facHour = "N/A",
                actCode = "N/A", hwVersion = "N/A", fwVersion = "N/A";
    }

    // ===== Datos del medidor (4 canales: en 4 cargas son Load0..3; en trifásico se usan 3) =====
    public final float[] v = new float[4], a = new float[4], p = new float[4], hz = new float[4];
    public final float[] ang = {0f, 120f, 240f, 0f};

    public int sensors = 2;      // 01h..05h
    public int wiring = 2;       // 0 = 1F·4C, 1 = 2F·2C, 2 = 3F·1C
    public int period = OctoNetCommandEncoder.PERIOD_5_MIN;
    public boolean recording;

    public DeviceIdInfo deviceId;
    public OctoNetCommandEncoder.WiFiSettings wifi;
    public OctoNetCommandEncoder.Sub1gSettings sub1g;
    public OctoNetCommandEncoder.NodeRecInfo recInfo;
    /** Hora (HH:mm:ss) con la que se sincronizó el reloj del medidor al conectar; null si aún no. */
    public String timeSynced;

    private volatile boolean connected;
    private volatile boolean synced;
    private volatile boolean stopping;
    private volatile boolean waiting;
    private volatile boolean connecting;   // hay un intento de conexión en curso: evita intentos simultáneos
    private int gen;                       // conexión vigente: un receptor viejo no puede tumbar la nueva
    private Boolean reported;              // último estado avisado al listener: sólo se avisa cuando cambia

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile Socket socket;
    private volatile OutputStream out;
    private volatile InputStream in;
    private static final String IP = "192.168.4.1";
    private static final int PORT = 333;

    public MeterLink(Listener listener) {
        this.listener = listener;
    }

    /** Entrega al hilo principal, salvo que el enlace ya se haya cerrado. */
    private void post(Runnable r) {
        main.post(() -> { if (!stopping) r.run(); });
    }

    private void later(Runnable r, long ms) {
        main.postDelayed(() -> { if (!stopping) r.run(); }, ms);
    }

    public boolean isConnected() { return connected; }
    public boolean isSynced() { return synced; }

    // ───────────────────────── conexión ─────────────────────────

    /** Sólo avisa al listener cuando el estado cambia (evita el parpadeo conectado/desconectado). */
    private void report(boolean c) {
        if (reported != null && reported == c) return;
        reported = c;
        listener.onConnectionChanged(c);
    }

    public void connect() {
        if (connected || connecting || stopping) return;
        connecting = true;
        executor.execute(() -> {
            try {
                closeStreams();
                Socket s = new Socket();
                s.connect(new InetSocketAddress(IP, PORT), 10000);
                s.setSoTimeout(15000);
                s.setTcpNoDelay(true);
                s.setKeepAlive(true);
                final InputStream is = s.getInputStream();
                final OutputStream os = s.getOutputStream();
                post(() -> {
                    connecting = false;
                    socket = s;
                    in = is;
                    out = os;
                    connected = true;
                    int g = ++gen;
                    report(true);
                    startReceiver(is, g);
                    later(this::initialSync, 500);
                });
            } catch (Exception e) {
                post(() -> {
                    connecting = false;
                    if (!connected) {
                        report(false);
                        later(this::connect, 3000); // sigue intentando hasta encontrar el medidor
                    }
                });
            }
        });
    }

    /** Cierra el enlace sin reintentar (al salir de la pantalla). */
    public void close() {
        stopping = true;
        connected = false;
        synced = false;
        waiting = false;
        main.removeCallbacksAndMessages(null);
        executor.execute(this::closeStreams);
        executor.shutdown();
    }

    private void closeStreams() {
        try { if (in != null) in.close(); } catch (IOException ignored) { }
        in = null;
        try { if (out != null) { out.flush(); out.close(); } } catch (IOException ignored) { }
        out = null;
        try { if (socket != null && !socket.isClosed()) socket.close(); } catch (IOException ignored) { }
        socket = null;
    }

    private void startReceiver(InputStream is, int g) {
        executor.execute(() -> {
            byte[] buffer = new byte[2048];
            while (!stopping && g == gen && connected) {
                try {
                    int n = is.read(buffer);
                    if (n > 0) {
                        byte[] data = new byte[n];
                        System.arraycopy(buffer, 0, data, 0, n);
                        post(() -> process(data));
                    } else if (n == -1) {
                        break; // el medidor cerró la conexión
                    }
                } catch (SocketTimeoutException e) {
                    // sin datos en 15 s: es normal, se sigue esperando
                } catch (Exception e) {
                    break;
                }
            }
            post(() -> {
                if (g != gen || !connected) return; // ya hay otra conexión (o ya se cerró): no tocar nada
                connected = false;
                synced = false;
                executor.execute(this::closeStreams);
                report(false);
                later(this::connect, 3000); // reintenta solo
            });
        });
    }

    /** Hora, WiFi, ajustes, ID y memoria; al terminar habilita los controles. */
    private void initialSync() {
        if (!connected) return;
        executor.execute(() -> {
            try {
                sendTime();
                Thread.sleep(1000);
                send(OctoNetCommandEncoder.createWiFiSettingsReadCommand());
                Thread.sleep(1000);
                readSettings();
                Thread.sleep(1500);
                send(OctoNetCommandEncoder.createDeviceIdReadCommand());
                Thread.sleep(1000);
                send(OctoNetCommandEncoder.createNodeRecInfoReadCommand());
                Thread.sleep(500);
            } catch (Exception ignored) { }
            post(() -> {
                synced = true;
                waiting = false;
                listener.onSynced();
            });
        });
    }

    // ───────────────────────── comandos ─────────────────────────

    private void send(byte[] command) {
        if (!connected || out == null) return;
        executor.execute(() -> {
            try {
                if (out != null) {
                    out.write(command);
                    out.flush();
                }
            } catch (IOException e) {
                post(() -> {
                    connected = false;
                    listener.onConnectionChanged(false);
                });
            }
        });
    }

    /** Comando que espera respuesta: evita solapar peticiones hasta que llegue o venza el plazo. */
    private void sendAwaiting(byte[] command, long timeoutMs) {
        send(command);
        waiting = true;
        later(() -> waiting = false, timeoutMs);
    }

    private void sendTime() {
        executor.execute(() -> {
            try {
                if (!connected || out == null) return;
                Calendar c = Calendar.getInstance();
                out.write(OctoNetCommandEncoder.createDeviceTimeWriteCommand(
                        c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                        c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), c.get(Calendar.SECOND),
                        c.get(Calendar.DAY_OF_WEEK)));
                out.flush();
                String hora = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
                post(() -> { timeSynced = hora; listener.onDeviceId(); });
            } catch (Exception ignored) { }
        });
    }

    private void readSettings() {
        if (!connected) return;
        send(OctoNetCommandEncoder.createNodeSettingsReadCommand());
        waiting = true;
        later(() -> {
            if (waiting) {
                waiting = false;
                synced = true;
                listener.onSynced();
            }
        }, 5000);
    }

    /** Lectura en vivo (una trama). */
    public void readCurrent() {
        if (!connected || waiting || socket == null || socket.isClosed()) return;
        sendAwaiting(OctoNetCommandEncoder.createNodeCurrentReadCommand(), 3000);
    }

    /** Envía cableado / sensor / grabación; false si hay una petición en curso. */
    public boolean writeSettings(boolean rec, int sensorsValue, int wiringValue) {
        if (!connected || waiting) return false;
        sensors = sensorsValue;
        wiring = wiringValue;
        sendAwaiting(OctoNetCommandEncoder.createNodeSettingsWriteCommand(rec, period, sensorsValue, wiringValue), 5000);
        return true;
    }

    public void readDeviceId() { send(OctoNetCommandEncoder.createDeviceIdReadCommand()); }
    public void readWifi() { send(OctoNetCommandEncoder.createWiFiSettingsReadCommand()); }
    public void readSub1g() { send(OctoNetCommandEncoder.createSub1gSettingsReadCommand()); }
    public void readRecInfo() { send(OctoNetCommandEncoder.createNodeRecInfoReadCommand()); }
    public void eraseMemory() { send(OctoNetCommandEncoder.createNodeRecEraseCommand()); }

    public void writeWifi(String ssid, String password) {
        String pw = password == null || password.trim().isEmpty() ? " " : password; // red abierta = 0x20
        send(OctoNetCommandEncoder.createWiFiSettingsWriteCommand(ssid, pw));
    }

    public void writeSub1g(int modem, int channel, int hop, int broadcast, int unicast, int gfskBr, int loraBr) {
        send(OctoNetCommandEncoder.createSub1gSettingsWriteCommand(modem, channel, hop, broadcast, unicast, gfskBr, loraBr));
    }

    // ───────────────────────── respuestas ─────────────────────────

    private static int u16(byte[] d, int o) { return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8); }

    /** Int32 con signo (Tabla 15): la potencia es negativa cuando hay inyección o sensor invertido. */
    private static int i32(byte[] d, int o) {
        return (d[o] & 0xFF) | ((d[o + 1] & 0xFF) << 8) | ((d[o + 2] & 0xFF) << 16) | (d[o + 3] << 24);
    }

    private void process(byte[] data) {
        try {
            waiting = false;
            if (data == null || data.length < 4) return;
            if (!OctoNetCommandEncoder.validateCommandStructure(data)) return;
            if (!OctoNetCommandEncoder.verifyChecksum(data)) return;

            int type = data[1] & 0xFF, cmd = data[2] & 0xFF, size = data[3] & 0xFF;
            if (type == 0x45 || type != 0x43) return;

            switch (cmd) {
                case 0x00: parseDeviceId(data); break;
                case 0x20: if (size > 0) parseSettings(data); break;
                case 0x21: if (size > 0) parseCurrent(data); break;
                case 0x22: parseRecInfo(data); break;
                case 0x25: listener.onErased(); break;
                case 0xE3: if (size > 0) parseWifi(data); break;
                case 0xE4: if (size > 0) parseSub1g(data); break;
                default: break;
            }
        } catch (Exception ignored) {
        } finally {
            waiting = false;
        }
    }

    private void parseDeviceId(byte[] response) {
        byte[] d = OctoNetCommandEncoder.extractCommandData(response);
        if (d == null || d.length == 0) return;
        DeviceIdInfo info = new DeviceIdInfo();
        try {
            String s = new String(d, "UTF-8").trim();
            if (s.length() >= 30) {
                info.serial = s.substring(0, 12);
                String date = s.substring(12, 18), time = s.substring(18, 24);
                info.facDate = date.substring(0, 2) + "/" + date.substring(2, 4) + "/20" + date.substring(4, 6);
                info.facHour = time.substring(0, 2) + ":" + time.substring(2, 4) + ":" + time.substring(4, 6);
                info.actCode = s.substring(24, 28);
                info.hwVersion = s.substring(28, 34);
                info.fwVersion = s.substring(34);
            } else {
                info.serial = s.isEmpty() ? "DESCONOCIDO" : s;
            }
        } catch (Exception e) {
            info.serial = "ERROR";
        }
        deviceId = info;
        listener.onDeviceId();
    }

    private void parseWifi(byte[] response) {
        byte[] d = OctoNetCommandEncoder.extractCommandData(response);
        if (d == null || d.length < 64) return;
        wifi = OctoNetCommandEncoder.processWiFiSettingsResponse(d);
        listener.onWifi();
    }

    private void parseSub1g(byte[] response) {
        byte[] d = OctoNetCommandEncoder.extractCommandData(response);
        if (d == null || d.length < 14) return;
        sub1g = OctoNetCommandEncoder.processSub1gSettingsResponse(d);
        listener.onSub1g();
    }

    private void parseRecInfo(byte[] response) {
        byte[] d = OctoNetCommandEncoder.extractCommandData(response);
        if (d == null || d.length < 20) return;
        recInfo = OctoNetCommandEncoder.processNodeRecInfoResponse(d);
        listener.onRecInfo();
    }

    private void parseSettings(byte[] data) {
        byte[] d = OctoNetCommandEncoder.extractCommandData(data);
        if (d == null || d.length < 12) return;
        // Tabla 11: el byte 0 SIEMPRE es EAh. El medidor también contesta 43/20 con otra
        // estructura; parsearla como ajustes inventa valores de sensor y cableado.
        if ((d[0] & 0xFF) != 0xEA) return;

        boolean rec = (d[8] & 0xFF) == 0x01;
        int sens = d[10] & 0xFF;
        int wir = d[11] & 0xFF;
        // Trama corrupta: se descarta en vez de tirar la configuración a un valor por defecto
        if (sens < 1 || sens > FasorDiagnosis.SENSOR_AMPS.length || wir < 0 || wir > 2) return;

        sensors = sens;
        wiring = wir;
        period = OctoNetCommandEncoder.PERIOD_5_MIN; // único valor válido
        recording = rec;
        synced = true;
        listener.onConfig(sens, wir, rec);
    }

    private void parseCurrent(byte[] response) {
        if (response == null || response.length < 4) return;
        int size = response[3] & 0xFF;
        if (size == 0) return;
        int real = size + 1;
        if (response.length < 4 + real + 2) return;

        byte[] e = new byte[real];
        System.arraycopy(response, 4, e, 0, real);
        if (e.length < 64) return;

        // Tabla 15 - METER_BASIC_STRC. El byte ID dice el cableado: cuántos canales trae la trama y desde cuál.
        int id = e[0] & 0xFF, canales, base;
        switch (id) {
            case 0x12: canales = 4; base = 0; break; // 1PH_2W_4LOADS → Load0..3
            case 0x13: canales = 4; base = 0; break; // 1PH_3W_2LOADS → CT-0..3
            case 0x34:
            case 0x44: canales = 3; base = 1; break; // 3PH_4W_1LOAD (el canal 0 es N)
            default: canales = 0; base = 0; break;
        }

        if (canales > 0 && e.length >= 112) {
            // W[4] bytes 16-31 (Int32, 0.1 W) · V[4] 80-87 · A[4] 88-95 · Hz[4] 96-103 · ANGLE[4] 104-111 (Uint16, 0.1)
            for (int i = 0; i < 4; i++) {
                if (i >= canales) { v[i] = a[i] = p[i] = hz[i] = ang[i] = 0f; continue; }
                int ch = base + i;
                v[i] = u16(e, 80 + ch * 2) * 0.1f;
                a[i] = u16(e, 88 + ch * 2) * 0.1f;
                hz[i] = u16(e, 96 + ch * 2) * 0.1f;
                ang[i] = u16(e, 104 + ch * 2) * 0.1f;
                p[i] = i32(e, 16 + ch * 4) * 0.1f;
            }
        } else {
            // ENERGY_3PHA_PAGE_STRC: canales en 28, 40 y 52
            if (e.length >= 40) channel(e, 28, 0);
            if (e.length >= 52) channel(e, 40, 1);
            if (e.length >= 64) channel(e, 52, 2);
            v[3] = a[3] = p[3] = hz[3] = ang[3] = 0f;
        }
        listener.onReading();
    }

    private void channel(byte[] d, int o, int i) {
        if (d.length >= o + 4) p[i] = i32(d, o) * 0.1f;
        if (d.length >= o + 6) v[i] = u16(d, o + 4) * 0.1f;
        if (d.length >= o + 8) a[i] = u16(d, o + 6) * 0.1f;
        if (d.length >= o + 10) hz[i] = u16(d, o + 8) * 0.1f;
        ang[i] = d.length >= o + 12 ? u16(d, o + 10) * 0.1f : i * 120f;
    }
}
