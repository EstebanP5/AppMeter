package de.kai_morich.simple_bluetooth_terminal.legacy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;

public class NodeTrafficLightAdapter extends RecyclerView.Adapter<NodeTrafficLightAdapter.ViewHolder> {

    public interface OnNodeClickListener {
        void onNodeClick(NodeTrafficLight node);
    }

    private final Context context;
    private final OnNodeClickListener listener;
    private List<NodeTrafficLight> items = new ArrayList<>();

    public NodeTrafficLightAdapter(Context context, OnNodeClickListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    public void setData(List<NodeTrafficLight> data) {
        items = data != null ? data : new ArrayList<>();
        notifyDataSetChanged();
    }

    public int countByStatus(String status) {
        int c = 0;
        for (NodeTrafficLight n : items) if (status.equalsIgnoreCase(n.getStatus())) c++;
        return c;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_node_traffic_light, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder vh, int position) {
        NodeTrafficLight node = items.get(position);

        vh.tvName.setText(node.getName() != null ? node.getName() : "—");
        vh.tvType.setText(node.getTypeName() != null ? node.getTypeName() : "—");

        String ultima = "Última: " + nvl(node.getLastDate()) + " " + nvl(node.getLastHour());
        vh.tvLastConnection.setText(ultima.trim());

        // Semáforo
        int color;
        if (node.isGreen())       color = ContextCompat.getColor(context, R.color.green_status);
        else if (node.isYellow()) color = ContextCompat.getColor(context, R.color.yellow_status);
        else                      color = ContextCompat.getColor(context, R.color.red_status);
        vh.viewIndicator.setBackgroundColor(color);

        vh.itemView.setOnClickListener(v -> listener.onNodeClick(node));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View viewIndicator;
        TextView tvName, tvType, tvLastConnection;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewIndicator    = itemView.findViewById(R.id.viewIndicator);
            tvName           = itemView.findViewById(R.id.tvName);
            tvType           = itemView.findViewById(R.id.tvType);
            tvLastConnection = itemView.findViewById(R.id.tvLastConnection);
        }
    }

    private static String nvl(String s) { return s != null ? s : ""; }
}
