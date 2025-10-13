package de.kai_morich.simple_bluetooth_terminal.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.WiFiSetupActivity;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.models.LoginResponse;
import de.kai_morich.simple_bluetooth_terminal.viewmodels.LoginViewModel;
import de.kai_morich.simple_bluetooth_terminal.MainActivity;
import de.kai_morich.simple_bluetooth_terminal.TcpClientActivity;
import de.kai_morich.simple_bluetooth_terminal.FasoresActivity;

public class LoginActivity extends AppCompatActivity {

    private static final String TAG = "LoginActivity";

    private EditText etEmail, etPassword;
    private Button btnLogin;
    private CardView cvErgometer; // ✅ NUEVO: CardView de la imagen
    private ProgressBar progressBar;
    private LoginViewModel loginViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // Inicializar ApiClient (incluye TokenManager)
        ApiClient.init(getApplicationContext());

        initializeViews();
        setupObservers();
        setupClickListeners();
    }

    private void initializeViews() {
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        cvErgometer = findViewById(R.id.cvErgometer); // ✅ NUEVO
        progressBar = findViewById(R.id.progressBar);

        loginViewModel = new ViewModelProvider(this).get(LoginViewModel.class);
    }

    private void setupObservers() {
        // Observer de loginResult
        loginViewModel.getLoginResult().observe(this, new Observer<LoginResponse>() {
            @Override
            public void onChanged(LoginResponse loginResponse) {
                if (loginResponse != null) {
                    String token = loginResponse.getToken();
                    String roll = loginResponse.getRoll();
                    Log.d(TAG, "LoginResponse recibido: Token=" + token + ", Roll=" + roll);

                    if (!TextUtils.isEmpty(token)) {
                        // Guardar token en SharedPreferences
                        new TokenManager(LoginActivity.this).saveToken(token);

                        Toast.makeText(LoginActivity.this, "Login exitoso", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(LoginActivity.this, MainActivity.class));
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

        // Observer de error
        loginViewModel.getError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String errorMsg) {
                if (errorMsg != null) {
                    Log.e(TAG, "Error recibido del ViewModel: " + errorMsg);
                    Toast.makeText(LoginActivity.this, errorMsg, Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Observer de loading
        loginViewModel.getLoading().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean isLoading) {
                progressBar.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE);
                btnLogin.setEnabled(isLoading == null || !isLoading);
            }
        });
    }

    private void setupClickListeners() {
        // ✅ Listener del botón Login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        // ✅ NUEVO: Listener de la imagen ErgoMeter
        cvErgometer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Ir directamente a FasoresActivity
                Intent intent = new Intent(LoginActivity.this, FasoresActivity.class);
                startActivity(intent);
            }
        });
    }

    private void attemptLogin() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "Ingresa correo y contraseña", Toast.LENGTH_SHORT).show();
            return;
        }

        // Limpiar estados previos
        loginViewModel.clearState();
        // Iniciar llamada a la API
        loginViewModel.loginUser(email, password);
    }
}