package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Punto de datos de energía por hora (respuesta de getEnergyDay).
 */
public class EnergyDayItem {

    @SerializedName("Hora")
    private String hora;

    @SerializedName("EnergiaTotal")
    private Double energiaTotal;

    public String getHora()          { return hora != null ? hora : ""; }
    public double getEnergiaTotal()  { return energiaTotal != null ? energiaTotal : 0.0; }
}
