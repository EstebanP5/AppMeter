package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class MeterCurrentViewModel {
    @SerializedName("IdCurrent")
    private Integer idCurrent;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("DateLastConnection")
    private String dateLastConnection;

    @SerializedName("HourLastConnection")
    private String hourLastConnection;

    @SerializedName("MeterType")
    private Integer meterType;

    @SerializedName("ConPower")
    private Double conPower;

    @SerializedName("ConEnergyDay")
    private Double conEnergyDay;

    @SerializedName("ConEnergyMonth")
    private Double conEnergyMonth;

    @SerializedName("ConEnergyLifetime")
    private Double conEnergyLifetime;

    @SerializedName("GenPower")
    private Double genPower;

    @SerializedName("GenEnergyDay")
    private Double genEnergyDay;

    @SerializedName("GenEnergyMonth")
    private Double genEnergyMonth;

    @SerializedName("GenEnergyLifetime")
    private Double genEnergyLifetime;

    @SerializedName("Name")
    private String name;

    // Getters
    public Integer getIdCurrent() { return idCurrent; }
    public String getSerie() { return serie; }
    public String getDateLastConnection() { return dateLastConnection; }
    public String getHourLastConnection() { return hourLastConnection; }
    public Integer getMeterType() { return meterType; }
    public Double getConPower() { return conPower; }
    public Double getConEnergyDay() { return conEnergyDay; }
    public Double getConEnergyMonth() { return conEnergyMonth; }
    public Double getConEnergyLifetime() { return conEnergyLifetime; }
    public Double getGenPower() { return genPower; }
    public Double getGenEnergyDay() { return genEnergyDay; }
    public Double getGenEnergyMonth() { return genEnergyMonth; }
    public Double getGenEnergyLifetime() { return genEnergyLifetime; }
    public String getName() { return name; }

    // Setters
    public void setIdCurrent(Integer idCurrent) { this.idCurrent = idCurrent; }
    public void setSerie(String serie) { this.serie = serie; }
    public void setDateLastConnection(String dateLastConnection) { this.dateLastConnection = dateLastConnection; }
    public void setHourLastConnection(String hourLastConnection) { this.hourLastConnection = hourLastConnection; }
    public void setMeterType(Integer meterType) { this.meterType = meterType; }
    public void setConPower(Double conPower) { this.conPower = conPower; }
    public void setConEnergyDay(Double conEnergyDay) { this.conEnergyDay = conEnergyDay; }
    public void setConEnergyMonth(Double conEnergyMonth) { this.conEnergyMonth = conEnergyMonth; }
    public void setConEnergyLifetime(Double conEnergyLifetime) { this.conEnergyLifetime = conEnergyLifetime; }
    public void setGenPower(Double genPower) { this.genPower = genPower; }
    public void setGenEnergyDay(Double genEnergyDay) { this.genEnergyDay = genEnergyDay; }
    public void setGenEnergyMonth(Double genEnergyMonth) { this.genEnergyMonth = genEnergyMonth; }
    public void setGenEnergyLifetime(Double genEnergyLifetime) { this.genEnergyLifetime = genEnergyLifetime; }
    public void setName(String name) { this.name = name; }
}