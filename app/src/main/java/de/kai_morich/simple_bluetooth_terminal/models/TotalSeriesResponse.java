package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Respuesta de getTotalPowerDay (kW por hora, 24 valores) y getTotalEnergyMonth (kWh por día, índice 0 = día 1).
 * Cada serie es un objeto {"0": v, "1": v, ...}. Generation viene NEGATIVA cuando hay generación solar.
 */
public class TotalSeriesResponse {

    @SerializedName("Hours")
    private Integer hours;

    @SerializedName("Days")
    private Integer days;

    @SerializedName("Generation")
    private Map<String, Double> generation;

    @SerializedName("GridConsumption")
    private Map<String, Double> gridConsumption;

    @SerializedName("Consumption")
    private Map<String, Double> consumption;

    @SerializedName("Inyection")
    private Map<String, Double> inyection;

    public Integer getHours() { return hours; }
    public Integer getDays() { return days; }
    public Map<String, Double> getGeneration() { return generation; }
    public Map<String, Double> getGridConsumption() { return gridConsumption; }
    public Map<String, Double> getConsumption() { return consumption; }
    public Map<String, Double> getInyection() { return inyection; }
}
