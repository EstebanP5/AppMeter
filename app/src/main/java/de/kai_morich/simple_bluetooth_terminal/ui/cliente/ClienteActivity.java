package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.TokenManager;
import de.kai_morich.simple_bluetooth_terminal.ui.login.LoginActivity;

/** Vista Cliente (K1–K4 + Cuenta): barra inferior Inicio · Consumo · Medidores · Cuenta. */
public class ClienteActivity extends AppCompatActivity {

    private static final String KEY_TAB = "tab";

    private View[] tabs;
    private int current = -1;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_App_NaranjaAzul);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cliente);
        ApiClient.init(getApplicationContext());

        ClienteViewModel vm = new ViewModelProvider(this).get(ClienteViewModel.class);
        vm.start(this);

        tabs = new View[]{findViewById(R.id.tab_inicio), findViewById(R.id.tab_consumo),
                findViewById(R.id.tab_medidores), findViewById(R.id.tab_cuenta)};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> selectTab(idx));
        }

        // Edge-to-edge: el título queda bajo la barra de estado y la barra inferior sobre la de gestos
        View root = findViewById(R.id.cliente_root);
        View bottom = findViewById(R.id.cliente_bottom_nav);
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(true);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // el contenido se dibuja bajo las barras; los márgenes salen de los insets
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            bottom.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        if (savedInstanceState == null) selectTab(0);
        else mark(savedInstanceState.getInt(KEY_TAB, 0));
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, current);
    }

    /** 0 Inicio · 1 Consumo · 2 Medidores · 3 Cuenta. Vuelve a la raíz de la pestaña. */
    public void selectTab(int idx) {
        FragmentManager fm = getSupportFragmentManager();
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        if (idx == current && fm.findFragmentById(R.id.cliente_container) != null) return;
        Fragment f;
        switch (idx) {
            case 1: f = new ClienteConsumoFragment(); break;
            case 2: f = new ClienteMedidoresFragment(); break;
            case 3: f = new ClienteCuentaFragment(); break;
            default: f = new ClienteInicioFragment();
        }
        fm.beginTransaction().setCustomAnimations(R.anim.am_fade_in, R.anim.am_fade_out)
                .replace(R.id.cliente_container, f).commit();
        mark(idx);
    }

    /** K3 → K4 (y similares): apila encima conservando la barra inferior. */
    public void push(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.am_slide_in, R.anim.am_slide_out, R.anim.am_pop_in, R.anim.am_pop_out)
                .replace(R.id.cliente_container, f)
                .addToBackStack(null)
                .commit();
    }

    private void mark(int idx) {
        current = idx;
        for (int i = 0; i < tabs.length; i++) {
            boolean on = i == idx;
            tabs[i].setSelected(on);
            ((TextView) ((ViewGroup) tabs[i]).getChildAt(1))
                    .setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        }
    }

    public void logout() {
        getSharedPreferences("user_prefs", MODE_PRIVATE).edit().clear().apply();
        new TokenManager(this).clearToken();
        ApiClient.clearToken();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().getBackStackEntryCount() == 0 && current != 0) selectTab(0);
        else super.onBackPressed();
    }
}
