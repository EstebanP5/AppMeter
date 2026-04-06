package de.kai_morich.simple_bluetooth_terminal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.models.RegisteredSerial;

public class MisDispositivosAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_METER = 0;
    private static final int TYPE_HUB   = 1;

    public interface ActionListener {
        void onEditLoad(RegisteredSerial serial);
        void onDelete(RegisteredSerial serial);
        void onHubExpand(RegisteredSerial hub, LinearLayout container);
    }

    private final Context context;
    private final ActionListener listener;
    private List<RegisteredSerial> items = new ArrayList<>();

    public MisDispositivosAdapter(Context context, ActionListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    public void setData(List<RegisteredSerial> data) {
        items = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).isHub() ? TYPE_HUB : TYPE_METER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        if (viewType == TYPE_HUB) {
            View v = inflater.inflate(R.layout.item_registered_hub, parent, false);
            return new HubViewHolder(v);
        } else {
            View v = inflater.inflate(R.layout.item_registered_meter, parent, false);
            return new MeterViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RegisteredSerial s = items.get(position);
        if (holder instanceof MeterViewHolder) {
            bindMeter((MeterViewHolder) holder, s);
        } else {
            bindHub((HubViewHolder) holder, s);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // -----------------------------------------------
    // METER ViewHolder
    // -----------------------------------------------

    private void bindMeter(MeterViewHolder vh, RegisteredSerial s) {
        vh.tvSerie.setText(s.getSerie() != null ? s.getSerie() : "—");
        vh.tvNodeType.setText(s.getNodeTypeName());

        // Chip de Load
        vh.chipLoad.setText("Load " + s.getLoad());

        // Badge vinculado / libre
        boolean linked = s.isLinkedStatus();
        vh.chipStatus.setText(linked ? "VINCULADO" : "LIBRE");
        vh.chipStatus.setChipBackgroundColorResource(
                linked ? R.color.web_success : android.R.color.darker_gray);

        // Detalles si vinculado
        if (linked) {
            vh.layoutDetails.setVisibility(View.VISIBLE);
            String name = s.getNodeName() != null ? s.getNodeName() : "—";
            vh.tvNodeName.setText(name);
            String asignado = s.getDateAssigned() + " " + nvl(s.getHourAssigned());
            String vinculado = s.getDateLinked()  + " " + nvl(s.getHourLinked());
            vh.tvAsignado.setText("Asignado: " + asignado.trim());
            vh.tvVinculado.setText("Vinculado: " + vinculado.trim());
        } else {
            vh.layoutDetails.setVisibility(View.GONE);
        }

        // Botón editar load
        vh.btnEditLoad.setOnClickListener(v -> listener.onEditLoad(s));

        // Botón eliminar (solo si no vinculado)
        vh.btnDelete.setVisibility(!linked ? View.VISIBLE : View.GONE);
        vh.btnDelete.setOnClickListener(v -> listener.onDelete(s));
    }

    // -----------------------------------------------
    // HUB ViewHolder
    // -----------------------------------------------

    private void bindHub(HubViewHolder vh, RegisteredSerial s) {
        vh.tvSerie.setText(s.getSerie() != null ? s.getSerie() : "—");
        vh.tvCount.setText(s.getNodeCount() + " nodos asignados");

        // Toggle expandir
        vh.nodesContainer.setVisibility(View.GONE);
        vh.ivExpand.setRotation(0f);

        vh.layoutHeader.setOnClickListener(v -> {
            boolean expanded = vh.nodesContainer.getVisibility() == View.VISIBLE;
            if (expanded) {
                vh.nodesContainer.setVisibility(View.GONE);
                vh.ivExpand.setRotation(0f);
            } else {
                vh.nodesContainer.setVisibility(View.VISIBLE);
                vh.ivExpand.setRotation(180f);
                listener.onHubExpand(s, vh.nodesContainer);
            }
        });
    }

    // -----------------------------------------------
    // ViewHolders
    // -----------------------------------------------

    static class MeterViewHolder extends RecyclerView.ViewHolder {
        TextView tvSerie, tvNodeType, tvNodeName, tvAsignado, tvVinculado;
        Chip chipLoad, chipStatus;
        View btnEditLoad, btnDelete;
        LinearLayout layoutDetails;

        MeterViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSerie      = itemView.findViewById(R.id.tvSerie);
            tvNodeType   = itemView.findViewById(R.id.tvNodeType);
            chipLoad     = itemView.findViewById(R.id.chipLoad);
            chipStatus   = itemView.findViewById(R.id.chipStatus);
            layoutDetails= itemView.findViewById(R.id.layoutDetails);
            tvNodeName   = itemView.findViewById(R.id.tvNodeName);
            tvAsignado   = itemView.findViewById(R.id.tvAsignado);
            tvVinculado  = itemView.findViewById(R.id.tvVinculado);
            btnEditLoad  = itemView.findViewById(R.id.btnEditLoad);
            btnDelete    = itemView.findViewById(R.id.btnDelete);
        }
    }

    static class HubViewHolder extends RecyclerView.ViewHolder {
        TextView tvSerie, tvCount;
        View ivExpand;
        LinearLayout layoutHeader, nodesContainer;

        HubViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSerie        = itemView.findViewById(R.id.tvSerie);
            tvCount        = itemView.findViewById(R.id.tvCount);
            ivExpand       = itemView.findViewById(R.id.ivExpand);
            layoutHeader   = itemView.findViewById(R.id.layoutHeader);
            nodesContainer = itemView.findViewById(R.id.nodesContainer);
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
