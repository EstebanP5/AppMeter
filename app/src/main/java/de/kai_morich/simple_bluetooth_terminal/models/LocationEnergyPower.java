package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class LocationEnergyPower {

    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("Name")
    private String name;

    @SerializedName("PanelsW")
    private Double panelsW;

    @SerializedName("PanelsWhDay")
    private Double panelsWhDay;

    @SerializedName("CfeW")
    private Double cfeW;

    @SerializedName("CfeWhDay")
    private Double cfeWhDay;

    @SerializedName("LocalityW")
    private Double localityW;

    @SerializedName("HasPanels")
    private Boolean hasPanels;

    @SerializedName("HasCfe")
    private Boolean hasCfe;

    @SerializedName("HasBess")
    private Boolean hasBess;

    @SerializedName("BessW")
    private Double bessW;

    @SerializedName("BessWhDay")
    private Double bessWhDay;

    @SerializedName("IsBessCharging")
    private Boolean isBessCharging;

    @SerializedName("IsBessDischarging")
    private Boolean isBessDischarging;

    // Getters
    public Integer getIdLocation()      { return idLocation; }
    public String getName()             { return name != null ? name : "—"; }
    public double getPanelsW()          { return panelsW != null ? panelsW : 0.0; }
    public double getPanelsWhDay()      { return panelsWhDay != null ? panelsWhDay : 0.0; }
    public double getCfeW()             { return cfeW != null ? cfeW : 0.0; }
    public double getCfeWhDay()         { return cfeWhDay != null ? cfeWhDay : 0.0; }
    public double getLocalityW()        { return localityW != null ? localityW : 0.0; }
    public boolean getHasPanels()       { return hasPanels != null && hasPanels; }
    public boolean getHasCfe()          { return hasCfe != null && hasCfe; }
    public boolean getHasBess()         { return hasBess != null && hasBess; }
    public double getBessW()            { return bessW != null ? bessW : 0.0; }
    public double getBessWhDay()        { return bessWhDay != null ? bessWhDay : 0.0; }
    public boolean getIsBessCharging()  { return isBessCharging != null && isBessCharging; }
    public boolean getIsBessDischarging() { return isBessDischarging != null && isBessDischarging; }

    public double getInjectionW() {
        double p = getPanelsW();
        double l = getLocalityW();
        return p > l ? p - l : 0.0;
    }
}
