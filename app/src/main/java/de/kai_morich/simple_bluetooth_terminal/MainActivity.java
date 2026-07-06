package de.kai_morich.simple_bluetooth_terminal;

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
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.navigation.NavigationView;

import java.io.IOException;

import de.kai_morich.simple_bluetooth_terminal.activities.LoginActivity;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.activities.MisDispositivosActivity;
import de.kai_morich.simple_bluetooth_terminal.activities.AdminMedicionesActivity;
import de.kai_morich.simple_bluetooth_terminal.activities.MedicionesActivity;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final int REQUEST_SELECT_DEVICE = 1;

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private ImageButton btnLogout;

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

        initToolbar();
        initDrawer();
        updateNavHeader();

        // Fragmento por defecto
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new DashboardFragment())
                    .commit();
            navigationView.setCheckedItem(R.id.nav_dashboard);
        }

        // Configurar visibilidad del menú según rol
        configureDrawerMenu();
    }

    private void configureDrawerMenu() {
        TokenManager tokenManager = new TokenManager(this);
        boolean isAdmin = JwtUtils.isInstallerAdmin(tokenManager.getToken());

        android.view.Menu menu = navigationView.getMenu();

        // Dashboard: siempre visible
        // Clientes: solo admin
        menu.findItem(R.id.nav_clientes).setVisible(isAdmin);
        // Mediciones Admin: solo admin
        menu.findItem(R.id.nav_mediciones_admin).setVisible(isAdmin);
        // Mediciones: todos
        menu.findItem(R.id.nav_mediciones).setVisible(true);
        // Mis Dispositivos: deshabilitado
        // menu.findItem(R.id.nav_mis_dispositivos).setVisible(true);
        // Configuración: todos
        menu.findItem(R.id.nav_configuracion).setVisible(true);
    }

    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // El botón está dentro del toolbar
        btnLogout = toolbar.findViewById(R.id.btnLogout);

        if (btnLogout != null) {
            btnLogout.setOnClickListener(v -> {
                SharedPreferences.Editor editor = getSharedPreferences("user_prefs", MODE_PRIVATE).edit();
                editor.clear().apply();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        }
    }

    private void initDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        // Buscar el toolbar correctamente
        Toolbar toolbar = findViewById(R.id.toolbar);

        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close
        );
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
    }

    private void updateNavHeader() {
        View header = navigationView.getHeaderView(0);
        TextView tv = header.findViewById(R.id.tvNavHeaderSubtitle);
        String user = getSharedPreferences("user_prefs", MODE_PRIVATE)
                .getString("user_name", "Usuario");
        tv.setText("Bienvenido, " + user);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment frag = null;
        int id = item.getItemId();

        if (id == R.id.nav_dashboard) {
            frag = new DashboardFragment();

        } else if (id == R.id.nav_clientes) {
            frag = new ConfiguracionFragment(); // Fragmento correcto para clientes

        } else if (id == R.id.nav_configuracion) {
            frag = new ConfiguracionFragment();

        } else if (id == R.id.nav_mediciones_admin) {
            startActivity(new Intent(this, AdminMedicionesActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;

        } else if (id == R.id.nav_mediciones) {
            startActivity(new Intent(this, MedicionesActivity.class));
            drawerLayout.closeDrawer(GravityCompat.START);
            return true;
        }

        if (frag != null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, frag)
                    .addToBackStack(null)
                    .commit();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
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
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
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