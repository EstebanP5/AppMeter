package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class EnergyFlowGraphViewModel {

    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

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

    @SerializedName("IsInjectingCfe")
    private Boolean isInjectingCfe;

    @SerializedName("InyectionW")
    private Double inyectionW;

    @SerializedName("InyectionWhDay")
    private Double inyectionWhDay;

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

    @SerializedName("BessFromCfeW")
    private Double bessFromCfeW;

    @SerializedName("BessToFactoryW")
    private Double bessToFactoryW;

    @SerializedName("BessToCfeW")
    private Double bessToCfeW;

    @SerializedName("BessFromCfeWhDay")
    private Double bessFromCfeWhDay;

    @SerializedName("BessToFactoryWhDay")
    private Double bessToFactoryWhDay;

    @SerializedName("BessToCfeWhDay")
    private Double bessToCfeWhDay;

    public Integer getIdCircuit() { return idCircuit; }
    public Integer getIdCustomer() { return idCustomer; }
    public Double getPanelsW() { return panelsW; }
    public Double getPanelsWhDay() { return panelsWhDay; }
    public Double getCfeW() { return cfeW; }
    public Double getCfeWhDay() { return cfeWhDay; }
    public Double getLocalityW() { return localityW; }
    public Boolean getHasPanels() { return hasPanels; }
    public Boolean getHasCfe() { return hasCfe; }
    public Boolean getIsInjectingCfe() { return isInjectingCfe; }
    public Double getInyectionW() { return inyectionW; }
    public Double getInyectionWhDay() { return inyectionWhDay; }
    public Boolean getHasBess() { return hasBess; }
    public Double getBessW() { return bessW; }
    public Double getBessWhDay() { return bessWhDay; }
    public Boolean getIsBessCharging() { return isBessCharging; }
    public Boolean getIsBessDischarging() { return isBessDischarging; }
    public Double getBessFromCfeW() { return bessFromCfeW; }
    public Double getBessToFactoryW() { return bessToFactoryW; }
    public Double getBessToCfeW() { return bessToCfeW; }
    public Double getBessFromCfeWhDay() { return bessFromCfeWhDay; }
    public Double getBessToFactoryWhDay() { return bessToFactoryWhDay; }
    public Double getBessToCfeWhDay() { return bessToCfeWhDay; }
}
