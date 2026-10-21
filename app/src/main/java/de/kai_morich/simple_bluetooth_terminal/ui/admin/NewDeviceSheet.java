package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.app.Activity;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.CircuitsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.DevicesViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.LocationsViewModel;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import de.kai_morich.simple_bluetooth_terminal.models.SerialValidation;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * C3 · Nuevo dispositivo en un solo paso. "Guardar y vincular" encadena: registrar la serie (sólo si es nueva) →
 * crear el nodo (SIN serie: addNewSmartSunLite la vincularía siempre en carga 0) → vincular serie y carga.
 * CFE se trata como nodo virtual (sin medidor): no pide serie ni carga.
 */
public final class NewDeviceSheet {

    // Orden de los chips → tipo de nodo (0 CFE/TOTAL_Wh, 1 Transformador, 2 PV, 3 BESS)
    private static final int[] TYPE_VALUES = {1, 2, 0, 3};

    // Estados de la serie
    private static final int EMPTY = 0, NEW = 1, FREE = 2, BAD = 3, UNKNOWN = 4;

    private final Activity act;
    private final ApiService api = ApiClient.getApiService();
    private final int customerId;
    private final LocationsViewModel location;
    private final CircuitsViewModel circuit;
    private final Runnable onDone;
    private final BottomSheetDialog dialog;

    private final TextView[] chips = new TextView[4], loads = new TextView[4];
    private EditText etName, etDesc, etSerie;
    private TextView tvState, btnSave;
    private View groupMeter;
    private int typeIdx = 0, load = 0;
    private int serieState = EMPTY;
    private int checkGen;
    private boolean saving;

    public static void show(Activity act, int customerId, String customerName, LocationsViewModel loc, CircuitsViewModel circuit, Runnable onDone) {
        new NewDeviceSheet(act, customerId, customerName, loc, circuit, onDone);
    }

