package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Diagnóstico automático de la instalación de sensores a partir del ángulo φ entre la corriente
 * y el voltaje de cada canal. Sin dependencias de Android para poder probarlo en JVM.
 *
 * Reglas (sólo si |I| supera un umbral mínimo):
 *  · |φ| ≤ 90°        → bien (se marca invertido sólo si además P &lt; 0)
 *  · 90° ≤ |φ| &lt; 150° → sensores cruzados entre fases (≈ ±120°)
 *  · |φ| ≥ 150°       → sensor invertido (≈ 180°)
 *  · |I| &lt; umbral     → sin corriente (sin carga o sensor mal cerrado)
 * Prioridad: invertido &gt; cruzado &gt; sin corriente.
 */
public final class FasorDiagnosis {

    /** Índice = valor de sensor 01h..05h − 1. */
    public static final String[] SENSOR_LABELS = {"CT-50A", "CT-200A", "CT-1000A", "RoGo-2000A", "RoGo-4000A"};
    public static final int[] SENSOR_AMPS = {50, 200, 1000, 2000, 4000};

    public enum Type { INVERTIDO, CRUZADO, SIN_CORRIENTE }

    public static final int OK = 0, AVISO = 1, ERROR = 2;

    public static final class Problem {
        public final Type type;
        public final int[] channels;
        public final String title;
        public final String text;

        Problem(Type type, int[] channels, String title, String text) {
            this.type = type;
            this.channels = channels;
            this.title = title;
            this.text = text;
        }
    }

    public static final class Result {
        public final int state;
        public final List<Problem> problems;

        Result(int state, List<Problem> problems) {
            this.state = state;
            this.problems = problems;
        }

        /** Firma estable para exigir lecturas consecutivas antes de cambiar el diagnóstico mostrado. */
        public String key() {
            StringBuilder sb = new StringBuilder().append(state);
            for (Problem p : problems) {
                sb.append('|').append(p.type);
                for (int c : p.channels) sb.append(',').append(c);
            }
            return sb.toString();
        }
    }

    private FasorDiagnosis() {}

    /** Canales que trae cada cableado: 0 = 1F·4C → 4, 1 = 2F·2C → 4 sensores, 2 = 3F·1C → 3. */
    public static int channels(int wiring) {
        return wiring == 2 ? 3 : 4;
    }

    /** Nombre del sensor de un canal: "CT-2 (L2)", "Load 1 (CT-1)"... */
    public static String sensorName(int wiring, int ch) {
        switch (wiring) {
            case 0: return "Load " + ch + " (CT-" + ch + ")";
            case 1: return "CT-" + ch + " (L" + (ch < 2 ? 1 : 2) + ")"; // CT-0/CT-1 en L1, CT-2/CT-3 en L2
            default: return "CT-" + (ch + 1) + " (L" + (ch + 1) + ")";
        }
    }

    private static String shortName(int wiring, int ch) {
        return wiring == 2 ? "CT-" + (ch + 1) : "CT-" + ch;
    }

    /** Ángulo normalizado a ±180°. */
    public static float norm(float deg) {
        while (deg > 180f) deg -= 360f;
        while (deg < -180f) deg += 360f;
        return deg;
    }

    /** Corriente mínima (A) para juzgar un canal: depende del rango del sensor. */
    public static double minCurrent(int rangeAmps) {
        return Math.max(0.05, 0.002 * rangeAmps);
    }

    public static Result evaluate(int wiring, float[] a, float[] p, float[] angDeg, int rangeAmps) {
        int n = channels(wiring);
        double min = minCurrent(rangeAmps);
        List<Integer> inverted = new ArrayList<>(), crossed = new ArrayList<>(), noCurrent = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            if (a[i] < min) { noCurrent.add(i); continue; }
            float phi = Math.abs(norm(angDeg[i]));
            if (phi >= 150f) inverted.add(i);
            else if (phi >= 90f) crossed.add(i);
            else if (p[i] < 0f) inverted.add(i);
        }

        List<Problem> out = new ArrayList<>();
        for (int c : inverted) {
            out.add(new Problem(Type.INVERTIDO, new int[]{c}, sensorName(wiring, c) + " invertido",
                    "Potencia negativa. Voltea el sensor: etiqueta «Load» hacia la carga."));
        }
        if (!crossed.isEmpty()) {
            // Dos sensores cruzados se ven a +120° y −120°: se reportan como par
            int pos = -1, neg = -1;
            for (int c : crossed) {
                if (norm(angDeg[c]) > 0) pos = c; else neg = c;
            }
            if (crossed.size() == 2 && pos >= 0 && neg >= 0) {
                out.add(new Problem(Type.CRUZADO, new int[]{pos, neg},
                        shortName(wiring, Math.min(pos, neg)) + " y " + shortName(wiring, Math.max(pos, neg)) + " cruzados",
                        "Cada sensor mide la fase de al lado. Intercámbialos."));
            } else {
                for (int c : crossed) {
                    out.add(new Problem(Type.CRUZADO, new int[]{c}, sensorName(wiring, c) + " cruzado",
                            "Está midiendo otra fase. Revisa en qué fase va el sensor."));
                }
            }
        }
        for (int c : noCurrent) {
            String name = wiring == 0 ? "Load " + c : sensorName(wiring, c);
            out.add(new Problem(Type.SIN_CORRIENTE, new int[]{c}, name + " sin corriente",
                    "Enciende esa carga o revisa que el sensor esté bien cerrado."));
        }

        int state = (!inverted.isEmpty() || !crossed.isEmpty()) ? ERROR : !noCurrent.isEmpty() ? AVISO : OK;
        return new Result(state, out);
    }

    /** "1.5" → "1.5" con signo menos tipográfico para negativos (como en el diseño). */
    public static String signed(String formatted) {
        return formatted.replace('-', '−');
    }

    public static String fmt(String f, double v) {
        return signed(String.format(Locale.US, f, v));
    }
}
