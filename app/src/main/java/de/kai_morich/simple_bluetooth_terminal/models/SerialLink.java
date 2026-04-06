package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/** Nodo vinculado a un serial físico (respuesta de getSerialLinks). */
public class SerialLink {

    @SerializedName("IdNode")
    private Integer idNode;

    @SerializedName("NodeName")
    private String nodeName;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("Load")
    private Integer load;

    @SerializedName("TypeName")
    private String typeName;

    @SerializedName("NodeType")
    private Integer nodeType;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

    // Runtime-only field (set by UI, not from JSON)
    private int pendingLoad = -1;

    public Integer getIdNode()    { return idNode; }
    public String getNodeName()  { return nodeName != null ? nodeName : "—"; }
    public String getSerie()     { return serie; }
    public Integer getLoad()     { return load != null ? load : 0; }
    public String getTypeName()  { return typeName != null ? typeName : "—"; }
    public Integer getNodeType() { return nodeType; }
    public Integer getIdCustomer() { return idCustomer; }

    public int getPendingLoad()          { return pendingLoad >= 0 ? pendingLoad : getLoad(); }
    public void setPendingLoad(int load) { this.pendingLoad = load; }
}
