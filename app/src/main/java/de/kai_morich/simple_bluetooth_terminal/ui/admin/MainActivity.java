package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;


import java.io.IOException;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.legacy.AdminMedicionesActivity;
import de.kai_morich.simple_bluetooth_terminal.legacy.MedicionesActivity;
import de.kai_morich.simple_bluetooth_terminal.serial.SerialListener;
import de.kai_morich.simple_bluetooth_terminal.serial.SerialService;
import de.kai_morich.simple_bluetooth_terminal.serial.SerialSocket;
import de.kai_morich.simple_bluetooth_terminal.ui.login.LoginActivity;
import de.kai_morich.simple_bluetooth_terminal.ui.medidor.InformacionDispositivoFragment;
import de.kai_morich.simple_bluetooth_terminal.ui.medidor.WiFiSetupActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_SELECT_DEVICE = 1;

    private View navInicio, navMediciones, navConfigurar;
    private View currentTab;

    private BluetoothAdapter bluetoothAdapter;
    private SerialService serialService;
    private boolean serviceBound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            serialService = ((SerialService.SerialBinder) binder).getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serialService = null;
            serviceBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(R.style.Theme_App_NaranjaAzul);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind al SerialService
        bindService(new Intent(this, SerialService.class),
                serviceConnection, Context.BIND_AUTO_CREATE);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        initBottomNav();

        // Pestaña por defecto
        if (savedInstanceState == null) {
            showTab(navInicio);
        } else {
            // Al recrear la actividad el FragmentManager restaura el fragmento; solo marcamos la pestaña
            setSelectedTab(navInicio);
        }
    }

    /** Barra inferior fija: Inicio · Mediciones · Configurar. */
    private void initBottomNav() {
        navInicio = findViewById(R.id.nav_inicio);
        navMediciones = findViewById(R.id.nav_mediciones);
        navConfigurar = findViewById(R.id.nav_configurar);
        navInicio.setOnClickListener(v -> showTab(v));
        navMediciones.setOnClickListener(v -> showTab(v));
        navConfigurar.setOnClickListener(v -> showTab(v));

        // Edge-to-edge (targetSdk 36): el header oscuro queda bajo la barra de estado y la barra inferior sobre la de gestos
        View root = findViewById(R.id.main_root);
        View bottomNav = findViewById(R.id.bottom_nav);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // el contenido se dibuja bajo las barras; los márgenes salen de los insets
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
    }

    private void setSelectedTab(View tab) {
        currentTab = tab;
        for (View t : new View[]{navInicio, navMediciones, navConfigurar}) {
            boolean on = t == tab;
            t.setSelected(on);
            // Activo: peso 600
            ((android.widget.TextView) ((android.view.ViewGroup) t).getChildAt(1))
                    .setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    private void showTab(View tab) {
        if (tab == currentTab) return;
        Fragment frag = tab == navConfigurar ? new ConfigClientesFragment()
                : tab == navMediciones ? new AdminMedicionesFragment() : new AdminInicioFragment();
        getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, frag)
                .commit();
        setSelectedTab(tab);
    }

    /** Abre una pantalla encima de la pestaña actual (por ejemplo el árbol de un cliente); atrás regresa. */
    public void push(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f)
                .addToBackStack(null)
                .commit();
    }

    /** Atajo desde Inicio (tarjeta "Nodos"). */
    public void openMediciones() {
        showTab(navMediciones);
    }

    /** Pregunta antes de cerrar sesión: el botón "Salir" está a la vista en las tres pestañas. */
    public void confirmLogout() {
        Forms.confirm(this, "Cerrar sesión", "¿Quieres salir de tu cuenta?", "Salir", this::logout);
    }

    /** Cierra la sesión: borra las preferencias del usuario y el token. */
    public void logout() {
        SharedPreferences.Editor editor = getSharedPreferences("user_prefs", MODE_PRIVATE).edit();
        editor.clear().apply();
        new de.kai_morich.simple_bluetooth_terminal.api.TokenManager(this).clearToken();
        de.kai_morich.simple_bluetooth_terminal.api.ApiClient.clearToken();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    // ✅ NUEVO: Método para abrir WiFi Setup con manejo de errores
    private void openWiFiSetup() {
        try {
            Intent intent = new Intent(this, WiFiSetupActivity.class);
            startActivity(intent);

            // Toast opcional para confirmar navegación
            Toast.makeText(this, "📶 Abriendo WiFi Setup...", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "❌ Error al abrir WiFi Setup: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();

            // Log para debugging
            android.util.Log.e("MainActivity", "Error abriendo WiFiSetupActivity", e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_DEVICE && resultCode == RESULT_OK && serviceBound && data != null) {
            String address = data.getStringExtra("device_address");
            if (address != null && bluetoothAdapter != null) {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                SerialSocket socket = new SerialSocket(getApplicationContext(), device);
                try {
                    serialService.connect(socket);
                    serialService.attach(new SerialListener() {
                        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                        @Override
                        public void onSerialConnect() {
                            runOnUiThread(() -> {
                                Toast.makeText(MainActivity.this, "Conectado a " + device.getName(), Toast.LENGTH_SHORT).show();
                                InformacionDispositivoFragment frag = InformacionDispositivoFragment.newInstance(socket);
                                getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, frag)
                                        .addToBackStack(null)
                                        .commit();
                            });
                        }

                        @Override
                        public void onSerialConnectError(Exception e) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this,
                                            "Error al conectar: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onSerialRead(byte[] data) {}

                        @Override
                        public void onSerialRead(java.util.ArrayDeque<byte[]> datas) {}

                        @Override
                        public void onSerialIoError(Exception e) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this,
                                            "Error I/O: " + e.getMessage(),
                                            Toast.LENGTH_LONG).show());
                        }
                    });
                } catch (IOException e) {
                    Toast.makeText(this, "Error en conexión: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }


    @Override
    public void onBackPressed() {
        // Desde Configurar sin pantallas apiladas, atrás vuelve a Inicio
        if (getSupportFragmentManager().getBackStackEntryCount() == 0 && currentTab != navInicio) {
            showTab(navInicio);
        } else {
            super.onBackPressed();
        }
    }

    // ✅ OPCIONAL: Método para manejar regreso desde WiFiSetupActivity
    @Override
    protected void onResume() {
        super.onResume();

        // Verificar si regresamos de WiFiSetupActivity con algún resultado
        Intent intent = getIntent();
        if (intent != null && intent.getBooleanExtra("from_wifi_setup", false)) {
            // Realizar alguna acción si es necesario
            Toast.makeText(this, "✅ Regresando de WiFi Setup", Toast.LENGTH_SHORT).show();

            // Limpiar el flag
            intent.removeExtra("from_wifi_setup");
        }
    }
}