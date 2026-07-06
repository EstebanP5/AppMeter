package de.kai_morich.simple_bluetooth_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class InformacionDispositivoFragment extends Fragment {

    private SerialSocket socket;
    private TextView tvSerial, tvHwVersion, tvFmVersion;
    private TextView tvFechaFab, tvHoraFab, tvKeyCode, tvComandoHex;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    public InformacionDispositivoFragment() {}

    public static InformacionDispositivoFragment newInstance(SerialSocket socket) {
        InformacionDispositivoFragment f = new InformacionDispositivoFragment();
        f.socket = socket;
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_informacion_dispositivo, container, false);

        tvSerial    = view.findViewById(R.id.tvSerial);
        tvFechaFab  = view.findViewById(R.id.tvFechaFab);
        tvHoraFab   = view.findViewById(R.id.tvHoraFab);
        tvKeyCode   = view.findViewById(R.id.tvKeyCode);
        tvHwVersion = view.findViewById(R.id.tvHwVersion);
        tvFmVersion = view.findViewById(R.id.tvFmVersion);
        tvComandoHex = view.findViewById(R.id.tvComandoHex);

        Button btnRefrescar = view.findViewById(R.id.btnRefrescar);
        Button btnMedicion  = view.findViewById(R.id.btnMedicion);
        Button btnArmonica  = view.findViewById(R.id.btnArmonica);

        btnRefrescar.setOnClickListener(v -> solicitarInformacion());

        btnMedicion.setOnClickListener(v -> {
            Intent intent = new Intent(getActivity(), FasoresActivity.class);
            startActivity(intent);
        });

        btnArmonica.setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ArmonicaFragment())
                        .addToBackStack(null)
                        .commit()
        );

        solicitarInformacion();
        return view;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Envía el comando DEVICE_ID_READ (protocolo hex) y lee la respuesta
    // Comando: 02 52 00 00 00 A6 03
    // Respuesta: 02 43 00 27 <DEVICE_ID_STRC 40 bytes> <CHK> 03
    // ────────────────────────────────────────────────────────────────────────
    private void solicitarInformacion() {
        if (socket == null) {
            Toast.makeText(getContext(), "Socket no inicializado", Toast.LENGTH_SHORT).show();
            return;
        }

        // Construir comando usando el encoder
        byte[] comando = OctoNetCommandEncoder.createDeviceIdReadCommand();

        // Mostrar el comando hex en la UI
        String hexCmd = "Cmd: " + OctoNetCommandEncoder.bytesToHexString(comando);
        if (tvComandoHex != null) tvComandoHex.setText(hexCmd);

        new Thread(() -> {
            try {
                OutputStream out = socket.getOutputStream();
                InputStream  in  = socket.getInputStream();

                // Enviar comando hex
                out.write(comando);
                out.flush();

                // Leer respuesta (máx 512 bytes con timeout implícito del socket)
                byte[] buf = new byte[512];
                int totalRead = 0;
                long deadline = System.currentTimeMillis() + 3000; // 3 s

                while (System.currentTimeMillis() < deadline) {
                    int available = in.available();
                    if (available > 0) {
                        int read = in.read(buf, totalRead, Math.min(available, buf.length - totalRead));
                        if (read > 0) totalRead += read;
                        // Comprobar si el frame está completo: STX ... ETX
                        if (totalRead >= 7 && buf[totalRead - 1] == 0x03) break;
                    } else {
                        Thread.sleep(20);
                    }
                }

                if (totalRead < 7) {
                    showToastOnUi("Sin respuesta del dispositivo");
                    return;
                }

                byte[] frame = new byte[totalRead];
                System.arraycopy(buf, 0, frame, 0, totalRead);

                // Validar estructura y checksum
                if (!OctoNetCommandEncoder.validateCommandStructure(frame)) {
                    showToastOnUi("Respuesta inválida (estructura)");
                    return;
                }
                if (!OctoNetCommandEncoder.verifyChecksum(frame)) {
                    showToastOnUi("Error de checksum");
                    return;
                }

                // Extraer datos: DEVICE_ID_STRC = 40 bytes (NDATA = 0x27 = 39 → 39+1 = 40)
                byte[] deviceData = OctoNetCommandEncoder.extractCommandData(frame);
                if (deviceData == null || deviceData.length < 30) {
                    showToastOnUi("Datos insuficientes: " + (deviceData == null ? 0 : deviceData.length) + " bytes");
                    return;
                }

                // ── Parsear DEVICE_ID_STRC según Tabla 3 ──
                // Bytes  0-11 (12): SERIAL       – Char
                // Bytes 12-17  (6): MANUFACTURE_DATE (DDMMYY) – Char
                // Bytes 18-23  (6): MANUFACTURE_HOUR (hhmmss) – Char
                // Bytes 24-27  (4): KEY_CODE     – Char
                // Bytes 28-33  (6): HW_VERSION   – Char
                // Bytes 34-39  (6): FW_VERSION   – Char

                String serial  = readAsciiField(deviceData, 0,  12);
                String dateFab = readAsciiField(deviceData, 12,  6);
                String hourFab = readAsciiField(deviceData, 18,  6);
                String keyCode = readAsciiField(deviceData, 24,  4);
                String hwVer   = readAsciiField(deviceData, 28,  6);
                String fwVer   = deviceData.length > 34
                        ? readAsciiField(deviceData, 34, deviceData.length - 34)
                        : "—";

                // Formatear fecha DD/MM/YY y hora hh:mm:ss
                String fechaFmt = formatDate(dateFab);
                String horaFmt  = formatTime(hourFab);

                // Capturar como efectivamente final para usar en la lambda
                final int bytesLeidos = totalRead;
                final String respHex = OctoNetCommandEncoder.bytesToHexString(frame);

                uiHandler.post(() -> {
                    if (!isAdded()) return;
                    tvSerial.setText("Serial: " + serial);
                    tvFechaFab.setText("Fecha fab.: " + fechaFmt);
                    tvHoraFab.setText("Hora fab.: " + horaFmt);
                    tvKeyCode.setText("Código llave: " + keyCode);
                    tvHwVersion.setText("HW Version: " + hwVer);
                    tvFmVersion.setText("FW Version: " + fwVer);
                    tvComandoHex.setText("Resp (" + bytesLeidos + "B): " + respHex);
                });

            } catch (Exception e) {
                showToastOnUi("Error conexión: " + e.getMessage());
            }
        }).start();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Lee `length` bytes de `data` a partir de `offset` como ASCII, sin nulos. */
    private String readAsciiField(byte[] data, int offset, int length) {
        if (offset >= data.length) return "—";
        int end = Math.min(offset + length, data.length);
        byte[] field = new byte[end - offset];
        System.arraycopy(data, offset, field, 0, field.length);
        return new String(field, StandardCharsets.US_ASCII)
                .replace("\0", "")
                .trim();
    }

    /** Formatea cadena "DDMMYY" → "DD/MM/YY" */
    private String formatDate(String raw) {
        if (raw == null || raw.length() < 6) return raw;
        return raw.substring(0, 2) + "/" + raw.substring(2, 4) + "/" + raw.substring(4, 6);
    }

    /** Formatea cadena "hhmmss" → "hh:mm:ss" */
    private String formatTime(String raw) {
        if (raw == null || raw.length() < 6) return raw;
        return raw.substring(0, 2) + ":" + raw.substring(2, 4) + ":" + raw.substring(4, 6);
    }

    private void showToastOnUi(String msg) {
        uiHandler.post(() -> {
            if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        });
    }
}
