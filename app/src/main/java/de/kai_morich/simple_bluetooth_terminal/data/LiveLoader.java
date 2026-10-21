package de.kai_morich.simple_bluetooth_terminal.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import de.kai_morich.simple_bluetooth_terminal.models.EnergyFlowGraphViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Lecturas en vivo de varios nodos a la vez (admin): una consulta por cada pareja circuito + tipo de nodo, porque
 * getCustomerDevicesCurrent filtra por idHW = tipo del nodo (CFE 0, LOAD 7...). Los callbacks llegan en el hilo principal.
 */
public final class LiveLoader {

    private LiveLoader() {}

    public interface Devices { void done(Map<Integer, DeviceCurrent> byNode); }

    public interface Battery { void done(BatteryInfo info); }

    /** Batería de la localidad, sumando los circuitos que la tengan (getEnergyFlowGraph). */
    public static final class BatteryInfo {
        public boolean has, charging, discharging;
        public double watts, whDay;
    }

    public static void devices(final ApiService api, final int customer, List<NodeTrafficLight> nodes, final Devices cb) {
        final Set<String> pairs = new HashSet<>();
        for (NodeTrafficLight n : nodes) {
            if (n.getIdCircuit() == null || n.getIdCircuit() <= 0) continue;
            pairs.add(n.getIdCircuit() + ":" + (n.getType() != null ? n.getType() : 0));
        }
        final Map<Integer, DeviceCurrent> out = new HashMap<>();
        if (pairs.isEmpty()) { cb.done(out); return; }
        final int[] pending = {pairs.size()};
        for (String p : pairs) {
            String[] a = p.split(":");
            api.getCustomerDevicesCurrentDetailed(Integer.parseInt(a[0]), customer, Integer.parseInt(a[1])).enqueue(new Callback<List<DeviceCurrent>>() {
                @Override public void onResponse(Call<List<DeviceCurrent>> c, Response<List<DeviceCurrent>> r) {
                    if (r.isSuccessful() && r.body() != null) {
                        for (DeviceCurrent d : r.body()) if (d.getIdMeasure() != null) out.put(d.getIdMeasure(), d);
                    }
                    finish();
                }
                @Override public void onFailure(Call<List<DeviceCurrent>> c, Throwable t) { finish(); }
                private void finish() { if (--pending[0] == 0) cb.done(out); }
            });
        }
    }

    /** Potencia en kW con signo: ConW / GenW si vienen, y si no la suma de las tres fases (W1 + W2 + W3). */
    public static double kw(DeviceCurrent d) {
        if (d == null) return 0;
        double w = d.getConW() != 0 ? d.getConW() : d.getGenW() != 0 ? d.getGenW() : d.getW1() + d.getW2() + d.getW3();
        return w / 1000.0;
    }

    /** Batería: getTotals no trae HasBess, el diagrama de flujo de cada circuito sí. */
    public static void battery(final ApiService api, final int customer, java.util.Collection<Integer> circuitIds, final Battery cb) {
        final Set<Integer> circuits = new HashSet<>();
        for (Integer id : circuitIds) if (id != null && id > 0) circuits.add(id);
        final BatteryInfo info = new BatteryInfo();
        if (circuits.isEmpty()) { cb.done(info); return; }
        final int[] pending = {circuits.size()};
        for (int circuit : circuits) {
            api.getEnergyFlowGraph(circuit, customer).enqueue(new Callback<EnergyFlowGraphViewModel>() {
                @Override public void onResponse(Call<EnergyFlowGraphViewModel> c, Response<EnergyFlowGraphViewModel> r) {
                    EnergyFlowGraphViewModel g = r.isSuccessful() ? r.body() : null;
                    if (g != null && Boolean.TRUE.equals(g.getHasBess())) {
                        info.has = true;
                        info.charging |= Boolean.TRUE.equals(g.getIsBessCharging());
                        info.discharging |= Boolean.TRUE.equals(g.getIsBessDischarging());
                        info.watts += g.getBessW() != null ? Math.abs(g.getBessW()) : 0;
                        info.whDay += g.getBessWhDay() != null ? g.getBessWhDay() : 0;
                    }
                    finish();
                }
                @Override public void onFailure(Call<EnergyFlowGraphViewModel> c, Throwable t) { finish(); }
                private void finish() { if (--pending[0] == 0) cb.done(info); }
            });
        }
    }
}
