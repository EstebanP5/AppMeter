// File: ConfiguracionFragment.java
package de.kai_morich.simple_bluetooth_terminal;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ConfiguracionFragment extends Fragment {

    private Button btnAgregar, btnModificar;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_configuracion, container, false);
        btnAgregar  = v.findViewById(R.id.btnModoAgregar);
        btnModificar= v.findViewById(R.id.btnModoModificar);

        btnAgregar.setOnClickListener(x ->
                showChildFragment(new AgregarClienteFragment())
        );
        btnModificar.setOnClickListener(x ->
                showChildFragment(new ModificarClienteFragment())
        );

        // Modo por defecto
        showChildFragment(new AgregarClienteFragment());
        return v;
    }

    private void showChildFragment(Fragment child) {
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.containerModo, child)
                .commit();
    }
}
