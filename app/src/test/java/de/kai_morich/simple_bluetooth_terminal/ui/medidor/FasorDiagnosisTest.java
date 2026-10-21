package de.kai_morich.simple_bluetooth_terminal.ui.medidor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FasorDiagnosisTest {

    private static FasorDiagnosis.Result eval(int wiring, float[] a, float[] p, float[] ang) {
        return FasorDiagnosis.evaluate(wiring, a, p, ang, 200);
    }

    @Test
    public void trifasicoBienEsOk() {
        FasorDiagnosis.Result r = eval(2, new float[]{9.4f, 9.4f, 9.5f, 0}, new float[]{1180, 1170, 1200, 0}, new float[]{9, 11, 8, 0});
        assertEquals(FasorDiagnosis.OK, r.state);
        assertTrue(r.problems.isEmpty());
    }

    @Test
    public void ct2InvertidoEn2F2C() {
        // CT-2 (L2) con φ ≈ 172° y potencia negativa
        FasorDiagnosis.Result r = eval(1, new float[]{4.1f, 3.9f, 3.95f, 4.0f}, new float[]{520, 500, -490, 510}, new float[]{9, 10, 172, 8});
        assertEquals(FasorDiagnosis.ERROR, r.state);
        assertEquals("CT-2 (L2) invertido", r.problems.get(0).title);
    }

    @Test
    public void ct2YCt3Cruzados() {
        FasorDiagnosis.Result r = eval(2, new float[]{9, 9, 9, 0}, new float[]{1100, -500, -500, 0}, new float[]{8, 128, -128, 0});
        assertEquals(FasorDiagnosis.ERROR, r.state);
        assertEquals("CT-2 y CT-3 cruzados", r.problems.get(0).title);
    }

    @Test
    public void load3SinCorrienteEsAviso() {
        FasorDiagnosis.Result r = eval(0, new float[]{6.2f, 3.1f, 2.45f, 0.02f}, new float[]{780, 390, 300, 0}, new float[]{11, 32, -23, 18});
        assertEquals(FasorDiagnosis.AVISO, r.state);
        assertEquals("Load 3 sin corriente", r.problems.get(0).title);
    }

    @Test
    public void invertidoTienePrioridadSobreSinCorriente() {
        FasorDiagnosis.Result r = eval(2, new float[]{9, 9, 0.0f, 0}, new float[]{-1100, 1100, 0, 0}, new float[]{178, 9, 0, 0});
        assertEquals(FasorDiagnosis.ERROR, r.state);
        assertEquals(FasorDiagnosis.Type.INVERTIDO, r.problems.get(0).type);
    }
}
