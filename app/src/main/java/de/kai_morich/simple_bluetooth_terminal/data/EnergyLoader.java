package de.kai_morich.simple_bluetooth_terminal.data;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.EnergyDayResponse;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.TotalSeriesResponse;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Carga de series de energía de la vista Cliente. Los callbacks llegan en el hilo principal (Retrofit/Android).
 * Un 400 ("EmptyMeasurements" / "EmptyDevice") significa "sin datos": se entrega en ceros. Si falla la red, null.
 */
public final class EnergyLoader {

    private static final String TAG = "EnergyLoader";

    private EnergyLoader() {}

    private static String errorText(Response<?> r) {
        try { return r.errorBody() != null ? r.errorBody().string() : ""; } catch (Exception e) { return ""; }
    }

    /**
     * Algunos clientes devuelven las series de la localidad en W en lugar de kW: si el maximo de la serie es
     * mas de 100 veces la potencia actual (refKw, en kW) se asume W. Devuelve el factor a multiplicar (1 o 0.001).
     */
    public static double unitFactor(double[] series, double refKw) {
        if (series == null || refKw <= 0.5) return 1;
        double max = 0;
        for (double v : series) max = Math.max(max, Math.abs(v));
        return max / refKw > 100 ? 0.001 : 1;
    }

    public static double[] scale(double[] v, double f) {
        if (v == null || f == 1) return v;
        double[] out = new double[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * f;
        return out;
    }

    /** Energía por hora de un nodo en kWh (24 valores). Solar usa Generation, el resto Consumption; null si falla la red. */
    public static void nodeEnergyHours(ApiService api, int customer, int idNode, String date, final boolean solar,
                                       final Power cb) {
        api.getEnergyDay(idNode, date, customer).enqueue(new Callback<EnergyDayResponse>() {
            @Override public void onResponse(Call<EnergyDayResponse> c, Response<EnergyDayResponse> r) {
                if (!r.isSuccessful() || r.body() == null) { cb.done(r.code() == 400 ? new double[24] : null); return; }
                double[] ga = series(r.body().getGeneration(), 24), ca = series(r.body().getConsumption(), 24);
                double g = 0, co = 0;
                for (int i = 0; i < 24; i++) { g += Math.abs(ga[i]); co += Math.abs(ca[i]); }
                boolean useGen = solar ? g > 0 || co == 0 : co == 0 && g > 0;
                double[] out = new double[24];
                for (int i = 0; i < 24; i++) out[i] = Math.abs(useGen ? ga[i] : ca[i]) / 1000.0;
                cb.done(out);
            }
            @Override public void onFailure(Call<EnergyDayResponse> c, Throwable t) { cb.done(null); }
        });
    }

    /** Energia de hoy de un nodo en kWh (suma de las 24 horas); null si falla. Solar usa Generation, el resto Consumption. */
    public static void nodeEnergyToday(ApiService api, int customer, int idNode, String date, final boolean solar,
                                       final java.util.function.Consumer<Double> cb) {
        api.getEnergyDay(idNode, date, customer).enqueue(new Callback<EnergyDayResponse>() {
            @Override public void onResponse(Call<EnergyDayResponse> c, Response<EnergyDayResponse> r) {
                if (!r.isSuccessful() || r.body() == null) { cb.accept(r.code() == 400 ? Double.valueOf(0) : null); return; }
                double g = 0, co = 0;
                double[] ga = series(r.body().getGeneration(), 24), ca = series(r.body().getConsumption(), 24);
                for (int i = 0; i < 24; i++) { g += Math.abs(ga[i]); co += Math.abs(ca[i]); }
                double wh = solar ? (g > 0 ? g : co) : (co > 0 ? co : g);
                cb.accept(wh / 1000.0);
            }
            @Override public void onFailure(Call<EnergyDayResponse> c, Throwable t) { cb.accept(null); }
        });
    }

    /** Series de la localidad: solar (positivo), CFE y consumo total. null en los tres si falló la red. */
    public interface Totals { void done(double[] solar, double[] grid, double[] consumption); }
    /** Igual que {@link Totals} pero con la inyección a la red (sólo getTotalPowerDay). */
    public interface Full { void done(double[] solar, double[] grid, double[] consumption, double[] injection); }
    public interface Power { void done(double[] kw); }

    public static String ymd(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    /** El backend busca la fila del mes por su clave de texto exacta: yyyy-MM (con yyyy-MM-15 no hay fila y todo sale en 0). */
    private static String monthParam(Calendar c) {
        return new SimpleDateFormat("yyyy-MM", Locale.US).format(c.getTime());
    }

    // ───────────────────────── totales por localidad ─────────────────────────

    /** kW por hora (24 valores) de un día. */
    public static void day(ApiService api, int customer, int location, String date, final Totals cb) {
        dayFull(api, customer, location, date, (s, g, c, i) -> cb.done(s, g, c));
    }

    /** kW por hora de un día, con la inyección a la red incluida. */
    public static void dayFull(ApiService api, int customer, int location, String date, final Full cb) {
        api.getTotalPowerDay(customer, location, date).enqueue(new Callback<TotalSeriesResponse>() {
            @Override public void onResponse(Call<TotalSeriesResponse> c, Response<TotalSeriesResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    final double[] inj = series(r.body().getInyection(), 24);
                    deliver(r.body(), 24, false, (s, g, co) -> cb.done(s, g, co, inj));
                } else if (r.code() == 400) {
                    cb.done(new double[24], new double[24], new double[24], new double[24]); // sin datos ese día
                } else {
                    cb.done(null, null, null, null);
                }
            }
            @Override public void onFailure(Call<TotalSeriesResponse> c, Throwable t) { cb.done(null, null, null, null); }
        });
    }

    /** kWh por día de un mes (índice 0 = día 1). */
    private static void month(ApiService api, int customer, int location, String date, final int days, final Totals cb) {
        api.getTotalEnergyMonth(customer, location, date).enqueue(new Callback<TotalSeriesResponse>() {
            @Override public void onResponse(Call<TotalSeriesResponse> c, Response<TotalSeriesResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    deliver(r.body(), days, true, cb);
                } else if (r.code() == 400) {
                    Log.w(TAG, "mes " + date + " loc " + location + " -> 400 " + errorText(r));
                    cb.done(new double[days], new double[days], new double[days]);
                } else {
                    Log.w(TAG, "mes " + date + " loc " + location + " -> HTTP " + r.code() + " " + errorText(r));
                    cb.done(null, null, null);
                }
            }
            @Override public void onFailure(Call<TotalSeriesResponse> c, Throwable t) {
                Log.w(TAG, "mes " + date + " loc " + location + " -> " + t);
                cb.done(null, null, null);
            }
        });
    }

