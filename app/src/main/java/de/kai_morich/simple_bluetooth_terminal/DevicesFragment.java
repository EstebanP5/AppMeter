package de.kai_morich.simple_bluetooth_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class DevicesFragment extends Fragment {

    private static final int REQUEST_SELECT_DEVICE = 1;

    private Spinner spinnerCircuito, spinnerTipoDispositivo;
    private Button btnBuscarDispositivo, btnAgregarDispositivo;
    private RecyclerView rvDevicesList;
    private DevicesListAdapter adapter;
    private List<DeviceItem> deviceList = new ArrayList<>();

    public DevicesFragment() {
        // Constructor vacío
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_devices, container, false);

        // Referencias
        spinnerCircuito        = view.findViewById(R.id.spinnerCircuito);
        spinnerTipoDispositivo = view.findViewById(R.id.spinnerTipoDispositivo);
        btnBuscarDispositivo   = view.findViewById(R.id.btnBuscarDispositivo);
        btnAgregarDispositivo  = view.findViewById(R.id.btnMostrarDialogoAgregar);
        rvDevicesList          = view.findViewById(R.id.rvDevicesList);

        // Spinners (valores de ejemplo)
        String[] circuitos    = {"Circuito 1", "Circuito 2", "Circuito 3"};
        String[] dispositivos = {"Ergo Meter Hub", "Otro Dispositivo"};
        spinnerCircuito.setAdapter(
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        circuitos)
        );
        spinnerTipoDispositivo.setAdapter(
                new ArrayAdapter<>(requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        dispositivos)
        );

        // RecyclerView
        rvDevicesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new DevicesListAdapter(deviceList);
        rvDevicesList.setAdapter(adapter);

        // Datos de ejemplo
        deviceList.add(new DeviceItem("120321000000", "Cfe y paneles", "18:43:24", "0 W"));
        deviceList.add(new DeviceItem("120321000000", "Cargador", "18:43:24", "0 W"));
        deviceList.add(new DeviceItem("120321000000", "Consumo Cfe", "18:43:24", "0 W"));
        adapter.notifyDataSetChanged();

        // Botón "Buscar"
        btnBuscarDispositivo.setOnClickListener(v -> {
            // Tu lógica de búsqueda local si aplica
        });

        // Botón "➕ Agregar dispositivo" - CORREGIDO
        btnAgregarDispositivo.setOnClickListener(v -> {
            try {
                // Verificar si la Activity existe antes de lanzarla
                Intent intent = new Intent(requireActivity(), ActivityDevicesBluetooth.class);
                startActivityForResult(intent, REQUEST_SELECT_DEVICE);
            } catch (Exception e) {
                // Si ActivityDevicesBluetooth no existe, usar el Fragment de Bluetooth
                showBluetoothDeviceDialog();
            }
        });

        return view;
    }

    /**
     * Método alternativo para mostrar dispositivos Bluetooth
     */
    private void showBluetoothDeviceDialog() {
        try {
            // Reemplazar el fragment actual con el de selección de dispositivos
            BluetoothDeviceListFragment bluetoothFragment = new BluetoothDeviceListFragment();

            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, bluetoothFragment) // Asegúrate de que este ID existe
                    .addToBackStack(null)
                    .commit();

        } catch (Exception e) {
            // Si no se puede navegar, mostrar un mensaje simple
            if (getContext() != null) {
                android.widget.Toast.makeText(getContext(),
                        "Función de vincular dispositivos en desarrollo",
                        android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_SELECT_DEVICE && resultCode == getActivity().RESULT_OK) {
            if (data != null) {
                String deviceName = data.getStringExtra("device_name");
                String deviceAddress = data.getStringExtra("device_address");

                // Agregar el nuevo dispositivo a la lista
                if (deviceName != null && deviceAddress != null) {
                    deviceList.add(new DeviceItem(deviceAddress, deviceName,
                            getCurrentTime(), "0 W"));
                    adapter.notifyDataSetChanged();

                    if (getContext() != null) {
                        android.widget.Toast.makeText(getContext(),
                                "Dispositivo agregado: " + deviceName,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    /**
     * Obtener la hora actual en formato HH:mm:ss
     */
    private String getCurrentTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm:ss",
                java.util.Locale.getDefault());
        return sdf.format(new java.util.Date());
    }
}