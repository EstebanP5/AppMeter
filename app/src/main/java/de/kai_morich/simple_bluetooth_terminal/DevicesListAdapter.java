// app/src/main/java/de/kai_morich/simple_bluetooth_terminal/DevicesListAdapter.java
package de.kai_morich.simple_bluetooth_terminal;

import android.app.AlertDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class DevicesListAdapter
        extends RecyclerView.Adapter<DevicesListAdapter.DeviceViewHolder> {

    private List<DeviceItem> devices;
    private Context context;

    public DevicesListAdapter(List<DeviceItem> devices) {
        this.devices = devices;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent, int viewType) {
        context = parent.getContext();
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(
            @NonNull DeviceViewHolder holder, int position) {
        DeviceItem d = devices.get(position);
        holder.tvName.setText(d.getName());
        holder.tvNodo.setText(d.getNodo());
        holder.tvUltimaConexion.setText(d.getUltimaConexion());
        holder.tvPotencia.setText(d.getPotencia());

        // Larga pulsación para eliminar o editar
        holder.itemView.setOnLongClickListener(v -> {
            new AlertDialog.Builder(context)
                    .setTitle(R.string.dialogo_eliminar_titulo)
                    .setMessage("¿Eliminar dispositivo \"" + d.getName() + "\"?")
                    .setPositiveButton(R.string.dialogo_si, (dialog, which) -> {
                        devices.remove(position);
                        notifyItemRemoved(position);
                        Toast.makeText(context, "Dispositivo eliminado", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton(R.string.dialogo_no, (dialog, which) -> dialog.dismiss())
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvNodo, tvUltimaConexion, tvPotencia;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvItemDevName);
            tvNodo = itemView.findViewById(R.id.tvItemDevNodo);
            tvUltimaConexion = itemView.findViewById(R.id.tvItemDevUltConexion);
            tvPotencia = itemView.findViewById(R.id.tvItemDevPotencia);
        }
    }
}
