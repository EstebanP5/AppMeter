package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import de.kai_morich.simple_bluetooth_terminal.R;

/**
 * Pestaña Sub-1G (E4h / Tabla 23). La velocidad cambia según la modulación: se muestra sólo el campo
 * que aplica (GFKS_BR o LORA_BR) y el otro se conserva tal como lo reportó el equipo.
 */
public class MeterSub1gTab {

    private static final String[] HOP = {"0–4", "10–14", "20–24"};
    private static final String[] GFSK_RATE = {
            "38.4 kbps · BW 166.2 kHz", "4.8 kbps · BW 23.4 kHz", "1.2 kbps · BW 19.5 kHz", "0.6 kbps · BW 4.8 kHz"};
    private static final String[] LORA_RATE = {
            "31.25 kbps · BW 250 kHz", "10.94 kbps · BW 250 kHz", "3.52 kbps · BW 250 kHz",
            "15.63 kbps · BW 125 kHz", "5.47 kbps · BW 125 kHz", "1.76 kbps · BW 125 kHz"};

    private final FasoresActivity act;
    private final MeterLink link;
    private final TextView segGfsk, segLora, tvRateLabel, tvWarn, tvAdvArrow;
    private final Spinner spRate, spHop;
    private final EditText etChannel, etBroadcast, etUnicast;
    private final View groupAdvanced;

    private int modem;        // 0 = GFSK, 1 = LoRa
    private int gfskIdx, loraIdx;
    private boolean loading;  // evita que llenar el formulario cuente como un cambio del usuario

    public MeterSub1gTab(FasoresActivity act, View page, MeterLink link) {
        this.act = act;
        this.link = link;
        segGfsk = page.findViewById(R.id.segGfsk);
        segLora = page.findViewById(R.id.segLora);
        tvRateLabel = page.findViewById(R.id.tvRateLabel);
        tvWarn = page.findViewById(R.id.tvSub1gWarn);
        tvAdvArrow = page.findViewById(R.id.tvAdvArrow);
        spRate = page.findViewById(R.id.spSub1gRate);
        spHop = page.findViewById(R.id.spSub1gHop);
        etChannel = page.findViewById(R.id.etSub1gChannel);
        etBroadcast = page.findViewById(R.id.etSub1gBroadcast);
        etUnicast = page.findViewById(R.id.etSub1gUnicast);
        groupAdvanced = page.findViewById(R.id.groupAdvanced);

        spHop.setAdapter(adapter(HOP));
        segGfsk.setOnClickListener(v -> setModem(0));
        segLora.setOnClickListener(v -> setModem(1));
        spRate.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (loading) return;
                if (modem == 0) gfskIdx = pos; else loraIdx = pos;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        page.findViewById(R.id.rowAdvanced).setOnClickListener(v -> {
            boolean open = groupAdvanced.getVisibility() != View.VISIBLE;
            groupAdvanced.setVisibility(open ? View.VISIBLE : View.GONE);
            tvAdvArrow.setText(open ? "▴" : "▾");
        });
        page.findViewById(R.id.btnSubCancel).setOnClickListener(v -> fill());
        page.findViewById(R.id.btnSubSend).setOnClickListener(v -> send());
        fill();
    }

    private ArrayAdapter<String> adapter(String[] items) {
        ArrayAdapter<String> a = new ArrayAdapter<>(act, R.layout.item_spinner_am, items);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    public void onShow() {
        if (link.sub1g == null) {
            if (link.isConnected()) link.readSub1g();
        }
        fill();
    }

    /** Llegó la configuración del equipo: se refleja en el formulario. */
    public void onData() {
        fill();
    }

    /** Carga el formulario con lo que reporta el equipo (o ceros si aún no reporta nada). */
    private void fill() {
        OctoNetCommandEncoder.Sub1gSettings s = link.sub1g;
        tvWarn.setVisibility(s == null ? View.VISIBLE : View.GONE);
        loading = true;
        modem = s != null ? s.modem : 0;
        gfskIdx = s != null ? s.gfskBr : 0;
        loraIdx = s != null ? s.loraBr : 0;
        etChannel.setText(String.valueOf(s != null ? s.channel : 0));
        etBroadcast.setText(String.valueOf(s != null ? s.broadcastAddr : 0));
        etUnicast.setText(String.valueOf(s != null ? s.unicastAddr : 0));
        spHop.setSelection(s != null && s.hopSchema >= 0 && s.hopSchema < HOP.length ? s.hopSchema : 0);
        applyModem();
        loading = false;
    }

    private void setModem(int m) {
        if (m == modem) return;
        modem = m;
        loading = true;
        applyModem();
        loading = false;
    }

    private void applyModem() {
        boolean lora = modem == 1;
        segGfsk.setBackgroundResource(lora ? 0 : R.drawable.am_seg_on);
        segLora.setBackgroundResource(lora ? R.drawable.am_seg_on : 0);
        segGfsk.setTextColor(ContextCompat.getColor(act, lora ? R.color.am_t666 : R.color.am_ink));
        segLora.setTextColor(ContextCompat.getColor(act, lora ? R.color.am_ink : R.color.am_t666));
        tvRateLabel.setText(lora ? "Velocidad LoRa" : "Velocidad GFSK");
        String[] rates = lora ? LORA_RATE : GFSK_RATE;
        spRate.setAdapter(adapter(rates));
        int idx = lora ? loraIdx : gfskIdx;
        spRate.setSelection(idx >= 0 && idx < rates.length ? idx : 0);
    }

    private void send() {
        if (!link.isConnected()) { act.toast("Sin conexión con el medidor"); return; }
        int channel = parseByte(etChannel), bcast = parseByte(etBroadcast), ucast = parseByte(etUnicast);
        link.writeSub1g(modem, channel, spHop.getSelectedItemPosition(), bcast, ucast, gfskIdx, loraIdx);
        act.toast("Configuración Sub-1G enviada");
    }

    /** Valor de 0 a 255 (un byte); lo inválido se toma como 0. */
    private static int parseByte(EditText et) {
        try {
            return Math.max(0, Math.min(255, Integer.parseInt(et.getText().toString().trim())));
        } catch (Exception e) {
            return 0;
        }
    }
}
