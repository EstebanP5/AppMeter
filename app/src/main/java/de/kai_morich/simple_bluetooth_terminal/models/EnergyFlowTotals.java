package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class EnergyFlowTotals {

    @SerializedName("PanelsW")
    private Double panelsW;

    @SerializedName("CfeW")
    private Double cfeW;

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

    @SerializedName("IsBessCharging")
    private Boolean isBessCharging;

    @SerializedName("IsBessDischarging")
    private Boolean isBessDischarging;

    // Getters with null-safe defaults
    public double getPanelsW()   { return panelsW   != null ? panelsW   : 0.0; }
    public double getCfeW()      { return cfeW      != null ? cfeW      : 0.0; }
    public double getLocalityW() { return localityW != null ? localityW : 0.0; }
    public boolean getHasPanels()          { return hasPanels          != null && hasPanels; }
    public boolean getHasCfe()             { return hasCfe             != null && hasCfe; }
    public boolean getHasBess()            { return hasBess            != null && hasBess; }
    public double getBessW()               { return bessW              != null ? bessW : 0.0; }
    public boolean getIsBessCharging()     { return isBessCharging     != null && isBessCharging; }
    public boolean getIsBessDischarging()  { return isBessDischarging  != null && isBessDischarging; }

    /** Inyección = PanelsW − LocalityW cuando PanelsW > LocalityW, sino 0 */
    public double getInjectionW() {
        double p = getPanelsW();
        double l = getLocalityW();
        return p > l ? p - l : 0.0;
    }
}
