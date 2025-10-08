package de.kai_morich.simple_bluetooth_terminal;

import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.Bundle;
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

public class InformacionDispositivoFragment extends Fragment {

    private SerialSocket socket;
    private TextView tvSerial, tvHwVersion, tvFmVersion;

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
        tvHwVersion = view.findViewById(R.id.tvHwVersion);
        tvFmVersion = view.findViewById(R.id.tvFmVersion);
        Button btnRefrescar = view.findViewById(R.id.btnRefrescar);
        Button btnMedicion  = view.findViewById(R.id.btnMedicion);
        Button btnArmonica  = view.findViewById(R.id.btnArmonica);

        btnRefrescar.setOnClickListener(v -> solicitarInformacion());

        // ✅ CORREGIDO: Iniciar FasoresActivity como una actividad
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

    private void solicitarInformacion() {
        if (socket == null) {
            Toast.makeText(getContext(), "Socket no inicializado", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            try {
                OutputStream out = socket.getOutputStream();
                InputStream in   = socket.getInputStream();
                out.write(("GET_INFO\n").getBytes());
                out.flush();

                byte[] buf = new byte[1024];
                int len = in.read(buf);
                String resp = new String(buf, 0, len);

                String serial = extraer(resp, "SERIAL:");
                String hw     = extraer(resp, "HW_VERSION:");
                String fm     = extraer(resp, "FM_VERSION:");

                requireActivity().runOnUiThread(() -> {
                    tvSerial.setText(serial);
                    tvHwVersion.setText(hw);
                    tvFmVersion.setText(fm);
                });
            } catch (IOException e) {
                requireActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(),
                                "Error conexión: " + e.getMessage(),
                                Toast.LENGTH_LONG).show()
                );
            }
        }).start();
    }

    private String extraer(String text, String key) {
        int i = text.indexOf(key);
        if (i < 0) return "N/A";
        int j = text.indexOf('\n', i);
        if (j < 0) j = text.length();
        return text.substring(i + key.length(), j).trim();
    }
}
