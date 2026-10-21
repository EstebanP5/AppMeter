package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

/**
 * Respuesta de validateSerialForLink.
 * Message: "Available" (registrada y libre), "SerialAlreadyLinked" (ocupada) o "SerialNotRegistered" (serie nueva).
 */
public class SerialValidation {

    @SerializedName("IsValid")
    private Boolean isValid;

    @SerializedName("Message")
    private String message;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("Load")
    private Integer load;

    @SerializedName("DeviceType")
    private Integer deviceType;

    @SerializedName("DeviceTypeName")
    private String deviceTypeName;

    /** null si está libre; si está ocupada trae el nodo al que está vinculada (formato no verificado). */
    @SerializedName("LinkedToNode")
    private Object linkedToNode;

    public boolean isValid() { return isValid != null && isValid; }
    public String getMessage() { return message != null ? message : ""; }
    public Object getLinkedToNode() { return linkedToNode; }
}
