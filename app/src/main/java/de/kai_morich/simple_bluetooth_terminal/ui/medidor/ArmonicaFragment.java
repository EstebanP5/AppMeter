// app/src/main/java/de/kai_morich/simple_bluetooth_terminal/ui/medidor/ArmonicaFragment.java
package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import de.kai_morich.simple_bluetooth_terminal.R;

public class ArmonicaFragment extends Fragment {

    public ArmonicaFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_armonica, container, false);
    }
}
