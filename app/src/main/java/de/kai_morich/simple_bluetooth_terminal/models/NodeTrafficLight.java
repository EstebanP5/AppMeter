package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class NodeTrafficLight {

    @SerializedName("IdNode")
    private Integer idNode;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("Name")
    private String name;

    @SerializedName("Type")
    private Integer type;

    @SerializedName("TypeName")
    private String typeName;

    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("LastDate")
    private String lastDate;

    @SerializedName("LastHour")
    private String lastHour;

    /** "Green" (≤5 min) | "Yellow" (>5 min, <1 día) | "Red" (≥1 día) */
    @SerializedName("Status")
    private String status;

    // Getters
    public Integer getIdNode()     { return idNode; }
    public String getSerie()       { return serie; }
    public String getName()        { return name; }
    public Integer getType()       { return type; }
    public String getTypeName()    { return typeName; }
    public Integer getIdCircuit()  { return idCircuit; }
    public Integer getIdLocation() { return idLocation; }
    public String getLastDate()    { return lastDate; }
    public String getLastHour()    { return lastHour; }
    public String getStatus()      { return status; }

    public boolean isGreen()  { return "Green".equalsIgnoreCase(status); }
    public boolean isYellow() { return "Yellow".equalsIgnoreCase(status); }
    public boolean isRed()    { return "Red".equalsIgnoreCase(status); }

    /** Setter de conveniencia para reconstruir desde Bundle (NodeDetailBottomSheet). */
    public void setFields(int idNode, String name, String typeName,
                          int idCircuit, int idLocation,
                          String lastDate, String lastHour, String status, int type) {
        this.idNode     = idNode > 0 ? idNode : null;
        this.name       = name;
        this.typeName   = typeName;
        this.idCircuit  = idCircuit > 0 ? idCircuit : null;
        this.idLocation = idLocation > 0 ? idLocation : null;
        this.lastDate   = lastDate;
        this.lastHour   = lastHour;
        this.status     = status;
        this.type       = type >= 0 ? type : null;
    }
}