    private NewDeviceSheet(Activity act, int customerId, String customerName, LocationsViewModel loc, CircuitsViewModel circuit, Runnable onDone) {
        this.act = act;
        this.customerId = customerId;
        this.location = loc;
        this.circuit = circuit;
        this.onDone = onDone;

        View v = LayoutInflater.from(act).inflate(R.layout.sheet_new_device, null);
        dialog = new BottomSheetDialog(act);
        dialog.setContentView(v);
        dialog.setOnShowListener(x -> {
            View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });

        // Migas: Cliente › Localidad › Circuito (el circuito en negrita)
        String a = (customerName != null ? customerName : "Cliente") + " › " + (loc != null ? loc.getName() : "—") + " › ";
        String b = circuit != null ? circuit.getName() : "—";
        SpannableString crumbs = new SpannableString(a + b);
        crumbs.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), a.length(), crumbs.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) v.findViewById(R.id.tvCrumbs)).setText(crumbs);

        int[] chipIds = {R.id.chipType0, R.id.chipType1, R.id.chipType2, R.id.chipType3};
        int[] loadIds = {R.id.load0, R.id.load1, R.id.load2, R.id.load3};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            chips[i] = v.findViewById(chipIds[i]);
            loads[i] = v.findViewById(loadIds[i]);
            chips[i].setOnClickListener(x -> { typeIdx = idx; paint(); });
            loads[i].setOnClickListener(x -> { load = idx; paint(); checkSerie(); }); // la carga cambia si la serie está libre
        }
        etName = v.findViewById(R.id.etName);
        etDesc = v.findViewById(R.id.etDesc);
        etSerie = v.findViewById(R.id.etSerie);
        tvState = v.findViewById(R.id.tvSerieState);
        groupMeter = v.findViewById(R.id.groupMeter);
        btnSave = v.findViewById(R.id.btnSave);
        v.findViewById(R.id.btnCancel).setOnClickListener(x -> dialog.dismiss());
        btnSave.setOnClickListener(x -> save());
        etSerie.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int s1, int s2, int s3) { }
            @Override public void onTextChanged(CharSequence s, int s1, int s2, int s3) { }
            @Override public void afterTextChanged(Editable s) { checkSerie(); }
        });
        paint();
        dialog.show();
    }

    private boolean isVirtual() { return TYPE_VALUES[typeIdx] == 0; }

    private void paint() {
        for (int i = 0; i < 4; i++) {
            boolean on = i == typeIdx;
            chips[i].setBackgroundResource(on ? R.drawable.am_pill_on : R.drawable.am_pill_off);
            chips[i].setTextColor(ContextCompat.getColor(act, on ? R.color.white : R.color.am_ink));
            boolean lon = i == load;
            loads[i].setBackgroundResource(lon ? R.drawable.am_box_on : R.drawable.am_box_off);
            loads[i].setTextColor(ContextCompat.getColor(act, lon ? R.color.white : R.color.am_ink));
        }
        groupMeter.setVisibility(isVirtual() ? View.GONE : View.VISIBLE);
    }

    // ───────────────────────── validación de la serie ─────────────────────────

    private String serie() { return etSerie.getText().toString().trim().toUpperCase(Locale.ROOT); }

    /** Pregunta al servidor: SerialNotRegistered → nueva · Available → registrada y libre · SerialAlreadyLinked → ocupada. */
    private void checkSerie() {
        final String s = serie();
        final int gen = ++checkGen;
        if (s.length() < 6) { setState(EMPTY, null, 0); return; } // las series reales tienen 12 dígitos
        api.validateSerialForLink(s, 0, customerId, load).enqueue(new Callback<SerialValidation>() {
            @Override public void onResponse(Call<SerialValidation> c, Response<SerialValidation> r) {
                if (gen != checkGen) return;
                String msg = r.isSuccessful() && r.body() != null ? r.body().getMessage() : errorBody(r);
                boolean valid = r.isSuccessful() && r.body() != null && r.body().isValid();
                if (msg.contains("SerialNotRegistered")) setState(NEW, "✓ Serie nueva · se registra al guardar", 0xFF157A3A);
                else if (valid) setState(FREE, "✓ Serie registrada · se vinculará", 0xFF157A3A);
                else if (msg.contains("SerialAlreadyLinked")) setState(BAD, "Esa serie ya está vinculada a otro nodo con esa carga", 0xFFD23B2B);
                else setState(BAD, msg.isEmpty() ? "No se pudo validar la serie" : msg, 0xFFD23B2B);
            }
            @Override public void onFailure(Call<SerialValidation> c, Throwable t) {
                if (gen == checkGen) setState(UNKNOWN, "No se pudo validar la serie (sin conexión)", 0xFFB07A00);
            }
        });
    }

    private void setState(int state, String text, int color) {
        serieState = state;
        if (text == null) { tvState.setVisibility(View.GONE); return; }
        tvState.setText(text);
        tvState.setTextColor(color);
        tvState.setVisibility(View.VISIBLE);
    }

    private static String errorBody(Response<?> r) {
        try { return r != null && r.errorBody() != null ? r.errorBody().string() : ""; } catch (IOException e) { return ""; }
    }

    // ───────────────────────── guardar ─────────────────────────

    private void toast(String m) { Forms.toast(act, m); }

    private void busy(boolean on) {
        saving = on;
        btnSave.setAlpha(on ? 0.5f : 1f);
        btnSave.setText(on ? "Guardando…" : "Guardar y vincular");
    }

    private void save() {
        if (saving) return;
        final String name = etName.getText().toString().trim();
        if (name.isEmpty()) { etName.setError("Obligatorio"); etName.requestFocus(); return; }
        final String serie = isVirtual() ? "" : serie();
        if (!serie.isEmpty() && serieState == BAD) { toast("Corrige la serie antes de guardar"); return; }
        if (circuit == null || circuit.getIdCircuit() == null) { toast("Falta el circuito"); return; }

        busy(true);
        // Paso 1 (sólo si la serie es nueva): registrarla al cliente
        if (!serie.isEmpty() && serieState == NEW) {
            DevicesViewModel reg = new DevicesViewModel();
            reg.setIdCustomer(customerId);
            reg.setSerie(serie);
            reg.setIdHW(0); // aquí el campo se llama idHW: 0 = METER
            api.registerDeviceSerial(reg).enqueue(new Callback<Void>() {
                @Override public void onResponse(Call<Void> c, Response<Void> r) {
                    if (r.isSuccessful()) { createNode(name, serie); return; }
                    busy(false);
                    String body = errorBody(r);
                    if (body.contains("SerialNotInCatalog")) toast("Esa serie no existe en el catálogo de medidores. Revisa el número.");
                    else if (body.contains("SerialInUse")) toast("Esa serie pertenece a otro cliente.");
                    else toast(Forms.error("No se pudo registrar la serie", r));
                }
                @Override public void onFailure(Call<Void> c, Throwable t) { busy(false); toast("Sin conexión"); }
            });
        } else {
            createNode(name, serie);
        }
    }

    /** Paso 2: crear el nodo, sin serie (si se manda aquí queda vinculada siempre en carga 0). */
    private void createNode(final String name, final String serie) {
        final int nodeType = TYPE_VALUES[typeIdx];
        DevicesViewModel dev = new DevicesViewModel();
        dev.setIdCircuit(circuit.getIdCircuit());
        dev.setIdLocation(location != null ? location.getIdLocation() : circuit.getIdLocation());
        dev.setType(nodeType);
        dev.setNodeType(nodeType);
        dev.setIdHW(nodeType);
        dev.setName(name);
        dev.setDescription(etDesc.getText().toString().trim());
        dev.setIdCustomer(customerId);
        api.addNewDevice(dev).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if (!r.isSuccessful()) { busy(false); toast(Forms.error("No se pudo crear el dispositivo", r)); return; }
                if (serie.isEmpty()) { finish("Dispositivo creado"); return; }
                findNode(name, serie);
            }
            @Override public void onFailure(Call<Void> c, Throwable t) { busy(false); toast("Sin conexión"); }
        });
    }

    /** La creación no devuelve el id del nodo: se busca el más reciente con ese nombre en el circuito. */
    private void findNode(final String name, final String serie) {
        api.getCustomerNodesTrafficLight(customerId).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                Integer id = null;
                if (r.isSuccessful() && r.body() != null && r.body().getNodes() != null) {
                    for (NodeTrafficLight n : r.body().getNodes()) {
                        if (name.equals(n.getName()) && n.getIdNode() != null
                                && circuit.getIdCircuit().equals(n.getIdCircuit())
                                && (id == null || n.getIdNode() > id)) id = n.getIdNode();
                    }
                }
                if (id == null) { busy(false); toast("El dispositivo se creó, pero no se encontró para vincular la serie"); onDone.run(); return; }
                link(id, serie);
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) {
                busy(false);
                toast("El dispositivo se creó, pero falló la vinculación: sin conexión");
                onDone.run();
            }
        });
    }

    /** Paso 3: vincular serie y carga al nodo. */
    private void link(int idNode, String serie) {
        Map<String, Object> body = new HashMap<>();
        body.put("IdNode", idNode);
        body.put("Serie", serie);
        body.put("Load", load);
        body.put("IdCustomer", customerId);
        api.linkNodeWithSerialMap(body).enqueue(new Callback<Void>() {
            @Override public void onResponse(Call<Void> c, Response<Void> r) {
                if (r.isSuccessful()) finish("Dispositivo creado y vinculado");
                else { busy(false); toast(Forms.error("Dispositivo creado, pero no se pudo vincular la serie", r)); onDone.run(); }
            }
            @Override public void onFailure(Call<Void> c, Throwable t) {
                busy(false);
                toast("Dispositivo creado, pero falló la vinculación");
                onDone.run();
            }
        });
    }

    private void finish(String message) {
        toast(message);
        dialog.dismiss();
        onDone.run();
    }
}
