package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Punto de datos de potencia por hora (respuesta de getPowerDay).
 * Los valores llegan en décimas de W → dividir entre 10 para W reales.
 */
public class PowerDayItem {

    @SerializedName("Hora")
    private String hora;

    @SerializedName("PotenciaC1")
    private Double potenciaC1;

    @SerializedName("PotenciaC2")
    private Double potenciaC2;

    @SerializedName("PotenciaC3")
    private Double potenciaC3;

    @SerializedName("PotenciaG1")
    private Double potenciaG1;

    @SerializedName("PotenciaG2")
    private Double potenciaG2;

    @SerializedName("PotenciaG3")
    private Double potenciaG3;

    public String getHora() { return hora != null ? hora : ""; }

    public double getPotenciaC1() { return potenciaC1 != null ? potenciaC1 / 10.0 : 0.0; }
    public double getPotenciaC2() { return potenciaC2 != null ? potenciaC2 / 10.0 : 0.0; }
    public double getPotenciaC3() { return potenciaC3 != null ? potenciaC3 / 10.0 : 0.0; }

    public double getPotenciaG1() { return potenciaG1 != null ? potenciaG1 / 10.0 : 0.0; }
    public double getPotenciaG2() { return potenciaG2 != null ? potenciaG2 / 10.0 : 0.0; }
    public double getPotenciaG3() { return potenciaG3 != null ? potenciaG3 / 10.0 : 0.0; }

    /** Consumo total = C1 + C2 + C3 (ya escalado) */
    public double getConsumoTotal() {
        return getPotenciaC1() + getPotenciaC2() + getPotenciaC3();
    }

    /** Generación total = G1 + G2 + G3 (ya escalado) */
    public double getGeneracionTotal() {
        return getPotenciaG1() + getPotenciaG2() + getPotenciaG3();
    }
}