    /** kWh por día para [start, start+count): pide cada mes que toque el rango y toma los días necesarios. */
    public static void days(final ApiService api, final int customer, final int location, final Calendar start,
                            final int count, final Totals cb) {
        days(start, count, (date, dim, t) -> month(api, customer, location, date, dim, t), cb);
    }

    /**
     * kWh por día de un nodo (medidor). solar = generación, grid = consumo (el API los da en Wh), cons = suma.
     */
    public static void nodeDays(final ApiService api, final int customer, final int idNode, final Calendar start,
                                final int count, final Totals cb) {
        days(start, count, (date, dim, t) -> api.getEnergyMonth(idNode, date, customer).enqueue(new Callback<EnergyDayResponse>() {
            @Override public void onResponse(Call<EnergyDayResponse> c, Response<EnergyDayResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    double[] g = dayKeys(r.body().getGeneration(), dim), co = dayKeys(r.body().getConsumption(), dim);
                    double[] gk = new double[dim], ck = new double[dim], sum = new double[dim];
                    for (int i = 0; i < dim; i++) {
                        gk[i] = Math.abs(g[i]) / 1000.0;
                        ck[i] = Math.abs(co[i]) / 1000.0;
                        sum[i] = gk[i] + ck[i];
                    }
                    t.done(gk, ck, sum);
                } else if (r.code() == 400) {
                    t.done(new double[dim], new double[dim], new double[dim]);
                } else {
                    t.done(null, null, null);
                }
            }
            @Override public void onFailure(Call<EnergyDayResponse> c, Throwable e) { t.done(null, null, null); }
        }), cb);
    }

    /** {"D01": v, ..., "D31": v} → arreglo por día (índice 0 = día 1). */
    private static double[] dayKeys(Map<String, Double> m, int len) {
        double[] out = new double[len];
        if (m == null) return out;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            int i = digits(e.getKey()) - 1;
            if (e.getValue() != null && i >= 0 && i < len) out[i] = e.getValue();
        }
        return out;
    }

    private interface MonthFetch { void get(String date, int daysInMonth, Totals cb); }

    private static void days(final Calendar start, final int count, final MonthFetch fetch, final Totals cb) {
        final double[] solar = new double[count], grid = new double[count], cons = new double[count];
        // Meses tocados por el rango: "yyyy-MM" → una fecha del mes
        final Map<String, Calendar> months = new HashMap<>();
        final SimpleDateFormat ym = new SimpleDateFormat("yyyy-MM", Locale.US);
        Calendar c = (Calendar) start.clone();
        for (int i = 0; i < count; i++) {
            months.put(ym.format(c.getTime()), (Calendar) c.clone());
            c.add(Calendar.DAY_OF_MONTH, 1);
        }
        final int[] pending = {months.size()}, ok = {0};
        for (final Map.Entry<String, Calendar> m : months.entrySet()) {
            final int dim = m.getValue().getActualMaximum(Calendar.DAY_OF_MONTH);
            fetch.get(monthParam(m.getValue()), dim, (s, g, t) -> {
                if (s != null) {
                    ok[0]++;
                    // Cada valor i del mes es el día i+1: se coloca en su posición dentro del rango
                    Calendar d = (Calendar) start.clone();
                    for (int i = 0; i < count; i++, d.add(Calendar.DAY_OF_MONTH, 1)) {
                        if (!ym.format(d.getTime()).equals(m.getKey())) continue;
                        int idx = d.get(Calendar.DAY_OF_MONTH) - 1;
                        if (idx < s.length) { solar[i] = s[idx]; grid[i] = g[idx]; cons[i] = t[idx]; }
                    }
                }
                if (--pending[0] == 0) {
                    if (ok[0] == 0) cb.done(null, null, null); else cb.done(solar, grid, cons);
                }
            });
        }
    }

    /** @param byDay true para meses ("D01".."D31", indice 0 = dia 1); false para horas ("H00".."H23" o "0".."23"). */
    private static void deliver(TotalSeriesResponse r, int len, boolean byDay, Totals cb) {
        double[] gen = byDay ? dayKeys(r.getGeneration(), len) : series(r.getGeneration(), len);
        double[] grid = byDay ? dayKeys(r.getGridConsumption(), len) : series(r.getGridConsumption(), len);
        double[] cons = byDay ? dayKeys(r.getConsumption(), len) : series(r.getConsumption(), len);
        double[] solar = new double[len];
        boolean consEmpty = true;
        for (int i = 0; i < len; i++) {
            solar[i] = Math.abs(gen[i]);          // Generation viene negativa cuando hay generación
            if (cons[i] != 0) consEmpty = false;
        }
        if (consEmpty) {                          // si Consumption viene en 0 se calcula: |Generation| + GridConsumption
            for (int i = 0; i < len; i++) cons[i] = solar[i] + grid[i];
        }
        cb.done(solar, grid, cons);
    }

    /** {"0": v, "1": v, ...} → arreglo de largo fijo (lo que falta queda en 0). */
    private static double[] series(Map<String, Double> m, int len) {
        double[] out = new double[len];
        if (m == null) return out;
        for (Map.Entry<String, Double> e : m.entrySet()) {
            int i = digits(e.getKey());
            if (e.getValue() != null && i >= 0 && i < len) out[i] = e.getValue();
        }
        return out;
    }

    // ───────────────────────── potencia de un nodo (detalle del medidor) ─────────────────────────

    /** Potencia por hora (24 valores en kW) sumando los nodos; valores crudos del API tomados como W (ver ClienteMedidorFragment). */
    public static void powerDay(ApiService api, int customer, List<NodeTrafficLight> nodes, String date, final Power cb) {
        final double[] kw = new double[24];
        int total = 0;
        for (NodeTrafficLight n : nodes) if (n.getIdNode() != null) total++;
        if (total == 0) { cb.done(kw); return; }
        final int[] pending = {total}, ok = {0};
        for (NodeTrafficLight n : nodes) {
            if (n.getIdNode() == null) continue;
            api.getPowerDay(n.getIdNode(), date, customer).enqueue(new Callback<Map<String, Double>>() {
                @Override public void onResponse(Call<Map<String, Double>> c, Response<Map<String, Double>> r) {
                    if (r.isSuccessful() && r.body() != null) {
                        ok[0]++;
                        for (Map.Entry<String, Double> e : r.body().entrySet()) {
                            int h = digits(e.getKey());
                            if (e.getValue() != null && h >= 0 && h < 24) kw[h] += Math.abs(e.getValue()) / 1000.0; // W; el cliente recalibra con la energia de hoy
                        }
                    }
                    finish();
                }
                @Override public void onFailure(Call<Map<String, Double>> c, Throwable t) { finish(); }
                private void finish() { if (--pending[0] == 0) cb.done(ok[0] == 0 ? null : kw); }
            });
        }
    }

    private static int digits(String key) {
        String d = key == null ? "" : key.replaceAll("\\D", "");
        try { return d.isEmpty() ? -1 : Integer.parseInt(d); } catch (NumberFormatException e) { return -1; }
    }
}
