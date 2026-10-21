package de.kai_morich.simple_bluetooth_terminal.ui.admin;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import de.kai_morich.simple_bluetooth_terminal.R;
import de.kai_morich.simple_bluetooth_terminal.api.ApiClient;
import de.kai_morich.simple_bluetooth_terminal.api.ApiService;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLight;
import de.kai_morich.simple_bluetooth_terminal.models.NodeTrafficLightResponse;
import de.kai_morich.simple_bluetooth_terminal.util.NodeKind;
import de.kai_morich.simple_bluetooth_terminal.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * A3 · A4 · A5 · Nodo: encabezado con el estado en vivo y tres pestañas (Ahora, Potencia día, Energía día), cada una un
 * fragmento que recibe los mismos argumentos. Extras: idMeasure, idCustomer, idCircuit, idLocation, nodeName, nodeType,
 * nodeTypeName, nodeSerie, nodeLastDate, nodeLastHour.
 */
public class NodoActivity extends AppCompatActivity {

    private static final long REFRESH_MS = 30_000L;
    private static final String KEY_TAB = "tab";

    private ApiService api;
    private int idNode, idCustomer, tab;
    private TextView tvNodeStatus;
    private View[] tabs;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refreshStatus();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_nodo);
        api = ApiClient.getApiService();
        Bundle extras = getIntent().getExtras() != null ? getIntent().getExtras() : new Bundle();
        idNode = extras.getInt("idMeasure", 0);
        idCustomer = extras.getInt("idCustomer", 0);

        String name = extras.getString("nodeName");
        ((TextView) findViewById(R.id.tvNodeName)).setText(name != null ? name : "Nodo");
        String typeName = extras.getString("nodeTypeName");
        String serie = extras.getString("nodeSerie");
        TextView chip = findViewById(R.id.tvNodeChip);
        String type = typeName != null && !typeName.isEmpty() ? typeName : "NODO";
        chip.setText(type + (serie != null && !serie.trim().isEmpty() ? " · " + serie : ""));
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvNodeStatus = findViewById(R.id.tvNodeStatus);
        paintStatus(extras.getString("nodeLastDate"), extras.getString("nodeLastHour"), null);
        initInsets();

        tabs = new View[]{findViewById(R.id.tabAhora), findViewById(R.id.tabPotencia), findViewById(R.id.tabEnergia)};
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(v -> openTab(idx));
        }
        if (savedInstanceState == null) {
            openTab(0);
        } else {
            tab = savedInstanceState.getInt(KEY_TAB, 0);
            mark(tab); // el fragmento ya lo restauró el sistema
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(KEY_TAB, tab);
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }

    private void initInsets() {
        View root = findViewById(R.id.nodo_root);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false); // el contenido se dibuja bajo las barras; los márgenes salen de los insets
        WindowCompat.getInsetsController(getWindow(), root).setAppearanceLightStatusBars(false);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    /** 0 Ahora · 1 Potencia día · 2 Energía día. Lo usa también el enlace "Ver día ›" de Ahora. */
    public void openTab(int idx) {
        tab = idx;
        mark(idx);
        Fragment f = idx == 0 ? new NodoAhoraFragment() : idx == 1 ? new NodoPotenciaFragment() : new NodoEnergiaFragment();
        Bundle args = new Bundle(getIntent().getExtras() != null ? getIntent().getExtras() : new Bundle());
        f.setArguments(args);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.am_fade_in, R.anim.am_fade_out)
                .replace(R.id.nodo_container, f)
                .commit();
    }

    private void mark(int idx) {
        for (int i = 0; i < tabs.length; i++) tabs[i].setSelected(i == idx);
    }

    // ───────────────────────── estado en vivo ─────────────────────────

    private void refreshStatus() {
        if (idCustomer == 0) return;
        api.getCustomerNodesTrafficLight(idCustomer).enqueue(new Callback<NodeTrafficLightResponse>() {
            @Override public void onResponse(Call<NodeTrafficLightResponse> c, Response<NodeTrafficLightResponse> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null || r.body().getNodes() == null) return;
                for (NodeTrafficLight n : r.body().getNodes()) {
                    if (n.getIdNode() != null && n.getIdNode() == idNode) {
                        paintStatus(n.getLastDate(), n.getLastHour(), n.isGreen());
                        break;
                    }
                }
            }
            @Override public void onFailure(Call<NodeTrafficLightResponse> c, Throwable t) { }
        });
    }

    /** "● en línea · hace 54 s" (verde) o "● sin reportar · hace 3 d" (rojo). online = null: se deduce del tiempo. */
    private void paintStatus(String lastDate, String lastHour, Boolean online) {
        if (lastDate == null) return;
        long ts = RelativeTime.parse(lastDate, lastHour);
        boolean on = online != null ? online : ts > 0 && System.currentTimeMillis() - ts < 5 * 60_000L;
        tvNodeStatus.setText("● " + (on ? "en línea" : "sin reportar") + " · " + RelativeTime.ago(lastDate, lastHour));
        tvNodeStatus.setTextColor(on ? 0xFF3DDC84 : 0xFFFF7A6B);
    }

    /** Tipo legible para el chip cuando el semáforo no trae TypeName. */
    static String kindLabel(int type) {
        int k = NodeKind.of(type, null);
        return k == NodeKind.SOLAR ? "PV" : k == NodeKind.CFE ? "CFE" : k == NodeKind.BESS ? "BESS" : "LOAD";
    }
}
