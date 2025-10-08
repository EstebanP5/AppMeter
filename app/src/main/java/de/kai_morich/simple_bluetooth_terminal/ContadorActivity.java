package de.kai_morich.simple_bluetooth_terminal;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class ContadorActivity extends AppCompatActivity {

    private int contador = 0;
    private TextView txtContador;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contador); // Usa el XML que ya tienes

        txtContador = findViewById(R.id.txtContador);
        Button btnIncrement = findViewById(R.id.btnIncrement);
        Button btnDecrement = findViewById(R.id.btnDecrement);

        btnIncrement.setOnClickListener(v -> {
            contador++;
            actualizarTexto();
        });

        btnDecrement.setOnClickListener(v -> {
            contador--;
            actualizarTexto();
        });
    }

    private void actualizarTexto() {
        txtContador.setText("Contador: " + contador);
    }
}
