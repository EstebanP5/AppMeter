package de.kai_morich.simple_bluetooth_terminal;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * BottomSheet de medición detallada al tocar un nodo en AdminMedicionesActivity.
 */
public class NodeDetailBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_NODE        = "node_json";
    private static final String ARG_ID_CUSTOMER = "idCustomer";

    private NodeTrafficLight node;
    private int idCustomer;

    public static NodeDetailBottomSheet newInstance(NodeTrafficLight node, int idCustomer) {
        NodeDetailBottomSheet f = new NodeDetailBottomSheet();
        Bundle args = new Bundle();
        // Pass fields individually to avoid Parcelable
        args.putInt("idNode",      node.getIdNode() != null ? node.getIdNode() : -1);
        args.putString("name",     node.getName());
        args.putString("typeName", node.getTypeName());
        args.putInt("idCircuit",   node.getIdCircuit() != null ? node.getIdCircuit() : -1);
        args.putInt("idLocation",  node.getIdLocation() != null ? node.getIdLocation() : -1);
        args.putString("lastDate", node.getLastDate());
        args.putString("lastHour", node.getLastHour());
        args.putString("status",   node.getStatus());
        args.putInt("type",        node.getType() != null ? node.getType() : -1);
        args.putInt(ARG_ID_CUSTOMER, idCustomer);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            Bundle a = getArguments();
            node = new NodeTrafficLight();
            node.setFields(
                    a.getInt("idNode"), a.getString("name"), a.getString("typeName"),
                    a.getInt("idCircuit"), a.getInt("idLocation"), a.getString("lastDate"),
                    a.getString("lastHour"), a.getString("status"), a.getInt("type")
            );
            idCustomer = a.getInt(ARG_ID_CUSTOMER, -1);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottomsheet_node_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView tvTitle     = view.findViewById(R.id.tvTitle);
        TextView tvNodeType  = view.findViewById(R.id.tvNodeType);
        TextView tvLastConn  = view.findViewById(R.id.tvLastConnection);
        LinearLayout content = view.findViewById(R.id.contentLayout);

        if (node != null) {
            tvTitle.setText(node.getName() != null ? node.getName() : "—");
            tvNodeType.setText(node.getTypeName() != null ? node.getTypeName() : "—");
            tvLastConn.setText("Última conexión: "
                    + nvl(node.getLastDate()) + " " + nvl(node.getLastHour()));

            // Color del título según NodeType
            int type = node.getType() != null ? node.getType() : -1;
            int titleColor;
            if (type == 2)      titleColor = Color.parseColor("#FFA726"); // Solar/PV → naranja
            else if (type == 3) titleColor = Color.parseColor("#4CAF50"); // BESS → verde
            else if (type == 7) titleColor = Color.parseColor("#42A5F5"); // LOAD → azul
            else                titleColor = Color.WHITE;
            tvTitle.setTextColor(titleColor);

            // Cargar datos detallados
            if (node.getIdCircuit() != null && node.getIdCircuit() > 0) {
                loadDeviceCurrent(node.getIdCircuit(), content);
            } else {
                addRow(content, "Sin circuito asociado", "");
            }
        }
    }

    private void loadDeviceCurrent(int idCircuit, LinearLayout content) {
        ApiClient.getApiService()
                .getCustomerDevicesCurrentDetailed(idCircuit, idCustomer, 0)
                .enqueue(new Callback<List<DeviceCurrent>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<DeviceCurrent>> call,
                                           @NonNull Response<List<DeviceCurrent>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null
                                && !response.body().isEmpty()) {
                            bindDeviceCurrent(response.body().get(0), content);
                        } else {
                            addRow(content, "Sin medición disponible", "");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<DeviceCurrent>> call,
                                          @NonNull Throwable t) {
                        if (isAdded())
                            Toast.makeText(requireContext(),
                                    "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void bindDeviceCurrent(DeviceCurrent d, LinearLayout c) {
        c.removeAllViews();

        // Indicador de tiempo
        addRow(c, "Serie",         d.getSerie());
        addRow(c, "Actualización", d.getCurrentDate() + " " + d.getCurrentHour());

        addSeparator(c);
        addRow(c, "Consumo",    fmt(d.getConW() / 1000.0) + " kW");
        if (d.getGenW() > 0)
            addRow(c, "Generación", fmt(d.getGenW() / 1000.0) + " kW");
        addRow(c, "Consumo hoy",    fmt(d.getConWhDay() / 1000.0) + " kWh");
        if (d.getGenWhDay() > 0)
            addRow(c, "Generación hoy", fmt(d.getGenWhDay() / 1000.0) + " kWh");

        addSeparator(c);
        addRow(c, "Voltajes",   fmt(d.getV1()) + " / " + fmt(d.getV2()) + " / " + fmt(d.getV3()) + " V");
        addRow(c, "Corrientes", fmt(d.getI1()) + " / " + fmt(d.getI2()) + " / " + fmt(d.getI3()) + " A");
        addRow(c, "F. Potencia",fmt(d.getPf1()) + " / " + fmt(d.getPf2()) + " / " + fmt(d.getPf3()));
        addRow(c, "Frecuencia", fmt(d.getHz1()) + " / " + fmt(d.getHz2()) + " / " + fmt(d.getHz3()) + " Hz");
    }

    private void addRow(LinearLayout container, String label, String value) {
        if (!isAdded()) return;
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_detail_row, container, false);
        ((TextView) row.findViewById(R.id.tvLabel)).setText(label);
        ((TextView) row.findViewById(R.id.tvValue)).setText(value);
        container.addView(row);
    }

    private void addSeparator(LinearLayout container) {
        if (!isAdded()) return;
        View sep = new View(requireContext());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(0, 12, 0, 12);
        sep.setLayoutParams(lp);
        sep.setBackgroundColor(Color.parseColor("#2A3645"));
        container.addView(sep);
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
