package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class DevicesCurrentViewModel extends DevicesViewModel {
    @SerializedName("Power")
    private Double power;

    @SerializedName("Energy")
    private Double energy;

    @SerializedName("StatusOnOff")
    private Boolean statusOnOff;

    @SerializedName("LastConnection")
    private String lastConnection;

    // Getters
    public Double getPower() { return power; }
    public Double getEnergy() { return energy; }
    public Boolean getStatusOnOff() { return statusOnOff; }
    public String getLastConnection() { return lastConnection; }

    // Setters
    public void setPower(Double power) { this.power = power; }
    public void setEnergy(Double energy) { this.energy = energy; }
    public void setStatusOnOff(Boolean statusOnOff) { this.statusOnOff = statusOnOff; }
    public void setLastConnection(String lastConnection) { this.lastConnection = lastConnection; }
}