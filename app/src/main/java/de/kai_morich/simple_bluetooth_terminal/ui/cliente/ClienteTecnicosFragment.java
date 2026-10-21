package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.ReadoutView;
import de.kai_morich.simple_bluetooth_terminal.ui.charts.TechChartView;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * K5 · Datos técnicos cada 5 minutos: potencia, voltaje, corriente y energía por fase, con un marcador de hora que se
 * mueve sobre las gráficas y una tabla con los valores de esa hora. Se adapta a horizontal con layout-land.
 */
public class ClienteTecnicosFragment extends Fragment {

    private static final Locale ES = new Locale("es", "MX");
    private static final int[] PHASE_COLORS = {0xFFFFFFFF, 0xFFE53935, 0xFF3B8BFF}; // L1 se ve blanca sobre fondo oscuro
    private static final int[] CHIP_COLORS = {0xFF111111, 0xFFE00000, 0xFF1E7BFF};
    private static final String[] PREFIXES = {"api/ergoems/measurements/", "api/ergoems/"};
    private static int prefix; // índice del prefijo que respondió bien (el doc no aclara si lleva "measurements/")

    static ClienteTecnicosFragment newInstance(int idNode, String name) {
        Bundle b = new Bundle();
        b.putInt("idNode", idNode);
        b.putString("name", name);
        ClienteTecnicosFragment f = new ClienteTecnicosFragment();
        f.setArguments(b);
        return f;
    }

    private ClienteViewModel cvm;
    private TecnicosViewModel vm;
    private int idNode;
    private String name;

