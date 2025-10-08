package de.kai_morich.simple_bluetooth_terminal.models;

import com.google.gson.annotations.SerializedName;

public class DevicesViewModel {
    @SerializedName("IdDevice")
    private Integer idDevice;

    @SerializedName("IdCircuit")
    private Integer idCircuit;

    @SerializedName("Name")
    private String name;

    @SerializedName("Serie")
    private String serie;

    @SerializedName("Description")
    private String description;

    @SerializedName("Ubiety")
    private String ubiety;

    @SerializedName("Latitude")
    private Double latitude;

    @SerializedName("Longitude")
    private Double longitude;

    @SerializedName("Icon")
    private String icon;

    @SerializedName("IdCustomer")
    private Integer idCustomer;

    // Constructor vacío
    public DevicesViewModel() {}

    // Constructor completo
    public DevicesViewModel(Integer idDevice, Integer idCircuit, String name, String serie,
                            String description, String ubiety, Double latitude, Double longitude,
                            String icon, Integer idCustomer) {
        this.idDevice = idDevice;
        this.idCircuit = idCircuit;
        this.name = name;
        this.serie = serie;
        this.description = description;
        this.ubiety = ubiety;
        this.latitude = latitude;
        this.longitude = longitude;
        this.icon = icon;
        this.idCustomer = idCustomer;
    }

    // Constructor para crear nuevo dispositivo (sin ID)
    public DevicesViewModel(Integer idCircuit, String name, String serie, String description,
                            String ubiety, Double latitude, Double longitude, String icon,
                            Integer idCustomer) {
        this.idCircuit = idCircuit;
        this.name = name;
        this.serie = serie;
        this.description = description;
        this.ubiety = ubiety;
        this.latitude = latitude;
        this.longitude = longitude;
        this.icon = icon;
        this.idCustomer = idCustomer;
    }

    // Getters y Setters
    public Integer getIdDevice() {
        return idDevice;
    }

    public void setIdDevice(Integer idDevice) {
        this.idDevice = idDevice;
    }

    public Integer getIdCircuit() {
        return idCircuit;
    }

    public void setIdCircuit(Integer idCircuit) {
        this.idCircuit = idCircuit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSerie() {
        return serie;
    }

    public void setSerie(String serie) {
        this.serie = serie;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUbiety() {
        return ubiety;
    }

    public void setUbiety(String ubiety) {
        this.ubiety = ubiety;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getIdCustomer() {
        return idCustomer;
    }

    public void setIdCustomer(Integer idCustomer) {
        this.idCustomer = idCustomer;
    }

    @Override
    public String toString() {
        return name != null ? name : "Dispositivo sin nombre";
    }
}