package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class PhotocellCurrentViewModel {
    @SerializedName("IdCurrent")
    private Integer idCurrent;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("CurrentDate")
    private String currentDate;

    @SerializedName("CurrentHour")
    private String currentHour;

    @SerializedName("LoadEnergyLifetime")
    private Double loadEnergyLifetime;

    @SerializedName("LoadEnergyMonth")
    private Double loadEnergyMonth;

    @SerializedName("LoadEnergyDay")
    private Double loadEnergyDay;

    @SerializedName("SourceEnergyLifetime")
    private Double sourceEnergyLifetime;

    @SerializedName("SourceEnergyMonth")
    private Double sourceEnergyMonth;

    @SerializedName("SourceEnergyDay")
    private Double sourceEnergyDay;

    @SerializedName("Power")
    private Double power;

    @SerializedName("ActivePower")
    private Double activePower;

    @SerializedName("ReactivePower")
    private Double reactivePower;

    @SerializedName("Voltage")
    private Double voltage;

    @SerializedName("ElectricCurrent")
    private Double electricCurrent;

    @SerializedName("Frequency")
    private Double frequency;

    @SerializedName("PF")
    private Double pf;

    @SerializedName("AlsCH0")
    private Integer alsCH0;

    @SerializedName("AlsCH1")
    private Integer alsCH1;

    @SerializedName("Dimmer")
    private Integer dimmer;

    @SerializedName("Temperature")
    private Double temperature;

    @SerializedName("LampStatusOnOff")
    private Boolean lampStatusOnOff;

    @SerializedName("PvStatusOnOff")
    private Boolean pvStatusOnOff;

    @SerializedName("MeterType")
    private Integer meterType;

    // Getters
    public Integer getIdCurrent() { return idCurrent; }
    public String getSerie() { return serie; }
    public String getCurrentDate() { return currentDate; }
    public String getCurrentHour() { return currentHour; }
    public Double getLoadEnergyLifetime() { return loadEnergyLifetime; }
    public Double getLoadEnergyMonth() { return loadEnergyMonth; }
    public Double getLoadEnergyDay() { return loadEnergyDay; }
    public Double getSourceEnergyLifetime() { return sourceEnergyLifetime; }
    public Double getSourceEnergyMonth() { return sourceEnergyMonth; }
    public Double getSourceEnergyDay() { return sourceEnergyDay; }
    public Double getPower() { return power; }
    public Double getActivePower() { return activePower; }
    public Double getReactivePower() { return reactivePower; }
    public Double getVoltage() { return voltage; }
    public Double getElectricCurrent() { return electricCurrent; }
    public Double getFrequency() { return frequency; }
    public Double getPf() { return pf; }
    public Integer getAlsCH0() { return alsCH0; }
    public Integer getAlsCH1() { return alsCH1; }
    public Integer getDimmer() { return dimmer; }
    public Double getTemperature() { return temperature; }
    public Boolean getLampStatusOnOff() { return lampStatusOnOff; }
    public Boolean getPvStatusOnOff() { return pvStatusOnOff; }
    public Integer getMeterType() { return meterType; }

    // Setters (implementar según necesidad)
    public void setIdCurrent(Integer idCurrent) { this.idCurrent = idCurrent; }
    public void setSerie(String serie) { this.serie = serie; }
    // ... resto de setters
}