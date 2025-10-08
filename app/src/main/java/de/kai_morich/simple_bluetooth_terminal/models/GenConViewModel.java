package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class GenConViewModel {
    @SerializedName("Generacion")
    private Double generacion;

    @SerializedName("Consumo")
    private Double consumo;

    // Getters
    public Double getGeneracion() { return generacion; }
    public Double getConsumo() { return consumo; }

    // Setters
    public void setGeneracion(Double generacion) { this.generacion = generacion; }
    public void setConsumo(Double consumo) { this.consumo = consumo; }
}