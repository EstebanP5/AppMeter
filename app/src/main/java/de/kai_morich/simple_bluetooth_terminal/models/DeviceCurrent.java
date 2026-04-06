package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Medición en tiempo real de un dispositivo (respuesta detallada).
 * Usada en AdminMedicionesActivity BottomSheet.
 */
public class DeviceCurrent {

    @SerializedName("IdMeasure")
    private Integer idMeasure;

    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("Name")
    private String name;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("CurrentDate")
    private String currentDate;

    @SerializedName("CurrentHour")
    private String currentHour;

    @SerializedName("RecordType")
    private Integer recordType;

    // Potencias
    @SerializedName("ConW")
    private Double conW;

    @SerializedName("GenW")
    private Double genW;

    // Energía
    @SerializedName("ConWhDay")
    private Double conWhDay;

    @SerializedName("ConWhMonth")
    private Double conWhMonth;

    @SerializedName("ConWhLifetime")
    private Double conWhLifetime;

    @SerializedName("GenWhDay")
    private Double genWhDay;

    @SerializedName("GenWhMonth")
    private Double genWhMonth;

    @SerializedName("GenWhLifetime")
    private Double genWhLifetime;

    // Voltajes
    @SerializedName("V1")
    private Double v1;

    @SerializedName("V2")
    private Double v2;

    @SerializedName("V3")
    private Double v3;

    // Corrientes
    @SerializedName("I1")
    private Double i1;

    @SerializedName("I2")
    private Double i2;

    @SerializedName("I3")
    private Double i3;

    // Factor de potencia
    @SerializedName("Pf1")
    private Double pf1;

    @SerializedName("Pf2")
    private Double pf2;

    @SerializedName("Pf3")
    private Double pf3;

    // Frecuencia
    @SerializedName("Hz1")
    private Double hz1;

    @SerializedName("Hz2")
    private Double hz2;

    @SerializedName("Hz3")
    private Double hz3;

    // Potencias por fase
    @SerializedName("W1")
    private Double w1;

    @SerializedName("W2")
    private Double w2;

    @SerializedName("W3")
    private Double w3;

    @SerializedName("Type")
    private Integer type;

    // -----------------------------------------------
    // Getters con valores seguros por defecto
    // -----------------------------------------------

    public String getName()        { return name  != null ? name  : "—"; }
    public String getSerie()       { return serie != null ? serie : "—"; }
    public String getCurrentDate() { return currentDate != null ? currentDate : "—"; }
    public String getCurrentHour() { return currentHour != null ? currentHour : "—"; }

    public double getConW()          { return conW          != null ? conW          : 0.0; }
    public double getGenW()          { return genW          != null ? genW          : 0.0; }
    public double getConWhDay()      { return conWhDay      != null ? conWhDay      : 0.0; }
    public double getConWhMonth()    { return conWhMonth    != null ? conWhMonth    : 0.0; }
    public double getConWhLifetime() { return conWhLifetime != null ? conWhLifetime : 0.0; }
    public double getGenWhDay()      { return genWhDay      != null ? genWhDay      : 0.0; }
    public double getGenWhMonth()    { return genWhMonth    != null ? genWhMonth    : 0.0; }
    public double getGenWhLifetime() { return genWhLifetime != null ? genWhLifetime : 0.0; }

    public double getV1() { return v1 != null ? v1 : 0.0; }
    public double getV2() { return v2 != null ? v2 : 0.0; }
    public double getV3() { return v3 != null ? v3 : 0.0; }

    public double getI1() { return i1 != null ? i1 : 0.0; }
    public double getI2() { return i2 != null ? i2 : 0.0; }
    public double getI3() { return i3 != null ? i3 : 0.0; }

    public double getPf1() { return pf1 != null ? pf1 : 0.0; }
    public double getPf2() { return pf2 != null ? pf2 : 0.0; }
    public double getPf3() { return pf3 != null ? pf3 : 0.0; }

    public double getHz1() { return hz1 != null ? hz1 : 0.0; }
    public double getHz2() { return hz2 != null ? hz2 : 0.0; }
    public double getHz3() { return hz3 != null ? hz3 : 0.0; }

    public double getW1() { return w1 != null ? w1 : 0.0; }
    public double getW2() { return w2 != null ? w2 : 0.0; }
    public double getW3() { return w3 != null ? w3 : 0.0; }

    public Integer getType() { return type; }

    public Integer getIdMeasure()  { return idMeasure; }
    public Integer getIdCircuit()  { return idCircuit; }
    public Integer getIdLocation() { return idLocation; }
    public Integer getRecordType() { return recordType; }
}