    private TextView tvDate, btnNext, tvEmpty;
    private ReadoutView readout;
    private final TextView[] chips = new TextView[4];
    private TechChartView chPower, chVolt, chAmp, chPf, chEnergy;
    private View bottomNav;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle b) {
        Bundle a = requireArguments();
        idNode = a.getInt("idNode");
        name = a.getString("name");
        cvm = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        vm = new ViewModelProvider(this).get(TecnicosViewModel.class);

        View v = inflater.inflate(R.layout.fragment_cliente_tecnicos, container, false);
        ((TextView) v.findViewById(R.id.tvSub)).setText((name != null ? name : "Medidor") + " · cada 5 min");
        tvDate = v.findViewById(R.id.tvDate);
        btnNext = v.findViewById(R.id.btnNext);
        readout = v.findViewById(R.id.readout);
        tvEmpty = v.findViewById(R.id.tvEmpty);
        chips[0] = v.findViewById(R.id.chipL1);
        chips[1] = v.findViewById(R.id.chipL2);
        chips[2] = v.findViewById(R.id.chipL3);
        chips[3] = v.findViewById(R.id.chipPf);
        chPower = v.findViewById(R.id.chPower);
        chVolt = v.findViewById(R.id.chVolt);
        chAmp = v.findViewById(R.id.chAmp);
        chPf = v.findViewById(R.id.chPf);
        chEnergy = v.findViewById(R.id.chEnergy);
        chPower.setHeader("Potencia activa", "kW · por fase");
        chVolt.setHeader("Voltaje", "V");
        chAmp.setHeader("Corriente", "A");
        chPf.setHeader("Factor de potencia", "FP");
        chEnergy.setHeader("Energía cada 5 min · L1+L2+L3", "kWh");
        chEnergy.setBars(true);
        chEnergy.setShowHours(true);
        TechChartView.OnMarker move = i -> { vm.marker = i; paintMarker(); };
        for (TechChartView c : new TechChartView[]{chPower, chVolt, chAmp, chPf, chEnergy}) c.setOnMarker(move);

        v.findViewById(R.id.btnBack).setOnClickListener(x -> requireActivity().onBackPressed());
        v.findViewById(R.id.btnCsv).setOnClickListener(x -> downloadCsv());
        v.findViewById(R.id.btnPrev).setOnClickListener(x -> { vm.offset++; reload(); });
        btnNext.setOnClickListener(x -> { if (vm.offset > 0) { vm.offset--; reload(); } });
        for (int i = 0; i < 4; i++) {
            final int k = i;
            chips[i].setOnClickListener(x -> {
                if (k < 3) vm.on[k] = !vm.on[k]; else vm.pfOn = !vm.pfOn;
                paintAll();
            });
        }
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        vm.onChange = this::paintAll;
        if (!vm.requested) reload(); else paintAll();
    }

    @Override public void onResume() {
        super.onResume();
        // En horizontal la barra inferior sólo estorba: la pantalla se queda con todo el alto
        bottomNav = requireActivity().findViewById(R.id.cliente_bottom_nav);
        boolean land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (bottomNav != null) bottomNav.setVisibility(land ? View.GONE : View.VISIBLE);
    }

    @Override public void onPause() {
        super.onPause();
        if (bottomNav != null) bottomNav.setVisibility(View.VISIBLE);
    }

    @Override public void onDestroyView() {
        super.onDestroyView();
        vm.onChange = null;
    }

    // ───────────────────────── datos ─────────────────────────

    private String date() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -vm.offset);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.getTime());
    }

    private void reload() {
        if (cvm.customerId == null || idNode == 0) return;
        vm.reset();
        vm.requested = true;
        vm.generation++;
        final int gen = vm.generation;
        final String date = date();
        vm.pending = 5;
        paintAll();
        fetch("get5MPowerDay", date, gen, m -> {
            double[][] c = phases(m, "W1C", "W2C", "W3C"), g = phases(m, "W1G", "W2G", "W3G");
            for (int i = 0; i < 3; i++) if (c[i] == null || allNaN(c[i])) c[i] = g[i]; // nodos que sólo reportan generación
            vm.kw = scaled(c, 10000.0); // W en décimas: /10 = W, /1000 = kW
        });
        fetch("get5MVoltageDay", date, gen, m -> vm.volt = scaled(phases(m, "V1", "V2", "V3"), 10.0));
        fetch("get5MCurrentDay", date, gen, m -> vm.amp = scaled(phases(m, "I1", "I2", "I3"), 10.0));
        fetch("get5MPowerFactorDay", date, gen, m -> vm.pf = scaled(phases(m, "PF1", "PF2", "PF3"), 1.0));
        fetch("get5MEnergyDay", date, gen, m -> {
            double[][] e = phases(m, "Energy1", "Energy1", "Energy1");
            vm.kwh = e[0] == null ? null : scaled(new double[][]{e[0]}, 1000.0)[0];
        });
    }

    private interface Parse { void run(Map<String, Map<String, Double>> body); }

    /** Pide una serie; 400 (sin datos) deja la serie vacía, 404 prueba la ruta sin "measurements/". */
    private void fetch(final String endpoint, final String date, final int gen, final Parse parse) {
        call(endpoint, date, gen, parse, prefix, false);
    }

    private void call(final String endpoint, final String date, final int gen, final Parse parse, final int pre, final boolean retried) {
        cvm.api().get5MSeries(PREFIXES[pre] + endpoint, idNode, date, cvm.customerId).enqueue(new Callback<Map<String, Map<String, Double>>>() {
            @Override public void onResponse(Call<Map<String, Map<String, Double>>> c, Response<Map<String, Map<String, Double>>> r) {
                if (gen != vm.generation) return;
                if (r.code() == 404 && !retried) { call(endpoint, date, gen, parse, 1 - pre, true); return; }
                if (r.isSuccessful() && r.body() != null) {
                    prefix = pre;
                    parse.run(r.body());
                    vm.anyData = true;
                }
                done();
            }
            @Override public void onFailure(Call<Map<String, Map<String, Double>>> c, Throwable t) {
                if (gen == vm.generation) done();
            }
        });
    }

    private void done() {
        vm.pending--;
        if (vm.pending == 0) {
            maskGaps();
            if (vm.marker < 0) vm.marker = defaultMarker();
        }
        if (vm.onChange != null) vm.onChange.run();
    }

    /** Última hora con dato; si el día está vacío, las 12:00. */
    private int defaultMarker() {
        double[][] s = vm.kw != null ? vm.kw : vm.amp != null ? vm.amp : vm.volt;
        if (s != null) {
            for (int i = TechChartView.POINTS - 1; i >= 0; i--) for (double[] p : s) if (p != null && !Double.isNaN(p[i])) return i;
        }
        return 144;
    }

    /** {"W1C": {"M0000": v|null...}} → arreglos de 288 con NaN donde no hay dato (null si la llave no vino). */
    private static double[][] phases(Map<String, Map<String, Double>> body, String... keys) {
        double[][] out = new double[keys.length][];
        for (int k = 0; k < keys.length; k++) {
            Map<String, Double> m = body.get(keys[k]);
            if (m == null) continue;
            double[] a = new double[TechChartView.POINTS];
            Arrays.fill(a, Double.NaN);
            for (Map.Entry<String, Double> e : m.entrySet()) {
                String key = e.getKey();
                if (key == null || key.length() < 5 || e.getValue() == null) continue;
                try {
                    int hh = Integer.parseInt(key.substring(1, 3)), mm = Integer.parseInt(key.substring(3, 5));
                    int i = (hh * 60 + mm) / 5;
                    if (i >= 0 && i < a.length) a[i] = e.getValue();
                } catch (NumberFormatException ignore) { /* llave rara: se omite */ }
            }
            out[k] = a;
        }
        return out;
    }

    private static boolean allNaN(double[] a) {
        for (double v : a) if (!Double.isNaN(v)) return false;
        return true;
    }

    /** Valor crudo / div (el back confirmó: potencia, voltaje y corriente llegan en décimas; la energía en Wh). */
    private static double[][] scaled(double[][] raw, double div) {
        double[][] out = new double[raw.length][];
        for (int k = 0; k < raw.length; k++) {
            if (raw[k] == null) continue;
            out[k] = new double[raw[k].length];
            for (int i = 0; i < out[k].length; i++) out[k][i] = raw[k][i] / div; // NaN / n = NaN
        }
        return out;
    }

    /**
     * Lo que aún no llega (o un corte del medidor) viene como 0.0, no null. Una fase energizada nunca marca 0 V:
     * donde el voltaje de una fase es 0 se deja hueco en todas sus series, y la energía donde ninguna fase tiene voltaje.
     */
    private void maskGaps() {
        if (vm.volt == null) return;
        int n = TechChartView.POINTS;
        boolean[] anyLive = new boolean[n];
        for (int p = 0; p < vm.volt.length; p++) {
            double[] v = vm.volt[p];
            if (v == null) continue;
            for (int i = 0; i < n; i++) {
                boolean live = !Double.isNaN(v[i]) && v[i] != 0;
                if (live) { anyLive[i] = true; continue; }
                for (double[][] s : new double[][][]{vm.kw, vm.volt, vm.amp, vm.pf}) {
                    if (s != null && p < s.length && s[p] != null) s[p][i] = Double.NaN;
                }
            }
        }
        if (vm.kwh != null) for (int i = 0; i < n; i++) if (!anyLive[i]) vm.kwh[i] = Double.NaN;
    }

    // ───────────────────────── pintar ─────────────────────────

    private void paintAll() {
        if (getView() == null) return;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -vm.offset);
        String d = new SimpleDateFormat("EEE d MMM yyyy", ES).format(c.getTime()).replace(".", "");
        tvDate.setText(vm.offset == 0 ? "Hoy · " + new SimpleDateFormat("d MMM yyyy", ES).format(c.getTime()).replace(".", "") : cap(d));
        btnNext.setAlpha(vm.offset > 0 ? 1f : 0.35f);

        boolean[] has = new boolean[3];
        for (int p = 0; p < 3; p++) {
            has[p] = hasData(vm.kw, p) || hasData(vm.amp, p) || hasData(vm.volt, p);
            styleChip(chips[p], CHIP_COLORS[p], vm.on[p] && has[p], has[p], "● L" + (p + 1));
        }
        styleChip(chips[3], 0xFF161616, vm.pfOn, vm.pf != null, "FP");

        boolean[] vis = {vm.on[0] && has[0], vm.on[1] && has[1], vm.on[2] && has[2]};
        chPower.setSeries(vm.kw, PHASE_COLORS, vis);
        chVolt.setSeries(vm.volt, PHASE_COLORS, vis);
        chAmp.setSeries(vm.amp, PHASE_COLORS, vis);
        if (vm.pfOn) chPf.setSeries(vm.pf, PHASE_COLORS, vis);
        int pfVis = vm.pfOn ? View.VISIBLE : View.GONE;
        if (chPf.getVisibility() != pfVis) chPf.setVisibility(pfVis);
        chEnergy.setSeries(vm.kwh == null ? null : new double[][]{vm.kwh}, new int[]{0xFFD9A21B}, new boolean[]{true});
        tvEmpty.setVisibility(vm.pending == 0 && !vm.anyData ? View.VISIBLE : View.GONE);
        paintMarker();
        // Al ocultar filas o la gráfica de FP cambia el alto del contenido: se pide un nuevo layout y se redibuja todo
        // después de él (en algunos equipos las gráficas se quedaban en blanco hasta el siguiente cambio de layout)
        final View card = (View) chPower.getParent();
        card.requestLayout();
        card.post(() -> {
            for (TechChartView ch : new TechChartView[]{chPower, chVolt, chAmp, chPf, chEnergy}) ch.invalidate();
        });
    }

    private static boolean hasData(double[][] s, int p) {
        return s != null && p < s.length && s[p] != null && !allNaN(s[p]);
    }

    private void paintMarker() {
        if (getView() == null) return;
        int i = vm.marker;
        for (TechChartView c : new TechChartView[]{chPower, chVolt, chAmp, chPf, chEnergy}) c.setMarker(i);
        List<String[]> rows = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        if (i < 0) { readout.set("--:--", "", rows, colors); return; }
        int mins = i * 5;
        double e = vm.kwh != null ? vm.kwh[i] : Double.NaN;
        for (int p = 0; p < 3; p++) {
            boolean has = hasData(vm.kw, p) || hasData(vm.amp, p) || hasData(vm.volt, p);
            if (!has || !vm.on[p]) continue; // sólo las fases activas, como en las gráficas
            rows.add(new String[]{"L" + (p + 1), val(vm.kw, p, i, "%.1f"), val(vm.volt, p, i, "%.0f"),
                    val(vm.amp, p, i, "%.0f"), val(vm.pf, p, i, "%.2f")});
            colors.add(CHIP_COLORS[p]);
        }
        readout.set(String.format(Locale.US, "%02d:%02d", mins / 60, mins % 60),
                Double.isNaN(e) ? "Sin energía en este intervalo" : "Energía 5 min " + signed(e) + " kWh", rows, colors);
    }

    private static String val(double[][] s, int p, int i, String fmt) {
        if (s == null || p >= s.length || s[p] == null || Double.isNaN(s[p][i])) return "—";
        return String.format(Locale.US, fmt, s[p][i]);
    }

    private static String signed(double v) {
        return (v < 0 ? "−" : "") + String.format(Locale.US, "%.1f", Math.abs(v));
    }

    private void styleChip(TextView t, int color, boolean on, boolean enabled, String label) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(100 * getResources().getDisplayMetrics().density);
        if (on) {
            g.setColor(color);
        } else {
            g.setColor(0xFFFFFFFF);
            g.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), enabled ? 0xFFDDDDDD : 0xFFEEEEEE);
        }
        t.setBackground(g);
        t.setText(label);
        t.setTextColor(on ? 0xFFFFFFFF : enabled ? ContextCompat.getColor(requireContext(), R.color.am_ink) : 0xFFBBBBBB);
        t.setEnabled(enabled);
    }

    private static String cap(String s) { return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(ES) + s.substring(1); }

    // ───────────────────────── CSV ─────────────────────────

    private static final java.util.concurrent.ExecutorService IO = java.util.concurrent.Executors.newSingleThreadExecutor();

    private retrofit2.Response<ResponseBody> csvCall(int pre, String day) throws java.io.IOException {
        return cvm.api().downloadCsv(PREFIXES[pre] + "downloadElectricityRawDataCSV", idNode,
                day + " 00:00:00", day + " 23:59:59", "todas", cvm.customerId).execute();
    }

    /**
     * Descarga el día completo con la sesión de la app (el administrador de descargas del sistema no manda el token y
     * no dice por qué falla). Se guarda en Descargas y la barra inferior ofrece abrirlo; si algo falla dice el motivo.
     */
    private void downloadCsv() {
        if (cvm.customerId == null) return;
        final android.app.Activity act = requireActivity();
        final Context app = act.getApplicationContext();
        final String day = date();
        final String fileName = "datos_" + idNode + "_" + day + ".csv";
        Snackbar.make(requireView(), "Preparando CSV…", Snackbar.LENGTH_SHORT).show();
        IO.execute(() -> {
            String msg;
            Uri saved = null;
            try {
                retrofit2.Response<ResponseBody> r = csvCall(prefix, day);
                if (r.code() == 404) r = csvCall(1 - prefix, day); // el doc no aclara si la ruta lleva "measurements/"
                if (r.isSuccessful() && r.body() != null) {
                    saved = save(app, fileName, r.body());
                    msg = "CSV guardado en Descargas: " + fileName;
                } else {
                    String err = r.errorBody() != null ? r.errorBody().string() : "";
                    Log.w("TecnicosCsv", "HTTP " + r.code() + " " + err);
                    msg = err.contains("EmptyMeasurements") ? "Ese día no tiene datos para exportar"
                            : "No se pudo descargar (error " + r.code() + ")";
                }
            } catch (Exception e) {
                Log.w("TecnicosCsv", e);
                msg = "No se pudo descargar: " + (e.getMessage() != null ? e.getMessage() : "sin conexión");
            }
            final String m = msg;
            final Uri u = saved;
            act.runOnUiThread(() -> {
                if (act.isFinishing()) return;
                Snackbar sb = Snackbar.make(act.findViewById(android.R.id.content), m, Snackbar.LENGTH_LONG);
                if (u != null) sb.setAction("Abrir", x -> openCsv(act, u));
                sb.show();
            });
        });
    }

    /** Android 10+: a la carpeta pública Descargas (sin permisos); antes, a la carpeta de la app y se comparte por FileProvider. */
    private static Uri save(Context app, String fileName, ResponseBody body) throws java.io.IOException {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            android.content.ContentValues cv = new android.content.ContentValues();
            cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/csv");
            cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri uri = app.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) throw new java.io.IOException("No se pudo crear el archivo");
            try (java.io.InputStream in = body.byteStream(); java.io.OutputStream out = app.getContentResolver().openOutputStream(uri)) {
                copy(in, out);
            }
            return uri;
        }
        java.io.File dir = app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) throw new java.io.IOException("Sin almacenamiento disponible");
        java.io.File f = new java.io.File(dir, fileName);
        try (java.io.InputStream in = body.byteStream(); java.io.OutputStream out = new java.io.FileOutputStream(f)) {
            copy(in, out);
        }
        return androidx.core.content.FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", f);
    }

    private static void copy(java.io.InputStream in, java.io.OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Abre el CSV con la app que lo soporte (Excel, Hojas de cálculo...); si no hay, ofrece compartirlo. */
    private static void openCsv(android.app.Activity act, Uri uri) {
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "text/csv").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            act.startActivity(view);
        } catch (android.content.ActivityNotFoundException e) {
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            act.startActivity(Intent.createChooser(send, "Compartir CSV"));
        }
    }
}
