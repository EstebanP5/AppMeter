package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.kai_morich.simple_bluetooth_terminal.R;

/**
 * Configuración del medidor en sitio (sin sesión). Header con la serie, cinco pestañas y, en Fasores,
 * la barra Play / Grabar. La conexión y el protocolo viven en {@link MeterLink}; el diagnóstico en {@link FasorDiagnosis}.
 */
public class FasoresActivity extends AppCompatActivity implements MeterLink.Listener {

    private static final String[] WIRING_NAMES = {"1-Fase 4-Cargas", "2-Fases 2-Cargas", "3-Fases 1-Carga"};
    private static final String[] WIRING_TOAST = {"1PH 2W 4-LOADS", "1PH 3W 2-LOAD", "3PH 4W 1-LOAD"};
    private static final String[] ROW_LABEL = {"Voltaje", "Corriente", "Potencia", "F. potencia", "Frecuencia", "Ángulo"};
    private static final String[] ROW_UNIT = {"V", "A", "kW", "FP", "Hz", "°"};
    private static final int WHITE = 0xFFFFFFFF, RED = 0xFFFF4A4A, BLUE = 0xFF3A8BFF, GRAY = 0xFF9AA0A6;
    private static final long POLL_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MeterLink link;
    private MeterWifiTab wifiTab;
    private MeterSub1gTab sub1gTab;

    // Estado de la UI (lo que el instalador eligió; el medidor se actualiza con un write)
    private int uiWiring = 2;
    private int uiSensors = 2;
    private boolean skipSpinnerEvents;
    private boolean playing;
    private boolean hasReading;
    private boolean pendingRecordingAfterErase;
    private Runnable eraseTimeout;

    // Diagnóstico mostrado (se cambia tras 3 lecturas seguidas iguales)
    private FasorDiagnosis.Result shown;
    private String pendingKey;
    private int pendingCount;

    // Pestañas
    private View[] tabs;
    private View[] pages;
    private int currentTab = -1;

    // Vistas
    private View dotConn, dotWifi, bottomBar, bannerDiag, rowDiagLinks, btnPlay, btnRec, dotRec, phasorBox;
    private LinearLayout loadsGrid;
    private TextView tvSerial, tvOffline, tvStatusTitle, tvStatusSub, tvRecText, iconDiag, tvDiagTitle, tvDiagText, tvPhasorCaption;
    private ImageView iconPlay, imgDiagramThumb;
    private Spinner spinnerCableado, spinnerSensor;
    private LinearLayout tableCard;
    private PhasorView phasorU, phasorI;

    // Tabla eléctrica
    private TextView[][] cells;
    private int[] tableChannels = {0, 1, 2};
    // Tarjetas por carga (1F·4C)
    private final LinearLayout[] loadCard = new LinearLayout[4];
    private final View[] loadBar = new View[4];
    private final TextView[] loadI = new TextView[4], loadP = new TextView[4];
    private TextView loadsFooter;

    private final Runnable showOffline = () -> tvOffline.setVisibility(View.VISIBLE);

