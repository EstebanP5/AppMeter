package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.BuildConfig;
import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Cuenta: perfil, localidades asignadas, cambiar contraseña y cerrar sesión. */
public class ClienteCuentaFragment extends Fragment {

    private ClienteViewModel vm;
    private TextView tvInitials, tvName, tvContact;
    private LinearLayout listLocations;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        View v = inflater.inflate(R.layout.fragment_cliente_cuenta, container, false);
        vm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        tvInitials = v.findViewById(R.id.tvInitials);
        tvName = v.findViewById(R.id.tvName);
        tvContact = v.findViewById(R.id.tvContact);
        listLocations = v.findViewById(R.id.listLocations);
        ((TextView) v.findViewById(R.id.tvVersion)).setText("METER APP V" + BuildConfig.VERSION_NAME);

        v.findViewById(R.id.rowPassword).setOnClickListener(x -> changePassword());
        v.findViewById(R.id.btnLogout).setOnClickListener(x -> ((ClienteActivity) requireActivity()).logout());

        vm.customerName.observe(getViewLifecycleOwner(), n -> {
            tvName.setText(n != null ? n : "");
            tvInitials.setText(initials(n));
        });
        vm.locations.observe(getViewLifecycleOwner(), this::showLocations);
        if (vm.customerId != null) {
            vm.api().getCustomerInformation(vm.customerId).enqueue(new Callback<CustomerViewModel>() {
                @Override public void onResponse(Call<CustomerViewModel> c, Response<CustomerViewModel> r) {
                    if (!isAdded() || !r.isSuccessful() || r.body() == null) return;
                    String email = r.body().getEmail(), tel = r.body().getTelephone();
                    tvContact.setText((email != null ? email : "") + (email != null && tel != null && !tel.isEmpty() ? " · " : "") + (tel != null ? tel : ""));
                }
                @Override public void onFailure(Call<CustomerViewModel> c, Throwable t) { /* sin contacto, no es crítico */ }
            });
        }
        return v;
    }

    private void showLocations(List<LocationsViewModel> locs) {
        if (getView() == null) return;
        listLocations.removeAllViews();
        if (locs == null || locs.isEmpty()) {
            TextView t = new TextView(requireContext());
            t.setPadding(dp(14), dp(12), dp(14), dp(12));
            t.setText("Sin localidades asignadas");
            t.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_t777));
            listLocations.addView(t);
            return;
        }
        for (int i = 0; i < locs.size(); i++) {
            if (i > 0) {
                View line = new View(requireContext());
                line.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.am_line));
                listLocations.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
            }
            TextView t = new TextView(requireContext());
            t.setPadding(dp(14), dp(12), dp(14), dp(12));
            t.setText(locs.get(i).getName());
            t.setTextColor(ContextCompat.getColor(requireContext(), R.color.am_ink));
            t.setTextSize(14);
            listLocations.addView(t);
        }
    }

    private void changePassword() {
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText old = field("Contraseña actual");
        final EditText nw = field("Nueva contraseña (mínimo 6 caracteres)");
        final EditText confirm = field("Confirma la nueva contraseña");
        box.addView(old);
        box.addView(nw);
        box.addView(confirm);
        new AlertDialog.Builder(requireContext())
                .setTitle("Cambiar contraseña")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Guardar", (d, w) -> {
                    String o = old.getText().toString(), n = nw.getText().toString(), c = confirm.getText().toString();
                    if (n.length() < 6) { toast("La nueva contraseña debe tener al menos 6 caracteres"); return; }
                    if (!n.equals(c)) { toast("Las contraseñas no coinciden"); return; }
                    Map<String, Object> body = new HashMap<>();
                    body.put("OldPassword", o);
                    body.put("NewPassword", n);
                    body.put("ConfirmNewPassword", c);
                    vm.api().changePassword(body).enqueue(new Callback<Void>() {
                        @Override public void onResponse(Call<Void> call, Response<Void> r) {
                            toast(r.isSuccessful() ? "Contraseña actualizada" : "No se pudo cambiar la contraseña. Revisa la contraseña actual.");
                        }
                        @Override public void onFailure(Call<Void> call, Throwable t) { toast("Sin conexión"); }
                    });
                })
                .show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(requireContext());
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private void toast(String s) {
        if (isAdded()) Toast.makeText(requireContext(), s, Toast.LENGTH_LONG).show();
    }

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    private static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "";
        String[] p = name.trim().split("\\s+");
        String s = p[0].substring(0, 1) + (p.length > 1 ? p[1].substring(0, 1) : (p[0].length() > 1 ? p[0].substring(1, 2) : ""));
        return s.toUpperCase();
    }
}
