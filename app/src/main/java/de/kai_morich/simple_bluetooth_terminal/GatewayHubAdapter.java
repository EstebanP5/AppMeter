package de.kai_morich.simple_bluetooth_terminal;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel;

public class GatewayHubAdapter extends RecyclerView.Adapter<GatewayHubAdapter.ViewHolder> {

    public interface HubActionListener {
        void onExpandHub(DevicesViewModel hub, LinearLayout container);
        void onDeleteHub(DevicesViewModel hub);
        void onAssignNode(DevicesViewModel hub);
    }

    private final Context context;
    private final HubActionListener listener;
    private List<DevicesViewModel> hubs = new ArrayList<>();
    // Stores expanded state per position
    private final java.util.Set<Integer> expandedIds = new java.util.HashSet<>();

    public GatewayHubAdapter(Context context, HubActionListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    public void setData(List<DevicesViewModel> data) {
        hubs = data != null ? data : new ArrayList<>();
        expandedIds.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_gateway_hub, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        DevicesViewModel hub = hubs.get(position);

        vh.tvHubName.setText(hub.getName() != null ? hub.getName() : "HUB");
        vh.tvHubSerie.setText(hub.getSerie() != null ? hub.getSerie() : "—");

        // Badge de serial no registrado
        boolean noSerial = hub.getSerie() == null || hub.getSerie().isEmpty();
        vh.tvNoSerialBadge.setVisibility(noSerial ? View.VISIBLE : View.GONE);

        // Estado expandido/colapsado
        boolean expanded = expandedIds.contains(position);
        vh.nodesContainer.setVisibility(expanded ? View.VISIBLE : View.GONE);
        vh.ivExpand.setRotation(expanded ? 180f : 0f);

        // Click en header: expandir / colapsar
        vh.layoutHeader.setOnClickListener(v -> {
            int pos = vh.getAdapterPosition();
            if (pos == RecyclerView.NO_ID) return;
            if (expandedIds.contains(pos)) {
                expandedIds.remove(pos);
                vh.nodesContainer.setVisibility(View.GONE);
                vh.ivExpand.setRotation(0f);
            } else {
                expandedIds.add(pos);
                vh.nodesContainer.setVisibility(View.VISIBLE);
                vh.ivExpand.setRotation(180f);
                vh.nodesContainer.removeAllViews();
                listener.onExpandHub(hub, vh.nodesContainer);
            }
        });

        // Botones de acción
        vh.btnDeleteHub.setOnClickListener(v -> listener.onDeleteHub(hub));
        vh.btnAssignNode.setOnClickListener(v -> listener.onAssignNode(hub));
    }

    @Override
    public int getItemCount() { return hubs.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvHubName, tvHubSerie, tvNoSerialBadge;
        View ivExpand, btnDeleteHub, btnAssignNode;
        LinearLayout layoutHeader, nodesContainer;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvHubName       = itemView.findViewById(R.id.tvHubName);
            tvHubSerie      = itemView.findViewById(R.id.tvHubSerie);
            tvNoSerialBadge = itemView.findViewById(R.id.tvNoSerialBadge);
            ivExpand        = itemView.findViewById(R.id.ivExpand);
            layoutHeader    = itemView.findViewById(R.id.layoutHeader);
            nodesContainer  = itemView.findViewById(R.id.nodesContainer);
            btnDeleteHub    = itemView.findViewById(R.id.btnDeleteHub);
            btnAssignNode   = itemView.findViewById(R.id.btnAssignNode);
        }
    }
}
