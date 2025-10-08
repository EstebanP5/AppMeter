package de.kai_morich.simple_bluetooth_terminal;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.Set;

public class ActivityDevicesBluetooth extends Activity {

    private static final int REQUEST_BLUETOOTH_CONNECT = 1001;

    private ListView listView;
    private BluetoothAdapter bluetoothAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices_bluetooth);

        listView = findViewById(R.id.listViewBluetoothDevices);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth no soportado en este dispositivo", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 1);
        } else {
            verificarPermisosYMostrar();
        }
    }

    private void verificarPermisosYMostrar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.BLUETOOTH_CONNECT},
                        REQUEST_BLUETOOTH_CONNECT);
                return;
            }
        }

        mostrarDispositivosEmparejados();
    }

    private void mostrarDispositivosEmparejados() {
        Set<BluetoothDevice> pairedDevices;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Permiso requerido para mostrar dispositivos", Toast.LENGTH_SHORT).show();
            return;
        }

        pairedDevices = bluetoothAdapter.getBondedDevices();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);

        for (BluetoothDevice device : pairedDevices) {
            String name = device.getName() != null ? device.getName() : "Desconocido";
            adapter.add(name + "\n" + device.getAddress());
        }

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) parent.getItemAtPosition(position);
            String deviceAddress = item.substring(item.length() - 17); // Últimos 17 caracteres = dirección MAC

            Intent resultIntent = new Intent();
            resultIntent.putExtra("device_address", deviceAddress);
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mostrarDispositivosEmparejados();
            } else {
                Toast.makeText(this, "Permiso BLUETOOTH_CONNECT denegado", Toast.LENGTH_LONG).show();
                finish(); // Cerramos si el permiso es esencial y no se concede
            }
        }
    }
}
