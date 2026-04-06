package de.kai_morich.simple_bluetooth_terminal;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;

public class DeviceCurrentCardAdapter extends RecyclerView.Adapter<DeviceCurrentCardAdapter.ViewHolder> {

    public interface OnHistoricoClickListener {
        void onHistoricoClick(DeviceCurrent device);
    }

    public interface OnNodeClickListener {
        void onNodeClick(DeviceCurrent device);
    }

    private final Context context;
    private final OnHistoricoClickListener historicoListener;
    private final OnNodeClickListener nodeClickListener;
    private List<DeviceCurrent> items = new ArrayList<>();
    private Map<Integer, String> statusMap = new HashMap<>(); // idMeasure -> status
    private Map<String, String> serieStatusMap = new HashMap<>(); // serie -> status

    public DeviceCurrentCardAdapter(Context context,
                                     OnHistoricoClickListener historicoListener,
                                     OnNodeClickListener nodeClickListener) {
        this.context = context;
        this.historicoListener = historicoListener;
        this.nodeClickListener = nodeClickListener;
    }

    public void setData(List<DeviceCurrent> data) {
        items = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setStatusMap(Map<Integer, String> map) {
        statusMap = map != null ? map : new HashMap<>();
        notifyDataSetChanged();
    }

    public void setSerieStatusMap(Map<String, String> map) {
        serieStatusMap = map != null ? map : new HashMap<>();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_device_current_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        DeviceCurrent d = items.get(position);

        // Icon by type
        vh.tvTypeIcon.setText(getTypeIcon(d.getType()));

        // Name + Serie
        vh.tvNodeName.setText(d.getName());
        vh.tvSerie.setText("S/N: " + d.getSerie());

        // Status badge
        String status = statusMap.get(d.getIdMeasure());
        if (status == null && d.getSerie() != null) {
            status = serieStatusMap.get(d.getSerie());
        }
        int statusColor;
        if ("Green".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(context, R.color.green_status);
        } else if ("Yellow".equalsIgnoreCase(status)) {
            statusColor = ContextCompat.getColor(context, R.color.yellow_status);
        } else {
            statusColor = ContextCompat.getColor(context, R.color.red_status);
        }
        if (vh.viewStatusBadge.getBackground() instanceof GradientDrawable) {
            ((GradientDrawable) vh.viewStatusBadge.getBackground()).setColor(statusColor);
        } else {
            vh.viewStatusBadge.setBackgroundColor(statusColor);
        }

        // Last update
        vh.tvLastUpdate.setText(nvl(d.getCurrentDate()) + "\n" + nvl(d.getCurrentHour()));

        // Power consumption
        vh.tvConW.setText(formatPower(d.getConW()));

        // Power generation
        if (d.getGenW() > 0) {
            vh.layoutGenW.setVisibility(View.VISIBLE);
            vh.tvGenW.setText(formatPower(d.getGenW()));
        } else {
            vh.layoutGenW.setVisibility(View.GONE);
        }

        // Energy day
        vh.tvEnergyDay.setText(formatEnergy(d.getConWhDay()));

        // Voltage
        vh.tvVoltage.setText(String.format(Locale.US, "V1=%.1fV  V2=%.1fV  V3=%.1fV",
                d.getV1(), d.getV2(), d.getV3()));

        // Current
        vh.tvCurrent.setText(String.format(Locale.US, "I1=%.2fA  I2=%.2fA  I3=%.2fA",
                d.getI1(), d.getI2(), d.getI3()));

        // Power factor
        vh.tvPf.setText(String.format(Locale.US, "Pf1=%.2f  Pf2=%.2f  Pf3=%.2f",
                d.getPf1(), d.getPf2(), d.getPf3()));

        // Historico button
        vh.btnHistorico.setOnClickListener(v -> historicoListener.onHistoricoClick(d));

        // Item click
        vh.itemView.setOnClickListener(v -> {
            if (nodeClickListener != null) nodeClickListener.onNodeClick(d);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    // ---- Helpers ----

    private String getTypeIcon(Integer type) {
        if (type == null) return "📊";
        switch (type) {
            case 0: return "⚡";  // CFE
            case 1: return "⚡";  // Gen CFE
            case 2: return "☀";  // PV Solar
            case 3: return "🔋";  // BESS
            case 4: return "🏭";  // Generador
            case 7: return "🏗";  // Load
            default: return "📊";
        }
    }

    static String formatPower(double w) {
        if (w == 0) return "—";
        if (Math.abs(w) >= 1000) return String.format(Locale.US, "%.1f kW", w / 1000.0);
        return String.format(Locale.US, "%.1f W", w);
    }

    static String formatEnergy(double wh) {
        if (wh == 0) return "—";
        if (Math.abs(wh) >= 1000) return String.format(Locale.US, "%.1f kWh", wh / 1000.0);
        return String.format(Locale.US, "%.1f Wh", wh);
    }

    private static String nvl(String s) { return s != null ? s : "—"; }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTypeIcon, tvNodeName, tvSerie, tvLastUpdate;
        View viewStatusBadge;
        TextView tvConW, tvGenW, tvEnergyDay, tvVoltage, tvCurrent, tvPf;
        LinearLayout layoutGenW;
        MaterialButton btnHistorico;

        ViewHolder(@NonNull View v) {
            super(v);
            tvTypeIcon     = v.findViewById(R.id.tvTypeIcon);
            tvNodeName     = v.findViewById(R.id.tvNodeName);
            tvSerie        = v.findViewById(R.id.tvSerie);
            tvLastUpdate   = v.findViewById(R.id.tvLastUpdate);
            viewStatusBadge= v.findViewById(R.id.viewStatusBadge);
            tvConW         = v.findViewById(R.id.tvConW);
            tvGenW         = v.findViewById(R.id.tvGenW);
            tvEnergyDay    = v.findViewById(R.id.tvEnergyDay);
            tvVoltage      = v.findViewById(R.id.tvVoltage);
            tvCurrent      = v.findViewById(R.id.tvCurrent);
            tvPf           = v.findViewById(R.id.tvPf);
            layoutGenW     = v.findViewById(R.id.layoutGenW);
            btnHistorico   = v.findViewById(R.id.btnHistorico);
        }
    }
}
