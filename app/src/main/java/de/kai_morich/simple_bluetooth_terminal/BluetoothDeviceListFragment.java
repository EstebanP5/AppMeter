package de.kai_morich.simple_bluetooth_terminal;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.Set;

public class BluetoothDeviceListFragment extends Fragment {

    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> devicesAdapter;
    private ArrayList<BluetoothDevice> deviceObjects = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_device_list, container, false);
        ListView listView = view.findViewById(R.id.device_list);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "Bluetooth no es compatible en este dispositivo", Toast.LENGTH_SHORT).show();
            return view;
        }

        // Verificar permisos de Bluetooth
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            loadPairedDevices(listView);
        }

        return view;
    }

    /**
     * Cargar dispositivos emparejados
     */
    private void loadPairedDevices(ListView listView) {
        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            ArrayList<String> deviceList = new ArrayList<>();
            deviceObjects.clear();

            if (pairedDevices.size() > 0) {
                for (BluetoothDevice device : pairedDevices) {
                    String deviceName = device.getName();
                    String deviceAddress = device.getAddress();

                    // Manejar dispositivos sin nombre
                    if (deviceName == null || deviceName.isEmpty()) {
                        deviceName = "Dispositivo desconocido";
                    }

                    deviceList.add(deviceName + "\n" + deviceAddress);
                    deviceObjects.add(device);
                }
            } else {
                deviceList.add("No hay dispositivos emparejados");
            }

            devicesAdapter = new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_list_item_1, deviceList);
            listView.setAdapter(devicesAdapter);

            listView.setOnItemClickListener((parent, view1, position, id) -> {
                if (position < deviceObjects.size()) {
                    BluetoothDevice selectedDevice = deviceObjects.get(position);
                    onDeviceSelected(selectedDevice);
                }
            });

        } catch (SecurityException e) {
            Toast.makeText(getContext(), "Error de permisos de Bluetooth", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Error al cargar dispositivos: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Manejar la selección de un dispositivo
     */
    private void onDeviceSelected(BluetoothDevice device) {
        try {
            String deviceName = device.getName();
            String deviceAddress = device.getAddress();

            if (deviceName == null) {
                deviceName = "Dispositivo desconocido";
            }

            Toast.makeText(getContext(), "Seleccionado: " + deviceName, Toast.LENGTH_SHORT).show();

            // Aquí puedes agregar lógica para conectar o agregar el dispositivo
            // Por ejemplo, volver al fragment anterior con el dispositivo seleccionado

            // Volver al fragment anterior
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }

        } catch (Exception e) {
            Toast.makeText(getContext(), "Error al seleccionar dispositivo", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == getActivity().RESULT_OK) {
                // Bluetooth habilitado, cargar dispositivos
                View view = getView();
                if (view != null) {
                    ListView listView = view.findViewById(R.id.device_list);
                    loadPairedDevices(listView);
                }
            } else {
                // Bluetooth no habilitado
                Toast.makeText(getContext(), "Bluetooth es necesario para esta función", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Recargar dispositivos cuando el fragment se reanude
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            View view = getView();
            if (view != null) {
                ListView listView = view.findViewById(R.id.device_list);
                loadPairedDevices(listView);
            }
        }
    }
}