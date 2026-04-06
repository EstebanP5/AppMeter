package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Punto de datos de energía por día del mes (respuesta de getEnergyMonth).
 */
public class EnergyMonthItem {

    @SerializedName("Dia")
    private Integer dia;

    @SerializedName("EnergiaTotal")
    private Double energiaTotal;

    public int getDia()              { return dia != null ? dia : 0; }
    public double getEnergiaTotal()  { return energiaTotal != null ? energiaTotal : 0.0; }
}
