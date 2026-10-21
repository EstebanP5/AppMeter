package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.SerialLink;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BottomSheet para editar el Load de los nodos vinculados a un serial físico.
 */
public class EditLoadBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_SERIE        = "serie";
    private static final String ARG_FRONTEND_TYPE = "frontendType";
    private static final String ARG_ID_CUSTOMER  = "idCustomer";

    private String serie;
    private int frontendType;
    private int idCustomer;

    private LinearLayout containerLinks;
    private Button btnGuardar;
    private List<SerialLink> links;

    public static EditLoadBottomSheet newInstance(String serie, int frontendType, int idCustomer) {
        EditLoadBottomSheet f = new EditLoadBottomSheet();
        Bundle args = new Bundle();
        args.putString(ARG_SERIE, serie);
        args.putInt(ARG_FRONTEND_TYPE, frontendType);
        args.putInt(ARG_ID_CUSTOMER, idCustomer);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            serie        = getArguments().getString(ARG_SERIE);
            frontendType = getArguments().getInt(ARG_FRONTEND_TYPE, 0);
            idCustomer   = getArguments().getInt(ARG_ID_CUSTOMER, -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_edit_load, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle = view.findViewById(R.id.tvTitle);
        tvTitle.setText("Load — " + serie);
        containerLinks = view.findViewById(R.id.containerLinks);
        btnGuardar     = view.findViewById(R.id.btnGuardar);

        loadLinks();

        btnGuardar.setOnClickListener(v -> saveAllLoads());
    }

    private void loadLinks() {
        ApiClient.getApiService()
                .getSerialLinks(serie, frontendType, idCustomer)
                .enqueue(new Callback<List<SerialLink>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<SerialLink>> call,
                                           @NonNull Response<List<SerialLink>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            links = response.body();
                            buildLinkRows();
                        } else {
                            showError("Sin datos");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<SerialLink>> call, @NonNull Throwable t) {
                        if (isAdded()) showError("Error: " + t.getMessage());
                    }
                });
    }

    private void buildLinkRows() {
        containerLinks.removeAllViews();
        if (links == null || links.isEmpty()) {
            showError("Sin nodos vinculados");
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        String[] loadOpts = {"0", "1", "2", "3"};
        ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, loadOpts);
        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        for (SerialLink link : links) {
            View row = inflater.inflate(R.layout.item_link_row, containerLinks, false);
            TextView tvNode = row.findViewById(R.id.tvNodeName);
            Spinner spLoad  = row.findViewById(R.id.spLoad);

            tvNode.setText(link.getNodeName() + " (" + link.getTypeName() + ")");
            spLoad.setAdapter(new ArrayAdapter<>(requireContext(),
                    android.R.layout.simple_spinner_item, loadOpts));
            spLoad.getAdapter().toString();
            ((ArrayAdapter<String>) spLoad.getAdapter())
                    .setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            int cur = link.getLoad();
            if (cur >= 0 && cur <= 3) spLoad.setSelection(cur);
            spLoad.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    link.setPendingLoad(pos);
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
            containerLinks.addView(row);
        }
    }

    private void saveAllLoads() {
        if (links == null || links.isEmpty()) { dismiss(); return; }
        ApiService api = ApiClient.getApiService();
        int[] pending = {links.size()};

        for (SerialLink link : links) {
            Map<String, Object> body = new HashMap<>();
            body.put("IdNode",     link.getIdNode());
            body.put("Serie",      serie);
            body.put("Load",       link.getPendingLoad());
            body.put("IdCustomer", idCustomer);

            api.linkNodeWithSerialMap(body).enqueue(new Callback<Void>() {
                @Override
                public void onResponse(@NonNull Call<Void> call,
                                       @NonNull Response<Void> response) {
                    pending[0]--;
                    if (!isAdded()) return;
                    if (pending[0] == 0) {
                        Toast.makeText(requireContext(),
                                "Load actualizado", Toast.LENGTH_SHORT).show();
                        dismiss();
                    }
                }

                @Override
                public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                    if (isAdded())
                        Toast.makeText(requireContext(),
                                "Error al guardar: " + t.getMessage(),
                                Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showError(String msg) {
        if (containerLinks != null) {
            containerLinks.removeAllViews();
            TextView tv = new TextView(requireContext());
            tv.setText(msg);
            tv.setTextColor(0xFFFFFFFF);
            containerLinks.addView(tv);
        }
    }
}
