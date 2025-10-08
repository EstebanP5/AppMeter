package de.kai_morich.simple_bluetooth_terminal;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import de.kai_morich.simple_bluetooth_terminal.models.CustomerViewModel;

public class ClientesAdapter extends RecyclerView.Adapter<ClientesAdapter.ClienteViewHolder> {

    private static final String TAG = "ClientesAdapter";

    private List<CustomerViewModel> clientes;
    private OnClienteActionListener actionListener;
    private OnClienteSelectionListener selectionListener;
    private int selectedPosition = -1;

    public interface OnClienteActionListener {
        void onRefreshRequested();
    }

    public interface OnClienteSelectionListener {
        void onClienteSelected(Integer clienteId);
    }

    public interface OnClienteInfoListener {
        void onClienteInfoRequested(Integer clienteId);
    }

    public ClientesAdapter(List<CustomerViewModel> clientes,
                           OnClienteActionListener actionListener) {
        this.clientes = clientes;
        this.actionListener = actionListener;
        Log.d(TAG, "ClientesAdapter creado SIN selectionListener");
    }

    public ClientesAdapter(List<CustomerViewModel> clientes,
                           OnClienteActionListener actionListener,
                           OnClienteSelectionListener selectionListener) {
        this.clientes = clientes;
        this.actionListener = actionListener;
        this.selectionListener = selectionListener;
        Log.d(TAG, "ClientesAdapter creado CON selectionListener: " + (selectionListener != null));
    }

    @NonNull
    @Override
    public ClienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cliente, parent, false);
        return new ClienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
        CustomerViewModel cliente = clientes.get(position);
        holder.bind(cliente, position);
    }

    @Override
    public int getItemCount() {
        return clientes.size();
    }

    public class ClienteViewHolder extends RecyclerView.ViewHolder {
        private TextView tvNombre;
        private TextView tvEmail;
        private TextView tvTelefono;
        private TextView tvEstado;
        private TextView tvClienteId;
        private View itemContainer;

        public ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvClienteNombre);
            tvEmail = itemView.findViewById(R.id.tvClienteEmail);
            tvTelefono = itemView.findViewById(R.id.tvClienteTelefono);
            tvEstado = itemView.findViewById(R.id.tvClienteEstado);
            tvEstado = itemView.findViewById(R.id.tvClienteEstado);
            itemContainer = itemView.findViewById(R.id.itemContainer);

            Log.d(TAG, "ViewHolder creado - Views encontradas: " +
                    "tvNombre=" + (tvNombre != null) +
                    ", tvEmail=" + (tvEmail != null) +
                    ", tvTelefono=" + (tvTelefono != null) +
                    ", tvEstado=" + (tvEstado != null) +
                    ", itemContainer=" + (itemContainer != null));
        }

        public void bind(CustomerViewModel cliente, int position) {
            Log.d(TAG, "Binding cliente en posición " + position + ": " +
                    (cliente != null ? cliente.getName() : "null"));

            // Nombre del cliente
            if (tvNombre != null) {
                String nombre = cliente.getName();
                if (nombre != null && !nombre.trim().isEmpty()) {
                    tvNombre.setText(nombre);
                } else {
                    tvNombre.setText("Sin nombre");
                }
            }

            // Email del cliente
            if (tvEmail != null) {
                String email = cliente.getEmail();
                if (email != null && !email.trim().isEmpty()) {
                    tvEmail.setText(email);
                } else {
                    tvEmail.setText("Sin email");
                }
            }

            // Teléfono del cliente
            if (tvTelefono != null) {
                String telefono = cliente.getTelephone();
                if (telefono != null && !telefono.trim().isEmpty()) {
                    tvTelefono.setText(telefono);
                } else {
                    tvTelefono.setText("Sin teléfono");
                }
            }

            // Estado del cliente
            if (tvEstado != null) {
                String estado = cliente.getState();
                if (estado != null && !estado.trim().isEmpty()) {
                    tvEstado.setText(estado);
                } else {
                    tvEstado.setText("Sin estado");
                }
            }

            // ID del cliente
            if (tvClienteId != null) {
                Integer id = cliente.getId();
                if (id != null) {
                    tvClienteId.setText("ID: " + id);
                } else {
                    tvClienteId.setText("ID: N/A");
                }
            }

            // Manejar selección visual
            if (itemContainer != null) {
                if (selectedPosition == position) {
                    itemContainer.setBackgroundColor(0xFFE8F4FD); // Color de selección (azul claro)
                    Log.d(TAG, "Item " + position + " marcado como seleccionado");
                } else {
                    itemContainer.setBackgroundColor(0xFFFFFFFF); // Color normal (blanco)
                }
            }

            // Configurar click listener
            itemView.setOnClickListener(v -> {
                Log.d(TAG, "Click en item " + position + ", cliente ID: " + cliente.getId());
                Log.d(TAG, "selectionListener disponible: " + (selectionListener != null));

                int previousSelected = selectedPosition;
                selectedPosition = position;

                // Notificar cambios visuales
                if (previousSelected != -1) {
                    notifyItemChanged(previousSelected);
                    Log.d(TAG, "Actualizado item anterior: " + previousSelected);
                }
                notifyItemChanged(selectedPosition);
                Log.d(TAG, "Actualizado item actual: " + selectedPosition);

                // Notificar selección al fragment
                if (selectionListener != null && cliente.getId() != null) {
                    Log.d(TAG, "Llamando onClienteSelected con ID: " + cliente.getId());
                    selectionListener.onClienteSelected(cliente.getId());
                } else {
                    Log.w(TAG, "No se puede notificar selección - " +
                            "selectionListener: " + (selectionListener != null) +
                            ", cliente.getId(): " + cliente.getId());
                }
            });
        }
    }

    public void clearSelection() {
        int previousSelected = selectedPosition;
        selectedPosition = -1;
        if (previousSelected != -1) {
            notifyItemChanged(previousSelected);
        }
        Log.d(TAG, "Selección limpiada");
    }

    public CustomerViewModel getSelectedCliente() {
        if (selectedPosition >= 0 && selectedPosition < clientes.size()) {
            CustomerViewModel selected = clientes.get(selectedPosition);
            Log.d(TAG, "getSelectedCliente: " + (selected != null ? selected.getName() : "null"));
            return selected;
        }
        Log.d(TAG, "getSelectedCliente: ninguno seleccionado");
        return null;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    // Método para actualizar la información de un cliente específico
    public void updateClienteInfo(Integer clienteId, CustomerViewModel clienteCompleto) {
        for (int i = 0; i < clientes.size(); i++) {
            CustomerViewModel cliente = clientes.get(i);
            if (cliente.getId() != null && cliente.getId().equals(clienteId)) {
                // Actualizar la información en la lista
                clientes.set(i, clienteCompleto);
                notifyItemChanged(i);
                Log.d(TAG, "Información del cliente actualizada en posición: " + i);
                break;
            }
        }
    }

    // Método para verificar si la información del cliente está completa
    private boolean isClienteInfoCompleta(CustomerViewModel cliente) {
        return cliente.getTelephone() != null && !cliente.getTelephone().trim().isEmpty() &&
                cliente.getState() != null && !cliente.getState().trim().isEmpty();
    }
}