    private final Runnable pollTask = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            link.readCurrent();
            handler.postDelayed(this, POLL_MS);
        }
    };

    // ───────────────────────── ciclo de vida ─────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fasores);
        link = new MeterLink(this);

        bindViews();
        initInsets();
        initTabs();
        initFasoresTab();
        wifiTab = new MeterWifiTab(this, findViewById(R.id.pageWifi), link);
        sub1gTab = new MeterSub1gTab(this, findViewById(R.id.pageSub1g), link);
        initMemoriaAndEquipo();

        selectTab(0);
        buildForWiring();
        refreshHeader();
        refreshBottomBar();
        handler.postDelayed(link::connect, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!link.isConnected()) handler.postDelayed(link::connect, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playing) stopPlaying();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        wifiTab.onDestroy();
        link.close();
    }

    private void bindViews() {
        dotConn = findViewById(R.id.dotConn);
        dotWifi = findViewById(R.id.dotWifi);
        tvSerial = findViewById(R.id.tvSerial);
        tvOffline = findViewById(R.id.tvOffline);
        bottomBar = findViewById(R.id.bottomBar);
        bannerDiag = findViewById(R.id.bannerDiag);
        iconDiag = findViewById(R.id.iconDiag);
        tvDiagTitle = findViewById(R.id.tvDiagTitle);
        tvDiagText = findViewById(R.id.tvDiagText);
        rowDiagLinks = findViewById(R.id.rowDiagLinks);
        tableCard = findViewById(R.id.tableCard);
        loadsGrid = findViewById(R.id.loadsGrid);
        phasorBox = findViewById(R.id.phasorBox);
        phasorU = findViewById(R.id.phasorU);
        phasorI = findViewById(R.id.phasorI);
        tvPhasorCaption = findViewById(R.id.tvPhasorCaption);
        imgDiagramThumb = findViewById(R.id.imgDiagramThumb);
        spinnerCableado = findViewById(R.id.spinnerCableado);
        spinnerSensor = findViewById(R.id.spinnerSensor);
        tvStatusTitle = findViewById(R.id.tvStatusTitle);
        tvStatusSub = findViewById(R.id.tvStatusSub);
        btnPlay = findViewById(R.id.btnPlay);
        iconPlay = findViewById(R.id.iconPlay);
        btnRec = findViewById(R.id.btnRec);
        dotRec = findViewById(R.id.dotRec);
        tvRecText = findViewById(R.id.tvRecText);
    }

    /** Edge-to-edge: el header oscuro queda bajo la barra de estado y la barra inferior sobre la de gestos. */
    private void initInsets() {
        View root = findViewById(R.id.fasores_root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // el contenido se dibuja bajo las barras; los márgenes salen de los insets
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            bottomBar.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });
    }

    // ───────────────────────── pestañas ─────────────────────────

    private void initTabs() {
        tabs = new View[]{findViewById(R.id.tab_fasores), findViewById(R.id.tab_wifi), findViewById(R.id.tab_memoria),
                findViewById(R.id.tab_sub1g), findViewById(R.id.tab_equipo)};
        pages = new View[]{findViewById(R.id.pageFasores), findViewById(R.id.pageWifi), findViewById(R.id.pageMemoria),
                findViewById(R.id.pageSub1g), findViewById(R.id.pageEquipo)};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> selectTab(idx));
        }
    }

    boolean isTabVisible(int idx) {
        return currentTab == idx;
    }

    void selectTab(int idx) {
        if (idx == currentTab) return;
        currentTab = idx;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setSelected(i == idx);
            pages[i].setVisibility(i == idx ? View.VISIBLE : View.GONE);
        }
        bottomBar.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        if (idx != 0 && playing) stopPlaying();
        switch (idx) {
            case 1: wifiTab.onShow(); break;
            case 2: link.readRecInfo(); renderMemoria(); break;
            case 3: sub1gTab.onShow(); break;
            case 4:
                if (link.deviceId == null) link.readDeviceId();
                renderEquipo();
                break;
            default: break;
        }
    }

    // ───────────────────────── pestaña Fasores ─────────────────────────

    private void initFasoresTab() {
        ArrayAdapter<String> cab = new ArrayAdapter<>(this, R.layout.item_spinner_am, WIRING_NAMES);
        cab.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCableado.setAdapter(cab);
        ArrayAdapter<String> sen = new ArrayAdapter<>(this, R.layout.item_spinner_am, FasorDiagnosis.SENSOR_LABELS);
        sen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSensor.setAdapter(sen);
        skipSpinnerEvents = true;
        spinnerCableado.setSelection(uiWiring);
        spinnerSensor.setSelection(uiSensors - 1);
        spinnerSensor.post(() -> skipSpinnerEvents = false);

        spinnerCableado.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (skipSpinnerEvents || !link.isSynced() || pos == uiWiring) return;
                uiWiring = pos;
                resetDiagnosis();
                buildForWiring();
                render();
                pushSettings("Cableado: " + WIRING_TOAST[pos], 0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        spinnerSensor.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (skipSpinnerEvents || !link.isSynced() || pos + 1 == uiSensors) return;
                uiSensors = pos + 1;
                resetDiagnosis();
                pushSettings("Sensor: " + FasorDiagnosis.SENSOR_LABELS[pos], 0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });

        findViewById(R.id.rowDiagram).setOnClickListener(v -> showDiagram());
        findViewById(R.id.linkHow).setOnClickListener(v -> showDiagram());
        findViewById(R.id.linkSolar).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
                .setTitle("¿Tiene paneles solares?")
                .setMessage("Con paneles solares, una potencia negativa puede ser inyección a la red y no un error.\n\n"
                        + "Para descartar un sensor invertido, revisa de noche o con el inversor apagado.")
                .setPositiveButton("Entendido", null)
                .show());
        bannerDiag.setOnClickListener(v -> showProblems());
        btnPlay.setOnClickListener(v -> onPlayClick());
        btnRec.setOnClickListener(v -> onRecClick());
    }

    /** Envía cableado/sensor al medidor; si hay otra petición en curso reintenta unos segundos. */
    private void pushSettings(String toastText, int attempt) {
        if (link.writeSettings(link.recording, uiSensors, uiWiring)) {
            toast(toastText);
        } else if (attempt < 4) {
            handler.postDelayed(() -> pushSettings(toastText, attempt + 1), 600);
        } else {
            toast("No se pudo enviar el cambio. Inténtalo de nuevo.");
        }
    }

    private int rangeAmps() {
        return FasorDiagnosis.SENSOR_AMPS[Math.max(0, Math.min(uiSensors - 1, FasorDiagnosis.SENSOR_AMPS.length - 1))];
    }

    private int diagramRes() {
        switch (uiWiring) {
            case 0: return R.drawable.w1ph_2w_4loads;
            case 1: return R.drawable.w1ph_3w_2load;
            default: return R.drawable.w3ph_4w_1load;
        }
    }

    private void resetDiagnosis() {
        shown = null;
        pendingKey = null;
        pendingCount = 0;
    }

    // ───────────────────────── Play / Grabar ─────────────────────────

    private void onPlayClick() {
        if (!link.isConnected()) { toast("Sin conexión con el medidor"); return; }
        if (!link.isSynced()) { toast("Esperando sincronización…"); return; }
        if (playing) stopPlaying(); else startPlaying();
    }

    private void startPlaying() {
        playing = true;
        iconPlay.setImageResource(R.drawable.ic_am_pause);
        resetDiagnosis();
        refreshEnabled();
        link.readCurrent();
        handler.postDelayed(pollTask, POLL_MS);
    }

    private void stopPlaying() {
        playing = false;
        handler.removeCallbacks(pollTask);
        iconPlay.setImageResource(R.drawable.ic_am_play);
        refreshEnabled();
    }

    private boolean recBlocked() {
        return shown != null && shown.state == FasorDiagnosis.ERROR && !link.recording;
    }

    private void onRecClick() {
        if (!link.isConnected()) { toast("Sin conexión con el medidor"); return; }
        if (!link.isSynced()) { toast("Esperando sincronización…"); return; }
        if (recBlocked()) { toast("Corrige el problema de los sensores antes de grabar"); return; }
        if (link.recording) {
            setRecording(false);
        } else {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("Iniciar grabación")
                    .setMessage("¿Cómo quieres iniciar la grabación?")
                    .setPositiveButton("Continuar nodo", (d, w) -> setRecording(true))
                    .setNeutralButton("Nuevo nodo", (d, w) -> confirmNewNode())
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    private void confirmNewNode() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Nuevo nodo")
                .setMessage("Se borrarán TODOS los datos de la memoria del medidor antes de grabar.\n\n"
                        + "Úsalo solo en instalaciones nuevas. Esta acción no se puede deshacer.")
                .setPositiveButton("Borrar y grabar", (d, w) -> {
                    pendingRecordingAfterErase = true;
                    link.eraseMemory();
                    toast("Borrando memoria…");
                    eraseTimeout = () -> {
                        if (pendingRecordingAfterErase) {
                            pendingRecordingAfterErase = false;
                            setRecording(true);
                        }
                    };
                    handler.postDelayed(eraseTimeout, 10000);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void setRecording(boolean on) {
        if (link.writeSettings(on, uiSensors, uiWiring)) {
            link.recording = on;
            toast(on ? "Grabación iniciada" : "Grabación detenida");
            refreshBottomBar();
            link.readRecInfo();
        } else {
            toast("El medidor está ocupado, inténtalo de nuevo");
        }
    }

    // ───────────────────────── tabla, tarjetas y diagramas ─────────────────────────

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** Arma la tabla (o las tarjetas) y la miniatura según el cableado elegido. */
    private void buildForWiring() {
        imgDiagramThumb.setImageResource(diagramRes());
        tableCard.removeAllViews();
        loadsGrid.removeAllViews();
        cells = null;
        boolean loads = uiWiring == 0;
        tableCard.setVisibility(loads ? View.GONE : View.VISIBLE);
        loadsGrid.setVisibility(loads ? View.VISIBLE : View.GONE);
        if (loads) buildLoads(); else buildTable();
    }

    private void buildTable() {
        boolean three = uiWiring == 2;
        tableChannels = three ? new int[]{0, 1, 2} : new int[]{0, 2, 1, 3}; // 2F·2C: LOAD_0 = CT-0 + CT-2, LOAD_1 = CT-1 + CT-3
        int n = tableChannels.length;
        GradientDrawable divider = new GradientDrawable();
        divider.setColor(0xFFEEEEEE);
        divider.setSize(1, dp(1));
        tableCard.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        tableCard.setDividerDrawable(divider);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        View spacer = new View(this);
        spacer.setBackgroundColor(0xFFFAFAFA);
        header.addView(spacer, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.MATCH_PARENT));
        for (int c = 0; c < n; c++) {
            int ch = tableChannels[c];
            TextView h = new TextView(this);
            h.setTextColor(Color.WHITE);
            h.setTypeface(Typeface.DEFAULT_BOLD);
            h.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            h.setPadding(dp(three ? 10 : 6), dp(7), dp(three ? 10 : 6), dp(7));
            int phase = three ? ch : (ch < 2 ? 0 : 1);
            h.setBackgroundColor(phase == 0 ? 0xFF161616 : phase == 1 ? 0xFFE00000 : 0xFF1E7BFF);
            if (three) {
                h.setText("L" + (ch + 1));
                h.setTextSize(13);
            } else {
                String sub = "L" + (phase + 1) + " · Load " + ((ch == 0 || ch == 2) ? 0 : 1);
                SpannableString s = new SpannableString("CT-" + ch + "\n" + sub);
                s.setSpan(new RelativeSizeSpan(0.72f), 5, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                h.setText(s);
                h.setTextSize(13);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            if (!three && c == 1) lp.rightMargin = dp(3); // separación entre cargas
            header.addView(h, lp);
        }
        tableCard.addView(header);

        cells = new TextView[ROW_LABEL.length][n];
        for (int r = 0; r < ROW_LABEL.length; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            LinearLayout label = new LinearLayout(this);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setPadding(dp(10), dp(4), dp(6), dp(4));
            TextView name = new TextView(this);
            name.setText(ROW_LABEL[r]);
            name.setTextSize(12);
            name.setTextColor(0xFF555555);
            label.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView unit = new TextView(this);
            unit.setText(ROW_UNIT[r]);
            unit.setTextSize(10);
            unit.setTypeface(Typeface.MONOSPACE);
            unit.setTextColor(0xFF999999);
            label.addView(unit);
            row.addView(label, new LinearLayout.LayoutParams(dp(84), ViewGroup.LayoutParams.WRAP_CONTENT));

            for (int c = 0; c < n; c++) {
                TextView v = new TextView(this);
                v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                v.setTextSize(three ? 15 : 14);
                v.setTextColor(0xFF161616);
                v.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                v.setPadding(dp(three ? 10 : 6), dp(4), dp(three ? 10 : 6), dp(4));
                v.setText("--");
                cells[r][c] = v;
                row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            tableCard.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    /** 1F·4C: tarjetas 2×2 por carga con borde del color de la carga. */
    private void buildLoads() {
        int[] border = {0xFF161616, 0xFFE00000, 0xFF9AA0A6, 0xFFE00000};
        for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                int i = rowIdx * 2 + k;
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.HORIZONTAL);
                card.setBackgroundResource(R.drawable.am_card12);
                card.setClipToOutline(true);
                View bar = new View(this);
                bar.setBackgroundColor(border[i]);
                card.addView(bar, new LinearLayout.LayoutParams(dp(5), ViewGroup.LayoutParams.MATCH_PARENT));
                LinearLayout col = new LinearLayout(this);
                col.setOrientation(LinearLayout.VERTICAL);
                col.setPadding(dp(10), dp(6), dp(10), dp(6));
                TextView title = new TextView(this);
                title.setText("Load " + i + " · CT-" + i);
                title.setTextSize(12);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                title.setTextColor(0xFF161616);
                TextView amps = new TextView(this);
                amps.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                amps.setTextSize(16);
                amps.setTextColor(0xFF161616);
                TextView power = new TextView(this);
                power.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
                power.setTextSize(14);
                col.addView(title);
                col.addView(amps);
                col.addView(power);
                card.addView(col, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                if (k == 0) lp.rightMargin = dp(3); else lp.leftMargin = dp(3);
                row.addView(card, lp);
                loadCard[i] = card;
                loadBar[i] = bar;
                loadI[i] = amps;
                loadP[i] = power;
            }
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (rowIdx == 1) rlp.topMargin = dp(6);
            loadsGrid.addView(row, rlp);
        }
        loadsFooter = new TextView(this);
        loadsFooter.setTypeface(Typeface.MONOSPACE);
        loadsFooter.setTextSize(11);
        loadsFooter.setTextColor(0xFF666666);
        loadsFooter.setPadding(dp(4), dp(6), dp(4), 0);
        loadsFooter.setText("L1 · -- V · -- Hz");
        loadsGrid.addView(loadsFooter);
        for (int i = 0; i < 4; i++) { loadI[i].setText("-- A"); loadP[i].setText("-- kW"); loadP[i].setTextColor(0xFF157A3A); }
    }

    private static float pf(float v, float a, float p) {
        float s = v * a;
        if (s <= 0f) return 0f;
        return Math.max(-1f, Math.min(1f, p / s));
    }

    /** Pone los valores de la última lectura en la tabla / tarjetas. */
    private void renderValues() {
        double min = FasorDiagnosis.minCurrent(rangeAmps());
        if (uiWiring == 0) {
            for (int i = 0; i < 4; i++) {
                boolean noCurrent = link.a[i] < min;
                boolean negative = link.p[i] < 0;
                loadI[i].setText(String.format(Locale.US, "%.2f A", link.a[i]));
                loadP[i].setText(FasorDiagnosis.fmt("%.2f kW", link.p[i] / 1000.0));
                loadP[i].setTextColor(negative ? 0xFFCC0000 : noCurrent ? 0xFF7A5A00 : 0xFF157A3A);
                loadCard[i].setBackgroundResource(noCurrent ? R.drawable.am_banner_warn : R.drawable.am_card12);
                loadBar[i].setBackgroundColor(noCurrent ? 0xFFE0A800 : new int[]{0xFF161616, 0xFFE00000, 0xFF9AA0A6, 0xFFE00000}[i]);
            }
            float v = (link.v[0] + link.v[1] + link.v[2] + link.v[3]) / 4f;
            loadsFooter.setText(String.format(Locale.US, "L1 · %.1f V · %.1f Hz", v, link.hz[0]));
            return;
        }
        if (cells == null) return;
        for (int c = 0; c < tableChannels.length; c++) {
            int ch = tableChannels[c];
            float phi = FasorDiagnosis.norm(link.ang[ch]);
            float fp = pf(link.v[ch], link.a[ch], link.p[ch]);
            boolean live = link.a[ch] >= min;
            String[] text = {
                    String.format(Locale.US, "%.1f", link.v[ch]),
                    String.format(Locale.US, "%.2f", link.a[ch]),
                    FasorDiagnosis.fmt("%.2f", link.p[ch] / 1000.0),
                    FasorDiagnosis.fmt("%.2f", fp),
                    String.format(Locale.US, "%.1f", link.hz[ch]),
                    FasorDiagnosis.fmt("%.0f", phi)};
            boolean[] bad = {false, false, live && link.p[ch] < 0, live && fp < 0, false, live && Math.abs(phi) > 40f};
            for (int r = 0; r < text.length; r++) {
                cells[r][c].setText(text[r]);
                cells[r][c].setTextColor(bad[r] ? 0xFFCC0000 : 0xFF161616);
                cells[r][c].setBackgroundColor(bad[r] ? 0xFFFDECEC : Color.TRANSPARENT);
            }
        }
    }

    private void renderPhasors() {
        double min = FasorDiagnosis.minCurrent(rangeAmps());
        boolean[] bad = new boolean[4];
        if (shown != null) {
            for (FasorDiagnosis.Problem p : shown.problems) {
                if (p.type == FasorDiagnosis.Type.SIN_CORRIENTE) continue;
                for (int c : p.channels) bad[c] = true;
            }
        }
        // Cada canal tiene su propio eje, repartidos por igual: 3F → 0/120/240°, 2F → 0/180°, 4 cargas → 0/90/180/270°.
        // El voltaje va sobre su eje y la corriente en eje + φ: en una instalación sana la estrella de I copia a la de U.
        int n = uiWiring == 2 ? 3 : uiWiring == 1 ? 2 : 4;
        float maxV = 0, maxA = 0;
        for (int i = 0; i < n; i++) { maxV = Math.max(maxV, link.v[i]); maxA = Math.max(maxA, link.a[i]); }
        boolean anyCurrent = maxA >= min;

        float[] um = new float[n], im = new float[n], axis = new float[n], ia = new float[n];
        String[] lab = new String[n];
        int[] col = new int[n];
        boolean[] badN = new boolean[n];
        int[] palette = {WHITE, RED, BLUE, 0xFF3DDC84};
        for (int i = 0; i < n; i++) {
            axis[i] = i * 360f / n;
            um[i] = maxV > 0 ? link.v[i] / maxV : 0;
            im[i] = anyCurrent ? link.a[i] / maxA : 0;
            ia[i] = axis[i] + link.ang[i];
            lab[i] = "L" + (i + 1);
            col[i] = palette[i];
            badN[i] = bad[i];
        }
        phasorU.setData("U", "Voltajes", um, axis, lab, col, new boolean[n], 2.5f);
        phasorI.setData("I", "Corrientes", im, ia, lab, col, badN, 4f);

        // 4 cargas: aviso de las que no tienen corriente
        tvPhasorCaption.setVisibility(View.GONE);
        if (uiWiring == 0) {
            StringBuilder zero = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                if (link.a[i] < min) zero.append(zero.length() > 0 ? " · " : "").append("Load ").append(i).append(" ≈ 0 A");
            }
            if (zero.length() > 0) {
                SpannableString s = new SpannableString(zero);
                s.setSpan(new ForegroundColorSpan(0xFFFFCF5C), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                tvPhasorCaption.setText(s);
                tvPhasorCaption.setVisibility(View.VISIBLE);
            }
        }
    }

    // ───────────────────────── diagnóstico y barra inferior ─────────────────────────

    /** Se llama con cada lectura: recalcula todo; el diagnóstico sólo cambia tras 3 lecturas iguales. */
    private void render() {
        if (hasReading) {
            FasorDiagnosis.Result r = FasorDiagnosis.evaluate(uiWiring, link.a, link.p, link.ang, rangeAmps());
            String key = r.key();
            if (shown == null || key.equals(shown.key())) {
                shown = r;
                pendingKey = null;
            } else if (key.equals(pendingKey)) {
                if (++pendingCount >= 3) { shown = r; pendingKey = null; }
            } else {
                pendingKey = key;
                pendingCount = 1;
            }
            renderValues();
        }
        renderBanner();
        renderPhasors();
        refreshBottomBar();
    }

    private void renderBanner() {
        if (shown == null) {
            bannerDiag.setBackgroundResource(R.drawable.am_banner_idle);
            iconDiag.setBackgroundResource(R.drawable.am_icon_idle);
            iconDiag.setText("▶");
            tvDiagTitle.setText("Sin lecturas todavía");
            tvDiagTitle.setTextColor(0xFF161616);
            tvDiagText.setText("Pulsa ▶ para medir y verificar los sensores.");
            rowDiagLinks.setVisibility(View.GONE);
            return;
        }
        switch (shown.state) {
            case FasorDiagnosis.OK:
                bannerDiag.setBackgroundResource(R.drawable.am_banner_ok);
                iconDiag.setBackgroundResource(R.drawable.am_icon_ok);
                iconDiag.setText("★");
                tvDiagTitle.setText("Sensores OK");
                tvDiagTitle.setTextColor(0xFF157A3A);
                tvDiagText.setText("Cada corriente está junto a su voltaje.");
                rowDiagLinks.setVisibility(View.GONE);
                break;
            case FasorDiagnosis.ERROR:
            default:
                FasorDiagnosis.Problem p = shown.problems.get(0);
                boolean error = shown.state == FasorDiagnosis.ERROR;
                bannerDiag.setBackgroundResource(error ? R.drawable.am_banner_err : R.drawable.am_banner_warn);
                iconDiag.setBackgroundResource(error ? R.drawable.am_icon_err : R.drawable.am_icon_warn);
                iconDiag.setText(error ? "!" : "?");
                tvDiagTitle.setText(p.title);
                tvDiagTitle.setTextColor(error ? 0xFFA32020 : 0xFF7A5A00);
                int more = shown.problems.size() - 1;
                tvDiagText.setText(p.text + (more > 0 ? "\n+" + more + (more == 1 ? " problema más" : " problemas más") + " · toca para ver todos" : ""));
                rowDiagLinks.setVisibility(error ? View.VISIBLE : View.GONE);
                findViewById(R.id.linkSolar).setVisibility(p.type == FasorDiagnosis.Type.INVERTIDO ? View.VISIBLE : View.GONE);
                break;
        }
    }

    /** Lista completa de lo que detectó el diagnóstico (el banner sólo muestra el más grave). */
    private void showProblems() {
        if (shown == null || shown.problems.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (FasorDiagnosis.Problem p : shown.problems) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("• ").append(p.title).append("\n   ").append(p.text);
        }
        int n = shown.problems.size();
        new MaterialAlertDialogBuilder(this)
                .setTitle(n == 1 ? "Problema detectado" : n + " problemas detectados")
                .setMessage(sb.toString())
                .setPositiveButton("Entendido", null)
                .show();
    }

    private String memoryText() {
        OctoNetCommandEncoder.NodeRecInfo r = link.recInfo;
        if (r == null) return " ";
        return r.totalRecords == 0 ? "Memoria vacía" : r.totalRecords + " registros en memoria";
    }

    private void refreshBottomBar() {
        boolean ready = link.isConnected() && link.isSynced();
        int ink = 0xFF161616, red = 0xFFA32020;
        String title, sub;
        int color = ink;
        if (!link.isConnected()) {
            title = "Sin conexión"; sub = "Conéctate al WiFi del medidor";
        } else if (!link.isSynced()) {
            title = "Sincronizando…"; sub = "Leyendo la configuración del medidor";
        } else if (link.recording) {
            title = "Grabando"; sub = memoryText();
        } else if (shown == null) {
            title = "Pulsa ▶ para verificar"; sub = memoryText();
        } else if (shown.state == FasorDiagnosis.ERROR) {
            int n = shown.problems.size();
            title = "Corrige antes de grabar"; color = red;
            sub = n + (n == 1 ? " problema detectado" : " problemas detectados");
        } else if (shown.state == FasorDiagnosis.AVISO) {
            int n = shown.problems.size();
            title = "Puedes grabar";
            sub = n + (n == 1 ? " aviso sin resolver" : " avisos sin resolver");
        } else {
            title = "Listo para grabar"; sub = memoryText();
        }
        tvStatusTitle.setText(title);
        tvStatusTitle.setTextColor(color);
        tvStatusSub.setText(sub);

        boolean blocked = recBlocked();
        btnRec.setBackgroundResource(!ready || blocked ? R.drawable.am_rec_pill_off : R.drawable.am_rec_pill);
        tvRecText.setText(link.recording ? "Detener" : "Grabar");
        dotRec.setBackgroundResource(link.recording ? R.drawable.am_square_white : R.drawable.am_dot_white);
        if (link.recording && dotRec.getAnimation() == null) {
            AlphaAnimation blink = new AlphaAnimation(1f, 0.2f);
            blink.setDuration(600);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            dotRec.startAnimation(blink);
        } else if (!link.recording) {
            dotRec.clearAnimation();
        }
        refreshEnabled();
    }

    private void refreshEnabled() {
        boolean ready = link.isConnected() && link.isSynced();
        btnPlay.setEnabled(ready);
        btnPlay.setAlpha(ready ? 1f : 0.4f);
        spinnerCableado.setEnabled(ready && !playing);
        spinnerSensor.setEnabled(ready && !playing);
    }

    private void refreshHeader() {
        boolean c = link.isConnected();
        dotConn.setBackgroundResource(c ? R.drawable.am_dot_live : R.drawable.am_alert_dot);
        MeterLink.DeviceIdInfo id = link.deviceId;
        tvSerial.setText(id != null && !"N/A".equals(id.serial) ? id.serial : c ? "Conectado" : "Conectando…");
        updateWifiDot();
    }

    /** Punto amarillo junto a "WiFi" cuando el medidor no tiene WiFi vinculado. */
    void updateWifiDot() {
        boolean unlinked = link.wifi != null && (link.wifi.ssid == null || link.wifi.ssid.trim().isEmpty());
        dotWifi.setVisibility(unlinked ? View.VISIBLE : View.GONE);
    }

    private void showDiagram() {
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        FrameLayout f = new FrameLayout(this);
        f.setBackgroundColor(Color.BLACK);
        ZoomableImageView z = new ZoomableImageView(this);
        z.setImageResource(diagramRes());
        f.addView(z, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView close = new TextView(this);
        close.setText("Cerrar ✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(14);
        close.setTypeface(Typeface.DEFAULT_BOLD);
        close.setPadding(dp(16), dp(10), dp(16), dp(10));
        close.setBackgroundColor(0x99000000);
        close.setOnClickListener(v -> d.dismiss());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = dp(32);
        f.addView(close, lp);
        TextView hint = new TextView(this);
        hint.setText("Pellizca para hacer zoom · arrastra para mover");
        hint.setTextColor(0xFFB4B4B4);
        hint.setTextSize(11);
        hint.setPadding(dp(16), 0, 0, dp(24));
        f.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START));
        d.setContentView(f);
        d.show();
        z.resetMatrix();
    }

    // ───────────────────────── MeterLink.Listener ─────────────────────────

    @Override
    public void onConnectionChanged(boolean connected) {
        // El banner sólo aparece si sigue sin conexión tras 1.5 s: un corte breve no debe parpadear
        handler.removeCallbacks(showOffline);
        if (connected) tvOffline.setVisibility(View.GONE);
        else handler.postDelayed(showOffline, 1500);
        if (!connected && playing) stopPlaying(); // se pausa la lectura al perder el medidor
        refreshHeader();
        refreshBottomBar();
        wifiTab.onConnectionChanged(connected);
    }

    @Override
    public void onSynced() {
        refreshHeader();
        refreshBottomBar();
    }

    @Override
    public void onConfig(int sensors, int wiring, boolean recording) {
        boolean wiringChanged = wiring != uiWiring;
        uiSensors = sensors;
        uiWiring = wiring;
        skipSpinnerEvents = true;
        spinnerCableado.setSelection(wiring);
        spinnerSensor.setSelection(sensors - 1);
        // setSelection entrega el evento en el siguiente layout: soltar el guard antes lo leería como toque
        spinnerSensor.post(() -> skipSpinnerEvents = false);
        if (wiringChanged) {
            resetDiagnosis();
            buildForWiring();
        }
        render();
    }

    @Override
    public void onReading() {
        hasReading = true;
        render();
    }

    @Override
    public void onDeviceId() {
        refreshHeader();
        renderEquipo();
    }

    @Override
    public void onWifi() {
        updateWifiDot();
    }

    @Override
    public void onSub1g() {
        sub1gTab.onData();
    }

    @Override
    public void onRecInfo() {
        renderMemoria();
        refreshBottomBar();
    }

    @Override
    public void onErased() {
        toast("Memoria borrada");
        link.readRecInfo();
        if (pendingRecordingAfterErase) {
            pendingRecordingAfterErase = false;
            if (eraseTimeout != null) handler.removeCallbacks(eraseTimeout);
            setRecording(true);
        }
    }

    // ───────────────────────── pestañas Memoria y Equipo ─────────────────────────

    private void initMemoriaAndEquipo() {
        findViewById(R.id.btnMemClose).setOnClickListener(v -> selectTab(0));
        findViewById(R.id.btnMemFormat).setOnClickListener(v -> confirmFormat());
        findViewById(R.id.btnCopySerie).setOnClickListener(v -> copy("Serie", ((TextView) findViewById(R.id.tvEqSerie)).getText().toString()));
        findViewById(R.id.btnCopyAct).setOnClickListener(v -> copy("Activación", ((TextView) findViewById(R.id.tvEqAct)).getText().toString()));
        findViewById(R.id.btnShare).setOnClickListener(v -> shareDevice());
    }

    private void renderMemoria() {
        TextView used = findViewById(R.id.tvMemUsed), info = findViewById(R.id.tvMemInfo);
        OctoNetCommandEncoder.NodeRecInfo r = link.recInfo;
        if (r == null) {
            used.setText("--");
            info.setText(link.isConnected() ? "Leyendo memoria…" : "Sin conexión con el medidor.");
        } else if (r.totalRecords == 0) {
            used.setText("0");
            info.setText("Sin mediciones en memoria.");
        } else {
            used.setText(String.valueOf(r.totalRecords));
            info.setText("Registros nuevos: " + r.newRecords + "\nPrimera medición: " + r.firstDate + "  " + r.firstTime
                    + "\nÚltima medición: " + r.lastDate + "  " + r.lastTime);
        }
    }

    /** Formatear borra todo: hay que escribir BORRAR para confirmar. */
    private void confirmFormat() {
        View v = LayoutInflater.from(this).inflate(R.layout.sheet_format_confirm, null);
        final BottomSheetDialog d = new BottomSheetDialog(this);
        d.setContentView(v);
        d.setOnShowListener(x -> {
            View sheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet != null) {
                sheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior.from(sheet).setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        final EditText input = v.findViewById(R.id.etConfirm);
        final View ok = v.findViewById(R.id.btnConfirmErase);
        ok.setEnabled(false);
        ok.setAlpha(0.45f);
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                boolean match = "BORRAR".equals(s.toString().trim());
                ok.setEnabled(match);
                ok.setAlpha(match ? 1f : 0.45f);
            }
        });
        v.findViewById(R.id.btnCancelErase).setOnClickListener(x -> d.dismiss());
        ok.setOnClickListener(x -> {
            link.eraseMemory();
            link.recInfo = null;
            renderMemoria();
            toast("Borrando memoria…");
            d.dismiss();
        });
        d.show();
    }

    private void renderEquipo() {
        MeterLink.DeviceIdInfo id = link.deviceId;
        boolean c = link.isConnected();
        TextView conn = findViewById(R.id.tvEqConn);
        conn.setText(c ? "● Conectado por WiFi directo" : "● Sin conexión");
        conn.setTextColor(c ? 0xFF1F9D4C : 0xFFD23B2B);
        ((TextView) findViewById(R.id.tvEqSerie)).setText(id != null ? id.serial : "--");
        ((TextView) findViewById(R.id.tvEqAct)).setText(id != null ? id.actCode : "--");
        ((TextView) findViewById(R.id.tvEqHw)).setText(id != null ? id.hwVersion : "--");
        ((TextView) findViewById(R.id.tvEqFw)).setText(id != null ? id.fwVersion : "--");
        ((TextView) findViewById(R.id.tvEqFab)).setText(id != null ? id.facDate + " " + id.facHour : "--");
        ((TextView) findViewById(R.id.tvEqHora)).setText(link.timeSynced != null ? link.timeSynced : "--");
        ((TextView) findViewById(R.id.tvEqHoraState)).setText(link.timeSynced != null ? "sincronizada" : "");
    }

    private void copy(String label, String value) {
        if (value == null || value.equals("--")) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText(label, value));
        toast(label + " copiada");
    }

    private void shareDevice() {
        MeterLink.DeviceIdInfo id = link.deviceId;
        if (id == null) { toast("Aún no se leen los datos del equipo"); return; }
        String text = "ErgoMeter\nSerie: " + id.serial + "\nActivación: " + id.actCode + "\nHardware: " + id.hwVersion
                + "\nFirmware: " + id.fwVersion + "\nFabricación: " + id.facDate + " " + id.facHour;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Datos del ErgoMeter " + id.serial);
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "Compartir datos del equipo"));
    }

    void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
