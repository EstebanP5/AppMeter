package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.serial.Constants;

/**
 * Pestaña WiFi: lista de redes de 2.4 GHz (recomendadas / pueden fallar), contraseña, validación y envío al medidor.
 * Android permite ~4 escaneos cada 2 minutos; más allá devuelve resultados viejos sin avisar, por eso el cooldown.
 */
public class MeterWifiTab {

    private static final long SCAN_COOLDOWN_MS = 10000;
    private static final String NOTE = "Solo aparecen redes de 2.4 GHz. Las de 5 GHz no son compatibles.";

    private final FasoresActivity act;
    private final MeterLink link;
    private final LinearLayout list;
    private final TextView btnScan, note;
    private final WifiManager wifiManager;
    private final BroadcastReceiver receiver;
    private final WiFiValidationModule validator;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean scanRequested, everScanned;
    private long lastScanMs;

    public MeterWifiTab(FasoresActivity act, View page, MeterLink link) {
        this.act = act;
        this.link = link;
        list = page.findViewById(R.id.wifiList);
        btnScan = page.findViewById(R.id.btnWifiScan);
        note = page.findViewById(R.id.tvWifiNote);
        wifiManager = (WifiManager) act.getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (scanRequested) {
                    scanRequested = false;
                    render();
                }
            }
        };
        act.registerReceiver(receiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));

        validator = new WiFiValidationModule(act, new WiFiValidationModule.ValidationListener() {
            @Override public void onValidationStarted(String ssid) { setNote("Validando «" + ssid + "»… puede tardar unos segundos."); }
            @Override public void onValidationProgress(String ssid, String progress) { setNote(progress); }
            @Override public void onValidationSuccess(WiFiValidationModule.ValidatedNetwork n) { setNote(NOTE); confirmSend(n); }
            @Override public void onValidationFailed(String ssid, String error) { setNote(NOTE); showFailure(ssid, error); }
            @Override public void onValidationTimeout(String ssid) { setNote(NOTE); showFailure(ssid, "Se agotó el tiempo de espera."); }
            @Override public void onDeviceConnectionStarted(String ssid) { }
            @Override public void onDeviceConnectionSuccess(String ssid) { }
            @Override public void onDeviceConnectionTimeout(String ssid) { }
            @Override public void onDeviceConnectionFailed(String ssid, String error) { }
            @Override public void onOriginalNetworkRestored() { setNote(NOTE); }
        });

        btnScan.setOnClickListener(v -> scan(true));
        page.findViewById(R.id.btnWifiHidden).setOnClickListener(v -> askHidden());
    }

    public void onShow() {
        if (!everScanned) scan(false);
    }

    public void onConnectionChanged(boolean connected) {
        if (connected && !everScanned && act.isTabVisible(1)) scan(false);
    }

    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { act.unregisterReceiver(receiver); } catch (Exception ignored) { }
        try { validator.cleanup(); } catch (Exception ignored) { }
    }

    private void setNote(String text) {
        note.setText(text);
    }

    // ───────────────────────── escaneo ─────────────────────────

    @SuppressLint("MissingPermission")
    private void scan(boolean userInitiated) {
        if (!link.isConnected()) {
            act.toast("Debes estar conectado al medidor");
            return;
        }
        if (userInitiated && wifiManager != null && wifiManager.isWifiEnabled()) {
            String current = wifiManager.getConnectionInfo().getSSID().replace("\"", "");
            if (!Constants.isMeterAp(current)) {
                new MaterialAlertDialogBuilder(act)
                        .setTitle("Advertencia")
                        .setMessage("No estás conectado a la red del medidor.\n\n¿Continuar?")
                        .setPositiveButton("Continuar", (d, w) -> startScan())
                        .setNegativeButton("Cancelar", null)
                        .show();
                return;
            }
        }
        startScan();
    }

    private void startScan() {
        if (wifiManager == null) return;
        everScanned = true;
        lastScanMs = System.currentTimeMillis();
        scanRequested = true;
        wifiManager.startScan();
        updateScanButton();
    }

    /** Desactiva "Buscar" hasta que se pueda escanear otra vez, con la cuenta regresiva. */
    private void updateScanButton() {
        long left = SCAN_COOLDOWN_MS - (System.currentTimeMillis() - lastScanMs);
        if (left <= 0) {
            btnScan.setEnabled(true);
            btnScan.setAlpha(1f);
            btnScan.setText("↻ Buscar");
            return;
        }
        btnScan.setEnabled(false);
        btnScan.setAlpha(0.5f);
        btnScan.setText("↻ " + (left / 1000 + 1) + " s");
        handler.postDelayed(this::updateScanButton, 1000);
    }

    // ───────────────────────── lista ─────────────────────────

    @SuppressLint("MissingPermission")
    private void render() {
        // Una entrada por SSID (el escaneo repite la red por cada antena): la más fuerte, de mejor a peor señal
        Map<String, ScanResult> bySsid = new LinkedHashMap<>();
        for (ScanResult r : wifiManager.getScanResults()) {
            if (r.SSID == null || r.SSID.isEmpty() || Constants.isMeterAp(r.SSID)) continue; // el AP del propio medidor
            if (r.frequency < 2400 || r.frequency > 2500) continue;                           // el medidor sólo hace 2.4 GHz
            ScanResult prev = bySsid.get(r.SSID);
            if (prev == null || r.level > prev.level) bySsid.put(r.SSID, r);
        }
        List<ScanResult> nets = new ArrayList<>(bySsid.values());
        Collections.sort(nets, (a, b) -> b.level - a.level);

        List<ScanResult> good = new ArrayList<>(), risky = new ArrayList<>();
        for (ScanResult r : nets) (isProtected(r) && !isDirect(r) ? good : risky).add(r);

        list.removeAllViews();
        if (nets.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("No se encontraron redes de 2.4 GHz. Toca Buscar.");
            empty.setTextColor(0xFF666666);
            empty.setTextSize(13.5f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(18), dp(40), dp(18), 0);
            list.addView(empty);
            return;
        }
        if (!good.isEmpty()) addSection("RECOMENDADAS", good, false);
        if (!risky.isEmpty()) addSection("PUEDEN FALLAR", risky, true);
    }

    private void addSection(String title, List<ScanResult> nets, boolean risky) {
        TextView h = new TextView(act, null, 0, R.style.AmSectionLabel);
        h.setText(title);
        h.setPadding(dp(18), dp(risky ? 12 : 4), dp(18), dp(8));
        list.addView(h);
        for (ScanResult r : nets) list.addView(row(r, risky));
    }

    private View row(ScanResult r, boolean risky) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(11), dp(18), dp(11));
        row.setClickable(true);
        row.setFocusable(true);
        row.setAlpha(risky ? 0.75f : 1f);
        android.util.TypedValue tv = new android.util.TypedValue();
        act.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);

        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(act);
        name.setText(r.SSID);
        name.setTextSize(15);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setTextColor(0xFF161616);
        texts.addView(name);

        int bars = bars(r.level);
        String sub;
        int subColor = 0xFF666666;
        if (isDirect(r)) { sub = "Impresora / Wi-Fi Direct"; subColor = 0xFFA32020; }
        else if (!isProtected(r)) { sub = "Abierta · puede pedir inicio de sesión"; subColor = 0xFFA32020; }
        else if (bars >= 4) sub = "Protegida · señal excelente";
        else if (bars == 3) sub = "Protegida · señal buena";
        else { sub = "Protegida · señal débil en este punto"; subColor = 0xFFB07A00; }
        TextView subtitle = new TextView(act);
        subtitle.setText(sub);
        subtitle.setTextSize(12);
        subtitle.setTextColor(subColor);
        texts.addView(subtitle);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        SignalBarsView sv = new SignalBarsView(act, null);
        sv.set(bars, risky ? 0xFF888888 : bars <= 2 ? 0xFFE0A800 : 0xFF1F9D4C);
        row.addView(sv);

        View line = new View(act);
        line.setBackgroundColor(0xFFEEEEEE);
        LinearLayout wrap = new LinearLayout(act);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        wrap.addView(row);
        row.setOnClickListener(v -> askPassword(r.SSID, isProtected(r)));
        return wrap;
    }

    private static boolean isProtected(ScanResult r) {
        String c = r.capabilities;
        return c != null && (c.contains("WPA") || c.contains("WEP") || c.contains("PSK") || c.contains("SAE"));
    }

    private static boolean isDirect(ScanResult r) {
        return r.SSID != null && r.SSID.toUpperCase().startsWith("DIRECT-");
    }

    /** RSSI en dBm a 1–4 barras. */
    private static int bars(int rssi) {
        if (rssi >= -55) return 4;
        if (rssi >= -67) return 3;
        if (rssi >= -78) return 2;
        return 1;
    }

    private int dp(int v) { return Math.round(v * act.getResources().getDisplayMetrics().density); }

    // ───────────────────────── contraseña, validación y envío ─────────────────────────

    private void askPassword(String ssid, boolean secured) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));

        final TextInputLayout layout = new TextInputLayout(act);
        layout.setHint(secured ? "Contraseña WiFi" : "Contraseña (vacío = red abierta)");
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        layout.addView(input);
        box.addView(layout);

        new MaterialAlertDialogBuilder(act)
                .setTitle("Red: " + ssid)
                .setView(box)
                .setPositiveButton("Validar", (d, w) -> submit(ssid, input.getText().toString()))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void askHidden() {
        if (!link.isConnected()) { act.toast("Debes estar conectado al medidor"); return; }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(16), dp(20), dp(8));
        final EditText ssid = new EditText(act);
        ssid.setHint("Nombre de la red (SSID)");
        ssid.setInputType(InputType.TYPE_CLASS_TEXT);
        final EditText pass = new EditText(act);
        pass.setHint("Contraseña (vacío = red abierta)");
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        box.addView(ssid);
        box.addView(pass);
        new MaterialAlertDialogBuilder(act)
                .setTitle("Red oculta")
                .setView(box)
                .setPositiveButton("Validar", (d, w) -> {
                    String s = ssid.getText().toString().trim();
                    if (s.isEmpty()) { act.toast("Escribe el nombre de la red"); return; }
                    submit(s, pass.getText().toString());
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void submit(String ssid, String password) {
        if (password.isEmpty()) {
            new MaterialAlertDialogBuilder(act)
                    .setTitle("Red sin contraseña")
                    .setMessage("¿Seguro que quieres conectar el medidor a «" + ssid + "» sin contraseña (red abierta)?")
                    .setPositiveButton("Sí, enviar", (d, w) -> send(ssid, " "))
                    .setNegativeButton("Cancelar", null)
                    .show();
        } else {
            validator.validateNetwork(ssid, password);
        }
    }

    private void confirmSend(WiFiValidationModule.ValidatedNetwork n) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("Enviar al medidor")
                .setMessage("Red validada: " + n.ssid)
                .setPositiveButton("Enviar", (d, w) -> send(n.ssid, n.password))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void send(String ssid, String password) {
        link.writeWifi(ssid, password);
        act.toast(" ".equals(password) ? "Red abierta enviada al medidor" : "Credenciales enviadas");
        // El medidor tarda en conectarse: se vuelve a leer para actualizar el aviso de WiFi sin vincular
        handler.postDelayed(link::readWifi, 3000);
        handler.postDelayed(link::readWifi, 8000);
    }

    private void showFailure(String ssid, String error) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("No se pudo validar «" + ssid + "»")
                .setMessage(error + "\n\nRevisa la contraseña, acércate al router (señal débil) y evita redes con portal de inicio de sesión.")
                .setPositiveButton("Entendido", null)
                .show();
    }
}
