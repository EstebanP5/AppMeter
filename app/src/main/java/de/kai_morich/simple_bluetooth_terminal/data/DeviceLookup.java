package de.kai_morich.simple_bluetooth_terminal.data;

import android.util.Log;

import java.util.List;

import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.DeviceCurrent;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Busca la medición en vivo (V, A, FP por fase, energía de hoy) de un nodo.
 * Primero con el idHW del tipo del nodo (CFE 0, LOAD 7...); si no aparece, con 111 (todos).
 * Se empareja por IdMeasure, y si no, por serie o por nombre. El callback llega con null si no se encontró.
 */
public final class DeviceLookup {

    private static final String TAG = "DeviceLookup";
    private static final int ALL = 111;

    private DeviceLookup() {}

    public interface Result { void done(DeviceCurrent d); }

    /** @param others otros circuitos de la localidad donde buscar si el propio no lo trae (puede ser null). */
    public static void find(final ApiService api, final int customer, final int circuit, final int idNode, final int type,
                     final String serie, final String name, final List<Integer> others, final Result cb) {
        if (circuit <= 0) {
            Log.w(TAG, "nodo " + idNode + " sin IdCircuit");
            searchOthers(api, customer, circuit, idNode, serie, others, 0, cb);
            return;
        }
        // idHW del API: 0-8 = tipo de nodo de energia (CFE 0, LOAD 7...), 111 = todos
        query(api, customer, circuit, type >= 0 ? type : 0, idNode, serie, name, d -> {
            if (d != null) { cb.done(d); return; }
            query(api, customer, circuit, ALL, idNode, serie, name, d2 -> {
                if (d2 != null) cb.done(d2); else searchOthers(api, customer, circuit, idNode, serie, others, 0, cb);
            });
        });
    }

    private static void searchOthers(final ApiService api, final int customer, final int own, final int idNode,
                                     final String serie, final List<Integer> others, final int from, final Result cb) {
        int i = from;
        while (others != null && i < others.size() && (others.get(i) == null || others.get(i) == own)) i++;
        if (others == null || i >= others.size()) { cb.done(null); return; }
        final int next = i;
        // En otros circuitos solo se acepta por IdMeasure o serie: emparejar por nombre daria falsos positivos
        query(api, customer, others.get(i), ALL, idNode, serie, null, d -> {
            if (d != null) cb.done(d); else searchOthers(api, customer, own, idNode, serie, others, next + 1, cb);
        });
    }

    private static void query(ApiService api, int customer, final int circuit, final Integer hw, final int idNode,
                              final String serie, final String name, final Result cb) {
        api.getCustomerDevicesCurrentDetailed(circuit, customer, hw).enqueue(new Callback<List<DeviceCurrent>>() {
            @Override public void onResponse(Call<List<DeviceCurrent>> c, Response<List<DeviceCurrent>> r) {
                if (!r.isSuccessful() || r.body() == null) { Log.w(TAG, "circuito " + circuit + " hw=" + hw + " -> HTTP " + r.code()); cb.done(null); return; }
                DeviceCurrent byName = null, bySerie = null;
                for (DeviceCurrent d : r.body()) {
                    if (d.getIdMeasure() != null && d.getIdMeasure() == idNode) { cb.done(d); return; }
                    if (serie != null && !serie.isEmpty() && serie.equals(d.getSerie())) bySerie = d;
                    if (name != null && name.equals(d.getName())) byName = d;
                }
                DeviceCurrent d = bySerie != null ? bySerie : byName;
                Log.w(TAG, "circuito " + circuit + " hw=" + hw + ": " + r.body().size() + " dispositivos, nodo " + idNode
                        + (d != null ? " emparejado por serie/nombre" : " no encontrado"));
                cb.done(d);
            }
            @Override public void onFailure(Call<List<DeviceCurrent>> c, Throwable t) { cb.done(null); }
        });
    }
}
