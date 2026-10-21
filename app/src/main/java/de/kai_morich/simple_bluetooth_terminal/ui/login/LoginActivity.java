package de.kai_morich.simple_bluetooth_terminal.ui.login;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.JwtUtils;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.LoginResponse;
import de.kai_morich.simple_bluetooth_terminal.ui.admin.MainActivity;
import de.kai_morich.simple_bluetooth_terminal.ui.cliente.ClienteActivity;
import de.kai_morich.simple_bluetooth_terminal.ui.medidor.FasoresActivity;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";
    private static final int REQUEST_PERMISSIONS = 100;

    private EditText etEmail, etPassword;
    private View btnLogin, cvErgometer;
    private TextView tvLoginLabel, tvError, btnTogglePassword;
    private ProgressBar progressBar;
    private LoginViewModel loginViewModel;

    // =========================================================================
    // ===== PERMISOS ==========================================================
    // =========================================================================

    private String[] getRequiredPermissions() {
        List<String> perms = new ArrayList<>();

        // Ubicación
        perms.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
        perms.add(android.Manifest.permission.ACCESS_COARSE_LOCATION);

        // Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(android.Manifest.permission.BLUETOOTH_SCAN);
            perms.add(android.Manifest.permission.BLUETOOTH_CONNECT);
        }

        // Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(android.Manifest.permission.NEARBY_WIFI_DEVICES);
            perms.add(android.Manifest.permission.POST_NOTIFICATIONS); // ✅ NUEVO
        }

        return perms.toArray(new String[0]);
    }

    private boolean hasAllPermissions() {
        for (String perm : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, perm)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, getRequiredPermissions(), REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode != REQUEST_PERMISSIONS) return;

    }

    // =========================================================================
    // ===== CICLO DE VIDA =====================================================
    // =========================================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Sin barra azul: el contenido se dibuja bajo las barras (como exige Android 15+) y el fondo claro de la
        // ventana cubre la zona de la barra de estado
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().getDecorView().setBackgroundColor(getResources().getColor(R.color.am_bg));
        View content = findViewById(android.R.id.content);
        androidx.core.view.WindowCompat.getInsetsController(getWindow(), content).setAppearanceLightStatusBars(true);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            androidx.core.graphics.Insets bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, bars.bottom);
            return insets;
        });

        // La versión sale de build.gradle (versionName), no se toca a mano
        ((android.widget.TextView) findViewById(R.id.tvVersion)).setText(
                "METER APP V" + de.kai_morich.simple_bluetooth_terminal.BuildConfig.VERSION_NAME);

        ApiClient.init(getApplicationContext());

        initializeViews();
        setupObservers();
        setupClickListeners();

        // Pedir permisos al iniciar
        if (!hasAllPermissions()) {
            requestPermissions();
        }
    }

    // =========================================================================
    // ===== INICIALIZACIÓN ====================================================
    // =========================================================================

    private void initializeViews() {
        etEmail    = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        cvErgometer = findViewById(R.id.cvErgometer);
        progressBar = findViewById(R.id.progressBar);
        tvLoginLabel = findViewById(R.id.tvLoginLabel);
        tvError = findViewById(R.id.tvError);
        btnTogglePassword = findViewById(R.id.btnTogglePassword);

        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);
    }

    private void setupObservers() {
        loginViewModel.getLoginResult().observe(this, new Observer<LoginResponse>() {
            @Override
            public void onChanged(LoginResponse loginResponse) {
                if (loginResponse != null) {
                    String token = loginResponse.getToken();
                    String roll  = loginResponse.getRoll();
                    Log.d(TAG, "LoginResponse: Token=" + token + ", Roll=" + roll);

                    if (!TextUtils.isEmpty(token)) {
                        new TokenManager(LoginActivity.this).saveToken(token);
                        // Admin/instalador → vista Admin; cualquier otro rol (Customer, SubUser) → vista Cliente
                        boolean admin = JwtUtils.isInstallerAdmin(token);
                        startActivity(new Intent(LoginActivity.this, admin ? MainActivity.class : ClienteActivity.class));
                        finish();
                    } else {
                        Toast.makeText(LoginActivity.this, "Token vacío en la respuesta", Toast.LENGTH_SHORT).show();
                        Log.e(TAG, "Token nulo o vacío.");
                    }
                } else {
                    Log.e(TAG, "loginResponse es null en el observer.");
                }
            }
        });

        loginViewModel.getError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String errorMsg) {
                if (errorMsg != null) {
                    Log.e(TAG, "Error del ViewModel: " + errorMsg);
                    showError(errorMsg);
                }
            }
        });

        loginViewModel.getLoading().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isLoading) {
                boolean loading = isLoading != null && isLoading;
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
                tvLoginLabel.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
                btnLogin.setEnabled(!loading);
            }
        });
    }

    private void setupClickListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());

        btnTogglePassword.setOnClickListener(v -> {
            boolean hidden = etPassword.getTransformationMethod() instanceof PasswordTransformationMethod;
            etPassword.setTransformationMethod(hidden
                    ? HideReturnsTransformationMethod.getInstance() : PasswordTransformationMethod.getInstance());
            etPassword.setSelection(etPassword.getText().length());
            btnTogglePassword.setText(hidden ? "Ocultar" : "Mostrar");
        });

        cvErgometer.setOnClickListener(v ->
                startActivity(new Intent(LoginActivity.this, FasoresActivity.class)));
    }



    private void attemptLogin() {
        String email    = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Ingresa tu usuario y contraseña");
            return;
        }
        tvError.setVisibility(View.GONE);

        ApiClient.clearToken(); // Limpiar el token antes de iniciar el login

        loginViewModel.clearState();
        loginViewModel.loginUser(email, password);
    }

    /** Mensaje bajo el botón, en rojo. */
    private void showError(String raw) {
        String msg = raw;
        if (raw.startsWith("Error de conexión")) msg = "Sin conexión";
        else if (raw.equals("Credenciales inválidas") || raw.equals("No autorizado")) msg = "Usuario o contraseña incorrectos";
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}
