package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Punto de datos de energía por mes del año (respuesta de getEnergyYear).
 */
public class EnergyYearItem {

    @SerializedName("Mes")
    private Integer mes;

    @SerializedName("EnergiaTotal")
    private Double energiaTotal;

    public int getMes()              { return mes != null ? mes : 0; }
    public double getEnergiaTotal()  { return energiaTotal != null ? energiaTotal : 0.0; }
}
