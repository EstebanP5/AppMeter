package de.kai_morich.simple_bluetooth_terminal.ui.cliente;

import androidx.lifecycle.ViewModel;

/**
 * Datos de la pantalla K5 (5 minutales). Vive mientras exista la pantalla: al girar el teléfono el fragmento se
 * vuelve a crear y recupera de aquí las series, el día, el marcador y los chips sin volver a pedir nada.
 */
public class TecnicosViewModel extends ViewModel {

    /** 0 = hoy, 1 = ayer... */
    int offset;
    /** Series por fase (kW, V, A, FP) y energía de cada 5 min (kWh); null = no llegó / no existe. */
    double[][] kw, volt, amp, pf;
    double[] kwh;
    int pending;      // llamadas en vuelo
    int generation;   // descarta respuestas de otro día
    boolean requested, anyData;
    int marker = -1;
    boolean[] on = {true, true, true};
    boolean pfOn;

    /** El fragmento actual pide repintar cuando llega algo (null si no hay pantalla). */
    Runnable onChange;

    void reset() {
        kw = volt = amp = pf = null;
        kwh = null;
        anyData = false;
        marker = -1;
    }
}
