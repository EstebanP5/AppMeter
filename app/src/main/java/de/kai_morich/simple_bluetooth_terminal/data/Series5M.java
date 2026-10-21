package de.kai_morich.simple_bluetooth_terminal.data;

import java.util.Arrays;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Potencia de un nodo cada 5 minutos (get5MPowerDay). Cada fase llega como {"M0000": W en décimas | null, ...}; la potencia
 * del nodo es la suma de las fases, en kW (valor crudo / 10000). Los callbacks llegan en el hilo principal.
 */
public final class Series5M {

    public static final int SLOTS = 288;

    private static final String[] PREFIXES = {"api/ergoems/measurements/", "api/ergoems/"};
    private static int prefix; // el doc no aclara si la ruta lleva "measurements/": se recuerda la que respondió

    private Series5M() {}

    public interface Result {
        /** @param kw 288 valores (NaN = sin dato) o null si falló; @param error null si todo bien, "unsupported" o "network" */
        void done(double[] kw, String error);
    }

    public static void powerKw(final ApiService api, final int customer, final int idNode, final String date, final Result cb) {
        call(api, customer, idNode, date, cb, prefix, false);
    }

    private static void call(final ApiService api, final int customer, final int idNode, final String date,
                             final Result cb, final int pre, final boolean retried) {
        api.get5MSeries(PREFIXES[pre] + "get5MPowerDay", idNode, date, customer).enqueue(new Callback<Map<String, Map<String, Double>>>() {
            @Override public void onResponse(Call<Map<String, Map<String, Double>>> c, Response<Map<String, Map<String, Double>>> r) {
                if (r.code() == 404 && !retried) { call(api, customer, idNode, date, cb, 1 - pre, true); return; }
                if (r.isSuccessful() && r.body() != null) {
                    prefix = pre;
                    cb.done(sum(r.body()), null);
                    return;
                }
                String err = "";
                try { err = r.errorBody() != null ? r.errorBody().string() : ""; } catch (Exception ignore) { /* sin detalle */ }
                if (r.code() == 400 && err.contains("EmptyMeasurements")) {
                    double[] none = new double[SLOTS];
                    Arrays.fill(none, Double.NaN);
                    cb.done(none, null); // ese día no hay datos
                } else {
                    cb.done(null, r.code() == 400 || r.code() == 404 ? "unsupported" : "network");
                }
            }
            @Override public void onFailure(Call<Map<String, Map<String, Double>>> c, Throwable t) { cb.done(null, "network"); }
        });
    }

    private static double[] sum(Map<String, Map<String, Double>> body) {
        double[][] c = phases(body, "W1C", "W2C", "W3C"), g = phases(body, "W1G", "W2G", "W3G");
        boolean anyC = false;
        for (double[] p : c) if (p != null) for (double v : p) if (!Double.isNaN(v)) { anyC = true; break; }
        double[][] use = anyC ? c : g; // nodos que sólo reportan generación
        double[] out = new double[SLOTS];
        Arrays.fill(out, Double.NaN);
        for (double[] p : use) {
            if (p == null) continue;
            for (int i = 0; i < SLOTS; i++) {
                if (Double.isNaN(p[i])) continue;
                out[i] = (Double.isNaN(out[i]) ? 0 : out[i]) + p[i] / 10000.0;
            }
        }
        return out;
    }

    private static double[][] phases(Map<String, Map<String, Double>> body, String... keys) {
        double[][] out = new double[keys.length][];
        for (int k = 0; k < keys.length; k++) {
            Map<String, Double> m = body.get(keys[k]);
            if (m == null) continue;
            double[] a = new double[SLOTS];
            Arrays.fill(a, Double.NaN);
            for (Map.Entry<String, Double> e : m.entrySet()) {
                String key = e.getKey();
                if (key == null || key.length() < 5 || e.getValue() == null) continue;
                try {
                    int i = (Integer.parseInt(key.substring(1, 3)) * 60 + Integer.parseInt(key.substring(3, 5))) / 5;
                    if (i >= 0 && i < SLOTS) a[i] = e.getValue();
                } catch (NumberFormatException ignore) { /* llave rara */ }
            }
            out[k] = a;
        }
        return out;
    }

    /** Agrupa los 288 valores en bloques de {@code per} (1 = 5 min, 3 = 15 min, 12 = 1 h) promediando los que traen dato. */
    public static double[] bucket(double[] kw, int per) {
        double[] out = new double[SLOTS / per];
        for (int i = 0; i < out.length; i++) {
            double s = 0;
            int n = 0;
            for (int j = 0; j < per; j++) {
                double v = kw[i * per + j];
                if (!Double.isNaN(v)) { s += v; n++; }
            }
            out[i] = n == 0 ? Double.NaN : s / n;
        }
        return out;
    }
}
