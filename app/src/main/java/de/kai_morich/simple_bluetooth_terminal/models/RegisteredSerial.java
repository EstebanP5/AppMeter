package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class RegisteredSerial {

    @SerializedName("Serie")
    private String serie;

    @SerializedName("DeviceType")
    private Integer deviceType;

    @SerializedName("NodeType")
    private Integer nodeType;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

    @SerializedName("IdNode")
    private Integer idNode;

    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("IdLocation")
    private Integer idLocation;

    @SerializedName("NodeName")
    private String nodeName;

    @SerializedName("IsLinked")
    private Boolean isLinked;

    @SerializedName("Load")
    private Integer load;

    @SerializedName("DateAssigned")
    private String dateAssigned;

    @SerializedName("HourAssigned")
    private String hourAssigned;

    @SerializedName("DateLinked")
    private String dateLinked;

    @SerializedName("HourLinked")
    private String hourLinked;

    @SerializedName("Status")
    private String status;

    /** For HUB items: total nodes assigned */
    @SerializedName("NodeCount")
    private Integer nodeCount;

    // -----------------------------------------------
    // Getters
    // -----------------------------------------------

    public String getSerie()         { return serie; }
    public Integer getDeviceType()   { return deviceType; }
    public Integer getNodeType()     { return nodeType; }
    public Integer getIdCustomer()   { return idCustomer; }
    public Integer getIdNode()       { return idNode; }
    public Integer getIdCircuit()    { return idCircuit; }
    public Integer getIdLocation()   { return idLocation; }
    public String getNodeName()      { return nodeName; }
    public Boolean getIsLinked()     { return isLinked; }
    public Integer getLoad()         { return load != null ? load : 0; }
    public String getDateAssigned()  { return dateAssigned; }
    public String getHourAssigned()  { return hourAssigned; }
    public String getDateLinked()    { return dateLinked; }
    public String getHourLinked()    { return hourLinked; }
    public String getStatus()        { return status; }
    public Integer getNodeCount()    { return nodeCount != null ? nodeCount : 0; }

    // -----------------------------------------------
    // Helpers
    // -----------------------------------------------

    public boolean isHub() {
        return deviceType != null && deviceType == 255;
    }

    public boolean isLinkedStatus() {
        return "RegisteredLinked".equals(status);
    }

    /** Human-readable label for NodeType */
    public String getNodeTypeName() {
        if (nodeType == null) return "—";
        switch (nodeType) {
            case 0:   return "Total Wh";
            case 1:   return "Transformador";
            case 2:   return "Solar (PV)";
            case 3:   return "BESS";
            case 4:   return "Generador";
            case 5:   return "Panel";
            case 6:   return "Circuito";
            case 7:   return "Carga";
            case 255: return "HUB";
            default:  return "Tipo " + nodeType;
        }
    }
}
