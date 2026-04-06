package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * Respuesta de getSmartSunLiteEnergyDay / getBessEnergyDay.
 * Formato: {"Generation":{"0":val,...,"23":val},"Consumption":{"0":val,...,"23":val}}
 */
public class EnergyDayResponse {

    @SerializedName("Generation")
    private Map<String, Double> generation;

    @SerializedName("Consumption")
    private Map<String, Double> consumption;

    public Map<String, Double> getGeneration() { return generation; }
    public Map<String, Double> getConsumption() { return consumption; }
}
